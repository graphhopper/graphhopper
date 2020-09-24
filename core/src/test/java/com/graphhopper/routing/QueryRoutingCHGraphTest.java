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

package com.graphhopper.routing;

import com.graphhopper.routing.ch.PrepareEncoder;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.TurnCost;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.querygraph.QueryRoutingCHGraph;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.weighting.DefaultTurnCostProvider;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.storage.*;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.DistancePlaneProjection;
import com.graphhopper.util.EdgeIteratorState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static com.graphhopper.util.EdgeIterator.NO_EDGE;
import static org.junit.jupiter.api.Assertions.*;

class QueryRoutingCHGraphTest {
    private CarFlagEncoder encoder;
    private EncodingManager encodingManager;
    private FastestWeighting weighting;
    private GraphHopperStorage graph;
    private NodeAccess na;
    private CHGraph chGraph;
    private RoutingCHGraph routingCHGraph;

    @BeforeEach
    public void setup() {
        encoder = new CarFlagEncoder(5, 5, 5).setSpeedTwoDirections(true);
        encodingManager = EncodingManager.create(encoder);
        graph = new GraphBuilder(encodingManager).build();
        weighting = new FastestWeighting(encoder, new DefaultTurnCostProvider(encoder, graph.getTurnCostStorage()));
        graph.addCHGraph(CHConfig.edgeBased("x", weighting));
        graph.create(100);
        na = graph.getNodeAccess();
        chGraph = graph.getCHGraph("x");
        routingCHGraph = graph.getRoutingCHGraph("x");
    }

    @Test
    public void basic() {
        // 0-1-2
        graph.edge(0, 1, 10, true);
        graph.edge(1, 2, 10, true);
        graph.freeze();
        assertEquals(2, graph.getEdges());

        QueryGraph queryGraph = QueryGraph.create(graph, Collections.<QueryResult>emptyList());
        QueryRoutingCHGraph queryCHGraph = new QueryRoutingCHGraph(routingCHGraph, queryGraph);

        assertEquals(3, queryCHGraph.getNodes());
        assertEquals(2, queryCHGraph.getEdges());
        assertTrue(queryCHGraph.isEdgeBased());
        assertTrue(queryCHGraph.hasTurnCosts());

        assertNodesConnected(queryCHGraph, 0, 1, true);
        assertNodesConnected(queryCHGraph, 1, 2, true);

        RoutingCHEdgeIterator outIter = queryCHGraph.createOutEdgeExplorer().setBaseNode(0);
        assertNextEdge(outIter, 0, 1, 0);
        assertEnd(outIter);

        RoutingCHEdgeIterator inIter = queryCHGraph.createInEdgeExplorer().setBaseNode(1);
        assertNextEdge(inIter, 1, 2, 1);
        assertNextEdge(inIter, 1, 0, 0);
        assertEnd(inIter);

        inIter = queryCHGraph.createInEdgeExplorer().setBaseNode(2);
        assertNextEdge(inIter, 2, 1, 1);
        assertEnd(inIter);
    }

    @Test
    public void withShortcuts() {
        // 0-1-2
        //  \-/
        graph.edge(0, 1, 10, true);
        graph.edge(1, 2, 10, true);
        graph.freeze();
        assertEquals(2, graph.getEdges());
        chGraph.shortcut(0, 2, PrepareEncoder.getScFwdDir(), 20, 0, 1);

        QueryGraph queryGraph = QueryGraph.create(graph, Collections.<QueryResult>emptyList());
        QueryRoutingCHGraph queryCHGraph = new QueryRoutingCHGraph(routingCHGraph, queryGraph);

        assertEquals(3, queryCHGraph.getNodes());
        assertEquals(3, queryCHGraph.getEdges());

        assertNodesConnected(queryCHGraph, 0, 1, true);
        assertNodesConnected(queryCHGraph, 1, 2, true);
        // the shortcut 0-2 is not visible from node 2
//        assertNodesConnected(queryCHGraph, 0, 2, false);

        RoutingCHEdgeIterator outIter = queryCHGraph.createOutEdgeExplorer().setBaseNode(0);
        assertNextShortcut(outIter, 0, 2, 0, 1);
        assertNextEdge(outIter, 0, 1, 0);
        assertEnd(outIter);

        RoutingCHEdgeIterator inIter = queryCHGraph.createInEdgeExplorer().setBaseNode(2);
        assertNextEdge(inIter, 2, 1, 1);
        assertEnd(inIter);
    }

