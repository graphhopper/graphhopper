package com.graphhopper.routing.matrix;

import com.graphhopper.routing.Dijkstra;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.ch.NodeOrderingProvider;
import com.graphhopper.routing.ch.PrepareContractionHierarchies;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.DecimalEncodedValueImpl;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.querygraph.QueryRoutingCHGraph;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.SpeedWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.*;
import com.graphhopper.storage.index.Snap;
import com.graphhopper.util.DistanceCalcEarth;
import com.graphhopper.util.EdgeIteratorState;
import org.junit.jupiter.api.Test;

import java.util.*;

import static com.graphhopper.routing.matrix.TargetSetDijkstra.Failure.*;
import static org.junit.jupiter.api.Assertions.*;

class BucketCHMatrixTest {
    @Test
    void directedToyUnpacksIndependentTimeDistanceAndDisconnectedCells() {
        try (Fixture f = new Fixture()) {
            f.arc(0, 1, 2, 2000, 100);
            f.arc(1, 2, 2, 3000, 150);
            f.arc(0, 2, 9, 1000, 50);
            f.arc(2, 3, 2, 4000, 200);
            f.arc(1, 3, 9, 1000, 30);
            f.arc(3, 0, 7, 7000, 350);
            f.graph.getNodeAccess().setNode(4, 0, 0);
            RoutingCHGraph ch = prepare(f.graph, f.weighting, NodeOrderingProvider.fromArray(1, 2, 0, 3, 4));
            BucketCHMatrix search = new BucketCHMatrix(ch);
            search.prepare(Set.of(0, 1, 2, 3, 4), Long.MAX_VALUE, 1000);
            for (int source = 0; source < 5; source++) {
                BucketCHMatrix.Row row = search.search(source, 1000);
                for (int target = 0; target < 5; target++) assertOracle(f.graph, f.weighting, source, target, row, "toy");
            }
            assertEquals(new BucketCHMatrix.Metrics(6, 9000, 450), search.search(0, 1000).targets().get(3));
            assertEquals(5, search.statistics().backwardSearches());
            assertEquals(6, search.statistics().forwardSearches());
            assertTrue(search.statistics().bucketEntries() > 5);
            assertTrue(search.statistics().unpackedOriginalEdges() > 0);
            assertTrue(search.statistics().metricExtractionNanos() > 0);
        }
    }

    @Test
    void twentySeededDirectedBaseGraphsMatchIndependentDijkstra() {
        DecimalEncodedValue speed = new DecimalEncodedValueImpl("speed", 10, 0.01, true);
        EncodingManager em = EncodingManager.start().add(speed).build();
        for (int seed = 0; seed < 20; seed++) {
            Random random = new Random(2026101900L + seed);
            try (BaseGraph graph = new BaseGraph.Builder(em).create()) {
                int nodes = 16;
                for (int node = 0; node < nodes; node++)
                    graph.getNodeAccess().setNode(node, 53 + node * 0.001, -2 + (node % 4) * 0.001);
                Set<Long> pairs = new HashSet<>();
                for (int node = 0; node < nodes - 1; node++) randomEdge(graph, speed, random, node, node + 1, pairs);
                while (pairs.size() < 40)
                    randomEdge(graph, speed, random, random.nextInt(nodes), random.nextInt(nodes), pairs);
                Weighting weighting = new SpeedWeighting(speed);
                RoutingCHGraph ch = prepare(graph, weighting, null);
                BucketCHMatrix search = new BucketCHMatrix(ch);
                Set<Integer> targets = new LinkedHashSet<>();
                for (int target = 0; target < nodes; target++) targets.add(target);
                search.prepare(targets, Long.MAX_VALUE, 100000);
                for (int source = 0; source < nodes; source++) {
                    BucketCHMatrix.Row row = search.search(source, 100000);
                    for (int target : targets)
                        assertOracle(graph, weighting, source, target, row, "seed=" + seed + " " + source + "->" + target);
                }
            }
        }
    }

