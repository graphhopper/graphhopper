package pl.cezarysanecki.solver.api;

/**
 * Computes the cost (weight) of traversing an edge.
 * This is the only place that inspects the edge data E
 * and produces a weight W.
 *
 * @param <N> node type
 * @param <E> edge data type
 * @param <W> weight type
 */
@FunctionalInterface
public interface CostFunction<N, E, W> {

    /**
     * Computes the weight of traversing the given edge.
     *
     * @param edge the edge to evaluate
     * @return traversal weight (cost) — must be non-negative
     */
    W cost(Edge<N, E> edge);
}