    @Test
    public void withVirtualEdges() {
        //  2 3
        // 0-x-1-2
        //   3
        na.setNode(0, 50.00, 10.00);
        na.setNode(1, 50.00, 10.10);
        na.setNode(2, 50.00, 10.20);
        EdgeIteratorState edge = addEdge(graph, 0, 1);
        addEdge(graph, 1, 2);
        graph.freeze();
        assertEquals(2, graph.getEdges());

        QueryResult qr = new QueryResult(50.00, 10.05);
        qr.setClosestEdge(edge);
        qr.setWayIndex(0);
        qr.setSnappedPosition(QueryResult.Position.EDGE);
        qr.calcSnappedPoint(DistancePlaneProjection.DIST_PLANE);

        QueryGraph queryGraph = QueryGraph.create(graph, Collections.singletonList(qr));
        QueryRoutingCHGraph queryCHGraph = new QueryRoutingCHGraph(routingCHGraph, queryGraph);

        assertEquals(4, queryCHGraph.getNodes());
        assertEquals(2 + 4, queryCHGraph.getEdges());

        assertNodesConnected(queryCHGraph, 1, 2, true);
        // virtual edges at virtual node 3
        assertNodesConnected(queryCHGraph, 0, 3, true);
        assertNodesConnected(queryCHGraph, 3, 1, true);

        // out-iter at real node
        RoutingCHEdgeIterator outIter = queryCHGraph.createOutEdgeExplorer().setBaseNode(2);
        assertNextEdge(outIter, 2, 1, 1);
        assertEnd(outIter);

        // in-iter at real node
        RoutingCHEdgeIterator inIter = queryCHGraph.createInEdgeExplorer().setBaseNode(2);
        assertNextEdge(inIter, 2, 1, 1);
        assertEnd(inIter);

        // out-iter at real node next to virtual node
        outIter = queryCHGraph.createOutEdgeExplorer().setBaseNode(0);
        assertNextEdge(outIter, 0, 3, 2);
        assertEnd(outIter);

        // in-iter at real node next to virtual node
        inIter = queryCHGraph.createInEdgeExplorer().setBaseNode(1);
        assertNextEdge(inIter, 1, 3, 3);
        assertNextEdge(inIter, 1, 2, 1);
        assertEnd(inIter);

        // out-iter at virtual node
        outIter = queryCHGraph.createOutEdgeExplorer().setBaseNode(3);
        assertNextEdge(outIter, 3, 0, 2);
        assertNextEdge(outIter, 3, 1, 3);
        assertEnd(outIter);

        // in-iter at virtual node
        inIter = queryCHGraph.createInEdgeExplorer().setBaseNode(3);
        assertNextEdge(inIter, 3, 0, 2);
        assertNextEdge(inIter, 3, 1, 3);
        assertEnd(inIter);
    }

