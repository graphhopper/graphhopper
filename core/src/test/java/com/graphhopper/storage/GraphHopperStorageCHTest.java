/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.storage;

import com.graphhopper.routing.ch.PrepareEncoder;
import com.graphhopper.routing.profiles.BooleanEncodedValue;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.util.*;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.routing.weighting.ShortestWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.*;
import com.graphhopper.util.shapes.BBox;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.graphhopper.routing.ch.NodeBasedNodeContractorTest.SC_ACCESS;
import static com.graphhopper.routing.weighting.TurnWeighting.INFINITE_U_TURN_COSTS;
import static com.graphhopper.util.EdgeIterator.NO_EDGE;
import static org.junit.Assert.*;

/**
 * @author Peter Karich
 */
public class GraphHopperStorageCHTest extends GraphHopperStorageTest {
    private CHGraph getGraph(GraphHopperStorage ghStorage) {
        return ghStorage.getCHGraph();
    }

    @Override
    protected GraphHopperStorage newGHStorage(Directory dir, boolean enabled3D) {
        return newGHStorage(dir, enabled3D, -1);
    }

    @Override
    public GraphHopperStorage newGHStorage(Directory dir, boolean is3D, int segmentSize) {
        return newGHStorage(dir, is3D, false, segmentSize);
    }

    private GraphHopperStorage newGHStorage(boolean is3D, boolean forEdgeBasedTraversal) {
        return newGHStorage(new RAMDirectory(defaultGraphLoc, true), is3D, forEdgeBasedTraversal, -1).create(defaultSize);
    }

    private GraphHopperStorage newGHStorage(Directory dir, boolean is3D, boolean forEdgeBasedTraversal, int segmentSize) {
        CHProfile chProfile = new CHProfile(new FastestWeighting(carEncoder), forEdgeBasedTraversal, INFINITE_U_TURN_COSTS);
        return new GraphBuilder(encodingManager).setCHProfiles(chProfile).setDir(dir).set3D(is3D).setSegmentSize(segmentSize).build();
    }

    @Test
    public void testCannotBeLoadedWithNormalGraphHopperStorageClass() {
        graph = newGHStorage(false, false);
        graph.flush();
        graph.close();

        graph = GraphBuilder.start(encodingManager).setRAM(defaultGraphLoc, true).create();
        try {
            graph.loadExisting();
            fail();
        } catch (Exception ex) {
        }

        graph = newGHStorage(new RAMDirectory(defaultGraphLoc, true), false);
        assertTrue(graph.loadExisting());
        // empty graph still has invalid bounds
        assertEquals(graph.getBounds(), BBox.createInverse(false));
    }

    @Test
    public void testPrios() {
        graph = createGHStorage();
        CHGraph g = getGraph(graph);
        g.getNodeAccess().ensureNode(30);
        graph.freeze();

        assertEquals(0, g.getLevel(10));

        g.setLevel(10, 100);
        assertEquals(100, g.getLevel(10));

        g.setLevel(30, 100);
        assertEquals(100, g.getLevel(30));
    }

    @Test
    public void testEdgeFilter() {
        graph = createGHStorage();
        graph.edge(0, 1, 10, true);
        graph.edge(0, 2, 20, true);
        graph.edge(2, 3, 30, true);
        graph.edge(10, 11, 1, true);
        graph.freeze();

        CHGraph lg = getGraph(graph);
        lg.shortcut(3, 4, PrepareEncoder.getScDirMask(), 0, NO_EDGE, NO_EDGE);
        lg.shortcut(0, 4, PrepareEncoder.getScDirMask(), 0, NO_EDGE, NO_EDGE);
        lg.setLevel(0, 1);
        lg.setLevel(4, 1);

        EdgeIterator iter = lg.createEdgeExplorer(new LevelEdgeFilter(lg)).setBaseNode(0);
        assertEquals(1, GHUtility.count(iter));
        iter = lg.createEdgeExplorer().setBaseNode(2);
        assertEquals(2, GHUtility.count(iter));

        int sc = lg.shortcut(5, 6, PrepareEncoder.getScDirMask(), 0, 1, 2);
        CHEdgeIteratorState tmpIter = lg.getEdgeIteratorState(sc, 6);
        assertEquals(1, tmpIter.getSkippedEdge1());
        assertEquals(2, tmpIter.getSkippedEdge2());
    }

