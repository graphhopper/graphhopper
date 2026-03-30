package pl.cezarysanecki.solver.graph;

import pl.cezarysanecki.solver.api.Edge;
import pl.cezarysanecki.solver.api.FiniteGraph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Adjacency-list based graph.
 * Immutable once built — built via {@link Builder}.
 * <p>
 * Internally stores {@code Map<N, List<Edge<N, E>>>} with unmodifiable views.
 *
 * @param <N> node type
 * @param <E> edge data type
 */
public class AdjacencyListGraph<N, E> implements FiniteGraph<N, E> {

    private final Map<N, List<Edge<N, E>>> adjacency;

    private AdjacencyListGraph(Map<N, List<Edge<N, E>>> adjacency) {
        this.adjacency = adjacency;
    }

    @Override
    public Set<N> nodes() {
        return adjacency.keySet();
    }

    @Override
    public boolean containsNode(N node) {
        return adjacency.containsKey(node);
    }

    @Override
    public Iterable<Edge<N, E>> neighbors(N node) {
        return adjacency.getOrDefault(node, List.of());
    }

    /**
     * Creates a new Builder.
     */
    public static <N, E> Builder<N, E> builder() {
        return new Builder<>();
    }

    /**
     * Builder for constructing an immutable AdjacencyListGraph.
     *
     * @param <N> node type
     * @param <E> edge data type
     */
    public static class Builder<N, E> {

        private final Map<N, List<Edge<N, E>>> adjacency = new HashMap<>();

        private Builder() {}

        /**
         * Adds a node (optional — nodes are added automatically when calling addEdge).
         */
        public Builder<N, E> addNode(N node) {
            Objects.requireNonNull(node, "node");
            adjacency.computeIfAbsent(node, k -> new ArrayList<>());
            return this;
        }

        /**
         * Adds a directed edge from source to target.
         */
        public Builder<N, E> addEdge(N source, N target, E data) {
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(target, "target");
            adjacency.computeIfAbsent(source, k -> new ArrayList<>())
                    .add(new Edge<>(source, target, data));
            adjacency.computeIfAbsent(target, k -> new ArrayList<>());
            return this;
        }

        /**
         * Adds an undirected edge (two directed edges: source→target and target→source).
         */
        public Builder<N, E> addUndirectedEdge(N source, N target, E data) {
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(target, "target");
            adjacency.computeIfAbsent(source, k -> new ArrayList<>())
                    .add(new Edge<>(source, target, data));
            adjacency.computeIfAbsent(target, k -> new ArrayList<>())
                    .add(new Edge<>(target, source, data));
            return this;
        }

        /**
         * Builds an immutable graph. The Builder should not be used after calling build().
         */
        public AdjacencyListGraph<N, E> build() {
            Map<N, List<Edge<N, E>>> immutable = new HashMap<>();
            for (var entry : adjacency.entrySet()) {
                immutable.put(entry.getKey(), Collections.unmodifiableList(new ArrayList<>(entry.getValue())));
            }
            return new AdjacencyListGraph<>(Collections.unmodifiableMap(immutable));
        }
    }
}
