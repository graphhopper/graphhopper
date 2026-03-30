package pl.cezarysanecki.solver.core;

import pl.cezarysanecki.solver.api.Edge;
import pl.cezarysanecki.solver.api.Path;

import java.util.List;

/**
 * Default implementation of {@link Path} — a found path.
 *
 * @param nodes       list of nodes from source to target inclusive
 * @param edges       list of edges (nodes.size() - 1 elements)
 * @param totalWeight total weight of the path
 * @param <N>         node type
 * @param <E>         edge data type
 * @param <W>         weight type
 */
public record SimplePath<N, E, W>(
        List<N> nodes,
        List<Edge<N, E>> edges,
        W totalWeight
) implements Path<N, E, W> {

    public SimplePath {
        nodes = List.copyOf(nodes);
        edges = List.copyOf(edges);
    }

    @Override
    public boolean isFound() {
        return true;
    }
}
