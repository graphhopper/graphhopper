package pl.cezarysanecki.solver.core;

import org.junit.jupiter.api.Test;
import pl.cezarysanecki.solver.api.DoubleAlgebra;
import pl.cezarysanecki.solver.api.Edge;
import pl.cezarysanecki.solver.api.Graph;
import pl.cezarysanecki.solver.api.Path;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BidirectionalDijkstraTest {

    // --- Helper: directed graph from adjacency list ---

    static <N> Graph<N, Double> graphOf(Map<N, List<Edge<N, Double>>> adjacency) {
        return new Graph<>() {
            @Override
            public Set<N> nodes() {
                return adjacency.keySet();
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

    /**
     * Builds an undirected graph — each edge exists in both directions.
     * For BidirectionalDijkstra on an undirected graph: forwardGraph == backwardGraph.
     */
    static Graph<String, Double> undirectedGraphOf(List<Edge<String, Double>> edges) {
        Map<String, List<Edge<String, Double>>> adjacency = new HashMap<>();
        for (Edge<String, Double> e : edges) {
            adjacency.computeIfAbsent(e.source(), k -> new ArrayList<>()).add(e);
            adjacency.computeIfAbsent(e.target(), k -> new ArrayList<>())
                    .add(e.reversed());
        }
        return graphOf(adjacency);
    }

    /**
     * Builds a reversed graph from a directed graph (reverses all edges).
     */
    static Graph<String, Double> reversedGraphOf(Map<String, List<Edge<String, Double>>> adjacency) {
        Map<String, List<Edge<String, Double>>> reversed = new HashMap<>();
        // First add all nodes (even without edges)
        for (String node : adjacency.keySet()) {
            reversed.putIfAbsent(node, new ArrayList<>());
        }
        // Reverse edges
        for (List<Edge<String, Double>> edgeList : adjacency.values()) {
            for (Edge<String, Double> e : edgeList) {
                reversed.computeIfAbsent(e.target(), k -> new ArrayList<>())
                        .add(e.reversed());
            }
        }
        return graphOf(reversed);
    }

    // -----------------------------------------------------------
    //  Test: undirected linear graph A - B - C - D
    //
    //    A --1-- B --2-- C --3-- D
    //
    //  forwardGraph == backwardGraph (undirected)
    // -----------------------------------------------------------
    @Test
    void shouldFindShortestPathInUndirectedLinearGraph() {
        var graph = undirectedGraphOf(List.of(
                edge("A", "B", 1.0),
                edge("B", "C", 2.0),
                edge("C", "D", 3.0)
        ));
        var bidir = new BidirectionalDijkstra<>(graph, graph, e -> e.data(), DoubleAlgebra.INSTANCE);

        Path<String, Double, Double> path = bidir.solve("A", "D");

        assertTrue(path.isFound());
        assertEquals(List.of("A", "B", "C", "D"), path.nodes());
        assertEquals(6.0, path.totalWeight());
        assertEquals(3, path.edges().size());
        assertTrue(bidir.getVisitedNodes() > 0);
    }

    // -----------------------------------------------------------
    //  Test: undirected graph with multiple paths — optimality
    //
    //    A --10-- B --1-- D
    //    |                |
    //    +---1--- C --1---+
    //
    //  Shortest: A → C → D (cost 2)
    // -----------------------------------------------------------
    @Test
    void shouldFindOptimalPathInUndirectedGraph() {
        var graph = undirectedGraphOf(List.of(
                edge("A", "B", 10.0),
                edge("A", "C", 1.0),
                edge("B", "D", 1.0),
                edge("C", "D", 1.0)
        ));
        var bidir = new BidirectionalDijkstra<>(graph, graph, e -> e.data(), DoubleAlgebra.INSTANCE);

        Path<String, Double, Double> path = bidir.solve("A", "D");

        assertTrue(path.isFound());
        assertEquals(2.0, path.totalWeight());
        assertEquals(List.of("A", "C", "D"), path.nodes());
    }

    // -----------------------------------------------------------
    //  Test: directed graph with reversed graph
    //
    //    A --1--> B --2--> C --3--> D
    //
    //  backwardGraph has reversed edges: D→C, C→B, B→A
    // -----------------------------------------------------------
    @Test
    void shouldFindPathInDirectedGraphWithReversedBackward() {
        var adjacency = Map.<String, List<Edge<String, Double>>>of(
                "A", List.of(edge("A", "B", 1.0)),
                "B", List.of(edge("B", "C", 2.0)),
                "C", List.of(edge("C", "D", 3.0)),
                "D", List.of()
        );
        var forward = graphOf(adjacency);
        var backward = reversedGraphOf(adjacency);

        var bidir = new BidirectionalDijkstra<>(forward, backward, e -> e.data(), DoubleAlgebra.INSTANCE);

        Path<String, Double, Double> path = bidir.solve("A", "D");

        assertTrue(path.isFound());
        assertEquals(6.0, path.totalWeight());
        assertEquals(List.of("A", "B", "C", "D"), path.nodes());
    }

    // -----------------------------------------------------------
    //  Test: unreachable target
    //
    //    A --1-- B     C (disconnected)
    // -----------------------------------------------------------
    @Test
    void shouldReturnNotFoundForUnreachableTarget() {
        var graph = undirectedGraphOf(List.of(
                edge("A", "B", 1.0)
        ));
        // Add C as isolated node
        Map<String, List<Edge<String, Double>>> adj = new HashMap<>();
        adj.put("A", List.of(edge("A", "B", 1.0)));
        adj.put("B", List.of(edge("B", "A", 1.0)));
        adj.put("C", List.of());
        var graphWithC = graphOf(adj);

        var bidir = new BidirectionalDijkstra<>(graphWithC, graphWithC,
                e -> e.data(), DoubleAlgebra.INSTANCE);

        Path<String, Double, Double> path = bidir.solve("A", "C");

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
        var graph = undirectedGraphOf(List.of(
                edge("A", "B", 1.0)
        ));
        var bidir = new BidirectionalDijkstra<>(graph, graph, e -> e.data(), DoubleAlgebra.INSTANCE);

        Path<String, Double, Double> path = bidir.solve("A", "A");

        assertTrue(path.isFound());
        assertEquals(List.of("A"), path.nodes());
        assertTrue(path.edges().isEmpty());
        assertEquals(0.0, path.totalWeight());
    }

    // -----------------------------------------------------------
    //  Test: node does not exist in the graph
    // -----------------------------------------------------------
    @Test
    void shouldThrowForNodeNotInGraph() {
        var adjacency = Map.<String, List<Edge<String, Double>>>of();
        var graph = graphOf(adjacency);
        var bidir = new BidirectionalDijkstra<>(graph, graph, e -> e.data(), DoubleAlgebra.INSTANCE);

        assertThrows(IllegalArgumentException.class, () -> bidir.solve("A", "B"));
    }

    // -----------------------------------------------------------
    //  Test: undirected diamond graph
    //
    //        B
    //       / \
    //    A      D
    //       \ /
    //        C
    //
    //  A-B=1, A-C=4, B-D=6, C-D=1
    //  Shortest: A→C→D = 5
    // -----------------------------------------------------------
    @Test
    void shouldFindShortestPathInUndirectedDiamondGraph() {
        var graph = undirectedGraphOf(List.of(
                edge("A", "B", 1.0),
                edge("A", "C", 4.0),
                edge("B", "D", 6.0),
                edge("C", "D", 1.0)
        ));
        var bidir = new BidirectionalDijkstra<>(graph, graph, e -> e.data(), DoubleAlgebra.INSTANCE);

        Path<String, Double, Double> path = bidir.solve("A", "D");

        assertTrue(path.isFound());
        assertEquals(5.0, path.totalWeight());
        assertEquals(List.of("A", "C", "D"), path.nodes());
    }

    // -----------------------------------------------------------
    //  Test: results consistency with Dijkstra on an undirected graph
    //
    //     A --2-- B --3-- E
    //     |       |       |
    //     1       4       1
    //     |       |       |
    //     C --1-- D --2-- F
    //
    //  Shortest A→F: A→C→D→F = 1+1+2 = 4
    // -----------------------------------------------------------
    @Test
    void shouldProduceSameResultAsDijkstra() {
        var graph = undirectedGraphOf(List.of(
                edge("A", "B", 2.0),
                edge("A", "C", 1.0),
                edge("B", "D", 4.0),
                edge("B", "E", 3.0),
                edge("C", "D", 1.0),
                edge("D", "F", 2.0),
                edge("E", "F", 1.0)
        ));

        var dijkstra = new Dijkstra<>(graph, e -> e.data(), DoubleAlgebra.INSTANCE);
        var bidir = new BidirectionalDijkstra<>(graph, graph, e -> e.data(), DoubleAlgebra.INSTANCE);

        Path<String, Double, Double> dp = dijkstra.solve("A", "F");
        Path<String, Double, Double> bp = bidir.solve("A", "F");

        assertTrue(dp.isFound());
        assertTrue(bp.isFound());
        assertEquals(dp.totalWeight(), bp.totalWeight());
        assertEquals(dp.nodes(), bp.nodes());
    }

    // -----------------------------------------------------------
    //  Test: edges in the result have correct source/target
    //
    //    A --1-- B --1-- C
    //
    //  Path A→C: edges should have source→target in the path direction
    // -----------------------------------------------------------
    @Test
    void shouldReturnEdgesWithCorrectDirection() {
        var graph = undirectedGraphOf(List.of(
                edge("A", "B", 1.0),
                edge("B", "C", 1.0)
        ));
        var bidir = new BidirectionalDijkstra<>(graph, graph, e -> e.data(), DoubleAlgebra.INSTANCE);

        Path<String, Double, Double> path = bidir.solve("A", "C");

        assertTrue(path.isFound());
        assertEquals(2, path.edges().size());

        // Verify forward direction of edges
        assertEquals("A", path.edges().get(0).source());
        assertEquals("B", path.edges().get(0).target());
        assertEquals("B", path.edges().get(1).source());
        assertEquals("C", path.edges().get(1).target());
    }

    // -----------------------------------------------------------
    //  Test: null arguments → NPE
    // -----------------------------------------------------------
    @Test
    void shouldThrowNullPointerForNullArguments() {
        var graph = undirectedGraphOf(List.of(edge("A", "B", 1.0)));
        var bidir = new BidirectionalDijkstra<>(graph, graph, e -> e.data(), DoubleAlgebra.INSTANCE);

        assertThrows(NullPointerException.class, () -> bidir.solve(null, "A"));
        assertThrows(NullPointerException.class, () -> bidir.solve("A", null));
        assertThrows(NullPointerException.class, () ->
                new BidirectionalDijkstra<>(null, graph, e -> e.data(), DoubleAlgebra.INSTANCE));
        assertThrows(NullPointerException.class, () ->
                new BidirectionalDijkstra<>(graph, null, e -> e.data(), DoubleAlgebra.INSTANCE));
    }

    // -----------------------------------------------------------
    //  Test: directed multi-path graph with reversed graph
    //
    //    A --10--> B --1--> D
    //    |                  ^
    //    +--1--> C ---1-----+
    //
    //  Shortest: A→C→D = 2
    // -----------------------------------------------------------
    @Test
    void shouldFindOptimalPathInDirectedGraphWithReversed() {
        var adjacency = Map.<String, List<Edge<String, Double>>>of(
                "A", List.of(edge("A", "B", 10.0), edge("A", "C", 1.0)),
                "B", List.of(edge("B", "D", 1.0)),
                "C", List.of(edge("C", "D", 1.0)),
                "D", List.of()
        );
        var forward = graphOf(adjacency);
        var backward = reversedGraphOf(adjacency);

        var bidir = new BidirectionalDijkstra<>(forward, backward, e -> e.data(), DoubleAlgebra.INSTANCE);

        Path<String, Double, Double> path = bidir.solve("A", "D");

        assertTrue(path.isFound());
        assertEquals(2.0, path.totalWeight());
        assertEquals(List.of("A", "C", "D"), path.nodes());
    }
}
