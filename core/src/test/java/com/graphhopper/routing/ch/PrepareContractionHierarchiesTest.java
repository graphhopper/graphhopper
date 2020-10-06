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
import com.graphhopper.routing.Dijkstra;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.RoutingAlgorithm;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.util.*;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.routing.weighting.ShortestWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.*;
import com.graphhopper.storage.index.Snap;
import com.graphhopper.util.*;
import org.junit.Before;
import org.junit.Test;

import java.util.*;

import static com.graphhopper.util.GHUtility.updateDistancesFor;
import static org.junit.Assert.*;

/**
 * @author Peter Karich
 */
public class PrepareContractionHierarchiesTest {
    private final CarFlagEncoder carEncoder = new CarFlagEncoder().setSpeedTwoDirections(true);
    private final EncodingManager encodingManager = EncodingManager.create(carEncoder);
    private final Weighting weighting = new ShortestWeighting(carEncoder);
    private final CHConfig chConfig = CHConfig.nodeBased("c", weighting);
    private GraphHopperStorage g;
    private CHGraph lg;
    private RoutingCHGraph routingCHGraph;

    // 0-1-.....-9-10
    // |         ^   \
    // |         |    |
    // 17-16-...-11<-/
    private static void initDirected2(Graph g) {
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

    // prepare-routing.svg
    private static void initShortcutsGraph(Graph g) {
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
    }

    private static void initExampleGraph(Graph g) {
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
    }

    @Before
    public void setUp() {
        g = createGHStorage();
        lg = g.getCHGraph();
        routingCHGraph = new RoutingCHGraphImpl(lg);
    }

    private GraphHopperStorage createGHStorage() {
        return createGHStorage(chConfig);
    }

    private GraphHopperStorage createGHStorage(CHConfig c) {
        return new GraphBuilder(encodingManager).setCHConfigs(c).create();
    }

    @Test
    public void testReturnsCorrectWeighting() {
        PrepareContractionHierarchies prepare = createPrepareContractionHierarchies(g);
        prepare.doWork();
        assertSame(weighting, prepare.getWeighting());
    }

    @Test
    public void testAddShortcuts() {
        initExampleGraph(g);
        int old = routingCHGraph.getEdges();
        PrepareContractionHierarchies prepare = createPrepareContractionHierarchies(g);
        useNodeOrdering(prepare, new int[]{5, 3, 4, 0, 1, 2});
        prepare.doWork();
        assertEquals(old + 2, routingCHGraph.getEdges());
    }

    @Test
    public void testMoreComplexGraph() {
        initShortcutsGraph(g);
        int oldCount = routingCHGraph.getEdges();
        PrepareContractionHierarchies prepare = createPrepareContractionHierarchies(g);
        useNodeOrdering(prepare, new int[]{0, 5, 6, 7, 8, 10, 11, 13, 15, 1, 3, 9, 14, 16, 12, 4, 2});
        prepare.doWork();
        assertEquals(oldCount, g.getEdges());
        assertEquals(oldCount + 7, routingCHGraph.getEdges());
    }

    @Test
    public void testDirectedGraph() {
        g.edge(5, 4, 3, false);
        g.edge(4, 5, 10, false);
        g.edge(2, 4, 1, false);
        g.edge(5, 2, 1, false);
        g.edge(3, 5, 1, false);
        g.edge(4, 3, 1, false);
        g.freeze();
        int oldCount = routingCHGraph.getEdges();
        assertEquals(6, oldCount);
        PrepareContractionHierarchies prepare = createPrepareContractionHierarchies(g);
        prepare.doWork();
        assertEquals(2, prepare.getShortcuts());
        assertEquals(oldCount, g.getEdges());
        assertEquals(oldCount + 2, routingCHGraph.getEdges());
        RoutingAlgorithm algo = new CHRoutingAlgorithmFactory(routingCHGraph).createAlgo(new PMap());
        Path p = algo.calcPath(4, 2);
        assertEquals(3, p.getDistance(), 1e-6);
        assertEquals(IntArrayList.from(4, 3, 5, 2), p.calcNodes());
    }

    @Test
    public void testDirectedGraph2() {
        initDirected2(g);
        int oldCount = g.getEdges();
        assertEquals(19, oldCount);
        PrepareContractionHierarchies prepare = createPrepareContractionHierarchies(g);
        useNodeOrdering(prepare, new int[]{10, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 17, 11, 12, 13, 14, 15, 16});
        prepare.doWork();
        assertEquals(oldCount, g.getEdges());
        assertEquals(oldCount, GHUtility.count(g.getAllEdges()));

        long numShortcuts = 9;
        assertEquals(numShortcuts, prepare.getShortcuts());
        assertEquals(oldCount, g.getEdges());
        assertEquals(oldCount + numShortcuts, routingCHGraph.getEdges());
        assertEquals(oldCount + numShortcuts, GHUtility.count(lg.getAllEdges()));
        RoutingAlgorithm algo = new CHRoutingAlgorithmFactory(routingCHGraph).createAlgo(new PMap());
        Path p = algo.calcPath(0, 10);
        assertEquals(10, p.getDistance(), 1e-6);
        assertEquals(IntArrayList.from(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10), p.calcNodes());
    }

    private void initRoundaboutGraph(Graph g) {
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
        initRoundaboutGraph(g);
        int oldCount = g.getEdges();
        PrepareContractionHierarchies prepare = createPrepareContractionHierarchies(g);
        useNodeOrdering(prepare, new int[]{26, 6, 12, 13, 2, 3, 8, 9, 10, 11, 14, 15, 16, 17, 18, 20, 21, 23, 24, 25, 19, 22, 27, 5, 29, 30, 31, 28, 7, 1, 0, 4});
        prepare.doWork();
        assertEquals(oldCount, g.getEdges());
        assertEquals(oldCount, routingCHGraph.getBaseGraph().getEdges());
        assertEquals(oldCount + 23, routingCHGraph.getEdges());
        RoutingAlgorithm algo = new CHRoutingAlgorithmFactory(routingCHGraph).createAlgo(new PMap());
        Path p = algo.calcPath(4, 7);
        assertEquals(IntArrayList.from(4, 5, 6, 7), p.calcNodes());
    }

    private void initUnpackingGraph(GraphHopperStorage g, CHGraph lg, Weighting w) {
        final IntsRef edgeFlags = encodingManager.createEdgeFlags();
        carEncoder.getAccessEnc().setBool(false, edgeFlags, true);
        carEncoder.getAccessEnc().setBool(true, edgeFlags, false);
        carEncoder.getAverageSpeedEnc().setDecimal(false, edgeFlags, 30.0);
        double dist = 1;
        g.edge(10, 0).setDistance(dist).setFlags(edgeFlags);
        EdgeIteratorState edgeState01 = g.edge(0, 1);
        edgeState01.setDistance(dist).setFlags(edgeFlags);
        EdgeIteratorState edgeState12 = g.edge(1, 2).setDistance(dist).setFlags(edgeFlags);
        EdgeIteratorState edgeState23 = g.edge(2, 3).setDistance(dist).setFlags(edgeFlags);
        EdgeIteratorState edgeState34 = g.edge(3, 4).setDistance(dist).setFlags(edgeFlags);
        EdgeIteratorState edgeState45 = g.edge(4, 5).setDistance(dist).setFlags(edgeFlags);
        EdgeIteratorState edgeState56 = g.edge(5, 6).setDistance(dist).setFlags(edgeFlags);
        int oneDirFlags = PrepareEncoder.getScFwdDir();

        int tmpEdgeId = edgeState01.getEdge();
        g.freeze();
        double weight = w.calcEdgeWeight(edgeState01, false) + w.calcEdgeWeight(edgeState12, false);
        int sc0_2 = lg.shortcut(0, 2, oneDirFlags, w.calcEdgeWeight(edgeState01, false) + w.calcEdgeWeight(edgeState12, false), tmpEdgeId, edgeState12.getEdge());

        tmpEdgeId = sc0_2;
        weight += w.calcEdgeWeight(edgeState23, false);
        int sc0_3 = lg.shortcut(0, 3, oneDirFlags, weight, tmpEdgeId, edgeState23.getEdge());

        tmpEdgeId = sc0_3;
        weight += w.calcEdgeWeight(edgeState34, false);
        int sc0_4 = lg.shortcut(0, 4, oneDirFlags, weight, tmpEdgeId, edgeState34.getEdge());

        tmpEdgeId = sc0_4;
        weight += w.calcEdgeWeight(edgeState45, false);
        int sc0_5 = lg.shortcut(0, 5, oneDirFlags, weight, tmpEdgeId, edgeState45.getEdge());

        tmpEdgeId = sc0_5;
        weight += w.calcEdgeWeight(edgeState56, false);
        int sc0_6 = lg.shortcut(0, 6, oneDirFlags, weight, tmpEdgeId, edgeState56.getEdge());

        lg.setLevel(0, 10);
        lg.setLevel(6, 9);
        lg.setLevel(5, 8);
        lg.setLevel(4, 7);
        lg.setLevel(3, 6);
        lg.setLevel(2, 5);
        lg.setLevel(1, 4);
        lg.setLevel(10, 3);
    }

    @Test
    public void testUnpackingOrder() {
        initUnpackingGraph(g, lg, weighting);
        createPrepareContractionHierarchies(g);
        // do not call prepare.doWork() here
        RoutingAlgorithm algo = new CHRoutingAlgorithmFactory(routingCHGraph).createAlgo(new PMap());
        Path p = algo.calcPath(10, 6);
        assertEquals(7, p.getDistance(), 1e-5);
        assertEquals(IntArrayList.from(10, 0, 1, 2, 3, 4, 5, 6), p.calcNodes());
    }

    @Test
    public void testUnpackingOrder_Fastest() {
        Weighting w = new FastestWeighting(carEncoder);
        initUnpackingGraph(g, lg, w);

        createPrepareContractionHierarchies(g);
        // do not call prepare.doWork() here
        RoutingAlgorithm algo = new CHRoutingAlgorithmFactory(routingCHGraph).createAlgo(new PMap());
        Path p = algo.calcPath(10, 6);
        assertEquals(7, p.getDistance(), 1e-1);
        assertEquals(IntArrayList.from(10, 0, 1, 2, 3, 4, 5, 6), p.calcNodes());
    }

    @Test
    public void testDisconnects() {
        //            4
        //            v
        //            0
        //            v
        //  8 -> 3 -> 6 -> 1 -> 5
        //            v
        //            2
        //            v
        //            7
        g.edge(8, 3, 1, false);
        g.edge(3, 6, 1, false);
        g.edge(6, 1, 1, false);
        g.edge(1, 5, 1, false);
        g.edge(4, 0, 1, false);
        g.edge(0, 6, 1, false);
        g.edge(6, 2, 1, false);
        g.edge(2, 7, 1, false);
        g.freeze();

        PrepareContractionHierarchies prepare = createPrepareContractionHierarchies(g)
                .useFixedNodeOrdering(new NodeOrderingProvider() {
                    @Override
                    public int getNodeIdForLevel(int level) {
                        return level;
                    }

                    @Override
                    public int getNumNodes() {
                        return g.getNodes();
                    }
                });
        prepare.doWork();
        CHEdgeExplorer explorer = lg.createEdgeExplorer();
        // shortcuts (and edges) leading to or coming from lower level nodes should be disconnected
        // so far we are only disconnecting shortcuts however, see comments in CHGraphImpl.
        assertEquals(buildSet(7, 8, 0, 1, 2, 3), GHUtility.getNeighbors(explorer.setBaseNode(6)));
        assertEquals(buildSet(6, 0), GHUtility.getNeighbors(explorer.setBaseNode(4)));
        assertEquals(buildSet(6, 1), GHUtility.getNeighbors(explorer.setBaseNode(5)));
        assertEquals(buildSet(8, 2), GHUtility.getNeighbors(explorer.setBaseNode(7)));
        assertEquals(buildSet(3), GHUtility.getNeighbors(explorer.setBaseNode(8)));
    }

    private Set<Integer> buildSet(Integer... values) {
        return new HashSet<>(Arrays.asList(values));
    }

    @Test
    public void testStallOnDemandViaVirtuaNode_issue1574() {
        // this test reproduces the issue that appeared in issue1574
        // the problem is very intricate and a combination of all these things:
        // * contraction hierarchies
        // * stall-on-demand (without sod there is no problem, at least in this test)
        // * shortcuts weight rounding
        // * via nodes/virtual edges and the associated weight precision (without virtual nodes between source and target
        //   there is no problem, but this can happen for via routes
        // * the fact that the CHLevelEdgeFilter always accepts virtual nodes
        // here we will construct a special case where a connection is not found without the fix in #1574.

        // use fastest weighting in this test to be able to fine-tune some weights via the speed (see below)
        Weighting fastestWeighting = new FastestWeighting(carEncoder);
        CHConfig chConfig = CHConfig.nodeBased("c", fastestWeighting);
        g = createGHStorage(chConfig);
        lg = g.getCHGraph("c");
        routingCHGraph = g.getRoutingCHGraph("c");
        // the following graph reproduces the issue. note that we will use the node ids as ch levels, so there will
        // be a shortcut 3->2 visible at node 2 and another one 3->4 visible at node 3.
        // we will fine-tune the edge-speeds such that without the fix node 4 will be stalled and node 5 will not get
        // discovered. consequently, no path will be found, because only the forward search runs (from 0 to 7 the
        // shortest path is strictly upward). node 4 is only stalled when node 2 gets stalled before, which in turn will
        // happen due to the the virtual node between 3 and 1.
        //
        // start 0 - 3 - x - 1 - 2
        //             \         |
        //               sc ---- 4 - 5 - 6 - 7 finish
        g.edge(0, 3, 1, true);
        EdgeIteratorState edge31 = g.edge(3, 1, 1, true);
        g.edge(1, 2, 1, true);
        EdgeIteratorState edge24 = g.edge(2, 4, 1, true);
        g.edge(4, 5, 1, true);
        g.edge(5, 6, 1, true);
        g.edge(6, 7, 1, true);
        updateDistancesFor(g, 0, 0.001, 0.0000);
        updateDistancesFor(g, 3, 0.001, 0.0001);
        updateDistancesFor(g, 1, 0.001, 0.0002);
        updateDistancesFor(g, 2, 0.001, 0.0003);
        updateDistancesFor(g, 4, 0.000, 0.0003);
        updateDistancesFor(g, 5, 0.000, 0.0004);
        updateDistancesFor(g, 6, 0.000, 0.0005);
        updateDistancesFor(g, 7, 0.000, 0.0006);

        // we use the speed to fine tune some weights:
        // the weight of edge 3-1 is chosen such that node 2 gets stalled in the forward search via the incoming shortcut
        // at node 2 coming from 3. this happens because due to the virtual node x between 3 and 1, the weight of the
        // spt entry at 2 is different to the sum of the weights of the spt entry at node 3 and the shortcut edge. this
        // is due to different floating point rounding arithmetic of shortcuts and virtual edges on the query graph.
        edge31.set(carEncoder.getAverageSpeedEnc(), 22);
        edge31.setReverse(carEncoder.getAverageSpeedEnc(), 22);

        // just stalling node 2 alone would not lead to connection not found, because the shortcut 3-4 still finds node
        // 4. however, we can choose the weight of edge 2-4 such that node 4 also gets stalled via node 2.
        // it is important that node 2 gets stalled before otherwise node 4 would have already be discovered.
        // note that without the virtual node between 3 and 1 node 2 would not even be explored in the forward search,
        // but because of the virtual node the strict upward search is modified and goes like 0-3-x-1-2.
        edge24.set(carEncoder.getAverageSpeedEnc(), 27.5);
        edge24.setReverse(carEncoder.getAverageSpeedEnc(), 27.5);

        // prepare ch, use node ids as levels
        PrepareContractionHierarchies pch = createPrepareContractionHierarchies(g, chConfig);
        pch.useFixedNodeOrdering(new NodeOrderingProvider() {
            @Override
            public int getNodeIdForLevel(int level) {
                return level;
            }

            @Override
            public int getNumNodes() {
                return g.getNodes();
            }
        }).doWork();
        assertEquals("there should be exactly two (bidirectional) shortcuts (2-3) and (3-4)", 2, lg.getEdges() - lg.getOriginalEdges());

        // insert virtual node and edges
        Snap snap = new Snap(0.0001, 0.0015);
        snap.setClosestEdge(edge31);
        snap.setSnappedPosition(Snap.Position.EDGE);
        snap.setClosestNode(8);
        snap.setWayIndex(0);
        snap.calcSnappedPoint(new DistanceCalcEuclidean());

        QueryGraph queryGraph = QueryGraph.create(g, snap);

        // we make sure our weight fine tunings do what they are supposed to
        double weight03 = getWeight(queryGraph, fastestWeighting, 0, 3, false);
        double scWeight23 = weight03 + getEdge(lg, 2, 3, true).getWeight();
        double scWeight34 = weight03 + getEdge(lg, 3, 4, false).getWeight();
        double sptWeight2 = weight03 + getWeight(queryGraph, fastestWeighting, 3, 8, false) + getWeight(queryGraph, fastestWeighting, 8, 1, false) + getWeight(queryGraph, fastestWeighting, 1, 2, false);
        double sptWeight4 = sptWeight2 + getWeight(queryGraph, fastestWeighting, 2, 4, false);
        assertTrue("incoming shortcut weight 3->2 should be smaller than sptWeight at node 2 to make sure 2 gets stalled", scWeight23 < sptWeight2);
        assertTrue("sptWeight at node 4 should be smaller than shortcut weight 3->4 to make sure node 4 gets stalled", sptWeight4 < scWeight34);

        Path path = new CHRoutingAlgorithmFactory(routingCHGraph, queryGraph).createAlgo(new PMap()).calcPath(0, 7);
        assertEquals("wrong or no path found", IntArrayList.from(0, 3, 8, 1, 2, 4, 5, 6, 7), path.calcNodes());
    }

    private double getWeight(Graph graph, Weighting w, int from, int to, boolean incoming) {
        return w.calcEdgeWeight(getEdge(graph, from, to, false), incoming);
    }

    private EdgeIteratorState getEdge(Graph graph, int from, int to, boolean incoming) {
        EdgeFilter filter = incoming ? DefaultEdgeFilter.inEdges(carEncoder) : DefaultEdgeFilter.outEdges(carEncoder);
        EdgeIterator iter = graph.createEdgeExplorer(filter).setBaseNode(from);
        while (iter.next()) {
            if (iter.getAdjNode() == to) {
                return iter;
            }
        }
        throw new IllegalArgumentException("Could not find edge from: " + from + " to: " + to);
    }

    private CHEdgeIteratorState getEdge(CHGraph graph, int from, int to, boolean incoming) {
        EdgeFilter filter = incoming ? DefaultEdgeFilter.inEdges(carEncoder) : DefaultEdgeFilter.outEdges(carEncoder);
        CHEdgeIterator iter = graph.createEdgeExplorer(filter).setBaseNode(from);
        while (iter.next()) {
            if (iter.getAdjNode() == to) {
                return iter;
            }
        }
        throw new IllegalArgumentException("Could not find edge from: " + from + " to: " + to);
    }

    @Test
    public void testCircleBug() {
        //  /--1
        // -0--/
        //  |
        g.edge(0, 1, 10, true);
        g.edge(0, 1, 4, true);
        g.edge(0, 2, 10, true);
        g.edge(0, 3, 10, true);
        PrepareContractionHierarchies prepare = createPrepareContractionHierarchies(g);
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
        g.edge(1, 2, 1, false);
        g.edge(2, 1, 1, false);

        g.edge(5, 0, 1, true);
        g.edge(5, 6, 1, true);
        g.edge(0, 1, 1, true);
        g.edge(2, 3, 1, true);
        g.edge(3, 4, 1, true);
        g.edge(6, 3, 1, true);

        PrepareContractionHierarchies prepare = createPrepareContractionHierarchies(g);
        useNodeOrdering(prepare, new int[]{4, 1, 2, 0, 5, 6, 3});
        prepare.doWork();
        assertEquals(2, prepare.getShortcuts());
    }

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
        EncodingManager tmpEncodingManager = EncodingManager.create(tmpCarEncoder, tmpBikeEncoder);

        // FastestWeighting would lead to different shortcuts due to different default speeds for bike and car
        CHConfig carProfile = CHConfig.nodeBased("c1", new ShortestWeighting(tmpCarEncoder));
        CHConfig bikeProfile = CHConfig.nodeBased("c2", new ShortestWeighting(tmpBikeEncoder));

        List<CHConfig> configs = Arrays.asList(carProfile, bikeProfile);
        GraphHopperStorage ghStorage = new GraphBuilder(tmpEncodingManager).setCHConfigs(configs).create();
        initShortcutsGraph(ghStorage);

        ghStorage.freeze();

        checkPath(ghStorage, carProfile, 7, 5, IntArrayList.from(3, 9, 14, 16, 13, 12), new int[]{0, 5, 6, 7, 8, 10, 11, 13, 15, 1, 3, 9, 14, 16, 12, 4, 2});
        checkPath(ghStorage, bikeProfile, 7, 5, IntArrayList.from(3, 9, 14, 16, 13, 12), new int[]{0, 5, 6, 7, 8, 10, 11, 13, 15, 1, 3, 9, 14, 16, 12, 4, 2});
    }

