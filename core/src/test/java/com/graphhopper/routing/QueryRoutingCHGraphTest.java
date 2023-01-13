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
import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.querygraph.QueryRoutingCHGraph;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.weighting.DefaultTurnCostProvider;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.storage.*;
import com.graphhopper.storage.index.Snap;
import com.graphhopper.core.util.DistancePlaneProjection;
import com.graphhopper.core.util.EdgeIteratorState;
import com.graphhopper.core.util.GHUtility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static com.graphhopper.core.util.EdgeIterator.NO_EDGE;
import static org.junit.jupiter.api.Assertions.*;

class QueryRoutingCHGraphTest {
    private BooleanEncodedValue accessEnc;
    private DecimalEncodedValue speedEnc;
    private DecimalEncodedValue turnCostEnc;
    private EncodingManager encodingManager;
    private FastestWeighting weighting;
    private BaseGraph graph;
    private NodeAccess na;

    @BeforeEach
    public void setup() {
        accessEnc = new SimpleBooleanEncodedValue("access", true);
        speedEnc = new DecimalEncodedValueImpl("speed", 5, 5, true);
        turnCostEnc = TurnCost.create("car", 5);
        encodingManager = EncodingManager.start().add(accessEnc).add(speedEnc).addTurnCostEncodedValue(turnCostEnc).build();
        graph = new BaseGraph.Builder(encodingManager).withTurnCosts(true).create();
        weighting = new FastestWeighting(accessEnc, speedEnc, new DefaultTurnCostProvider(turnCostEnc, graph.getTurnCostStorage()));
        na = graph.getNodeAccess();
    }

