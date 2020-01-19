package com.graphhopper.routing;

import com.carrotsearch.hppc.IntArrayList;
import com.graphhopper.Repeat;
import com.graphhopper.RepeatRule;
import com.graphhopper.routing.profiles.DecimalEncodedValue;
import com.graphhopper.routing.profiles.TurnCost;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.util.*;
import com.graphhopper.routing.weighting.*;
import com.graphhopper.storage.*;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.LocationIndexTree;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.GHUtility;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Random;

import static com.graphhopper.routing.profiles.TurnCost.EV_SUFFIX;
import static com.graphhopper.routing.util.EncodingManager.getKey;
import static com.graphhopper.util.EdgeIterator.ANY_EDGE;
import static com.graphhopper.util.EdgeIterator.NO_EDGE;
import static org.junit.Assert.*;

/**
 * This test makes sure that {@link DijkstraBidirectionRef#calcPath(int from, int to, int fromOutEdge, int toInEdge)}, i.e.
 * calculating a path with restricted/enforced first/last edges works as expected.
 * <p>
 * For other bidirectional algorithms we simply compare with {@link DijkstraBidirectionRef} in {@link DirectedRoutingTest}
 */
public class DirectedBidirectionalDijkstraTest {
    private Directory dir;
    private TurnCostStorage turnCostStorage;
    private int maxTurnCosts;
    private GraphHopperStorage graph;
    private FlagEncoder encoder;
    private EncodingManager encodingManager;
    private Weighting weighting;
    private DecimalEncodedValue turnCostEnc;

    @Rule
    public RepeatRule repeatRule = new RepeatRule();

    @Before
    public void setup() {
        dir = new RAMDirectory();
        maxTurnCosts = 10;
        encoder = new CarFlagEncoder(5, 5, maxTurnCosts);
        encodingManager = EncodingManager.create(encoder);
        graph = new GraphHopperStorage(dir, encodingManager, false, true).create(1000);
        turnCostStorage = graph.getTurnCostStorage();
        weighting = createWeighting(Double.POSITIVE_INFINITY);
        turnCostEnc = encodingManager.getDecimalEncodedValue(getKey(encoder.toString(), EV_SUFFIX));
    }

    private Weighting createWeighting(double defaultUTurnCosts) {
        return new TurnWeighting(new FastestWeighting(encoder), turnCostStorage, defaultUTurnCosts);
    }

    @Test
    public void connectionNotFound() {
        // nodes 0 and 2 are not connected
        // 0 -> 1     2 -> 3
        graph.edge(0, 1, 1, false);
        graph.edge(2, 3, 1, false);

        Path path = calcPath(0, 3, 0, 1);
        assertNotFound(path);
    }

    @Test
    public void singleEdge() {
        graph.edge(0, 1, 1, true);

        // source edge does not exist -> no path
        assertNotFound(calcPath(0, 1, 5, 0));
        // target edge does not exist -> no path
        assertNotFound(calcPath(0, 1, 0, 5));
        // using NO_EDGE -> no path
        assertNotFound(calcPath(0, 1, NO_EDGE, 0));
        assertNotFound(calcPath(0, 1, 0, NO_EDGE));
        // using ANY_EDGE -> no restriction
        assertPath(calcPath(0, 1, ANY_EDGE, 0), 0.06, 1, 60, nodes(0, 1));
        assertPath(calcPath(0, 1, 0, ANY_EDGE), 0.06, 1, 60, nodes(0, 1));
        // edges exist -> they are used as restrictions
        assertPath(calcPath(0, 1, 0, 0), 0.06, 1, 60, nodes(0, 1));
    }

    @Test
    public void simpleGraph() {
        // 0 -> 1 -> 2
        graph.edge(0, 1, 1, true);
        graph.edge(1, 2, 1, true);

        // source edge does not exist -> no path
        assertNotFound(calcPath(0, 2, 5, 0));
        // target edge does not exist -> no path
        assertNotFound(calcPath(0, 2, 0, 5));
        // using NO_EDGE -> no  path
        assertNotFound(calcPath(0, 2, NO_EDGE, 0));
        assertNotFound(calcPath(0, 2, 0, NO_EDGE));
        // using ANY_EDGE -> no restriction
        assertPath(calcPath(0, 2, ANY_EDGE, 1), 0.12, 2, 120, nodes(0, 1, 2));
        assertPath(calcPath(0, 2, 0, ANY_EDGE), 0.12, 2, 120, nodes(0, 1, 2));
        // edges exist -> they are used as restrictions
        assertPath(calcPath(0, 2, 0, 1), 0.12, 2, 120, nodes(0, 1, 2));
    }

