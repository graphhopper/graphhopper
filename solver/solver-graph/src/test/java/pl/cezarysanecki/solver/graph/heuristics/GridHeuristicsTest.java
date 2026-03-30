package pl.cezarysanecki.solver.graph.heuristics;

import org.junit.jupiter.api.Test;
import pl.cezarysanecki.solver.api.CostFunction;
import pl.cezarysanecki.solver.api.DoubleAlgebra;
import pl.cezarysanecki.solver.api.Edge;
import pl.cezarysanecki.solver.api.Heuristic;
import pl.cezarysanecki.solver.api.Path;
import pl.cezarysanecki.solver.core.AStar;
import pl.cezarysanecki.solver.core.Dijkstra;
import pl.cezarysanecki.solver.graph.GridEdge;
import pl.cezarysanecki.solver.graph.GridGraph;
import pl.cezarysanecki.solver.graph.GridGraph.Connectivity;
import pl.cezarysanecki.solver.graph.GridNode;
import pl.cezarysanecki.solver.graph.SimpleGridNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GridHeuristicsTest {

    private static final double DELTA = 1e-9;
    private static final double SQRT2 = Math.sqrt(2.0);

    // =========================================================
    //  Value tests — known node pairs
    // =========================================================

    // -----------------------------------------------------------
    //  Manhattan: |Δrow| + |Δcol|
    // -----------------------------------------------------------
    @Test
    void manhattanSameNode() {
        var h = GridHeuristics.manhattan();
        assertEquals(0.0, h.estimate(node(3, 5), node(3, 5)), DELTA);
    }

    @Test
    void manhattanKnownPair() {
        var h = GridHeuristics.manhattan();
        // |0-3| + |0-4| = 7
        assertEquals(7.0, h.estimate(node(0, 0), node(3, 4)), DELTA);
    }

    // -----------------------------------------------------------
    //  Chebyshev: max(|Δrow|, |Δcol|)
    // -----------------------------------------------------------
    @Test
    void chebyshevSameNode() {
        var h = GridHeuristics.chebyshev();
        assertEquals(0.0, h.estimate(node(2, 2), node(2, 2)), DELTA);
    }

    @Test
    void chebyshevKnownPair() {
        var h = GridHeuristics.chebyshev();
        // max(|0-3|, |0-4|) = max(3, 4) = 4
        assertEquals(4.0, h.estimate(node(0, 0), node(3, 4)), DELTA);
    }

    // -----------------------------------------------------------
    //  Octile: max(dx,dy) + (√2-1) * min(dx,dy)
    // -----------------------------------------------------------
    @Test
    void octileSameNode() {
        var h = GridHeuristics.octile();
        assertEquals(0.0, h.estimate(node(1, 1), node(1, 1)), DELTA);
    }

    @Test
    void octileKnownPair() {
        var h = GridHeuristics.octile();
        // dx=4, dy=3 → max=4, min=3 → 4 + (√2-1)*3
        double expected = 4.0 + (SQRT2 - 1.0) * 3.0;
        assertEquals(expected, h.estimate(node(0, 0), node(3, 4)), DELTA);
    }

    @Test
    void octilePureDiagonal() {
        var h = GridHeuristics.octile();
        // dx=3, dy=3 → max=3, min=3 → 3 + (√2-1)*3 = 3*√2
        assertEquals(3.0 * SQRT2, h.estimate(node(0, 0), node(3, 3)), DELTA);
    }

    @Test
    void octilePureStraight() {
        var h = GridHeuristics.octile();
        // dx=5, dy=0 → max=5, min=0 → 5.0
        assertEquals(5.0, h.estimate(node(0, 0), node(0, 5)), DELTA);
    }

    // -----------------------------------------------------------
    //  Euclidean: √(Δrow² + Δcol²)
    // -----------------------------------------------------------
    @Test
    void euclideanSameNode() {
        var h = GridHeuristics.euclidean();
        assertEquals(0.0, h.estimate(node(4, 4), node(4, 4)), DELTA);
    }

    @Test
    void euclideanKnownPair() {
        var h = GridHeuristics.euclidean();
        // √(3² + 4²) = 5.0
        assertEquals(5.0, h.estimate(node(0, 0), node(3, 4)), DELTA);
    }

    // =========================================================
    //  Relationships between heuristics
    // =========================================================

    // -----------------------------------------------------------
    //  Manhattan ≥ Chebyshev (always)
    // -----------------------------------------------------------
    @Test
    void manhattanGreaterOrEqualChebyshev() {
        var manhattan = GridHeuristics.manhattan();
        var chebyshev = GridHeuristics.chebyshev();

        for (int dr = 0; dr <= 10; dr++) {
            for (int dc = 0; dc <= 10; dc++) {
                var from = node(0, 0);
                var to = node(dr, dc);
                assertTrue(manhattan.estimate(from, to) >= chebyshev.estimate(from, to),
                        "Manhattan < Chebyshev at dr=" + dr + " dc=" + dc);
            }
        }
    }

    // -----------------------------------------------------------
    //  Octile ≥ Chebyshev and ≤ Manhattan
    // -----------------------------------------------------------
    @Test
    void octileBetweenChebyshevAndManhattan() {
        var manhattan = GridHeuristics.manhattan();
        var chebyshev = GridHeuristics.chebyshev();
        var octile = GridHeuristics.octile();

        for (int dr = 0; dr <= 10; dr++) {
            for (int dc = 0; dc <= 10; dc++) {
                var from = node(0, 0);
                var to = node(dr, dc);
                double m = manhattan.estimate(from, to);
                double c = chebyshev.estimate(from, to);
                double o = octile.estimate(from, to);
                assertTrue(o >= c - DELTA,
                        "Octile < Chebyshev at dr=" + dr + " dc=" + dc);
                assertTrue(o <= m + DELTA,
                        "Octile > Manhattan at dr=" + dr + " dc=" + dc);
            }
        }
    }

    // -----------------------------------------------------------
    //  Euclidean ≤ Manhattan (always)
    // -----------------------------------------------------------
    @Test
    void euclideanLessOrEqualManhattan() {
        var manhattan = GridHeuristics.manhattan();
        var euclidean = GridHeuristics.euclidean();

        for (int dr = 0; dr <= 10; dr++) {
            for (int dc = 0; dc <= 10; dc++) {
                var from = node(0, 0);
                var to = node(dr, dc);
                assertTrue(euclidean.estimate(from, to) <= manhattan.estimate(from, to) + DELTA,
                        "Euclidean > Manhattan at dr=" + dr + " dc=" + dc);
            }
        }
    }

    // =========================================================
    //  Integration with A* — correctness vs Dijkstra
    // =========================================================

    // -----------------------------------------------------------
    //  A* with Manhattan on 4-connectivity → result == Dijkstra
    // -----------------------------------------------------------
    @Test
    void aStarManhattanMatchesDijkstraOnFourConnGrid() {
        var grid = new GridGraph(8, 8, Connectivity.FOUR);
        CostFunction<GridNode, GridEdge, Double> cost = e -> 1.0;

        var dijkstra = new Dijkstra<>(grid, cost, DoubleAlgebra.INSTANCE);
        var astar = new AStar<>(grid, cost, DoubleAlgebra.INSTANCE, GridHeuristics.manhattan());

        var source = node(0, 0);
        var target = node(7, 7);

        Path<GridNode, GridEdge, Double> dp = dijkstra.solve(source, target);
        Path<GridNode, GridEdge, Double> ap = astar.solve(source, target);

        assertTrue(dp.isFound());
        assertTrue(ap.isFound());
        assertEquals(dp.totalWeight(), ap.totalWeight(), DELTA);
        assertTrue(astar.getVisitedNodes() <= dijkstra.getVisitedNodes(),
                "A* visited " + astar.getVisitedNodes()
                        + " but Dijkstra visited " + dijkstra.getVisitedNodes());
    }

    // -----------------------------------------------------------
    //  A* with Octile on 8-connectivity with √2 cost for diagonals
    // -----------------------------------------------------------
    @Test
    void aStarOctileMatchesDijkstraOnEightConnGrid() {
        var grid = new GridGraph(8, 8, Connectivity.EIGHT);
        CostFunction<GridNode, GridEdge, Double> cost = e -> {
            var dir = e.data().direction();
            return (dir.dRow() != 0 && dir.dCol() != 0) ? SQRT2 : 1.0;
        };

        var dijkstra = new Dijkstra<>(grid, cost, DoubleAlgebra.INSTANCE);
        var astar = new AStar<>(grid, cost, DoubleAlgebra.INSTANCE, GridHeuristics.octile());

        var source = node(0, 0);
        var target = node(7, 7);

        Path<GridNode, GridEdge, Double> dp = dijkstra.solve(source, target);
        Path<GridNode, GridEdge, Double> ap = astar.solve(source, target);

        assertTrue(dp.isFound());
        assertTrue(ap.isFound());
        assertEquals(dp.totalWeight(), ap.totalWeight(), DELTA);
        assertTrue(astar.getVisitedNodes() <= dijkstra.getVisitedNodes(),
                "A* visited " + astar.getVisitedNodes()
                        + " but Dijkstra visited " + dijkstra.getVisitedNodes());
    }

    // -----------------------------------------------------------
    //  A* with Chebyshev on 8-connectivity with unit cost for diagonals
    // -----------------------------------------------------------
    @Test
    void aStarChebyshevMatchesDijkstraOnEightConnGridUnitCost() {
        var grid = new GridGraph(8, 8, Connectivity.EIGHT);
        CostFunction<GridNode, GridEdge, Double> cost = e -> 1.0;

        var dijkstra = new Dijkstra<>(grid, cost, DoubleAlgebra.INSTANCE);
        var astar = new AStar<>(grid, cost, DoubleAlgebra.INSTANCE, GridHeuristics.chebyshev());

        var source = node(0, 0);
        var target = node(7, 7);

        Path<GridNode, GridEdge, Double> dp = dijkstra.solve(source, target);
        Path<GridNode, GridEdge, Double> ap = astar.solve(source, target);

        assertTrue(dp.isFound());
        assertTrue(ap.isFound());
        assertEquals(dp.totalWeight(), ap.totalWeight(), DELTA);
    }

    // -----------------------------------------------------------
    //  A* with Euclidean on 4-connectivity — admissible, correct result
    // -----------------------------------------------------------
    @Test
    void aStarEuclideanMatchesDijkstraOnFourConnGrid() {
        var grid = new GridGraph(8, 8, Connectivity.FOUR);
        CostFunction<GridNode, GridEdge, Double> cost = e -> 1.0;

        var dijkstra = new Dijkstra<>(grid, cost, DoubleAlgebra.INSTANCE);
        var astar = new AStar<>(grid, cost, DoubleAlgebra.INSTANCE, GridHeuristics.euclidean());

        var source = node(0, 0);
        var target = node(7, 7);

        Path<GridNode, GridEdge, Double> dp = dijkstra.solve(source, target);
        Path<GridNode, GridEdge, Double> ap = astar.solve(source, target);

        assertTrue(dp.isFound());
        assertTrue(ap.isFound());
        assertEquals(dp.totalWeight(), ap.totalWeight(), DELTA);
    }

    // -----------------------------------------------------------
    //  A* with Manhattan on a maze — path correctness
    //
    //    .  .  .  .  .
    //    .  #  #  #  .
    //    .  .  .  #  .
    //    .  #  .  .  .
    //    .  .  .  .  .
    // -----------------------------------------------------------
    @Test
    void aStarManhattanOnMazeMatchesDijkstra() {
        boolean[][] walls = {
                {false, false, false, false, false},
                {false, true,  true,  true,  false},
                {false, false, false, true,  false},
                {false, true,  false, false, false},
                {false, false, false, false, false},
        };

        var maze = new GridGraph(5, 5, Connectivity.FOUR,
                n -> !walls[n.row()][n.col()]);
        CostFunction<GridNode, GridEdge, Double> cost = e -> 1.0;

        var dijkstra = new Dijkstra<>(maze, cost, DoubleAlgebra.INSTANCE);
        var astar = new AStar<>(maze, cost, DoubleAlgebra.INSTANCE, GridHeuristics.manhattan());

        var source = node(0, 0);
        var target = node(4, 4);

        Path<GridNode, GridEdge, Double> dp = dijkstra.solve(source, target);
        Path<GridNode, GridEdge, Double> ap = astar.solve(source, target);

        assertTrue(dp.isFound());
        assertTrue(ap.isFound());
        assertEquals(dp.totalWeight(), ap.totalWeight(), DELTA);

        // Path does not go through walls
        for (GridNode node : ap.nodes()) {
            assertTrue(!walls[node.row()][node.col()],
                    "Path goes through wall at " + node);
        }
    }

    // --- utility ---

    private static GridNode node(int row, int col) {
        return new SimpleGridNode(row, col);
    }
}
