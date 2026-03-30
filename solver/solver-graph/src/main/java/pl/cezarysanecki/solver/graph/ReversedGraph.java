package pl.cezarysanecki.solver.graph;

import pl.cezarysanecki.solver.api.Edge;
import pl.cezarysanecki.solver.api.FiniteGraph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A graph view with reversed edges.
 * <p>
 * For a directed graph: if the original graph has an edge A→B,
 * then ReversedGraph has an edge B→A.
 * <p>
 * Requires a one-time scan of the original graph (in the constructor)
 * to build the reversed adjacency list.
 * Therefore it accepts {@link FiniteGraph} — it needs {@code nodes()} for iteration.
 * <p>
 * Needed for {@link pl.cezarysanecki.solver.core.BidirectionalDijkstra}
 * on directed graphs.
 *
 * @param <N> node type
 * @param <E> edge data type
 */
public class ReversedGraph<N, E> implements FiniteGraph<N, E> {

    private final FiniteGraph<N, E> original;
    private final Map<N, List<Edge<N, E>>> reversedAdjacency;

    public ReversedGraph(FiniteGraph<N, E> original) {
        Objects.requireNonNull(original, "original");
        this.original = original;
        this.reversedAdjacency = buildReversed(original);
    }

    @Override
    public Set<N> nodes() {
        return original.nodes();
    }

    @Override
    public boolean containsNode(N node) {
        return original.containsNode(node);
    }

    @Override
    public Iterable<Edge<N, E>> neighbors(N node) {
        return reversedAdjacency.getOrDefault(node, List.of());
    }

    private static <N, E> Map<N, List<Edge<N, E>>> buildReversed(FiniteGraph<N, E> graph) {
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
