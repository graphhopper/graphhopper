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
import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.util.*;
import com.graphhopper.routing.weighting.SpeedWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.*;
import com.graphhopper.storage.index.Snap;
import com.graphhopper.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static com.graphhopper.util.GHUtility.updateDistancesFor;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Peter Karich
 */
public class PrepareContractionHierarchiesTest {
    private final DecimalEncodedValue speedEnc = new DecimalEncodedValueImpl("speed", 5, 5, true);
    private final EncodingManager encodingManager = EncodingManager.start().add(speedEnc).build();
    private final Weighting weighting = new SpeedWeighting(speedEnc);
    private final CHConfig chConfig = CHConfig.nodeBased("c", weighting);
    private BaseGraph g;

    // 0-1-.....-9-10
    // |         ^   \
    // |         |    |
    // 17-16-...-11<-/
    private static void initDirected2(Graph g, DecimalEncodedValue speedEnc) {
        g.edge(0, 1).setDistance(100).set(speedEnc, 60, 60);
        g.edge(1, 2).setDistance(100).set(speedEnc, 60, 60);
        g.edge(2, 3).setDistance(100).set(speedEnc, 60, 60);
        g.edge(3, 4).setDistance(100).set(speedEnc, 60, 60);
        g.edge(4, 5).setDistance(100).set(speedEnc, 60, 60);
        g.edge(5, 6).setDistance(100).set(speedEnc, 60, 60);
        g.edge(6, 7).setDistance(100).set(speedEnc, 60, 60);
        g.edge(7, 8).setDistance(100).set(speedEnc, 60, 60);
        g.edge(8, 9).setDistance(100).set(speedEnc, 60, 60);
        g.edge(9, 10).setDistance(100).set(speedEnc, 60, 60);
        g.edge(10, 11).setDistance(100).set(speedEnc, 60, 0);
        g.edge(11, 12).setDistance(100).set(speedEnc, 60, 60);
        g.edge(11, 9).setDistance(300).set(speedEnc, 60, 0);
        g.edge(12, 13).setDistance(100).set(speedEnc, 60, 60);
        g.edge(13, 14).setDistance(100).set(speedEnc, 60, 60);
        g.edge(14, 15).setDistance(100).set(speedEnc, 60, 60);
        g.edge(15, 16).setDistance(100).set(speedEnc, 60, 60);
        g.edge(16, 17).setDistance(100).set(speedEnc, 60, 60);
        g.edge(17, 0).setDistance(100).set(speedEnc, 60, 60);
    }

    // prepare-routing.svg
    private static void initShortcutsGraph(Graph g, DecimalEncodedValue speedEnc) {
        g.edge(0, 1).setDistance(100).set(speedEnc, 60, 60);
        g.edge(0, 2).setDistance(100).set(speedEnc, 60, 60);
        g.edge(1, 2).setDistance(100).set(speedEnc, 60, 60);
        g.edge(2, 3).setDistance(150).set(speedEnc, 60, 60);
        g.edge(1, 4).setDistance(100).set(speedEnc, 60, 60);
        g.edge(2, 9).setDistance(100).set(speedEnc, 60, 60);
        g.edge(9, 3).setDistance(100).set(speedEnc, 60, 60);
        g.edge(10, 3).setDistance(100).set(speedEnc, 60, 60);
        g.edge(4, 5).setDistance(100).set(speedEnc, 60, 60);
        g.edge(5, 6).setDistance(100).set(speedEnc, 60, 60);
        g.edge(6, 7).setDistance(100).set(speedEnc, 60, 60);
        g.edge(7, 8).setDistance(100).set(speedEnc, 60, 60);
        g.edge(8, 9).setDistance(100).set(speedEnc, 60, 60);
        g.edge(4, 11).setDistance(100).set(speedEnc, 60, 60);
        g.edge(9, 14).setDistance(100).set(speedEnc, 60, 60);
        g.edge(10, 14).setDistance(100).set(speedEnc, 60, 60);
        g.edge(11, 12).setDistance(100).set(speedEnc, 60, 60);
        g.edge(12, 15).setDistance(100).set(speedEnc, 60, 60);
        g.edge(12, 13).setDistance(100).set(speedEnc, 60, 60);
        g.edge(13, 16).setDistance(100).set(speedEnc, 60, 60);
        g.edge(15, 16).setDistance(200).set(speedEnc, 60, 60);
        g.edge(14, 16).setDistance(100).set(speedEnc, 60, 60);
    }

