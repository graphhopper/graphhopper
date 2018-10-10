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
package com.graphhopper.routing.ch;

import com.carrotsearch.hppc.IntArrayList;
import com.carrotsearch.hppc.IntIndexedContainer;
import com.graphhopper.routing.*;
import com.graphhopper.routing.util.BikeFlagEncoder;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.routing.weighting.ShortestWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.*;
import com.graphhopper.util.*;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static com.graphhopper.util.Parameters.Algorithms.DIJKSTRA_BI;
import static org.junit.Assert.*;

/**
 * @author Peter Karich
 */
public class PrepareContractionHierarchiesTest {
    private final CarFlagEncoder carEncoder = new CarFlagEncoder();
    private final EncodingManager encodingManager = new EncodingManager(carEncoder);
    private final Weighting weighting = new ShortestWeighting(carEncoder);
    private final TraversalMode tMode = TraversalMode.NODE_BASED;
    private Directory dir;

    // 0-1-.....-9-10
    // |         ^   \
    // |         |    |
    // 17-16-...-11<-/
    public static void initDirected2(Graph g) {
        g.edge(0, 1, 1, true);
        g.edge(1, 2, 1, true);
        g.edge(2, 3, 1, true);
        g.edge(3, 4, 1, true);
        g.edge(4, 5, 1, true);
        g.edge(5, 6, 1, true);
        g.edge(6, 7, 1, true);
        g.edge(7, 8, 1, true);
        g.edge(8, 9, 1, true);
        g.edge(9, 10, 1, true);
        g.edge(10, 11, 1, false);
        g.edge(11, 12, 1, true);
        g.edge(11, 9, 3, false);
        g.edge(12, 13, 1, true);
        g.edge(13, 14, 1, true);
        g.edge(14, 15, 1, true);
        g.edge(15, 16, 1, true);
        g.edge(16, 17, 1, true);
        g.edge(17, 0, 1, true);
    }

    //       8
    //       |
    //    6->0->1->3->7
    //    |        |
    //    |        v
    //10<-2---4<---5
    //    9
    public static void initDirected1(Graph g) {
        g.edge(0, 8, 1, true);
        g.edge(0, 1, 1, false);
        g.edge(1, 3, 1, false);
        g.edge(3, 7, 1, false);
        g.edge(3, 5, 1, false);
        g.edge(5, 4, 1, false);
        g.edge(4, 2, 1, true);
        g.edge(2, 9, 1, false);
        g.edge(2, 10, 1, false);
        g.edge(2, 6, 1, true);
        g.edge(6, 0, 1, false);
    }

    // prepare-routing.svg
    public static Graph initShortcutsGraph(Graph g) {
        g.edge(0, 1, 1, true);
        g.edge(0, 2, 1, true);
        g.edge(1, 2, 1, true);
        g.edge(2, 3, 1.5, true);
        g.edge(1, 4, 1, true);
        g.edge(2, 9, 1, true);
        g.edge(9, 3, 1, true);
        g.edge(10, 3, 1, true);
        g.edge(4, 5, 1, true);
        g.edge(5, 6, 1, true);
        g.edge(6, 7, 1, true);
        g.edge(7, 8, 1, true);
        g.edge(8, 9, 1, true);
        g.edge(4, 11, 1, true);
        g.edge(9, 14, 1, true);
        g.edge(10, 14, 1, true);
        g.edge(11, 12, 1, true);
        g.edge(12, 15, 1, true);
        g.edge(12, 13, 1, true);
        g.edge(13, 16, 1, true);
        g.edge(15, 16, 2, true);
        g.edge(14, 16, 1, true);
        return g;
    }

    GraphHopperStorage createGHStorage() {
        return new GraphBuilder(encodingManager).setCHGraph(weighting).create();
    }

    GraphHopperStorage createExampleGraph() {
        GraphHopperStorage g = createGHStorage();

        //5-1-----2
        //   \ __/|
        //    0   |
        //   /    |
        //  4-----3
        //
        g.edge(0, 1, 1, true);
        g.edge(0, 2, 1, true);
        g.edge(0, 4, 3, true);
        g.edge(1, 2, 3, true);
        g.edge(2, 3, 1, true);
        g.edge(4, 3, 2, true);
        g.edge(5, 1, 2, true);
        return g;
    }

    @Before
    public void setUp() {
        dir = new GHDirectory("", DAType.RAM_INT);
    }