    @Test
    public void testMultiplePreparationsDifferentView() {
        CarFlagEncoder tmpCarEncoder = new CarFlagEncoder();
        BikeFlagEncoder tmpBikeEncoder = new BikeFlagEncoder();
        EncodingManager tmpEncodingManager = EncodingManager.create(tmpCarEncoder, tmpBikeEncoder);

        CHConfig carConfig = CHConfig.nodeBased("c1", new FastestWeighting(tmpCarEncoder));
        CHConfig bikeConfig = CHConfig.nodeBased("c2", new FastestWeighting(tmpBikeEncoder));

        GraphHopperStorage ghStorage = new GraphBuilder(tmpEncodingManager).setCHConfigs(carConfig, bikeConfig).create();
        initShortcutsGraph(ghStorage);
        GHUtility.getEdge(ghStorage, 9, 14).
                set(tmpBikeEncoder.getAccessEnc(), false).
                setReverse(tmpBikeEncoder.getAccessEnc(), false);

        ghStorage.freeze();

        checkPath(ghStorage, carConfig, 7, 5, IntArrayList.from(3, 9, 14, 16, 13, 12), new int[]{0, 5, 6, 7, 8, 10, 11, 13, 15, 1, 3, 9, 14, 16, 12, 4, 2});
        // detour around blocked 9,14
        checkPath(ghStorage, bikeConfig, 9, 5, IntArrayList.from(3, 10, 14, 16, 13, 12), new int[]{0, 5, 6, 7, 8, 10, 11, 13, 14, 15, 9, 1, 4, 3, 2, 12, 16});
    }