    @Test
    public void testDisconnectEdge() {
        graph = createGHStorage();
        EdgeExplorer baseCarOutExplorer = graph.createEdgeExplorer(carOutFilter);
        // only remove edges
        graph.edge(4, 1, 30, true);
        graph.freeze();

        CHGraph lg = getGraph(graph);
        EdgeExplorer chCarOutExplorer = lg.createEdgeExplorer(carOutFilter);
        EdgeExplorer tmpCarInExplorer = lg.createEdgeExplorer(carInFilter);
        lg.shortcut(1, 2, PrepareEncoder.getScDirMask(), 0, 10, 11);
        lg.shortcut(1, 0, PrepareEncoder.getScFwdDir(), 0, 12, 13);
        lg.shortcut(3, 1, PrepareEncoder.getScFwdDir(), 0, 14, 15);
        // create everytime a new independent iterator for disconnect method
        EdgeIterator iter = lg.createEdgeExplorer().setBaseNode(1);
        iter.next();
        assertEquals(3, iter.getAdjNode());
        assertEquals(1, GHUtility.count(chCarOutExplorer.setBaseNode(3)));
        lg.disconnectEdge(iter.getEdge(), iter.getAdjNode(), -1);
        assertEquals(0, GHUtility.count(chCarOutExplorer.setBaseNode(3)));
        // no shortcuts visible
        assertEquals(0, GHUtility.count(baseCarOutExplorer.setBaseNode(3)));

        // even directed ways change!
        assertTrue(iter.next());
        assertEquals(0, iter.getAdjNode());
        assertEquals(1, GHUtility.count(tmpCarInExplorer.setBaseNode(0)));
        lg.disconnectEdge(iter.getEdge(), iter.getAdjNode(), -1);
        assertEquals(0, GHUtility.count(tmpCarInExplorer.setBaseNode(0)));

        iter.next();
        assertEquals(2, iter.getAdjNode());
        assertEquals(1, GHUtility.count(chCarOutExplorer.setBaseNode(2)));
        lg.disconnectEdge(iter.getEdge(), iter.getAdjNode(), -1);
        assertEquals(0, GHUtility.count(chCarOutExplorer.setBaseNode(2)));

        assertEquals(GHUtility.asSet(0, 2, 4), GHUtility.getNeighbors(chCarOutExplorer.setBaseNode(1)));
        assertEquals(GHUtility.asSet(4), GHUtility.getNeighbors(baseCarOutExplorer.setBaseNode(1)));
    }

    @Test
    public void testGetWeight() {
        graph = createGHStorage();
        CHGraph g = getGraph(graph);
        EdgeIteratorState edge1 = graph.edge(0, 1);
        EdgeIteratorState edge2 = graph.edge(1, 2);
        graph.freeze();
        assertFalse(g.getEdgeIteratorState(edge1.getEdge(), Integer.MIN_VALUE).isShortcut());
        assertFalse(g.getEdgeIteratorState(edge2.getEdge(), Integer.MIN_VALUE).isShortcut());


        // only remove edges
        int flags = PrepareEncoder.getScDirMask();
        int sc = g.shortcut(0, 1, flags, 5, NO_EDGE, NO_EDGE);
        CHEdgeIteratorState sc1 = g.getEdgeIteratorState(sc, 1);
        assertTrue(sc1.isShortcut());
        sc1.setWeight(2.001);
        assertEquals(2.001, sc1.getWeight(), 1e-3);
        sc1.setWeight(100.123);
        assertEquals(100.123, sc1.getWeight(), 1e-3);
        sc1.setWeight(Double.MAX_VALUE);
        assertTrue(Double.isInfinite(sc1.getWeight()));

        sc1.setFlagsAndWeight(flags, 100.123);
        assertEquals(100.123, sc1.getWeight(), 1e-3);
        assertTrue(sc1.get(SC_ACCESS));
        assertTrue(sc1.getReverse(SC_ACCESS));

        flags = PrepareEncoder.getScBwdDir();
        sc1.setFlagsAndWeight(flags, 100.123);
        assertEquals(100.123, sc1.getWeight(), 1e-3);
        assertFalse(sc1.get(SC_ACCESS));
        assertTrue(sc1.getReverse(SC_ACCESS));

        // check min weight
        sc1.setFlagsAndWeight(flags, 1e-5);
        assertEquals(1e-3, sc1.getWeight(), 1e-10);
    }

