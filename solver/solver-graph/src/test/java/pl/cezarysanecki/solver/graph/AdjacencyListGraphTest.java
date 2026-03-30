package pl.cezarysanecki.solver.graph;

import org.junit.jupiter.api.Test;
import pl.cezarysanecki.solver.api.DoubleAlgebra;
import pl.cezarysanecki.solver.api.Edge;
import pl.cezarysanecki.solver.api.Path;
import pl.cezarysanecki.solver.core.Dijkstra;

import java.util.List;
import java.util.Set;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdjacencyListGraphTest {

    // -----------------------------------------------------------
    //  Test: builder creates a graph with correct nodes
    // -----------------------------------------------------------
    @Test
    void shouldContainAllNodes() {
        var graph = AdjacencyListGraph.<String, Double>builder()
                .addEdge("A", "B", 1.0)
                .addEdge("B", "C", 2.0)
                .addNode("D")
                .build();

        assertEquals(Set.of("A", "B", "C", "D"), graph.nodes());
    }

    // -----------------------------------------------------------
    //  Test: addEdge adds a directed edge (not the reverse)
    //
    //    A --1--> B
    //
    //  A has neighbor B, B does not have neighbor A
    // -----------------------------------------------------------
    @Test
    void shouldAddDirectedEdge() {
        var graph = AdjacencyListGraph.<String, Double>builder()
                .addEdge("A", "B", 1.0)
                .build();

        var neighborsOfA = toList(graph.neighbors("A"));
        var neighborsOfB = toList(graph.neighbors("B"));

        assertEquals(1, neighborsOfA.size());
        assertEquals("A", neighborsOfA.get(0).source());
        assertEquals("B", neighborsOfA.get(0).target());
        assertEquals(1.0, neighborsOfA.get(0).data());

        assertTrue(neighborsOfB.isEmpty());
    }

    // -----------------------------------------------------------
    //  Test: addUndirectedEdge adds edges in both directions
    //
    //    A --1-- B
    // -----------------------------------------------------------
    @Test
    void shouldAddUndirectedEdge() {
        var graph = AdjacencyListGraph.<String, Double>builder()
                .addUndirectedEdge("A", "B", 5.0)
                .build();

        var neighborsOfA = toList(graph.neighbors("A"));
        var neighborsOfB = toList(graph.neighbors("B"));

        assertEquals(1, neighborsOfA.size());
        assertEquals("B", neighborsOfA.get(0).target());
        assertEquals(5.0, neighborsOfA.get(0).data());

        assertEquals(1, neighborsOfB.size());
        assertEquals("A", neighborsOfB.get(0).target());
        assertEquals(5.0, neighborsOfB.get(0).data());
    }

    // -----------------------------------------------------------
    //  Test: containsNode
    // -----------------------------------------------------------
    @Test
    void shouldReportContainsNode() {
        var graph = AdjacencyListGraph.<String, Double>builder()
                .addEdge("A", "B", 1.0)
                .build();

        assertTrue(graph.containsNode("A"));
        assertTrue(graph.containsNode("B"));
        assertFalse(graph.containsNode("Z"));
    }

    // -----------------------------------------------------------
    //  Test: neighbors for a non-existent node → empty
    // -----------------------------------------------------------
    @Test
    void shouldReturnEmptyNeighborsForUnknownNode() {
        var graph = AdjacencyListGraph.<String, Double>builder()
                .addEdge("A", "B", 1.0)
                .build();

        var neighbors = toList(graph.neighbors("Z"));
        assertTrue(neighbors.isEmpty());
    }

    // -----------------------------------------------------------
    //  Test: immutability — modifying builder after build() has no effect
    // -----------------------------------------------------------
    @Test
    void shouldBeImmutableAfterBuild() {
        var builder = AdjacencyListGraph.<String, Double>builder()
                .addEdge("A", "B", 1.0);
        var graph = builder.build();

        // Builder still works, but changes do not affect the built graph
        builder.addEdge("A", "C", 2.0);

        assertEquals(Set.of("A", "B"), graph.nodes());
        assertEquals(1, toList(graph.neighbors("A")).size());
    }

    // -----------------------------------------------------------
    //  Test: null arguments in Builder → NPE
    // -----------------------------------------------------------
    @Test
    void shouldThrowNullPointerForNullArguments() {
        var builder = AdjacencyListGraph.<String, Double>builder();

        assertThrows(NullPointerException.class, () -> builder.addNode(null));
        assertThrows(NullPointerException.class, () -> builder.addEdge(null, "B", 1.0));
        assertThrows(NullPointerException.class, () -> builder.addEdge("A", null, 1.0));
        assertThrows(NullPointerException.class, () -> builder.addUndirectedEdge(null, "B", 1.0));
        assertThrows(NullPointerException.class, () -> builder.addUndirectedEdge("A", null, 1.0));
    }

    // -----------------------------------------------------------
    //  End-to-end: build graph via builder → solve with Dijkstra
    //
    //    A ---5--- B
    //    |         |
    //    2         3
    //    |         |
    //    C ---1--- D
    //
    //  A→D: A→C→D = 3 (not A→B→D = 8)
    // -----------------------------------------------------------
    @Test
    void endToEndDijkstraOnUndirectedGraph() {
        var graph = AdjacencyListGraph.<String, Double>builder()
                .addUndirectedEdge("A", "B", 5.0)
                .addUndirectedEdge("A", "C", 2.0)
                .addUndirectedEdge("B", "D", 3.0)
                .addUndirectedEdge("C", "D", 1.0)
                .build();

        var dijkstra = new Dijkstra<>(graph, e -> e.data(), DoubleAlgebra.INSTANCE);
        Path<String, Double, Double> path = dijkstra.solve("A", "D");

        assertTrue(path.isFound());
        assertEquals(List.of("A", "C", "D"), path.nodes());
        assertEquals(3.0, path.totalWeight());
    }

    // -----------------------------------------------------------
    //  End-to-end: directed graph with Dijkstra
    //
    //    A --1--> B --2--> C --3--> D
    // -----------------------------------------------------------
    @Test
    void endToEndDijkstraOnDirectedGraph() {
        var graph = AdjacencyListGraph.<String, Double>builder()
                .addEdge("A", "B", 1.0)
                .addEdge("B", "C", 2.0)
                .addEdge("C", "D", 3.0)
                .build();

        var dijkstra = new Dijkstra<>(graph, e -> e.data(), DoubleAlgebra.INSTANCE);
        Path<String, Double, Double> path = dijkstra.solve("A", "D");

        assertTrue(path.isFound());
        assertEquals(List.of("A", "B", "C", "D"), path.nodes());
        assertEquals(6.0, path.totalWeight());
    }

    // -----------------------------------------------------------
    //  End-to-end: unreachable target in a directed graph
    //
    //    A --1--> B     C (no outgoing from B, C isolated)
    // -----------------------------------------------------------
    @Test
    void endToEndUnreachableTarget() {
        var graph = AdjacencyListGraph.<String, Double>builder()
                .addEdge("A", "B", 1.0)
                .addNode("C")
                .build();

        var dijkstra = new Dijkstra<>(graph, e -> e.data(), DoubleAlgebra.INSTANCE);
        Path<String, Double, Double> path = dijkstra.solve("A", "C");

        assertFalse(path.isFound());
    }

    // -----------------------------------------------------------
    //  Test: empty graph
    // -----------------------------------------------------------
    @Test
    void shouldBuildEmptyGraph() {
        var graph = AdjacencyListGraph.<String, Double>builder().build();

        assertTrue(graph.nodes().isEmpty());
    }

    // -----------------------------------------------------------
    //  Test: Integer nodes (generics)
    // -----------------------------------------------------------
    @Test
    void shouldWorkWithIntegerNodes() {
        var graph = AdjacencyListGraph.<Integer, Double>builder()
                .addEdge(1, 2, 1.5)
                .addEdge(2, 3, 2.5)
                .build();

        var dijkstra = new Dijkstra<>(graph, e -> e.data(), DoubleAlgebra.INSTANCE);
        Path<Integer, Double, Double> path = dijkstra.solve(1, 3);

        assertTrue(path.isFound());
        assertEquals(List.of(1, 2, 3), path.nodes());
        assertEquals(4.0, path.totalWeight());
    }

    // --- utility ---

    private static <T> List<T> toList(Iterable<T> iterable) {
        return StreamSupport.stream(iterable.spliterator(), false).toList();
    }
}