    @Test
    public void testReusingNodeOrdering() {
        CarFlagEncoder carFlagEncoder = new CarFlagEncoder();
        MotorcycleFlagEncoder motorCycleEncoder = new MotorcycleFlagEncoder();
        EncodingManager em = EncodingManager.create(carFlagEncoder, motorCycleEncoder);
        CHConfig carConfig = CHConfig.nodeBased("c1", new FastestWeighting(carFlagEncoder));
        CHConfig motorCycleConfig = CHConfig.nodeBased("c2", new FastestWeighting(motorCycleEncoder));
        GraphHopperStorage ghStorage = new GraphBuilder(em).setCHConfigs(carConfig, motorCycleConfig).create();

        int numNodes = 5_000;
        int numQueries = 100;
        long seed = System.nanoTime();
        Random rnd = new Random(seed);
        GHUtility.buildRandomGraph(ghStorage, rnd, numNodes, 1.3, true, true, carFlagEncoder.getAverageSpeedEnc(), 0.7, 0.9, 0.8);
        ghStorage.freeze();

        // create CH for cars
        StopWatch sw = new StopWatch().start();
        PrepareContractionHierarchies carPch = PrepareContractionHierarchies.fromGraphHopperStorage(ghStorage, carConfig);
        carPch.doWork();
        CHGraph carCH = ghStorage.getCHGraph(carConfig.getName());
        long timeCar = sw.stop().getMillis();

        // create CH for motorcycles, re-use car contraction order
        // this speeds up contraction significantly, but can lead to slower queries
        sw = new StopWatch().start();
        NodeOrderingProvider nodeOrderingProvider = carCH.getNodeOrderingProvider();
        PrepareContractionHierarchies motorCyclePch = PrepareContractionHierarchies.fromGraphHopperStorage(ghStorage, motorCycleConfig)
                .useFixedNodeOrdering(nodeOrderingProvider);
        motorCyclePch.doWork();
        RoutingCHGraph motorCycleCH = ghStorage.getRoutingCHGraph(motorCycleConfig.getName());

        // run a few sample queries to check correctness
        for (int i = 0; i < numQueries; ++i) {
            Dijkstra dijkstra = new Dijkstra(ghStorage, motorCycleConfig.getWeighting(), TraversalMode.NODE_BASED);
            RoutingAlgorithm chAlgo = new CHRoutingAlgorithmFactory(motorCycleCH).createAlgo(new PMap());

            int from = rnd.nextInt(numNodes);
            int to = rnd.nextInt(numNodes);
            double dijkstraWeight = dijkstra.calcPath(from, to).getWeight();
            double chWeight = chAlgo.calcPath(from, to).getWeight();
            assertEquals(dijkstraWeight, chWeight, 1.e-1);
        }
        long timeMotorCycle = sw.getMillis();

        assertTrue("reusing node ordering should speed up ch contraction", timeMotorCycle < 0.5 * timeCar);
    }

