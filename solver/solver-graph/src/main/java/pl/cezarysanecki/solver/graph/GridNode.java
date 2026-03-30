package pl.cezarysanecki.solver.graph;

/**
 * A vertex in a 2D grid graph — any type with (row, col) coordinates.
 * <p>
 * Implementations must correctly define {@code equals}/{@code hashCode}
 * based on (row, col) — algorithms use vertices as keys in maps.
 *
 * @see SimpleGridNode
 */
public interface GridNode {

    /** Row (0-indexed). */
    int row();

    /** Column (0-indexed). */
    int col();
}