    @Test
    public void withVirtualEdgesAndShortcuts() {
        //  /---\
        // 0-x-1-2
        //   3
        na.setNode(0, 50.00, 10.00);
        na.setNode(1, 50.00, 10.10);
        na.setNode(2, 50.00, 10.20);
        EdgeIteratorState edge = addEdge(graph, 0, 1);
        addEdge(graph, 1, 2);
        graph.freeze();
        assertEquals(2, graph.getEdges());
        chGraph.shortcut(0, 2, PrepareEncoder.getScFwdDir(), 20, 0, 1);

        QueryResult qr = new QueryResult(50.00, 10.05);
        qr.setClosestEdge(edge);
        qr.setWayIndex(0);
        qr.setSnappedPosition(QueryResult.Position.EDGE);
        qr.calcSnappedPoint(DistancePlaneProjection.DIST_PLANE);

        QueryGraph queryGraph = QueryGraph.create(graph, Collections.singletonList(qr));
        QueryRoutingCHGraph queryCHGraph = new QueryRoutingCHGraph(routingCHGraph, queryGraph);

        assertEquals(4, queryCHGraph.getNodes());
        assertEquals(3 + 4, queryCHGraph.getEdges());

        assertNodesConnected(queryCHGraph, 0, 3, true);
        assertNodesConnected(queryCHGraph, 3, 1, true);
        assertNodesConnected(queryCHGraph, 1, 2, true);
        // node 0 is not visible from node 0 via shortcut 0-2
//        assertNodesConnected(queryCHGraph, 0, 2, false);

        // at real nodes
        RoutingCHEdgeIterator outIter = queryCHGraph.createOutEdgeExplorer().setBaseNode(0);
        // note that orig edge of virtual edges corresponds to the id of the virtual edge on the base graph
        assertNextEdge(outIter, 0, 3, 2);
        assertNextShortcut(outIter, 0, 2, 0, 1);
        assertEnd(outIter);

        RoutingCHEdgeIterator inIter = queryCHGraph.createInEdgeExplorer().setBaseNode(2);
        assertNextEdge(inIter, 2, 1, 1);
        assertEnd(inIter);

        // at virtual nodes
        outIter = queryCHGraph.createOutEdgeExplorer().setBaseNode(3);
        assertNextEdge(outIter, 3, 0, 2);
        assertNextEdge(outIter, 3, 1, 3);
        assertEnd(outIter);

        inIter = queryCHGraph.createInEdgeExplorer().setBaseNode(3);
        assertNextEdge(inIter, 3, 0, 2);
        assertNextEdge(inIter, 3, 1, 3);
        assertEnd(inIter);
    }

    @Test
    public void getBaseGraph() {
        graph.edge(0, 1, 10, true);
        QueryGraph queryGraph = QueryGraph.create(graph, Collections.<QueryResult>emptyList());
        assertSame(graph.getBaseGraph(), routingCHGraph.getBaseGraph());
        QueryRoutingCHGraph queryCHGraph = new QueryRoutingCHGraph(routingCHGraph, queryGraph);
        assertSame(queryGraph, queryCHGraph.getBaseGraph());
    }

    @Test
    public void getEdgeIteratorState() {
        //  /---\
        // 0-x-1-2
        //   3
        na.setNode(0, 50.00, 10.00);
        na.setNode(1, 50.00, 10.10);
        na.setNode(2, 50.00, 10.20);
        EdgeIteratorState edge = addEdge(graph, 0, 1);
        addEdge(graph, 1, 2);
        graph.freeze();
        chGraph.shortcut(0, 2, PrepareEncoder.getScFwdDir(), 20, 0, 1);

        QueryResult qr = new QueryResult(50.00, 10.05);
        qr.setClosestEdge(edge);
        qr.setWayIndex(0);
        qr.setSnappedPosition(QueryResult.Position.EDGE);
        qr.calcSnappedPoint(DistancePlaneProjection.DIST_PLANE);

        QueryGraph queryGraph = QueryGraph.create(graph, Collections.singletonList(qr));
        QueryRoutingCHGraph queryCHGraph = new QueryRoutingCHGraph(routingCHGraph, queryGraph);

        assertGetEdgeIteratorState(queryCHGraph, 1, 2, 1);
        assertGetEdgeIteratorShortcut(queryCHGraph, 0, 2, 0, 1);
        // the orig edge corresponds to the edge id of the edge in the (base) query graph
        assertGetEdgeIteratorState(queryCHGraph, 0, 3, 2);
        assertGetEdgeIteratorState(queryCHGraph, 3, 0, 2);
        assertGetEdgeIteratorState(queryCHGraph, 1, 3, 3);
        assertGetEdgeIteratorState(queryCHGraph, 3, 1, 3);
    }

