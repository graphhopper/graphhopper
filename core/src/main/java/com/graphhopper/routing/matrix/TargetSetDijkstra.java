/*
 * Licensed to GraphHopper GmbH under one or more contributor license agreements.
 * See the NOTICE file distributed with this work for additional information.
 * GraphHopper GmbH licenses this file to you under the Apache License, Version 2.0.
 */
package com.graphhopper.routing.matrix;

import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.GHUtility;

import java.util.*;

/**
 * A forward, target-aware Dijkstra search. Create one instance per source search and pass an
 * already graph-wrapped weighting (in particular, do not wrap a QueryGraph weighting twice).
 * Missing targets in a successful result are unreachable; an incomplete search always throws.
 */
public final class TargetSetDijkstra {
    public record Metrics(double weight, long timeMillis, double distance) {
    }

    public record Result(Map<Integer, Metrics> targets, long visitedStates) {
        public Result {
            targets = Collections.unmodifiableMap(new LinkedHashMap<>(targets));
        }
    }

    /** Directed path evidence, available only when diagnostics have been explicitly enabled. */
    public record Step(int edge, int edgeKey, int fromNode, int toNode) {
    }

    public enum Failure {
        DEADLINE_EXCEEDED, CANCELLED, SEARCH_LIMIT_EXCEEDED, INVALID_COST, METRIC_OVERFLOW
    }

    public static final class SearchException extends RuntimeException {
        private final Failure reason;

        public SearchException(Failure reason, String message) {
            super(message);
            this.reason = reason;
        }

        public Failure reason() {
            return reason;
        }
    }

    private final Graph graph;
    private final Weighting weighting;
    private final TraversalMode mode;
    private final EdgeExplorer explorer;
    private boolean diagnosticsEnabled;
    private boolean alreadyRun;
    private Map<Integer, Label> diagnosticTargets;

    public TargetSetDijkstra(Graph graph, Weighting wrappedWeighting, TraversalMode mode) {
        this.graph = Objects.requireNonNull(graph);
        this.weighting = Objects.requireNonNull(wrappedWeighting);
        this.mode = Objects.requireNonNull(mode);
        if (weighting.hasTurnCosts() && !mode.isEdgeBased())
            throw new IllegalArgumentException("Turn costs require directed edge-based traversal");
        this.explorer = graph.createEdgeExplorer();
    }

    /** Retains predecessor labels for tests or bounded diagnostic searches, never normal matrices. */
    public TargetSetDijkstra withDiagnostics() {
        if (alreadyRun)
            throw new IllegalStateException("Enable diagnostics before searching");
        diagnosticsEnabled = true;
        return this;
    }

