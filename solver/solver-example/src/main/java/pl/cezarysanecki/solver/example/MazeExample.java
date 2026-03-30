package pl.cezarysanecki.solver.example;

import pl.cezarysanecki.solver.api.DoubleAlgebra;
import pl.cezarysanecki.solver.api.Path;
import pl.cezarysanecki.solver.core.AStar;
import pl.cezarysanecki.solver.core.Dijkstra;
import pl.cezarysanecki.solver.graph.GridEdge;
import pl.cezarysanecki.solver.graph.GridGraph;
import pl.cezarysanecki.solver.graph.GridGraph.Connectivity;
import pl.cezarysanecki.solver.graph.GridNode;
import pl.cezarysanecki.solver.graph.heuristics.GridHeuristics;

/**
 * Example 2: A* with Manhattan heuristic on a 7×7 maze.
 *
 * <pre>
 *   . . . # . . .
 *   . # . # . # .
 *   . # . . . # .
 *   . # # # . # .
 *   . . . # . . .
 *   . # . # # # .
 *   . # . . . . .
 *
 *   S = (0,0), T = (6,6)
 *   # = wall (impassable)
 *   . = free cell
 * </pre>
 *
 * Compares A* (with Manhattan) vs Dijkstra — same route, fewer visited nodes.
 *
 * Run with:
 * <pre>
 *   ./gradlew :solver-example:run -PmainClass=pl.cezarysanecki.solver.example.MazeExample
 * </pre>
 */
public class MazeExample {

    // 0 = free, 1 = wall
    private static final int[][] MAZE = {
            {0, 0, 0, 1, 0, 0, 0},
            {0, 1, 0, 1, 0, 1, 0},
            {0, 1, 0, 0, 0, 1, 0},
            {0, 1, 1, 1, 0, 1, 0},
            {0, 0, 0, 1, 0, 0, 0},
            {0, 1, 0, 1, 1, 1, 0},
            {0, 1, 0, 0, 0, 0, 0},
    };

    public static void main(String[] args) {
        int rows = MAZE.length;
        int cols = MAZE[0].length;

        // --- Build a grid with a wall-blocking predicate ---
        var grid = new GridGraph(rows, cols, Connectivity.FOUR,
                node -> MAZE[node.row()][node.col()] == 0);

        GridNode start = grid.node(0, 0);
        GridNode end = grid.node(rows - 1, cols - 1);

        // Cost = 1.0 per step
        var costFn = new pl.cezarysanecki.solver.api.CostFunction<GridNode, GridEdge, Double>() {
            @Override
            public Double cost(pl.cezarysanecki.solver.api.Edge<GridNode, GridEdge> edge) {
                return 1.0;
            }
        };
        var algebra = DoubleAlgebra.INSTANCE;

        // --- A* with Manhattan ---
        var aStar = new AStar<>(grid, costFn, algebra, GridHeuristics.manhattan());
        Path<GridNode, GridEdge, Double> aStarPath = aStar.solve(start, end);

        System.out.println("=== A* (Manhattan) on 7×7 maze ===");
        System.out.println("Found:          " + aStarPath.isFound());
        System.out.println("Route length:   " + aStarPath.edges().size() + " steps");
        System.out.println("Cost:           " + aStarPath.totalWeight());
        System.out.println("Visited nodes:  " + aStar.getVisitedNodes());
        System.out.println();

        // --- Dijkstra (no heuristic) on the same maze ---
        var dijkstra = new Dijkstra<>(grid, costFn, algebra);
        Path<GridNode, GridEdge, Double> dijkstraPath = dijkstra.solve(start, end);

        System.out.println("=== Dijkstra on the same maze ===");
        System.out.println("Found:          " + dijkstraPath.isFound());
        System.out.println("Route length:   " + dijkstraPath.edges().size() + " steps");
        System.out.println("Cost:           " + dijkstraPath.totalWeight());
        System.out.println("Visited nodes:  " + dijkstra.getVisitedNodes());
        System.out.println();

        // --- Comparison ---
        System.out.println("=== Comparison ===");
        System.out.println("Same optimal cost: "
                + aStarPath.totalWeight().equals(dijkstraPath.totalWeight()));
        System.out.println("A* visited fewer nodes: "
                + (aStar.getVisitedNodes() < dijkstra.getVisitedNodes()));
        System.out.println();

        // --- Route visualization ---
        printMaze(aStarPath, rows, cols);
    }

    private static void printMaze(Path<GridNode, GridEdge, Double> path, int rows, int cols) {
        char[][] display = new char[rows][cols];
        for (int r = 0; r < rows; r++)
            for (int c = 0; c < cols; c++)
                display[r][c] = MAZE[r][c] == 1 ? '#' : '.';

        for (GridNode node : path.nodes())
            display[node.row()][node.col()] = '*';

        // start and target
        display[0][0] = 'S';
        display[rows - 1][cols - 1] = 'T';

        System.out.println("A* route through maze (* = path, # = wall):");
        for (char[] row : display) {
            for (char c : row)
                System.out.print(c + " ");
            System.out.println();
        }
    }
}