    @Test
    public void sourceEqualsTarget() {
        // Since we are enforcing source/target edges, starting and arriving at the same node normally does not yield
        // zero weight. Either the weight is finite (using a real path back to the source node) or the
        // weight is infinite (when no such path exists). The exception of course would involve zero weight edges.
        // 0 - 1
        //  \  |
        //   - 2
        graph.edge(0, 1, 1, true);
        graph.edge(0, 2, 1, true);
        graph.edge(1, 2, 1, true);
        assertPath(calcPath(0, 0, 0, 1), 0.18, 3, 180, nodes(0, 1, 2, 0));
        assertPath(calcPath(0, 0, 1, 0), 0.18, 3, 180, nodes(0, 2, 1, 0));
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
        int costlySource = graph.edge(0, 1, 5, true).getEdge();
        graph.edge(1, 2, 1, true);
        graph.edge(2, 3, 1, true);
        int costlyTarget = graph.edge(3, 4, 5, true).getEdge();
        int cheapSource = graph.edge(0, 5, 1, true).getEdge();
        graph.edge(5, 6, 1, true);
        graph.edge(6, 7, 1, true);
        int cheapTarget = graph.edge(7, 4, 1, true).getEdge();
        graph.edge(2, 6, 1, true);

        assertPath(calcPath(0, 4, cheapSource, cheapTarget), 0.24, 4, 240, nodes(0, 5, 6, 7, 4));
        assertPath(calcPath(0, 4, cheapSource, costlyTarget), 0.54, 9, 540, nodes(0, 5, 6, 2, 3, 4));
        assertPath(calcPath(0, 4, costlySource, cheapTarget), 0.54, 9, 540, nodes(0, 1, 2, 6, 7, 4));
        assertPath(calcPath(0, 4, costlySource, costlyTarget), 0.72, 12, 720, nodes(0, 1, 2, 3, 4));
    }

    @Test
    public void notConnectedDueToRestrictions() {
        //   - 1 -
        //  /     \
        // 0       2
        //  \     /
        //   - 3 -
        // we cannot go from 0 to 2 if we enforce north-south or south-north
        int sourceNorth = graph.edge(0, 1, 1, true).getEdge();
        int sourceSouth = graph.edge(0, 3, 2, true).getEdge();
        int targetNorth = graph.edge(1, 2, 3, true).getEdge();
        int targetSouth = graph.edge(3, 2, 4, true).getEdge();

        assertPath(calcPath(0, 2, sourceNorth, targetNorth), 0.24, 4, 240, nodes(0, 1, 2));
        assertNotFound(calcPath(0, 2, sourceNorth, targetSouth));
        assertNotFound(calcPath(0, 2, sourceSouth, targetNorth));
        assertPath(calcPath(0, 2, sourceSouth, targetSouth), 0.36, 6, 360, nodes(0, 3, 2));
    }

    @Test
    public void restrictions_one_ways() {
        // 0 <- 1 <- 2
        //  \   |   /
        //   >--3-->
        graph.edge(0, 3, 1, false);
        graph.edge(1, 0, 1, false);
        graph.edge(3, 2, 1, false);
        graph.edge(2, 1, 1, false);
        graph.edge(1, 3, 1, true);

        assertPath(calcPath(0, 2, 0, 2), 0.12, 2, 120, nodes(0, 3, 2));
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
        int north = graph.edge(1, 0, 1, true).getEdge();
        int south = graph.edge(1, 2, 1, true).getEdge();
        graph.edge(2, 5, 1, false);
        graph.edge(5, 4, 1, false);
        graph.edge(4, 3, 1, false);
        graph.edge(3, 2, 1, false);
        graph.edge(1, 0, 1, false);
        graph.edge(0, 6, 1, false);
        int targetEdge = graph.edge(6, 7, 1, false).getEdge();
        assertPath(calcPath(1, 7, north, targetEdge), 0.18, 3, 180, nodes(1, 0, 6, 7));
        assertPath(calcPath(1, 7, south, targetEdge), 0.54, 9, 540, nodes(1, 2, 5, 4, 3, 2, 1, 0, 6, 7));
    }

