package com.graphhopper.routing.matrix;

import com.graphhopper.routing.Dijkstra;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.DecimalEncodedValueImpl;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.SpeedWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.index.Snap;
import com.graphhopper.util.DistanceCalcEarth;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

import static com.graphhopper.routing.matrix.TargetSetDijkstra.Failure.*;
import static com.graphhopper.routing.util.TraversalMode.*;
import static org.junit.jupiter.api.Assertions.*;

class TargetSetDijkstraTest {
    private static final long UNLIMITED = Long.MAX_VALUE;

    @Test
    void handComputedToyMatricesKeepIndependentMetrics() {
        try (Fixture f = new Fixture(false)) {
            f.arc(0, 1, 2, 2000, 100);
            f.arc(1, 2, 2, 3000, 150);
            f.arc(0, 2, 9, 1000, 50);
            f.arc(2, 3, 2, 4000, 200);
            f.arc(1, 3, 9, 1000, 30);
            f.arc(3, 0, 7, 7000, 350);
            f.graph.getNodeAccess().setNode(4, 0, 0);
            double[][] weights = {{0, 2, 4, 6}, {11, 0, 2, 4}, {9, 11, 0, 2}, {7, 9, 11, 0}};
            long[][] times = {{0, 2000, 5000, 9000}, {14000, 0, 3000, 7000},
                    {11000, 13000, 0, 4000}, {7000, 9000, 12000, 0}};
            double[][] distances = {{0, 100, 250, 450}, {700, 0, 150, 350},
                    {550, 650, 0, 200}, {350, 450, 600, 0}};
            for (int source = 0; source < 5; source++) {
                TargetSetDijkstra search = new TargetSetDijkstra(f.graph, f.weighting, NODE_BASED).withDiagnostics();
                TargetSetDijkstra.Result result = search.search(source, Set.of(0, 1, 2, 3, 4), UNLIMITED, 100);
                for (int target = 0; target < 5; target++) {
                    if (source == 4 || target == 4) {
                        if (source == target)
                            assertEquals(new TargetSetDijkstra.Metrics(0, 0, 0), result.targets().get(target));
                        else
                            assertFalse(result.targets().containsKey(target));
                    } else {
                        assertEquals(new TargetSetDijkstra.Metrics(weights[source][target], times[source][target],
                                distances[source][target]), result.targets().get(target));
                        assertPathConsistent(f.graph, f.weighting, source, target, search, result.targets().get(target), "toy");
                    }
                }
            }
        }
    }

    @Test
    void realTurnStoragePreservesArrivalStatesAndEndpointSemantics() {
        for (double penalty : new double[]{Double.POSITIVE_INFINITY, 10, 0.5}) {
            try (Fixture f = new Fixture(true)) {
                EdgeIteratorState sj = f.arc(0, 2, 1, 1000, 100);
                EdgeIteratorState sk = f.arc(0, 1, 2, 2000, 200);
                EdgeIteratorState kj = f.arc(1, 2, 1, 1000, 100);
                EdgeIteratorState jt = f.arc(2, 3, 1, 1000, 100);
                f.graph.getTurnCostStorage().set(f.turnEnc, sj.getEdge(), 2, jt.getEdge(), penalty);
                TargetSetDijkstra search = new TargetSetDijkstra(f.graph, f.weighting, EDGE_BASED).withDiagnostics();
                TargetSetDijkstra.Result result = search.search(0, Set.of(0, 2, 3), UNLIMITED, 100);
                assertEquals(new TargetSetDijkstra.Metrics(1, 1000, 100), result.targets().get(2));
                boolean detour = penalty > 1;
                assertEquals(new TargetSetDijkstra.Metrics(detour ? 4 : 2.5, detour ? 4000 : 2500,
                        detour ? 400 : 200), result.targets().get(3));
                assertEquals(detour ? List.of(sk.getEdge(), kj.getEdge(), jt.getEdge()) : List.of(sj.getEdge(), jt.getEdge()),
                        search.diagnosticPath(3).stream().map(TargetSetDijkstra.Step::edge).toList());
                assertAgainstDijkstra(f.graph, f.weighting, EDGE_BASED, 0, 3, search, result, "turn penalty=" + penalty);
            }
        }
    }