    @Test
    void sameEdgeVirtualPointsRespectDirectionAndTargetBatching() {
        DecimalEncodedValue speed = new DecimalEncodedValueImpl("speed", 8, 0.5, true);
        EncodingManager em = EncodingManager.start().add(speed).build();
        try (BaseGraph graph = new BaseGraph.Builder(em).create()) {
            graph.getNodeAccess().setNode(0, 53.47, -2.25);
            graph.getNodeAccess().setNode(1, 53.47, -2.24);
            graph.getNodeAccess().setNode(2, 53.48, -2.24);
            graph.getNodeAccess().setNode(3, 53.49, -2.24);
            double distance = DistanceCalcEarth.DIST_EARTH.calcDist(53.47, -2.25, 53.47, -2.24);
            EdgeIteratorState edge = graph.edge(0, 1).setDistance(distance).set(speed, 10, 5);
            graph.edge(1, 2).setDistance(200).set(speed, 20, 0);
            Weighting weighting = new SpeedWeighting(speed);
            RoutingCHGraph ch = prepare(graph, weighting, NodeOrderingProvider.identity(4));
            List<Snap> snaps = List.of(snap(graph, edge, 0.2), snap(graph, edge, 0.5), snap(graph, edge, 0.8));
            QueryGraph query = QueryGraph.create(graph, snaps);
            RoutingCHGraph queryCH = new QueryRoutingCHGraph(ch, query);
            Set<Integer> targets = new LinkedHashSet<>(List.of(snaps.get(0).getClosestNode(),
                    snaps.get(1).getClosestNode(), snaps.get(2).getClosestNode(), 2, 3));
            BucketCHMatrix batched = new BucketCHMatrix(queryCH);
            batched.prepare(targets, Long.MAX_VALUE, 10000);
            for (int source : targets) {
                BucketCHMatrix.Row row = batched.search(source, 10000);
                for (int target : targets) {
                    assertOracle(query, query.wrapWeighting(weighting), source, target, row, "virtual " + source + "->" + target);
                    BucketCHMatrix singleton = new BucketCHMatrix(queryCH);
                    singleton.prepare(Set.of(target), Long.MAX_VALUE, 10000);
                    assertEquals(row.targets().get(target), singleton.search(source, 10000).targets().get(target));
                }
            }
            int first = snaps.get(0).getClosestNode(), last = snaps.get(2).getClosestNode();
            assertEquals(2 * batched.search(first, 10000).targets().get(last).weight(),
                    batched.search(last, 10000).targets().get(first).weight(), 1e-9);
        }
    }

    @Test
    void twentyRandomDirectedQueryChainsMatchAllIndependentPairs() {
        // Contract from one end of each chain so there are no shortcut witnesses to mask
        // virtual-overlay bugs. Arbitrary shortcut topology is tested separately above;
        // the mandatory engine certificate handles float preparation and split differences.
        DecimalEncodedValue speed = new DecimalEncodedValueImpl("speed", 10, 0.01, true);
        EncodingManager em = EncodingManager.start().add(speed).build();
        for (int seed = 0; seed < 20; seed++) {
            Random random = new Random(2026101950L + seed);
            try (BaseGraph graph = new BaseGraph.Builder(em).create()) {
                for (int node = 0; node < 12; node++)
                    graph.getNodeAccess().setNode(node, 53.47 + node * 0.001, -2.25);
                List<EdgeIteratorState> edges = new ArrayList<>();
                for (int node = 0; node < 11; node++) {
                    double forward = random.nextDouble() < 0.2 ? 0 : 1 + random.nextInt(900) * 0.01;
                    double reverse = random.nextDouble() < 0.3 ? 0 : 1 + random.nextInt(900) * 0.01;
                    if (forward == 0 && reverse == 0) forward = 5;
                    edges.add(graph.edge(node, node + 1).setDistance(100 + random.nextDouble() * 10)
                            .set(speed, forward, reverse));
                }
                Weighting weighting = new SpeedWeighting(speed);
                RoutingCHGraph ch = prepare(graph, weighting, NodeOrderingProvider.identity(12));
                assertEquals(0, ch.getShortcuts());
                EdgeIteratorState split = edges.get(random.nextInt(edges.size()));
                QueryGraph query = QueryGraph.create(graph, List.of(snap(graph, split, 0.2), snap(graph, split, 0.5),
                        snap(graph, split, 0.8), snap(graph, edges.get((split.getEdge() + 1) % edges.size()), 0.35)));
                Set<Integer> targets = new LinkedHashSet<>();
                for (int target = 0; target < query.getNodes(); target++) targets.add(target);
                BucketCHMatrix search = new BucketCHMatrix(new QueryRoutingCHGraph(ch, query));
                search.prepare(targets, Long.MAX_VALUE, 10000);
                for (int source : targets) {
                    BucketCHMatrix.Row row = search.search(source, 10000);
                    for (int target : targets)
                        assertOracle(query, query.wrapWeighting(weighting), source, target, row,
                                "query-seed=" + seed + " " + source + "->" + target);
                }
            }
        }
    }

