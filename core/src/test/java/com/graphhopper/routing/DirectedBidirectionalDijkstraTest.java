package com.graphhopper.routing;

import com.carrotsearch.hppc.IntArrayList;
import com.carrotsearch.hppc.IntHashSet;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.DecimalEncodedValueImpl;
import com.graphhopper.routing.ev.TurnCost;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.AvoidEdgesWeighting;
import com.graphhopper.routing.weighting.SpeedWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.TurnCostStorage;
import com.graphhopper.storage.index.LocationIndexTree;
import com.graphhopper.storage.index.Snap;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.RandomGraph;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.*;

import static com.graphhopper.util.EdgeIterator.ANY_EDGE;
import static com.graphhopper.util.EdgeIterator.NO_EDGE;
import static com.graphhopper.util.GHUtility.updateDistancesFor;
import static org.junit.jupiter.api.Assertions.*;

/**
 * This test makes sure that {@link DijkstraBidirectionRef#calcPath(int from, int to, int fromOutEdge, int toInEdge)}, i.e.
 * calculating a path with restricted/enforced first/last edges works as expected.
 * <p>
 * For other bidirectional algorithms we simply compare with {@link DijkstraBidirectionRef} in {@link DirectedRoutingTest}
 */
public class DirectedBidirectionalDijkstraTest {
    private TurnCostStorage turnCostStorage;
    private int maxTurnCosts;
    private BaseGraph graph;
    private DecimalEncodedValue speedEnc;
    private Weighting weighting;
    private DecimalEncodedValue turnCostEnc;

    @BeforeEach
    public void setup() {
        maxTurnCosts = 10;
        speedEnc = new DecimalEncodedValueImpl("speed", 5, 5, true);
        turnCostEnc = TurnCost.create("car", maxTurnCosts);
        EncodingManager encodingManager = EncodingManager.start().add(speedEnc).addTurnCostEncodedValue(turnCostEnc).build();
        graph = new BaseGraph.Builder(encodingManager).withTurnCosts(true).create();
        turnCostStorage = graph.getTurnCostStorage();
        weighting = createWeighting(Double.POSITIVE_INFINITY);
    }

    private Weighting createWeighting(double uTurnCosts) {
        return new SpeedWeighting(speedEnc, turnCostEnc, turnCostStorage, uTurnCosts);
    }

    @Test
    public void connectionNotFound() {
        // nodes 0 and 2 are not connected
        // 0 -> 1     2 -> 3
        graph.edge(0, 1).setDistance(100).set(speedEnc, 10, 0);
        graph.edge(2, 3).setDistance(100).set(speedEnc, 10, 0);

        Path path = calcPath(0, 3, 0, 1);
        assertNotFound(path);
    }

    @Test
    public void singleEdge() {
        graph.edge(0, 1).setDistance(100).set(speedEnc, 10, 10);

        // source edge does not exist -> no path
        assertNotFound(calcPath(0, 1, 5, 0));
        // target edge does not exist -> no path
        assertNotFound(calcPath(0, 1, 0, 5));
        // using NO_EDGE -> no path
        assertNotFound(calcPath(0, 1, NO_EDGE, 0));
        assertNotFound(calcPath(0, 1, 0, NO_EDGE));
        // using ANY_EDGE -> no restriction
        assertPath(calcPath(0, 1, ANY_EDGE, 0), 10, 100, 10000, nodes(0, 1));
        assertPath(calcPath(0, 1, 0, ANY_EDGE), 10, 100, 10000, nodes(0, 1));
        // edges exist -> they are used as restrictions
        assertPath(calcPath(0, 1, 0, 0), 10, 100, 10000, nodes(0, 1));
    }

    @Test
    public void simpleGraph() {
        // 0 -> 1 -> 2
        graph.edge(0, 1).setDistance(100).set(speedEnc, 10, 10);
        graph.edge(1, 2).setDistance(100).set(speedEnc, 10, 10);

        // source edge does not exist -> no path
        assertNotFound(calcPath(0, 2, 5, 0));
        // target edge does not exist -> no path
        assertNotFound(calcPath(0, 2, 0, 5));
        // using NO_EDGE -> no  path
        assertNotFound(calcPath(0, 2, NO_EDGE, 0));
        assertNotFound(calcPath(0, 2, 0, NO_EDGE));
        // using ANY_EDGE -> no restriction
        assertPath(calcPath(0, 2, ANY_EDGE, 1), 20, 200, 20000, nodes(0, 1, 2));
        assertPath(calcPath(0, 2, 0, ANY_EDGE), 20, 200, 20000, nodes(0, 1, 2));
        // edges exist -> they are used as restrictions
        assertPath(calcPath(0, 2, 0, 1), 20, 200, 20000, nodes(0, 1, 2));
    }

