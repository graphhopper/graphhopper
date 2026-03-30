package pl.cezarysanecki.solver.core;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import pl.cezarysanecki.solver.api.CostFunction;
import pl.cezarysanecki.solver.api.DoubleAlgebra;
import pl.cezarysanecki.solver.api.Edge;
import pl.cezarysanecki.solver.api.Graph;
import pl.cezarysanecki.solver.api.Heuristic;
import pl.cezarysanecki.solver.api.Path;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Comparative test (property-based) — generates a random undirected graph,
 * runs all 3 algorithms (Dijkstra, A*, Bidirectional Dijkstra)
 * and verifies that they return the same optimal weight.
 * <p>
 * A* heuristic = zero (admissible, degenerates to Dijkstra).
 * Bidirectional: forwardGraph == backwardGraph (undirected).
 */
class AlgorithmComparisonTest {

    private static final DoubleAlgebra ALGEBRA = DoubleAlgebra.INSTANCE;
    private static final CostFunction<Integer, Double, Double> COST = Edge::data;
    private static final Heuristic<Integer, Double> ZERO_HEURISTIC = (from, to) -> 0.0;

    // -----------------------------------------------------------
    //  Random undirected graph: V nodes (0..V-1),
    //  each pair (i,j) has an edge with probability edgeProb,
    //  random weight in range [1.0, maxWeight].
    // -----------------------------------------------------------
    static Graph<Integer, Double> randomUndirectedGraph(
            int nodeCount, double edgeProb, double maxWeight, Random rng
    ) {
        Map<Integer, List<Edge<Integer, Double>>> adjacency = new HashMap<>();
        for (int i = 0; i < nodeCount; i++) {
            adjacency.put(i, new ArrayList<>());
        }
        for (int i = 0; i < nodeCount; i++) {
            for (int j = i + 1; j < nodeCount; j++) {
                if (rng.nextDouble() < edgeProb) {
                    double weight = 1.0 + rng.nextDouble() * (maxWeight - 1.0);
                    adjacency.get(i).add(new Edge<>(i, j, weight));
                    adjacency.get(j).add(new Edge<>(j, i, weight));
                }
            }
        }
        return new Graph<>() {
            @Override
            public boolean containsNode(Integer node) {
                return adjacency.containsKey(node);
            }

            @Override
            public Iterable<Edge<Integer, Double>> neighbors(Integer node) {
                return adjacency.getOrDefault(node, List.of());
            }
        };
    }

    // -----------------------------------------------------------
    //  Repeated test: generate a random graph, run 3 algorithms,
    //  compare totalWeight.
    //
    //  @RepeatedTest(50) — 50 random graphs, enough for confidence.
    // -----------------------------------------------------------
    @RepeatedTest(50)
    void allThreeAlgorithmsShouldAgreeTotalWeight() {
        Random rng = new Random(); // new seed each time
        int nodeCount = 10 + rng.nextInt(21); // 10..30 nodes
        double edgeProb = 0.3 + rng.nextDouble() * 0.4; // 0.3..0.7
        double maxWeight = 10.0;

        Graph<Integer, Double> graph = randomUndirectedGraph(nodeCount, edgeProb, maxWeight, rng);

        int source = 0;
        int target = nodeCount - 1;

        // Dijkstra
        var dijkstra = new Dijkstra<>(graph, COST, ALGEBRA);
        Path<Integer, Double, Double> dp = dijkstra.solve(source, target);

        // A* (zero heuristic)
        var astar = new AStar<>(graph, COST, ALGEBRA, ZERO_HEURISTIC);
        Path<Integer, Double, Double> ap = astar.solve(source, target);

        // Bidirectional Dijkstra (undirected → same graph for forward/backward)
        var bidir = new BidirectionalDijkstra<>(graph, graph, COST, ALGEBRA);
        Path<Integer, Double, Double> bp = bidir.solve(source, target);

        // All 3 should agree on isFound
        assertEquals(dp.isFound(), ap.isFound(),
                "Dijkstra vs A*: isFound mismatch");
        assertEquals(dp.isFound(), bp.isFound(),
                "Dijkstra vs Bidir: isFound mismatch");

        if (dp.isFound()) {
            // Compare weights with floating point tolerance
            assertEquals(dp.totalWeight(), ap.totalWeight(), 1e-9,
                    "Dijkstra vs A*: totalWeight mismatch");
            assertEquals(dp.totalWeight(), bp.totalWeight(), 1e-9,
                    "Dijkstra vs Bidir: totalWeight mismatch");

            // Paths should have a valid structure
            assertTrue(dp.nodes().size() >= 2);
            assertTrue(ap.nodes().size() >= 2);
            assertTrue(bp.nodes().size() >= 2);

            assertEquals(source, dp.nodes().get(0));
            assertEquals(target, dp.nodes().get(dp.nodes().size() - 1));
            assertEquals(source, bp.nodes().get(0));
            assertEquals(target, bp.nodes().get(bp.nodes().size() - 1));
        }
    }

