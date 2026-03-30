package pl.cezarysanecki.solver.graph;

import org.junit.jupiter.api.Test;
import pl.cezarysanecki.solver.api.DoubleAlgebra;
import pl.cezarysanecki.solver.api.Edge;
import pl.cezarysanecki.solver.api.Path;
import pl.cezarysanecki.solver.core.BidirectionalDijkstra;
import pl.cezarysanecki.solver.core.Dijkstra;

import java.util.List;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReversedGraphTest {

    // -----------------------------------------------------------
    //  Test: reversed graph reverses edges
    //
    //    Original: A --1--> B --2--> C
    //    Reversed: A <--1-- B <--2-- C
    //
    //  In reversed: neighbors(B) = [Edge(B, A, 1.0)]
    //               neighbors(C) = [Edge(C, B, 2.0)]
    // -----------------------------------------------------------
    @Test
    void shouldReverseEdgeDirections() {
        var original = AdjacencyListGraph.<String, Double>builder()
                .addEdge("A", "B", 1.0)
                .addEdge("B", "C", 2.0)
                .build();

        var reversed = new ReversedGraph<>(original);

        // A had outgoing A→B, reversed: B has outgoing B→A
        var neighborsOfA = toList(reversed.neighbors("A"));
        assertTrue(neighborsOfA.isEmpty()); // A had no incoming edges

        var neighborsOfB = toList(reversed.neighbors("B"));
        assertEquals(1, neighborsOfB.size());
        assertEquals("B", neighborsOfB.get(0).source());
        assertEquals("A", neighborsOfB.get(0).target());
        assertEquals(1.0, neighborsOfB.get(0).data());

        var neighborsOfC = toList(reversed.neighbors("C"));
        assertEquals(1, neighborsOfC.size());
        assertEquals("C", neighborsOfC.get(0).source());
        assertEquals("B", neighborsOfC.get(0).target());
        assertEquals(2.0, neighborsOfC.get(0).data());
    }

    // -----------------------------------------------------------
    //  Test: nodes() returns the same nodes
    // -----------------------------------------------------------
    @Test
    void shouldHaveSameNodes() {
        var original = AdjacencyListGraph.<String, Double>builder()
                .addEdge("A", "B", 1.0)
                .addNode("C")
                .build();

        var reversed = new ReversedGraph<>(original);

        assertEquals(original.nodes(), reversed.nodes());
    }

    // -----------------------------------------------------------
    //  Test: containsNode delegates correctly
    // -----------------------------------------------------------
    @Test
    void shouldReportContainsNode() {
        var original = AdjacencyListGraph.<String, Double>builder()
                .addEdge("A", "B", 1.0)
                .build();

        var reversed = new ReversedGraph<>(original);

        assertTrue(reversed.containsNode("A"));
        assertTrue(reversed.containsNode("B"));
        assertFalse(reversed.containsNode("Z"));
    }

    // -----------------------------------------------------------
    //  Test: null original → NPE
    // -----------------------------------------------------------
    @Test
    void shouldThrowForNullOriginal() {
        assertThrows(NullPointerException.class, () -> new ReversedGraph<>(null));
    }

    // -----------------------------------------------------------
    //  End-to-end: BidirectionalDijkstra z ReversedGraph
    //
    //    A --1--> B --2--> C --3--> D
    //
    //  Forward: A→B→C→D
    //  Backward: ReversedGraph (D→C→B→A)
    // -----------------------------------------------------------
    @Test
    void endToEndBidirectionalDijkstraWithReversedGraph() {
        var forward = AdjacencyListGraph.<String, Double>builder()
                .addEdge("A", "B", 1.0)
                .addEdge("B", "C", 2.0)
                .addEdge("C", "D", 3.0)
                .build();

        var backward = new ReversedGraph<>(forward);

        var bidir = new BidirectionalDijkstra<>(forward, backward,
                e -> e.data(), DoubleAlgebra.INSTANCE);

        Path<String, Double, Double> path = bidir.solve("A", "D");

        assertTrue(path.isFound());
        assertEquals(6.0, path.totalWeight());
        assertEquals(List.of("A", "B", "C", "D"), path.nodes());
    }

    // -----------------------------------------------------------
    //  End-to-end: graph with multiple paths — optimality
    //
    //    A --10--> B --1--> D
    //    |                  ^
    //    +--1--> C ---1-----+
    //
    //  Shortest: A→C→D = 2
    // -----------------------------------------------------------
    @Test
    void endToEndBidirectionalFindsOptimalPath() {
        var forward = AdjacencyListGraph.<String, Double>builder()
                .addEdge("A", "B", 10.0)
                .addEdge("A", "C", 1.0)
                .addEdge("B", "D", 1.0)
                .addEdge("C", "D", 1.0)
                .build();

        var backward = new ReversedGraph<>(forward);

        var bidir = new BidirectionalDijkstra<>(forward, backward,
                e -> e.data(), DoubleAlgebra.INSTANCE);

        Path<String, Double, Double> path = bidir.solve("A", "D");

        assertTrue(path.isFound());
        assertEquals(2.0, path.totalWeight());
        assertEquals(List.of("A", "C", "D"), path.nodes());
    }

    // -----------------------------------------------------------
    //  End-to-end: comparison of Dijkstra vs Bidirectional+Reversed
    //
    //    A --2--> B --3--> E
    //    |        |        |
    //    v1       v4       v1
    //    C --1--> D --2--> F
    // -----------------------------------------------------------
    @Test
    void endToEndShouldMatchDijkstraResult() {
        var forward = AdjacencyListGraph.<String, Double>builder()
                .addEdge("A", "B", 2.0)
                .addEdge("A", "C", 1.0)
                .addEdge("B", "D", 4.0)
                .addEdge("B", "E", 3.0)
                .addEdge("C", "D", 1.0)
                .addEdge("D", "F", 2.0)
                .addEdge("E", "F", 1.0)
                .build();

        var backward = new ReversedGraph<>(forward);

        var dijkstra = new Dijkstra<>(forward, e -> e.data(), DoubleAlgebra.INSTANCE);
        var bidir = new BidirectionalDijkstra<>(forward, backward,
                e -> e.data(), DoubleAlgebra.INSTANCE);

        Path<String, Double, Double> dp = dijkstra.solve("A", "F");
        Path<String, Double, Double> bp = bidir.solve("A", "F");

        assertTrue(dp.isFound());
        assertTrue(bp.isFound());
        assertEquals(dp.totalWeight(), bp.totalWeight());
        assertEquals(dp.nodes(), bp.nodes());
    }

    // -----------------------------------------------------------
    //  End-to-end: unreachable target
    // -----------------------------------------------------------
    @Test
    void endToEndUnreachableTarget() {
        var forward = AdjacencyListGraph.<String, Double>builder()
                .addEdge("A", "B", 1.0)
                .addNode("C")
                .build();

        var backward = new ReversedGraph<>(forward);
        var bidir = new BidirectionalDijkstra<>(forward, backward,
                e -> e.data(), DoubleAlgebra.INSTANCE);

        Path<String, Double, Double> path = bidir.solve("A", "C");

        assertFalse(path.isFound());
    }

    // --- utility ---

    private static <T> List<T> toList(Iterable<T> iterable) {
        return StreamSupport.stream(iterable.spliterator(), false).toList();
    }
}
