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

import com.carrotsearch.hppc.IntObjectMap;
import com.graphhopper.routing.profiles.BooleanEncodedValue;
import com.graphhopper.routing.profiles.DecimalEncodedValue;
import com.graphhopper.routing.util.*;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.routing.weighting.TurnWeighting;
import com.graphhopper.storage.*;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.LocationIndexTree;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.*;
import com.graphhopper.util.shapes.GHPoint;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;

import static com.graphhopper.storage.index.QueryResult.Position.*;
import static org.junit.Assert.*;

/**
 * @author Peter Karich
 */
public class QueryGraphTest {
    private EncodingManager encodingManager;
    private FlagEncoder carEncoder;
    private GraphHopperStorage g;

    @Before
    public void setUp() {
        carEncoder = new CarFlagEncoder();
        encodingManager = EncodingManager.create(carEncoder);
        g = new GraphHopperStorage(new RAMDirectory(), encodingManager, false, new GraphExtension.NoOpExtension()).create(100);
    }

    @After
    public void tearDown() {
        g.close();
    }

    void initGraph(Graph g) {
        //
        //  /*-*\
        // 0     1
        // |
        // 2
        NodeAccess na = g.getNodeAccess();
        na.setNode(0, 1, 0);
        na.setNode(1, 1, 2.5);
        na.setNode(2, 0, 0);
        g.edge(0, 2, 10, true);
        g.edge(0, 1, 10, true).setWayGeometry(Helper.createPointList(1.5, 1, 1.5, 1.5));
    }

    @Test
    public void testOneVirtualNode() {
        initGraph(g);
        EdgeExplorer expl = g.createEdgeExplorer();

        // snap directly to tower node => pointList could get of size 1?!?      
        // a)
        EdgeIterator iter = expl.setBaseNode(2);
        iter.next();

        QueryGraph queryGraph = new QueryGraph(g);
        QueryResult res = createLocationResult(1, -1, iter, 0, TOWER);
        queryGraph.lookup(Arrays.asList(res));
        assertEquals(new GHPoint(0, 0), res.getSnappedPoint());

        // b)
        res = createLocationResult(1, -1, iter, 1, TOWER);
        queryGraph = new QueryGraph(g);
        queryGraph.lookup(Arrays.asList(res));
        assertEquals(new GHPoint(1, 0), res.getSnappedPoint());
        // c)
        iter = expl.setBaseNode(1);
        iter.next();
        res = createLocationResult(1.2, 2.7, iter, 0, TOWER);
        queryGraph = new QueryGraph(g);
        queryGraph.lookup(Arrays.asList(res));
        assertEquals(new GHPoint(1, 2.5), res.getSnappedPoint());

        // node number stays
        assertEquals(3, queryGraph.getNodes());

        // snap directly to pillar node
        queryGraph = new QueryGraph(g);
        iter = expl.setBaseNode(1);
        iter.next();
        res = createLocationResult(2, 1.5, iter, 1, PILLAR);
        queryGraph.lookup(Arrays.asList(res));
        assertEquals(new GHPoint(1.5, 1.5), res.getSnappedPoint());
        assertEquals(3, res.getClosestNode());
        assertEquals(3, getPoints(queryGraph, 0, 3).getSize());
        assertEquals(2, getPoints(queryGraph, 3, 1).getSize());

        queryGraph = new QueryGraph(g);
        res = createLocationResult(2, 1.7, iter, 1, PILLAR);
        queryGraph.lookup(Arrays.asList(res));
        assertEquals(new GHPoint(1.5, 1.5), res.getSnappedPoint());
        assertEquals(3, res.getClosestNode());
        assertEquals(3, getPoints(queryGraph, 0, 3).getSize());
        assertEquals(2, getPoints(queryGraph, 3, 1).getSize());

        // snap to edge which has pillar nodes        
        queryGraph = new QueryGraph(g);
        res = createLocationResult(1.5, 2, iter, 0, EDGE);
        queryGraph.lookup(Arrays.asList(res));
        assertEquals(new GHPoint(1.300019, 1.899962), res.getSnappedPoint());
        assertEquals(3, res.getClosestNode());
        assertEquals(4, getPoints(queryGraph, 0, 3).getSize());
        assertEquals(2, getPoints(queryGraph, 3, 1).getSize());

        // snap to edge which has no pillar nodes
        queryGraph = new QueryGraph(g);
        iter = expl.setBaseNode(2);
        iter.next();
        res = createLocationResult(0.5, 0.1, iter, 0, EDGE);
        queryGraph.lookup(Arrays.asList(res));
        assertEquals(new GHPoint(0.5, 0), res.getSnappedPoint());
        assertEquals(3, res.getClosestNode());
        assertEquals(2, getPoints(queryGraph, 0, 3).getSize());
        assertEquals(2, getPoints(queryGraph, 3, 2).getSize());
    }