    @Test
    public void getWeighting() {
        QueryGraph queryGraph = QueryGraph.create(graph, Collections.<QueryResult>emptyList());
        QueryRoutingCHGraph queryCHGraph = new QueryRoutingCHGraph(routingCHGraph, queryGraph);
        // maybe query CH graph should return query graph weighting instead?
        assertSame(weighting, queryCHGraph.getWeighting());
    }

    @Test
    public void getLevel() {
        // 0-x-1
        na.setNode(0, 50.00, 10.00);
        na.setNode(1, 50.00, 10.10);
        EdgeIteratorState edge = addEdge(graph, 0, 1);
        graph.freeze();
        chGraph.setLevel(0, 5);
        chGraph.setLevel(1, 7);

        QueryResult qr = new QueryResult(50.00, 10.05);
        qr.setClosestEdge(edge);
        qr.setWayIndex(0);
        qr.setSnappedPosition(QueryResult.Position.EDGE);
        qr.calcSnappedPoint(DistancePlaneProjection.DIST_PLANE);

        QueryGraph queryGraph = QueryGraph.create(graph, Collections.singletonList(qr));
        QueryRoutingCHGraph queryCHGraph = new QueryRoutingCHGraph(routingCHGraph, queryGraph);
        assertEquals(5, queryCHGraph.getLevel(0));
        assertEquals(7, queryCHGraph.getLevel(1));
        assertEquals(Integer.MAX_VALUE, queryCHGraph.getLevel(2));
    }