    private static void initExampleGraph(Graph g, DecimalEncodedValue speedEnc) {
        //5-1-----2
        //   \ __/|
        //    0   |
        //   /    |
        //  4-----3
        //
        g.edge(0, 1).setDistance(100).set(speedEnc, 60, 60);
        g.edge(0, 2).setDistance(100).set(speedEnc, 60, 60);
        g.edge(0, 4).setDistance(300).set(speedEnc, 60, 60);
        g.edge(1, 2).setDistance(300).set(speedEnc, 60, 60);
        g.edge(2, 3).setDistance(100).set(speedEnc, 60, 60);
        g.edge(4, 3).setDistance(200).set(speedEnc, 60, 60);
        g.edge(5, 1).setDistance(200).set(speedEnc, 60, 60);
    }

    @BeforeEach
    public void setUp() {
        g = createGraph();
    }

    private BaseGraph createGraph() {
        return new BaseGraph.Builder(encodingManager).create();
    }

    @Test
    public void testReturnsCorrectWeighting() {
        PrepareContractionHierarchies prepare = createPrepareContractionHierarchies(g);
        prepare.doWork();
        assertSame(weighting, prepare.getCHConfig().getWeighting());
    }

    @Test
    public void testAddShortcuts() {
        initExampleGraph(g, speedEnc);
        PrepareContractionHierarchies prepare = createPrepareContractionHierarchies(g);
        useNodeOrdering(prepare, new int[]{5, 3, 4, 0, 1, 2});
        PrepareContractionHierarchies.Result res = prepare.doWork();
        assertEquals(2, res.getShortcuts());
    }

    @Test
    public void testMoreComplexGraph() {
        initShortcutsGraph(g, speedEnc);
        PrepareContractionHierarchies prepare = createPrepareContractionHierarchies(g);
        useNodeOrdering(prepare, new int[]{0, 5, 6, 7, 8, 10, 11, 13, 15, 1, 3, 9, 14, 16, 12, 4, 2});
        PrepareContractionHierarchies.Result res = prepare.doWork();
        assertEquals(7, res.getShortcuts());
    }

    @Test
    public void testDirectedGraph() {
        g.edge(5, 4).setDistance(300).set(speedEnc, 60, 0);
        g.edge(4, 5).setDistance(1000).set(speedEnc, 60, 0);
        g.edge(2, 4).setDistance(100).set(speedEnc, 60, 0);
        g.edge(5, 2).setDistance(100).set(speedEnc, 60, 0);
        g.edge(3, 5).setDistance(100).set(speedEnc, 60, 0);
        g.edge(4, 3).setDistance(100).set(speedEnc, 60, 0);
        g.freeze();
        assertEquals(6, g.getEdges());
        PrepareContractionHierarchies prepare = createPrepareContractionHierarchies(g);
        PrepareContractionHierarchies.Result result = prepare.doWork();
        assertEquals(2, result.getShortcuts());
        RoutingCHGraph routingCHGraph = RoutingCHGraphImpl.fromGraph(g, result.getCHStorage(), result.getCHConfig());
        assertEquals(6 + 2, routingCHGraph.getEdges());
        RoutingAlgorithm algo = new CHRoutingAlgorithmFactory(routingCHGraph).createAlgo(new PMap());
        Path p = algo.calcPath(4, 2);
        assertEquals(300, p.getDistance(), 1e-6);
        assertEquals(IntArrayList.from(4, 3, 5, 2), p.calcNodes());
    }