    @Test
    public void directedCircle() {
        // 0---6--1 -> 2
        // |          /
        // 5 <- 4 <- 3
        graph.edge(0, 6, 1, true);
        graph.edge(6, 1, 1, true);
        graph.edge(1, 2, 1, false);
        graph.edge(2, 3, 1, false);
        graph.edge(3, 4, 1, false);
        graph.edge(4, 5, 1, false);
        graph.edge(5, 0, 1, false);
        assertPath(calcPath(6, 0, 1, 6), 0.36, 6, 360, nodes(6, 1, 2, 3, 4, 5, 0));
    }

    @Test
    public void directedRouting() {
        // =: costly edge
        //   0 - 1   3 = 4
        //   |    \ /    |
        //   9     2     10
        //   |    / \    |
        //   8 = 7   6 = 5
        graph.edge(0, 1, 1, true);
        graph.edge(1, 2, 1, true);
        graph.edge(2, 3, 1, true);
        graph.edge(3, 4, 3, true);
        int rightNorth = graph.edge(4, 10, 1, true).getEdge();
        int rightSouth = graph.edge(10, 5, 1, true).getEdge();
        graph.edge(5, 6, 2, true);
        graph.edge(6, 2, 1, true);
        graph.edge(2, 7, 1, true);
        graph.edge(7, 8, 9, true);
        int leftSouth = graph.edge(8, 9, 1, true).getEdge();
        int leftNorth = graph.edge(9, 0, 1, true).getEdge();

        // make paths fully deterministic by applying some turn costs at junction node 2
        setTurnCost(7, 2, 3, 1);
        setTurnCost(7, 2, 6, 3);
        setTurnCost(1, 2, 3, 5);
        setTurnCost(1, 2, 6, 7);
        setTurnCost(1, 2, 7, 9);

        final double unitEdgeWeight = 0.06;
        assertPath(calcPath(9, 9, leftNorth, leftSouth),
                23 * unitEdgeWeight + 5, 23, (long) ((23 * unitEdgeWeight + 5) * 1000),
                nodes(9, 0, 1, 2, 3, 4, 10, 5, 6, 2, 7, 8, 9));
        assertPath(calcPath(9, 9, leftSouth, leftNorth),
                14 * unitEdgeWeight, 14, (long) ((14 * unitEdgeWeight) * 1000),
                nodes(9, 8, 7, 2, 1, 0, 9));
        assertPath(calcPath(9, 10, leftSouth, rightSouth),
                15 * unitEdgeWeight + 3, 15, (long) ((15 * unitEdgeWeight + 3) * 1000),
                nodes(9, 8, 7, 2, 6, 5, 10));
        assertPath(calcPath(9, 10, leftSouth, rightNorth),
                16 * unitEdgeWeight + 1, 16, (long) ((16 * unitEdgeWeight + 1) * 1000),
                nodes(9, 8, 7, 2, 3, 4, 10));
    }

    @Test
    public void enforceLoopEdge() {
        //  o       o
        //  0 - 1 - 2
        graph.edge(0, 0, 1, true);
        graph.edge(0, 1, 1, true);
        graph.edge(1, 2, 1, true);
        graph.edge(2, 2, 1, true);

        assertPath(calcPath(0, 2, ANY_EDGE, ANY_EDGE), 0.12, 2, 120, nodes(0, 1, 2));
        assertPath(calcPath(0, 2, 1, 2), 0.12, 2, 120, nodes(0, 1, 2));
        // we can enforce taking the loop at start/target
        assertPath(calcPath(0, 2, 0, 2), 0.18, 3, 180, nodes(0, 0, 1, 2));
        assertPath(calcPath(0, 2, 1, 3), 0.18, 3, 180, nodes(0, 1, 2, 2));
        assertPath(calcPath(0, 2, 0, 3), 0.24, 4, 240, nodes(0, 0, 1, 2, 2));
    }

