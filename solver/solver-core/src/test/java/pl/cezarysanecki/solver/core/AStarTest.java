package pl.cezarysanecki.solver.core;

import org.junit.jupiter.api.Test;
import pl.cezarysanecki.solver.api.DoubleAlgebra;
import pl.cezarysanecki.solver.api.Edge;
import pl.cezarysanecki.solver.api.Graph;
import pl.cezarysanecki.solver.api.Heuristic;
import pl.cezarysanecki.solver.api.Path;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AStarTest {

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

    /** Zero heuristic — admissible, reduces A* to Dijkstra. */
    static final Heuristic<String, Double> ZERO_HEURISTIC = (from, to) -> 0.0;

    // -----------------------------------------------------------
    //  Test: linear graph A → B → C → D (zero heuristic)
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
        var astar = new AStar<>(graph, e -> e.data(), DoubleAlgebra.INSTANCE, ZERO_HEURISTIC);

        Path<String, Double, Double> path = astar.solve("A", "D");

        assertTrue(path.isFound());
        assertEquals(List.of("A", "B", "C", "D"), path.nodes());
        assertEquals(6.0, path.totalWeight());
        assertEquals(3, path.edges().size());
        assertTrue(astar.getVisitedNodes() > 0);
    }

    // -----------------------------------------------------------
    //  Test: graph with multiple paths — optimality with zero heuristic
    //
    //    A --10--> B --1--> D
    //    |                  ^
    //    +--1--> C --1------+
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
        var astar = new AStar<>(graph, e -> e.data(), DoubleAlgebra.INSTANCE, ZERO_HEURISTIC);

        Path<String, Double, Double> path = astar.solve("A", "D");

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
        var astar = new AStar<>(graph, e -> e.data(), DoubleAlgebra.INSTANCE, ZERO_HEURISTIC);

        Path<String, Double, Double> path = astar.solve("A", "C");

        assertFalse(path.isFound());
        assertEquals(Double.POSITIVE_INFINITY, path.totalWeight());
        assertTrue(path.nodes().isEmpty());
        assertTrue(path.edges().isEmpty());
    }

    // -----------------------------------------------------------
    //  Test: source == target → zero-cost path
    // -----------------------------------------------------------
    @Test
    void shouldReturnZeroCostPathWhenSourceEqualsTarget() {
        var adjacency = Map.<String, List<Edge<String, Double>>>of(
                "A", List.of(edge("A", "B", 1.0)),
                "B", List.of()
        );
        var graph = graphOf(adjacency);
        var astar = new AStar<>(graph, e -> e.data(), DoubleAlgebra.INSTANCE, ZERO_HEURISTIC);

        Path<String, Double, Double> path = astar.solve("A", "A");

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
        var astar = new AStar<>(graph, e -> e.data(), DoubleAlgebra.INSTANCE, ZERO_HEURISTIC);

        assertThrows(IllegalArgumentException.class, () -> astar.solve("A", "B"));
    }

    // -----------------------------------------------------------
    //  Test: diamond graph with zero heuristic
    //
    //        B
    //       / \
    //    A      D
    //       \ /
    //        C
    //
    //  A→B=1, A→C=4, B→D=6, C→D=1
    //  Shortest: A→C→D = 5
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
        var astar = new AStar<>(graph, e -> e.data(), DoubleAlgebra.INSTANCE, ZERO_HEURISTIC);

        Path<String, Double, Double> path = astar.solve("A", "D");

        assertTrue(path.isFound());
        assertEquals(List.of("A", "C", "D"), path.nodes());
        assertEquals(5.0, path.totalWeight());
    }

    // -----------------------------------------------------------
    //  Test: graph with cycle — A* does not enter an infinite loop
    //
    //    A --1--> B --1--> C
    //    ^                 |
    //    +-------100-------+
    // -----------------------------------------------------------
    @Test
    void shouldHandleGraphWithCycle() {
        var adjacency = Map.<String, List<Edge<String, Double>>>of(
                "A", List.of(edge("A", "B", 1.0)),
                "B", List.of(edge("B", "C", 1.0)),
                "C", List.of(edge("C", "A", 100.0))
        );
        var graph = graphOf(adjacency);
        var astar = new AStar<>(graph, e -> e.data(), DoubleAlgebra.INSTANCE, ZERO_HEURISTIC);

        Path<String, Double, Double> path = astar.solve("A", "C");

        assertTrue(path.isFound());
        assertEquals(List.of("A", "B", "C"), path.nodes());
        assertEquals(2.0, path.totalWeight());
    }

    // -----------------------------------------------------------
    //  Test: A* with a good heuristic visits fewer nodes than Dijkstra
    //
    //  Grid-like graph:
    //
    //    S --1--> A --1--> B --1--> T
    //    |                          ^
    //    +--1--> X --1--> Y --1----+
    //
    //  Heuristic: "hops to target" distance (admissible)
    //  S=3, A=2, B=1, T=0, X=2, Y=1
    //
    //  Dijkstra explores both directions, A* with the heuristic prefers
    //  the shorter path to T.
    // -----------------------------------------------------------
    @Test
    void shouldVisitFewerNodesWithGoodHeuristic() {
        var adjacency = Map.<String, List<Edge<String, Double>>>of(
                "S", List.of(edge("S", "A", 1.0), edge("S", "X", 1.0)),
                "A", List.of(edge("A", "B", 1.0)),
                "B", List.of(edge("B", "T", 1.0)),
                "X", List.of(edge("X", "Y", 1.0)),
                "Y", List.of(edge("Y", "T", 1.0)),
                "T", List.of()
        );
        var graph = graphOf(adjacency);

        // Run Dijkstra (baseline)
        var dijkstra = new Dijkstra<>(graph, e -> e.data(), DoubleAlgebra.INSTANCE);
        Path<String, Double, Double> dijkstraPath = dijkstra.solve("S", "T");

        // Run A* with admissible heuristic that guides toward S→A→B→T path
        // Heuristic: minimum hops to T (admissible since each edge costs >= 1.0)
        Map<String, Double> hopEstimate = Map.of(
                "S", 3.0, "A", 2.0, "B", 1.0, "T", 0.0,
                "X", 2.0, "Y", 1.0
        );
        Heuristic<String, Double> heuristic = (from, to) -> hopEstimate.getOrDefault(from, 0.0);

        var astar = new AStar<>(graph, e -> e.data(), DoubleAlgebra.INSTANCE, heuristic);
        Path<String, Double, Double> astarPath = astar.solve("S", "T");

        // Both should find optimal path with same weight
        assertTrue(dijkstraPath.isFound());
        assertTrue(astarPath.isFound());
        assertEquals(dijkstraPath.totalWeight(), astarPath.totalWeight());

        // A* should visit fewer or equal nodes
        assertTrue(astar.getVisitedNodes() <= dijkstra.getVisitedNodes(),
                "A* visited " + astar.getVisitedNodes() +
                        " nodes but Dijkstra visited " + dijkstra.getVisitedNodes());
    }

    // -----------------------------------------------------------
    //  Test: A* with zero heuristic produces the same result as Dijkstra
    // -----------------------------------------------------------
    @Test
    void shouldProduceSameResultAsDijkstraWithZeroHeuristic() {
        var adjacency = Map.<String, List<Edge<String, Double>>>of(
                "A", List.of(edge("A", "B", 1.0), edge("A", "C", 4.0)),
                "B", List.of(edge("B", "D", 6.0)),
                "C", List.of(edge("C", "D", 1.0)),
                "D", List.of()
        );
        var graph = graphOf(adjacency);

        var dijkstra = new Dijkstra<>(graph, e -> e.data(), DoubleAlgebra.INSTANCE);
        var astar = new AStar<>(graph, e -> e.data(), DoubleAlgebra.INSTANCE, ZERO_HEURISTIC);

        Path<String, Double, Double> dp = dijkstra.solve("A", "D");
        Path<String, Double, Double> ap = astar.solve("A", "D");

        assertEquals(dp.totalWeight(), ap.totalWeight());
        assertEquals(dp.nodes(), ap.nodes());
        assertEquals(dp.edges().size(), ap.edges().size());
    }

    // -----------------------------------------------------------
    //  Test: null source/target/heuristic → NullPointerException
    // -----------------------------------------------------------
    @Test
    void shouldThrowNullPointerForNullArguments() {
        var adjacency = Map.<String, List<Edge<String, Double>>>of(
                "A", List.of()
        );
        var graph = graphOf(adjacency);
        var astar = new AStar<>(graph, e -> e.data(), DoubleAlgebra.INSTANCE, ZERO_HEURISTIC);

        assertThrows(NullPointerException.class, () -> astar.solve(null, "A"));
        assertThrows(NullPointerException.class, () -> astar.solve("A", null));
        assertThrows(NullPointerException.class, () ->
                new AStar<>(graph, e -> e.data(), DoubleAlgebra.INSTANCE, null));
    }
}