    /**
     * @param deadlineNanos absolute System.nanoTime deadline, or Long.MAX_VALUE for no deadline
     * @param maxVisited maximum number of settled states, including the initial source state
     */
    public Result search(int source, Set<Integer> targets, long deadlineNanos, int maxVisited) {
        if (alreadyRun)
            throw new IllegalStateException("Create a new instance per source search");
        alreadyRun = true;
        Objects.requireNonNull(targets);
        requireNode(source);
        Set<Integer> remaining = new HashSet<>(targets);
        remaining.forEach(this::requireNode);
        if (maxVisited < 1)
            throw new IllegalArgumentException("maxVisited must be positive");
        checkCancellation(deadlineNanos);

        Map<Integer, Metrics> result = new LinkedHashMap<>();
        Map<Integer, Label> paths = diagnosticsEnabled ? new HashMap<>() : null;
        if (remaining.isEmpty()) {
            diagnosticTargets = paths;
            return new Result(result, 0);
        }

        // Labels never change their ordering key after insertion. A replacement leaves an old
        // label in the heap; identity against the best-state map recognizes these stale labels.
        PriorityQueue<Label> heap = new PriorityQueue<>(Comparator
                .comparingDouble((Label l) -> l.weight)
                .thenComparingInt(l -> l.state)
                .thenComparingLong(l -> l.sequence));
        Map<Integer, Label> best = new HashMap<>();
        Label start = new Label(source, EdgeIterator.NO_EDGE, -1, source, 0, 0, 0, 0, null);
        heap.add(start);
        if (!mode.isEdgeBased())
            best.put(source, start);
        long sequence = 1;
        long visited = 0;

        while (!heap.isEmpty()) {
            checkCancellation(deadlineNanos);
            Label current = heap.poll();
            if (current != start && best.get(current.state) != current)
                continue;
            if (visited >= maxVisited)
                throw new SearchException(Failure.SEARCH_LIMIT_EXCEEDED,
                        "Settled-state limit exceeded: " + maxVisited);
            visited++;
            // First settled arrival is optimal, even when several incoming edge states exist.
            if (remaining.remove(current.node)) {
                result.put(current.node, new Metrics(current.weight, current.time, current.distance));
                if (paths != null)
                    paths.put(current.node, current);
                if (remaining.isEmpty())
                    break;
            }

            EdgeIterator edge = explorer.setBaseNode(current.node);
            while (edge.next()) {
                checkCancellation(deadlineNanos);
                // Match AbstractRoutingAlgorithm: edge-based traversal delegates u-turn
                // permission to the weighting, while node-based searches omit immediate reversal.
                if (!mode.isEdgeBased() && edge.getEdge() == current.edge)
                    continue;
                double transitionWeight = GHUtility.calcWeightWithTurnWeight(weighting, edge, false, current.edge);
                if (transitionWeight == Double.POSITIVE_INFINITY)
                    continue;
                if (!Double.isFinite(transitionWeight) || transitionWeight < 0)
                    throw new SearchException(Failure.INVALID_COST,
                            "Nonfinite or negative accepted transition weight on edge " + edge.getEdge());
                double nextWeight = current.weight + transitionWeight;
                if (!Double.isFinite(nextWeight))
                    throw new SearchException(Failure.METRIC_OVERFLOW, "Accumulated route weight overflow");
                int state = mode.createTraversalId(edge, false);
                Label previous = best.get(state);
                if (previous != null && nextWeight >= previous.weight)
                    continue;

                double edgeDistance = edge.getDistance();
                if (!Double.isFinite(edgeDistance) || edgeDistance < 0)
                    throw new SearchException(Failure.INVALID_COST, "Invalid edge distance on edge " + edge.getEdge());
                double nextDistance = current.distance + edgeDistance;
                if (!Double.isFinite(nextDistance))
                    throw new SearchException(Failure.METRIC_OVERFLOW, "Accumulated route distance overflow");
                long transitionTime = GHUtility.calcMillisWithTurnMillis(weighting, edge, false, current.edge);
                // GHUtility adds two nonnegative long durations internally. An overflow there
                // is negative, and must be caught before the further accumulation below.
                if (transitionTime < 0)
                    throw new SearchException(Failure.METRIC_OVERFLOW, "Negative or overflowing edge/turn duration");
                if (transitionTime == Long.MAX_VALUE) {
                    // The upstream helper treats MAX_VALUE as an infinity sentinel and can
                    // return before adding the other component. With finite accepted weight,
                    // verify that the represented nonnegative duration actually fits in long.
                    long edgeTime = weighting.calcEdgeMillis(edge, false);
                    long turnTime = current.edge == EdgeIterator.NO_EDGE ? 0
                            : weighting.calcTurnMillis(current.edge, current.node, edge.getEdge());
                    if (edgeTime < 0 || turnTime < 0)
                        throw new SearchException(Failure.INVALID_COST, "Negative edge/turn duration");
                    try {
                        transitionTime = Math.addExact(edgeTime, turnTime);
                    } catch (ArithmeticException e) {
                        throw new SearchException(Failure.METRIC_OVERFLOW, "Edge/turn duration overflow");
                    }
                }
                long nextTime;
                try {
                    nextTime = Math.addExact(current.time, transitionTime);
                } catch (ArithmeticException e) {
                    throw new SearchException(Failure.METRIC_OVERFLOW, "Accumulated route duration overflow");
                }
                Label next = new Label(edge.getAdjNode(), edge.getEdge(), edge.getEdgeKey(), state,
                        nextWeight, nextTime, nextDistance, sequence++, diagnosticsEnabled ? current : null);
                best.put(state, next);
                heap.add(next);
            }
        }
        checkCancellation(deadlineNanos);
        diagnosticTargets = paths;
        return new Result(result, visited);
    }

    /** Empty for a zero-length or unreachable path; consult Result.targets for reachability. */
    public List<Step> diagnosticPath(int target) {
        if (!diagnosticsEnabled || diagnosticTargets == null)
            throw new IllegalStateException("A successful search with diagnostics is required");
        List<Step> result = new ArrayList<>();
        for (Label label = diagnosticTargets.get(target); label != null && label.parent != null; label = label.parent)
            result.add(new Step(label.edge, label.edgeKey, label.parent.node, label.node));
        Collections.reverse(result);
        return Collections.unmodifiableList(result);
    }

    private void requireNode(int node) {
        if (node < 0 || node >= graph.getNodes())
            throw new IllegalArgumentException("Invalid graph node: " + node);
    }

    private static void checkCancellation(long deadline) {
        if (Thread.currentThread().isInterrupted())
            throw new SearchException(Failure.CANCELLED, "Matrix search interrupted");
        if (deadline != Long.MAX_VALUE && System.nanoTime() - deadline >= 0)
            throw new SearchException(Failure.DEADLINE_EXCEEDED, "Matrix search deadline exceeded");
    }

    private record Label(int node, int edge, int edgeKey, int state, double weight, long time,
                         double distance, long sequence, Label parent) {
    }
}
