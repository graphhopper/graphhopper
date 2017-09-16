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

import com.graphhopper.routing.QueryGraph;
import com.graphhopper.routing.ch.PrepareEncoder;
import com.graphhopper.routing.profiles.BooleanEncodedValue;
import com.graphhopper.routing.profiles.DecimalEncodedValue;
import com.graphhopper.routing.profiles.TagParserFactory;
import com.graphhopper.routing.util.*;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.*;
import com.graphhopper.util.shapes.BBox;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/**
 * @author Peter Karich
 */
public class GraphHopperStorageCHTest extends GraphHopperStorageTest {
    protected CHGraph getGraph(GraphHopperStorage ghStorage) {
        return ghStorage.getGraph(CHGraph.class);
    }

    @Override
    public GraphHopperStorage newGHStorage(Directory dir, boolean is3D) {
        return new GraphHopperStorage(Arrays.asList(new FastestWeighting(carEncoder)), dir, encodingManager, is3D, new GraphExtension.NoOpExtension());
    }

    @Test
    public void testCannotBeLoadedWithNormalGraphHopperStorageClass() {
        graph = newGHStorage(new RAMDirectory(defaultGraphLoc, true), false).create(defaultSize);
        graph.flush();
        graph.close();

        graph = new GraphBuilder(encodingManager).setLocation(defaultGraphLoc).setMmap(false).setStore(true).create();
        try {
            graph.loadExisting();
            assertTrue(false);
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
        CHGraph g = getGraph(graph);
        GHUtility.createEdge(g, carAverageSpeedEnc, 60, carAccessEnc, 0, 1, true, 10);
        GHUtility.createEdge(g, carAverageSpeedEnc, 60, carAccessEnc, 0, 2, true, 20);
        GHUtility.createEdge(g, carAverageSpeedEnc, 60, carAccessEnc, 2, 3, true, 30);
        GHUtility.createEdge(g, carAverageSpeedEnc, 60, carAccessEnc, 10, 11, true, 1);

        graph.freeze();
        CHEdgeIteratorState tmpIter = g.shortcut(3, 4);
        tmpIter.setDistance(40).set(carAverageSpeedEnc, 0d);
        assertEquals(EdgeIterator.NO_EDGE, tmpIter.getSkippedEdge1());
        assertEquals(EdgeIterator.NO_EDGE, tmpIter.getSkippedEdge2());

        g.shortcut(0, 4).setDistance(40).set(carAverageSpeedEnc, 0d);
        g.setLevel(0, 1);
        g.setLevel(4, 1);

        EdgeIterator iter = g.createEdgeExplorer(new LevelEdgeFilter(g)).setBaseNode(0);
        assertEquals(1, GHUtility.count(iter));
        iter = g.createEdgeExplorer().setBaseNode(2);
        assertEquals(2, GHUtility.count(iter));

        tmpIter = g.shortcut(5, 6);
        tmpIter.setSkippedEdges(1, 2);
        assertEquals(1, tmpIter.getSkippedEdge1());
        assertEquals(2, tmpIter.getSkippedEdge2());
    }

    @Test
    public void testDisconnectEdge() {
        graph = createGHStorage();
        CHGraphImpl lg = (CHGraphImpl) getGraph(graph);

        EdgeExplorer chCarOutExplorer = lg.createEdgeExplorer(carOutFilter);
        EdgeExplorer tmpCarInExplorer = lg.createEdgeExplorer(carInFilter);

        EdgeExplorer baseCarOutExplorer = graph.createEdgeExplorer(carOutFilter);

        // only remove edges
        IntsRef flags = GHUtility.setProperties(encodingManager.createIntsRef(), carAverageSpeedEnc, 60d, carAccessEnc, true, true);
        IntsRef flags2 = GHUtility.setProperties(encodingManager.createIntsRef(), carAverageSpeedEnc, 60d, carAccessEnc, true, false);
        GHUtility.createEdge(lg, carAverageSpeedEnc, 60, carAccessEnc, 4, 1, true, 30);
        graph.freeze();
        CHEdgeIteratorState tmp = lg.shortcut(1, 2);
        tmp.setDistance(10).setData(flags);
        tmp.setSkippedEdges(10, 11);
        tmp = lg.shortcut(1, 0);
        tmp.setDistance(20).setData(flags2);
        tmp.setSkippedEdges(12, 13);
        tmp = lg.shortcut(3, 1);
        tmp.setDistance(30).setData(flags2);
        tmp.setSkippedEdges(14, 15);
        // create everytime a new independent iterator for disconnect method
        EdgeIterator iter = lg.createEdgeExplorer().setBaseNode(1);
        iter.next();
        assertEquals(3, iter.getAdjNode());
        assertEquals(1, GHUtility.count(chCarOutExplorer.setBaseNode(3)));
        lg.disconnect(lg.createEdgeExplorer(), iter);
        assertEquals(0, GHUtility.count(chCarOutExplorer.setBaseNode(3)));
        // no shortcuts visible
        assertEquals(0, GHUtility.count(baseCarOutExplorer.setBaseNode(3)));

        // even directed ways change!
        assertTrue(iter.next());
        assertEquals(0, iter.getAdjNode());
        assertEquals(1, GHUtility.count(tmpCarInExplorer.setBaseNode(0)));
        lg.disconnect(lg.createEdgeExplorer(), iter);
        assertEquals(0, GHUtility.count(tmpCarInExplorer.setBaseNode(0)));

        iter.next();
        assertEquals(2, iter.getAdjNode());
        assertEquals(1, GHUtility.count(chCarOutExplorer.setBaseNode(2)));
        lg.disconnect(lg.createEdgeExplorer(), iter);
        assertEquals(0, GHUtility.count(chCarOutExplorer.setBaseNode(2)));

        assertEquals(GHUtility.asSet(0, 2, 4), GHUtility.getNeighbors(chCarOutExplorer.setBaseNode(1)));
        assertEquals(GHUtility.asSet(4), GHUtility.getNeighbors(baseCarOutExplorer.setBaseNode(1)));
    }

    @Test
    public void testGetWeight() {
        graph = createGHStorage();
        CHGraphImpl g = (CHGraphImpl) getGraph(graph);
        assertFalse(g.edge(0, 1).isShortcut());
        assertFalse(g.edge(1, 2).isShortcut());

        graph.freeze();

        // only remove edges
        IntsRef flags = GHUtility.setProperties(encodingManager.createIntsRef(), carAverageSpeedEnc, 10d, carAccessEnc, true, true);
        CHEdgeIteratorState sc1 = g.shortcut(0, 1);
        assertTrue(sc1.isShortcut());
        sc1.setWeight(2.001);
        assertEquals(2.001, sc1.getWeight(), 1e-3);
        sc1.setWeight(100.123);
        assertEquals(100.123, sc1.getWeight(), 1e-3);
        sc1.setWeight(Double.MAX_VALUE);
        assertTrue(Double.isInfinite(sc1.getWeight()));

        sc1.setData(flags);
        sc1.setWeight(100.123);
        assertEquals(100.123, sc1.getWeight(), 1e-3);
        assertTrue(sc1.get(carAccessEnc));
        assertTrue(sc1.getReverse(carAccessEnc));
        assertTrue(sc1.detach(true).get(carAccessEnc));

        flags = GHUtility.setProperties(encodingManager.createIntsRef(), carAverageSpeedEnc, 10d, carAccessEnc, false, true);
        sc1.setData(flags);
        sc1.setWeight(100.123);
        assertEquals(100.123, sc1.getWeight(), 1e-3);
        assertFalse(sc1.get(carAccessEnc));
        assertTrue(sc1.getReverse(carAccessEnc));
        assertTrue(sc1.detach(true).get(carAccessEnc));
    }

    @Test
    public void testGetWeightIfAdvancedEncoder() {
        FlagEncoder customEncoder = new Bike2WeightFlagEncoder();
        EncodingManager em = new EncodingManager.Builder().addGlobalEncodedValues().addAll(customEncoder).build();
        FastestWeighting weighting = new FastestWeighting(customEncoder);
        GraphHopperStorage ghStorage = new GraphBuilder(em).setCHGraph(weighting).create();
        ghStorage.edge(0, 2);
        ghStorage.freeze();

        CHGraphImpl lg = (CHGraphImpl) ghStorage.getGraph(CHGraph.class, weighting);
        CHEdgeIteratorState sc1 = lg.shortcut(0, 1);
        IntsRef flags = GHUtility.setProperties(encodingManager.createIntsRef(), carAverageSpeedEnc, 10d, carAccessEnc, false, true);
        sc1.setData(flags);
        sc1.setWeight(100.123);

        assertEquals(100.123, lg.getEdgeIteratorState(sc1.getEdge(), sc1.getAdjNode()).getWeight(), 1e-3);
        assertEquals(100.123, lg.getEdgeIteratorState(sc1.getEdge(), sc1.getBaseNode()).getWeight(), 1e-3);
        assertEquals(100.123, ((CHEdgeIteratorState) GHUtility.getEdge(lg, sc1.getBaseNode(), sc1.getAdjNode())).getWeight(), 1e-3);
        assertEquals(100.123, ((CHEdgeIteratorState) GHUtility.getEdge(lg, sc1.getAdjNode(), sc1.getBaseNode())).getWeight(), 1e-3);

        sc1 = lg.shortcut(1, 0);
        assertTrue(sc1.isShortcut());
        IntsRef ints = encodingManager.createIntsRef();
        ints.ints[0] = PrepareEncoder.getScDirMask();
        sc1.setData(ints);
        sc1.setWeight(1.011011);
        assertEquals(1.011011, sc1.getWeight(), 1e-3);
    }

    @Test
    public void testQueryGraph() {
        graph = createGHStorage();
        CHGraph chGraph = getGraph(graph);
        NodeAccess na = chGraph.getNodeAccess();
        na.setNode(0, 1.00, 1.00);
        na.setNode(1, 1.02, 1.00);
        na.setNode(2, 1.04, 1.00);

        EdgeIteratorState edge1 = chGraph.edge(0, 1);
        chGraph.edge(1, 2);
        graph.freeze();
        chGraph.shortcut(0, 1);

        QueryGraph qGraph = new QueryGraph(chGraph);
        QueryResult fromRes = createQR(1.004, 1.01, 0, edge1);
        QueryResult toRes = createQR(1.019, 1.00, 0, edge1);
        qGraph.lookup(fromRes, toRes);

        Graph baseGraph = qGraph.getBaseGraph();
        EdgeExplorer explorer = baseGraph.createEdgeExplorer();

        assertTrue(chGraph.getNodes() < qGraph.getNodes());
        assertTrue(baseGraph.getNodes() == qGraph.getNodes());

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
        graph = newGHStorage(new RAMDirectory(defaultGraphLoc, true), true).
                create(defaultSize);
        graph.edge(1, 0);
        graph.edge(8, 9);
        graph.freeze();
        CHGraph chGraph = getGraph(graph);

        assertEquals(1, GHUtility.count(graph.createEdgeExplorer().setBaseNode(1)));
        assertEquals(1, GHUtility.count(chGraph.createEdgeExplorer().setBaseNode(1)));

        chGraph.shortcut(2, 3);

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
        assertEquals(2, graph.getAllEdges().getMaxId());
        assertEquals(3, chGraph.getAllEdges().getMaxId());
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
        GHUtility.createEdge(graph, carAverageSpeedEnc, 60, carAccessEnc, 1, 3, true, 10);
        GHUtility.createEdge(graph, carAverageSpeedEnc, 60, carAccessEnc, 3, 4, true, 10);
        graph.freeze();

        CHGraph lg = graph.getGraph(CHGraph.class);
        lg.shortcut(1, 4).setWeight(3).
                setData(GHUtility.setProperties(encodingManager.createIntsRef(), carAverageSpeedEnc, 10d,
                        carAccessEnc, true, true));

        EdgeExplorer vehicleOutExplorer = lg.createEdgeExplorer(new DefaultEdgeFilter(carAccessEnc, true, false));
        // iteration should result in same nodes even if reusing the iterator
        assertEquals(GHUtility.asSet(3, 4), GHUtility.getNeighbors(vehicleOutExplorer.setBaseNode(1)));
        assertEquals(GHUtility.asSet(3, 4), GHUtility.getNeighbors(vehicleOutExplorer.setBaseNode(1)));
    }

    @Test
    public void testShortcutCreationAndAccessForManyVehicles() {
        FlagEncoder tmpCar = new CarFlagEncoder();
        FlagEncoder tmpBike = new Bike2WeightFlagEncoder();
        EncodingManager em = new EncodingManager.Builder().addGlobalEncodedValues(true).addAll(tmpCar, tmpBike).build();
        List<Weighting> chWeightings = new ArrayList<Weighting>();
        chWeightings.add(new FastestWeighting(tmpCar));
        chWeightings.add(new FastestWeighting(tmpBike));
        BooleanEncodedValue tmpCarAccessEnc = encodingManager.getBooleanEncodedValue(TagParserFactory.Car.ACCESS);
        DecimalEncodedValue tmpCarAverageSpeedEnc = encodingManager.getDecimalEncodedValue(TagParserFactory.Car.AVERAGE_SPEED);
        BooleanEncodedValue tmpBikeAccessEnc = encodingManager.getBooleanEncodedValue(tmpBike.getPrefix() + "access");
        DecimalEncodedValue tmpBikeAverageSpeedEnc = encodingManager.getDecimalEncodedValue(tmpBike.getPrefix() + "average_speed");

        graph = new GraphHopperStorage(chWeightings, new RAMDirectory(), em, false, new GraphExtension.NoOpExtension()).create(1000);
        IntsRef ints = encodingManager.createIntsRef();
        GHUtility.setProperties(ints, tmpCarAverageSpeedEnc, 100d, tmpCarAccessEnc, true, true);
        GHUtility.setProperties(ints, tmpBikeAverageSpeedEnc, 10d, tmpBikeAccessEnc, true, true);

        graph.edge(0, 1).setDistance(10).setData(ints);
        graph.edge(1, 2).setDistance(10).setData(ints);

        graph.freeze();

        CHGraph carCHGraph = graph.getGraph(CHGraph.class, chWeightings.get(0));
        // enable forward directions for car
        ints = encodingManager.createIntsRef();
        ints.ints[0] = PrepareEncoder.getScFwdDir();
        EdgeIteratorState carSC02 = carCHGraph.shortcut(0, 2).setWeight(10).setDistance(20);
        carSC02.setData(ints);

        CHGraph bikeCHGraph = graph.getGraph(CHGraph.class, chWeightings.get(1));
        // enable both directions for bike
        ints.ints[0] = PrepareEncoder.getScDirMask();
        EdgeIteratorState bikeSC02 = bikeCHGraph.shortcut(0, 2).setWeight(10).setDistance(20);
        bikeSC02.setData(ints);

        // assert car CH graph
        assertTrue(carCHGraph.getEdgeIteratorState(carSC02.getEdge(), 2).get(tmpCarAccessEnc));
        assertFalse(carCHGraph.getEdgeIteratorState(carSC02.getEdge(), 2).getReverse(tmpCarAccessEnc));

        // throw exception for wrong encoder
        try {
            assertFalse(carCHGraph.getEdgeIteratorState(carSC02.getEdge(), 2).get(tmpBikeAccessEnc));
            assertTrue(false);
        } catch (AssertionError ex) {
        }

        // assert bike CH graph
        assertTrue(bikeCHGraph.getEdgeIteratorState(bikeSC02.getEdge(), 2).get(tmpBikeAccessEnc));
        assertTrue(bikeCHGraph.getEdgeIteratorState(bikeSC02.getEdge(), 2).getReverse(tmpBikeAccessEnc));

        // throw exception for wrong encoder
        try {
            assertFalse(bikeCHGraph.getEdgeIteratorState(bikeSC02.getEdge(), 2).getReverse(tmpCarAccessEnc));
            assertTrue(false);
        } catch (AssertionError ex) {
        }
    }
}