    @Test
    public void sourceEqualsTarget() {
        // Since we are enforcing source/target edges, starting and arriving at the same node normally does not yield
        // zero weight. Either the weight is finite (using a real path back to the source node) or the
        // weight is infinite (when no such path exists). The exception of course would involve zero weight edges.
        // 0 - 1
        //  \  |
        //   - 2
        graph.edge(0, 1).setDistance(100).set(speedEnc, 10, 10);
        graph.edge(0, 2).setDistance(100).set(speedEnc, 10, 10);
        graph.edge(1, 2).setDistance(100).set(speedEnc, 10, 10);
        assertPath(calcPath(0, 0, 0, 1), 30, 300, 30000, nodes(0, 1, 2, 0));
        assertPath(calcPath(0, 0, 1, 0), 30, 300, 30000, nodes(0, 2, 1, 0));
        // without restrictions the weight should be zero
        assertPath(calcPath(0, 0, ANY_EDGE, ANY_EDGE), 0, 0, 0, nodes(0));
        // in some cases no path is possible
        assertNotFound(calcPath(0, 0, 1, 1));
        assertNotFound(calcPath(0, 0, 5, 1));
    }

    @Test
    public void restrictedEdges() {
        // =: costly edge
        // 0 = 1 - 2 - 3 = 4
        //  \      |      /
        //   - 5 - 6 - 7 -
        int costlySource = graph.edge(0, 1).setDistance(500).set(speedEnc, 10, 10).getEdge();
        graph.edge(1, 2).setDistance(100).set(speedEnc, 10, 10);
        graph.edge(2, 3).setDistance(100).set(speedEnc, 10, 10);
        int costlyTarget = graph.edge(3, 4).setDistance(500).set(speedEnc, 10, 10).getEdge();
        int cheapSource = graph.edge(0, 5).setDistance(100).set(speedEnc, 10, 10).getEdge();
        graph.edge(5, 6).setDistance(100).set(speedEnc, 10, 10);
        graph.edge(6, 7).setDistance(100).set(speedEnc, 10, 10);
        int cheapTarget = graph.edge(7, 4).setDistance(100).set(speedEnc, 10, 10).getEdge();
        graph.edge(2, 6).setDistance(100).set(speedEnc, 10, 10);

        assertPath(calcPath(0, 4, cheapSource, cheapTarget), 40, 400, 40000, nodes(0, 5, 6, 7, 4));
        assertPath(calcPath(0, 4, cheapSource, costlyTarget), 90, 900, 90000, nodes(0, 5, 6, 2, 3, 4));
        assertPath(calcPath(0, 4, costlySource, cheapTarget), 90, 900, 90000, nodes(0, 1, 2, 6, 7, 4));
        assertPath(calcPath(0, 4, costlySource, costlyTarget), 120, 1200, 120000, nodes(0, 1, 2, 3, 4));
    }

    @Test
    public void notConnectedDueToRestrictions() {
        //   - 1 -
        //  /     \
        // 0       2
        //  \     /
        //   - 3 -
        // we cannot go from 0 to 2 if we enforce north-south or south-north
        int sourceNorth = graph.edge(0, 1).setDistance(100).set(speedEnc, 10, 10).getEdge();
        int sourceSouth = graph.edge(0, 3).setDistance(200).set(speedEnc, 10, 10).getEdge();
        int targetNorth = graph.edge(1, 2).setDistance(300).set(speedEnc, 10, 10).getEdge();
        int targetSouth = graph.edge(3, 2).setDistance(400).set(speedEnc, 10, 10).getEdge();

        assertPath(calcPath(0, 2, sourceNorth, targetNorth), 40, 400, 40000, nodes(0, 1, 2));
        assertNotFound(calcPath(0, 2, sourceNorth, targetSouth));
        assertNotFound(calcPath(0, 2, sourceSouth, targetNorth));
        assertPath(calcPath(0, 2, sourceSouth, targetSouth), 60, 600, 60000, nodes(0, 3, 2));
    }