    @Test
    void allowedPenalizedAndForbiddenUTurns() {
        for (double uTurn : new double[]{0, 2.5, Double.POSITIVE_INFINITY}) {
            try (Fixture f = new Fixture(true)) {
                f.uTurn = uTurn;
                EdgeIteratorState sj = f.arc(0, 1, 1, 1000, 100);
                EdgeIteratorState jk = f.arc(1, 2, 1, 1000, 100);
                f.costs.put(jk.getReverseEdgeKey(), new Cost(1, 1000));
                EdgeIteratorState jt = f.arc(1, 3, 1, 1000, 100);
                f.graph.getTurnCostStorage().set(f.turnEnc, sj.getEdge(), 1, jt.getEdge(), Double.POSITIVE_INFINITY);
                TargetSetDijkstra search = new TargetSetDijkstra(f.graph, f.weighting, EDGE_BASED).withDiagnostics();
                TargetSetDijkstra.Result result = search.search(0, Set.of(3), UNLIMITED, 100);
                if (Double.isInfinite(uTurn)) {
                    assertFalse(result.targets().containsKey(3));
                } else {
                    assertEquals(new TargetSetDijkstra.Metrics(4 + uTurn, 4000 + (long) (1000 * uTurn), 400),
                            result.targets().get(3));
                    assertPathConsistent(f.graph, f.weighting, 0, 3, search, result.targets().get(3), "u-turn=" + uTurn);
                }
                assertAgainstDijkstra(f.graph, f.weighting, EDGE_BASED, 0, 3, search, result, "u-turn=" + uTurn);
            }
        }
    }

    @Test
    void targetsAreSettledOnPopContinueThroughTargetsAndSkipStaleLabels() {
        try (Fixture f = new Fixture(false)) {
            f.arc(0, 3, 100, 1, 1);
            f.arc(0, 1, 1, 10, 10);
            f.arc(1, 2, 1, 20, 20);
            f.arc(2, 3, 1, 30, 30);
            f.graph.getNodeAccess().setNode(4, 0, 0);
            TargetSetDijkstra.Result result = new TargetSetDijkstra(f.graph, f.weighting, NODE_BASED)
                    .search(0, Set.of(0, 1, 2, 3, 4), UNLIMITED, 4);
            assertEquals(new TargetSetDijkstra.Metrics(3, 60, 60), result.targets().get(3));
            assertEquals(4, result.visitedStates());
            assertFalse(result.targets().containsKey(4));
        }
    }

    @Test
    void zeroCostsAndEqualWeightAlternativesHaveDeterministicLegalMetrics() {
        try (Fixture f = new Fixture(false)) {
            f.arc(0, 1, 0, 10, 10);
            f.arc(1, 0, 0, 10, 10);
            f.arc(0, 2, 0, 20, 20);
            f.arc(1, 3, 1, 100, 100);
            f.arc(2, 3, 1, 200, 200);
            List<TargetSetDijkstra.Step> previous = null;
            for (int repeat = 0; repeat < 5; repeat++) {
                TargetSetDijkstra search = new TargetSetDijkstra(f.graph, f.weighting, NODE_BASED).withDiagnostics();
                TargetSetDijkstra.Result result = search.search(0, Set.of(0, 1, 2, 3), UNLIMITED, 4);
                assertEquals(1, result.targets().get(3).weight());
                assertEquals(110, result.targets().get(3).timeMillis());
                assertEquals(110, result.targets().get(3).distance());
                assertPathConsistent(f.graph, f.weighting, 0, 3, search, result.targets().get(3), "explicit equal-cost alternatives");
                if (previous != null)
                    assertEquals(previous, search.diagnosticPath(3));
                previous = search.diagnosticPath(3);
            }
        }
    }