    // -----------------------------------------------------------
    //  Test: dense graph (nearly complete) — verification on a larger instance
    // -----------------------------------------------------------
    @Test
    void shouldAgreeOnDenseGraph() {
        Random rng = new Random(42); // deterministic seed
        Graph<Integer, Double> graph = randomUndirectedGraph(50, 0.8, 20.0, rng);

        var dijkstra = new Dijkstra<>(graph, COST, ALGEBRA);
        var astar = new AStar<>(graph, COST, ALGEBRA, ZERO_HEURISTIC);
        var bidir = new BidirectionalDijkstra<>(graph, graph, COST, ALGEBRA);

        // Test multiple source-target pairs
        int[] sources = {0, 5, 10, 20};
        int[] targets = {49, 40, 30, 25};

        for (int i = 0; i < sources.length; i++) {
            Path<Integer, Double, Double> dp = dijkstra.solve(sources[i], targets[i]);
            Path<Integer, Double, Double> ap = astar.solve(sources[i], targets[i]);
            Path<Integer, Double, Double> bp = bidir.solve(sources[i], targets[i]);

            assertEquals(dp.isFound(), ap.isFound());
            assertEquals(dp.isFound(), bp.isFound());

            if (dp.isFound()) {
                assertEquals(dp.totalWeight(), ap.totalWeight(), 1e-9,
                        "Mismatch for " + sources[i] + " → " + targets[i]);
                assertEquals(dp.totalWeight(), bp.totalWeight(), 1e-9,
                        "Mismatch for " + sources[i] + " → " + targets[i]);
            }
        }
    }

    // -----------------------------------------------------------
    //  Test: sparse graph — unreachable pairs may exist
    // -----------------------------------------------------------
    @Test
    void shouldAgreeOnSparseGraph() {
        Random rng = new Random(123);
        Graph<Integer, Double> graph = randomUndirectedGraph(30, 0.15, 10.0, rng);

        var dijkstra = new Dijkstra<>(graph, COST, ALGEBRA);
        var astar = new AStar<>(graph, COST, ALGEBRA, ZERO_HEURISTIC);
        var bidir = new BidirectionalDijkstra<>(graph, graph, COST, ALGEBRA);

        for (int s = 0; s < 30; s += 5) {
            for (int t = 0; t < 30; t += 7) {
                if (s == t) continue;

                Path<Integer, Double, Double> dp = dijkstra.solve(s, t);
                Path<Integer, Double, Double> ap = astar.solve(s, t);
                Path<Integer, Double, Double> bp = bidir.solve(s, t);

                assertEquals(dp.isFound(), ap.isFound(),
                        "isFound mismatch for " + s + " → " + t);
                assertEquals(dp.isFound(), bp.isFound(),
                        "isFound mismatch for " + s + " → " + t);

                if (dp.isFound()) {
                    assertEquals(dp.totalWeight(), ap.totalWeight(), 1e-9,
                            "Weight mismatch for " + s + " → " + t);
                    assertEquals(dp.totalWeight(), bp.totalWeight(), 1e-9,
                            "Weight mismatch for " + s + " → " + t);
                }
            }
        }
    }
}