    @Test
    public void testDirectedGraph2() {
        initDirected2(g, speedEnc);
        int oldCount = g.getEdges();
        assertEquals(19, oldCount);
        PrepareContractionHierarchies prepare = createPrepareContractionHierarchies(g);
        useNodeOrdering(prepare, new int[]{10, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 17, 11, 12, 13, 14, 15, 16});
        PrepareContractionHierarchies.Result result = prepare.doWork();
        assertEquals(oldCount, g.getEdges());
        assertEquals(oldCount, GHUtility.count(g.getAllEdges()));

        long numShortcuts = 9;
        assertEquals(numShortcuts, result.getShortcuts());
        assertEquals(oldCount, g.getEdges());
        RoutingCHGraph routingCHGraph = RoutingCHGraphImpl.fromGraph(g, result.getCHStorage(), result.getCHConfig());
        assertEquals(oldCount + numShortcuts, routingCHGraph.getEdges());
        RoutingAlgorithm algo = new CHRoutingAlgorithmFactory(routingCHGraph).createAlgo(new PMap());
        Path p = algo.calcPath(0, 10);
        assertEquals(1000, p.getDistance(), 1e-6);
        assertEquals(IntArrayList.from(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10), p.calcNodes());
    }

    private static void initRoundaboutGraph(Graph g, DecimalEncodedValue speedEnc) {
        //              roundabout:
        //16-0-9-10--11   12<-13
        //    \       \  /      \
        //    17       \|        7-8-..
        // -15-1--2--3--4       /     /
        //     /         \-5->6/     /
        //  -14            \________/
        g.edge(16, 0).setDistance(100).set(speedEnc, 60, 60);
        g.edge(0, 9).setDistance(100).set(speedEnc, 60, 60);
        g.edge(0, 17).setDistance(100).set(speedEnc, 60, 60);
        g.edge(9, 10).setDistance(100).set(speedEnc, 60, 60);
        g.edge(10, 11).setDistance(100).set(speedEnc, 60, 60);
        g.edge(11, 28).setDistance(100).set(speedEnc, 60, 60);
        g.edge(28, 29).setDistance(100).set(speedEnc, 60, 60);
        g.edge(29, 30).setDistance(100).set(speedEnc, 60, 60);
        g.edge(30, 31).setDistance(100).set(speedEnc, 60, 60);
        g.edge(31, 4).setDistance(100).set(speedEnc, 60, 60);

        g.edge(17, 1).setDistance(100).set(speedEnc, 60, 60);
        g.edge(15, 1).setDistance(100).set(speedEnc, 60, 60);
        g.edge(14, 1).setDistance(100).set(speedEnc, 60, 60);
        g.edge(14, 18).setDistance(100).set(speedEnc, 60, 60);
        g.edge(18, 19).setDistance(100).set(speedEnc, 60, 60);
        g.edge(19, 20).setDistance(100).set(speedEnc, 60, 60);
        g.edge(20, 15).setDistance(100).set(speedEnc, 60, 60);
        g.edge(19, 21).setDistance(100).set(speedEnc, 60, 60);
        g.edge(21, 16).setDistance(100).set(speedEnc, 60, 60);
        g.edge(1, 2).setDistance(100).set(speedEnc, 60, 60);
        g.edge(2, 3).setDistance(100).set(speedEnc, 60, 60);
        g.edge(3, 4).setDistance(100).set(speedEnc, 60, 60);

        g.edge(4, 5).setDistance(100).set(speedEnc, 60, 0);
        g.edge(5, 6).setDistance(100).set(speedEnc, 60, 0);
        g.edge(6, 7).setDistance(100).set(speedEnc, 60, 0);
        g.edge(7, 13).setDistance(100).set(speedEnc, 60, 0);
        g.edge(13, 12).setDistance(100).set(speedEnc, 60, 0);
        g.edge(12, 4).setDistance(100).set(speedEnc, 60, 0);

        g.edge(7, 8).setDistance(100).set(speedEnc, 60, 60);
        g.edge(8, 22).setDistance(100).set(speedEnc, 60, 60);
        g.edge(22, 23).setDistance(100).set(speedEnc, 60, 60);
        g.edge(23, 24).setDistance(100).set(speedEnc, 60, 60);
        g.edge(24, 25).setDistance(100).set(speedEnc, 60, 60);
        g.edge(25, 27).setDistance(100).set(speedEnc, 60, 60);
        g.edge(27, 5).setDistance(100).set(speedEnc, 60, 60);
        g.edge(25, 26).setDistance(100).set(speedEnc, 60, 0);
        g.edge(26, 25).setDistance(100).set(speedEnc, 60, 0);
    }

