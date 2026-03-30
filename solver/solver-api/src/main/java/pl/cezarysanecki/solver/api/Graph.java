package pl.cezarysanecki.solver.api;

/**
 * Read-only graph — topology + edge data.
 * Has no W parameter — weight is computed by CostFunction, not stored.
 * <p>
 * Minimal contract: {@link #neighbors(Object)} + {@link #containsNode(Object)}.
 * Does not require enumeration of all nodes — that is what {@link FiniteGraph} does.
 *
 * @param <N> node type (must have correct equals/hashCode)
 * @param <E> edge data type
 */
public interface Graph<N, E> {

    /**
     * Returns the neighbors (outgoing edges) of the given node.
     * For a node not present in the graph, returns an empty iterable.
     */
    Iterable<Edge<N, E>> neighbors(N node);

    /**
     * Checks whether the graph contains the given node.
     */
    boolean containsNode(N node);
}