    @Test
    public void restrictions_one_ways() {
        // 0 <- 1 <- 2
        //  \   |   /
        //   >--3-->
        graph.edge(0, 3).setDistance(100).set(speedEnc, 10, 0);
        graph.edge(1, 0).setDistance(100).set(speedEnc, 10, 0);
        graph.edge(3, 2).setDistance(100).set(speedEnc, 10, 0);
        graph.edge(2, 1).setDistance(100).set(speedEnc, 10, 0);
        graph.edge(1, 3).setDistance(100).set(speedEnc, 10, 10);

        assertPath(calcPath(0, 2, 0, 2), 20, 200, 20000, nodes(0, 3, 2));
        assertNotFound(calcPath(0, 2, 1, 2));
        assertNotFound(calcPath(0, 2, 0, 3));
        assertNotFound(calcPath(0, 2, 1, 3));
    }

    @Test
    public void forcingDirectionDoesNotMeanWeCannotUseEdgeAtAll() {
        // 0 - 6 - 7
        // |
        // 1
        // |
        // 2 - 3
        // |   |
        // 5 - 4
        int north = graph.edge(1, 0).setDistance(100).set(speedEnc, 10, 10).getEdge();
        int south = graph.edge(1, 2).setDistance(100).set(speedEnc, 10, 10).getEdge();
        graph.edge(2, 5).setDistance(100).set(speedEnc, 10, 0);
        graph.edge(5, 4).setDistance(100).set(speedEnc, 10, 0);
        graph.edge(4, 3).setDistance(100).set(speedEnc, 10, 0);
        graph.edge(3, 2).setDistance(100).set(speedEnc, 10, 0);
        graph.edge(1, 0).setDistance(100).set(speedEnc, 10, 0);
        graph.edge(0, 6).setDistance(100).set(speedEnc, 10, 0);
        int targetEdge = graph.edge(6, 7).setDistance(100).set(speedEnc, 10, 0).getEdge();
        assertPath(calcPath(1, 7, north, targetEdge), 30, 300, 30000, nodes(1, 0, 6, 7));
        assertPath(calcPath(1, 7, south, targetEdge), 90, 900, 90000, nodes(1, 2, 5, 4, 3, 2, 1, 0, 6, 7));
    }

    @Test
    public void directedCircle() {
        // 0---6--1 -> 2
        // |          /
        // 5 <- 4 <- 3
        graph.edge(0, 6).setDistance(100).set(speedEnc, 10, 10);
        graph.edge(6, 1).setDistance(100).set(speedEnc, 10, 10);
        graph.edge(1, 2).setDistance(100).set(speedEnc, 10, 0);
        graph.edge(2, 3).setDistance(100).set(speedEnc, 10, 0);
        graph.edge(3, 4).setDistance(100).set(speedEnc, 10, 0);
        graph.edge(4, 5).setDistance(100).set(speedEnc, 10, 0);
        graph.edge(5, 0).setDistance(100).set(speedEnc, 10, 0);
        assertPath(calcPath(6, 0, 1, 6), 60, 600, 60000, nodes(6, 1, 2, 3, 4, 5, 0));
    }