    @Test
    public void getWeight() {
        //  /---\
        // 0-x-1-2
        //   3
        na.setNode(0, 50.00, 10.00);
        na.setNode(1, 50.00, 10.10);
        na.setNode(2, 50.00, 10.20);
        EdgeIteratorState edge = addEdge(graph, 0, 1)
                // use different speeds for the two directions
                .set(encoder.getAverageSpeedEnc(), 90)
                .setReverse(encoder.getAverageSpeedEnc(), 30);
        addEdge(graph, 1, 2);
        graph.freeze();
        chGraph.shortcut(0, 2, PrepareEncoder.getScDirMask(), 20, 0, 1);

        // without query graph
        RoutingCHEdgeIterator iter = routingCHGraph.createOutEdgeExplorer().setBaseNode(0);
        assertNextShortcut(iter, 0, 2, 0, 1);
        assertEquals(20, iter.getWeight(false), 1.e-6);
        assertEquals(20, iter.getWeight(true), 1.e-6);
        assertNextEdge(iter, 0, 1, 0);
        assertEquals(285.8984, iter.getWeight(false), 1.e-6);
        assertEquals(857.6952, iter.getWeight(true), 1.e-6);
        assertEnd(iter);

        // for incoming edges its the same
        iter = routingCHGraph.createInEdgeExplorer().setBaseNode(0);
        assertNextShortcut(iter, 0, 2, 0, 1);
        assertEquals(20, iter.getWeight(false), 1.e-6);
        assertEquals(20, iter.getWeight(true), 1.e-6);
        assertNextEdge(iter, 0, 1, 0);
        assertEquals(285.8984, iter.getWeight(false), 1.e-6);
        assertEquals(857.6952, iter.getWeight(true), 1.e-6);
        assertEnd(iter);

        // now including virtual edges
        QueryResult qr = new QueryResult(50.00, 10.05);
        qr.setClosestEdge(edge);
        qr.setWayIndex(0);
        qr.setSnappedPosition(QueryResult.Position.EDGE);
        qr.calcSnappedPoint(DistancePlaneProjection.DIST_PLANE);

        QueryGraph queryGraph = QueryGraph.create(graph, Collections.singletonList(qr));
        QueryRoutingCHGraph queryCHGraph = new QueryRoutingCHGraph(routingCHGraph, queryGraph);

        iter = queryCHGraph.createOutEdgeExplorer().setBaseNode(0);
        assertNextEdge(iter, 0, 3, 2);
        // should be about half the weight as for the original edge as the query point is in the middle of the edge
        assertEquals(142.9494, iter.getWeight(false), 1.e-4);
        assertEquals(428.8483, iter.getWeight(true), 1.e-4);
        assertNextShortcut(iter, 0, 2, 0, 1);
        assertEquals(20, iter.getWeight(false), 1.e-6);
        assertEquals(20, iter.getWeight(true), 1.e-6);
        assertEnd(iter);

        iter = queryCHGraph.createInEdgeExplorer().setBaseNode(0);
        assertNextEdge(iter, 0, 3, 2);
        assertEquals(142.9494, iter.getWeight(false), 1.e-4);
        assertEquals(428.8483, iter.getWeight(true), 1.e-4);
        assertNextShortcut(iter, 0, 2, 0, 1);
        assertEquals(20, iter.getWeight(false), 1.e-6);
        assertEquals(20, iter.getWeight(true), 1.e-6);
        assertEnd(iter);

        // at the virtual node
        iter = queryCHGraph.createOutEdgeExplorer().setBaseNode(3);
        assertNextEdge(iter, 3, 0, 2);
        assertEquals(428.8483, iter.getWeight(false), 1.e-4);
        assertEquals(142.9494, iter.getWeight(true), 1.e-4);
        assertNextEdge(iter, 3, 1, 3);
        assertEquals(142.9489, iter.getWeight(false), 1.e-4);
        assertEquals(428.8469, iter.getWeight(true), 1.e-4);
        assertEnd(iter);

        iter = queryCHGraph.createInEdgeExplorer().setBaseNode(3);
        assertNextEdge(iter, 3, 0, 2);
        assertEquals(428.8483, iter.getWeight(false), 1.e-4);
        assertEquals(142.9494, iter.getWeight(true), 1.e-4);
        assertNextEdge(iter, 3, 1, 3);
        assertEquals(142.9489, iter.getWeight(false), 1.e-4);
        assertEquals(428.8469, iter.getWeight(true), 1.e-4);
        assertEnd(iter);

        // getting a single edge
        RoutingCHEdgeIteratorState edgeState = queryCHGraph.getEdgeIteratorState(3, 3);
        assertEdgeState(edgeState, 0, 3, 2);
        assertEquals(142.9494, edgeState.getWeight(false), 1.e-4);
        assertEquals(428.8483, edgeState.getWeight(true), 1.e-4);

        edgeState = queryCHGraph.getEdgeIteratorState(3, 0);
        assertEdgeState(edgeState, 3, 0, 2);
        assertEquals(428.8483, edgeState.getWeight(false), 1.e-4);
        assertEquals(142.9494, edgeState.getWeight(true), 1.e-4);
    }