    @Test
    public void testGetWeightIfAdvancedEncoder() {
        FlagEncoder customEncoder = new Bike2WeightFlagEncoder();
        EncodingManager em = EncodingManager.create(customEncoder);
        FastestWeighting weighting = new FastestWeighting(customEncoder);
        GraphHopperStorage ghStorage = new GraphBuilder(em).setCHProfiles(CHProfile.nodeBased(weighting)).create();
        ghStorage.edge(0, 2);
        ghStorage.freeze();

        CHGraph lg = ghStorage.getCHGraph();
        int sc1 = lg.shortcut(0, 1, PrepareEncoder.getScFwdDir(), 100.123, NO_EDGE, NO_EDGE);

        assertEquals(100.123, lg.getEdgeIteratorState(sc1, 1).getWeight(), 1e-3);
        assertEquals(100.123, lg.getEdgeIteratorState(sc1, 0).getWeight(), 1e-3);
        assertEquals(100.123, ((CHEdgeIteratorState) GHUtility.getEdge(lg, 0, 1)).getWeight(), 1e-3);
        assertEquals(100.123, ((CHEdgeIteratorState) GHUtility.getEdge(lg, 1, 0)).getWeight(), 1e-3);

        int sc2 = lg.shortcut(1, 0, PrepareEncoder.getScDirMask(), 1.011011, NO_EDGE, NO_EDGE);
        assertEquals(1.011011, lg.getEdgeIteratorState(sc2, 0).getWeight(), 1e-3);
        assertEquals(1.011011, lg.getEdgeIteratorState(sc2, 1).getWeight(), 1e-3);
    }

    @Test
    public void weightExact() {
        graph = createGHStorage();
        CHGraph chGraph = getGraph(graph);
        graph.edge(0, 1, 1, false);
        graph.edge(1, 2, 1, false);
        graph.freeze();

        // we just make up some weights, they do not really have to be related to our previous edges.
        // 1.004+1.006 = 2.09999999999. we make sure this does not become 2.09 instead of 2.10 (due to truncation)
        double x1 = 1.004;
        double x2 = 1.006;
        chGraph.shortcut(0, 2, PrepareEncoder.getScFwdDir(), x1 + x2, 0, 1);
        CHEdgeIteratorState sc = chGraph.getEdgeIteratorState(2, 2);
        assertEquals(2.01, sc.getWeight(), 1.e-6);
    }

    @Test
    public void testQueryGraph() {
        graph = createGHStorage();
        CHGraph chGraph = getGraph(graph);
        NodeAccess na = chGraph.getNodeAccess();
        na.setNode(0, 1.00, 1.00);
        na.setNode(1, 1.02, 1.00);
        na.setNode(2, 1.04, 1.00);

        EdgeIteratorState edge1 = graph.edge(0, 1);
        graph.edge(1, 2);
        graph.freeze();
        chGraph.shortcut(0, 1, PrepareEncoder.getScDirMask(), 10, NO_EDGE, NO_EDGE);

        QueryResult fromRes = createQR(1.004, 1.01, 0, edge1);
        QueryResult toRes = createQR(1.019, 1.00, 0, edge1);
        QueryGraph qGraph = QueryGraph.lookup(chGraph, fromRes, toRes);

        Graph baseGraph = qGraph.getBaseGraph();
        EdgeExplorer explorer = baseGraph.createEdgeExplorer();

        assertTrue(chGraph.getNodes() < qGraph.getNodes());
        assertEquals(baseGraph.getNodes(), qGraph.getNodes());

        // traverse virtual edges and normal edges but no shortcuts!
        assertEquals(GHUtility.asSet(fromRes.getClosestNode()), GHUtility.getNeighbors(explorer.setBaseNode(0)));
        assertEquals(GHUtility.asSet(toRes.getClosestNode(), 2), GHUtility.getNeighbors(explorer.setBaseNode(1)));

        // get neighbors from virtual nodes
        assertEquals(GHUtility.asSet(0, toRes.getClosestNode()), GHUtility.getNeighbors(explorer.setBaseNode(fromRes.getClosestNode())));
        assertEquals(GHUtility.asSet(1, fromRes.getClosestNode()), GHUtility.getNeighbors(explorer.setBaseNode(toRes.getClosestNode())));
    }