    @Test
    void floatWitnessCanLoseOriginalObjectiveAndCandidateMustBeCertified() {
        // v=1 is contracted first. Both alternatives round to 10000.0 per leg in the
        // upstream float preparation graph, so the longer route through w=2 witnesses
        // the route through v. Expanded-double query costs cannot repair the missing topology.
        try (Fixture f = new Fixture()) {
            f.arc(0, 1, 10000.0001, 10, 10);
            f.arc(1, 3, 10000.0001, 20, 20);
            f.arc(0, 2, 10000.0002, 100, 100);
            f.arc(2, 3, 10000.0002, 200, 200);
            RoutingCHGraph ch = prepare(f.graph, f.weighting, NodeOrderingProvider.fromArray(1, 0, 2, 3));
            BucketCHMatrix search = new BucketCHMatrix(ch);
            search.prepare(Set.of(3), Long.MAX_VALUE, 1000);
            BucketCHMatrix.Metrics candidate = search.search(0, 1000).targets().get(3);
            Path exact = new Dijkstra(f.graph, f.weighting, TraversalMode.NODE_BASED).calcPath(0, 3);
            assertEquals(20000.0002, exact.getWeight(), 1e-9);
            assertEquals(20000.0004, candidate.weight(), 1e-9);
            assertEquals(300, candidate.timeMillis());
            assertEquals(300, candidate.distance());
            assertEquals(30, exact.getTime());
            assertTrue(candidate.weight() > exact.getWeight());
        }
    }

    @Test
    void equalWeightTiesHaveLegalIndependentMetricsWithoutInventingTime() {
        try (Fixture f = new Fixture()) {
            f.arc(0, 1, 2, 10, 100);
            f.arc(1, 3, 2, 20, 200);
            f.arc(0, 2, 2, 100, 1000);
            f.arc(2, 3, 2, 200, 2000);
            RoutingCHGraph ch = prepare(f.graph, f.weighting, NodeOrderingProvider.identity(4));
            BucketCHMatrix search = new BucketCHMatrix(ch);
            search.prepare(Set.of(3), Long.MAX_VALUE, 1000);
            BucketCHMatrix.Metrics actual = search.search(0, 1000).targets().get(3);
            assertEquals(4, actual.weight());
            assertTrue(actual.equals(new BucketCHMatrix.Metrics(4, 30, 300))
                    || actual.equals(new BucketCHMatrix.Metrics(4, 300, 3000)));
        }
    }

    @Test
    void wholePreparationBudgetDeadlineAndInterruptionThrowWithoutPartialSuccess() {
        try (Fixture f = new Fixture()) {
            f.arc(0, 1, 1, 1, 1);
            f.arc(1, 2, 1, 1, 1);
            RoutingCHGraph ch = prepare(f.graph, f.weighting, NodeOrderingProvider.identity(3));
            assertFailure(SEARCH_LIMIT_EXCEEDED, () -> new BucketCHMatrix(ch).prepare(Set.of(1, 2), Long.MAX_VALUE, 1));
            assertFailure(DEADLINE_EXCEEDED, () -> new BucketCHMatrix(ch).prepare(Set.of(2), System.nanoTime() - 1, 100));
            BucketCHMatrix search = new BucketCHMatrix(ch);
            search.prepare(Set.of(2), Long.MAX_VALUE, 100);
            assertFailure(SEARCH_LIMIT_EXCEEDED, () -> search.search(0, 1));
            Thread.currentThread().interrupt();
            try {
                assertFailure(CANCELLED, () -> new BucketCHMatrix(ch).prepare(Set.of(2), Long.MAX_VALUE, 100));
                assertTrue(Thread.currentThread().isInterrupted());
            } finally {
                Thread.interrupted();
            }
        }
    }

    @Test
    void metricCacheIsBoundedEvenBeforeAHighDegreeSearchSettlesNeighbors() {
        try (Fixture f = new Fixture()) {
            for (int node = 1; node <= 10; node++) f.arc(node, 0, 1, 1, 1);
            RoutingCHGraph ch = prepare(f.graph, f.weighting, NodeOrderingProvider.identity(11));
            BucketCHMatrix search = new BucketCHMatrix(ch);
            TargetSetDijkstra.SearchException failure = assertThrows(TargetSetDijkstra.SearchException.class,
                    () -> search.prepare(Set.of(0), Long.MAX_VALUE, 3));
            assertEquals(SEARCH_LIMIT_EXCEEDED, failure.reason());
            assertTrue(failure.getMessage().contains("cache entry limit"));
            assertEquals(3, search.statistics().metricCacheEntries());
            assertEquals(1, search.statistics().visitedStates());
            assertThrows(IllegalStateException.class, () -> search.search(1, 100));
        }
    }

