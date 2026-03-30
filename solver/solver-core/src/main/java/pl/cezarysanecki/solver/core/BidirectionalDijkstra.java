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
 * Bidirectional Dijkstra — search from both ends (source and target),
 * meeting in the middle (meet-in-the-middle).
 * <p>
 * Requires two graphs: forward (from source) and backward (from target).
 * For undirected graphs both can be the same object.
 * For directed graphs the backward graph should have reversed edges.
 * <p>
 * Stopping condition: when the sum of minimum distances from both heaps &gt;= the best
 * path found through the meeting point.
 * <p>
 * Complexity: ~O((V + E) log V / 2) — in practice ~2x faster than Dijkstra.
 *
 * @param <N> node type
 * @param <E> edge data type
 * @param <W> weight type
 */
public class BidirectionalDijkstra<N, E, W> implements ShortestPathSolver<N, E, W> {

    private final Graph<N, E> forwardGraph;
    private final Graph<N, E> backwardGraph;
    private final CostFunction<N, E, W> costFunction;
    private final WeightAlgebra<W> algebra;
    private int visitedNodes;

    public BidirectionalDijkstra(
            Graph<N, E> forwardGraph,
            Graph<N, E> backwardGraph,
            CostFunction<N, E, W> costFunction,
            WeightAlgebra<W> algebra
    ) {
        this.forwardGraph = Objects.requireNonNull(forwardGraph, "forwardGraph");
        this.backwardGraph = Objects.requireNonNull(backwardGraph, "backwardGraph");
        this.costFunction = Objects.requireNonNull(costFunction, "costFunction");
        this.algebra = Objects.requireNonNull(algebra, "algebra");
    }

    @Override
    public Path<N, E, W> solve(N source, N target) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");

        visitedNodes = 0;

        if (source.equals(target))
            return new SimplePath<>(List.of(source), List.of(), algebra.zero());

        if (!forwardGraph.containsNode(source))
            throw new IllegalArgumentException("Source node not in graph: " + source);
        if (!forwardGraph.containsNode(target))
            throw new IllegalArgumentException("Target node not in graph: " + target);

        // Forward search state
        Map<N, W> distFwd = new HashMap<>();
        Map<N, Edge<N, E>> prevFwd = new HashMap<>();
        distFwd.put(source, algebra.zero());

        MinHeap<N> heapFwd = new MinHeap<>((a, b) ->
                algebra.compare(distFwd.getOrDefault(a, algebra.infinity()),
                        distFwd.getOrDefault(b, algebra.infinity())));
        heapFwd.insert(source);

        // Backward search state
        Map<N, W> distBwd = new HashMap<>();
        Map<N, Edge<N, E>> prevBwd = new HashMap<>();
        distBwd.put(target, algebra.zero());

        MinHeap<N> heapBwd = new MinHeap<>((a, b) ->
                algebra.compare(distBwd.getOrDefault(a, algebra.infinity()),
                        distBwd.getOrDefault(b, algebra.infinity())));
        heapBwd.insert(target);

        // Best path through meeting point (μ)
        W bestWeight = algebra.infinity();
        N meetingNode = null;

        // Track settled (extracted) nodes per direction
        Map<N, Boolean> settledFwd = new HashMap<>();
        Map<N, Boolean> settledBwd = new HashMap<>();

        boolean finishedFwd = false;
        boolean finishedBwd = false;