    QueryResult createQR(double lat, double lon, int wayIndex, EdgeIteratorState edge) {
        QueryResult res = new QueryResult(lat, lon);
        res.setClosestEdge(edge);
        res.setWayIndex(wayIndex);
        res.setSnappedPosition(QueryResult.Position.EDGE);
        res.calcSnappedPoint(Helper.DIST_PLANE);
        return res;
    }

    @Test
    @Override
    public void testSave_and_Freeze() throws IOException {
        // belongs to each other
        super.testSave_and_Freeze();
        graph.close();

        // test freeze and shortcut creation & loading
        graph = newGHStorage(true, false);
        graph.edge(1, 0);
        graph.edge(8, 9);
        graph.freeze();
        CHGraph chGraph = getGraph(graph);

        assertEquals(1, GHUtility.count(graph.createEdgeExplorer().setBaseNode(1)));
        assertEquals(1, GHUtility.count(chGraph.createEdgeExplorer().setBaseNode(1)));

        chGraph.shortcut(2, 3, PrepareEncoder.getScDirMask(), 10, NO_EDGE, NO_EDGE);

        // should be identical to access without shortcut
        assertEquals(1, GHUtility.count(graph.createEdgeExplorer().setBaseNode(1)));
        assertEquals(1, GHUtility.count(chGraph.createEdgeExplorer().setBaseNode(1)));

        // base graph does not see shortcut
        assertEquals(0, GHUtility.count(graph.createEdgeExplorer().setBaseNode(2)));
        assertEquals(1, GHUtility.count(chGraph.createEdgeExplorer().setBaseNode(2)));

        graph.flush();
        graph.close();

        graph = newGHStorage(new RAMDirectory(defaultGraphLoc, true), true);
        assertTrue(graph.loadExisting());
        assertTrue(graph.isFrozen());

        chGraph = getGraph(graph);
        assertEquals(10, chGraph.getNodes());
        assertEquals(2, graph.getEdges());
        assertEquals(3, chGraph.getEdges());
        assertEquals(1, GHUtility.count(chGraph.createEdgeExplorer().setBaseNode(2)));

        AllCHEdgesIterator iter = chGraph.getAllEdges();
        assertTrue(iter.next());
        assertFalse(iter.isShortcut());
        assertEquals(0, iter.getEdge());

        assertTrue(iter.next());
        assertFalse(iter.isShortcut());
        assertEquals(1, iter.getEdge());

        assertTrue(iter.next());
        assertTrue(iter.isShortcut());
        assertEquals(2, iter.getEdge());
        assertFalse(iter.next());
    }

    @Test
    public void testSimpleShortcutCreationAndTraversal() {
        graph = createGHStorage();
        graph.edge(1, 3, 10, true);
        graph.edge(3, 4, 10, true);
        graph.freeze();

        CHGraph lg = graph.getCHGraph();
        lg.shortcut(1, 4, PrepareEncoder.getScFwdDir(), 3, NO_EDGE, NO_EDGE);

        EdgeExplorer vehicleOutExplorer = lg.createEdgeExplorer(DefaultEdgeFilter.outEdges(carEncoder));
        // iteration should result in same nodes even if reusing the iterator
        assertEquals(GHUtility.asSet(3, 4), GHUtility.getNeighbors(vehicleOutExplorer.setBaseNode(1)));
        assertEquals(GHUtility.asSet(3, 4), GHUtility.getNeighbors(vehicleOutExplorer.setBaseNode(1)));
    }

    @Test
    public void testAddShortcutSkippedEdgesWriteRead() {
        graph = createGHStorage();
        final EdgeIteratorState edge1 = graph.edge(1, 3, 10, true);
        final EdgeIteratorState edge2 = graph.edge(3, 4, 10, true);
        graph.freeze();

        CHGraph lg = graph.getCHGraph();
        lg.shortcut(1, 4, PrepareEncoder.getScDirMask(), 10, NO_EDGE, NO_EDGE);

        AllCHEdgesIterator iter = lg.getAllEdges();
        iter.next();
        iter.next();
        iter.next();
        assertTrue(iter.isShortcut());
        iter.setSkippedEdges(edge1.getEdge(), edge2.getEdge());
        assertEquals(edge1.getEdge(), iter.getSkippedEdge1());
        assertEquals(edge2.getEdge(), iter.getSkippedEdge2());
    }