    @Test
    public void testRoundaboutUnpacking() {
        initRoundaboutGraph(g, speedEnc);
        int oldCount = g.getEdges();
        PrepareContractionHierarchies prepare = createPrepareContractionHierarchies(g);
        useNodeOrdering(prepare, new int[]{26, 6, 12, 13, 2, 3, 8, 9, 10, 11, 14, 15, 16, 17, 18, 20, 21, 23, 24, 25, 19, 22, 27, 5, 29, 30, 31, 28, 7, 1, 0, 4});
        PrepareContractionHierarchies.Result res = prepare.doWork();
        assertEquals(oldCount, g.getEdges());
        RoutingCHGraph routingCHGraph = RoutingCHGraphImpl.fromGraph(g, res.getCHStorage(), res.getCHConfig());
        assertEquals(oldCount, routingCHGraph.getBaseGraph().getEdges());
        assertEquals(oldCount + 23, routingCHGraph.getEdges());
        RoutingAlgorithm algo = new CHRoutingAlgorithmFactory(routingCHGraph).createAlgo(new PMap());
        Path p = algo.calcPath(4, 7);
        assertEquals(IntArrayList.from(4, 5, 6, 7), p.calcNodes());
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
        g.edge(8, 3).setDistance(100).set(speedEnc, 60, 0);
        g.edge(3, 6).setDistance(100).set(speedEnc, 60, 0);
        g.edge(6, 1).setDistance(100).set(speedEnc, 60, 0);
        g.edge(1, 5).setDistance(100).set(speedEnc, 60, 0);
        g.edge(4, 0).setDistance(100).set(speedEnc, 60, 0);
        g.edge(0, 6).setDistance(100).set(speedEnc, 60, 0);
        g.edge(6, 2).setDistance(100).set(speedEnc, 60, 0);
        g.edge(2, 7).setDistance(100).set(speedEnc, 60, 0);
        g.freeze();

        PrepareContractionHierarchies prepare = createPrepareContractionHierarchies(g)
                .useFixedNodeOrdering(NodeOrderingProvider.identity(g.getNodes()));
        PrepareContractionHierarchies.Result res = prepare.doWork();
        RoutingCHGraph routingCHGraph = RoutingCHGraphImpl.fromGraph(g, res.getCHStorage(), res.getCHConfig());
        RoutingCHEdgeExplorer outExplorer = routingCHGraph.createOutEdgeExplorer();
        RoutingCHEdgeExplorer inExplorer = routingCHGraph.createInEdgeExplorer();
        // shortcuts leading to or coming from lower level nodes are not visible
        // so far we still receive base graph edges leading to or coming from lower level nodes though
        assertEquals(IntArrayList.from(7, 2, 1), getAdjs(outExplorer.setBaseNode(6)));
        assertEquals(IntArrayList.from(8, 0, 3), getAdjs(inExplorer.setBaseNode(6)));
        assertEquals(IntArrayList.from(6, 0), getAdjs(outExplorer.setBaseNode(4)));
        assertEquals(IntArrayList.from(6, 1), getAdjs(inExplorer.setBaseNode(5)));
        assertEquals(IntArrayList.from(8, 2), getAdjs(inExplorer.setBaseNode(7)));
        assertEquals(IntArrayList.from(3), getAdjs(outExplorer.setBaseNode(8)));
        assertEquals(IntArrayList.from(), getAdjs(inExplorer.setBaseNode(8)));
    }

