package pl.cezarysanecki.solver.graph;

import pl.cezarysanecki.solver.api.Edge;
import pl.cezarysanecki.solver.api.Graph;
import pl.cezarysanecki.solver.graph.GridEdge.Direction;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * 2D grid graph — nodes are (row, col) pairs.
 * Edges are generated dynamically (4 or 8 neighbors).
 * Optional predicate to block cells (maze walls).
 * <p>
 * Does not store edges in memory — generates neighbors on-the-fly.
 * Memory: O(1) plus the predicate if provided.
 *
 * @see GridNode
 * @see GridEdge
 */
public class GridGraph implements Graph<GridNode, GridEdge> {

    private static final Direction[] FOUR_DIRS = {
            Direction.UP, Direction.DOWN, Direction.LEFT, Direction.RIGHT
    };

    private static final Direction[] EIGHT_DIRS = Direction.values();

    private final int rows;
    private final int cols;
    private final Direction[] directions;
    private final Predicate<GridNode> isPassable;

    /**
     * Creates a GridGraph without obstacles (all cells are passable).
     */
    public GridGraph(int rows, int cols, Connectivity connectivity) {
        this(rows, cols, connectivity, node -> true);
    }

    /**
     * Creates a GridGraph with a passability predicate (e.g. maze walls).
     */
    public GridGraph(int rows, int cols, Connectivity connectivity,
                     Predicate<GridNode> isPassable) {
        if (rows <= 0) throw new IllegalArgumentException("rows must be > 0: " + rows);
        if (cols <= 0) throw new IllegalArgumentException("cols must be > 0: " + cols);
        Objects.requireNonNull(connectivity, "connectivity");
        Objects.requireNonNull(isPassable, "isPassable");

        this.rows = rows;
        this.cols = cols;
        this.directions = (connectivity == Connectivity.FOUR) ? FOUR_DIRS : EIGHT_DIRS;
        this.isPassable = isPassable;
    }

    @Override
    public Set<GridNode> nodes() {
        Set<GridNode> result = new HashSet<>();
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                GridNode node = new GridNode(r, c);
                if (isPassable.test(node))
                    result.add(node);
            }
        }
        return result;
    }

    @Override
    public Iterable<Edge<GridNode, GridEdge>> neighbors(GridNode node) {
        if (!isInBounds(node) || !isPassable.test(node))
            return List.of();

        List<Edge<GridNode, GridEdge>> result = new ArrayList<>(directions.length);
        for (Direction dir : directions) {
            int nr = node.row() + dir.dRow();
            int nc = node.col() + dir.dCol();
            GridNode neighbor = new GridNode(nr, nc);
            if (isInBounds(neighbor) && isPassable.test(neighbor)) {
                result.add(new Edge<>(node, neighbor, new GridEdge(dir)));
            }
        }
        return result;
    }

    private boolean isInBounds(GridNode node) {
        return node.row() >= 0 && node.row() < rows
                && node.col() >= 0 && node.col() < cols;
    }

    public int rows() {
        return rows;
    }

    public int cols() {
        return cols;
    }

    /**
     * Grid connectivity type.
     */
    public enum Connectivity {
        /** 4 directions: up, down, left, right. */
        FOUR,
        /** 8 directions: 4 cardinal + diagonals. */
        EIGHT
    }
}
