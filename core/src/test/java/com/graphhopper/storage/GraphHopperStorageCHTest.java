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
import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.util.*;
import com.graphhopper.routing.weighting.DefaultTurnCostProvider;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.routing.weighting.TurnCostProvider;
import com.graphhopper.util.CHEdgeIteratorState;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.shapes.BBox;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static com.graphhopper.routing.ch.NodeBasedNodeContractorTest.SC_ACCESS;
import static com.graphhopper.util.EdgeIterator.NO_EDGE;
import static org.junit.Assert.*;

/**
 * @author Peter Karich
 */
public class GraphHopperStorageCHTest extends GraphHopperStorageTest {
    private CHGraph getGraph(GraphHopperStorage ghStorage) {
        return ghStorage.getCHGraph(ghStorage.getCHConfigs().get(0).getName());
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
        GraphHopperStorage graph = new GraphBuilder(encodingManager)
                .setDir(dir).set3D(is3D).withTurnCosts(true).setSegmentSize(segmentSize).build();
        for (FlagEncoder encoder : encodingManager.fetchEdgeEncoders()) {
            TurnCostProvider turnCostProvider = forEdgeBasedTraversal
                    ? new DefaultTurnCostProvider(encoder, graph.getTurnCostStorage())
                    : TurnCostProvider.NO_TURN_COST_PROVIDER;
            FastestWeighting weighting = new FastestWeighting(encoder, turnCostProvider);
            graph.addCHGraph(new CHConfig("p_" + encoder.toString(), weighting, forEdgeBasedTraversal));
        }
        return graph;
    }

    @Test(expected = IllegalArgumentException.class)
    @Override
    public void testClone() {
        // todo: implement graph copying in the presence of turn costs
        super.testClone();
    }

    @Test(expected = IllegalArgumentException.class)
    @Override
    public void testCopyTo() {
        // todo: implement graph copying in the presence of turn costs
        super.testCopyTo();
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
        g.getBaseGraph().getNodeAccess().ensureNode(30);
        graph.freeze();

        assertEquals(0, g.getLevel(10));

        g.setLevel(10, 100);
        assertEquals(100, g.getLevel(10));

        g.setLevel(30, 100);
        assertEquals(100, g.getLevel(30));
    }

