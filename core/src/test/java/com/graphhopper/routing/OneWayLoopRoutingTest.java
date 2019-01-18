package com.graphhopper.routing;

import com.carrotsearch.hppc.IntArrayList;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.routing.weighting.TurnWeighting;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.storage.RAMDirectory;
import com.graphhopper.storage.TurnCostExtension;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.PointList;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class OneWayLoopRoutingTest {

    private TurnCostExtension turnCostExtension;
    private FlagEncoder encoder;
    private TurnWeighting turnWeighting;
    private GraphHopperStorage graph;
    private int defaultDist;
    private NodeAccess na;

    @Before
    public void setup() {
        encoder = new CarFlagEncoder(5, 5, 10);
        EncodingManager em = new EncodingManager(encoder);
        turnCostExtension = new TurnCostExtension();
        FastestWeighting weighting = new FastestWeighting(encoder);
        turnWeighting = new TurnWeighting(weighting, turnCostExtension);
        graph = new GraphHopperStorage(new RAMDirectory(), em, false, turnCostExtension).create(10);
        na = graph.getNodeAccess();
        defaultDist = 1;
    }

    @Test
    public void canRouteFwdLoop() {
        testCanRouteOneWayLoop(false);
    }

    @Test
    public void canRouteBwdLoop() {
        testCanRouteOneWayLoop(true);
    }

    private void testCanRouteOneWayLoop(boolean bwdLoop) {
        createGraph(bwdLoop);
        Dijkstra algo = new Dijkstra(graph, turnWeighting, TraversalMode.EDGE_BASED_2DIR);
        testAlgo(algo, bwdLoop);
    }

    @Test
    public void canRouteFwdLoopUsingBidirDijkstra() {
        testCanRouteOneWayLoopUsingBidirDijkstra(false);
    }

    @Test
    public void canRouteBwdLoopUsingBidirDijkstra() {
        testCanRouteOneWayLoopUsingBidirDijkstra(true);
    }

    private void testCanRouteOneWayLoopUsingBidirDijkstra(boolean bwdLoop) {
        createGraph(bwdLoop);
        DijkstraBidirectionRef algo = new DijkstraBidirectionRef(graph, turnWeighting, TraversalMode.EDGE_BASED_2DIR);
        testAlgo(algo, bwdLoop);
    }

    private void createGraph(boolean bwdLoop) {
        //     / \
        //     \ /
        // 0 -> 1 -> 2
        na.setNode(0, 0, 0);
        na.setNode(1, 0, 1);
        na.setNode(2, 0, 2);
        graph.edge(0, 1, defaultDist, false);
        graph.edge(1, 1).setDistance(defaultDist);
        graph.edge(1, 2, defaultDist, false);
        // set loop direction
        EdgeIteratorState loop = graph.getEdgeIteratorState(1, 1);
        loop.setFlags(encoder.setSpeed(loop.getFlags(), 60));
        loop.setFlags(encoder.setAccess(loop.getFlags(), !bwdLoop, bwdLoop));
        // add loop geometry
        PointList pointList = new PointList();
        pointList.add(1, 1);
        pointList.add(1, 2);
        pointList.add(1, 3);
        loop.setWayGeometry(pointList);
        // deny direct turn at node 1
        addRestriction(0, 2, 1);
    }

    private void testAlgo(RoutingAlgorithm algo, boolean bwdLoop) {
        Path path = algo.calcPath(0, 2);
        assertEquals(IntArrayList.from(0, 1, 1, 2), path.calcNodes());
        PointList expected = bwdLoop ? getExpectedPointsBwd() : getExpectedPointsFwd();
        assertEquals(expected, path.calcPoints());
    }

    private PointList getExpectedPointsFwd() {
        PointList pointList = new PointList();
        pointList.add(0, 0);
        pointList.add(0, 1);
        pointList.add(1, 1);
        pointList.add(1, 2);
        pointList.add(1, 3);
        pointList.add(0, 1);
        pointList.add(0, 2);
        return pointList;
    }

    private PointList getExpectedPointsBwd() {
        PointList pointList = new PointList();
        pointList.add(0, 0);
        pointList.add(0, 1);
        pointList.add(1, 3);
        pointList.add(1, 2);
        pointList.add(1, 1);
        pointList.add(0, 1);
        pointList.add(0, 2);
        return pointList;
    }

    private void addRestriction(int inEdge, int outEdge, int viaNode) {
        turnCostExtension.addTurnInfo(inEdge, viaNode, outEdge, encoder.getTurnFlags(true, 0));
    }

}