    @Test
    void directionalSpeedsAndSeveralVirtualPointsOnOneEdge() {
        DecimalEncodedValue speed = new DecimalEncodedValueImpl("speed", 8, 0.5, true);
        EncodingManager em = EncodingManager.start().add(speed).build();
        try (BaseGraph graph = new BaseGraph.Builder(em).create()) {
            graph.getNodeAccess().setNode(0, 53.47, -2.25);
            graph.getNodeAccess().setNode(1, 53.47, -2.24);
            graph.getNodeAccess().setNode(2, 53.48, -2.24);
            double fullDistance = DistanceCalcEarth.DIST_EARTH.calcDist(53.47, -2.25, 53.47, -2.24);
            EdgeIteratorState edge = graph.edge(0, 1).setDistance(fullDistance).set(speed, 10, 5);
            // Reverse-only second edge catches accidentally assuming edge creation direction.
            graph.edge(2, 1).setDistance(200).set(speed, 0, 20);
            List<Snap> snaps = List.of(snap(graph, edge, 0.2), snap(graph, edge, 0.5), snap(graph, edge, 0.8));
            graph.freeze();
            QueryGraph query = QueryGraph.create(graph, snaps);
            Weighting weighting = query.wrapWeighting(new SpeedWeighting(speed));
            Set<Integer> targets = allNodes(query);
            for (int source : targets) {
                TargetSetDijkstra search = new TargetSetDijkstra(query, weighting, NODE_BASED).withDiagnostics();
                TargetSetDijkstra.Result result = search.search(source, targets, UNLIMITED, 100);
                for (int target : targets)
                    assertAgainstDijkstra(query, weighting, NODE_BASED, source, target, search, result, "virtual directed");
            }
            int first = snaps.get(0).getClosestNode();
            int last = snaps.get(2).getClosestNode();
            TargetSetDijkstra.Metrics forward = new TargetSetDijkstra(query, weighting, NODE_BASED)
                    .search(first, Set.of(last), UNLIMITED, 100).targets().get(last);
            TargetSetDijkstra.Metrics backward = new TargetSetDijkstra(query, weighting, NODE_BASED)
                    .search(last, Set.of(first), UNLIMITED, 100).targets().get(first);
            assertEquals(2 * forward.weight(), backward.weight(), 1e-8);
            // Virtual-edge distances follow the actual edge geometry. Keep the stored full
            // distance consistent with that geometry and independently check the 20%-80% span.
            assertEquals(0.6 * fullDistance, forward.distance(), 0.01);
        }
    }

    @Test
    void deadlineCancellationAndVisitedLimitNeverReturnPartialSuccess() {
        try (Fixture f = new Fixture(false)) {
            f.arc(0, 1, 1, 1, 1);
            f.arc(1, 2, 1, 1, 1);
            assertFailure(DEADLINE_EXCEEDED, () -> new TargetSetDijkstra(f.graph, f.weighting, NODE_BASED)
                    .search(0, Set.of(0), System.nanoTime() - 1, 100));
            assertFailure(SEARCH_LIMIT_EXCEEDED, () -> new TargetSetDijkstra(f.graph, f.weighting, NODE_BASED)
                    .search(0, Set.of(0, 2), UNLIMITED, 1));
            Thread.currentThread().interrupt();
            try {
                assertFailure(CANCELLED, () -> new TargetSetDijkstra(f.graph, f.weighting, NODE_BASED)
                        .search(0, Set.of(2), UNLIMITED, 100));
                assertTrue(Thread.currentThread().isInterrupted());
            } finally {
                Thread.interrupted();
            }
            assertEquals(2, new TargetSetDijkstra(f.graph, f.weighting, NODE_BASED)
                    .search(0, Set.of(2), UNLIMITED, 100).targets().get(2).weight());
        }
    }