    @Test
    public void testFillVirtualEdges() {
        initGraph(g);
        g.getNodeAccess().setNode(3, 0, 1);
        g.edge(1, 3);

        final int baseNode = 1;
        EdgeIterator iter = g.createEdgeExplorer().setBaseNode(baseNode);
        iter.next();
        QueryResult res1 = createLocationResult(2, 1.7, iter, 1, PILLAR);
        QueryGraph queryGraph = new QueryGraph(g) {

            @Override
            void fillVirtualEdges(IntObjectMap<VirtualEdgeIterator> node2Edge, int towerNode, EdgeExplorer mainExpl) {
                super.fillVirtualEdges(node2Edge, towerNode, mainExpl);
                // ignore nodes should include baseNode == 1
                if (towerNode == 3)
                    assertEquals("[3->4]", node2Edge.get(towerNode).toString());
                else if (towerNode == 1)
                    assertEquals("[1->4, 1 1-0]", node2Edge.get(towerNode).toString());
                else
                    throw new IllegalStateException("not allowed " + towerNode);
            }
        };
        queryGraph.lookup(Arrays.asList(res1));
        EdgeIteratorState state = GHUtility.getEdge(queryGraph, 0, 1);
        assertEquals(4, state.fetchWayGeometry(3).size());

        // fetch virtual edge and check way geometry
        state = GHUtility.getEdge(queryGraph, 4, 3);
        assertEquals(2, state.fetchWayGeometry(3).size());
    }

    @Test
    public void testMultipleVirtualNodes() {
        initGraph(g);

        // snap to edge which has pillar nodes        
        EdgeIterator iter = g.createEdgeExplorer().setBaseNode(1);
        iter.next();
        QueryResult res1 = createLocationResult(2, 1.7, iter, 1, PILLAR);
        QueryGraph queryGraph = new QueryGraph(g);
        queryGraph.lookup(Arrays.asList(res1));
        assertEquals(new GHPoint(1.5, 1.5), res1.getSnappedPoint());
        assertEquals(3, res1.getClosestNode());
        assertEquals(3, getPoints(queryGraph, 0, 3).getSize());
        PointList pl = getPoints(queryGraph, 3, 1);
        assertEquals(2, pl.getSize());
        assertEquals(new GHPoint(1.5, 1.5), pl.toGHPoint(0));
        assertEquals(new GHPoint(1, 2.5), pl.toGHPoint(1));

        EdgeIteratorState edge = GHUtility.getEdge(queryGraph, 3, 1);
        assertNotNull(queryGraph.getEdgeIteratorState(edge.getEdge(), 3));
        assertNotNull(queryGraph.getEdgeIteratorState(edge.getEdge(), 1));

        edge = GHUtility.getEdge(queryGraph, 3, 0);
        assertNotNull(queryGraph.getEdgeIteratorState(edge.getEdge(), 3));
        assertNotNull(queryGraph.getEdgeIteratorState(edge.getEdge(), 0));

        // snap again => new virtual node on same edge!
        iter = g.createEdgeExplorer().setBaseNode(1);
        iter.next();
        res1 = createLocationResult(2, 1.7, iter, 1, PILLAR);
        QueryResult res2 = createLocationResult(1.5, 2, iter, 0, EDGE);
        queryGraph = new QueryGraph(g);
        queryGraph.lookup(Arrays.asList(res1, res2));
        assertEquals(4, res2.getClosestNode());
        assertEquals(new GHPoint(1.300019, 1.899962), res2.getSnappedPoint());
        assertEquals(3, res1.getClosestNode());
        assertEquals(new GHPoint(1.5, 1.5), res1.getSnappedPoint());

        assertEquals(3, getPoints(queryGraph, 3, 0).getSize());
        assertEquals(2, getPoints(queryGraph, 3, 4).getSize());
        assertEquals(2, getPoints(queryGraph, 4, 1).getSize());
        assertNull(GHUtility.getEdge(queryGraph, 4, 0));
        assertNull(GHUtility.getEdge(queryGraph, 3, 1));
    }