    @Test
    public void directedRouting() {
        // =: costly edge
        //   0 - 1   3 = 4
        //   |    \ /    |
        //   9     2     10
        //   |    / \    |
        //   8 = 7   6 = 5
        EdgeIteratorState rightNorth, rightSouth, leftSouth, leftNorth;
        graph.edge(0, 1).setDistance(100).set(speedEnc, 10, 10);
        graph.edge(1, 2).setDistance(100).set(speedEnc, 10, 10);
        graph.edge(2, 3).setDistance(100).set(speedEnc, 10, 10);
        graph.edge(3, 4).setDistance(300).set(speedEnc, 10, 10);
        rightNorth = graph.edge(4, 10).setDistance(100).set(speedEnc, 10, 10);
        rightSouth = graph.edge(10, 5).setDistance(100).set(speedEnc, 10, 10);
        graph.edge(5, 6).setDistance(200).set(speedEnc, 10, 10);
        graph.edge(6, 2).setDistance(100).set(speedEnc, 10, 10);
        graph.edge(2, 7).setDistance(100).set(speedEnc, 10, 10);
        graph.edge(7, 8).setDistance(900).set(speedEnc, 10, 10);
        leftSouth = graph.edge(8, 9).setDistance(100).set(speedEnc, 10, 10);
        leftNorth = graph.edge(9, 0).setDistance(100).set(speedEnc, 10, 10);

        // make paths fully deterministic by adding some restrictions
        setRestriction(1, 2, 3);
        setRestriction(1, 2, 7);

        final double unitEdgeWeight = 10;
        assertPath(calcPath(9, 9, leftNorth.getEdge(), leftSouth.getEdge()),
                23 * unitEdgeWeight, 2300, (long) ((23 * unitEdgeWeight) * 1000),
                nodes(9, 0, 1, 2, 6, 5, 10, 4, 3, 2, 7, 8, 9));
        assertPath(calcPath(9, 9, leftSouth.getEdge(), leftNorth.getEdge()),
                14 * unitEdgeWeight, 1400, (long) ((14 * unitEdgeWeight) * 1000),
                nodes(9, 8, 7, 2, 1, 0, 9));
        assertPath(calcPath(9, 10, leftSouth.getEdge(), rightSouth.getEdge()),
                15 * unitEdgeWeight, 1500, (long) ((15 * unitEdgeWeight) * 1000),
                nodes(9, 8, 7, 2, 6, 5, 10));
        assertPath(calcPath(9, 10, leftSouth.getEdge(), rightNorth.getEdge()),
                16 * unitEdgeWeight, 1600, (long) ((16 * unitEdgeWeight) * 1000),
                nodes(9, 8, 7, 2, 3, 4, 10));
    }

    @Test
    public void sourceAndTargetAreNeighbors() {
        // 0-1-2-3
        graph.edge(0, 1).setDistance(100).set(speedEnc, 10, 10);
        graph.edge(1, 2).setDistance(100).set(speedEnc, 10, 10);
        graph.edge(2, 3).setDistance(100).set(speedEnc, 10, 10);
        assertPath(calcPath(1, 2, ANY_EDGE, ANY_EDGE), 10, 100, 10000, nodes(1, 2));
        assertPath(calcPath(1, 2, 1, ANY_EDGE), 10, 100, 10000, nodes(1, 2));
        assertPath(calcPath(1, 2, ANY_EDGE, 1), 10, 100, 10000, nodes(1, 2));
        assertPath(calcPath(1, 2, 1, 1), 10, 100, 10000, nodes(1, 2));
        // this case is a bit sketchy: we may not find a valid path just because the from/to
        // node initialization hits the target/source node, but we have to also consider the
        // edge restriction at the source/target nodes
        assertNotFound(calcPath(1, 2, 1, 2));
        assertNotFound(calcPath(1, 2, 0, 1));
        assertNotFound(calcPath(1, 2, 0, 2));

        // if we allow u-turns it is of course different again
        assertPath(calcPath(1, 2, 1, 2, createWeighting(100)), 30 + 100, 300, 130000, nodes(1, 2, 3, 2));
        assertPath(calcPath(1, 2, 0, 1, createWeighting(100)), 30 + 100, 300, 130000, nodes(1, 0, 1, 2));
        assertPath(calcPath(1, 2, 0, 2, createWeighting(100)), 50 + 200, 500, 250000, nodes(1, 0, 1, 2, 3, 2));
    }

    @Test
    public void worksWithTurnCosts() {
        // 0 - 1 - 2
        // |   |   |
        // 3 - 4 - 5
        graph.edge(0, 1).setDistance(100).set(speedEnc, 10, 10);
        graph.edge(1, 2).setDistance(100).set(speedEnc, 10, 10);
        graph.edge(1, 4).setDistance(100).set(speedEnc, 10, 10);
        graph.edge(0, 3).setDistance(100).set(speedEnc, 10, 10);
        graph.edge(3, 4).setDistance(100).set(speedEnc, 10, 10);
        graph.edge(4, 5).setDistance(100).set(speedEnc, 10, 10);
        graph.edge(5, 2).setDistance(100).set(speedEnc, 10, 10);

        setRestriction(0, 3, 4);
        setTurnCost(4, 5, 2, 6);

        // due to the restrictions we have to take the expensive path with turn costs
        assertPath(calcPath(0, 2, 0, 6), 40 + 6, 400, 40000 + 6000, nodes(0, 1, 4, 5, 2));
        // enforcing going south from node 0 yields no path, because of the restricted turn 0->3->4
        assertNotFound(calcPath(0, 2, 3, ANY_EDGE));
        // without the restriction its possible
        assertPath(calcPath(0, 2, ANY_EDGE, ANY_EDGE), 20, 200, 20000, nodes(0, 1, 2));
    }

