package pl.cezarysanecki.solver.graph.heuristics;

import pl.cezarysanecki.solver.api.Heuristic;
import pl.cezarysanecki.solver.graph.GridNode;

/**
 * Heuristic factory for {@link GridNode} — for use with A* on {@code GridGraph}.
 * <p>
 * Each method returns a {@code Heuristic<GridNode, Double>} compatible
 * with {@link pl.cezarysanecki.solver.api.DoubleAlgebra}.
 * <p>
 * Admissibility (never overestimates):
 * <ul>
 *   <li>{@link #manhattan()} — admissible for 4-connectivity with cost ≥ 1.0 per edge</li>
 *   <li>{@link #chebyshev()} — admissible for 8-connectivity with cost ≥ 1.0 per edge</li>
 *   <li>{@link #octile()} — admissible for 8-connectivity with cost 1.0 (straight) / √2 (diagonal)</li>
 *   <li>{@link #euclidean()} — admissible always (weakest, but universal)</li>
 * </ul>
 */
public final class GridHeuristics {

    private static final double SQRT2 = Math.sqrt(2.0);
    private static final double SQRT2_MINUS_1 = SQRT2 - 1.0;

    private GridHeuristics() {
        // utility class
    }

    /**
     * Manhattan distance: {@code |Δrow| + |Δcol|}.
     * <p>
     * Admissible for 4-connectivity with cost ≥ 1.0 per edge.
     * Overestimates on 8-connectivity (a diagonal move costs 1, but Manhattan counts 2).
     */
    public static Heuristic<GridNode, Double> manhattan() {
        return (from, to) -> {
            int dr = Math.abs(from.row() - to.row());
            int dc = Math.abs(from.col() - to.col());
            return (double) (dr + dc);
        };
    }

    /**
     * Chebyshev distance: {@code max(|Δrow|, |Δcol|)}.
     * <p>
     * Admissible for 8-connectivity with cost ≥ 1.0 per edge
     * (diagonal moves cost the same as straight moves).
     */
    public static Heuristic<GridNode, Double> chebyshev() {
        return (from, to) -> {
            int dr = Math.abs(from.row() - to.row());
            int dc = Math.abs(from.col() - to.col());
            return (double) Math.max(dr, dc);
        };
    }

    /**
     * Octile distance: {@code max(|Δrow|, |Δcol|) + (√2 − 1) × min(|Δrow|, |Δcol|)}.
     * <p>
     * Admissible for 8-connectivity with cost 1.0 for straight moves and √2 for diagonal.
     * Exactly models the optimal movement cost on a grid with this cost model.
     */
    public static Heuristic<GridNode, Double> octile() {
        return (from, to) -> {
            int dr = Math.abs(from.row() - to.row());
            int dc = Math.abs(from.col() - to.col());
            return Math.max(dr, dc) + SQRT2_MINUS_1 * Math.min(dr, dc);
        };
    }

    /**
     * Euclidean distance: {@code √(Δrow² + Δcol²)}.
     * <p>
     * Admissible always (straight line ≤ any path on the grid).
     * Weakest of the heuristics — prunes less than Manhattan/Octile,
     * but works with any connectivity.
     */
    public static Heuristic<GridNode, Double> euclidean() {
        return (from, to) -> {
            int dr = from.row() - to.row();
            int dc = from.col() - to.col();
            return Math.sqrt((double) dr * dr + (double) dc * dc);
        };
    }
}
