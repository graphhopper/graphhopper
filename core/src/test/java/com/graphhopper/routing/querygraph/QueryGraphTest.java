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

import com.carrotsearch.hppc.IntArrayList;
import com.carrotsearch.hppc.IntObjectMap;
import com.graphhopper.routing.HeadingResolver;
import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.weighting.SpeedWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.search.KVStorage;
import com.graphhopper.storage.*;
import com.graphhopper.storage.index.LocationIndexTree;
import com.graphhopper.storage.index.Snap;
import com.graphhopper.util.*;
import com.graphhopper.util.shapes.GHPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.graphhopper.storage.index.Snap.Position.*;
import static com.graphhopper.util.EdgeIteratorState.UNFAVORED_EDGE;
import static com.graphhopper.util.GHUtility.updateDistancesFor;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Peter Karich
 */
public class QueryGraphTest {
    private EncodingManager encodingManager;
    private DecimalEncodedValue speedEnc;
    private BaseGraph g;

    @BeforeEach
    public void setUp() {
        speedEnc = new DecimalEncodedValueImpl("speed", 5, 5, true);
        encodingManager = EncodingManager.start().add(speedEnc).build();
        g = new BaseGraph.Builder(encodingManager).create();
    }

    @AfterEach
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
        g.edge(0, 2).setDistance(10).set(speedEnc, 60, 60);
        g.edge(0, 1).setDistance(10).set(speedEnc, 60, 60).
                setWayGeometry(Helper.createPointList(1.5, 1, 1.5, 1.5));
    }

    @Test
    public void testOneVirtualNode() {
        initGraph(g);
        EdgeExplorer expl = g.createEdgeExplorer();

        // snap directly to tower node => pointList could get of size 1?!?      
        // a)
        EdgeIterator iter = expl.setBaseNode(2);
        iter.next();

        Snap res = createLocationResult(1, -1, iter, 0, TOWER);
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
        assertEquals(3, getPoints(queryGraph3, 0, 3).size());
        assertEquals(2, getPoints(queryGraph3, 3, 1).size());

        res = createLocationResult(2, 1.7, iter, 1, PILLAR);
        QueryGraph queryGraph4 = lookup(res);
        assertEquals(new GHPoint(1.5, 1.5), res.getSnappedPoint());
        assertEquals(3, res.getClosestNode());
        assertEquals(3, getPoints(queryGraph4, 0, 3).size());
        assertEquals(2, getPoints(queryGraph4, 3, 1).size());

        // snap to edge which has pillar nodes        
        res = createLocationResult(1.5, 2, iter, 0, EDGE);
        QueryGraph queryGraph5 = lookup(res);
        assertEquals(new GHPoint(1.300019, 1.899962), res.getSnappedPoint());
        assertEquals(3, res.getClosestNode());
        assertEquals(4, getPoints(queryGraph5, 0, 3).size());
        assertEquals(2, getPoints(queryGraph5, 3, 1).size());

        // snap to edge which has no pillar nodes
        iter = expl.setBaseNode(2);
        iter.next();
        res = createLocationResult(0.5, 0.1, iter, 0, EDGE);
        QueryGraph queryGraph6 = lookup(res);
        assertEquals(new GHPoint(0.5, 0), res.getSnappedPoint());
        assertEquals(3, res.getClosestNode());
        assertEquals(2, getPoints(queryGraph6, 0, 3).size());
        assertEquals(2, getPoints(queryGraph6, 3, 2).size());
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
        g.edge(0, 2).setDistance(10).set(speedEnc, 60, 60);
        g.edge(0, 1).setDistance(10).set(speedEnc, 60, 60)
                .setWayGeometry(Helper.createPointList(1.5, 1, 1.5, 1.5));
        g.edge(1, 3);

        final int baseNode = 1;
        EdgeIterator iter = g.createEdgeExplorer().setBaseNode(baseNode);
        iter.next();
        // note that we do not really do a location index lookup, but rather create a snap artificially, also
        // this snap is not very intuitive as we would expect snapping to the 1-0 edge, but this is how this
        // test was written initially...
        Snap snap = createLocationResult(2, 1.7, iter, 1, PILLAR);
        QueryOverlay queryOverlay = QueryOverlayBuilder.build(g, Collections.singletonList(snap));
        IntObjectMap<QueryOverlay.EdgeChanges> realNodeModifications = queryOverlay.getEdgeChangesAtRealNodes();
        assertEquals(2, realNodeModifications.size());
        // ignore nodes should include baseNode == 1
        assertEquals("[3->4]", realNodeModifications.get(3).getAdditionalEdges().toString());
        assertEquals("[2]", realNodeModifications.get(3).getRemovedEdges().toString());
        assertEquals("[1->4]", realNodeModifications.get(1).getAdditionalEdges().toString());
        assertEquals("[2]", realNodeModifications.get(1).getRemovedEdges().toString());

        QueryGraph queryGraph = QueryGraph.create(g, snap);
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
        Snap res1 = createLocationResult(2, 1.7, iter, 1, PILLAR);
        QueryGraph queryGraph = lookup(res1);
        assertEquals(new GHPoint(1.5, 1.5), res1.getSnappedPoint());
        assertEquals(3, res1.getClosestNode());
        assertEquals(3, getPoints(queryGraph, 0, 3).size());
        PointList pl = getPoints(queryGraph, 3, 1);
        assertEquals(2, pl.size());
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
        Snap res2 = createLocationResult(1.5, 2, iter, 0, EDGE);
        queryGraph = lookup(Arrays.asList(res1, res2));
        assertEquals(4, res2.getClosestNode());
        assertEquals(new GHPoint(1.300019, 1.899962), res2.getSnappedPoint());
        assertEquals(3, res1.getClosestNode());
        assertEquals(new GHPoint(1.5, 1.5), res1.getSnappedPoint());

        assertEquals(3, getPoints(queryGraph, 3, 0).size());
        assertEquals(2, getPoints(queryGraph, 3, 4).size());
        assertEquals(2, getPoints(queryGraph, 4, 1).size());
        assertNull(GHUtility.getEdge(queryGraph, 4, 0));
        assertNull(GHUtility.getEdge(queryGraph, 3, 1));
    }

    @Test
    public void testOneWay() {
        NodeAccess na = g.getNodeAccess();
        na.setNode(0, 0, 0);
        na.setNode(1, 0, 1);
        g.edge(0, 1).setDistance(10).set(speedEnc, 60, 0);

        EdgeIteratorState edge = GHUtility.getEdge(g, 0, 1);
        Snap res1 = createLocationResult(0.1, 0.1, edge, 0, EDGE);
        Snap res2 = createLocationResult(0.1, 0.9, edge, 0, EDGE);
        QueryGraph queryGraph = lookup(Arrays.asList(res2, res1));
        assertEquals(2, res1.getClosestNode());
        assertEquals(new GHPoint(0, 0.1), res1.getSnappedPoint());
        assertEquals(3, res2.getClosestNode());
        assertEquals(new GHPoint(0, 0.9), res2.getSnappedPoint());

        assertEquals(2, getPoints(queryGraph, 0, 2).size());
        assertEquals(2, getPoints(queryGraph, 2, 3).size());
        assertEquals(2, getPoints(queryGraph, 3, 1).size());
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
        g = new BaseGraph.Builder(encodingManager).set3D(true).create();
        NodeAccess na = g.getNodeAccess();
        na.setNode(0, 0, 0, 0);
        na.setNode(1, 0, 0.0001, 20);
        EdgeIteratorState edge = g.edge(0, 1);
        EdgeIteratorState edgeReverse = edge.detach(true);

        DistanceCalcEuclidean distCalc = new DistanceCalcEuclidean();
        Snap snap = new Snap(0, 0.00005);
        snap.setClosestEdge(edge);
        snap.setWayIndex(0);
        snap.setSnappedPosition(EDGE);
        snap.calcSnappedPoint(distCalc);
        assertEquals(10, snap.getSnappedPoint().getEle(), 1e-1);

        snap = new Snap(0, 0.00005);
        snap.setClosestEdge(edgeReverse);
        snap.setWayIndex(0);
        snap.setSnappedPosition(EDGE);
        snap.calcSnappedPoint(distCalc);
        assertEquals(10, snap.getSnappedPoint().getEle(), 1e-1);
    }

    @Test
    public void testLoopStreet_Issue151() {
        // do query at x should result in ignoring only the bottom edge 1-3 not the upper one => getNeighbors are 0, 5, 3 and not only 0, 5
        //
        // 0--1--3--4
        //    |  |
        //    x---
        //
        g.edge(0, 1).setDistance(10).set(speedEnc, 60, 60);
        g.edge(1, 3).setDistance(10).set(speedEnc, 60, 60);
        g.edge(3, 4).setDistance(10).set(speedEnc, 60, 60);
        EdgeIteratorState edge = g.edge(1, 3).setDistance(20).set(speedEnc, 60, 60).setWayGeometry(Helper.createPointList(-0.001, 0.001, -0.001, 0.002));
        updateDistancesFor(g, 0, 0, 0);
        updateDistancesFor(g, 1, 0, 0.001);
        updateDistancesFor(g, 3, 0, 0.002);
        updateDistancesFor(g, 4, 0, 0.003);

        Snap snap = new Snap(-0.0005, 0.001);
        snap.setClosestEdge(edge);
        snap.setWayIndex(1);
        snap.calcSnappedPoint(new DistanceCalcEuclidean());

        QueryGraph qg = lookup(snap);
        EdgeExplorer ee = qg.createEdgeExplorer();

        assertEquals(GHUtility.asSet(0, 5, 3), GHUtility.getNeighbors(ee.setBaseNode(1)));
    }

    @Test
    public void testEdgesShareOneNode() {
        initGraph(g);

        EdgeIteratorState iter = GHUtility.getEdge(g, 0, 2);
        Snap res1 = createLocationResult(0.5, 0, iter, 0, EDGE);
        iter = GHUtility.getEdge(g, 1, 0);
        Snap res2 = createLocationResult(1.5, 2, iter, 0, EDGE);
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
        Snap res1 = createLocationResult(0.5, 0, edgeState, 0, EDGE);
        Snap res2 = createLocationResult(0.5, 0, edgeState, 0, EDGE);
        lookup(Arrays.asList(res1, res2));
        assertEquals(new GHPoint(0.5, 0), res1.getSnappedPoint());
        assertEquals(new GHPoint(0.5, 0), res2.getSnappedPoint());
        assertEquals(3, res1.getClosestNode());
        assertEquals(3, res2.getClosestNode());

        // force skip due to **tower** node snapping in phase 2 (QueryOverlayBuilder.buildVirtualEdges -> Snap.considerEqual)
        // and no virtual edges should be created for res1
        edgeState = GHUtility.getEdge(g, 0, 1);
        res1 = createLocationResult(1, 0, edgeState, 0, TOWER);
        // now create virtual edges
        edgeState = GHUtility.getEdge(g, 0, 2);
        res2 = createLocationResult(0.5, 0, edgeState, 0, EDGE);
        QueryGraph queryGraph = lookup(Arrays.asList(res1, res2));
        // make sure only one virtual node was created
        assertEquals(queryGraph.getNodes(), g.getNodes() + 1);
        EdgeIterator iter = queryGraph.createEdgeExplorer().setBaseNode(0);
        assertEquals(GHUtility.asSet(1, 3), GHUtility.getNeighbors(iter));
    }

    @Test
    void towerSnapWhenCrossingPointIsOnEdgeButCloseToTower() {
        g.getNodeAccess().setNode(0, 49.000000, 11.00100);
        g.getNodeAccess().setNode(1, 49.000000, 11.00200);
        g.getNodeAccess().setNode(2, 49.000300, 11.00200);
        g.edge(0, 1);
        g.edge(1, 2);
        LocationIndexTree locationIndex = new LocationIndexTree(g, new RAMDirectory());
        locationIndex.prepareIndex();
        Snap snap = locationIndex.findClosest(49.0000010, 11.00800, EdgeFilter.ALL_EDGES);
        // Our query point is quite far away from the edge and further away from the tower node than from the crossing
        // point along the edge. But since the crossing point is very near to the tower node we still want it to be a
        // tower-snap to prevent a virtual node with a very short virtual edge
        assertEquals(Snap.Position.TOWER, snap.getSnappedPosition());
        QueryGraph queryGraph = QueryGraph.create(g, snap);
        assertEquals(g.getNodes(), queryGraph.getNodes());
    }

    @Test
    public void testGetEdgeProps() {
        initGraph(g);
        EdgeIteratorState e1 = GHUtility.getEdge(g, 0, 2);
        Snap res1 = createLocationResult(0.5, 0, e1, 0, EDGE);
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

    public Snap createLocationResult(double lat, double lon,
                                     EdgeIteratorState edge, int wayIndex, Snap.Position pos) {
        if (edge == null)
            throw new IllegalStateException("Specify edge != null");
        Snap tmp = new Snap(lat, lon);
        tmp.setClosestEdge(edge);
        tmp.setWayIndex(wayIndex);
        tmp.setSnappedPosition(pos);
        tmp.calcSnappedPoint(new DistanceCalcEarth());
        return tmp;
    }

    @Test
    public void testIteration_Issue163() {
        EdgeFilter inEdgeFilter = edge -> edge.getReverse(speedEnc) > 0;
        EdgeFilter outEdgeFilter = edge -> edge.get(speedEnc) > 0;
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
        g.edge(nodeA, nodeB).setDistance(10).set(speedEnc, 60, 0).
                setWayGeometry(Helper.createPointList(1.5, 3, 1.5, 7));

        // assert the behavior for classic edgeIterator        
        assertEdgeIdsStayingEqual(inExplorer, outExplorer, nodeA, nodeB);

        // setup snaps
        EdgeIteratorState it = GHUtility.getEdge(g, nodeA, nodeB);
        Snap snap1 = createLocationResult(1.5, 3, it, 1, PILLAR);
        Snap snap2 = createLocationResult(1.5, 7, it, 2, PILLAR);

        QueryGraph q = lookup(Arrays.asList(snap1, snap2));
        int nodeC = snap1.getClosestNode();
        int nodeD = snap2.getClosestNode();

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
        assertEquals(expectedEdgeId, it.getEdge(), "The edge id is not the same,");
        assertFalse(it.next());
    }

    @Test
    public void testTurnCostsProperlyPropagated_Issue282() {
        DecimalEncodedValue speedEnc = new DecimalEncodedValueImpl("speed", 5, 5, true);
        DecimalEncodedValue turnCostEnc = TurnCost.create("car", 15);
        EncodingManager em = EncodingManager.start().add(speedEnc).addTurnCostEncodedValue(turnCostEnc).build();
        BaseGraph graphWithTurnCosts = new BaseGraph.Builder(em).withTurnCosts(true).create();
        TurnCostStorage turnExt = graphWithTurnCosts.getTurnCostStorage();
        NodeAccess na = graphWithTurnCosts.getNodeAccess();
        na.setNode(0, .00, .00);
        na.setNode(1, .00, .01);
        na.setNode(2, .01, .01);

        EdgeIteratorState edge0 = graphWithTurnCosts.edge(0, 1).setDistance(10).set(speedEnc, 60, 60);
        EdgeIteratorState edge1 = graphWithTurnCosts.edge(2, 1).setDistance(10).set(speedEnc, 60, 60);
        Weighting weighting = new SpeedWeighting(speedEnc, turnCostEnc, graphWithTurnCosts.getTurnCostStorage(), Double.POSITIVE_INFINITY);

        // no turn costs initially
        assertEquals(0, weighting.calcTurnWeight(edge0.getEdge(), 1, edge1.getEdge()), .1);

        // now use turn costs
        turnExt.set(turnCostEnc, edge0.getEdge(), 1, edge1.getEdge(), 10);
        assertEquals(10, weighting.calcTurnWeight(edge0.getEdge(), 1, edge1.getEdge()), .1);

        // now use turn costs with query graph
        Snap res1 = createLocationResult(0.000, 0.005, edge0, 0, Snap.Position.EDGE);
        Snap res2 = createLocationResult(0.005, 0.010, edge1, 0, Snap.Position.EDGE);
        QueryGraph qGraph = QueryGraph.create(graphWithTurnCosts, res1, res2);
        weighting = qGraph.wrapWeighting(weighting);

        int fromQueryEdge = GHUtility.getEdge(qGraph, res1.getClosestNode(), 1).getEdge();
        int toQueryEdge = GHUtility.getEdge(qGraph, res2.getClosestNode(), 1).getEdge();

        assertEquals(10, weighting.calcTurnWeight(fromQueryEdge, 1, toQueryEdge), .1);

        graphWithTurnCosts.close();
    }

    private Snap fakeEdgeSnap(EdgeIteratorState edge, double lat, double lon, int wayIndex) {
        Snap snap = new Snap(lat, lon);
        snap.setClosestEdge(edge);
        snap.setWayIndex(wayIndex);
        snap.setSnappedPosition(EDGE);
        snap.calcSnappedPoint(new DistanceCalcEuclidean());
        return snap;
    }

    private boolean isAvoidEdge(EdgeIteratorState edge) {
        return edge.get(EdgeIteratorState.UNFAVORED_EDGE);
    }

    @Test
    public void testEnforceHeading() {
        // setup graph
        //   ____
        //  |    |
        //  x    |
        //  |    |
        //  0    1
        NodeAccess na = g.getNodeAccess();
        na.setNode(0, 0, 0);
        na.setNode(1, 0, 2);
        g.edge(0, 1).setDistance(10).set(speedEnc, 60, 60).
                setWayGeometry(Helper.createPointList(2, 0, 2, 2));
        EdgeIteratorState edge = GHUtility.getEdge(g, 0, 1);

        // snap on first vertical part of way (upward, base is in south)
        Snap snap = fakeEdgeSnap(edge, 1.5, 0, 0);
        QueryGraph queryGraph = lookup(snap);

        // enforce going out north
        HeadingResolver headingResolver = new HeadingResolver(queryGraph);
        IntArrayList unfavoredEdges = headingResolver.getEdgesWithDifferentHeading(snap.getClosestNode(), 0);
        queryGraph.unfavorVirtualEdges(unfavoredEdges);

        // test penalized south
        boolean expect = true;
        assertEquals(expect, isAvoidEdge(queryGraph.getEdgeIteratorState(1, 2)));
        assertEquals(expect, isAvoidEdge(queryGraph.getEdgeIteratorState(1, 0)));

        queryGraph.clearUnfavoredStatus();
        // test cleared edges south
        expect = false;
        assertEquals(expect, isAvoidEdge(queryGraph.getEdgeIteratorState(1, 2)));
        assertEquals(expect, isAvoidEdge(queryGraph.getEdgeIteratorState(1, 0)));

        // enforce going south (same as coming in from north)
        unfavoredEdges = headingResolver.getEdgesWithDifferentHeading(snap.getClosestNode(), 180);
        queryGraph.unfavorVirtualEdges(unfavoredEdges);

        // test penalized north
        expect = true;
        assertEquals(expect, isAvoidEdge(queryGraph.getEdgeIteratorState(2, 1)));
        assertEquals(expect, isAvoidEdge(queryGraph.getEdgeIteratorState(2, 2)));

        // snap on second vertical part of way (downward, base is in north)
        //   ____
        //  |    |
        //  |    x
        //  |    |
        //  0    1
        snap = fakeEdgeSnap(edge, 1.5, 2, 2);
        queryGraph = lookup(Arrays.asList(snap));

        // enforce north
        unfavoredEdges = headingResolver.getEdgesWithDifferentHeading(snap.getClosestNode(), 180);
        queryGraph.unfavorVirtualEdges(unfavoredEdges);
        // test penalized south
        expect = true;
        assertEquals(expect, isAvoidEdge(queryGraph.getEdgeIteratorState(2, 1)));
        assertEquals(expect, isAvoidEdge(queryGraph.getEdgeIteratorState(2, 2)));

        queryGraph.clearUnfavoredStatus();
        // enforce south
        unfavoredEdges = headingResolver.getEdgesWithDifferentHeading(snap.getClosestNode(), 0);
        queryGraph.unfavorVirtualEdges(unfavoredEdges);

        // test penalized north
        expect = true;
        assertEquals(expect, isAvoidEdge(queryGraph.getEdgeIteratorState(1, 0)));
        assertEquals(expect, isAvoidEdge(queryGraph.getEdgeIteratorState(1, 2)));
    }

    @Test
    public void testUnfavoredEdgeDirections() {
        NodeAccess na = g.getNodeAccess();
        // 0 <-> x <-> 1
        //       2
        na.setNode(0, 0, 0);
        na.setNode(1, 0, 2);
        EdgeIteratorState edge = g.edge(0, 1).setDistance(10).set(speedEnc, 60, 60);

        Snap snap = fakeEdgeSnap(edge, 0, 1, 0);
        QueryGraph queryGraph = QueryGraph.create(g, snap);
        queryGraph.unfavorVirtualEdge(1);
        // this sets the unfavored flag for both 'directions' (not sure if this is really what we want, but this is how
        // it is). for example we can not set the virtual edge 0-2 unfavored when going from 0 to 2 but *not* unfavored
        // when going from 2 to 0. this would be a problem for edge-based routing where we might apply a penalty when
        // going in one direction but not the other
        assertTrue(GHUtility.getEdge(queryGraph, 2, 0).get(UNFAVORED_EDGE));
        assertTrue(GHUtility.getEdge(queryGraph, 2, 0).getReverse(UNFAVORED_EDGE));
        assertTrue(GHUtility.getEdge(queryGraph, 0, 2).get(UNFAVORED_EDGE));
        assertTrue(GHUtility.getEdge(queryGraph, 0, 2).getReverse(UNFAVORED_EDGE));

        assertFalse(GHUtility.getEdge(queryGraph, 2, 1).get(UNFAVORED_EDGE));
        assertFalse(GHUtility.getEdge(queryGraph, 2, 1).getReverse(UNFAVORED_EDGE));
        assertFalse(GHUtility.getEdge(queryGraph, 1, 2).get(UNFAVORED_EDGE));
        assertFalse(GHUtility.getEdge(queryGraph, 1, 2).getReverse(UNFAVORED_EDGE));
    }

    @Test
    public void testUnfavorVirtualEdgePair() {
        // setup graph
        //   ____
        //  |    |
        //  |    |
        //  0    1
        NodeAccess na = g.getNodeAccess();
        na.setNode(0, 0, 0);
        na.setNode(1, 0, 2);
        g.edge(0, 1).setDistance(10).set(speedEnc, 60, 60).
                setWayGeometry(Helper.createPointList(2, 0, 2, 2));
        EdgeIteratorState edge = GHUtility.getEdge(g, 0, 1);

        // snap on first vertical part of way (upward)
        Snap snap = fakeEdgeSnap(edge, 1.5, 0, 0);
        QueryGraph queryGraph = lookup(snap);

        // enforce coming in north
        queryGraph.unfavorVirtualEdge(1);
        // test penalized south
        VirtualEdgeIteratorState incomingEdge = (VirtualEdgeIteratorState) queryGraph.getEdgeIteratorState(1, 2);
        VirtualEdgeIteratorState incomingEdgeReverse = (VirtualEdgeIteratorState) queryGraph.getEdgeIteratorState(1, incomingEdge.getBaseNode());
        boolean expect = true;  // expect incoming and reverse incoming edge to be avoided
        assertEquals(expect, isAvoidEdge(incomingEdge));
        assertEquals(expect, isAvoidEdge(incomingEdgeReverse));
        assertEquals(new LinkedHashSet<>(Arrays.asList(incomingEdge, incomingEdgeReverse)),
                queryGraph.getUnfavoredVirtualEdges());

        queryGraph.clearUnfavoredStatus();
        expect = false; // expect incoming and reverse incoming edge not to be avoided
        assertEquals(expect, isAvoidEdge(incomingEdge));
        assertEquals(expect, isAvoidEdge(incomingEdgeReverse));
        assertEquals(new LinkedHashSet<>(), queryGraph.getUnfavoredVirtualEdges());
    }

    @Test
    public void testInternalAPIOriginalEdgeKey() {
        initGraph(g);

        EdgeExplorer explorer = g.createEdgeExplorer();
        EdgeIterator iter = explorer.setBaseNode(1);
        assertTrue(iter.next());
        Snap res = createLocationResult(2, 1.5, iter, 1, PILLAR);
        QueryGraph queryGraph = lookup(res);

        assertEquals(new GHPoint(1.5, 1.5), res.getSnappedPoint());
        assertEquals(3, res.getClosestNode());

        EdgeExplorer qGraphExplorer = queryGraph.createEdgeExplorer();
        iter = qGraphExplorer.setBaseNode(3);
        assertTrue(iter.next());
        assertEquals(2, iter.getEdge());
        assertEquals(0, iter.getAdjNode());
        assertEquals(3, ((VirtualEdgeIteratorState) queryGraph.getEdgeIteratorState(iter.getEdge(), 0)).getOriginalEdgeKey());

        assertTrue(iter.next());
        assertEquals(3, iter.getEdge());
        assertEquals(1, iter.getAdjNode());
        assertEquals(2, ((VirtualEdgeIteratorState) queryGraph.getEdgeIteratorState(iter.getEdge(), 1)).getOriginalEdgeKey());
    }

    @Test
    public void testWayGeometry_edge() {
        // drawn as horizontal linear graph for simplicity
        // 0 - * - x - * - 1
        NodeAccess na = g.getNodeAccess();
        na.setNode(0, 0, 0);
        na.setNode(1, 0.3, 0.3);
        g.edge(0, 1).setDistance(10).set(speedEnc, 60, 60).
                setWayGeometry(Helper.createPointList(0.1, 0.1, 0.2, 0.2));

        LocationIndexTree locationIndex = new LocationIndexTree(g, new RAMDirectory());
        locationIndex.prepareIndex();
        Snap snap = locationIndex.findClosest(0.15, 0.15, EdgeFilter.ALL_EDGES);
        assertTrue(snap.isValid());
        assertEquals(EDGE, snap.getSnappedPosition(), "this test was supposed to test the Position.EDGE case");
        QueryGraph queryGraph = lookup(snap);
        EdgeIterator iter = queryGraph.createEdgeExplorer().setBaseNode(snap.getClosestNode());

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
        g.edge(0, 1).setDistance(10).set(speedEnc, 60, 60).
                setWayGeometry(Helper.createPointList(0.1, 0.1, 0.2, 0.2));

        LocationIndexTree locationIndex = new LocationIndexTree(g, new RAMDirectory());
        locationIndex.prepareIndex();
        Snap snap = locationIndex.findClosest(0.2, 0.21, EdgeFilter.ALL_EDGES);
        assertTrue(snap.isValid());
        assertEquals(PILLAR, snap.getSnappedPosition(), "this test was supposed to test the Position.PILLAR case");
        QueryGraph queryGraph = lookup(snap);
        EdgeIterator iter = queryGraph.createEdgeExplorer().setBaseNode(snap.getClosestNode());

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
        DistanceCalc distCalc = DistancePlaneProjection.DIST_PLANE;
        double dist = 0;
        dist += distCalc.calcDist(0, 0, 1, 0);
        dist += distCalc.calcDist(1, 0, 1, 1);
        dist += distCalc.calcDist(1, 1, 0, 1);
        g.edge(0, 1).setDistance(dist).set(speedEnc, 60, 60).
                setWayGeometry(Helper.createPointList(1, 0, 1, 1));
        LocationIndexTree index = new LocationIndexTree(g, new RAMDirectory());
        index.prepareIndex();
        Snap snap = index.findClosest(1.01, 0.7, EdgeFilter.ALL_EDGES);
        QueryGraph queryGraph = lookup(snap);
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

    @Test
    public void testVirtualEdgeIds() {
        // virtual nodes:     2
        //                0 - x - 1
        // virtual edges:   1   2
        BooleanEncodedValue accessEnc = new SimpleBooleanEncodedValue("access", true);
        DecimalEncodedValue speedEnc = new DecimalEncodedValueImpl("speed", 5, 5, true);
        EncodingManager em = EncodingManager.start().add(accessEnc).add(speedEnc).build();
        BaseGraph g = new BaseGraph.Builder(em).create();
        NodeAccess na = g.getNodeAccess();
        na.setNode(0, 50.00, 10.10);
        na.setNode(1, 50.00, 10.20);
        double dist = DistanceCalcEarth.DIST_EARTH.calcDist(na.getLat(0), na.getLon(0), na.getLat(1), na.getLon(1));
        EdgeIteratorState edge = g.edge(0, 1).setDistance(dist).set(speedEnc, 60, 60);
        edge.set(speedEnc, 50);
        edge.setReverse(speedEnc, 100);

        // query graph
        Snap snap = createLocationResult(50.00, 10.15, edge, 0, EDGE);
        QueryGraph queryGraph = QueryGraph.create(g, snap);
        assertEquals(3, queryGraph.getNodes());
        assertEquals(3, queryGraph.getEdges());
        assertEquals(4, queryGraph.getVirtualEdges().size());

        EdgeIteratorState edge_0x = queryGraph.getEdgeIteratorState(1, 2);
        EdgeIteratorState edge_x0 = queryGraph.getEdgeIteratorState(1, 0);
        EdgeIteratorState edge_x1 = queryGraph.getEdgeIteratorState(2, 1);
        EdgeIteratorState edge_1x = queryGraph.getEdgeIteratorState(2, 2);

        assertNodes(edge_0x, 0, 2);
        assertNodes(edge_x0, 2, 0);
        assertNodes(edge_x1, 2, 1);
        assertNodes(edge_1x, 1, 2);

        // virtual edge IDs are 1 and 2
        assertEquals(1, edge_0x.getEdge());
        assertEquals(1, edge_x0.getEdge());
        assertEquals(2, edge_x1.getEdge());
        assertEquals(2, edge_1x.getEdge());

        // edge keys
        assertEquals(2, edge_0x.getEdgeKey());
        assertEquals(3, edge_x0.getEdgeKey());
        assertEquals(4, edge_x1.getEdgeKey());
        assertEquals(5, edge_1x.getEdgeKey());
        assertNodes(queryGraph.getEdgeIteratorStateForKey(2), 0, 2);
        assertNodes(queryGraph.getEdgeIteratorStateForKey(3), 2, 0);
        assertNodes(queryGraph.getEdgeIteratorStateForKey(4), 2, 1);
        assertNodes(queryGraph.getEdgeIteratorStateForKey(5), 1, 2);

        // internally each edge is represented by two edge states for the two directions
        assertSame(queryGraph.getVirtualEdges().get(0), edge_0x);
        assertSame(queryGraph.getVirtualEdges().get(1), edge_x0);
        assertSame(queryGraph.getVirtualEdges().get(2), edge_x1);
        assertSame(queryGraph.getVirtualEdges().get(3), edge_1x);

        for (EdgeIteratorState e : Arrays.asList(edge_0x, edge_x1)) {
            assertEquals(50, e.get(speedEnc), 1.e-6);
            assertEquals(100, e.getReverse(speedEnc), 1.e-6);
        }

        for (EdgeIteratorState e : Arrays.asList(edge_x0, edge_1x)) {
            assertEquals(100, e.get(speedEnc), 1.e-6);
            assertEquals(50, e.getReverse(speedEnc), 1.e-6);
        }

        try {
            queryGraph.getEdgeIteratorState(3, 2);
            fail("there should be an error");
        } catch (IndexOutOfBoundsException e) {
            // ok
        }
    }

    @Test
    public void testVirtualEdgeIds_reverse() {
        // virtual nodes:     2
        //                0 - x - 1
        // virtual edges:   1   2
        BooleanEncodedValue accessEnc = new SimpleBooleanEncodedValue("access", true);
        DecimalEncodedValue speedEnc = new DecimalEncodedValueImpl("speed", 5, 5, true);
        EncodingManager em = EncodingManager.start().add(accessEnc).add(speedEnc).build();
        BaseGraph g = new BaseGraph.Builder(em).create();
        NodeAccess na = g.getNodeAccess();
        na.setNode(0, 50.00, 10.10);
        na.setNode(1, 50.00, 10.20);
        double dist = DistanceCalcEarth.DIST_EARTH.calcDist(na.getLat(0), na.getLon(0), na.getLat(1), na.getLon(1));
        // this time we store the edge the other way
        EdgeIteratorState edge = g.edge(1, 0).setDistance(dist).set(speedEnc, 60, 60);
        edge.set(speedEnc, 100, 50);

        // query graph
        Snap snap = createLocationResult(50.00, 10.15, edge, 0, EDGE);
        QueryGraph queryGraph = QueryGraph.create(g, snap);
        assertEquals(3, queryGraph.getNodes());
        assertEquals(3, queryGraph.getEdges());
        assertEquals(4, queryGraph.getVirtualEdges().size());

        EdgeIteratorState edge_0x = queryGraph.getEdgeIteratorState(1, 2);
        EdgeIteratorState edge_x0 = queryGraph.getEdgeIteratorState(1, 0);
        EdgeIteratorState edge_x1 = queryGraph.getEdgeIteratorState(2, 1);
        EdgeIteratorState edge_1x = queryGraph.getEdgeIteratorState(2, 2);

        assertNodes(edge_0x, 0, 2);
        assertNodes(edge_x0, 2, 0);
        assertNodes(edge_x1, 2, 1);
        assertNodes(edge_1x, 1, 2);

        // virtual edge IDs are 1 and 2
        assertEquals(1, edge_0x.getEdge());
        assertEquals(1, edge_x0.getEdge());
        assertEquals(2, edge_x1.getEdge());
        assertEquals(2, edge_1x.getEdge());

        // edge keys
        assertEquals(2, edge_0x.getEdgeKey());
        assertEquals(3, edge_x0.getEdgeKey());
        assertEquals(4, edge_x1.getEdgeKey());
        assertEquals(5, edge_1x.getEdgeKey());
        assertNodes(queryGraph.getEdgeIteratorStateForKey(2), 0, 2);
        assertNodes(queryGraph.getEdgeIteratorStateForKey(3), 2, 0);
        assertNodes(queryGraph.getEdgeIteratorStateForKey(4), 2, 1);
        assertNodes(queryGraph.getEdgeIteratorStateForKey(5), 1, 2);

        // internally each edge is represented by two edge states for the two directions
        assertSame(queryGraph.getVirtualEdges().get(0), edge_0x);
        assertSame(queryGraph.getVirtualEdges().get(1), edge_x0);
        assertSame(queryGraph.getVirtualEdges().get(2), edge_x1);
        assertSame(queryGraph.getVirtualEdges().get(3), edge_1x);

        for (EdgeIteratorState e : Arrays.asList(edge_0x, edge_x1)) {
            assertEquals(50, e.get(speedEnc), 1.e-6);
            assertEquals(100, e.getReverse(speedEnc), 1.e-6);
        }

        for (EdgeIteratorState e : Arrays.asList(edge_x0, edge_1x)) {
            assertEquals(100, e.get(speedEnc), 1.e-6);
            assertEquals(50, e.getReverse(speedEnc), 1.e-6);
        }

        try {
            queryGraph.getEdgeIteratorState(3, 2);
            fail("there should be an error");
        } catch (IndexOutOfBoundsException e) {
            // ok
        }
    }

    private void assertNodes(EdgeIteratorState edge, int base, int adj) {
        assertEquals(base, edge.getBaseNode());
        assertEquals(adj, edge.getAdjNode());
    }

    @Test
    public void testTotalEdgeCount() {
        // virtual nodes:     2     3
        //                0 - x --- x - 1
        // virtual edges:   1   2/3 4
        BaseGraph g = new BaseGraph.Builder(1).create();
        NodeAccess na = g.getNodeAccess();
        na.setNode(0, 50.00, 10.00);
        na.setNode(1, 50.00, 10.30);
        g.edge(0, 1);

        LocationIndexTree locationIndex = new LocationIndexTree(g, g.getDirectory());
        locationIndex.prepareIndex();

        // query graph
        Snap snap1 = locationIndex.findClosest(50.00, 10.10, EdgeFilter.ALL_EDGES);
        Snap snap2 = locationIndex.findClosest(50.00, 10.20, EdgeFilter.ALL_EDGES);

        QueryGraph queryGraph = QueryGraph.create(g, snap1, snap2);
        assertEquals(4, queryGraph.getNodes());
        assertEquals(8, queryGraph.getVirtualEdges().size());
        assertEquals(1 + 8 / 2, queryGraph.getEdges());

        // internally the QueryGraph reserves edge IDs 2 and 3 for the edges between 2 and three,
        // but the edge iterator only reveals edge 2
        assertEquals("[1],[4],[1, 2],[2, 4]",
                IntStream.range(0, queryGraph.getNodes()).mapToObj(i ->
                        GHUtility.getEdgeIds(queryGraph.createEdgeExplorer().setBaseNode(i)).toString()).collect(Collectors.joining(",")));

        // by iterating up to the total edge count we get all edges
        assertEquals("0 0-1,0->2,2->3,2->3,3->1",
                IntStream.range(0, queryGraph.getEdges()).mapToObj(i ->
                        queryGraph.getEdgeIteratorState(i, Integer.MIN_VALUE).toString()).collect(Collectors.joining(",")));
    }

    @Test
    public void testExternalEV() {
        IntEncodedValue intEnc = new IntEncodedValueImpl("my_int", 3, true);
        EnumEncodedValue<RoadClass> enumEnc = RoadClass.create();
        BooleanEncodedValue roadClassLincEnc = RoadClassLink.create();
        ExternalBooleanEncodedValue externalEnc = new ExternalBooleanEncodedValue("my_ext", true);
        EncodingManager encodingManager = EncodingManager.start()
                .add(intEnc).add(enumEnc).add(roadClassLincEnc).add(externalEnc)
                .build();
        BaseGraph g = new BaseGraph.Builder(encodingManager).create();
        g.edge(0, 1).set(intEnc, 5).set(enumEnc, RoadClass.BRIDLEWAY).set(roadClassLincEnc, true).set(externalEnc, false, true);
        g.edge(1, 2).set(intEnc, 2).set(enumEnc, RoadClass.CYCLEWAY).set(roadClassLincEnc, false).set(externalEnc, false, true);
        g.edge(2, 3).set(intEnc, 7).set(enumEnc, RoadClass.PRIMARY).set(roadClassLincEnc, true).set(externalEnc, true, false);
        g.edge(3, 4).set(intEnc, 1).set(enumEnc, RoadClass.MOTORWAY).set(roadClassLincEnc, false).set(externalEnc, true, false);

        NodeAccess na = g.getNodeAccess();
        na.setNode(0, 50.00, 10.00);
        na.setNode(1, 50.10, 10.10);
        na.setNode(2, 50.20, 10.20);
        na.setNode(3, 50.30, 10.30);
        na.setNode(4, 50.40, 10.40);

        LocationIndexTree locationIndex = new LocationIndexTree(g, g.getDirectory());
        locationIndex.prepareIndex();

        Snap snap1 = locationIndex.findClosest(50.05, 10.05, EdgeFilter.ALL_EDGES);
        Snap snap2 = locationIndex.findClosest(50.35, 10.35, EdgeFilter.ALL_EDGES);

        QueryGraph queryGraph = QueryGraph.create(g, snap1, snap2);
        assertEquals(7, queryGraph.getNodes());
        assertEquals(4, queryGraph.getBaseGraph().getEdges());
        assertEquals(8, queryGraph.getVirtualEdges().size());

        // fetching EVs from the virtual edge yields the values of the corresponding original edge
        EdgeIteratorState virt05 = queryGraph.getEdgeIteratorState(4, 5);
        assertFalse(queryGraph.getEdgeIteratorState(0, 1).get(externalEnc));
        assertTrue(queryGraph.getEdgeIteratorState(0, 1).getReverse(externalEnc));
        assertFalse(virt05.get(externalEnc));
        assertTrue(virt05.getReverse(externalEnc));

        assertEquals(RoadClass.MOTORWAY, queryGraph.getEdgeIteratorState(3, 4).get(enumEnc));
        EdgeIteratorState virt64 = queryGraph.getEdgeIteratorState(7, 4);
        assertEquals(6, virt64.getBaseNode());
        assertEquals(4, virt64.getAdjNode());
        assertEquals(RoadClass.MOTORWAY, virt64.get(enumEnc));
        assertTrue(virt64.get(externalEnc));
        assertFalse(virt64.getReverse(externalEnc));
    }

    @Test
    public void directedKeyValues() {
        NodeAccess na = g.getNodeAccess();
        na.setNode(0, 1, 0);
        na.setNode(1, 1, 2.5);
        ArrayList<KVStorage.KeyValue> kvs = new ArrayList<>();
        kvs.add(new KVStorage.KeyValue("a", "hello", true, false));
        kvs.add(new KVStorage.KeyValue("b", "world", false, true));
        EdgeIteratorState origEdge = g.edge(0, 1).setDistance(10).set(speedEnc, 60, 60).setKeyValues(kvs);

        // keyValues List stays the same
        assertEquals(origEdge.getKeyValues().toString(), origEdge.detach(true).getKeyValues().toString());
        // determine if edge is reverse via origEdge.get(EdgeIteratorState.REVERSE_STATE)

        // but getValue is sensitive to direction
        assertEquals("hello", origEdge.getValue("a"));
        assertNull(origEdge.detach(true).getValue("a"));
        assertEquals("world", origEdge.detach(true).getValue("b"));
        assertNull(origEdge.getValue("b"));

        LocationIndexTree index = new LocationIndexTree(g, new RAMDirectory());
        index.prepareIndex();
        Snap snap = index.findClosest(1.01, 0.7, EdgeFilter.ALL_EDGES);
        QueryGraph queryGraph = lookup(snap);
        EdgeIteratorState edge0ToSnap = queryGraph.getEdgeIteratorState(1, 2);

        assertEquals(edge0ToSnap.getKeyValues().toString(), edge0ToSnap.detach(true).getKeyValues().toString());

        assertEquals("hello", edge0ToSnap.getValue("a"));
        assertNull(edge0ToSnap.detach(true).getValue("a"));
        assertEquals("world", edge0ToSnap.detach(true).getValue("b"));
        assertNull(edge0ToSnap.getValue("b"));
    }

    private QueryGraph lookup(Snap res) {
        return lookup(Collections.singletonList(res));
    }

    private QueryGraph lookup(List<Snap> snaps) {
        return QueryGraph.create(g, snaps);
    }
}
