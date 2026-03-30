package pl.cezarysanecki.solver.api;

import java.util.Set;

/**
 * A graph with enumerable nodes — extends {@link Graph}.
 * <p>
 * Adds {@link #nodes()} — the set of all nodes.
 * Use this interface when you need to iterate over all nodes
 * (e.g. reversing edges in {@code ReversedGraph}).
 * <p>
 * Algorithms (Dijkstra, A*, Bidirectional) accept {@link Graph} —
 * they do not require {@code nodes()}.
 *
 * @param <N> node type (must have correct equals/hashCode)
 * @param <E> edge data type
 */
public interface FiniteGraph<N, E> extends Graph<N, E> {

    /**
     * Returns the set of all nodes in the graph.
     */
    Set<N> nodes();
}