    @Test
    public void finiteUTurnCosts() {
        // = expensive edge
        //      3 - 4
        //      |   |
        //      2 = 5
        //      |
        // 0 -- 1 -- 6
        // |         |
        // 7 -- 8 -- 9
        int right0 = graph.edge(0, 1).setDistance(10).set(speedEnc, 10, 10).getEdge();
        graph.edge(1, 2).setDistance(10).set(speedEnc, 10, 10);
        graph.edge(2, 3).setDistance(10).set(speedEnc, 10, 10);
        graph.edge(3, 4).setDistance(10).set(speedEnc, 10, 10);
        graph.edge(4, 5).setDistance(10).set(speedEnc, 10, 10);
        graph.edge(5, 2).setDistance(1000).set(speedEnc, 10, 10);
        int left6 = graph.edge(1, 6).setDistance(10).set(speedEnc, 10, 10).getEdge();
        int left0 = graph.edge(0, 7).setDistance(10).set(speedEnc, 10, 10).getEdge();
        graph.edge(7, 8).setDistance(10).set(speedEnc, 10, 10);
        graph.edge(8, 9).setDistance(10).set(speedEnc, 10, 10);
        int right6 = graph.edge(9, 6).setDistance(10).set(speedEnc, 10, 10).getEdge();

        // enforce p-turn (using the loop in clockwise direction)
        setRestriction(0, 1, 6);
        setRestriction(5, 4, 3);

        assertPath(calcPath(0, 6, right0, left6), 107.0, 1070, 107000, nodes(0, 1, 2, 3, 4, 5, 2, 1, 6));
        // if the u-turn cost is finite it depends on its value if we rather do the p-turn or do an immediate u-turn at node 2
        assertPath(calcPath(0, 6, right0, left6, createWeighting(5000)), 107.0, 1070, 107000, nodes(0, 1, 2, 3, 4, 5, 2, 1, 6));
        assertPath(calcPath(0, 6, right0, left6, createWeighting(40)), 44, 40, 44000, nodes(0, 1, 2, 1, 6));

        assertPath(calcPath(0, 6, left0, right6), 4, 40, 4000, nodes(0, 7, 8, 9, 6));
        assertPath(calcPath(0, 6, left0, left6), 111, 1110, 111000, nodes(0, 7, 8, 9, 6, 1, 2, 3, 4, 5, 2, 1, 6));
        // if the u-turn cost is finite we do a u-turn at node 1 (not at node 7 at the beginning!)
        assertPath(calcPath(0, 6, left0, left6, createWeighting(40)), 46.0, 60, 46000, nodes(0, 7, 8, 9, 6, 1, 6));
    }

    @RepeatedTest(10)
    public void compare_standard_dijkstra() {
        compare_with_dijkstra(weighting, false);
    }

    @RepeatedTest(10)
    public void compare_standard_dijkstra_finite_uturn_costs() {
        compare_with_dijkstra(createWeighting(40), false);
    }

    @RepeatedTest(10)
    public void compare_standard_dijkstra_strict() {
        compare_with_dijkstra(weighting, true);
    }

    @RepeatedTest(10)
    public void compare_standard_dijkstra_finite_uturn_costs_strict() {
        compare_with_dijkstra(createWeighting(40), true);
    }