    private IntArrayList getAdjs(RoutingCHEdgeIterator iter) {
        IntArrayList result = new IntArrayList();
        while (iter.next())
            result.add(iter.getAdjNode());
        return result;
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

        DecimalEncodedValue speedEnc = new DecimalEncodedValueImpl("speed", 5, 5, true);
        EncodingManager encodingManager = EncodingManager.start().add(speedEnc).build();
        BaseGraph g = new BaseGraph.Builder(encodingManager).create();
        // note: we changed the weighting during some refactorings, so if this test is no longer testing what
        //       it is supposed to test maybe that's why.
        Weighting weighting = new SpeedWeighting(speedEnc);
        CHConfig chConfig = CHConfig.nodeBased("c", weighting);
        // the following graph reproduces the issue. note that we will use the node ids as ch levels, so there will
        // be a shortcut 3->2 visible at node 2 and another one 3->4 visible at node 3.
        // we will fine-tune the edge-speeds such that without the fix node 4 will be stalled and node 5 will not get
        // discovered. consequently, no path will be found, because only the forward search runs (from 0 to 7 the
        // shortest path is strictly upward). node 4 is only stalled when node 2 gets stalled before, which in turn will
        // happen due to the virtual node between 3 and 1.
        //
        // start 0 - 3 - x - 1 - 2
        //             \         |
        //               sc ---- 4 - 5 - 6 - 7 finish
        g.edge(0, 3).setDistance(0).set(speedEnc, 60, 60);
        EdgeIteratorState edge31 = g.edge(3, 1).setDistance(0).set(speedEnc, 60, 60);
        g.edge(1, 2).setDistance(0).set(speedEnc, 60, 60);
        EdgeIteratorState edge24 = g.edge(2, 4).setDistance(0).set(speedEnc, 60, 60);
        g.edge(4, 5).setDistance(0).set(speedEnc, 60, 60);
        g.edge(5, 6).setDistance(0).set(speedEnc, 60, 60);
        g.edge(6, 7).setDistance(0).set(speedEnc, 60, 60);
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
        edge31.set(speedEnc, 12, 12);

        // just stalling node 2 alone would not lead to connection not found, because the shortcut 3-4 still finds node
        // 4. however, we can choose the weight of edge 2-4 such that node 4 also gets stalled via node 2.
        // it is important that node 2 gets stalled before otherwise node 4 would have already be discovered.
        // note that without the virtual node between 3 and 1 node 2 would not even be explored in the forward search,
        // but because of the virtual node the strict upward search is modified and goes like 0-3-x-1-2.
        edge24.set(speedEnc, 27.5, 27.5);

        // prepare ch, use node ids as levels
        PrepareContractionHierarchies pch = createPrepareContractionHierarchies(g, chConfig);
        PrepareContractionHierarchies.Result res = pch.useFixedNodeOrdering(NodeOrderingProvider.identity(g.getNodes())).doWork();
        RoutingCHGraph routingCHGraph = RoutingCHGraphImpl.fromGraph(g, res.getCHStorage(), res.getCHConfig());
        assertEquals(2, routingCHGraph.getEdges() - g.getEdges(), "there should be exactly two (bidirectional) shortcuts (2-3) and (3-4)");

        // insert virtual node and edges
        Snap snap = new Snap(0.001, 0.00015); // between 3 and 1
        snap.setClosestEdge(edge31);
        snap.setSnappedPosition(Snap.Position.EDGE);
        snap.setClosestNode(8);
        snap.setWayIndex(0);
        snap.calcSnappedPoint(new DistanceCalcEuclidean());

        QueryGraph queryGraph = QueryGraph.create(g, snap);

        // we make sure our weight fine tunings do what they are supposed to
        double weight03 = getWeight(queryGraph, weighting, 0, 3);
        double scWeight23 = weight03 + getEdge(routingCHGraph, 2, 3, true).getWeight(false);
        double scWeight34 = weight03 + getEdge(routingCHGraph, 3, 4, false).getWeight(false);
        double sptWeight2 = weight03 + getWeight(queryGraph, weighting, 3, 8) + getWeight(queryGraph, weighting, 8, 1) + getWeight(queryGraph, weighting, 1, 2);
        double sptWeight4 = sptWeight2 + getWeight(queryGraph, weighting, 2, 4);
        assertTrue(scWeight23 < sptWeight2, "incoming shortcut weight 3->2 should be smaller than sptWeight at node 2 to make sure 2 gets stalled");
        assertTrue(sptWeight4 < scWeight34, "sptWeight at node 4 should be smaller than shortcut weight 3->4 to make sure node 4 gets stalled");

        Path path = new CHRoutingAlgorithmFactory(routingCHGraph, queryGraph).createAlgo(new PMap()).calcPath(0, 7);
        assertEquals(IntArrayList.from(0, 3, 8, 1, 2, 4, 5, 6, 7), path.calcNodes(), "wrong or no path found");
    }