    @Test
    public void sourceAndTargetAreNeighbors() {
        // 0-1-2-3
        graph.edge(0, 1, 100, true);
        graph.edge(1, 2, 100, true);
        graph.edge(2, 3, 100, true);
        assertPath(calcPath(1, 2, ANY_EDGE, ANY_EDGE), 6, 100, 6000, nodes(1, 2));
        assertPath(calcPath(1, 2, 1, ANY_EDGE), 6, 100, 6000, nodes(1, 2));
        assertPath(calcPath(1, 2, ANY_EDGE, 1), 6, 100, 6000, nodes(1, 2));
        assertPath(calcPath(1, 2, 1, 1), 6, 100, 6000, nodes(1, 2));
        // this case is a bit sketchy: we may not find a valid path just because the from/to
        // node initialization hits the target/source node, but we have to also consider the
        // edge restriction at the source/target nodes
        assertNotFound(calcPath(1, 2, 1, 2));
        assertNotFound(calcPath(1, 2, 0, 1));
        assertNotFound(calcPath(1, 2, 0, 2));

        // if we allow u-turns it is of course different again
        assertPath(calcPath(1, 2, 1, 2, createWeighting(100)), 118, 300, 118000, nodes(1, 2, 3, 2));
        assertPath(calcPath(1, 2, 0, 1, createWeighting(100)), 118, 300, 118000, nodes(1, 0, 1, 2));
        assertPath(calcPath(1, 2, 0, 2, createWeighting(100)), 230, 500, 230000, nodes(1, 0, 1, 2, 3, 2));
    }

    @Test
    public void worksWithTurnCosts() {
        // 0 - 1 - 2
        // |   |   |
        // 3 - 4 - 5
        graph.edge(0, 1, 1, true);
        graph.edge(1, 2, 1, true);
        graph.edge(1, 4, 1, true);
        graph.edge(0, 3, 1, true);
        graph.edge(3, 4, 1, true);
        graph.edge(4, 5, 1, true);
        graph.edge(5, 2, 1, true);

        addRestriction(0, 3, 4);
        setTurnCost(4, 5, 2, 6);

        // due to the restrictions we have to take the expensive path with turn costs
        assertPath(calcPath(0, 2, 0, 6), 6.24, 4, 6240, nodes(0, 1, 4, 5, 2));
        // enforcing going south from node 0 yields no path, because of the restricted turn 0->3->4
        assertNotFound(calcPath(0, 2, 3, ANY_EDGE));
        // without the restriction its possible
        assertPath(calcPath(0, 2, ANY_EDGE, ANY_EDGE), 0.12, 2, 120, nodes(0, 1, 2));
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
        int right0 = graph.edge(0, 1, 10, true).getEdge();
        graph.edge(1, 2, 10, true);
        graph.edge(2, 3, 10, true);
        graph.edge(3, 4, 10, true);
        graph.edge(4, 5, 10, true);
        graph.edge(5, 2, 1000, true);
        int left6 = graph.edge(1, 6, 10, true).getEdge();
        int left0 = graph.edge(0, 7, 10, true).getEdge();
        graph.edge(7, 8, 10, true);
        graph.edge(8, 9, 10, true);
        int right6 = graph.edge(9, 6, 10, true).getEdge();

        // enforce p-turn (using the loop in clockwise direction)
        addRestriction(0, 1, 6);
        addRestriction(5, 4, 3);

        assertPath(calcPath(0, 6, right0, left6), 64.2, 1070, 64200, nodes(0, 1, 2, 3, 4, 5, 2, 1, 6));
        // if the u-turn cost is finite it depends on its value if we rather do the p-turn or do an immediate u-turn at node 2
        assertPath(calcPath(0, 6, right0, left6, createWeighting(65)), 64.2, 1070, 64200, nodes(0, 1, 2, 3, 4, 5, 2, 1, 6));
        assertPath(calcPath(0, 6, right0, left6, createWeighting(40)), 42.4, 40, 42400, nodes(0, 1, 2, 1, 6));

        assertPath(calcPath(0, 6, left0, right6), 2.4, 40, 2400, nodes(0, 7, 8, 9, 6));
        assertPath(calcPath(0, 6, left0, left6), 66.6, 1110, 66600, nodes(0, 7, 8, 9, 6, 1, 2, 3, 4, 5, 2, 1, 6));
        // if the u-turn cost is finite we do a u-turn at node 1 (not at node 7 at the beginning!)
        assertPath(calcPath(0, 6, left0, left6, createWeighting(40)), 43.6, 60, 43600, nodes(0, 7, 8, 9, 6, 1, 6));
    }