    private void compare_with_dijkstra(Weighting w, boolean tree) {
        // if we do not use start/target edge restrictions we should get the same result as with Dijkstra.
        // basically this test should cover all kinds of interesting cases except the ones where we restrict the
        // start/target edges.
        final long seed = System.nanoTime();
        final int numQueries = 1000;

        Random rnd = new Random(seed);
        int numNodes = 100;
        RandomGraph.start().seed(seed).nodes(numNodes).curviness(0.1).speedZero(tree ? 0 : 0.1).tree(tree).fill(graph, speedEnc);
        GHUtility.addRandomTurnCosts(graph, seed, null, turnCostEnc, maxTurnCosts, turnCostStorage);

        for (int i = 0; i < numQueries; i++) {
            int source = rnd.nextInt(numNodes);
            int target = rnd.nextInt(numNodes);
            Path dijkstraPath = new Dijkstra(graph, w, TraversalMode.EDGE_BASED).calcPath(source, target);
            Path path = calcPath(source, target, ANY_EDGE, ANY_EDGE, w);
            assertEquals(dijkstraPath.isFound(), path.isFound(), "dijkstra found/did not find a path, from: " + source + ", to: " + target + ", seed: " + seed);
            List<String> strictViolations = GHUtility.comparePaths(dijkstraPath, path, source, target, true, seed);
            if (tree && !strictViolations.isEmpty())
                fail(strictViolations.toString());
        }
    }

    @Test
    public void blockArea() {
        // 0 - 1 - 2 - 3
        // |           |
        // 4 --- 5 --- 6
        EdgeIteratorState edge1 = graph.edge(0, 1).setDistance(10).set(speedEnc, 10, 10);
        graph.edge(1, 2).setDistance(10).set(speedEnc, 10, 10);
        EdgeIteratorState edge2 = graph.edge(2, 3).setDistance(10).set(speedEnc, 10, 10);
        graph.edge(0, 4).setDistance(100).set(speedEnc, 10, 10);
        graph.edge(4, 5).setDistance(100).set(speedEnc, 10, 10);
        graph.edge(5, 6).setDistance(100).set(speedEnc, 10, 10);
        graph.edge(6, 3).setDistance(100).set(speedEnc, 10, 10);

        // usually we would take the direct route
        assertPath(calcPath(0, 3, ANY_EDGE, ANY_EDGE), 3, 30, 3000, nodes(0, 1, 2, 3));

        // with forced edges we might have to go around
        assertPath(calcPath(0, 3, 3, ANY_EDGE), 40, 400, 40000, nodes(0, 4, 5, 6, 3));
        assertPath(calcPath(0, 3, ANY_EDGE, 6), 40, 400, 40000, nodes(0, 4, 5, 6, 3));

        // with avoided edges we also have to take a longer route
        assertPath(calcPath(0, 3, ANY_EDGE, ANY_EDGE, createAvoidEdgeWeighting(edge1)), 40, 400, 40000, nodes(0, 4, 5, 6, 3));
        assertPath(calcPath(0, 3, ANY_EDGE, ANY_EDGE, createAvoidEdgeWeighting(edge2)), 40, 400, 40000, nodes(0, 4, 5, 6, 3));

        // enforcing forbidden start/target edges still does not allow using them
        assertNotFound(calcPath(0, 3, edge1.getEdge(), edge2.getEdge(), createAvoidEdgeWeighting(edge1)));
        assertNotFound(calcPath(0, 3, edge1.getEdge(), edge2.getEdge(), createAvoidEdgeWeighting(edge2)));

        // .. even when the nodes are just next to each other
        assertNotFound(calcPath(0, 1, edge1.getEdge(), ANY_EDGE, createAvoidEdgeWeighting(edge1)));
        assertNotFound(calcPath(0, 1, ANY_EDGE, edge2.getEdge(), createAvoidEdgeWeighting(edge2)));
    }

    private AvoidEdgesWeighting createAvoidEdgeWeighting(EdgeIteratorState edgeOut) {
        AvoidEdgesWeighting avoidEdgesWeighting = new AvoidEdgesWeighting(weighting);
        avoidEdgesWeighting.setEdgePenaltyFactor(Double.POSITIVE_INFINITY);
        avoidEdgesWeighting.setAvoidedEdges(IntHashSet.from(edgeOut.getEdge()));
        return avoidEdgesWeighting;
    }