    private double getWeight(Graph graph, Weighting w, int from, int to) {
        return w.calcEdgeWeight(getEdge(graph, from, to), false);
    }

    private EdgeIteratorState getEdge(Graph graph, int from, int to) {
        EdgeIterator iter = graph.createEdgeExplorer().setBaseNode(from);
        while (iter.next()) {
            if (iter.getAdjNode() == to) {
                return iter;
            }
        }
        throw new IllegalArgumentException("Could not find edge from: " + from + " to: " + to);
    }

    private RoutingCHEdgeIteratorState getEdge(RoutingCHGraph graph, int from, int to, boolean incoming) {
        RoutingCHEdgeIterator iter = incoming ?
                graph.createInEdgeExplorer().setBaseNode(from) :
                graph.createOutEdgeExplorer().setBaseNode(from);
        while (iter.next())
            if (iter.getAdjNode() == to)
                return iter;
        throw new IllegalArgumentException("Could not find edge from: " + from + " to: " + to);
    }

    @Test
    public void testCircleBug() {
        //  /--1
        // -0--/
        //  |
        g.edge(0, 1).setDistance(1000).set(speedEnc, 60, 60);
        g.edge(0, 1).setDistance(400).set(speedEnc, 60, 60);
        g.edge(0, 2).setDistance(1000).set(speedEnc, 60, 60);
        g.edge(0, 3).setDistance(1000).set(speedEnc, 60, 60);
        PrepareContractionHierarchies prepare = createPrepareContractionHierarchies(g);
        PrepareContractionHierarchies.Result result = prepare.doWork();
        assertEquals(0, result.getShortcuts());
    }

    @Test
    public void testBug178() {
        // 5--------6__
        // |        |  \
        // 0-1->-2--3--4
        //   \-<-/
        //
        g.edge(1, 2).setDistance(100).set(speedEnc, 60, 0);
        g.edge(2, 1).setDistance(100).set(speedEnc, 60, 0);

        g.edge(5, 0).setDistance(100).set(speedEnc, 60, 60);
        g.edge(5, 6).setDistance(100).set(speedEnc, 60, 60);
        g.edge(0, 1).setDistance(100).set(speedEnc, 60, 60);
        g.edge(2, 3).setDistance(100).set(speedEnc, 60, 60);
        g.edge(3, 4).setDistance(100).set(speedEnc, 60, 60);
        g.edge(6, 3).setDistance(100).set(speedEnc, 60, 60);

        PrepareContractionHierarchies prepare = createPrepareContractionHierarchies(g);
        useNodeOrdering(prepare, new int[]{4, 1, 2, 0, 5, 6, 3});
        PrepareContractionHierarchies.Result result = prepare.doWork();
        assertEquals(2, result.getShortcuts());
    }

    @Test
    public void testBits() {
        int fromNode = Integer.MAX_VALUE / 3 * 2;
        int endNode = Integer.MAX_VALUE / 37 * 17;

        long edgeId = (long) fromNode << 32 | endNode;
        assertEquals((BitUtil.LITTLE.toBitString(edgeId)),
                BitUtil.LITTLE.toLastBitString(fromNode, 32) + BitUtil.LITTLE.toLastBitString(endNode, 32));
    }

