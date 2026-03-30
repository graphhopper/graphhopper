package pl.cezarysanecki.solver.core;

import org.junit.jupiter.api.Test;
import pl.cezarysanecki.solver.api.DoubleAlgebra;
import pl.cezarysanecki.solver.api.Edge;
import pl.cezarysanecki.solver.api.Graph;
import pl.cezarysanecki.solver.api.IntAlgebra;
import pl.cezarysanecki.solver.api.Path;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DijkstraTest {

    // --- Helper: simple directed graph from adjacency list ---

    static <N> Graph<N, Double> graphOf(Map<N, List<Edge<N, Double>>> adjacency) {
        return new Graph<>() {
            @Override
            public boolean containsNode(N node) {
                return adjacency.containsKey(node);
            }

            @Override
            public Iterable<Edge<N, Double>> neighbors(N node) {
                return adjacency.getOrDefault(node, List.of());
            }
        };
    }

    static Edge<String, Double> edge(String from, String to, double weight) {
        return new Edge<>(from, to, weight);
    }

    // -----------------------------------------------------------
    //  Test: linear graph A → B → C → D
    //
    //    A --1.0--> B --2.0--> C --3.0--> D
    // -----------------------------------------------------------
    @Test
    void shouldFindShortestPathInLinearGraph() {
        var adjacency = Map.<String, List<Edge<String, Double>>>of(
                "A", List.of(edge("A", "B", 1.0)),
                "B", List.of(edge("B", "C", 2.0)),
                "C", List.of(edge("C", "D", 3.0)),
                "D", List.of()
        );
        var graph = graphOf(adjacency);
        var dijkstra = new Dijkstra<>(graph, e -> e.data(), DoubleAlgebra.INSTANCE);

        Path<String, Double, Double> path = dijkstra.solve("A", "D");

        assertTrue(path.isFound());
        assertEquals(List.of("A", "B", "C", "D"), path.nodes());
        assertEquals(6.0, path.totalWeight());
        assertEquals(3, path.edges().size());
        assertTrue(dijkstra.getVisitedNodes() > 0);
    }

    // -----------------------------------------------------------
    //  Test: graph with multiple paths — optimality verification
    //
    //    A --10--> B --1--> D
    //    |                  ^
    //    +--1--> C --1------+
    //
    //  Shortest: A → C → D (cost 2), not A → B → D (cost 11)
    // -----------------------------------------------------------
    @Test
    void shouldFindOptimalPathAmongMultiplePaths() {
        var adjacency = Map.<String, List<Edge<String, Double>>>of(
                "A", List.of(edge("A", "B", 10.0), edge("A", "C", 1.0)),
                "B", List.of(edge("B", "D", 1.0)),
                "C", List.of(edge("C", "D", 1.0)),
                "D", List.of()
        );
        var graph = graphOf(adjacency);
        var dijkstra = new Dijkstra<>(graph, e -> e.data(), DoubleAlgebra.INSTANCE);

        Path<String, Double, Double> path = dijkstra.solve("A", "D");

        assertTrue(path.isFound());
        assertEquals(List.of("A", "C", "D"), path.nodes());
        assertEquals(2.0, path.totalWeight());
    }

    // -----------------------------------------------------------
    //  Test: unreachable target
    //
    //    A --1--> B     C (disconnected)
    // -----------------------------------------------------------
    @Test
    void shouldReturnNotFoundForUnreachableTarget() {
        var adjacency = Map.<String, List<Edge<String, Double>>>of(
                "A", List.of(edge("A", "B", 1.0)),
                "B", List.of(),
                "C", List.of()
        );
        var graph = graphOf(adjacency);
        var dijkstra = new Dijkstra<>(graph, e -> e.data(), DoubleAlgebra.INSTANCE);

        Path<String, Double, Double> path = dijkstra.solve("A", "C");

        assertFalse(path.isFound());
        assertEquals(Double.POSITIVE_INFINITY, path.totalWeight());
        assertTrue(path.nodes().isEmpty());
        assertTrue(path.edges().isEmpty());
    }

    // -----------------------------------------------------------
    //  Test: source == target → zero-length path
    // -----------------------------------------------------------
    @Test
    void shouldReturnZeroCostPathWhenSourceEqualsTarget() {
        var adjacency = Map.<String, List<Edge<String, Double>>>of(
                "A", List.of(edge("A", "B", 1.0)),
                "B", List.of()
        );
        var graph = graphOf(adjacency);
        var dijkstra = new Dijkstra<>(graph, e -> e.data(), DoubleAlgebra.INSTANCE);

        Path<String, Double, Double> path = dijkstra.solve("A", "A");

        assertTrue(path.isFound());
        assertEquals(List.of("A"), path.nodes());
        assertTrue(path.edges().isEmpty());
        assertEquals(0.0, path.totalWeight());
    }

    // -----------------------------------------------------------
    //  Test: empty graph → node does not exist
    // -----------------------------------------------------------
    @Test
    void shouldThrowForNodeNotInGraph() {
        var adjacency = Map.<String, List<Edge<String, Double>>>of();
        var graph = graphOf(adjacency);
        var dijkstra = new Dijkstra<>(graph, e -> e.data(), DoubleAlgebra.INSTANCE);

        assertThrows(IllegalArgumentException.class, () -> dijkstra.solve("A", "B"));
    }

    // -----------------------------------------------------------
    //  Test: diamond graph — verify that Dijkstra explores correctly
    //
    //        B
    //       / \
    //    A      D
    //       \ /
    //        C
    //
    //  A→B=1, A→C=4, B→D=6, C→D=1
    //  Shortest: A→C→D = 5 (not A→B→D = 7)
    // -----------------------------------------------------------
    @Test
    void shouldFindShortestPathInDiamondGraph() {
        var adjacency = Map.<String, List<Edge<String, Double>>>of(
                "A", List.of(edge("A", "B", 1.0), edge("A", "C", 4.0)),
                "B", List.of(edge("B", "D", 6.0)),
                "C", List.of(edge("C", "D", 1.0)),
                "D", List.of()
        );
        var graph = graphOf(adjacency);
        var dijkstra = new Dijkstra<>(graph, e -> e.data(), DoubleAlgebra.INSTANCE);

        Path<String, Double, Double> path = dijkstra.solve("A", "D");

        assertTrue(path.isFound());
        assertEquals(List.of("A", "C", "D"), path.nodes());
        assertEquals(5.0, path.totalWeight());
    }

    // -----------------------------------------------------------
    //  Test: Integer weights with IntAlgebra
    //
    //    A --3--> B --2--> C
    // -----------------------------------------------------------
    @Test
    void shouldWorkWithIntegerWeights() {
        var adjacency = Map.<String, List<Edge<String, Integer>>>of(
                "A", List.of(new Edge<>("A", "B", 3)),
                "B", List.of(new Edge<>("B", "C", 2)),
                "C", List.of()
        );
        Graph<String, Integer> graph = new Graph<>() {
            @Override
            public boolean containsNode(String node) {
                return adjacency.containsKey(node);
            }

            @Override
            public Iterable<Edge<String, Integer>> neighbors(String node) {
                return adjacency.getOrDefault(node, List.of());
            }
        };
        var dijkstra = new Dijkstra<>(graph, e -> e.data(), IntAlgebra.INSTANCE);

        Path<String, Integer, Integer> path = dijkstra.solve("A", "C");

        assertTrue(path.isFound());
        assertEquals(List.of("A", "B", "C"), path.nodes());
        assertEquals(5, path.totalWeight());
    }

    // -----------------------------------------------------------
    //  Test: graph with cycle — Dijkstra does not enter an infinite loop
    //
    //    A --1--> B --1--> C
    //    ^                 |
    //    +-------100-------+
    //
    //  A→C = 2 (forward), nie 100 (back edge C→A irrelevant for A→C)
    // -----------------------------------------------------------
    @Test
    void shouldHandleGraphWithCycle() {
        var adjacency = Map.<String, List<Edge<String, Double>>>of(
                "A", List.of(edge("A", "B", 1.0)),
                "B", List.of(edge("B", "C", 1.0)),
                "C", List.of(edge("C", "A", 100.0))
        );
        var graph = graphOf(adjacency);
        var dijkstra = new Dijkstra<>(graph, e -> e.data(), DoubleAlgebra.INSTANCE);

        Path<String, Double, Double> path = dijkstra.solve("A", "C");

        assertTrue(path.isFound());
        assertEquals(List.of("A", "B", "C"), path.nodes());
        assertEquals(2.0, path.totalWeight());
    }

    // -----------------------------------------------------------
    //  Test: CostFunction interprets edge data in a custom way
    //  edge data = (distance, tollCost), cost = distance + 2*tollCost
    // -----------------------------------------------------------
    @Test
    void shouldUseCostFunctionToInterpretEdgeData() {
        record Road(double distance, double tollCost) {}

        var ab = new Edge<>("A", "B", new Road(5.0, 0.0));  // cost = 5 + 0 = 5
        var bc = new Edge<>("B", "C", new Road(1.0, 10.0)); // cost = 1 + 20 = 21
        var ac = new Edge<>("A", "C", new Road(20.0, 0.0)); // cost = 20 + 0 = 20

        var adjacency = new HashMap<String, List<Edge<String, Road>>>();
        adjacency.put("A", List.of(ab, ac));
        adjacency.put("B", List.of(bc));
        adjacency.put("C", List.of());

        Graph<String, Road> graph = new Graph<>() {
            @Override
            public boolean containsNode(String node) {
                return adjacency.containsKey(node);
            }

            @Override
            public Iterable<Edge<String, Road>> neighbors(String node) {
                return adjacency.getOrDefault(node, List.of());
            }
        };

        // cost = distance + 2 * tollCost
        var dijkstra = new Dijkstra<>(graph,
                e -> e.data().distance() + 2 * e.data().tollCost(),
                DoubleAlgebra.INSTANCE);

        Path<String, Road, Double> path = dijkstra.solve("A", "C");

        assertTrue(path.isFound());
        // A→C direct (cost=20) is cheaper than A→B→C (cost=5+21=26)
        assertEquals(List.of("A", "C"), path.nodes());
        assertEquals(20.0, path.totalWeight());
    }
}