    @Test
    public void testReturnsCorrectWeighting() {
        GraphHopperStorage g = createGHStorage();
        CHGraph lg = g.getGraph(CHGraph.class);
        PrepareContractionHierarchies prepare = new PrepareContractionHierarchies(dir, g, lg, tMode);
        assertSame(weighting, prepare.getWeighting());
    }
    
    @Test
    public void testAddShortcuts() {
        GraphHopperStorage g = createExampleGraph();
        CHGraph lg = g.getGraph(CHGraph.class);
        int old = lg.getAllEdges().length();
        PrepareContractionHierarchies prepare = new PrepareContractionHierarchies(dir, g, lg, tMode);
        prepare.doWork();
        assertEquals(old + 2, lg.getAllEdges().length());
    }

    @Test
    public void testMoreComplexGraph() {
        GraphHopperStorage g = createGHStorage();
        CHGraph lg = g.getGraph(CHGraph.class);
        initShortcutsGraph(lg);
        int oldCount = g.getAllEdges().length();
        PrepareContractionHierarchies prepare = new PrepareContractionHierarchies(dir, g, lg, tMode);
        prepare.doWork();
        assertEquals(oldCount, g.getAllEdges().length());
        assertEquals(oldCount + 7, lg.getAllEdges().length());
    }

    @Test
    public void testDirectedGraph() {
        GraphHopperStorage g = createGHStorage();
        CHGraph lg = g.getGraph(CHGraph.class);
        g.edge(5, 4, 3, false);
        g.edge(4, 5, 10, false);
        g.edge(2, 4, 1, false);
        g.edge(5, 2, 1, false);
        g.edge(3, 5, 1, false);
        g.edge(4, 3, 1, false);
        g.freeze();
        int oldCount = GHUtility.count(lg.getAllEdges());
        assertEquals(6, oldCount);
        PrepareContractionHierarchies prepare = new PrepareContractionHierarchies(dir, g, lg, tMode);
        prepare.doWork();
        assertEquals(2, prepare.getShortcuts());
        assertEquals(oldCount + 2, GHUtility.count(lg.getAllEdges()));
        RoutingAlgorithm algo = prepare.createAlgo(lg, new AlgorithmOptions(DIJKSTRA_BI, weighting, tMode));
        Path p = algo.calcPath(4, 2);
        assertEquals(3, p.getDistance(), 1e-6);
        assertEquals(IntArrayList.from(4, 3, 5, 2), p.calcNodes());
    }

    @Test
    public void testDirectedGraph2() {
        GraphHopperStorage g = createGHStorage();
        CHGraph lg = g.getGraph(CHGraph.class);
        initDirected2(g);
        int oldCount = GHUtility.count(g.getAllEdges());
        assertEquals(19, oldCount);
        PrepareContractionHierarchies prepare = new PrepareContractionHierarchies(dir, g, lg, tMode);
        prepare.doWork();
        // PrepareTowerNodesShortcutsTest.printEdges(g);
        assertEquals(oldCount, g.getAllEdges().length());
        assertEquals(oldCount, GHUtility.count(g.getAllEdges()));

        long numShortcuts = 9;
        assertEquals(numShortcuts, prepare.getShortcuts());
        assertEquals(oldCount + numShortcuts, lg.getAllEdges().length());
        assertEquals(oldCount + numShortcuts, GHUtility.count(lg.getAllEdges()));
        RoutingAlgorithm algo = prepare.createAlgo(lg, new AlgorithmOptions(DIJKSTRA_BI, weighting, tMode));
        Path p = algo.calcPath(0, 10);
        assertEquals(10, p.getDistance(), 1e-6);
        assertEquals(IntArrayList.from(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10), p.calcNodes());
    }

