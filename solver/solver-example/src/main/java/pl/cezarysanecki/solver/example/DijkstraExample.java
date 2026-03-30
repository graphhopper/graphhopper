package pl.cezarysanecki.solver.example;

import pl.cezarysanecki.solver.api.DoubleAlgebra;
import pl.cezarysanecki.solver.api.Path;
import pl.cezarysanecki.solver.core.Dijkstra;
import pl.cezarysanecki.solver.graph.AdjacencyListGraph;

/**
 * Example 1: Dijkstra on a hand-built city graph.
 *
 * <pre>
 *         7
 *   WAW ----→ GDA
 *    |  \       |
 *  4 |   \ 2   | 3
 *    ↓    ↓    ↓
 *   LOD   KRA → WRO
 *    |     1    ↑
 *  3 |          | 2
 *    └──────────┘
 * </pre>
 *
 * Run with:
 * <pre>
 *   ./gradlew :solver-example:run -PmainClass=pl.cezarysanecki.solver.example.DijkstraExample
 * </pre>
 */
public class DijkstraExample {

    public static void main(String[] args) {
        // --- Build a city graph with distances (km) ---
        var graph = AdjacencyListGraph.<String, Double>builder()
                .addEdge("WAW", "GDA", 7.0)
                .addEdge("WAW", "LOD", 4.0)
                .addEdge("WAW", "KRA", 2.0)
                .addEdge("GDA", "WRO", 3.0)
                .addEdge("KRA", "WRO", 1.0)
                .addEdge("LOD", "WRO", 3.0)
                .build();

        // --- Dijkstra: WAW → WRO ---
        var dijkstra = new Dijkstra<>(graph, edge -> edge.data(), DoubleAlgebra.INSTANCE);
        Path<String, Double, Double> path = dijkstra.solve("WAW", "WRO");

        System.out.println("=== Dijkstra: WAW → WRO ===");
        System.out.println("Found:          " + path.isFound());
        System.out.println("Route:          " + path.nodes());
        System.out.println("Cost:           " + path.totalWeight());
        System.out.println("Visited nodes:  " + dijkstra.getVisitedNodes());
        System.out.println();

        // --- Dijkstra: WAW → GDA ---
        Path<String, Double, Double> path2 = dijkstra.solve("WAW", "GDA");

        System.out.println("=== Dijkstra: WAW → GDA ===");
        System.out.println("Found:          " + path2.isFound());
        System.out.println("Route:          " + path2.nodes());
        System.out.println("Cost:           " + path2.totalWeight());
        System.out.println("Visited nodes:  " + dijkstra.getVisitedNodes());
        System.out.println();

        // --- Unreachable target: WRO → WAW (directed graph, no return path) ---
        Path<String, Double, Double> path3 = dijkstra.solve("WRO", "WAW");

        System.out.println("=== Dijkstra: WRO → WAW (no path) ===");
        System.out.println("Found:          " + path3.isFound());
        System.out.println("Route:          " + path3.nodes());
        System.out.println("Cost:           " + path3.totalWeight());
    }
}