    @Test
    public void testShortcutConnection() {
        graph = createGHStorage();
        EdgeExplorer baseCarOutExplorer = graph.createEdgeExplorer(carOutFilter);
        graph.edge(4, 1, 30, true);
        graph.freeze();

        CHGraph lg = getGraph(graph);
        EdgeExplorer chOutExplorer = lg.createEdgeExplorer(carOutFilter);
        EdgeExplorer chInExplorer = lg.createEdgeExplorer(carInFilter);
        lg.shortcut(1, 2, PrepareEncoder.getScDirMask(), 0, 10, 11);
        lg.shortcut(1, 0, PrepareEncoder.getScFwdDir(), 0, 12, 13);
        lg.shortcut(3, 1, PrepareEncoder.getScFwdDir(), 0, 14, 15);
        // shortcuts are only visible from the base node, for example we do not see node 1 from node 2, or node
        // 3 from node 1
        assertEquals(0, GHUtility.count(chOutExplorer.setBaseNode(2)));
        assertEquals(0, GHUtility.count(chInExplorer.setBaseNode(2)));

        assertEquals(3, GHUtility.count(chOutExplorer.setBaseNode(1)));
        assertEquals(2, GHUtility.count(chInExplorer.setBaseNode(1)));
        assertEquals(GHUtility.asSet(0, 2, 4), GHUtility.getNeighbors(chOutExplorer.setBaseNode(1)));
        assertEquals(GHUtility.asSet(4), GHUtility.getNeighbors(baseCarOutExplorer.setBaseNode(1)));

        assertEquals(1, GHUtility.count(chOutExplorer.setBaseNode(3)));
        assertEquals(0, GHUtility.count(chInExplorer.setBaseNode(3)));
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
        GraphHopperStorage ghStorage = new GraphBuilder(em).setCHConfigs(CHConfig.nodeBased("p1", weighting)).create();
        ghStorage.edge(0, 2);
        ghStorage.freeze();

        CHGraph lg = ghStorage.getCHGraph();
        int sc1 = lg.shortcut(0, 1, PrepareEncoder.getScFwdDir(), 100.123, NO_EDGE, NO_EDGE);

        assertEquals(100.123, lg.getEdgeIteratorState(sc1, 1).getWeight(), 1e-3);
        assertEquals(100.123, lg.getEdgeIteratorState(sc1, 0).getWeight(), 1e-3);
        assertEquals(100.123, GHUtility.getEdge(lg, 0, 1).getWeight(), 1e-3);

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

        CHGraph lg = getGraph(graph);
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

        CHGraph lg = getGraph(graph);
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

        CHGraph lg = getGraph(graph);
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
        List<CHConfig> chConfigs = Arrays.asList(
                CHConfig.nodeBased("p1", new FastestWeighting(tmpCar)),
                CHConfig.nodeBased("p2", new FastestWeighting(tmpBike)));
        BooleanEncodedValue tmpCarAccessEnc = tmpCar.getAccessEnc();

        graph = new GraphBuilder(em).setCHConfigs(chConfigs).create();
        IntsRef edgeFlags = GHUtility.setProperties(em.createEdgeFlags(), tmpCar, 100, true, false);
        graph.edge(0, 1).setDistance(10).setFlags(GHUtility.setProperties(edgeFlags, tmpBike, 10, true, true));
        graph.edge(1, 2).setDistance(10).setFlags(edgeFlags);

        graph.freeze();

        CHGraph carCHGraph = graph.getCHGraph(chConfigs.get(0).getName());
        // enable forward directions for car
        int carSC02 = carCHGraph.shortcut(0, 2, PrepareEncoder.getScFwdDir(), 10, NO_EDGE, NO_EDGE);

        CHGraph bikeCHGraph = graph.getCHGraph(chConfigs.get(1).getName());
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
        String edgeOrNode = edgeBased ? "edge" : "node";
        // we start with one profile
        GraphHopperStorage ghStorage = createStorageWithWeightings("p1|car|fastest|" + edgeOrNode);
        ghStorage.create(defaultSize);
        ghStorage.flush();

        // but then configure another profile and try to load the graph from disk -> error
        GraphHopperStorage newGHStorage = createStorageWithWeightings("p2|car|shortest|" + edgeOrNode);
        newGHStorage.loadExisting();
    }

    @Test(expected = IllegalStateException.class)
    public void testLoadingWithExtraWeighting_throws() {
        // we start with one profile
        GraphHopperStorage ghStorage = createStorageWithWeightings("p|car|fastest|node");
        ghStorage.create(defaultSize);
        ghStorage.flush();

        // but then add an additional profile and try to load the graph from disk -> error
        GraphHopperStorage newGHStorage = createStorageWithWeightings("p|car|fastest|node", "q|car|shortest|node");
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
        String edgeOrNode = edgeBased ? "edge" : "node";
        // we start with a gh storage with two ch weightings and flush it to disk
        GraphHopperStorage originalStorage = createStorageWithWeightings(
                "p1|car|fastest|" + edgeOrNode,
                "p2|car|shortest|" + edgeOrNode
        );
        originalStorage.create(defaultSize);
        originalStorage.flush();

        // now we create a new storage but only use one of the weightings, which should be ok
        GraphHopperStorage smallStorage = createStorageWithWeightings("p1|car|fastest|" + edgeOrNode);
        smallStorage.loadExisting();
        assertEquals(edgeBased ? 0 : 1, smallStorage.getCHConfigs(false).size());
        assertEquals(edgeBased ? 1 : 0, smallStorage.getCHConfigs(true).size());
        smallStorage.flush();

        // now we create a new storage without any ch weightings, which should also be ok
        GraphHopperStorage smallerStorage = createStorageWithWeightings();
        smallerStorage.loadExisting();
        assertEquals(0, smallerStorage.getCHConfigs(false).size());
        assertEquals(0, smallerStorage.getCHConfigs(true).size());
        smallerStorage.flush();

        // now we create yet another storage that uses both weightings again, which still works
        GraphHopperStorage fullStorage = createStorageWithWeightings(
                "p1|car|fastest|" + edgeOrNode,
                "p2|car|shortest|" + edgeOrNode
        );
        fullStorage.loadExisting();
        assertEquals(edgeBased ? 0 : 2, fullStorage.getCHConfigs(false).size());
        assertEquals(edgeBased ? 2 : 0, fullStorage.getCHConfigs(true).size());
        fullStorage.flush();
    }

