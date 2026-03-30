package pl.cezarysanecki.solver.api;

import java.util.Set;

/**
 * Read-only graph — topology + edge data.
 * No W parameter — weight is computed by CostFunction, not stored.
 *
 * @param <N> node type (must have proper equals/hashCode)
 * @param <E> edge data type
 */
public interface Graph<N, E> {

    /**
     * Returns the set of all nodes in the graph.
     */
    Set<N> nodes();

    /**
     * Returns the neighbors (outgoing edges) of the given node.
     * Returns an empty iterable for nodes not present in the graph.
     */
    Iterable<Edge<N, E>> neighbors(N node);

    /**
     * Checks whether the graph contains the given node.
     */
    default boolean containsNode(N node) {
        return nodes().contains(node);
    }
}