    private void checkPath(GraphHopperStorage g, CHConfig c, int expShortcuts, double expDistance, IntIndexedContainer expNodes, int[] nodeOrdering) {
        RoutingCHGraph lg = g.getRoutingCHGraph(c.getName());
        PrepareContractionHierarchies prepare = createPrepareContractionHierarchies(g, c);
        useNodeOrdering(prepare, nodeOrdering);
        prepare.doWork();
        assertEquals(c.toString(), expShortcuts, prepare.getShortcuts());
        RoutingAlgorithm algo = new CHRoutingAlgorithmFactory(lg).createAlgo(new PMap());
        Path path = algo.calcPath(3, 12);
        assertEquals(path.toString(), expDistance, path.getDistance(), 1e-5);
        assertEquals(path.toString(), expNodes, path.calcNodes());
    }

    private PrepareContractionHierarchies createPrepareContractionHierarchies(GraphHopperStorage g) {
        return createPrepareContractionHierarchies(g, chConfig);
    }

    private PrepareContractionHierarchies createPrepareContractionHierarchies(GraphHopperStorage g, CHConfig p) {
        g.freeze();
        return PrepareContractionHierarchies.fromGraphHopperStorage(g, p);
    }

    private void useNodeOrdering(PrepareContractionHierarchies prepare, int[] nodeOrdering) {
        prepare.useFixedNodeOrdering(new NodeOrderingProvider() {
            @Override
            public int getNodeIdForLevel(int level) {
                return nodeOrdering[level];
            }

            @Override
            public int getNumNodes() {
                return nodeOrdering.length;
            }
        });
    }

}