    @Test
    public void testMultiplePreparationsIdenticalView() {
        DecimalEncodedValue carSpeedEnc = new DecimalEncodedValueImpl("car_speed", 5, 5, true);
        DecimalEncodedValue bikeSpeedEnc = new DecimalEncodedValueImpl("bike_speed", 4, 2, true);
        EncodingManager tmpEncodingManager = EncodingManager.start()
                .add(carSpeedEnc)
                .add(bikeSpeedEnc)
                .build();

        CHConfig carProfile = CHConfig.nodeBased("c1", new SpeedWeighting(carSpeedEnc));
        CHConfig bikeProfile = CHConfig.nodeBased("c2", new SpeedWeighting(bikeSpeedEnc));

        BaseGraph graph = new BaseGraph.Builder(tmpEncodingManager).create();
        initShortcutsGraph(graph, carSpeedEnc);
        AllEdgesIterator iter = graph.getAllEdges();
        while (iter.next())
            iter.set(bikeSpeedEnc, 18, 18);
        graph.freeze();

        checkPath(graph, carProfile, 7, 500, IntArrayList.from(3, 9, 14, 16, 13, 12), new int[]{0, 5, 6, 7, 8, 10, 11, 13, 15, 1, 3, 9, 14, 16, 12, 4, 2});
        checkPath(graph, bikeProfile, 7, 500, IntArrayList.from(3, 9, 14, 16, 13, 12), new int[]{0, 5, 6, 7, 8, 10, 11, 13, 15, 1, 3, 9, 14, 16, 12, 4, 2});
    }

    @Test
    public void testMultiplePreparationsDifferentView() {
        DecimalEncodedValue carSpeedEnc = new DecimalEncodedValueImpl("car_speed", 5, 5, true);
        DecimalEncodedValue bikeSpeedEnc = new DecimalEncodedValueImpl("bike_speed", 4, 2, true);
        EncodingManager tmpEncodingManager = EncodingManager.start()
                .add(carSpeedEnc)
                .add(bikeSpeedEnc)
                .build();

        CHConfig carConfig = CHConfig.nodeBased("c1", new SpeedWeighting(carSpeedEnc));
        CHConfig bikeConfig = CHConfig.nodeBased("c2", new SpeedWeighting(bikeSpeedEnc));

        BaseGraph graph = new BaseGraph.Builder(tmpEncodingManager).create();
        initShortcutsGraph(graph, carSpeedEnc);
        AllEdgesIterator iter = graph.getAllEdges();
        while (iter.next())
            iter.set(bikeSpeedEnc, 18, 18);

        GHUtility.getEdge(graph, 9, 14).set(bikeSpeedEnc, 0, 0);

        graph.freeze();

        checkPath(graph, carConfig, 7, 500, IntArrayList.from(3, 9, 14, 16, 13, 12), new int[]{0, 5, 6, 7, 8, 10, 11, 13, 15, 1, 3, 9, 14, 16, 12, 4, 2});
        // detour around blocked 9,14
        checkPath(graph, bikeConfig, 9, 500, IntArrayList.from(3, 10, 14, 16, 13, 12), new int[]{0, 5, 6, 7, 8, 10, 11, 13, 14, 15, 9, 1, 4, 3, 2, 12, 16});
    }

