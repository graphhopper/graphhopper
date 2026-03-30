package pl.cezarysanecki.solver.graph;

import org.junit.jupiter.api.Test;
import pl.cezarysanecki.solver.api.Edge;
import pl.cezarysanecki.solver.api.Heuristic;
import pl.cezarysanecki.solver.api.IntAlgebra;
import pl.cezarysanecki.solver.api.Path;
import pl.cezarysanecki.solver.core.AStar;
import pl.cezarysanecki.solver.core.Dijkstra;
import pl.cezarysanecki.solver.graph.GridGraph.Connectivity;

import java.util.List;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GridGraphTest {

    // -----------------------------------------------------------
    //  Test: 3x3 grid, 4-connectivity — correct node count
    // -----------------------------------------------------------
    @Test
    void shouldHaveCorrectNodeCount() {
        var grid = new GridGraph(3, 4, Connectivity.FOUR);

        assertEquals(12, grid.nodes().size());
    }

    // -----------------------------------------------------------
    //  Test: 4-connectivity — corner has 2 neighbors, edge 3, center 4
    // -----------------------------------------------------------
    @Test
    void shouldHaveCorrectNeighborCountFourConnectivity() {
        var grid = new GridGraph(3, 3, Connectivity.FOUR);

        // Corner (0,0) → 2 neighbors
        assertEquals(2, neighborCount(grid, new SimpleGridNode(0, 0)));
        // Edge (0,1) → 3 neighbors
        assertEquals(3, neighborCount(grid, new SimpleGridNode(0, 1)));
        // Center (1,1) → 4 neighbors
        assertEquals(4, neighborCount(grid, new SimpleGridNode(1, 1)));
    }

    // -----------------------------------------------------------
    //  Test: 8-connectivity — corner 3, edge 5, center 8
    // -----------------------------------------------------------
    @Test
    void shouldHaveCorrectNeighborCountEightConnectivity() {
        var grid = new GridGraph(3, 3, Connectivity.EIGHT);

        assertEquals(3, neighborCount(grid, new SimpleGridNode(0, 0)));
        assertEquals(5, neighborCount(grid, new SimpleGridNode(0, 1)));
        assertEquals(8, neighborCount(grid, new SimpleGridNode(1, 1)));
    }

    // -----------------------------------------------------------
    //  Test: blocking predicate — maze walls
    //
    //    . . .
    //    . # .
    //    . . .
    //
    //  (1,1) blocked → not a vertex and not a neighbor
    // -----------------------------------------------------------
    @Test
    void shouldRespectPassabilityPredicate() {
        var grid = new GridGraph(3, 3, Connectivity.FOUR,
                node -> !(node.row() == 1 && node.col() == 1));

        // (1,1) is not passable → missing from nodes
        assertEquals(8, grid.nodes().size());
        assertFalse(grid.containsNode(new SimpleGridNode(1, 1)));

        // Neighbors of (0,1) do not include (1,1)
        var neighbors = toList(grid.neighbors(new SimpleGridNode(0, 1)));
        assertTrue(neighbors.stream().noneMatch(e -> e.target().equals(new SimpleGridNode(1, 1))));
        assertEquals(2, neighbors.size()); // (0,0) and (0,2)
    }

    // -----------------------------------------------------------
    //  Test: neighbors for a blocked node → empty
    // -----------------------------------------------------------
    @Test
    void shouldReturnEmptyNeighborsForBlockedNode() {
        var grid = new GridGraph(3, 3, Connectivity.FOUR,
                node -> !(node.row() == 1 && node.col() == 1));

        var neighbors = toList(grid.neighbors(new SimpleGridNode(1, 1)));
        assertTrue(neighbors.isEmpty());
    }

    // -----------------------------------------------------------
    //  Test: neighbors for an out-of-bounds node → empty
    // -----------------------------------------------------------
    @Test
    void shouldReturnEmptyNeighborsForOutOfBoundsNode() {
        var grid = new GridGraph(3, 3, Connectivity.FOUR);

        var neighbors = toList(grid.neighbors(new SimpleGridNode(-1, 0)));
        assertTrue(neighbors.isEmpty());

        var neighbors2 = toList(grid.neighbors(new SimpleGridNode(5, 5)));
        assertTrue(neighbors2.isEmpty());
    }

    // -----------------------------------------------------------
    //  Test: edge data contains correct Direction
    // -----------------------------------------------------------
    @Test
    void shouldHaveCorrectDirectionInEdgeData() {
        var grid = new GridGraph(3, 3, Connectivity.FOUR);

        var neighbors = toList(grid.neighbors(new SimpleGridNode(1, 1)));
        var directions = neighbors.stream()
                .map(e -> e.data().direction())
                .toList();

        assertTrue(directions.contains(GridEdge.Direction.UP));
        assertTrue(directions.contains(GridEdge.Direction.DOWN));
        assertTrue(directions.contains(GridEdge.Direction.LEFT));
        assertTrue(directions.contains(GridEdge.Direction.RIGHT));
    }

    // -----------------------------------------------------------
    //  Test: invalid constructor args
    // -----------------------------------------------------------
    @Test
    void shouldThrowForInvalidArgs() {
        assertThrows(IllegalArgumentException.class,
                () -> new GridGraph(0, 5, Connectivity.FOUR));
        assertThrows(IllegalArgumentException.class,
                () -> new GridGraph(5, -1, Connectivity.FOUR));
        assertThrows(NullPointerException.class,
                () -> new GridGraph(5, 5, null));
        assertThrows(NullPointerException.class,
                () -> new GridGraph(5, 5, Connectivity.FOUR, null));
    }

    // -----------------------------------------------------------
    //  End-to-end: Dijkstra on 3x3 grid without obstacles
    //
    //    (0,0) (0,1) (0,2)
    //    (1,0) (1,1) (1,2)
    //    (2,0) (2,1) (2,2)
    //
    //  (0,0) → (2,2): Manhattan cost = 4 (unit cost per edge)
    // -----------------------------------------------------------
    @Test
    void endToEndDijkstraOnGrid() {
        var grid = new GridGraph(3, 3, Connectivity.FOUR);

        var dijkstra = new Dijkstra<>(grid, e -> 1, IntAlgebra.INSTANCE);
        Path<GridNode, GridEdge, Integer> path = dijkstra.solve(
                new SimpleGridNode(0, 0), new SimpleGridNode(2, 2));

        assertTrue(path.isFound());
        assertEquals(4, path.totalWeight());
        assertEquals(5, path.nodes().size()); // 4 edges + 1
        assertEquals(new SimpleGridNode(0, 0), path.nodes().get(0));
        assertEquals(new SimpleGridNode(2, 2), path.nodes().get(path.nodes().size() - 1));
    }

    // -----------------------------------------------------------
    //  End-to-end: A* + Manhattan heuristic on 5x5 maze
    //
    //    .  .  .  .  .
    //    .  #  #  #  .
    //    .  .  .  #  .
    //    .  #  .  .  .
    //    .  .  .  .  .
    //
    //  Start: (0,0), Target: (4,4)
    // -----------------------------------------------------------
    @Test
    void endToEndAStarMazeWithManhattanHeuristic() {
        boolean[][] walls = {
                {false, false, false, false, false},
                {false, true,  true,  true,  false},
                {false, false, false, true,  false},
                {false, true,  false, false, false},
                {false, false, false, false, false},
        };

        var maze = new GridGraph(5, 5, Connectivity.FOUR,
                node -> !walls[node.row()][node.col()]);

        // Manhattan distance (admissible for 4-connectivity with unit cost)
        Heuristic<GridNode, Integer> manhattan = (from, to) ->
                Math.abs(from.row() - to.row()) + Math.abs(from.col() - to.col());

        var astar = new AStar<>(maze, e -> 1, IntAlgebra.INSTANCE, manhattan);
        Path<GridNode, GridEdge, Integer> path = astar.solve(
                new SimpleGridNode(0, 0), new SimpleGridNode(4, 4));

        assertTrue(path.isFound());
        assertEquals(new SimpleGridNode(0, 0), path.nodes().get(0));
        assertEquals(new SimpleGridNode(4, 4), path.nodes().get(path.nodes().size() - 1));

        // Verify path only goes through passable cells
        for (GridNode node : path.nodes()) {
            assertFalse(walls[node.row()][node.col()],
                    "Path goes through wall at " + node);
        }

        // Also run Dijkstra to compare
        var dijkstra = new Dijkstra<>(maze, e -> 1, IntAlgebra.INSTANCE);
        Path<GridNode, GridEdge, Integer> dijkstraPath = dijkstra.solve(
                new SimpleGridNode(0, 0), new SimpleGridNode(4, 4));

        assertEquals(dijkstraPath.totalWeight(), path.totalWeight());

        // A* should visit fewer nodes
        assertTrue(astar.getVisitedNodes() <= dijkstra.getVisitedNodes(),
                "A* visited " + astar.getVisitedNodes() +
                        " but Dijkstra visited " + dijkstra.getVisitedNodes());
    }

    // -----------------------------------------------------------
    //  Test: unreachable target — wall cuts off target
    //
    //    .  #
    //    #  .
    //
    //  (0,0) has no path to (1,1)
    // -----------------------------------------------------------
    @Test
    void shouldReturnNotFoundWhenTargetUnreachable() {
        var grid = new GridGraph(2, 2, Connectivity.FOUR,
                node -> !((node.row() == 0 && node.col() == 1) ||
                          (node.row() == 1 && node.col() == 0)));

        var dijkstra = new Dijkstra<>(grid, e -> 1, IntAlgebra.INSTANCE);
        Path<GridNode, GridEdge, Integer> path = dijkstra.solve(
                new SimpleGridNode(0, 0), new SimpleGridNode(1, 1));

        assertFalse(path.isFound());
    }

    // -----------------------------------------------------------
    //  Test: 8-connectivity — diagonal path
    //
    //    (0,0) → (1,1) → (2,2) in 2 steps (diagonal)
    // -----------------------------------------------------------
    @Test
    void shouldFindDiagonalPathWithEightConnectivity() {
        var grid = new GridGraph(3, 3, Connectivity.EIGHT);

        var dijkstra = new Dijkstra<>(grid, e -> 1, IntAlgebra.INSTANCE);
        Path<GridNode, GridEdge, Integer> path = dijkstra.solve(
                new SimpleGridNode(0, 0), new SimpleGridNode(2, 2));

        assertTrue(path.isFound());
        assertEquals(2, path.totalWeight()); // 2 diagonal steps
        assertEquals(3, path.nodes().size());
    }

    // -----------------------------------------------------------
    //  Test: 1x1 grid — source == target
    // -----------------------------------------------------------
    @Test
    void shouldHandleSingleCellGrid() {
        var grid = new GridGraph(1, 1, Connectivity.FOUR);

        var dijkstra = new Dijkstra<>(grid, e -> 1, IntAlgebra.INSTANCE);
        Path<GridNode, GridEdge, Integer> path = dijkstra.solve(
                new SimpleGridNode(0, 0), new SimpleGridNode(0, 0));

        assertTrue(path.isFound());
        assertEquals(0, path.totalWeight());
        assertEquals(List.of(new SimpleGridNode(0, 0)), path.nodes());
    }

    // --- utility ---

    private static long neighborCount(GridGraph grid, GridNode node) {
        return StreamSupport.stream(grid.neighbors(node).spliterator(), false).count();
    }

    private static <T> List<T> toList(Iterable<T> iterable) {
        return StreamSupport.stream(iterable.spliterator(), false).toList();
    }
}
