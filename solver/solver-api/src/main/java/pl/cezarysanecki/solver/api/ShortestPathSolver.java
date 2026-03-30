package pl.cezarysanecki.solver.api;

/**
 * Shortest-path solver.
 * Each implementation (Dijkstra, A*, Bidirectional) implements this interface.
 *
 * @param <N> node type
 * @param <E> edge data type
 * @param <W> weight type
 */
public interface ShortestPathSolver<N, E, W> {

    /**
     * Finds the shortest path from {@code source} to {@code target}.
     *
     * @return Path with isFound()==true if a path exists,
     *         Path.notFound() otherwise
     */
    Path<N, E, W> solve(N source, N target);
}