    @Test
    public void testOneWay() {
        NodeAccess na = g.getNodeAccess();
        na.setNode(0, 0, 0);
        na.setNode(1, 0, 1);
        g.edge(0, 1, 10, false);

        EdgeIteratorState edge = GHUtility.getEdge(g, 0, 1);
        QueryResult res1 = createLocationResult(0.1, 0.1, edge, 0, EDGE);
        QueryResult res2 = createLocationResult(0.1, 0.9, edge, 0, EDGE);
        QueryGraph queryGraph = new QueryGraph(g);
        queryGraph.lookup(Arrays.asList(res2, res1));
        assertEquals(2, res1.getClosestNode());
        assertEquals(new GHPoint(0, 0.1), res1.getSnappedPoint());
        assertEquals(3, res2.getClosestNode());
        assertEquals(new GHPoint(0, 0.9), res2.getSnappedPoint());

        assertEquals(2, getPoints(queryGraph, 0, 2).getSize());
        assertEquals(2, getPoints(queryGraph, 2, 3).getSize());
        assertEquals(2, getPoints(queryGraph, 3, 1).getSize());
        assertNull(GHUtility.getEdge(queryGraph, 3, 0));
        assertNull(GHUtility.getEdge(queryGraph, 2, 1));
    }

    @Test
    public void testVirtEdges() {
        initGraph(g);

        EdgeIterator iter = g.createEdgeExplorer().setBaseNode(0);
        iter.next();

        VirtualEdgeIterator vi = new VirtualEdgeIterator(2);
        vi.add(iter.detach(false));

        assertTrue(vi.next());
    }

    @Test
    public void testUseMeanElevation() {
        g.close();
        g = new GraphHopperStorage(new RAMDirectory(), encodingManager, true, new GraphExtension.NoOpExtension()).create(100);
        NodeAccess na = g.getNodeAccess();
        na.setNode(0, 0, 0, 0);
        na.setNode(1, 0, 0.0001, 20);
        EdgeIteratorState edge = g.edge(0, 1);
        EdgeIteratorState edgeReverse = edge.detach(true);

        DistanceCalc2D distCalc = new DistanceCalc2D();
        QueryResult qr = new QueryResult(0, 0.00005);
        qr.setClosestEdge(edge);
        qr.setWayIndex(0);
        qr.setSnappedPosition(EDGE);
        qr.calcSnappedPoint(distCalc);
        assertEquals(10, qr.getSnappedPoint().getEle(), 1e-1);

        qr = new QueryResult(0, 0.00005);
        qr.setClosestEdge(edgeReverse);
        qr.setWayIndex(0);
        qr.setSnappedPosition(EDGE);
        qr.calcSnappedPoint(distCalc);
        assertEquals(10, qr.getSnappedPoint().getEle(), 1e-1);
    }

    @Test
    public void testLoopStreet_Issue151() {
        // do query at x should result in ignoring only the bottom edge 1-3 not the upper one => getNeighbors are 0, 5, 3 and not only 0, 5
        //
        // 0--1--3--4
        //    |  |
        //    x---
        //
        g.edge(0, 1, 10, true);
        g.edge(1, 3, 10, true);
        g.edge(3, 4, 10, true);
        EdgeIteratorState edge = g.edge(1, 3, 20, true).setWayGeometry(Helper.createPointList(-0.001, 0.001, -0.001, 0.002));
        AbstractRoutingAlgorithmTester.updateDistancesFor(g, 0, 0, 0);
        AbstractRoutingAlgorithmTester.updateDistancesFor(g, 1, 0, 0.001);
        AbstractRoutingAlgorithmTester.updateDistancesFor(g, 3, 0, 0.002);
        AbstractRoutingAlgorithmTester.updateDistancesFor(g, 4, 0, 0.003);

        QueryResult qr = new QueryResult(-0.0005, 0.001);
        qr.setClosestEdge(edge);
        qr.setWayIndex(1);
        qr.calcSnappedPoint(new DistanceCalc2D());

        QueryGraph qg = new QueryGraph(g);
        qg.lookup(Arrays.asList(qr));
        EdgeExplorer ee = qg.createEdgeExplorer();

        assertEquals(GHUtility.asSet(0, 5, 3), GHUtility.getNeighbors(ee.setBaseNode(1)));
    }