    @Test
    public void testLoadingWithLessWeightings_nodeAndEdge_works() {
        // we start with a gh storage with two node-based and one edge-based ch profiles and flush it to disk
        GraphHopperStorage originalStorage = createStorageWithWeightings(
                "p1|car|fastest|node",
                "p2|car|shortest|node",
                "p3|car|shortest|edge");
        originalStorage.create(defaultSize);
        originalStorage.flush();

        // now we create a new storage but only use the edge profile, which should be ok
        GraphHopperStorage edgeStorage = createStorageWithWeightings(
                "p3|car|shortest|edge"
        );
        edgeStorage.loadExisting();
        assertEquals(0, edgeStorage.getCHConfigs(false).size());
        assertEquals(1, edgeStorage.getCHConfigs(true).size());
        edgeStorage.flush();

        // now we create yet another storage that uses one of the node and the edge profiles, which still works
        GraphHopperStorage mixedStorage = createStorageWithWeightings(
                "p1|car|fastest|node",
                "p3|car|shortest|edge"
        );
        mixedStorage.loadExisting();
        assertEquals(1, mixedStorage.getCHConfigs(false).size());
        assertEquals(1, mixedStorage.getCHConfigs(true).size());
        mixedStorage.flush();
    }

    @Test
    public void testCHProfilesWithDifferentNames() {
        FastestWeighting weighting = new FastestWeighting(carEncoder);
        // creating multiple profiles with the same name is an error
        {
            try {
                new GraphBuilder(encodingManager)
                        .setCHConfigs(
                                CHConfig.nodeBased("a", weighting),
                                CHConfig.nodeBased("b", weighting),
                                CHConfig.nodeBased("a", weighting)
                        )
                        .create();
                fail("creating multiple profiles with the same name should be an error");
            } catch (Exception e) {
                assertTrue("unexpected error: " + e.getMessage(), e.getMessage().contains("a CHGraph already exists"));
            }
        }
        // ... but using multiple profiles with different names is fine even when their properties/weighting are the same
        {
            GraphHopperStorage storage = new GraphBuilder(encodingManager)
                    .setCHConfigs(
                            CHConfig.nodeBased("a", weighting),
                            CHConfig.nodeBased("b", weighting),
                            CHConfig.nodeBased("c", weighting)
                    )
                    .create();
            assertSame(storage.getCHGraph("a"), storage.getCHGraph("a"));
            assertNotNull(storage.getCHGraph("a"));
            assertNotNull(storage.getCHGraph("b"));
            assertNotNull(storage.getCHGraph("c"));
            assertNotSame(storage.getCHGraph("a"), storage.getCHGraph("b"));
            assertNotSame(storage.getCHGraph("b"), storage.getCHGraph("c"));
            assertNotSame(storage.getCHGraph("a"), storage.getCHGraph("c"));
        }
    }

    private GraphHopperStorage createStorageWithWeightings(String... profileStrings) {
        return new GraphBuilder(encodingManager)
                .setCHConfigStrings(profileStrings)
                .setDir(new GHDirectory(defaultGraphLoc, DAType.RAM_STORE))
                .withTurnCosts(true)
                .build();
    }
}