    @Test
    @Repeat(times = 10)
    public void compare_standard_dijkstra() {
        compare_with_dijkstra(weighting);
    }

    @Test
    @Repeat(times = 10)
    public void compare_standard_dijkstra_finite_uturn_costs() {
        compare_with_dijkstra(createWeighting(40));
    }

    private void compare_with_dijkstra(Weighting w) {
        // if we do not use start/target edge restrictions we should get the same result as with Dijkstra.
        // basically this test should cover all kinds of interesting cases except the ones where we restrict the
        // start/target edges.
        final long seed = System.nanoTime();
        final int numQueries = 1000;
        System.out.println("compare_standard_dijkstra seed: " + seed);

        Random rnd = new Random(seed);
        int numNodes = 100;
        GHUtility.buildRandomGraph(graph, rnd, numNodes, 2.2, true, true, encoder.getAverageSpeedEnc(), 0.7, 0.8, 0.8);
        GHUtility.addRandomTurnCosts(graph, seed, encodingManager, encoder, maxTurnCosts, turnCostStorage);

        long numStrictViolations = 0;
        for (int i = 0; i < numQueries; i++) {
            int source = rnd.nextInt(numNodes);
            int target = rnd.nextInt(numNodes);
            Path dijkstraPath = new Dijkstra(graph, w, TraversalMode.EDGE_BASED).calcPath(source, target);
            Path path = calcPath(source, target, ANY_EDGE, ANY_EDGE, w);
            assertEquals("dijkstra found/did not find a path, from: " + source + ", to: " + target, dijkstraPath.isFound(), path.isFound());
            assertEquals("weight does not match dijkstra, from: " + source + ", to: " + target, dijkstraPath.getWeight(), path.getWeight(), 1.e-6);
            // we do not do a strict check because there can be ambiguity, for example when there are zero weight loops.
            // however, when there are too many deviations we fail
            if (
                    Math.abs(dijkstraPath.getDistance() - path.getDistance()) > 1.e-6
                            || Math.abs(dijkstraPath.getTime() - path.getTime()) > 10
                            || !dijkstraPath.calcNodes().equals(path.calcNodes())) {
                numStrictViolations++;
            }
        }
        if (numStrictViolations > Math.max(1, 0.05 * numQueries)) {
            fail("Too many strict violations: " + numStrictViolations + " / " + numQueries);
        }
    }

    @Test
    public void blockArea() {
        // 0 - 1 - 2 - 3
        // |           |
        // 4 --- 5 --- 6
        EdgeIteratorState edge1 = graph.edge(0, 1, 10, true);
        graph.edge(1, 2, 10, true);
        EdgeIteratorState edge2 = graph.edge(2, 3, 10, true);
        graph.edge(0, 4, 100, true);
        graph.edge(4, 5, 100, true);
        graph.edge(5, 6, 100, true);
        graph.edge(6, 3, 100, true);

        // usually we would take the direct route
        assertPath(calcPath(0, 3, ANY_EDGE, ANY_EDGE), 1.8, 30, 1800, nodes(0, 1, 2, 3));

        // with forced edges we might have to go around
        assertPath(calcPath(0, 3, 3, ANY_EDGE), 24, 400, 24000, nodes(0, 4, 5, 6, 3));
        assertPath(calcPath(0, 3, ANY_EDGE, 6), 24, 400, 24000, nodes(0, 4, 5, 6, 3));

        // with avoided edges we also have to take a longer route
        assertPath(calcPath(0, 3, ANY_EDGE, ANY_EDGE, createAvoidEdgeWeighting(edge1)), 24, 400, 24000, nodes(0, 4, 5, 6, 3));
        assertPath(calcPath(0, 3, ANY_EDGE, ANY_EDGE, createAvoidEdgeWeighting(edge2)), 24, 400, 24000, nodes(0, 4, 5, 6, 3));

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
        avoidEdgesWeighting.addEdges(Collections.singletonList(edgeOut));
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
        graph.edge(0, 1, 1, true);
        graph.edge(1, 2, 1, false);
        graph.edge(2, 3, 1, false);
        graph.edge(3, 4, 1, false);
        graph.edge(4, 5, 1, false);
        graph.edge(5, 0, 1, false);
        NodeAccess na = graph.getNodeAccess();
        na.setNode(0, 1, 0);
        na.setNode(1, 1, 1);
        na.setNode(2, 1, 2);
        na.setNode(3, 0, 2);
        na.setNode(4, 0, 1);
        na.setNode(5, 0, 0);

        LocationIndex locationIndex = new LocationIndexTree(graph, dir);
        locationIndex.prepareIndex();
        QueryResult qr = locationIndex.findClosest(1.1, 0.5, EdgeFilter.ALL_EDGES);
        QueryGraph queryGraph = QueryGraph.lookup(graph, qr);

        assertEquals("wanted to get EDGE", QueryResult.Position.EDGE, qr.getSnappedPosition());
        assertEquals(6, qr.getClosestNode());

        // check what edges there are on the query graph directly, there should not be a direct connection from 1 to 0
        // anymore, but only the virtual edge from 1 to 6 (this is how the u-turn is prevented).
        assertEquals(new HashSet<>(Arrays.asList(0, 2)), GHUtility.getNeighbors(graph.createEdgeExplorer().setBaseNode(1)));
        assertEquals(new HashSet<>(Arrays.asList(6, 2)), GHUtility.getNeighbors(queryGraph.createEdgeExplorer().setBaseNode(1)));

        EdgeIteratorState virtualEdge = GHUtility.getEdge(queryGraph, 6, 1);
        int outEdge = virtualEdge.getEdge();
        BidirRoutingAlgorithm algo = createAlgo(queryGraph, weighting);
        Path path = algo.calcPath(6, 0, outEdge, ANY_EDGE);
        assertEquals(nodes(6, 1, 2, 3, 4, 5, 0), path.calcNodes());
        assertEquals(5 + virtualEdge.getDistance(), path.getDistance(), 1.e-3);
    }