    @Test
    public void testOneWayLoop_Issue162() {
        // do query at x, where edge is oneway
        //
        // |\
        // | x
        // 0<-\
        // |
        // 1        
        NodeAccess na = g.getNodeAccess();
        na.setNode(0, 0, 0);
        na.setNode(1, 0, -0.001);
        g.edge(0, 1, 10, true);
        BooleanEncodedValue accessEnc = carEncoder.getAccessEnc();
        DecimalEncodedValue avSpeedEnc = carEncoder.getAverageSpeedEnc();
        // in the case of identical nodes the wayGeometry defines the direction!
        EdgeIteratorState edge = g.edge(0, 0).
                setDistance(100).
                set(accessEnc, true).setReverse(accessEnc, false).set(avSpeedEnc, 20.0).
                setWayGeometry(Helper.createPointList(0.001, 0, 0, 0.001));

        QueryResult qr = new QueryResult(0.0011, 0.0009);
        qr.setClosestEdge(edge);
        qr.setWayIndex(1);
        qr.calcSnappedPoint(new DistanceCalc2D());

        QueryGraph qg = new QueryGraph(g);
        qg.lookup(Arrays.asList(qr));
        EdgeExplorer ee = qg.createEdgeExplorer();
        assertTrue(qr.getClosestNode() > 1);
        assertEquals(2, GHUtility.count(ee.setBaseNode(qr.getClosestNode())));
        EdgeIterator iter = ee.setBaseNode(qr.getClosestNode());
        iter.next();
        assertTrue(iter.toString(), iter.get(accessEnc));
        assertFalse(iter.toString(), iter.getReverse(accessEnc));

        iter.next();
        assertFalse(iter.toString(), iter.get(accessEnc));
        assertTrue(iter.toString(), iter.getReverse(accessEnc));
    }

    @Test
    public void testEdgesShareOneNode() {
        initGraph(g);

        EdgeIteratorState iter = GHUtility.getEdge(g, 0, 2);
        QueryResult res1 = createLocationResult(0.5, 0, iter, 0, EDGE);
        iter = GHUtility.getEdge(g, 1, 0);
        QueryResult res2 = createLocationResult(1.5, 2, iter, 0, EDGE);
        QueryGraph queryGraph = new QueryGraph(g);
        queryGraph.lookup(Arrays.asList(res1, res2));
        assertEquals(new GHPoint(0.5, 0), res1.getSnappedPoint());
        assertEquals(new GHPoint(1.300019, 1.899962), res2.getSnappedPoint());
        assertNotNull(GHUtility.getEdge(queryGraph, 0, 4));
        assertNotNull(GHUtility.getEdge(queryGraph, 0, 3));
    }

    @Test
    public void testAvoidDuplicateVirtualNodesIfIdentical() {
        initGraph(g);

        EdgeIteratorState edgeState = GHUtility.getEdge(g, 0, 2);
        QueryResult res1 = createLocationResult(0.5, 0, edgeState, 0, EDGE);
        QueryResult res2 = createLocationResult(0.5, 0, edgeState, 0, EDGE);
        QueryGraph queryGraph = new QueryGraph(g);
        queryGraph.lookup(Arrays.asList(res1, res2));
        assertEquals(new GHPoint(0.5, 0), res1.getSnappedPoint());
        assertEquals(new GHPoint(0.5, 0), res2.getSnappedPoint());
        assertEquals(3, res1.getClosestNode());
        assertEquals(3, res2.getClosestNode());

        // force skip due to **tower** node snapping in phase 2, but no virtual edges should be created for res1
        edgeState = GHUtility.getEdge(g, 0, 1);
        res1 = createLocationResult(1, 0, edgeState, 0, EDGE);
        // now create virtual edges
        edgeState = GHUtility.getEdge(g, 0, 2);
        res2 = createLocationResult(0.5, 0, edgeState, 0, EDGE);
        queryGraph = new QueryGraph(g);
        queryGraph.lookup(Arrays.asList(res1, res2));
        // make sure only one virtual node was created
        assertEquals(queryGraph.getNodes(), g.getNodes() + 1);
        EdgeIterator iter = queryGraph.createEdgeExplorer().setBaseNode(0);
        assertEquals(GHUtility.asSet(1, 3), GHUtility.getNeighbors(iter));
    }

    @Test
    public void testGetEdgeProps() {
        initGraph(g);
        EdgeIteratorState e1 = GHUtility.getEdge(g, 0, 2);
        QueryGraph queryGraph = new QueryGraph(g);
        QueryResult res1 = createLocationResult(0.5, 0, e1, 0, EDGE);
        queryGraph.lookup(Arrays.asList(res1));
        // get virtual edge
        e1 = GHUtility.getEdge(queryGraph, res1.getClosestNode(), 0);
        EdgeIteratorState e2 = queryGraph.getEdgeIteratorState(e1.getEdge(), Integer.MIN_VALUE);
        assertEquals(e1.getEdge(), e2.getEdge());
    }