    @Test
    public void directedRouting_noUTurnAtVirtualEdge() {
        // what happens if we force to leave the snapped (virtual) node in eastern direction, even though we would
        // like to go to node 0 just west from us ? we have to make sure there is no u-turn at node 1 (from the
        // virtual edge onto edge 1-0). the query graph does this for us!

        //    x
        // 0 -- 1 -> 2
        // |         |
        // 5 <- 4 <- 3
        graph.edge(0, 1).setDistance(0).set(speedEnc, 10, 10);
        graph.edge(1, 2).setDistance(0).set(speedEnc, 10, 0);
        graph.edge(2, 3).setDistance(0).set(speedEnc, 10, 0);
        graph.edge(3, 4).setDistance(0).set(speedEnc, 10, 0);
        graph.edge(4, 5).setDistance(0).set(speedEnc, 10, 0);
        graph.edge(5, 0).setDistance(0).set(speedEnc, 10, 0);
        updateDistancesFor(graph, 0, 0.01, 0.00);
        updateDistancesFor(graph, 1, 0.01, 0.01);
        updateDistancesFor(graph, 2, 0.01, 0.02);
        updateDistancesFor(graph, 3, 0.00, 0.02);
        updateDistancesFor(graph, 4, 0.00, 0.01);
        updateDistancesFor(graph, 5, 0.00, 0.00);

        LocationIndexTree locationIndex = new LocationIndexTree(graph, graph.getDirectory());
        locationIndex.prepareIndex();
        Snap snap = locationIndex.findClosest(0.011, 0.005, EdgeFilter.ALL_EDGES);
        QueryGraph queryGraph = QueryGraph.create(graph, snap);

        assertEquals(Snap.Position.EDGE, snap.getSnappedPosition(), "wanted to get EDGE");
        assertEquals(6, snap.getClosestNode());

        // check what edges there are on the query graph directly, there should not be a direct connection from 1 to 0
        // anymore, but only the virtual edge from 1 to 6 (this is how the u-turn is prevented).
        assertEquals(new HashSet<>(Arrays.asList(0, 2)), GHUtility.getNeighbors(graph.createEdgeExplorer().setBaseNode(1)));
        assertEquals(new HashSet<>(Arrays.asList(6, 2)), GHUtility.getNeighbors(queryGraph.createEdgeExplorer().setBaseNode(1)));

        EdgeIteratorState virtualEdge = GHUtility.getEdge(queryGraph, 6, 1);
        int outEdge = virtualEdge.getEdge();
        EdgeToEdgeRoutingAlgorithm algo = createAlgo(queryGraph, queryGraph.wrapWeighting(weighting));
        Path path = algo.calcPath(6, 0, outEdge, ANY_EDGE);
        assertEquals(nodes(6, 1, 2, 3, 4, 5, 0), path.calcNodes());
        assertEquals(6115.720, path.getDistance(), 1.e-3);
    }

    private Path calcPath(int source, int target, int sourceOutEdge, int targetInEdge) {
        return calcPath(source, target, sourceOutEdge, targetInEdge, weighting);
    }

    private Path calcPath(int source, int target, int sourceOutEdge, int targetInEdge, Weighting w) {
        EdgeToEdgeRoutingAlgorithm algo = createAlgo(graph, w);
        return algo.calcPath(source, target, sourceOutEdge, targetInEdge);
    }

    private EdgeToEdgeRoutingAlgorithm createAlgo(Graph graph, Weighting weighting) {
        return new DijkstraBidirectionRef(graph, weighting, TraversalMode.EDGE_BASED);
    }

    private IntArrayList nodes(int... nodes) {
        return IntArrayList.from(nodes);
    }

    private void assertPath(Path path, double weight, double distance, long time, IntArrayList nodes) {
        assertTrue(path.isFound(), "expected a path, but no path was found");
        assertEquals(nodes, path.calcNodes(), "unexpected nodes");
        assertEquals(weight, path.getWeight(), 1.e-6, "unexpected weight");
        assertEquals(distance, path.getDistance(), 1.e-6, "unexpected distance");
        assertEquals(time, path.getTime(), "unexpected time");
    }

    private void assertNotFound(Path path) {
        assertFalse(path.isFound(), "expected no path, but a path was found");
        assertEquals(Double.MAX_VALUE, path.getWeight(), 1.e-6);
        // if no path is found dist&time are zero, see core #1566
        assertEquals(0, path.getDistance(), 1.e-6);
        assertEquals(0, path.getTime());
        assertEquals(nodes(), path.calcNodes());
    }

    private void setRestriction(int fromNode, int node, int toNode) {
        setTurnCost(fromNode, node, toNode, Double.POSITIVE_INFINITY);
    }

    private void setTurnCost(int fromNode, int node, int toNode, double turnCost) {
        turnCostStorage.set(turnCostEnc, GHUtility.getEdge(graph, fromNode, node).getEdge(), node, GHUtility.getEdge(graph, node, toNode).getEdge(), turnCost);
    }
}
