package pl.cezarysanecki.solver.example;

import pl.cezarysanecki.solver.api.DoubleAlgebra;
import pl.cezarysanecki.solver.api.Path;
import pl.cezarysanecki.solver.core.BidirectionalDijkstra;
import pl.cezarysanecki.solver.core.Dijkstra;
import pl.cezarysanecki.solver.graph.AdjacencyListGraph;
import pl.cezarysanecki.solver.graph.ReversedGraph;

/**
 * Example 3: Bidirectional Dijkstra on a directed graph.
 *
 * <pre>
 *         2        3        1
 *   A --------→ B ------→ C ------→ D
 *   |                     ↑         |
 *   |          5          |    2    |
 *   └──────→ E ───────────┘         |
 *             ↑                      |
 *             └──────────────────────┘
 *                       4
 * </pre>
 *
 * Bidirectional searches the graph from both ends (source → and ← target)
 * and meets in the middle. Requires a reversed graph (ReversedGraph).
 *
 * Run with:
 * <pre>
 *   ./gradlew :solver-example:run -PmainClass=pl.cezarysanecki.solver.example.BidirectionalExample
 * </pre>
 */
public class BidirectionalExample {

    public static void main(String[] args) {
        // --- Directed graph ---
        var graph = AdjacencyListGraph.<String, Double>builder()
                .addEdge("A", "B", 2.0)
                .addEdge("B", "C", 3.0)
                .addEdge("C", "D", 1.0)
                .addEdge("A", "E", 5.0)
                .addEdge("E", "C", 2.0)    // A→E→C = 7.0, A→B→C = 5.0
                .addEdge("D", "E", 4.0)
                .build();

        // ReversedGraph — needed for backward search
        var reversed = new ReversedGraph<>(graph);

        var algebra = DoubleAlgebra.INSTANCE;
        var costFn = new pl.cezarysanecki.solver.api.CostFunction<String, Double, Double>() {
            @Override
            public Double cost(pl.cezarysanecki.solver.api.Edge<String, Double> edge) {
                return edge.data();
            }
        };

        // --- Dijkstra (unidirectional) ---
        var dijkstra = new Dijkstra<>(graph, costFn, algebra);
        Path<String, Double, Double> dijkstraPath = dijkstra.solve("A", "D");

        System.out.println("=== Dijkstra: A → D ===");
        System.out.println("Route:          " + dijkstraPath.nodes());
        System.out.println("Cost:           " + dijkstraPath.totalWeight());
        System.out.println("Visited nodes:  " + dijkstra.getVisitedNodes());
        System.out.println();

        // --- Bidirectional Dijkstra ---
        var bidir = new BidirectionalDijkstra<>(graph, reversed, costFn, algebra);
        Path<String, Double, Double> bidirPath = bidir.solve("A", "D");

        System.out.println("=== Bidirectional Dijkstra: A → D ===");
        System.out.println("Route:          " + bidirPath.nodes());
        System.out.println("Cost:           " + bidirPath.totalWeight());
        System.out.println("Visited nodes:  " + bidir.getVisitedNodes());
        System.out.println();

        // --- Comparison ---
        System.out.println("=== Comparison ===");
        System.out.println("Same cost:      " + dijkstraPath.totalWeight().equals(bidirPath.totalWeight()));
        System.out.println("Same route:     " + dijkstraPath.nodes().equals(bidirPath.nodes()));
        System.out.println();

        // --- Cycle: D → E → C → D (circular route) ---
        Path<String, Double, Double> cyclePath = dijkstra.solve("D", "D");

        System.out.println("=== Dijkstra: D → D (source == target) ===");
        System.out.println("Route:          " + cyclePath.nodes());
        System.out.println("Cost:           " + cyclePath.totalWeight());
        System.out.println("(Returns zero-cost path — source == target)");
    }
}