    PointList getPoints(Graph g, int base, int adj) {
        EdgeIteratorState edge = GHUtility.getEdge(g, base, adj);
        if (edge == null)
            throw new IllegalStateException("edge " + base + "-" + adj + " not found");
        return edge.fetchWayGeometry(3);
    }

    public QueryResult createLocationResult(double lat, double lon,
                                            EdgeIteratorState edge, int wayIndex, QueryResult.Position pos) {
        if (edge == null)
            throw new IllegalStateException("Specify edge != null");
        QueryResult tmp = new QueryResult(lat, lon);
        tmp.setClosestEdge(edge);
        tmp.setWayIndex(wayIndex);
        tmp.setSnappedPosition(pos);
        tmp.calcSnappedPoint(new DistanceCalcEarth());
        return tmp;
    }

    @Test
    public void testIteration_Issue163() {
        EdgeFilter outEdgeFilter = DefaultEdgeFilter.outEdges(encodingManager.getEncoder("car"));
        EdgeFilter inEdgeFilter = DefaultEdgeFilter.inEdges(encodingManager.getEncoder("car"));
        EdgeExplorer inExplorer = g.createEdgeExplorer(inEdgeFilter);
        EdgeExplorer outExplorer = g.createEdgeExplorer(outEdgeFilter);

        int nodeA = 0;
        int nodeB = 1;

        /* init test graph: one directional edge going from A to B, via virtual nodes C and D
         *
         *   (C)-(D)
         *  /       \
         * A         B
         */
        g.getNodeAccess().setNode(nodeA, 1, 0);
        g.getNodeAccess().setNode(nodeB, 1, 10);
        g.edge(nodeA, nodeB, 10, false).setWayGeometry(Helper.createPointList(1.5, 3, 1.5, 7));

        // assert the behavior for classic edgeIterator        
        assertEdgeIdsStayingEqual(inExplorer, outExplorer, nodeA, nodeB);

        // setup query results
        EdgeIteratorState it = GHUtility.getEdge(g, nodeA, nodeB);
        QueryResult res1 = createLocationResult(1.5, 3, it, 1, QueryResult.Position.EDGE);
        QueryResult res2 = createLocationResult(1.5, 7, it, 2, QueryResult.Position.EDGE);

        QueryGraph q = new QueryGraph(g);
        q.lookup(Arrays.asList(res1, res2));
        int nodeC = res1.getClosestNode();
        int nodeD = res2.getClosestNode();

        inExplorer = q.createEdgeExplorer(inEdgeFilter);
        outExplorer = q.createEdgeExplorer(outEdgeFilter);

        // assert the same behavior for queryGraph
        assertEdgeIdsStayingEqual(inExplorer, outExplorer, nodeA, nodeC);
        assertEdgeIdsStayingEqual(inExplorer, outExplorer, nodeC, nodeD);
        assertEdgeIdsStayingEqual(inExplorer, outExplorer, nodeD, nodeB);
    }

    private void assertEdgeIdsStayingEqual(EdgeExplorer inExplorer, EdgeExplorer outExplorer, int startNode, int endNode) {
        EdgeIterator it = outExplorer.setBaseNode(startNode);
        it.next();
        assertEquals(startNode, it.getBaseNode());
        assertEquals(endNode, it.getAdjNode());
        // we expect the edge id to be the same when exploring in backward direction
        int expectedEdgeId = it.getEdge();
        assertFalse(it.next());

        // backward iteration, edge id should remain the same!!
        it = inExplorer.setBaseNode(endNode);
        it.next();
        assertEquals(endNode, it.getBaseNode());
        assertEquals(startNode, it.getAdjNode());
        assertEquals("The edge id is not the same,", expectedEdgeId, it.getEdge());
        assertFalse(it.next());
    }