    @Test
    void interruptStopsAnInFlightSearchAndSubsequentWorkStillSucceeds() throws Exception {
        try (Fixture f = new Fixture(false)) {
            f.arc(0, 1, 1, 1, 1);
            f.arc(1, 2, 1, 1, 1);
            CountDownLatch entered = new CountDownLatch(1);
            CountDownLatch stopped = new CountDownLatch(1);
            AtomicReference<TargetSetDijkstra.Failure> failure = new AtomicReference<>();
            f.onWeight = () -> {
                entered.countDown();
                while (!Thread.currentThread().isInterrupted())
                    LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
            };
            ExecutorService worker = Executors.newSingleThreadExecutor();
            try {
                Future<?> task = worker.submit(() -> {
                    try {
                        new TargetSetDijkstra(f.graph, f.weighting, NODE_BASED)
                                .search(0, Set.of(2), UNLIMITED, 100);
                    } catch (TargetSetDijkstra.SearchException e) {
                        failure.set(e.reason());
                    } finally {
                        stopped.countDown();
                    }
                });
                assertTrue(entered.await(5, TimeUnit.SECONDS), "search did not begin");
                task.cancel(true);
                assertTrue(stopped.await(5, TimeUnit.SECONDS), "search did not stop after interruption");
                assertEquals(CANCELLED, failure.get());
            } finally {
                worker.shutdownNow();
                assertTrue(worker.awaitTermination(5, TimeUnit.SECONDS));
                f.onWeight = null;
            }
            assertEquals(2, new TargetSetDijkstra(f.graph, f.weighting, NODE_BASED)
                    .search(0, Set.of(2), UNLIMITED, 100).targets().get(2).weight());
        }
    }

    @Test
    void rejectsInvalidWeightsAndCatchesWeightAndDurationOverflow() {
        for (double weight : new double[]{-1, Double.NaN, Double.NEGATIVE_INFINITY}) {
            try (Fixture f = new Fixture(false)) {
                f.arc(0, 1, weight, 1, 1);
                assertFailure(INVALID_COST, () -> new TargetSetDijkstra(f.graph, f.weighting, NODE_BASED)
                        .search(0, Set.of(1), UNLIMITED, 100));
            }
        }
        try (Fixture f = new Fixture(false)) {
            f.arc(0, 1, Double.MAX_VALUE, 1, 1);
            f.arc(1, 2, Double.MAX_VALUE, 1, 1);
            assertFailure(METRIC_OVERFLOW, () -> new TargetSetDijkstra(f.graph, f.weighting, NODE_BASED)
                    .search(0, Set.of(2), UNLIMITED, 100));
        }
        try (Fixture f = new Fixture(false)) {
            f.arc(0, 1, 1, Long.MAX_VALUE, 1);
            f.arc(1, 2, 1, 1, 1);
            assertFailure(METRIC_OVERFLOW, () -> new TargetSetDijkstra(f.graph, f.weighting, NODE_BASED)
                    .search(0, Set.of(2), UNLIMITED, 100));
        }
        try (Fixture f = new Fixture(true)) {
            EdgeIteratorState first = f.arc(0, 1, 1, 1, 1);
            EdgeIteratorState second = f.arc(1, 2, 1, Long.MAX_VALUE - 1, 1);
            f.graph.getTurnCostStorage().set(f.turnEnc, first.getEdge(), 1, second.getEdge(), 10);
            assertFailure(METRIC_OVERFLOW, () -> new TargetSetDijkstra(f.graph, f.weighting, EDGE_BASED)
                    .search(0, Set.of(2), UNLIMITED, 100));
        }
        try (Fixture f = new Fixture(true)) {
            EdgeIteratorState first = f.arc(0, 1, 1, 0, 1);
            EdgeIteratorState second = f.arc(1, 2, 1, Long.MAX_VALUE, 1);
            f.graph.getTurnCostStorage().set(f.turnEnc, first.getEdge(), 1, second.getEdge(), 1);
            assertFailure(METRIC_OVERFLOW, () -> new TargetSetDijkstra(f.graph, f.weighting, EDGE_BASED)
                    .search(0, Set.of(2), UNLIMITED, 100));
        }
    }

