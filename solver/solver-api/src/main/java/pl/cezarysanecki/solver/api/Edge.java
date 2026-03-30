package pl.cezarysanecki.solver.api;

/**
 * A graph edge connecting two nodes with associated data.
 *
 * @param source source node
 * @param target target node
 * @param data   edge data (opaque to the solver — read by CostFunction)
 * @param <N>    node type
 * @param <E>    edge data type
 */
public record Edge<N, E>(N source, N target, E data) {

    /**
     * Returns an edge with swapped source/target (for undirected graphs).
     */
    public Edge<N, E> reversed() {
        return new Edge<>(target, source, data);
    }
}