    @Test
    public void testTurnCostsProperlyPropagated_Issue282() {
        TurnCostExtension turnExt = new TurnCostExtension();
        FlagEncoder encoder = new CarFlagEncoder(5, 5, 15);

        GraphHopperStorage graphWithTurnCosts = new GraphHopperStorage(new RAMDirectory(),
                EncodingManager.create(encoder), false, turnExt).
                create(100);
        NodeAccess na = graphWithTurnCosts.getNodeAccess();
        na.setNode(0, .00, .00);
        na.setNode(1, .00, .01);
        na.setNode(2, .01, .01);

        EdgeIteratorState edge0 = graphWithTurnCosts.edge(0, 1, 10, true);
        EdgeIteratorState edge1 = graphWithTurnCosts.edge(2, 1, 10, true);

        QueryGraph qGraph = new QueryGraph(graphWithTurnCosts);
        FastestWeighting weighting = new FastestWeighting(encoder);
        TurnWeighting turnWeighting = new TurnWeighting(weighting, (TurnCostExtension) qGraph.getExtension());

        assertEquals(0, turnWeighting.calcTurnWeight(edge0.getEdge(), 1, edge1.getEdge()), .1);

        // now use turn costs and QueryGraph
        turnExt.addTurnInfo(edge0.getEdge(), 1, edge1.getEdge(), encoder.getTurnFlags(false, 10));
        assertEquals(10, turnWeighting.calcTurnWeight(edge0.getEdge(), 1, edge1.getEdge()), .1);

        QueryResult res1 = createLocationResult(0.000, 0.005, edge0, 0, QueryResult.Position.EDGE);
        QueryResult res2 = createLocationResult(0.005, 0.010, edge1, 0, QueryResult.Position.EDGE);

        qGraph.lookup(Arrays.asList(res1, res2));

        int fromQueryEdge = GHUtility.getEdge(qGraph, res1.getClosestNode(), 1).getEdge();
        int toQueryEdge = GHUtility.getEdge(qGraph, res2.getClosestNode(), 1).getEdge();

        assertEquals(10, turnWeighting.calcTurnWeight(fromQueryEdge, 1, toQueryEdge), .1);

        graphWithTurnCosts.close();
    }

    private void initHorseshoeGraph(Graph g) {
        // setup graph
        //   ____
        //  |    |
        //  |    |
        //  0    1
        NodeAccess na = g.getNodeAccess();
        na.setNode(0, 0, 0);
        na.setNode(1, 0, 2);
        g.edge(0, 1, 10, true).setWayGeometry(Helper.createPointList(2, 0, 2, 2));
    }

    private QueryResult fakeEdgeQueryResult(EdgeIteratorState edge, double lat, double lon, int wayIndex) {
        QueryResult qr = new QueryResult(lat, lon);
        qr.setClosestEdge(edge);
        qr.setWayIndex(wayIndex);
        qr.setSnappedPosition(EDGE);
        qr.calcSnappedPoint(new DistanceCalc2D());
        return qr;
    }

    private boolean isAvoidEdge(QueryGraph queryGraph, int virtualEdgeTypeId) {
        return queryGraph.virtualEdges.get(virtualEdgeTypeId).get(EdgeIteratorState.UNFAVORED_EDGE);
    }

    @Test
    public void testEnforceHeading() {

        initHorseshoeGraph(g);
        EdgeIteratorState edge = GHUtility.getEdge(g, 0, 1);

        // query result on first vertical part of way (upward)
        QueryResult qr = fakeEdgeQueryResult(edge, 1.5, 0, 0);
        QueryGraph queryGraph = new QueryGraph(g);
        queryGraph.lookup(Arrays.asList(qr));

        // enforce going out north
        queryGraph.enforceHeading(qr.getClosestNode(), 0., false);
        // test penalized south
        boolean expect = true;
        assertEquals(expect, isAvoidEdge(queryGraph, QueryGraph.VE_BASE_REV));
        assertEquals(expect, isAvoidEdge(queryGraph, QueryGraph.VE_BASE));

        queryGraph.clearUnfavoredStatus();
        // test cleared edges south
        expect = false;
        assertEquals(expect, isAvoidEdge(queryGraph, QueryGraph.VE_BASE_REV));
        assertEquals(expect, isAvoidEdge(queryGraph, QueryGraph.VE_BASE));

        // enforce coming in north
        queryGraph.enforceHeading(qr.getClosestNode(), 180., true);
        // test penalized south
        expect = true;
        assertEquals(expect, isAvoidEdge(queryGraph, QueryGraph.VE_BASE_REV));
        assertEquals(expect, isAvoidEdge(queryGraph, QueryGraph.VE_BASE));

        // query result on second vertical part of way (downward)
        qr = fakeEdgeQueryResult(edge, 1.5, 2, 2);
        queryGraph = new QueryGraph(g);
        queryGraph.lookup(Arrays.asList(qr));

        // enforce going north
        queryGraph.enforceHeading(qr.getClosestNode(), 0., false);
        // test penalized south
        expect = true;
        assertEquals(expect, isAvoidEdge(queryGraph, QueryGraph.VE_ADJ));
        assertEquals(expect, isAvoidEdge(queryGraph, QueryGraph.VE_ADJ_REV));

        queryGraph.clearUnfavoredStatus();
        // enforce coming in north
        queryGraph.enforceHeading(qr.getClosestNode(), 180., true);
        // test penalized south
        expect = true;
        assertEquals(expect, isAvoidEdge(queryGraph, QueryGraph.VE_ADJ));
        assertEquals(expect, isAvoidEdge(queryGraph, QueryGraph.VE_ADJ_REV));
    }