    @Test
    public void basic() {
        // 0-1-2
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(0, 1).setDistance(10));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(1, 2).setDistance(10));
        graph.freeze();
        assertEquals(2, graph.getEdges());

        CHConfig chConfig = CHConfig.edgeBased("x", weighting);
        CHStorage chStore = CHStorage.fromGraph(graph, chConfig);
        RoutingCHGraph routingCHGraph = RoutingCHGraphImpl.fromGraph(graph, chStore, chConfig);

        QueryGraph queryGraph = QueryGraph.create(graph, Collections.<Snap>emptyList());
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
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(0, 1).setDistance(10));
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(1, 2).setDistance(10));
        graph.freeze();
        assertEquals(2, graph.getEdges());

        CHConfig chConfig = CHConfig.edgeBased("x", weighting);
        CHStorage chStore = CHStorage.fromGraph(graph, chConfig);
        RoutingCHGraph routingCHGraph = RoutingCHGraphImpl.fromGraph(graph, chStore, chConfig);

        CHStorageBuilder chBuilder = new CHStorageBuilder(chStore);
        chBuilder.setIdentityLevels();
        chBuilder.addShortcutEdgeBased(0, 2, PrepareEncoder.getScFwdDir(), 20, 0, 1, 0, 2);

        QueryGraph queryGraph = QueryGraph.create(graph, Collections.emptyList());
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

        CHConfig chConfig = CHConfig.edgeBased("x", weighting);
        CHStorage chStore = CHStorage.fromGraph(graph, chConfig);
        RoutingCHGraph routingCHGraph = RoutingCHGraphImpl.fromGraph(graph, chStore, chConfig);

        Snap snap = new Snap(50.00, 10.05);
        snap.setClosestEdge(edge);
        snap.setWayIndex(0);
        snap.setSnappedPosition(Snap.Position.EDGE);
        snap.calcSnappedPoint(DistancePlaneProjection.DIST_PLANE);

        QueryGraph queryGraph = QueryGraph.create(graph, Collections.singletonList(snap));
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

        CHConfig chConfig = CHConfig.edgeBased("x", weighting);
        CHStorage chStore = CHStorage.fromGraph(graph, chConfig);
        RoutingCHGraph routingCHGraph = RoutingCHGraphImpl.fromGraph(graph, chStore, chConfig);
        CHStorageBuilder chBuilder = new CHStorageBuilder(chStore);

        chBuilder.setIdentityLevels();
        chBuilder.addShortcutEdgeBased(0, 2, PrepareEncoder.getScFwdDir(), 20, 0, 1, 0, 2);

        Snap snap = new Snap(50.00, 10.05);
        snap.setClosestEdge(edge);
        snap.setWayIndex(0);
        snap.setSnappedPosition(Snap.Position.EDGE);
        snap.calcSnappedPoint(DistancePlaneProjection.DIST_PLANE);

        QueryGraph queryGraph = QueryGraph.create(graph, Collections.singletonList(snap));
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
        GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(0, 1).setDistance(10));
        graph.freeze();

        CHConfig chConfig = CHConfig.edgeBased("x", weighting);
        CHStorage chStore = CHStorage.fromGraph(graph, chConfig);
        RoutingCHGraph routingCHGraph = RoutingCHGraphImpl.fromGraph(graph, chStore, chConfig);

        QueryGraph queryGraph = QueryGraph.create(graph, Collections.<Snap>emptyList());
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

        CHConfig chConfig = CHConfig.edgeBased("x", weighting);
        CHStorage chStore = CHStorage.fromGraph(graph, chConfig);
        RoutingCHGraph routingCHGraph = RoutingCHGraphImpl.fromGraph(graph, chStore, chConfig);
        CHStorageBuilder chBuilder = new CHStorageBuilder(chStore);

        chBuilder.setIdentityLevels();
        chBuilder.addShortcutEdgeBased(0, 2, PrepareEncoder.getScFwdDir(), 20, 0, 1, 0, 2);

        Snap snap = new Snap(50.00, 10.05);
        snap.setClosestEdge(edge);
        snap.setWayIndex(0);
        snap.setSnappedPosition(Snap.Position.EDGE);
        snap.calcSnappedPoint(DistancePlaneProjection.DIST_PLANE);

        QueryGraph queryGraph = QueryGraph.create(graph, Collections.singletonList(snap));
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
        graph.freeze();
        QueryGraph queryGraph = QueryGraph.create(graph, Collections.<Snap>emptyList());

        CHConfig chConfig = CHConfig.edgeBased("x", weighting);
        CHStorage chStore = CHStorage.fromGraph(graph, chConfig);
        RoutingCHGraph routingCHGraph = RoutingCHGraphImpl.fromGraph(graph, chStore, chConfig);

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

        CHConfig chConfig = CHConfig.edgeBased("x", weighting);
        CHStorage chStore = CHStorage.fromGraph(graph, chConfig);
        RoutingCHGraph routingCHGraph = RoutingCHGraphImpl.fromGraph(graph, chStore, chConfig);
        CHStorageBuilder chBuilder = new CHStorageBuilder(chStore);

        chBuilder.setLevel(0, 5);
        chBuilder.setLevel(1, 7);

        Snap snap = new Snap(50.00, 10.05);
        snap.setClosestEdge(edge);
        snap.setWayIndex(0);
        snap.setSnappedPosition(Snap.Position.EDGE);
        snap.calcSnappedPoint(DistancePlaneProjection.DIST_PLANE);

        QueryGraph queryGraph = QueryGraph.create(graph, Collections.singletonList(snap));
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
                .set(speedEnc, 90, 30);
        addEdge(graph, 1, 2);
        graph.freeze();

        CHConfig chConfig = CHConfig.edgeBased("x", weighting);
        CHStorage chStore = CHStorage.fromGraph(graph, chConfig);
        RoutingCHGraph routingCHGraph = RoutingCHGraphImpl.fromGraph(graph, chStore, chConfig);
        CHStorageBuilder chBuilder = new CHStorageBuilder(chStore);

        chBuilder.setIdentityLevels();
        chBuilder.addShortcutEdgeBased(0, 2, PrepareEncoder.getScDirMask(), 20, 0, 1, 0, 2);

        // without query graph
        RoutingCHEdgeIterator iter = routingCHGraph.createOutEdgeExplorer().setBaseNode(0);
        assertNextShortcut(iter, 0, 2, 0, 1);
        assertEquals(20, iter.getWeight(false), 1.e-6);
        assertEquals(20, iter.getWeight(true), 1.e-6);
        assertNextEdge(iter, 0, 1, 0);
        assertEquals(285.89888, iter.getWeight(false), 1.e-6);
        assertEquals(857.69664, iter.getWeight(true), 1.e-6);
        assertEnd(iter);

        // for incoming edges its the same
        iter = routingCHGraph.createInEdgeExplorer().setBaseNode(0);
        assertNextShortcut(iter, 0, 2, 0, 1);
        assertEquals(20, iter.getWeight(false), 1.e-6);
        assertEquals(20, iter.getWeight(true), 1.e-6);
        assertNextEdge(iter, 0, 1, 0);
        assertEquals(285.89888, iter.getWeight(false), 1.e-6);
        assertEquals(857.69664, iter.getWeight(true), 1.e-6);
        assertEnd(iter);

        // now including virtual edges
        Snap snap = new Snap(50.00, 10.05);
        snap.setClosestEdge(edge);
        snap.setWayIndex(0);
        snap.setSnappedPosition(Snap.Position.EDGE);
        snap.calcSnappedPoint(DistancePlaneProjection.DIST_PLANE);

        QueryGraph queryGraph = QueryGraph.create(graph, Collections.singletonList(snap));
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
        assertEquals(142.9494, iter.getWeight(false), 1.e-4);
        assertEquals(428.8483, iter.getWeight(true), 1.e-4);
        assertEnd(iter);

        iter = queryCHGraph.createInEdgeExplorer().setBaseNode(3);
        assertNextEdge(iter, 3, 0, 2);
        assertEquals(428.8483, iter.getWeight(false), 1.e-4);
        assertEquals(142.9494, iter.getWeight(true), 1.e-4);
        assertNextEdge(iter, 3, 1, 3);
        assertEquals(142.9494, iter.getWeight(false), 1.e-4);
        assertEquals(428.8483, iter.getWeight(true), 1.e-4);
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
        edge.set(accessEnc, true, false);
        edge.set(speedEnc, 60, 60);
        graph.freeze();

        CHConfig chConfig = CHConfig.edgeBased("x", weighting);
        CHStorage chStore = CHStorage.fromGraph(graph, chConfig);
        RoutingCHGraph routingCHGraph = RoutingCHGraphImpl.fromGraph(graph, chStore, chConfig);

        // without query graph
        // 0->1
        RoutingCHEdgeIterator iter = routingCHGraph.createOutEdgeExplorer().setBaseNode(0);
        assertNextEdge(iter, 0, 1, 0);
        assertEquals(428.8483, iter.getWeight(false), 1.e-4);
        assertEquals(Double.POSITIVE_INFINITY, iter.getWeight(true));
        assertEnd(iter);

        iter = routingCHGraph.createInEdgeExplorer().setBaseNode(1);
        assertNextEdge(iter, 1, 0, 0);
        assertEquals(Double.POSITIVE_INFINITY, iter.getWeight(false));
        assertEquals(428.8483, iter.getWeight(true), 1.e-4);
        assertEnd(iter);

        // single edges
        assertEquals(428.8483, routingCHGraph.getEdgeIteratorState(0, 1).getWeight(false), 1.e-4);
        assertEquals(Double.POSITIVE_INFINITY, routingCHGraph.getEdgeIteratorState(0, 1).getWeight(true));
        assertEquals(Double.POSITIVE_INFINITY, routingCHGraph.getEdgeIteratorState(0, 0).getWeight(false));
        assertEquals(428.8483, routingCHGraph.getEdgeIteratorState(0, 0).getWeight(true), 1.e-4);

        // with query graph
        // 0-x->1
        //   2
        Snap snap = new Snap(50.00, 10.05);
        snap.setClosestEdge(edge);
        snap.setWayIndex(0);
        snap.setSnappedPosition(Snap.Position.EDGE);
        snap.calcSnappedPoint(DistancePlaneProjection.DIST_PLANE);

        QueryGraph queryGraph = QueryGraph.create(graph, Collections.singletonList(snap));
        QueryRoutingCHGraph queryCHGraph = new QueryRoutingCHGraph(routingCHGraph, queryGraph);

        iter = queryCHGraph.createOutEdgeExplorer().setBaseNode(0);
        assertNextEdge(iter, 0, 2, 1);
        assertEquals(214.4241, iter.getWeight(false), 1.e-4);
        assertEquals(Double.POSITIVE_INFINITY, iter.getWeight(true));
        assertEnd(iter);

        iter = queryCHGraph.createInEdgeExplorer().setBaseNode(1);
        assertNextEdge(iter, 1, 2, 2);
        assertEquals(Double.POSITIVE_INFINITY, iter.getWeight(false));
        assertEquals(214.4241, iter.getWeight(true), 1.e-4);
        assertEnd(iter);

        // at virtual node
        iter = queryCHGraph.createInEdgeExplorer().setBaseNode(2);
        assertNextEdge(iter, 2, 0, 1);
        assertEquals(Double.POSITIVE_INFINITY, iter.getWeight(false));
        assertEquals(214.4241, iter.getWeight(true), 1.e-4);
        assertNextEdge(iter, 2, 1, 2);
        assertEquals(214.4241, iter.getWeight(false), 1.e-4);
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
        graph.getTurnCostStorage().set(turnCostEnc, 0, 1, 1, 5);
        graph.freeze();

        CHConfig chConfig = CHConfig.edgeBased("x", weighting);
        CHStorage chStore = CHStorage.fromGraph(graph, chConfig);
        RoutingCHGraph routingCHGraph = RoutingCHGraphImpl.fromGraph(graph, chStore, chConfig);
        CHStorageBuilder chBuilder = new CHStorageBuilder(chStore);

        chBuilder.setIdentityLevels();
        chBuilder.addShortcutEdgeBased(0, 2, PrepareEncoder.getScFwdDir(), 20, 0, 1, 0, 2);

        // without virtual nodes
        assertEquals(5, routingCHGraph.getTurnWeight(0, 1, 1));

        // with virtual nodes
        Snap snap1 = new Snap(50.00, 10.05);
        snap1.setClosestEdge(edge1);
        snap1.setWayIndex(0);
        snap1.setSnappedPosition(Snap.Position.EDGE);
        snap1.calcSnappedPoint(DistancePlaneProjection.DIST_PLANE);

        Snap snap2 = new Snap(50.00, 10.15);
        snap2.setClosestEdge(edge2);
        snap2.setWayIndex(0);
        snap2.setSnappedPosition(Snap.Position.EDGE);
        snap2.calcSnappedPoint(DistancePlaneProjection.DIST_PLANE);

        QueryGraph queryGraph = QueryGraph.create(graph, Arrays.asList(snap1, snap2));
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
        boolean fails = true;
        {
            RoutingCHEdgeIterator iter = graph.createOutEdgeExplorer().setBaseNode(p);
            while (iter.next())
                if (iter.getAdjNode() == q && iter.getEdge() == shortcut)
                    fails = false;
        }
        {
            RoutingCHEdgeIterator iter = graph.createInEdgeExplorer().setBaseNode(q);
            while (iter.next())
                if (iter.getAdjNode() == p && iter.getEdge() == shortcut)
                    fails = false;
        }
        assertFalse(fails);
    }

    private void assertEnd(RoutingCHEdgeIterator outIter) {
        assertFalse(outIter.next());
    }

    private EdgeIteratorState addEdge(Graph graph, int from, int to) {
        NodeAccess na = graph.getNodeAccess();
        double dist = DistancePlaneProjection.DIST_PLANE.calcDist(na.getLat(from), na.getLon(from), na.getLat(to), na.getLon(to));
        return GHUtility.setSpeed(60, true, true, accessEnc, speedEnc, graph.edge(from, to).setDistance(dist));
    }

}