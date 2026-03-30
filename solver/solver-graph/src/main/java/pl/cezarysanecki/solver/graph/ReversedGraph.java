package pl.cezarysanecki.solver.graph;

import pl.cezarysanecki.solver.api.Edge;
import pl.cezarysanecki.solver.api.Graph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Graph view with reversed edges.
 * <p>
 * For a directed graph: if the original graph has an edge A→B,
 * then ReversedGraph has an edge B→A.
 * <p>
 * Requires a one-time scan of the original graph (in the constructor)
 * to build the reversed adjacency list.
 * <p>
 * Needed by {@link pl.cezarysanecki.solver.core.BidirectionalDijkstra}
 * on directed graphs.
 *
 * @param <N> node type
 * @param <E> edge data type
 */
public class ReversedGraph<N, E> implements Graph<N, E> {

    private final Graph<N, E> original;
    private final Map<N, List<Edge<N, E>>> reversedAdjacency;

    public ReversedGraph(Graph<N, E> original) {
        Objects.requireNonNull(original, "original");
        this.original = original;
        this.reversedAdjacency = buildReversed(original);
    }

    @Override
    public Set<N> nodes() {
        return original.nodes();
    }

    @Override
    public Iterable<Edge<N, E>> neighbors(N node) {
        return reversedAdjacency.getOrDefault(node, List.of());
    }

    private static <N, E> Map<N, List<Edge<N, E>>> buildReversed(Graph<N, E> graph) {
        Map<N, List<Edge<N, E>>> reversed = new HashMap<>();
        for (N node : graph.nodes()) {
            reversed.putIfAbsent(node, new ArrayList<>());
            for (Edge<N, E> edge : graph.neighbors(node)) {
                reversed.computeIfAbsent(edge.target(), k -> new ArrayList<>())
                        .add(edge.reversed());
            }
        }
        return reversed;
    }
}
