package pl.cezarysanecki.solver.api;

import java.util.List;

/**
 * Result of a shortest-path search.
 *
 * @param <N> node type
 * @param <E> edge data type
 * @param <W> weight type
 */
public interface Path<N, E, W> {

    /** List of nodes on the path (from source to target, inclusive). */
    List<N> nodes();

    /** List of edges on the path (nodes.size() - 1 elements). */
    List<Edge<N, E>> edges();

    /** Total weight of the path. */
    W totalWeight();

    /** Was a path found? false = target unreachable. */
    boolean isFound();

    /** Empty path — target unreachable. */
    static <N, E, W> Path<N, E, W> notFound(W infiniteWeight) {
        return new Path<>() {
            @Override
            public List<N> nodes() {
                return List.of();
            }

            @Override
            public List<Edge<N, E>> edges() {
                return List.of();
            }

            @Override
            public W totalWeight() {
                return infiniteWeight;
            }

            @Override
            public boolean isFound() {
                return false;
            }
        };
    }
}