    private Path calcPath(int source, int target, int sourceOutEdge, int targetInEdge) {
        return calcPath(source, target, sourceOutEdge, targetInEdge, weighting);
    }

    private Path calcPath(int source, int target, int sourceOutEdge, int targetInEdge, Weighting w) {
        BidirRoutingAlgorithm algo = createAlgo(graph, w);
        return algo.calcPath(source, target, sourceOutEdge, targetInEdge);
    }

    private BidirRoutingAlgorithm createAlgo(Graph graph, Weighting weighting) {
        return new DijkstraBidirectionRef(graph, weighting, TraversalMode.EDGE_BASED);
    }

    private void addRestriction(int fromNode, int node, int toNode) {
        IntsRef tcFlags = TurnCost.createFlags();
        turnCostEnc.setDecimal(false, tcFlags, Double.POSITIVE_INFINITY);
        turnCostStorage.setTurnCost(
                tcFlags,
                GHUtility.getEdge(graph, fromNode, node).getEdge(),
                node,
                GHUtility.getEdge(graph, node, toNode).getEdge()
        );
    }

    private void setTurnCost(int fromNode, int node, int toNode, double turnCost) {
        IntsRef tcFlags = TurnCost.createFlags();
        turnCostEnc.setDecimal(false, tcFlags, turnCost);
        turnCostStorage.setTurnCost(
                tcFlags,
                GHUtility.getEdge(graph, fromNode, node).getEdge(),
                node,
                GHUtility.getEdge(graph, node, toNode).getEdge());
    }

    private IntArrayList nodes(int... nodes) {
        return IntArrayList.from(nodes);
    }

    private void assertPath(Path path, double weight, double distance, long time, IntArrayList nodes) {
        assertTrue("expected a path, but no path was found", path.isFound());
        assertEquals("unexpected weight", weight, path.getWeight(), 1.e-6);
        assertEquals("unexpected distance", distance, path.getDistance(), 1.e-6);
        assertEquals("unexpected time", time, path.getTime());
        assertEquals("unexpected nodes", nodes, path.calcNodes());
    }

    private void assertNotFound(Path path) {
        assertFalse("expected no path, but a path was found", path.isFound());
        assertEquals(Double.MAX_VALUE, path.getWeight(), 1.e-6);
        // if no path is found dist&time are zero, see core #1566
        assertEquals(0, path.getDistance(), 1.e-6);
        assertEquals(0, path.getTime());
        assertEquals(nodes(), path.calcNodes());
    }
}