    @Test
    public void testAddShortcutSkippedEdgesWriteRead_writeWithCHEdgeIterator() {
        graph = createGHStorage();
        final EdgeIteratorState edge1 = graph.edge(1, 3, 10, true);
        final EdgeIteratorState edge2 = graph.edge(3, 4, 10, true);
        graph.freeze();

        CHGraph lg = graph.getCHGraph();
        lg.shortcut(1, 4, PrepareEncoder.getScDirMask(), 10, edge1.getEdge(), edge2.getEdge());

        AllCHEdgesIterator iter = lg.getAllEdges();
        iter.next();
        iter.next();
        iter.next();
        assertTrue(iter.isShortcut());
        assertEquals(edge1.getEdge(), iter.getSkippedEdge1());
        assertEquals(edge2.getEdge(), iter.getSkippedEdge2());
    }

    @Test(expected = IllegalStateException.class)
    public void testAddShortcut_edgeBased_throwsIfNotConfiguredForEdgeBased() {
        graph = newGHStorage(false, false);
        graph.edge(0, 1, 1, false);
        graph.edge(1, 2, 1, false);
        graph.freeze();
        addShortcut(getGraph(graph), 0, 2, true, 0, 1, 0, 1, 2);
    }

    @Test
    public void testAddShortcut_edgeBased() {
        // 0 -> 1 -> 2
        graph = newGHStorage(false, true);
        graph.edge(0, 1, 1, false);
        graph.edge(1, 2, 3, false);
        graph.freeze();
        CHGraph lg = getGraph(this.graph);
        addShortcut(lg, 0, 2, true, 0, 1, 0, 1, 4);
        AllCHEdgesIterator iter = lg.getAllEdges();
        iter.next();
        iter.next();
        iter.next();
        assertEquals(0, iter.getOrigEdgeFirst());
        assertEquals(1, iter.getOrigEdgeLast());
    }

    @Test
    public void testGetEdgeIterator() {
        graph = newGHStorage(false, true);
        graph.edge(0, 1, 1, false);
        graph.edge(1, 2, 1, false);
        graph.freeze();
        CHGraph lg = getGraph(graph);
        addShortcut(lg, 0, 2, true, 0, 1, 0, 1, 2);

        CHEdgeIteratorState sc02 = lg.getEdgeIteratorState(2, 2);
        assertNotNull(sc02);
        assertEquals(0, sc02.getBaseNode());
        assertEquals(2, sc02.getAdjNode());
        assertEquals(2, sc02.getEdge());
        assertEquals(0, sc02.getSkippedEdge1());
        assertEquals(1, sc02.getSkippedEdge2());
        assertEquals(0, sc02.getOrigEdgeFirst());
        assertEquals(1, sc02.getOrigEdgeLast());

        CHEdgeIteratorState sc20 = lg.getEdgeIteratorState(2, 0);
        assertNotNull(sc20);
        assertEquals(2, sc20.getBaseNode());
        assertEquals(0, sc20.getAdjNode());
        assertEquals(2, sc20.getEdge());
        // note these are not stateful! i.e. even though we are looking at the edge 2->0 the first skipped/orig edge
        // is still edge 0 and the second skipped/last orig edge is edge 1
        assertEquals(0, sc20.getSkippedEdge1());
        assertEquals(1, sc20.getSkippedEdge2());
        assertEquals(0, sc20.getOrigEdgeFirst());
        assertEquals(1, sc20.getOrigEdgeLast());
    }

    private void addShortcut(CHGraph chGraph, int from, int to, boolean fwd, int firstOrigEdge, int lastOrigEdge,
                             int skipEdge1, int skipEdge2, int distance) {
        chGraph.shortcutEdgeBased(from, to, fwd ? PrepareEncoder.getScFwdDir() : PrepareEncoder.getScBwdDir(), 0, skipEdge1, skipEdge2, firstOrigEdge, lastOrigEdge);
    }