    @Test
    public void getWeight_withAccess() {
        na.setNode(0, 50.00, 10.00);
        na.setNode(1, 50.00, 10.10);
        double dist = DistancePlaneProjection.DIST_PLANE.calcDist(na.getLat(0), na.getLon(0), na.getLat(1), na.getLon(1));
        EdgeIteratorState edge = graph.edge(0, 1).setDistance(dist);
        // we set the access flags, but do use direction dependent speeds to make sure we are testing whether or not the
        // access flags are respected and the weight calculation does not simply rely on the speed, see this forum issue
        // https://discuss.graphhopper.com/t/speed-and-access-when-setbothdirections-true-false/5695
        edge.set(encoder.getAccessEnc(), true);
        edge.setReverse(encoder.getAccessEnc(), false);
        edge.set(encoder.getAverageSpeedEnc(), 60);
        edge.setReverse(encoder.getAverageSpeedEnc(), 60);
        graph.freeze();

        // without query graph
        // 0->1
        RoutingCHEdgeIterator iter = routingCHGraph.createOutEdgeExplorer().setBaseNode(0);
        assertNextEdge(iter, 0, 1, 0);
        assertEquals(428.8476, iter.getWeight(false), 1.e-4);
        assertEquals(Double.POSITIVE_INFINITY, iter.getWeight(true));
        assertEnd(iter);

        iter = routingCHGraph.createInEdgeExplorer().setBaseNode(1);
        assertNextEdge(iter, 1, 0, 0);
        assertEquals(Double.POSITIVE_INFINITY, iter.getWeight(false));
        assertEquals(428.8476, iter.getWeight(true), 1.e-4);
        assertEnd(iter);

        // single edges
        assertEquals(428.8476, routingCHGraph.getEdgeIteratorState(0, 1).getWeight(false), 1.e-4);
        assertEquals(Double.POSITIVE_INFINITY, routingCHGraph.getEdgeIteratorState(0, 1).getWeight(true));
        assertEquals(Double.POSITIVE_INFINITY, routingCHGraph.getEdgeIteratorState(0, 0).getWeight(false));
        assertEquals(428.8476, routingCHGraph.getEdgeIteratorState(0, 0).getWeight(true), 1.e-4);

        // with query graph
        // 0-x->1
        //   2
        QueryResult qr = new QueryResult(50.00, 10.05);
        qr.setClosestEdge(edge);
        qr.setWayIndex(0);
        qr.setSnappedPosition(QueryResult.Position.EDGE);
        qr.calcSnappedPoint(DistancePlaneProjection.DIST_PLANE);

        QueryGraph queryGraph = QueryGraph.create(graph, Collections.singletonList(qr));
        QueryRoutingCHGraph queryCHGraph = new QueryRoutingCHGraph(routingCHGraph, queryGraph);

        iter = queryCHGraph.createOutEdgeExplorer().setBaseNode(0);
        assertNextEdge(iter, 0, 2, 1);
        assertEquals(214.4241, iter.getWeight(false), 1.e-4);
        assertEquals(Double.POSITIVE_INFINITY, iter.getWeight(true));
        assertEnd(iter);

        iter = queryCHGraph.createInEdgeExplorer().setBaseNode(1);
        assertNextEdge(iter, 1, 2, 2);
        assertEquals(Double.POSITIVE_INFINITY, iter.getWeight(false));
        assertEquals(214.4234, iter.getWeight(true), 1.e-4);
        assertEnd(iter);

        // at virtual node
        iter = queryCHGraph.createInEdgeExplorer().setBaseNode(2);
        assertNextEdge(iter, 2, 0, 1);
        assertEquals(Double.POSITIVE_INFINITY, iter.getWeight(false));
        assertEquals(214.4241, iter.getWeight(true), 1.e-4);
        assertNextEdge(iter, 2, 1, 2);
        assertEquals(214.4234, iter.getWeight(false), 1.e-4);
        assertEquals(Double.POSITIVE_INFINITY, iter.getWeight(true));
        assertEnd(iter);

        // single edges
        assertEquals(214.4241, queryCHGraph.getEdgeIteratorState(1, 2).getWeight(false), 1.e-4);
        assertEquals(Double.POSITIVE_INFINITY, queryCHGraph.getEdgeIteratorState(1, 2).getWeight(true));
        assertEquals(Double.POSITIVE_INFINITY, queryCHGraph.getEdgeIteratorState(1, 0).getWeight(false));
        assertEquals(214.4241, queryCHGraph.getEdgeIteratorState(1, 0).getWeight(true), 1.e-4);
    }

