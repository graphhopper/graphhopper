package pl.cezarysanecki.solver.api;

/**
 * A heuristic that estimates a lower bound on the cost from a node to the target.
 * Used by A* to speed up the search.
 * <p>
 * Requirement (admissibility): estimate(n, target) &lt;= realCost(n, target)
 * The heuristic must never overestimate the cost.
 *
 * @param <N> node type
 * @param <W> weight type
 */
@FunctionalInterface
public interface Heuristic<N, W> {

    /**
     * Estimates a lower bound on the cost of traveling from {@code from} to {@code to}.
     */
    W estimate(N from, N to);
}
