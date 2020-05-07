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
package com.graphhopper.routing.querygraph;

import com.carrotsearch.hppc.IntObjectMap;
import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.TurnCost;
import com.graphhopper.routing.util.*;
import com.graphhopper.routing.weighting.DefaultTurnCostProvider;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.routing.weighting.Weighting;
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
import java.util.List;

import static com.graphhopper.storage.index.QueryResult.Position.*;
import static com.graphhopper.util.GHUtility.updateDistancesFor;
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
        g = new GraphHopperStorage(new RAMDirectory(), encodingManager, false).create(100);
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

        QueryResult res = createLocationResult(1, -1, iter, 0, TOWER);
        QueryGraph queryGraph0 = lookup(res);
        assertEquals(new GHPoint(0, 0), res.getSnappedPoint());

        // b)
        res = createLocationResult(1, -1, iter, 1, TOWER);
        QueryGraph queryGraph1 = lookup(res);
        assertEquals(new GHPoint(1, 0), res.getSnappedPoint());
        // c)
        iter = expl.setBaseNode(1);
        iter.next();
        res = createLocationResult(1.2, 2.7, iter, 0, TOWER);
        QueryGraph queryGraph2 = lookup(res);
        assertEquals(new GHPoint(1, 2.5), res.getSnappedPoint());

        // node number stays
        assertEquals(3, queryGraph2.getNodes());

        // snap directly to pillar node
        iter = expl.setBaseNode(1);
        iter.next();
        res = createLocationResult(2, 1.5, iter, 1, PILLAR);
        QueryGraph queryGraph3 = lookup(res);
        assertEquals(new GHPoint(1.5, 1.5), res.getSnappedPoint());
        assertEquals(3, res.getClosestNode());
        assertEquals(3, getPoints(queryGraph3, 0, 3).getSize());
        assertEquals(2, getPoints(queryGraph3, 3, 1).getSize());

        res = createLocationResult(2, 1.7, iter, 1, PILLAR);
        QueryGraph queryGraph4 = lookup(res);
        assertEquals(new GHPoint(1.5, 1.5), res.getSnappedPoint());
        assertEquals(3, res.getClosestNode());
        assertEquals(3, getPoints(queryGraph4, 0, 3).getSize());
        assertEquals(2, getPoints(queryGraph4, 3, 1).getSize());

        // snap to edge which has pillar nodes        
        res = createLocationResult(1.5, 2, iter, 0, EDGE);
        QueryGraph queryGraph5 = lookup(res);
        assertEquals(new GHPoint(1.300019, 1.899962), res.getSnappedPoint());
        assertEquals(3, res.getClosestNode());
        assertEquals(4, getPoints(queryGraph5, 0, 3).getSize());
        assertEquals(2, getPoints(queryGraph5, 3, 1).getSize());

        // snap to edge which has no pillar nodes
        iter = expl.setBaseNode(2);
        iter.next();
        res = createLocationResult(0.5, 0.1, iter, 0, EDGE);
        QueryGraph queryGraph6 = lookup(res);
        assertEquals(new GHPoint(0.5, 0), res.getSnappedPoint());
        assertEquals(3, res.getClosestNode());
        assertEquals(2, getPoints(queryGraph6, 0, 3).getSize());
        assertEquals(2, getPoints(queryGraph6, 3, 2).getSize());
    }

    @Test
    public void testFillVirtualEdges() {
        //       x (4)
        //  /*-*\
        // 0     1
        // |    /
        // 2  3
        NodeAccess na = g.getNodeAccess();
        na.setNode(0, 1, 0);
        na.setNode(1, 1, 2.5);
        na.setNode(2, 0, 0);
        na.setNode(3, 0, 1);
        g.edge(0, 2, 10, true);
        g.edge(0, 1, 10, true).setWayGeometry(Helper.createPointList(1.5, 1, 1.5, 1.5));
        g.edge(1, 3);

        final int baseNode = 1;
        EdgeIterator iter = g.createEdgeExplorer().setBaseNode(baseNode);
        iter.next();
        // note that we do not really do a location index lookup, but rather create a query result artificially, also
        // this query result is not very intuitive as we would expect snapping to the 1-0 edge, but this is how this
        // test was written initially...
        QueryResult qr = createLocationResult(2, 1.7, iter, 1, PILLAR);
        QueryOverlay queryOverlay = QueryOverlayBuilder.build(g, Collections.singletonList(qr));
        IntObjectMap<QueryOverlay.EdgeChanges> realNodeModifications = queryOverlay.getEdgeChangesAtRealNodes();
        assertEquals(2, realNodeModifications.size());
        // ignore nodes should include baseNode == 1
        assertEquals("[3->4]", realNodeModifications.get(3).getAdditionalEdges().toString());
        assertEquals("[2]", realNodeModifications.get(3).getRemovedEdges().toString());
        assertEquals("[1->4]", realNodeModifications.get(1).getAdditionalEdges().toString());
        assertEquals("[2]", realNodeModifications.get(1).getRemovedEdges().toString());

        QueryGraph queryGraph = QueryGraph.create(g, qr);
        EdgeIteratorState state = GHUtility.getEdge(queryGraph, 0, 1);
        assertEquals(4, state.fetchWayGeometry(FetchMode.ALL).size());

        //  fetch virtual edge and check way geometry
        state = GHUtility.getEdge(queryGraph, 4, 3);
        assertEquals(2, state.fetchWayGeometry(FetchMode.ALL).size());

        // now we actually test the edges at the real tower nodes (virtual ones should be added and some real ones removed)
        assertEquals("[1->4, 1 1-0]", ((VirtualEdgeIterator) queryGraph.createEdgeExplorer().setBaseNode(1)).getEdges().toString());
        assertEquals("[3->4]", ((VirtualEdgeIterator) queryGraph.createEdgeExplorer().setBaseNode(3)).getEdges().toString());
    }

    @Test
    public void testMultipleVirtualNodes() {
        initGraph(g);

        // snap to edge which has pillar nodes        
        EdgeIterator iter = g.createEdgeExplorer().setBaseNode(1);
        iter.next();
        QueryResult res1 = createLocationResult(2, 1.7, iter, 1, PILLAR);
        QueryGraph queryGraph = lookup(res1);
        assertEquals(new GHPoint(1.5, 1.5), res1.getSnappedPoint());
        assertEquals(3, res1.getClosestNode());
        assertEquals(3, getPoints(queryGraph, 0, 3).getSize());
        PointList pl = getPoints(queryGraph, 3, 1);
        assertEquals(2, pl.getSize());
        assertEquals(new GHPoint(1.5, 1.5), pl.get(0));
        assertEquals(new GHPoint(1, 2.5), pl.get(1));

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
        queryGraph = lookup(Arrays.asList(res1, res2));
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
        QueryGraph queryGraph = lookup(Arrays.asList(res2, res1));
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
        List<EdgeIteratorState> vEdges = Collections.singletonList(iter.detach(false));
        VirtualEdgeIterator vi = new VirtualEdgeIterator(EdgeFilter.ALL_EDGES, vEdges);
        assertTrue(vi.next());
    }

    @Test
    public void testUseMeanElevation() {
        g.close();
        g = new GraphHopperStorage(new RAMDirectory(), encodingManager, true).create(100);
        NodeAccess na = g.getNodeAccess();
        na.setNode(0, 0, 0, 0);
        na.setNode(1, 0, 0.0001, 20);
        EdgeIteratorState edge = g.edge(0, 1);
        EdgeIteratorState edgeReverse = edge.detach(true);

        DistanceCalcEuclidean distCalc = new DistanceCalcEuclidean();
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
        updateDistancesFor(g, 0, 0, 0);
        updateDistancesFor(g, 1, 0, 0.001);
        updateDistancesFor(g, 3, 0, 0.002);
        updateDistancesFor(g, 4, 0, 0.003);

        QueryResult qr = new QueryResult(-0.0005, 0.001);
        qr.setClosestEdge(edge);
        qr.setWayIndex(1);
        qr.calcSnappedPoint(new DistanceCalcEuclidean());

        QueryGraph qg = lookup(qr);
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
        qr.calcSnappedPoint(new DistanceCalcEuclidean());

        QueryGraph qg = lookup(qr);
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
        QueryGraph queryGraph = lookup(Arrays.asList(res1, res2));
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
        QueryGraph queryGraph = lookup(Arrays.asList(res1, res2));
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
        queryGraph = lookup(Arrays.asList(res1, res2));
        // make sure only one virtual node was created
        assertEquals(queryGraph.getNodes(), g.getNodes() + 1);
        EdgeIterator iter = queryGraph.createEdgeExplorer().setBaseNode(0);
        assertEquals(GHUtility.asSet(1, 3), GHUtility.getNeighbors(iter));
    }

    @Test
    public void testGetEdgeProps() {
        initGraph(g);
        EdgeIteratorState e1 = GHUtility.getEdge(g, 0, 2);
        QueryResult res1 = createLocationResult(0.5, 0, e1, 0, EDGE);
        QueryGraph queryGraph = lookup(res1);
        // get virtual edge
        e1 = GHUtility.getEdge(queryGraph, res1.getClosestNode(), 0);
        EdgeIteratorState e2 = queryGraph.getEdgeIteratorState(e1.getEdge(), Integer.MIN_VALUE);
        assertEquals(e1.getEdge(), e2.getEdge());
    }

    PointList getPoints(Graph g, int base, int adj) {
        EdgeIteratorState edge = GHUtility.getEdge(g, base, adj);
        if (edge == null)
            throw new IllegalStateException("edge " + base + "-" + adj + " not found");
        return edge.fetchWayGeometry(FetchMode.ALL);
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

        QueryGraph q = lookup(Arrays.asList(res1, res2));
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
        FlagEncoder encoder = new CarFlagEncoder(5, 5, 15);
        EncodingManager em = EncodingManager.create(encoder);
        GraphHopperStorage graphWithTurnCosts = new GraphHopperStorage(new RAMDirectory(), em, false, true).
                create(100);
        TurnCostStorage turnExt = graphWithTurnCosts.getTurnCostStorage();
        DecimalEncodedValue turnCostEnc = em.getDecimalEncodedValue(TurnCost.key(encoder.toString()));
        NodeAccess na = graphWithTurnCosts.getNodeAccess();
        na.setNode(0, .00, .00);
        na.setNode(1, .00, .01);
        na.setNode(2, .01, .01);

        EdgeIteratorState edge0 = graphWithTurnCosts.edge(0, 1, 10, true);
        EdgeIteratorState edge1 = graphWithTurnCosts.edge(2, 1, 10, true);

        Weighting weighting = new FastestWeighting(encoder, new DefaultTurnCostProvider(encoder, graphWithTurnCosts.getTurnCostStorage()));

        // no turn costs initially
        assertEquals(0, weighting.calcTurnWeight(edge0.getEdge(), 1, edge1.getEdge()), .1);

        // now use turn costs
        turnExt.set(turnCostEnc, edge0.getEdge(), 1, edge1.getEdge(), 10);
        assertEquals(10, weighting.calcTurnWeight(edge0.getEdge(), 1, edge1.getEdge()), .1);

        // now use turn costs with query graph
        QueryResult res1 = createLocationResult(0.000, 0.005, edge0, 0, QueryResult.Position.EDGE);
        QueryResult res2 = createLocationResult(0.005, 0.010, edge1, 0, QueryResult.Position.EDGE);
        QueryGraph qGraph = QueryGraph.create(graphWithTurnCosts, res1, res2);
        weighting = qGraph.wrapWeighting(weighting);

        int fromQueryEdge = GHUtility.getEdge(qGraph, res1.getClosestNode(), 1).getEdge();
        int toQueryEdge = GHUtility.getEdge(qGraph, res2.getClosestNode(), 1).getEdge();

        assertEquals(10, weighting.calcTurnWeight(fromQueryEdge, 1, toQueryEdge), .1);

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
        qr.calcSnappedPoint(new DistanceCalcEuclidean());
        return qr;
    }

    private boolean isAvoidEdge(QueryGraph queryGraph, int virtualEdgeTypeId) {
        return queryGraph.getVirtualEdges().get(virtualEdgeTypeId).get(EdgeIteratorState.UNFAVORED_EDGE);
    }

    @Test
    public void testEnforceHeading() {

        initHorseshoeGraph(g);
        EdgeIteratorState edge = GHUtility.getEdge(g, 0, 1);

        // query result on first vertical part of way (upward)
        QueryResult qr = fakeEdgeQueryResult(edge, 1.5, 0, 0);
        QueryGraph queryGraph = lookup(qr);

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
        queryGraph = lookup(Arrays.asList(qr));

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
        QueryGraph queryGraph = lookup(qr);

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
        EdgeIterator iter = explorer.setBaseNode(1);
        assertTrue(iter.next());
        int origEdgeId = iter.getEdge();
        QueryResult res = createLocationResult(2, 1.5, iter, 1, PILLAR);
        QueryGraph queryGraph = lookup(res);

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
    public void testWayGeometry_edge() {
        // drawn as horizontal linear graph for simplicity
        // 0 - * - x - * - 1
        NodeAccess na = g.getNodeAccess();
        na.setNode(0, 0, 0);
        na.setNode(1, 0.3, 0.3);
        g.edge(0, 1, 10, true).setWayGeometry(Helper.createPointList(0.1, 0.1, 0.2, 0.2));

        LocationIndex locationIndex = new LocationIndexTree(g, new RAMDirectory());
        locationIndex.prepareIndex();
        QueryResult qr = locationIndex.findClosest(0.15, 0.15, DefaultEdgeFilter.allEdges(carEncoder));
        assertTrue(qr.isValid());
        assertEquals("this test was supposed to test the Position.EDGE case", EDGE, qr.getSnappedPosition());
        QueryGraph queryGraph = lookup(qr);
        EdgeIterator iter = queryGraph.createEdgeExplorer().setBaseNode(qr.getClosestNode());

        assertTrue(iter.next());
        assertEquals(0, iter.getAdjNode());
        assertEquals(1, iter.fetchWayGeometry(FetchMode.PILLAR_ONLY).size());
        assertEquals(2, iter.fetchWayGeometry(FetchMode.BASE_AND_PILLAR).size());
        assertEquals(2, iter.fetchWayGeometry(FetchMode.PILLAR_AND_ADJ).size());
        assertEquals(3, iter.fetchWayGeometry(FetchMode.ALL).size());
        assertEquals(Helper.createPointList(0.15, 0.15, 0.1, 0.1, 0.0, 0.0), iter.fetchWayGeometry(FetchMode.ALL));

        assertTrue(iter.next());
        assertEquals(1, iter.getAdjNode());
        assertEquals(1, iter.fetchWayGeometry(FetchMode.PILLAR_ONLY).size());
        assertEquals(2, iter.fetchWayGeometry(FetchMode.BASE_AND_PILLAR).size());
        assertEquals(2, iter.fetchWayGeometry(FetchMode.PILLAR_AND_ADJ).size());
        assertEquals(3, iter.fetchWayGeometry(FetchMode.ALL).size());
        assertEquals(Helper.createPointList(0.15, 0.15, 0.2, 0.2, 0.3, 0.3), iter.fetchWayGeometry(FetchMode.ALL));

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

        LocationIndex locationIndex = new LocationIndexTree(g, new RAMDirectory());
        locationIndex.prepareIndex();
        QueryResult qr = locationIndex.findClosest(0.2, 0.21, DefaultEdgeFilter.allEdges(carEncoder));
        assertTrue(qr.isValid());
        assertEquals("this test was supposed to test the Position.PILLAR case", PILLAR, qr.getSnappedPosition());
        QueryGraph queryGraph = lookup(qr);
        EdgeIterator iter = queryGraph.createEdgeExplorer().setBaseNode(qr.getClosestNode());

        assertTrue(iter.next());
        assertEquals(0, iter.getAdjNode());
        assertEquals(1, iter.fetchWayGeometry(FetchMode.PILLAR_ONLY).size());
        assertEquals(2, iter.fetchWayGeometry(FetchMode.BASE_AND_PILLAR).size());
        assertEquals(2, iter.fetchWayGeometry(FetchMode.PILLAR_AND_ADJ).size());
        assertEquals(3, iter.fetchWayGeometry(FetchMode.ALL).size());
        assertEquals(Helper.createPointList(0.2, 0.2, 0.1, 0.1, 0.0, 0.0), iter.fetchWayGeometry(FetchMode.ALL));

        assertTrue(iter.next());
        assertEquals(1, iter.getAdjNode());
        assertEquals(0, iter.fetchWayGeometry(FetchMode.PILLAR_ONLY).size());
        assertEquals(1, iter.fetchWayGeometry(FetchMode.BASE_AND_PILLAR).size());
        assertEquals(1, iter.fetchWayGeometry(FetchMode.PILLAR_AND_ADJ).size());
        assertEquals(2, iter.fetchWayGeometry(FetchMode.ALL).size());
        assertEquals(Helper.createPointList(0.2, 0.2, 0.5, 0.1), iter.fetchWayGeometry(FetchMode.ALL));

        assertFalse(iter.next());
    }

    @Test
    public void testVirtualEdgeDistance() {
        //   x
        // -----
        // |   |
        // 0   1
        NodeAccess na = g.getNodeAccess();
        na.setNode(0, 0, 0);
        na.setNode(1, 0, 1);
        // dummy node to make sure graph bounds are valid
        na.setNode(2, 2, 2);
        DistanceCalc distCalc = Helper.DIST_PLANE;
        double dist = 0;
        dist += distCalc.calcDist(0, 0, 1, 0);
        dist += distCalc.calcDist(1, 0, 1, 1);
        dist += distCalc.calcDist(1, 1, 0, 1);
        g.edge(0, 1, dist, true).setWayGeometry(Helper.createPointList(1, 0, 1, 1));
        LocationIndexTree index = new LocationIndexTree(g, new RAMDirectory());
        index.prepareIndex();
        QueryResult qr = index.findClosest(1.01, 0.7, EdgeFilter.ALL_EDGES);
        QueryGraph queryGraph = lookup(qr);
        // the sum of the virtual edge distances adjacent to the virtual node should be equal to the distance
        // of the real edge, so the 'distance' from 0 to 1 is the same no matter if we travel on the query graph or the
        // real graph
        EdgeIterator iter = queryGraph.createEdgeExplorer().setBaseNode(3);
        double virtualEdgeDistanceSum = 0;
        while (iter.next()) {
            virtualEdgeDistanceSum += iter.getDistance();
        }
        double directDist = g.getEdgeIteratorState(0, 1).getDistance();
        assertEquals(directDist, virtualEdgeDistanceSum, 1.e-3);
    }


    private QueryGraph lookup(QueryResult res) {
        return lookup(Collections.singletonList(res));
    }

    private QueryGraph lookup(List<QueryResult> queryResults) {
        return QueryGraph.create(g, queryResults);
    }

}
