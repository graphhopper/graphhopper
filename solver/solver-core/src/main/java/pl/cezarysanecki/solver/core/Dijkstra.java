package pl.cezarysanecki.solver.core;

import pl.cezarysanecki.solver.api.CostFunction;
import pl.cezarysanecki.solver.api.Edge;
import pl.cezarysanecki.solver.api.Graph;
import pl.cezarysanecki.solver.api.Path;
import pl.cezarysanecki.solver.api.ShortestPathSolver;
import pl.cezarysanecki.solver.api.WeightAlgebra;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Dijkstra's algorithm for finding shortest paths.
 * <p>
 * Requirements:
 * <ul>
 *   <li>Non-negative weights (CostFunction returns w &gt;= algebra.zero())</li>
 *   <li>WeightAlgebra defines a valid ordered monoid</li>
 * </ul>
 * <p>
 * Complexity: O((V + E) log V) with MinHeap.
 *
 * @param <N> node type
 * @param <E> edge data type
 * @param <W> weight type
 */
public class Dijkstra<N, E, W> implements ShortestPathSolver<N, E, W> {

    private final Graph<N, E> graph;
    private final CostFunction<N, E, W> costFunction;
    private final WeightAlgebra<W> algebra;
    private int visitedNodes;

    public Dijkstra(Graph<N, E> graph, CostFunction<N, E, W> costFunction, WeightAlgebra<W> algebra) {
        this.graph = Objects.requireNonNull(graph, "graph");
        this.costFunction = Objects.requireNonNull(costFunction, "costFunction");
        this.algebra = Objects.requireNonNull(algebra, "algebra");
    }

    @Override
    public Path<N, E, W> solve(N source, N target) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");

        visitedNodes = 0;

        // source == target → zero-cost path
        if (source.equals(target))
            return new SimplePath<>(List.of(source), List.of(), algebra.zero());

        // validation: nodes must exist in the graph
        if (!graph.containsNode(source))
            throw new IllegalArgumentException("Source node not in graph: " + source);
        if (!graph.containsNode(target))
            throw new IllegalArgumentException("Target node not in graph: " + target);

        Map<N, W> dist = new HashMap<>();
        Map<N, Edge<N, E>> prev = new HashMap<>();

        dist.put(source, algebra.zero());

        MinHeap<N> heap = new MinHeap<>((a, b) ->
                algebra.compare(dist.getOrDefault(a, algebra.infinity()),
                        dist.getOrDefault(b, algebra.infinity())));
        heap.insert(source);

        while (!heap.isEmpty()) {
            N u = heap.extractMin();
            visitedNodes++;

            // early termination
            if (u.equals(target))
                return reconstructPath(prev, source, target, dist.get(target));

            W distU = dist.getOrDefault(u, algebra.infinity());
            if (algebra.isInfinite(distU))
                break; // remaining nodes unreachable

            for (Edge<N, E> edge : graph.neighbors(u)) {
                N v = edge.target();
                W edgeCost = costFunction.cost(edge);
                W newDist = algebra.add(distU, edgeCost);

                // skip infinite (unreachable via this edge)
                if (algebra.isInfinite(newDist))
                    continue;

                W currentDist = dist.getOrDefault(v, algebra.infinity());
                if (algebra.isLessThan(newDist, currentDist)) {
                    dist.put(v, newDist);
                    prev.put(v, edge);
                    if (heap.contains(v))
                        heap.decreaseKey(v);
                    else
                        heap.insert(v);
                }
            }
        }

        return Path.notFound(algebra.infinity());
    }

    /**
     * Number of visited nodes (extracted from the heap) in the last call to solve().
     */
    public int getVisitedNodes() {
        return visitedNodes;
    }

    private Path<N, E, W> reconstructPath(Map<N, Edge<N, E>> prev, N source, N target, W totalWeight) {
        List<Edge<N, E>> edges = new ArrayList<>();
        List<N> nodes = new ArrayList<>();

        N current = target;
        while (!current.equals(source)) {
            Edge<N, E> edge = prev.get(current);
            edges.add(edge);
            nodes.add(current);
            current = edge.source();
        }
        nodes.add(source);

        Collections.reverse(edges);
        Collections.reverse(nodes);

        return new SimplePath<>(nodes, edges, totalWeight);
    }
}