    @Test
    void fiftyFixedSeedsCompareNodeEdgeAndQueryGraphsWithoutMetricWaivers() {
        for (int seed = 0; seed < 50; seed++) {
            for (TraversalMode mode : TraversalMode.values()) {
                long replaySeed = 730201L + seed;
                Random random = new Random(replaySeed);
                DecimalEncodedValue speed = new DecimalEncodedValueImpl("speed", 10, 0.01, true);
                DecimalEncodedValue turn = turnEncoding();
                EncodingManager em = EncodingManager.start().add(speed).addTurnCostEncodedValue(turn).build();
                try (BaseGraph graph = new BaseGraph.Builder(em).withTurnCosts(true).create()) {
                    int nodes = 20 + random.nextInt(61);
                    for (int node = 0; node < nodes; node++)
                        graph.getNodeAccess().setNode(node, 53 + node / 10 * 0.001, -2 + node % 10 * 0.001);
                    Set<Long> pairs = new HashSet<>();
                    List<EdgeIteratorState> edges = new ArrayList<>();
                    for (int node = 0; node < nodes; node++)
                        randomEdge(graph, speed, random, node, (node + 1) % nodes, pairs, edges);
                    while (edges.size() < nodes * 2)
                        randomEdge(graph, speed, random, random.nextInt(nodes), random.nextInt(nodes), pairs, edges);
                    if (mode.isEdgeBased()) {
                        for (int node = 0; node < nodes; node++) {
                            List<Integer> adjacent = new ArrayList<>();
                            EdgeIterator iterator = graph.createEdgeExplorer().setBaseNode(node);
                            while (iterator.next()) adjacent.add(iterator.getEdge());
                            for (int in : adjacent) {
                                for (int out : adjacent) {
                                    if (random.nextDouble() < 0.2)
                                        graph.getTurnCostStorage().set(turn, in, node, out,
                                                random.nextDouble() < 0.3 ? Double.POSITIVE_INFINITY : random.nextInt(12) * 0.5);
                                }
                            }
                        }
                    }
                    double uTurn = seed % 3 == 0 ? 0 : seed % 3 == 1 ? 2.5 : Double.POSITIVE_INFINITY;
                    Weighting baseWeighting = mode.isEdgeBased()
                            ? new SpeedWeighting(speed, turn, graph.getTurnCostStorage(), uTurn) : new SpeedWeighting(speed);
                    graph.freeze();
                    compareHundredPairs(graph, baseWeighting, mode, random, "seed=" + replaySeed + " mode=" + mode + " base");
                    List<Snap> snaps = List.of(snap(graph, edges.get(0), 0.2), snap(graph, edges.get(0), 0.5),
                            snap(graph, edges.get(0), 0.8), snap(graph, edges.get(1), 0.4));
                    QueryGraph query = QueryGraph.create(graph, snaps);
                    Weighting wrapped = query.wrapWeighting(baseWeighting);
                    compareHundredPairs(query, wrapped, mode, random, "seed=" + replaySeed + " mode=" + mode + " query");
                }
            }
        }
    }

    private static void compareHundredPairs(Graph graph, Weighting weighting, TraversalMode mode, Random random, String context) {
        for (int batch = 0; batch < 10; batch++) {
            int source = random.nextInt(graph.getNodes());
            Set<Integer> targets = new LinkedHashSet<>();
            while (targets.size() < 10) targets.add(random.nextInt(graph.getNodes()));
            TargetSetDijkstra search = new TargetSetDijkstra(graph, weighting, mode).withDiagnostics();
            TargetSetDijkstra.Result result = search.search(source, targets, UNLIMITED, 100000);
            for (int target : targets)
                assertAgainstDijkstra(graph, weighting, mode, source, target, search, result, context);
        }
    }