    void initRoundaboutGraph(Graph g) {
        //              roundabout:
        //16-0-9-10--11   12<-13
        //    \       \  /      \
        //    17       \|        7-8-..
        // -15-1--2--3--4       /     /
        //     /         \-5->6/     /
        //  -14            \________/

        g.edge(16, 0, 1, true);
        g.edge(0, 9, 1, true);
        g.edge(0, 17, 1, true);
        g.edge(9, 10, 1, true);
        g.edge(10, 11, 1, true);
        g.edge(11, 28, 1, true);
        g.edge(28, 29, 1, true);
        g.edge(29, 30, 1, true);
        g.edge(30, 31, 1, true);
        g.edge(31, 4, 1, true);

        g.edge(17, 1, 1, true);
        g.edge(15, 1, 1, true);
        g.edge(14, 1, 1, true);
        g.edge(14, 18, 1, true);
        g.edge(18, 19, 1, true);
        g.edge(19, 20, 1, true);
        g.edge(20, 15, 1, true);
        g.edge(19, 21, 1, true);
        g.edge(21, 16, 1, true);
        g.edge(1, 2, 1, true);
        g.edge(2, 3, 1, true);
        g.edge(3, 4, 1, true);

        g.edge(4, 5, 1, false);
        g.edge(5, 6, 1, false);
        g.edge(6, 7, 1, false);
        g.edge(7, 13, 1, false);
        g.edge(13, 12, 1, false);
        g.edge(12, 4, 1, false);

        g.edge(7, 8, 1, true);
        g.edge(8, 22, 1, true);
        g.edge(22, 23, 1, true);
        g.edge(23, 24, 1, true);
        g.edge(24, 25, 1, true);
        g.edge(25, 27, 1, true);
        g.edge(27, 5, 1, true);
        g.edge(25, 26, 1, false);
        g.edge(26, 25, 1, false);
    }

    @Test
    public void testRoundaboutUnpacking() {
        GraphHopperStorage g = createGHStorage();
        CHGraph lg = g.getGraph(CHGraph.class);
        initRoundaboutGraph(g);
        int oldCount = g.getAllEdges().length();
        PrepareContractionHierarchies prepare = new PrepareContractionHierarchies(dir, g, lg, tMode);
        prepare.doWork();
        assertEquals(oldCount, g.getAllEdges().length());
        assertEquals(oldCount + 23, lg.getAllEdges().length());
        RoutingAlgorithm algo = prepare.createAlgo(lg, new AlgorithmOptions(DIJKSTRA_BI, weighting, tMode));
        Path p = algo.calcPath(4, 7);
        assertEquals(IntArrayList.from(4, 5, 6, 7), p.calcNodes());
    }

    void initUnpackingGraph(GraphHopperStorage ghStorage, CHGraph g, Weighting w) {
        final long flags = carEncoder.setProperties(30, true, false);
        double dist = 1;
        g.edge(10, 0).setDistance(dist).setFlags(flags);
        EdgeIteratorState edgeState01 = g.edge(0, 1);
        edgeState01.setDistance(dist).setFlags(flags);
        EdgeIteratorState edgeState12 = g.edge(1, 2).setDistance(dist).setFlags(flags);
        EdgeIteratorState edgeState23 = g.edge(2, 3).setDistance(dist).setFlags(flags);
        EdgeIteratorState edgeState34 = g.edge(3, 4).setDistance(dist).setFlags(flags);
        EdgeIteratorState edgeState45 = g.edge(4, 5).setDistance(dist).setFlags(flags);
        EdgeIteratorState edgeState56 = g.edge(5, 6).setDistance(dist).setFlags(flags);
        long oneDirFlags = PrepareEncoder.getScFwdDir();

        int tmpEdgeId = edgeState01.getEdge();
        ghStorage.freeze();
        CHEdgeIteratorState sc0_2 = g.shortcut(0, 2);
        int x = EdgeIterator.NO_EDGE;
        sc0_2.setWeight(w.calcWeight(edgeState01, false, x) + w.calcWeight(edgeState12, false, x)).setDistance(2 * dist).setFlags(oneDirFlags);
        sc0_2.setSkippedEdges(tmpEdgeId, edgeState12.getEdge());
        tmpEdgeId = sc0_2.getEdge();
        CHEdgeIteratorState sc0_3 = g.shortcut(0, 3);
        sc0_3.setWeight(sc0_2.getWeight() + w.calcWeight(edgeState23, false, x)).setDistance(3 * dist).setFlags(oneDirFlags);
        sc0_3.setSkippedEdges(tmpEdgeId, edgeState23.getEdge());
        tmpEdgeId = sc0_3.getEdge();
        CHEdgeIteratorState sc0_4 = g.shortcut(0, 4);
        sc0_4.setWeight(sc0_3.getWeight() + w.calcWeight(edgeState34, false, x)).setDistance(4).setFlags(oneDirFlags);
        sc0_4.setSkippedEdges(tmpEdgeId, edgeState34.getEdge());
        tmpEdgeId = sc0_4.getEdge();
        CHEdgeIteratorState sc0_5 = g.shortcut(0, 5);
        sc0_5.setWeight(sc0_4.getWeight() + w.calcWeight(edgeState45, false, x)).setDistance(5).setFlags(oneDirFlags);
        sc0_5.setSkippedEdges(tmpEdgeId, edgeState45.getEdge());
        tmpEdgeId = sc0_5.getEdge();
        CHEdgeIteratorState sc0_6 = g.shortcut(0, 6);
        sc0_6.setWeight(sc0_5.getWeight() + w.calcWeight(edgeState56, false, x)).setDistance(6).setFlags(oneDirFlags);
        sc0_6.setSkippedEdges(tmpEdgeId, edgeState56.getEdge());
        g.setLevel(0, 10);
        g.setLevel(6, 9);
        g.setLevel(5, 8);
        g.setLevel(4, 7);
        g.setLevel(3, 6);
        g.setLevel(2, 5);
        g.setLevel(1, 4);
        g.setLevel(10, 3);
    }