    @Test
    public void getTurnCost() {
        //  /-----\
        // 0-x-1-x-2
        //   3   4
        na.setNode(0, 50.00, 10.00);
        na.setNode(1, 50.00, 10.10);
        na.setNode(2, 50.00, 10.20);
        EdgeIteratorState edge1 = addEdge(graph, 0, 1);
        EdgeIteratorState edge2 = addEdge(graph, 1, 2);
        DecimalEncodedValue turnCostEnc = encodingManager.getDecimalEncodedValue(TurnCost.key(encoder.toString()));
        graph.getTurnCostStorage().set(turnCostEnc, 0, 1, 1, 5);
        graph.freeze();
        chGraph.shortcut(0, 2, PrepareEncoder.getScDirMask(), 20, 0, 1);

        // without virtual nodes
        assertEquals(5, routingCHGraph.getTurnWeight(0, 1, 1));

        // with virtual nodes
        QueryResult qr1 = new QueryResult(50.00, 10.05);
        qr1.setClosestEdge(edge1);
        qr1.setWayIndex(0);
        qr1.setSnappedPosition(QueryResult.Position.EDGE);
        qr1.calcSnappedPoint(DistancePlaneProjection.DIST_PLANE);

        QueryResult qr2 = new QueryResult(50.00, 10.15);
        qr2.setClosestEdge(edge2);
        qr2.setWayIndex(0);
        qr2.setSnappedPosition(QueryResult.Position.EDGE);
        qr2.calcSnappedPoint(DistancePlaneProjection.DIST_PLANE);

        QueryGraph queryGraph = QueryGraph.create(graph, Arrays.asList(qr1, qr2));
        QueryRoutingCHGraph queryCHGraph = new QueryRoutingCHGraph(routingCHGraph, queryGraph);
        assertEquals(5, queryCHGraph.getTurnWeight(0, 1, 1));

        // take a look at edges 3->1 and 1->4, their original edge ids are 3 and 4 (not 4 and 5)
        assertNodesConnected(queryCHGraph, 3, 1, true);
        assertNodesConnected(queryCHGraph, 1, 4, true);
        int expectedEdge31 = 3;
        int expectedEdge14 = 4;
        RoutingCHEdgeIterator iter = queryCHGraph.createOutEdgeExplorer().setBaseNode(3);
        assertNextEdge(iter, 3, 0, 2);
        assertNextEdge(iter, 3, 1, expectedEdge31);
        assertEnd(iter);

        iter = queryCHGraph.createOutEdgeExplorer().setBaseNode(1);
        assertNextEdge(iter, 1, 3, 3);
        assertNextEdge(iter, 1, 4, expectedEdge14);
        assertEnd(iter);

        // check the turn weight between these edges
        assertEquals(5, queryCHGraph.getTurnWeight(expectedEdge31, 1, expectedEdge14));
    }

    private void assertGetEdgeIteratorState(RoutingCHGraph graph, int base, int adj, int origEdge) {
        int chEdge = getCHEdge(graph.createOutEdgeExplorer(), base, adj);
        assertEdgeState(graph.getEdgeIteratorState(chEdge, adj), base, adj, origEdge);
        assertEdgeState(graph.getEdgeIteratorState(chEdge, base), adj, base, origEdge);
    }

    private void assertGetEdgeIteratorShortcut(RoutingCHGraph graph, int base, int adj, int skip1, int skip2) {
        int chEdge = getCHEdge(graph.createOutEdgeExplorer(), base, adj);
        assertShortcut(graph.getEdgeIteratorState(chEdge, adj), base, adj, skip1, skip2);
        assertShortcut(graph.getEdgeIteratorState(chEdge, base), adj, base, skip1, skip2);
    }

    private void assertNextEdge(RoutingCHEdgeIterator iter, int base, int adj, int origEdge) {
        assertTrue(iter.next(), "there is no further edge");
        assertEdgeState(iter, base, adj, origEdge);
    }

