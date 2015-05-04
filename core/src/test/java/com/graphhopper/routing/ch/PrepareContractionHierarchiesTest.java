/*
 *  Licensed to GraphHopper and Peter Karich under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  GraphHopper licenses this file to you under the Apache License, 
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

import com.graphhopper.routing.*;
import com.graphhopper.routing.ch.PrepareContractionHierarchies.Shortcut;
import com.graphhopper.routing.util.*;
import com.graphhopper.storage.*;
import com.graphhopper.util.*;
import java.util.Collection;
import java.util.Iterator;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

/**
 * @author Peter Karich
 */
public class PrepareContractionHierarchiesTest
{
    private final EncodingManager encodingManager = new EncodingManager("CAR");
    private final CarFlagEncoder carEncoder = (CarFlagEncoder) encodingManager.getEncoder("CAR");
    private final Weighting weighting = new ShortestWeighting();
    private final TraversalMode tMode = TraversalMode.NODE_BASED;
    private Directory dir;

    LevelGraph createGraph()
    {
        return new GraphBuilder(encodingManager).levelGraphCreate();
    }

    LevelGraph createExampleGraph()
    {
        LevelGraph g = createGraph();

        //5-1-----2
        //   \ __/|
        //    0   |
        //   /    |
        //  4-----3
        //
        g.edge(0, 1, 1, true);
        g.edge(0, 2, 1, true);
        g.edge(0, 4, 3, true);
        g.edge(1, 2, 2, true);
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
    public void testShortestPathSkipNode()
    {
        LevelGraph g = createExampleGraph();
        double normalDist = new Dijkstra(g, carEncoder, weighting, tMode).calcPath(4, 2).getDistance();
        DijkstraOneToMany algo = new DijkstraOneToMany(g, carEncoder, weighting, tMode);
        PrepareContractionHierarchies prepare = new PrepareContractionHierarchies(dir, g, carEncoder, weighting, tMode);
        prepare.initFromGraph().prepareNodes();
        algo.setEdgeFilter(new PrepareContractionHierarchies.IgnoreNodeFilter(g, g.getNodes() + 1).setAvoidNode(3));
        algo.setWeightLimit(100);
        int nodeEntry = algo.findEndNode(4, 2);
        assertTrue(algo.getWeight(nodeEntry) > normalDist);

        algo.clear();
        nodeEntry = algo.setLimitVisitedNodes(1).findEndNode(4, 2);
        assertEquals(-1, nodeEntry);
    }

    @Test
    public void testShortestPathSkipNode2()
    {
        LevelGraph g = createExampleGraph();
        double normalDist = new Dijkstra(g, carEncoder, weighting, tMode).calcPath(4, 2).getDistance();
        assertEquals(3, normalDist, 1e-5);
        DijkstraOneToMany algo = new DijkstraOneToMany(g, carEncoder, weighting, tMode);
        PrepareContractionHierarchies prepare = new PrepareContractionHierarchies(dir, g, carEncoder, weighting, tMode);
        prepare.initFromGraph().prepareNodes();
        algo.setEdgeFilter(new PrepareContractionHierarchies.IgnoreNodeFilter(g, g.getNodes() + 1).setAvoidNode(3));
        algo.setWeightLimit(10);
        int nodeEntry = algo.findEndNode(4, 2);
        assertEquals(4, algo.getWeight(nodeEntry), 1e-5);

        nodeEntry = algo.findEndNode(4, 1);
        assertEquals(4, algo.getWeight(nodeEntry), 1e-5);
    }

    @Test
    public void testShortestPathLimit()
    {
        LevelGraph g = createExampleGraph();
        DijkstraOneToMany algo = new DijkstraOneToMany(g, carEncoder, weighting, tMode);
        PrepareContractionHierarchies prepare = new PrepareContractionHierarchies(dir, g, carEncoder, weighting, tMode);
        prepare.initFromGraph().prepareNodes();
        algo.setEdgeFilter(new PrepareContractionHierarchies.IgnoreNodeFilter(g, g.getNodes() + 1).setAvoidNode(0));
        algo.setWeightLimit(2);
        int endNode = algo.findEndNode(4, 1);
        // did not reach endNode
        assertNotEquals(1, endNode);
    }

    @Test
    public void testAddShortcuts()
    {
        LevelGraph g = createExampleGraph();
        int old = g.getAllEdges().getCount();
        PrepareContractionHierarchies prepare = new PrepareContractionHierarchies(dir, g, carEncoder, weighting, tMode);
        prepare.doWork();
        assertEquals(old + 1, g.getAllEdges().getCount());
    }

    @Test
    public void testMoreComplexGraph()
    {
        LevelGraph g = initShortcutsGraph(createGraph());
        int old = g.getAllEdges().getCount();
        PrepareContractionHierarchies prepare = new PrepareContractionHierarchies(dir, g, carEncoder, weighting, tMode);
        prepare.doWork();
        assertEquals(old + 7, g.getAllEdges().getCount());
    }

    @Test
    public void testDirectedGraph()
    {
        LevelGraph g = createGraph();
        g.edge(5, 4, 3, false);
        g.edge(4, 5, 10, false);
        g.edge(2, 4, 1, false);
        g.edge(5, 2, 1, false);
        g.edge(3, 5, 1, false);
        g.edge(4, 3, 1, false);
        int old = GHUtility.count(g.getAllEdges());
        PrepareContractionHierarchies prepare = new PrepareContractionHierarchies(dir, g, carEncoder, weighting, tMode);
        prepare.doWork();
        assertEquals(old + 2, GHUtility.count(g.getAllEdges()));
        RoutingAlgorithm algo = prepare.createAlgo(g, new AlgorithmOptions(AlgorithmOptions.DIJKSTRA_BI, carEncoder, weighting, tMode));
        Path p = algo.calcPath(4, 2);
        assertEquals(3, p.getDistance(), 1e-6);
        assertEquals(Helper.createTList(4, 3, 5, 2), p.calcNodes());
    }

    @Test
    public void testDirectedGraph2()
    {
        LevelGraph g = createGraph();
        initDirected2(g);
        int old = GHUtility.count(g.getAllEdges());
        PrepareContractionHierarchies prepare = new PrepareContractionHierarchies(dir, g, carEncoder, weighting, tMode);
        prepare.doWork();
        // PrepareTowerNodesShortcutsTest.printEdges(g);
        assertEquals(old + 9, GHUtility.count(g.getAllEdges()));
        RoutingAlgorithm algo = prepare.createAlgo(g, new AlgorithmOptions(AlgorithmOptions.DIJKSTRA_BI, carEncoder, weighting, tMode));
        Path p = algo.calcPath(0, 10);
        assertEquals(10, p.getDistance(), 1e-6);
        assertEquals(Helper.createTList(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10), p.calcNodes());
    }

    @Test
    public void testDirectedGraph3()
    {
        LevelGraph g = createGraph();
        //5 6 7
        // \|/
        //4-3_1<-
        //    \_|_10
        //   0__2_11

        g.edge(0, 2, 2, true);
        g.edge(10, 2, 2, true);
        g.edge(11, 2, 2, true);
        // create a longer one directional edge => no longish one-dir shortcut should be created        
        g.edge(2, 1, 2, true);
        g.edge(2, 1, 10, false);

        g.edge(1, 3, 2, true);
        g.edge(3, 4, 2, true);
        g.edge(3, 5, 2, true);
        g.edge(3, 6, 2, true);
        g.edge(3, 7, 2, true);

        PrepareContractionHierarchies prepare = new PrepareContractionHierarchies(dir, g, carEncoder, weighting, tMode);
        prepare.initFromGraph();
        prepare.prepareNodes();
        // find all shortcuts if we contract node 1
        Collection<Shortcut> scs = prepare.testFindShortcuts(1);
        assertEquals(2, scs.size());
        Iterator<Shortcut> iter = scs.iterator();
        Shortcut sc1 = iter.next();
        Shortcut sc2 = iter.next();
        if (sc1.weight > sc2.weight)
        {
            Shortcut tmp = sc1;
            sc1 = sc2;
            sc2 = tmp;
        }

        // both dirs
        assertTrue(sc1.toString(), sc1.from == 3 && sc1.to == 2);
        assertTrue(sc1.toString(), carEncoder.isForward(sc1.flags) && carEncoder.isBackward(sc1.flags));

        // directed
        assertTrue(sc2.toString(), sc2.from == 2 && sc2.to == 3);
        assertTrue(sc2.toString(), carEncoder.isForward(sc2.flags));

        assertEquals(sc1.toString(), 4, sc1.weight, 1e-4);
        assertEquals(sc2.toString(), 12, sc2.weight, 1e-4);
    }

    void initRoundaboutGraph( Graph g )
    {
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
    public void testRoundaboutUnpacking()
    {
        LevelGraph g = createGraph();
        initRoundaboutGraph(g);
        int old = g.getAllEdges().getCount();
        PrepareContractionHierarchies prepare = new PrepareContractionHierarchies(dir, g, carEncoder, weighting, tMode);
        prepare.doWork();
        assertEquals(old + 23, g.getAllEdges().getCount());
        RoutingAlgorithm algo = prepare.createAlgo(g, new AlgorithmOptions(AlgorithmOptions.DIJKSTRA_BI, carEncoder, weighting, tMode));
        Path p = algo.calcPath(4, 7);
        assertEquals(Helper.createTList(4, 5, 6, 7), p.calcNodes());
    }

    @Test
    public void testFindShortcuts_Roundabout()
    {
        LevelGraphStorage g = (LevelGraphStorage) createGraph();
        EdgeIteratorState iter1_1 = g.edge(1, 3, 1, true);
        EdgeIteratorState iter1_2 = g.edge(3, 4, 1, true);
        EdgeIteratorState iter2_1 = g.edge(4, 5, 1, false);
        EdgeIteratorState iter2_2 = g.edge(5, 6, 1, false);
        EdgeIteratorState iter3_1 = g.edge(6, 7, 1, true);
        EdgeIteratorState iter3_2 = g.edge(6, 8, 2, false);
        g.edge(8, 4, 1, false);

        PrepareContractionHierarchies prepare = new PrepareContractionHierarchies(dir, g, carEncoder, weighting, tMode);
        EdgeSkipIterState tmp = g.shortcut(1, 4);
        tmp.setFlags(PrepareEncoder.getScDirMask());
        tmp.setWeight(2);
        tmp.setSkippedEdges(iter1_1.getEdge(), iter1_2.getEdge());
        long f = PrepareEncoder.getScFwdDir();
        tmp = g.shortcut(4, 6);
        tmp.setFlags(f);
        tmp.setWeight(2);
        tmp.setSkippedEdges(iter2_1.getEdge(), iter2_2.getEdge());
        tmp = g.shortcut(6, 4);
        tmp.setFlags(f);
        tmp.setWeight(3);
        tmp.setSkippedEdges(iter3_1.getEdge(), iter3_2.getEdge());

        prepare.initFromGraph();
        prepare.prepareNodes();
        g.setLevel(3, 3);
        g.setLevel(5, 5);
        g.setLevel(7, 7);
        g.setLevel(8, 8);

        // there should be two different shortcuts for both directions!
        Collection<Shortcut> sc = prepare.testFindShortcuts(4);
        assertEquals(2, sc.size());
    }

    void initUnpackingGraph( LevelGraphStorage g, Weighting w )
    {
        final long flags = carEncoder.setProperties(30, true, false);
        double dist = 1;
        g.edge(10, 0).setDistance(dist).setFlags(flags);
        EdgeIteratorState iterTmp1 = g.edge(0, 1);
        iterTmp1.setDistance(dist).setFlags(flags);
        EdgeIteratorState iter2 = g.edge(1, 2).setDistance(dist).setFlags(flags);
        EdgeIteratorState iter3 = g.edge(2, 3).setDistance(dist).setFlags(flags);
        EdgeIteratorState iter4 = g.edge(3, 4).setDistance(dist).setFlags(flags);
        EdgeIteratorState iter5 = g.edge(4, 5).setDistance(dist).setFlags(flags);
        EdgeIteratorState iter6 = g.edge(5, 6).setDistance(dist).setFlags(flags);
        long oneDirFlags = PrepareEncoder.getScFwdDir();

        int tmp = iterTmp1.getEdge();
        EdgeSkipIterState sc1 = g.shortcut(0, 2);
        int x = EdgeIterator.NO_EDGE;
        sc1.setWeight(w.calcWeight(iterTmp1, false, x) + w.calcWeight(iter2, false, x)).setDistance(2 * dist).setFlags(oneDirFlags);
        sc1.setSkippedEdges(tmp, iter2.getEdge());
        tmp = sc1.getEdge();
        EdgeSkipIterState sc2 = g.shortcut(0, 3);
        sc2.setWeight(w.calcWeight(sc1, false, x) + w.calcWeight(iter3, false, x)).setDistance(3 * dist).setFlags(oneDirFlags);
        sc2.setSkippedEdges(tmp, iter3.getEdge());
        tmp = sc2.getEdge();
        sc1 = g.shortcut(0, 4);
        sc1.setWeight(w.calcWeight(sc2, false, x) + w.calcWeight(iter4, false, x)).setDistance(4).setFlags(oneDirFlags);
        sc1.setSkippedEdges(tmp, iter4.getEdge());
        tmp = sc1.getEdge();
        sc2 = g.shortcut(0, 5);
        sc2.setWeight(w.calcWeight(sc1, false, x) + w.calcWeight(iter5, false, x)).setDistance(5).setFlags(oneDirFlags);
        sc2.setSkippedEdges(tmp, iter5.getEdge());
        tmp = sc2.getEdge();
        sc1 = g.shortcut(0, 6);
        sc1.setWeight(w.calcWeight(sc2, false, x) + w.calcWeight(iter6, false, x)).setDistance(6).setFlags(oneDirFlags);
        sc1.setSkippedEdges(tmp, iter6.getEdge());
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
    public void testUnpackingOrder()
    {
        LevelGraphStorage g = (LevelGraphStorage) createGraph();
        initUnpackingGraph(g, weighting);
        PrepareContractionHierarchies prepare = new PrepareContractionHierarchies(dir, g, carEncoder, weighting, tMode);
        RoutingAlgorithm algo = prepare.createAlgo(g, new AlgorithmOptions(AlgorithmOptions.DIJKSTRA_BI, carEncoder, weighting, tMode));
        Path p = algo.calcPath(10, 6);
        assertEquals(7, p.getDistance(), 1e-5);
        assertEquals(Helper.createTList(10, 0, 1, 2, 3, 4, 5, 6), p.calcNodes());
    }

    @Test
    public void testUnpackingOrder_Fastest()
    {
        LevelGraphStorage g = (LevelGraphStorage) createGraph();
        Weighting w = new FastestWeighting(carEncoder);
        initUnpackingGraph(g, w);

        PrepareContractionHierarchies prepare = new PrepareContractionHierarchies(dir, g, carEncoder, weighting, tMode);
        RoutingAlgorithm algo = prepare.createAlgo(g, new AlgorithmOptions(AlgorithmOptions.DIJKSTRA_BI, carEncoder, weighting, tMode));
        Path p = algo.calcPath(10, 6);
        assertEquals(7, p.getDistance(), 1e-1);
        assertEquals(Helper.createTList(10, 0, 1, 2, 3, 4, 5, 6), p.calcNodes());
    }

    @Test
    public void testCircleBug()
    {
        LevelGraph g = createGraph();
        //  /--1
        // -0--/
        //  |
        g.edge(0, 1, 10, true);
        g.edge(0, 1, 4, true);
        g.edge(0, 2, 10, true);
        g.edge(0, 3, 10, true);
        PrepareContractionHierarchies prepare = new PrepareContractionHierarchies(dir, g, carEncoder, weighting, tMode);
        prepare.doWork();
        assertEquals(0, prepare.getShortcuts());
    }

    @Test
    public void testBug178()
    {
        // 5--------6__
        // |        |  \
        // 0-1->-2--3--4
        //   \-<-/
        //
        LevelGraph g = createGraph();
        g.edge(1, 2, 1, false);
        g.edge(2, 1, 1, false);

        g.edge(5, 0, 1, true);
        g.edge(5, 6, 1, true);
        g.edge(0, 1, 1, true);
        g.edge(2, 3, 1, true);
        g.edge(3, 4, 1, true);
        g.edge(6, 3, 1, true);

        PrepareContractionHierarchies prepare = new PrepareContractionHierarchies(dir, g, carEncoder, weighting, tMode);
        prepare.doWork();
        assertEquals(2, prepare.getShortcuts());
    }

    // 0-1-2-3-4
    // |     / |
    // |    8  |
    // \   /   /
    //  7-6-5-/
    void initBiGraph( Graph graph )
    {
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

    // 0-1-.....-9-10
    // |         ^   \
    // |         |    |
    // 17-16-...-11<-/
    public static void initDirected2( Graph g )
    {
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
    public static void initDirected1( Graph g )
    {
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
    public static LevelGraph initShortcutsGraph( LevelGraph g )
    {
        g.edge(0, 1, 1, true);
        g.edge(0, 2, 1, true);
        g.edge(1, 2, 1, true);
        g.edge(2, 3, 1, true);
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

//    public static void printEdges(LevelGraph g) {
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
    public void testBits()
    {
        int fromNode = Integer.MAX_VALUE / 3 * 2;
        int endNode = Integer.MAX_VALUE / 37 * 17;

        long edgeId = (long) fromNode << 32 | endNode;
        assertEquals((BitUtil.BIG.toBitString(edgeId)),
                BitUtil.BIG.toLastBitString(fromNode, 32) + BitUtil.BIG.toLastBitString(endNode, 32));
    }
}