        while (!finishedFwd || !finishedBwd) {
            // Termination check: sum of min-distances from both heaps >= best path found
            W fwdMin = heapFwd.isEmpty() ? algebra.infinity()
                    : distFwd.getOrDefault(heapFwd.peekMin(), algebra.infinity());
            W bwdMin = heapBwd.isEmpty() ? algebra.infinity()
                    : distBwd.getOrDefault(heapBwd.peekMin(), algebra.infinity());
            W lowerBound = algebra.add(fwdMin, bwdMin);

            if (algebra.isLessOrEqual(bestWeight, lowerBound))
                break;

            // Forward step
            if (!finishedFwd) {
                if (heapFwd.isEmpty()) {
                    finishedFwd = true;
                } else {
                    N u = heapFwd.extractMin();
                    visitedNodes++;
                    settledFwd.put(u, Boolean.TRUE);

                    W distU = distFwd.get(u);

                    for (Edge<N, E> edge : forwardGraph.neighbors(u)) {
                        N v = edge.target();
                        W edgeCost = costFunction.cost(edge);
                        W newDist = algebra.add(distU, edgeCost);

                        if (algebra.isInfinite(newDist))
                            continue;

                        W currentDist = distFwd.getOrDefault(v, algebra.infinity());
                        if (algebra.isLessThan(newDist, currentDist)) {
                            distFwd.put(v, newDist);
                            prevFwd.put(v, edge);
                            if (heapFwd.contains(v))
                                heapFwd.decreaseKey(v);
                            else if (!settledFwd.containsKey(v))
                                heapFwd.insert(v);
                        }

                        // Check if v is settled in backward search → potential meeting point
                        if (settledBwd.containsKey(v)) {
                            W throughV = algebra.add(
                                    distFwd.getOrDefault(v, algebra.infinity()),
                                    distBwd.getOrDefault(v, algebra.infinity()));
                            if (algebra.isLessThan(throughV, bestWeight)) {
                                bestWeight = throughV;
                                meetingNode = v;
                            }
                        }
                    }

                    // Also check if u itself is settled in backward
                    if (settledBwd.containsKey(u)) {
                        W throughU = algebra.add(distU, distBwd.getOrDefault(u, algebra.infinity()));
                        if (algebra.isLessThan(throughU, bestWeight)) {
                            bestWeight = throughU;
                            meetingNode = u;
                        }
                    }
                }
            }

            // Backward step
            if (!finishedBwd) {
                if (heapBwd.isEmpty()) {
                    finishedBwd = true;
                } else {
                    N u = heapBwd.extractMin();
                    visitedNodes++;
                    settledBwd.put(u, Boolean.TRUE);

                    W distU = distBwd.get(u);

                    for (Edge<N, E> edge : backwardGraph.neighbors(u)) {
                        N v = edge.target();
                        W edgeCost = costFunction.cost(edge);
                        W newDist = algebra.add(distU, edgeCost);

                        if (algebra.isInfinite(newDist))
                            continue;

                        W currentDist = distBwd.getOrDefault(v, algebra.infinity());
                        if (algebra.isLessThan(newDist, currentDist)) {
                            distBwd.put(v, newDist);
                            prevBwd.put(v, edge);
                            if (heapBwd.contains(v))
                                heapBwd.decreaseKey(v);
                            else if (!settledBwd.containsKey(v))
                                heapBwd.insert(v);
                        }

                        // Check if v is settled in forward search → potential meeting point
                        if (settledFwd.containsKey(v)) {
                            W throughV = algebra.add(
                                    distFwd.getOrDefault(v, algebra.infinity()),
                                    distBwd.getOrDefault(v, algebra.infinity()));
                            if (algebra.isLessThan(throughV, bestWeight)) {
                                bestWeight = throughV;
                                meetingNode = v;
                            }
                        }
                    }

                    // Also check if u itself is settled in forward
                    if (settledFwd.containsKey(u)) {
                        W throughU = algebra.add(distFwd.getOrDefault(u, algebra.infinity()), distU);
                        if (algebra.isLessThan(throughU, bestWeight)) {
                            bestWeight = throughU;
                            meetingNode = u;
                        }
                    }
                }
            }
        }

        if (meetingNode == null)
            return Path.notFound(algebra.infinity());

        return reconstructPath(prevFwd, prevBwd, source, target, meetingNode, bestWeight);
    }

    /**
     * Number of visited nodes (extracted from the heaps) in the last call to solve().
     */
    public int getVisitedNodes() {
        return visitedNodes;
    }

    private Path<N, E, W> reconstructPath(
            Map<N, Edge<N, E>> prevFwd,
            Map<N, Edge<N, E>> prevBwd,
            N source, N target, N meeting, W totalWeight
    ) {
        // Forward part: source → meeting
        List<Edge<N, E>> fwdEdges = new ArrayList<>();
        List<N> fwdNodes = new ArrayList<>();
        N current = meeting;
        while (!current.equals(source)) {
            Edge<N, E> edge = prevFwd.get(current);
            fwdEdges.add(edge);
            fwdNodes.add(current);
            current = edge.source();
        }
        fwdNodes.add(source);
        Collections.reverse(fwdEdges);
        Collections.reverse(fwdNodes);

        // Backward part: meeting → target
        // prevBwd maps each node to the edge used to reach it FROM target direction.
        // prevBwd.get(v) = Edge(u, v, data) from backwardGraph, meaning backward search
        // went u → v. To go toward target we follow edge.source() (= u, closer to target).
        List<Edge<N, E>> bwdEdges = new ArrayList<>();
        List<N> bwdNodes = new ArrayList<>();
        current = meeting;
        while (!current.equals(target)) {
            Edge<N, E> edge = prevBwd.get(current);
            // edge is Edge(u, current, data) in backward graph — reverse for forward direction
            bwdEdges.add(edge.reversed());
            // edge.source() is the node closer to target in backward search tree
            current = edge.source();
            bwdNodes.add(current);
        }

        // Merge: fwdNodes already contains meeting, bwdNodes starts after meeting
        List<N> allNodes = new ArrayList<>(fwdNodes);
        allNodes.addAll(bwdNodes);

        List<Edge<N, E>> allEdges = new ArrayList<>(fwdEdges);
        allEdges.addAll(bwdEdges);

        return new SimplePath<>(allNodes, allEdges, totalWeight);
    }
}