    @Test
    public void testUnpackingOrder() {
        GraphHopperStorage ghStorage = createGHStorage();
        CHGraph lg = ghStorage.getGraph(CHGraph.class);
        initUnpackingGraph(ghStorage, lg, weighting);
        PrepareContractionHierarchies prepare = new PrepareContractionHierarchies(dir, ghStorage, lg, tMode);
        RoutingAlgorithm algo = prepare.createAlgo(lg, new AlgorithmOptions(DIJKSTRA_BI, weighting, tMode));
        Path p = algo.calcPath(10, 6);
        assertEquals(7, p.getDistance(), 1e-5);
        assertEquals(IntArrayList.from(10, 0, 1, 2, 3, 4, 5, 6), p.calcNodes());
    }

    @Test
    public void testUnpackingOrder_Fastest() {
        GraphHopperStorage ghStorage = createGHStorage();
        CHGraph lg = ghStorage.getGraph(CHGraph.class);
        Weighting w = new FastestWeighting(carEncoder);
        initUnpackingGraph(ghStorage, lg, w);

        PrepareContractionHierarchies prepare = new PrepareContractionHierarchies(dir, ghStorage, lg, tMode);
        RoutingAlgorithm algo = prepare.createAlgo(lg, new AlgorithmOptions(DIJKSTRA_BI, weighting, tMode));
        Path p = algo.calcPath(10, 6);
        assertEquals(7, p.getDistance(), 1e-1);
        assertEquals(IntArrayList.from(10, 0, 1, 2, 3, 4, 5, 6), p.calcNodes());
    }

    @Test
    public void testCircleBug() {
        GraphHopperStorage g = createGHStorage();
        CHGraph lg = g.getGraph(CHGraph.class);
        //  /--1
        // -0--/
        //  |
        g.edge(0, 1, 10, true);
        g.edge(0, 1, 4, true);
        g.edge(0, 2, 10, true);
        g.edge(0, 3, 10, true);
        PrepareContractionHierarchies prepare = new PrepareContractionHierarchies(dir, g, lg, tMode);
        prepare.doWork();
        assertEquals(0, prepare.getShortcuts());
    }

    @Test
    public void testBug178() {
        // 5--------6__
        // |        |  \
        // 0-1->-2--3--4
        //   \-<-/
        //
        GraphHopperStorage g = createGHStorage();
        CHGraph lg = g.getGraph(CHGraph.class);
        g.edge(1, 2, 1, false);
        g.edge(2, 1, 1, false);

        g.edge(5, 0, 1, true);
        g.edge(5, 6, 1, true);
        g.edge(0, 1, 1, true);
        g.edge(2, 3, 1, true);
        g.edge(3, 4, 1, true);
        g.edge(6, 3, 1, true);

        PrepareContractionHierarchies prepare = new PrepareContractionHierarchies(dir, g, lg, tMode);
        prepare.doWork();
        assertEquals(2, prepare.getShortcuts());
    }

    // 0-1-2-3-4
    // |     / |
    // |    8  |
    // \   /   /
    //  7-6-5-/
    void initBiGraph(Graph graph) {
        graph.edge(0, 1, 100, true);
        graph.edge(1, 2, 1, true);
        graph.edge(2, 3, 1, true);
        graph.edge(3, 4, 1, true);
        graph.edge(4, 5, 25, true);
        graph.edge(5, 6, 25, true);
        graph.edge(6, 7, 5, true);
        graph.edge(7, 0, 5, true);
        graph.edge(3, 8, 20, true);
        graph.edge(8, 6, 20, true);
    }