    private void assertEdgeState(RoutingCHEdgeIteratorState edgeState, int base, int adj, int origEdge) {
        assertFalse(edgeState.isShortcut(), "did not expect a shortcut");
        assertEquals(base, edgeState.getBaseNode(), "wrong base node");
        assertEquals(adj, edgeState.getAdjNode(), "wrong adj node");
        assertEquals(origEdge, edgeState.getOrigEdge(), "wrong orig edge");
    }

    private void assertNextShortcut(RoutingCHEdgeIterator iter, int base, int adj, int skip1, int skip2) {
        assertTrue(iter.next(), "there is no further edge");
        assertShortcut(iter, base, adj, skip1, skip2);
    }

    private void assertShortcut(RoutingCHEdgeIteratorState edgeState, int base, int adj, int skip1, int skip2) {
        assertTrue(edgeState.isShortcut(), "expected a shortcut");
        assertEquals(base, edgeState.getBaseNode(), "wrong base node");
        assertEquals(adj, edgeState.getAdjNode(), "wrong adj node");
        assertEquals(NO_EDGE, edgeState.getOrigEdge(), "wrong orig edge");
        assertEquals(skip1, edgeState.getSkippedEdge1(), "wrong skip1 edge");
        assertEquals(skip2, edgeState.getSkippedEdge2(), "wrong skip2 edge");
    }

    private void assertNodesConnected(RoutingCHGraph graph, int p, int q, boolean bothDirections) {
        int chEdge = getCHEdge(graph.createOutEdgeExplorer(), p, q);
        assertNotEquals(NO_EDGE, chEdge, "No CH out-edge " + p + "->" + q);
        assertEdgeAtNodes(graph, chEdge, p, q);
        chEdge = getCHEdge(graph.createInEdgeExplorer(), q, p);
        assertNotEquals(NO_EDGE, chEdge, "No CH in-edge " + q + "<-" + p);
        assertEdgeAtNodes(graph, chEdge, p, q);

        int revCHEdge = getCHEdge(graph.createOutEdgeExplorer(), q, p);
        if (bothDirections) {
            assertNotEquals(NO_EDGE, revCHEdge, "No CH out-edge " + q + "->" + p);
            assertEdgeAtNodes(graph, revCHEdge, p, q);
        } else {
            assertEquals(NO_EDGE, revCHEdge, "Unexpected CH out-edge " + q + "->" + p);
        }
        revCHEdge = getCHEdge(graph.createInEdgeExplorer(), p, q);
        if (bothDirections) {
            assertNotEquals(NO_EDGE, revCHEdge, "No CH in-edge " + q + "<-" + p);
            assertEdgeAtNodes(graph, revCHEdge, p, q);
        } else {
            assertEquals(NO_EDGE, revCHEdge, "Unexpected CH in-edge " + q + "<-" + p);
        }
    }

    private int getCHEdge(RoutingCHEdgeExplorer explorer, int base, int adj) {
        RoutingCHEdgeIterator iter = explorer.setBaseNode(base);
        while (iter.next())
            if (iter.getAdjNode() == adj)
                return iter.getEdge();
        return NO_EDGE;
    }

    private void assertEdgeAtNodes(RoutingCHGraph graph, int shortcut, int p, int q) {
        assertEquals(p, graph.getOtherNode(shortcut, q));
        assertEquals(q, graph.getOtherNode(shortcut, p));
        assertTrue(graph.isAdjacentToNode(shortcut, p));
        assertTrue(graph.isAdjacentToNode(shortcut, q));
    }

    private void assertEnd(RoutingCHEdgeIterator outIter) {
        assertFalse(outIter.next());
    }

    private EdgeIteratorState addEdge(Graph graph, int from, int to) {
        NodeAccess na = graph.getNodeAccess();
        double dist = DistancePlaneProjection.DIST_PLANE.calcDist(na.getLat(from), na.getLon(from), na.getLat(to), na.getLon(to));
        return graph.edge(from, to, dist, true);
    }

}