    @Test
    public void testShortcutCreationAndAccessForManyVehicles() {
        FlagEncoder tmpCar = new CarFlagEncoder();
        FlagEncoder tmpBike = new Bike2WeightFlagEncoder();
        EncodingManager em = EncodingManager.create(tmpCar, tmpBike);
        List<CHProfile> chProfiles = Arrays.asList(
                CHProfile.nodeBased(new FastestWeighting(tmpCar)),
                CHProfile.nodeBased(new FastestWeighting(tmpBike)));
        BooleanEncodedValue tmpCarAccessEnc = tmpCar.getAccessEnc();

        graph = new GraphBuilder(em).setCHProfiles(chProfiles).create();
        IntsRef edgeFlags = GHUtility.setProperties(em.createEdgeFlags(), tmpCar, 100, true, false);
        graph.edge(0, 1).setDistance(10).setFlags(GHUtility.setProperties(edgeFlags, tmpBike, 10, true, true));
        graph.edge(1, 2).setDistance(10).setFlags(edgeFlags);

        graph.freeze();

        CHGraph carCHGraph = graph.getCHGraph(chProfiles.get(0));
        // enable forward directions for car
        int carSC02 = carCHGraph.shortcut(0, 2, PrepareEncoder.getScFwdDir(), 10, NO_EDGE, NO_EDGE);

        CHGraph bikeCHGraph = graph.getCHGraph(chProfiles.get(1));
        // enable both directions for bike
        int bikeSC02 = bikeCHGraph.shortcut(0, 2, PrepareEncoder.getScDirMask(), 10, NO_EDGE, NO_EDGE);

        // assert car CH graph
        assertTrue(carCHGraph.getEdgeIteratorState(carSC02, 2).get(tmpCarAccessEnc));
        assertFalse(carCHGraph.getEdgeIteratorState(carSC02, 2).getReverse(tmpCarAccessEnc));

        BooleanEncodedValue tmpBikeAccessEnc = tmpBike.getAccessEnc();

        // throw exception for wrong encoder
        try {
            assertFalse(carCHGraph.getEdgeIteratorState(carSC02, 2).get(tmpBikeAccessEnc));
            fail();
        } catch (AssertionError ex) {
        }

        // assert bike CH graph
        assertTrue(bikeCHGraph.getEdgeIteratorState(bikeSC02, 2).get(tmpBikeAccessEnc));
        assertTrue(bikeCHGraph.getEdgeIteratorState(bikeSC02, 2).getReverse(tmpBikeAccessEnc));

        // throw exception for wrong encoder
        try {
            assertFalse(bikeCHGraph.getEdgeIteratorState(bikeSC02, 2).getReverse(tmpCarAccessEnc));
            fail();
        } catch (AssertionError ex) {
        }
    }

    @Test(expected = IllegalStateException.class)
    public void testLoadingWithWrongWeighting_node_throws() {
        testLoadingWithWrongWeighting_throws(false);
    }

    @Test(expected = IllegalStateException.class)
    public void testLoadingWithWrongWeighting_edge_throws() {
        testLoadingWithWrongWeighting_throws(true);
    }

    private void testLoadingWithWrongWeighting_throws(boolean edgeBased) {
        // we start with one weighting
        GraphHopperStorage ghStorage = newGHStorage(new GHDirectory(defaultGraphLoc, DAType.RAM_STORE), false, edgeBased, -1);
        ghStorage.create(defaultSize);
        ghStorage.flush();

        // but then configure another weighting and try to load the graph from disk -> error
        GraphHopperStorage newGHStorage = createStorageWithWeightings(edgeBased, new ShortestWeighting(carEncoder));
        newGHStorage.loadExisting();
    }

    @Test(expected = IllegalStateException.class)
    public void testLoadingWithExtraWeighting_throws() {
        // we start with one weighting
        GraphHopperStorage ghStorage = newGHStorage(new GHDirectory(defaultGraphLoc, DAType.RAM_STORE), false);
        ghStorage.create(defaultSize);
        ghStorage.flush();

        // but then add an additional weighting and try to load the graph from disk -> error
        GraphHopperStorage newGHStorage = createStorageWithWeightings(false,
                new FastestWeighting(carEncoder), new ShortestWeighting(carEncoder));
        newGHStorage.loadExisting();
    }

    @Test
    public void testLoadingWithLessWeightings_node_works() {
        testLoadingWithLessWeightings_works(false);
    }