    private static void randomEdge(BaseGraph graph, DecimalEncodedValue speed, Random random, int from, int to,
                                   Set<Long> pairs, List<EdgeIteratorState> edges) {
        if (from == to) return;
        long key = ((long) Math.min(from, to) << 32) | Math.max(from, to);
        if (!pairs.add(key)) return;
        double forward = random.nextDouble() < 0.2 ? 0 : 1 + random.nextInt(900) * 0.01;
        double reverse = random.nextDouble() < 0.3 ? 0 : 1 + random.nextInt(900) * 0.01;
        if (forward == 0 && reverse == 0) forward = 5;
        edges.add(graph.edge(from, to).setDistance(10 + random.nextDouble() * 990).set(speed, forward, reverse));
    }

    private static Snap snap(Graph graph, EdgeIteratorState edge, double fraction) {
        double lat = graph.getNodeAccess().getLat(edge.getBaseNode()) * (1 - fraction)
                + graph.getNodeAccess().getLat(edge.getAdjNode()) * fraction;
        double lon = graph.getNodeAccess().getLon(edge.getBaseNode()) * (1 - fraction)
                + graph.getNodeAccess().getLon(edge.getAdjNode()) * fraction;
        Snap snap = new Snap(lat, lon);
        snap.setClosestEdge(edge);
        snap.setWayIndex(0);
        snap.setSnappedPosition(Snap.Position.EDGE);
        snap.calcSnappedPoint(new DistanceCalcEarth());
        return snap;
    }

    private static Set<Integer> allNodes(Graph graph) {
        Set<Integer> nodes = new LinkedHashSet<>();
        for (int node = 0; node < graph.getNodes(); node++) nodes.add(node);
        return nodes;
    }

    private static void assertAgainstDijkstra(Graph graph, Weighting weighting, TraversalMode mode, int source, int target,
                                               TargetSetDijkstra search, TargetSetDijkstra.Result result, String context) {
        Path oracle = new Dijkstra(graph, weighting, mode).calcPath(source, target);
        TargetSetDijkstra.Metrics actual = result.targets().get(target);
        String replay = context + " " + source + "->" + target + " actual=" + search.diagnosticPath(target)
                + " oracle=" + oracle.getEdges();
        assertEquals(oracle.isFound(), actual != null, replay);
        if (actual == null) return;
        assertEquals(oracle.getWeight(), actual.weight(), 1e-7 + Math.abs(oracle.getWeight()) * 1e-10, replay);
        // Random fixtures have independently varied edge costs. No failure fraction or assumed
        // equal-cost waiver is permitted; any metric difference is a replayable test failure.
        assertEquals(oracle.getTime(), actual.timeMillis(), replay);
        assertEquals(oracle.getDistance(), actual.distance(), 1e-8 + Math.abs(oracle.getDistance()) * 1e-12, replay);
        assertPathConsistent(graph, weighting, source, target, search, actual, replay);
    }