    @Test
    public void testReusingNodeOrdering() {
        DecimalEncodedValue car1SpeedEnc = new DecimalEncodedValueImpl("car1_speed", 5, 5, true);
        DecimalEncodedValue car2SpeedEnc = new DecimalEncodedValueImpl("car2_speed", 5, 5, true);
        DecimalEncodedValue car1TurnCostEnc = TurnCost.create("car1", 1);
        DecimalEncodedValue car2TurnCostEnc = TurnCost.create("car2", 1);
        EncodingManager em = EncodingManager.start()
                .add(car1SpeedEnc).addTurnCostEncodedValue(car1TurnCostEnc)
                .add(car2SpeedEnc).addTurnCostEncodedValue(car2TurnCostEnc)
                .build();
        CHConfig car1Config = CHConfig.nodeBased("c1", new SpeedWeighting(car1SpeedEnc));
        CHConfig car2Config = CHConfig.nodeBased("c2", new SpeedWeighting(car2SpeedEnc));
        BaseGraph graph = new BaseGraph.Builder(em).create();

        int numNodes = 5_000;
        int numQueries = 100;
        long seed = System.nanoTime();
        Random rnd = new Random(seed);
        GHUtility.buildRandomGraph(graph, rnd, numNodes, 1.3, true, null, null, 0.9, 0.8);
        AllEdgesIterator iter = graph.getAllEdges();
        while (iter.next()) {
            double car1Fwd = rnd.nextDouble() * 100;
            if (rnd.nextDouble() < 0.05) car1Fwd = 0;
            double car1Bwd = rnd.nextDouble() * 100;
            if (rnd.nextDouble() < 0.05) car1Bwd = 0;
            double car2Fwd = rnd.nextDouble() * 100;
            if (rnd.nextDouble() < 0.05) car2Fwd = 0;
            double car2Bwd = rnd.nextDouble() * 100;
            if (rnd.nextDouble() < 0.05) car2Bwd = 0;
            iter.set(car1SpeedEnc, car1Fwd, car1Bwd);
            iter.set(car2SpeedEnc, car2Fwd, car2Bwd);
        }
        graph.freeze();

        // create CH for cars
        PrepareContractionHierarchies car1Pch = PrepareContractionHierarchies.fromGraph(graph, car1Config);
        PrepareContractionHierarchies.Result resCar1 = car1Pch.doWork();

        // create CH for motorcycles, re-use car contraction order
        // this speeds up contraction significantly, but can lead to slower queries
        CHStorage car1CHStore = resCar1.getCHStorage();
        NodeOrderingProvider nodeOrderingProvider = car1CHStore.getNodeOrderingProvider();
        PrepareContractionHierarchies car2Pch = PrepareContractionHierarchies.fromGraph(graph, car2Config)
                .useFixedNodeOrdering(nodeOrderingProvider);
        PrepareContractionHierarchies.Result resCar2 = car2Pch.doWork();
        RoutingCHGraph car2CH = RoutingCHGraphImpl.fromGraph(graph, resCar2.getCHStorage(), resCar2.getCHConfig());

        assertTrue(car1CHStore.getShortcuts() > 0 && resCar2.getCHStorage().getShortcuts() > 0);
        assertNotEquals(car1CHStore.getShortcuts(), resCar2.getCHStorage().getShortcuts());

        // run a few sample queries to check correctness
        for (int i = 0; i < numQueries; ++i) {
            Dijkstra dijkstra = new Dijkstra(graph, car2Config.getWeighting(), TraversalMode.NODE_BASED);
            RoutingAlgorithm chAlgo = new CHRoutingAlgorithmFactory(car2CH).createAlgo(new PMap());

            int from = rnd.nextInt(numNodes);
            int to = rnd.nextInt(numNodes);
            double dijkstraWeight = dijkstra.calcPath(from, to).getWeight();
            double chWeight = chAlgo.calcPath(from, to).getWeight();
            assertEquals(dijkstraWeight, chWeight, 1.e-1);
        }
    }

    private void checkPath(BaseGraph g, CHConfig c, int expShortcuts, double expDistance, IntIndexedContainer expNodes, int[] nodeOrdering) {
        PrepareContractionHierarchies prepare = createPrepareContractionHierarchies(g, c);
        useNodeOrdering(prepare, nodeOrdering);
        PrepareContractionHierarchies.Result result = prepare.doWork();
        assertEquals(expShortcuts, result.getShortcuts(), c.toString());
        RoutingCHGraph lg = RoutingCHGraphImpl.fromGraph(g, result.getCHStorage(), result.getCHConfig());
        RoutingAlgorithm algo = new CHRoutingAlgorithmFactory(lg).createAlgo(new PMap());
        Path path = algo.calcPath(3, 12);
        assertEquals(expDistance, path.getDistance(), 1e-5, path.toString());
        assertEquals(expNodes, path.calcNodes(), path.toString());
    }

    private PrepareContractionHierarchies createPrepareContractionHierarchies(BaseGraph g) {
        return createPrepareContractionHierarchies(g, chConfig);
    }

    private PrepareContractionHierarchies createPrepareContractionHierarchies(BaseGraph g, CHConfig p) {
        if (!g.isFrozen())
            g.freeze();
        return PrepareContractionHierarchies.fromGraph(g, p);
    }

    private void useNodeOrdering(PrepareContractionHierarchies prepare, int[] nodeOrdering) {
        prepare.useFixedNodeOrdering(NodeOrderingProvider.fromArray(nodeOrdering));
    }

}