    @Test
    public void testunfavorVirtualEdgePair() {

        initHorseshoeGraph(g);
        EdgeIteratorState edge = GHUtility.getEdge(g, 0, 1);

        // query result on first vertical part of way (upward)
        QueryResult qr = fakeEdgeQueryResult(edge, 1.5, 0, 0);
        QueryGraph queryGraph = new QueryGraph(g);
        queryGraph.lookup(Arrays.asList(qr));

        // enforce coming in north
        queryGraph.unfavorVirtualEdgePair(2, 1);
        // test penalized south
        VirtualEdgeIteratorState incomingEdge = (VirtualEdgeIteratorState) queryGraph.getEdgeIteratorState(1, 2);
        VirtualEdgeIteratorState incomingEdgeReverse = (VirtualEdgeIteratorState) queryGraph.getEdgeIteratorState(1, incomingEdge.getBaseNode());
        boolean expect = true;  // expect incoming and reverse incoming edge to be avoided
        assertEquals(expect, incomingEdge.get(EdgeIteratorState.UNFAVORED_EDGE));
        assertEquals(expect, incomingEdgeReverse.get(EdgeIteratorState.UNFAVORED_EDGE));
        assertEquals(new LinkedHashSet<>(Arrays.asList(incomingEdge, incomingEdgeReverse)),
                queryGraph.getUnfavoredVirtualEdges());

        queryGraph.clearUnfavoredStatus();
        expect = false; // expect incoming and reverse incoming edge not to be avoided
        assertEquals(expect, incomingEdge.get(EdgeIteratorState.UNFAVORED_EDGE));
        assertEquals(expect, incomingEdgeReverse.get(EdgeIteratorState.UNFAVORED_EDGE));
        assertEquals(new LinkedHashSet<>(), queryGraph.getUnfavoredVirtualEdges());
    }

    @Test
    public void testInternalAPIOriginalEdgeKey() {
        initGraph(g);

        EdgeExplorer explorer = g.createEdgeExplorer();
        QueryGraph queryGraph = new QueryGraph(g);
        EdgeIterator iter = explorer.setBaseNode(1);
        assertTrue(iter.next());
        int origEdgeId = iter.getEdge();
        QueryResult res = createLocationResult(2, 1.5, iter, 1, PILLAR);
        queryGraph.lookup(Arrays.asList(res));

        assertEquals(new GHPoint(1.5, 1.5), res.getSnappedPoint());
        assertEquals(3, res.getClosestNode());

        EdgeExplorer qGraphExplorer = queryGraph.createEdgeExplorer();
        iter = qGraphExplorer.setBaseNode(3);
        assertTrue(iter.next());
        assertEquals(0, iter.getAdjNode());
        assertEquals(GHUtility.createEdgeKey(1, 0, origEdgeId, false),
                ((VirtualEdgeIteratorState) queryGraph.getEdgeIteratorState(iter.getEdge(), 0)).getOriginalEdgeKey());

        assertTrue(iter.next());
        assertEquals(1, iter.getAdjNode());
        assertEquals(GHUtility.createEdgeKey(0, 1, origEdgeId, false),
                ((VirtualEdgeIteratorState) queryGraph.getEdgeIteratorState(iter.getEdge(), 1)).getOriginalEdgeKey());
    }

    @Test
    public void useEECache() {
        initGraph(g);
        EdgeExplorer explorer = g.createEdgeExplorer();
        EdgeIterator iter = explorer.setBaseNode(1);
        assertTrue(iter.next());
        QueryResult res = createLocationResult(2, 1.5, iter, 1, PILLAR);

        QueryGraph queryGraph = new QueryGraph(g).setUseEdgeExplorerCache(true);
        queryGraph.lookup(Arrays.asList(res));

        EdgeExplorer edgeExplorer = queryGraph.createEdgeExplorer();
        // using cache means same reference
        assertTrue(edgeExplorer == queryGraph.createEdgeExplorer());
    }