    private static void randomEdge(BaseGraph graph, DecimalEncodedValue speed, Random random, int from, int to, Set<Long> pairs) {
        if (from == to) return;
        long key = ((long) Math.min(from, to) << 32) | Math.max(from, to);
        if (!pairs.add(key)) return;
        double forward = random.nextDouble() < 0.25 ? 0 : 1 + random.nextInt(900) * 0.01;
        double reverse = random.nextDouble() < 0.3 ? 0 : 1 + random.nextInt(900) * 0.01;
        if (forward == 0 && reverse == 0) forward = 5;
        graph.edge(from, to).setDistance(10 + random.nextDouble() * 990).set(speed, forward, reverse);
    }

    private static Snap snap(BaseGraph graph, EdgeIteratorState edge, double fraction) {
        double lat = graph.getNodeAccess().getLat(edge.getBaseNode()) * (1 - fraction) + graph.getNodeAccess().getLat(edge.getAdjNode()) * fraction;
        double lon = graph.getNodeAccess().getLon(edge.getBaseNode()) * (1 - fraction) + graph.getNodeAccess().getLon(edge.getAdjNode()) * fraction;
        Snap snap = new Snap(lat, lon);
        snap.setClosestEdge(edge);
        snap.setWayIndex(0);
        snap.setSnappedPosition(Snap.Position.EDGE);
        snap.calcSnappedPoint(DistanceCalcEarth.DIST_EARTH);
        return snap;
    }

    private static RoutingCHGraph prepare(BaseGraph graph, Weighting weighting, NodeOrderingProvider ordering) {
        graph.freeze();
        PrepareContractionHierarchies preparation = PrepareContractionHierarchies.fromGraph(graph, CHConfig.nodeBased("test", weighting));
        if (ordering != null) preparation.useFixedNodeOrdering(ordering);
        PrepareContractionHierarchies.Result result = preparation.doWork();
        return RoutingCHGraphImpl.fromGraph(graph, result.getCHStorage(), result.getCHConfig());
    }

    private static void assertOracle(Graph graph, Weighting weighting, int source, int target, BucketCHMatrix.Row row, String context) {
        Path expected = new Dijkstra(graph, weighting, TraversalMode.NODE_BASED).calcPath(source, target);
        BucketCHMatrix.Metrics actual = row.targets().get(target);
        assertEquals(expected.isFound(), actual != null, context);
        if (actual == null) return;
        assertEquals(expected.getWeight(), actual.weight(), 1e-7, context);
        assertEquals(expected.getTime(), actual.timeMillis(), context);
        assertEquals(expected.getDistance(), actual.distance(), 1e-7, context);
    }

    private static void assertFailure(TargetSetDijkstra.Failure reason, org.junit.jupiter.api.function.Executable work) {
        assertEquals(reason, assertThrows(TargetSetDijkstra.SearchException.class, work).reason());
    }

    private record Cost(double weight, long time) { }
    private static final class Fixture implements AutoCloseable {
        final DecimalEncodedValue speed = new DecimalEncodedValueImpl("speed", 5, 1, true);
        final EncodingManager em = EncodingManager.start().add(speed).build();
        final BaseGraph graph = new BaseGraph.Builder(em).create();
        final Map<Integer, Cost> costs = new HashMap<>();
        final Weighting weighting = new Weighting() {
            public double calcMinWeightPerDistance() { return 0; }
            public double calcEdgeWeight(EdgeIteratorState edge, boolean reverse) {
                Cost cost = costs.get(reverse ? edge.getReverseEdgeKey() : edge.getEdgeKey());
                return cost == null ? Double.POSITIVE_INFINITY : cost.weight();
            }
            public long calcEdgeMillis(EdgeIteratorState edge, boolean reverse) {
                Cost cost = costs.get(reverse ? edge.getReverseEdgeKey() : edge.getEdgeKey());
                return cost == null ? Long.MAX_VALUE : cost.time();
            }
            public double calcTurnWeight(int in, int via, int out) { return 0; }
            public long calcTurnMillis(int in, int via, int out) { return 0; }
            public boolean hasTurnCosts() { return false; }
            public String getName() { return "independent_ch_metrics"; }
        };
        void arc(int from, int to, double weight, long time, double distance) {
            EdgeIteratorState edge = graph.edge(from, to).setDistance(distance);
            costs.put(edge.getEdgeKey(), new Cost(weight, time));
        }
        public void close() { graph.close(); }
    }
}