    private static void assertPathConsistent(Graph graph, Weighting weighting, int source, int target,
                                             TargetSetDijkstra search, TargetSetDijkstra.Metrics actual, String context) {
        double weight = 0, distance = 0;
        long time = 0;
        int node = source, previousEdge = EdgeIterator.NO_EDGE;
        for (TargetSetDijkstra.Step step : search.diagnosticPath(target)) {
            EdgeIteratorState edge = graph.getEdgeIteratorStateForKey(step.edgeKey());
            assertEquals(node, step.fromNode(), context);
            assertEquals(node, edge.getBaseNode(), context);
            assertEquals(step.toNode(), edge.getAdjNode(), context);
            double edgeWeight = weighting.calcEdgeWeight(edge, false);
            assertTrue(Double.isFinite(edgeWeight) && edgeWeight >= 0, context);
            weight += edgeWeight;
            time = Math.addExact(time, weighting.calcEdgeMillis(edge, false));
            if (previousEdge != EdgeIterator.NO_EDGE) {
                double turnWeight = weighting.calcTurnWeight(previousEdge, node, edge.getEdge());
                assertTrue(Double.isFinite(turnWeight) && turnWeight >= 0, context);
                weight += turnWeight;
                time = Math.addExact(time, weighting.calcTurnMillis(previousEdge, node, edge.getEdge()));
            }
            distance += edge.getDistance();
            node = edge.getAdjNode();
            previousEdge = edge.getEdge();
        }
        assertEquals(target, node, context);
        assertEquals(actual.weight(), weight, 1e-8 + Math.abs(weight) * 1e-12, context);
        assertEquals(actual.timeMillis(), time, context);
        assertEquals(actual.distance(), distance, 1e-8 + Math.abs(distance) * 1e-12, context);
    }

    private static void assertFailure(TargetSetDijkstra.Failure expected, org.junit.jupiter.api.function.Executable executable) {
        assertEquals(expected, assertThrows(TargetSetDijkstra.SearchException.class, executable).reason());
    }

    private static DecimalEncodedValue turnEncoding() {
        return new DecimalEncodedValueImpl("test_turn_cost", 8, 0, 0.5, false, false, true);
    }

    private record Cost(double weight, long time) {
    }

    /** Independent abstract metrics, with real upstream turn storage rather than node-only turns. */
    private static class Fixture implements AutoCloseable {
        final DecimalEncodedValue speed = new DecimalEncodedValueImpl("speed", 5, 1, true);
        final DecimalEncodedValue turnEnc = turnEncoding();
        final BaseGraph graph;
        final Map<Integer, Cost> costs = new HashMap<>();
        double uTurn = Double.POSITIVE_INFINITY;
        Runnable onWeight;
        final Weighting weighting;

        Fixture(boolean turns) {
            EncodingManager em = EncodingManager.start().add(speed).addTurnCostEncodedValue(turnEnc).build();
            graph = new BaseGraph.Builder(em).withTurnCosts(true).create();
            weighting = new Weighting() {
                public double calcMinWeightPerDistance() { return 0; }
                public double calcEdgeWeight(EdgeIteratorState edge, boolean reverse) {
                    if (onWeight != null) onWeight.run();
                    Cost cost = costs.get(reverse ? edge.getReverseEdgeKey() : edge.getEdgeKey());
                    return cost == null ? Double.POSITIVE_INFINITY : cost.weight;
                }
                public long calcEdgeMillis(EdgeIteratorState edge, boolean reverse) {
                    Cost cost = costs.get(reverse ? edge.getReverseEdgeKey() : edge.getEdgeKey());
                    return cost == null ? Long.MAX_VALUE : cost.time;
                }
                public double calcTurnWeight(int in, int via, int out) {
                    if (!turns || in == EdgeIterator.NO_EDGE || out == EdgeIterator.NO_EDGE) return 0;
                    double stored = graph.getTurnCostStorage().get(turnEnc, in, via, out);
                    return in == out ? Math.max(stored, uTurn) : stored;
                }
                public long calcTurnMillis(int in, int via, int out) { return (long) (1000 * calcTurnWeight(in, via, out)); }
                public boolean hasTurnCosts() { return turns; }
                public String getName() { return "independent_test_metrics"; }
            };
        }

        EdgeIteratorState arc(int from, int to, double weight, long time, double distance) {
            EdgeIteratorState edge = graph.edge(from, to).setDistance(distance);
            costs.put(edge.getEdgeKey(), new Cost(weight, time));
            return edge;
        }

        public void close() { graph.close(); }
    }
}