    @Test
    public void testWayGeometry_edge() {
        // drawn as horizontal linear graph for simplicity
        // 0 - * - x - * - 1
        NodeAccess na = g.getNodeAccess();
        na.setNode(0, 0, 0);
        na.setNode(1, 0.3, 0.3);
        g.edge(0, 1, 10, true).setWayGeometry(Helper.createPointList(0.1, 0.1, 0.2, 0.2));

        QueryGraph queryGraph = new QueryGraph(g);
        LocationIndex locationIndex = new LocationIndexTree(g, new RAMDirectory());
        locationIndex.prepareIndex();
        QueryResult qr = locationIndex.findClosest(0.15, 0.15, DefaultEdgeFilter.allEdges(carEncoder));
        assertTrue(qr.isValid());
        assertEquals("this test was supposed to test the Position.EDGE case", EDGE, qr.getSnappedPosition());
        queryGraph.lookup(Collections.singletonList(qr));
        EdgeIterator iter = queryGraph.createEdgeExplorer().setBaseNode(qr.getClosestNode());

        assertTrue(iter.next());
        assertEquals(0, iter.getAdjNode());
        assertEquals(1, iter.fetchWayGeometry(0).size());
        assertEquals(2, iter.fetchWayGeometry(1).size());
        assertEquals(2, iter.fetchWayGeometry(2).size());
        assertEquals(3, iter.fetchWayGeometry(3).size());
        assertEquals(Helper.createPointList(0.15, 0.15, 0.1, 0.1, 0.0, 0.0), iter.fetchWayGeometry(3));

        assertTrue(iter.next());
        assertEquals(1, iter.getAdjNode());
        assertEquals(1, iter.fetchWayGeometry(0).size());
        assertEquals(2, iter.fetchWayGeometry(1).size());
        assertEquals(2, iter.fetchWayGeometry(2).size());
        assertEquals(3, iter.fetchWayGeometry(3).size());
        assertEquals(Helper.createPointList(0.15, 0.15, 0.2, 0.2, 0.3, 0.3), iter.fetchWayGeometry(3));

        assertFalse(iter.next());
    }

    @Test
    public void testWayGeometry_pillar() {
        //   1
        //    \
        //     * x
        //    /
        //   *
        //  /
        // 0
        NodeAccess na = g.getNodeAccess();
        na.setNode(0, 0, 0);
        na.setNode(1, 0.5, 0.1);
        g.edge(0, 1, 10, true).setWayGeometry(Helper.createPointList(0.1, 0.1, 0.2, 0.2));

        QueryGraph queryGraph = new QueryGraph(g);
        LocationIndex locationIndex = new LocationIndexTree(g, new RAMDirectory());
        locationIndex.prepareIndex();
        QueryResult qr = locationIndex.findClosest(0.2, 0.21, DefaultEdgeFilter.allEdges(carEncoder));
        assertTrue(qr.isValid());
        assertEquals("this test was supposed to test the Position.PILLAR case", PILLAR, qr.getSnappedPosition());
        queryGraph.lookup(Collections.singletonList(qr));
        EdgeIterator iter = queryGraph.createEdgeExplorer().setBaseNode(qr.getClosestNode());

        assertTrue(iter.next());
        assertEquals(0, iter.getAdjNode());
        assertEquals(1, iter.fetchWayGeometry(0).size());
        assertEquals(2, iter.fetchWayGeometry(1).size());
        assertEquals(2, iter.fetchWayGeometry(2).size());
        assertEquals(3, iter.fetchWayGeometry(3).size());
        assertEquals(Helper.createPointList(0.2, 0.2, 0.1, 0.1, 0.0, 0.0), iter.fetchWayGeometry(3));

        assertTrue(iter.next());
        assertEquals(1, iter.getAdjNode());
        assertEquals(0, iter.fetchWayGeometry(0).size());
        assertEquals(1, iter.fetchWayGeometry(1).size());
        assertEquals(1, iter.fetchWayGeometry(2).size());
        assertEquals(2, iter.fetchWayGeometry(3).size());
        assertEquals(Helper.createPointList(0.2, 0.2, 0.5, 0.1), iter.fetchWayGeometry(3));

        assertFalse(iter.next());
    }

}