    @Test
    public void testLoadingWithLessWeightings_edge_works() {
        testLoadingWithLessWeightings_works(true);
    }

    private void testLoadingWithLessWeightings_works(boolean edgeBased) {
        // we start with a gh storage with two ch weightings and flush it to disk
        FastestWeighting weighting1 = new FastestWeighting(carEncoder);
        ShortestWeighting weighting2 = new ShortestWeighting(carEncoder);
        GraphHopperStorage originalStorage = createStorageWithWeightings(edgeBased, weighting1, weighting2);
        originalStorage.create(defaultSize);
        originalStorage.flush();

        // now we create a new storage but only use one of the weightings, which should be ok
        GraphHopperStorage smallStorage = createStorageWithWeightings(edgeBased, weighting1);
        smallStorage.loadExisting();
        assertEquals(edgeBased ? 0 : 1, smallStorage.getCHProfiles(false).size());
        assertEquals(edgeBased ? 1 : 0, smallStorage.getCHProfiles(true).size());
        smallStorage.flush();

        // now we create yet another storage that uses both weightings again, which still works
        GraphHopperStorage fullStorage = createStorageWithWeightings(edgeBased, weighting1, weighting2);
        fullStorage.loadExisting();
        assertEquals(edgeBased ? 0 : 2, fullStorage.getCHProfiles(false).size());
        assertEquals(edgeBased ? 2 : 0, fullStorage.getCHProfiles(true).size());
        fullStorage.flush();
    }

    @Test
    public void testLoadingWithLessWeightings_nodeAndEdge_works() {
        // we start with a gh storage with two node-based and one edge-based ch weighting and flush it to disk
        FastestWeighting weighting1 = new FastestWeighting(carEncoder);
        ShortestWeighting weighting2 = new ShortestWeighting(carEncoder);
        GraphHopperStorage originalStorage = createStorageWithWeightings(
                Arrays.<Weighting>asList(weighting1, weighting2),
                Arrays.<Weighting>asList(weighting2));
        originalStorage.create(defaultSize);
        originalStorage.flush();

        // now we create a new storage but only use the edge weighting, which should be ok
        GraphHopperStorage edgeStorage = createStorageWithWeightings(true, weighting2);
        edgeStorage.loadExisting();
        assertEquals(0, edgeStorage.getCHProfiles(false).size());
        assertEquals(1, edgeStorage.getCHProfiles(true).size());
        edgeStorage.flush();

        // now we create yet another storage that uses one of the node and the edge weighting, which still works
        GraphHopperStorage mixedStorage = createStorageWithWeightings(
                Collections.<Weighting>singletonList(weighting1),
                Collections.<Weighting>singletonList(weighting2));
        mixedStorage.loadExisting();
        assertEquals(1, mixedStorage.getCHProfiles(false).size());
        assertEquals(1, mixedStorage.getCHProfiles(true).size());
        mixedStorage.flush();
    }

    private GraphHopperStorage createStorageWithWeightings(boolean edgeBased, Weighting... weightings) {
        List<Weighting> nodeBasedCHWeightings = edgeBased ? Collections.<Weighting>emptyList() : Arrays.asList(weightings);
        List<Weighting> edgeBasedCHWeightings = edgeBased ? Arrays.asList(weightings) : Collections.<Weighting>emptyList();
        return createStorageWithWeightings(nodeBasedCHWeightings, edgeBasedCHWeightings);
    }

    private GraphHopperStorage createStorageWithWeightings(List<Weighting> nodeBasedCHWeightings, List<Weighting> edgeBasedCHWeightings) {
        List<CHProfile> profiles = new ArrayList<>(nodeBasedCHWeightings.size() + edgeBasedCHWeightings.size());
        for (Weighting nodeBasedCHWeighting : nodeBasedCHWeightings) {
            profiles.add(CHProfile.nodeBased(nodeBasedCHWeighting));
        }
        for (Weighting edgeBasedCHWeighting : edgeBasedCHWeightings) {
            profiles.add(CHProfile.edgeBased(edgeBasedCHWeighting, INFINITE_U_TURN_COSTS));
        }
        return new GraphBuilder(encodingManager).setCHProfiles(profiles).setDir(new GHDirectory(defaultGraphLoc, DAType.RAM_STORE)).build();
    }
}