    //    public static void printEdges(CHGraph g) {
//        RawEdgeIterator iter = g.getAllEdges();
//        while (iter.next()) {
//            EdgeSkipIterator single = g.getEdgeProps(iter.edge(), iter.nodeB());
//            System.out.println(iter.nodeA() + "<->" + iter.nodeB() + " \\"
//                    + single.skippedEdge1() + "," + single.skippedEdge2() + " (" + iter.edge() + ")"
//                    + ", dist: " + (float) iter.weight()
//                    + ", level:" + g.getLevel(iter.nodeA()) + "<->" + g.getLevel(iter.nodeB())
//                    + ", bothDir:" + CarFlagEncoder.isBoth(iter.setProperties()));
//        }
//        System.out.println("---");
//    }
    @Test
    public void testBits() {
        int fromNode = Integer.MAX_VALUE / 3 * 2;
        int endNode = Integer.MAX_VALUE / 37 * 17;

        long edgeId = (long) fromNode << 32 | endNode;
        assertEquals((BitUtil.BIG.toBitString(edgeId)),
                BitUtil.BIG.toLastBitString(fromNode, 32) + BitUtil.BIG.toLastBitString(endNode, 32));
    }

    @Test
    public void testMultiplePreparationsIdenticalView() {
        CarFlagEncoder tmpCarEncoder = new CarFlagEncoder();
        BikeFlagEncoder tmpBikeEncoder = new BikeFlagEncoder();
        EncodingManager tmpEncodingManager = new EncodingManager(tmpCarEncoder, tmpBikeEncoder);

        // FastestWeighting would lead to different shortcuts due to different default speeds for bike and car
        Weighting carWeighting = new ShortestWeighting(tmpCarEncoder);
        Weighting bikeWeighting = new ShortestWeighting(tmpBikeEncoder);

        List<Weighting> chWeightings = Arrays.asList(carWeighting, bikeWeighting);
        GraphHopperStorage ghStorage = new GraphHopperStorage(chWeightings, dir, tmpEncodingManager, false, new GraphExtension.NoOpExtension()).create(1000);
        initShortcutsGraph(ghStorage);

        ghStorage.freeze();

        for (Weighting w : chWeightings) {
            checkPath(ghStorage, w, 7, 5, IntArrayList.from(3, 9, 14, 16, 13, 12));
        }
    }

    @Test
    public void testMultiplePreparationsDifferentView() {
        CarFlagEncoder tmpCarEncoder = new CarFlagEncoder();
        BikeFlagEncoder tmpBikeEncoder = new BikeFlagEncoder();
        EncodingManager tmpEncodingManager = new EncodingManager(tmpCarEncoder, tmpBikeEncoder);

        Weighting carWeighting = new FastestWeighting(tmpCarEncoder);
        Weighting bikeWeighting = new FastestWeighting(tmpBikeEncoder);

        List<Weighting> chWeightings = Arrays.asList(carWeighting, bikeWeighting);
        GraphHopperStorage ghStorage = new GraphHopperStorage(chWeightings, dir, tmpEncodingManager, false, new GraphExtension.NoOpExtension()).create(1000);
        initShortcutsGraph(ghStorage);
        EdgeIteratorState edge = GHUtility.getEdge(ghStorage, 9, 14);
        edge.setFlags(tmpBikeEncoder.setAccess(edge.getFlags(), false, false));

        ghStorage.freeze();

        checkPath(ghStorage, carWeighting, 7, 5, IntArrayList.from(3, 9, 14, 16, 13, 12));
        // detour around blocked 9,14
        checkPath(ghStorage, bikeWeighting, 9, 5, IntArrayList.from(3, 10, 14, 16, 13, 12));
    }

    void checkPath(GraphHopperStorage ghStorage, Weighting w, int expShortcuts, double expDistance, IntIndexedContainer expNodes) {
        CHGraph lg = ghStorage.getGraph(CHGraph.class, w);
        PrepareContractionHierarchies prepare = new PrepareContractionHierarchies(dir, ghStorage, lg, tMode);
        prepare.doWork();
        assertEquals(w.toString(), expShortcuts, prepare.getShortcuts());
        RoutingAlgorithm algo = prepare.createAlgo(lg, new AlgorithmOptions(DIJKSTRA_BI, w, tMode));
        Path p = algo.calcPath(3, 12);
        assertEquals(w.toString(), expDistance, p.getDistance(), 1e-5);
        assertEquals(w.toString(), expNodes, p.calcNodes());
    }

}
