/*
 * Licensed to GraphHopper GmbH under one or more contributor license agreements.
 * See the NOTICE file distributed with this work for additional information.
 * GraphHopper GmbH licenses this file to you under the Apache License, Version 2.0.
 */
package com.graphhopper.routing.matrix;

import com.graphhopper.routing.ch.ShortcutUnpacker;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.RoutingCHEdgeExplorer;
import com.graphhopper.storage.RoutingCHEdgeIterator;
import com.graphhopper.storage.RoutingCHGraph;
import com.graphhopper.util.EdgeIterator;

import java.util.*;

import static com.graphhopper.routing.matrix.TargetSetDijkstra.Failure.*;

/**
 * Experimental, serial node-based bucket CH candidate search. Each target has one exhaustive
 * reverse upward search; each source has one exhaustive forward upward search joining its labels
 * with target buckets. Virtual edges have the same level exception as upstream CH queries.
 *
 * <p>CH storage weights are deliberately not reported as elapsed time, distance, or original
 * profile cost. Each directed CH arc is unpacked once per request and all three actual metrics
 * are cached. The original objective is also used for candidate relaxation, removing query-time
 * shortcut quantization. This does not repair topology lost by float-weight witness preparation.
 * Consequently callers MUST independently certify the candidate rows against flexible routing;
 * this class is not a standalone exact original-weight shortest-path oracle.</p>
 *
 * <p>All mutable state belongs to this request. The prepared graph is borrowed and never closed.</p>
 */
public final class BucketCHMatrix {
    public record Metrics(double weight, long timeMillis, double distance) { }

    public record Row(Map<Integer, Metrics> targets, long visitedStates) {
        public Row { targets = Collections.unmodifiableMap(new LinkedHashMap<>(targets)); }
    }

    public record Statistics(long backwardSearches, long forwardSearches, long visitedStates,
                             long bucketEntries, long metricCacheEntries, long unpackedOriginalEdges,
                             long metricExtractionNanos) { }

    private record Label(int node, int edge, Metrics metrics, long sequence) { }
    private record Bucket(int target, Metrics metrics) { }
    private record DirectedArc(int edge, int adjacentNode, boolean reverse) { }

    private static final Metrics ZERO = new Metrics(0, 0, 0);
    private final RoutingCHGraph graph;
    private final Weighting weighting;
    private final int realNodes;
    private final Map<Integer, List<Bucket>> buckets = new HashMap<>();
    private final Map<DirectedArc, Metrics> metricCache = new HashMap<>();
    private final ShortcutUnpacker unpacker;
    private Metrics unpacked;
    private long deadline;
    private int maxMetricCacheEntries;
    private boolean prepared;
    private boolean failed;
    private long backwardSearches, forwardSearches, visitedStates, bucketEntries;
    private long unpackedOriginalEdges, metricExtractionNanos;

    public BucketCHMatrix(RoutingCHGraph graph) {
        this.graph = Objects.requireNonNull(graph);
        this.weighting = graph.getWeighting();
        if (graph.isEdgeBased() || graph.hasTurnCosts() || weighting.hasTurnCosts())
            throw new IllegalArgumentException("Bucket CH currently supports only node-based profiles without turn costs");
        this.realNodes = graph.getBaseGraph().getBaseGraph().getNodes();
        unpacker = new ShortcutUnpacker(graph, (edge, reverse, previous) -> {
            check();
            double weight = weighting.calcEdgeWeight(edge, reverse);
            double distance = edge.getDistance();
            long millis = weighting.calcEdgeMillis(edge, reverse);
            validate(weight, millis, distance);
            unpacked = add(unpacked, new Metrics(weight, millis, distance));
            unpackedOriginalEdges++;
        }, false);
    }

    /** Builds all target buckets. The budget includes every settled reverse-search state. */
    public void prepare(Set<Integer> targetNodes, long deadlineNanos, int maxVisitedStates) {
        if (prepared || failed) throw new IllegalStateException("Prepare a new bucket search once per request");
        Objects.requireNonNull(targetNodes);
        if (maxVisitedStates < 1) throw new IllegalArgumentException("maxVisitedStates must be positive");
        deadline = deadlineNanos;
        // An arc cache can otherwise grow with examined degree even when few states are
        // settled. Keep its lifetime entry count bounded by the initial request budget.
        maxMetricCacheEntries = maxVisitedStates;
        check();
        List<Integer> ordered = new ArrayList<>(targetNodes);
        Collections.sort(ordered);
        ordered.forEach(this::requireNode);
        long before = visitedStates;
        try {
            for (int target : ordered) {
                backwardSearches++;
                Map<Integer, Label> labels = explore(target, true, maxVisitedStates, before, null);
                for (Label label : labels.values()) {
                    check();
                    buckets.computeIfAbsent(label.node(), ignored -> new ArrayList<>())
                            .add(new Bucket(target, label.metrics()));
                    bucketEntries++;
                }
            }
            prepared = true;
        } catch (RuntimeException e) {
            failed = true;
            throw e;
        }
    }

    /** One forward upward search. A missing target is unreachable within this CH candidate topology. */
    public Row search(int source, int maxVisitedStates) {
        if (!prepared || failed) throw new IllegalStateException("A successful bucket preparation is required");
        requireNode(source);
        if (maxVisitedStates < 1) throw new IllegalArgumentException("maxVisitedStates must be positive");
        check();
        if (buckets.isEmpty()) return new Row(Map.of(), 0);
        long before = visitedStates;
        Map<Integer, Metrics> result = new LinkedHashMap<>();
        try {
            forwardSearches++;
            explore(source, false, maxVisitedStates, before, result);
            check();
            return new Row(result, visitedStates - before);
        } catch (RuntimeException e) {
            failed = true;
            throw e;
        }
    }

    public Statistics statistics() {
        return new Statistics(backwardSearches, forwardSearches, visitedStates, bucketEntries,
                metricCache.size(), unpackedOriginalEdges, metricExtractionNanos);
    }

    private Map<Integer, Label> explore(int start, boolean reverse, int maxVisited, long visitedBefore,
                                       Map<Integer, Metrics> result) {
        RoutingCHEdgeExplorer explorer = reverse ? graph.createInEdgeExplorer() : graph.createOutEdgeExplorer();
        PriorityQueue<Label> queue = new PriorityQueue<>(Comparator
                .comparingDouble((Label label) -> label.metrics().weight())
                .thenComparingInt(Label::node).thenComparingLong(Label::sequence));
        Map<Integer, Label> best = new HashMap<>();
        Label initial = new Label(start, EdgeIterator.NO_EDGE, ZERO, 0);
        best.put(start, initial);
        queue.add(initial);
        long sequence = 1;
        while (!queue.isEmpty()) {
            check();
            Label label = queue.poll();
            if (best.get(label.node()) != label) continue;
            if (visitedStates - visitedBefore >= maxVisited)
                throw new TargetSetDijkstra.SearchException(SEARCH_LIMIT_EXCEEDED,
                        "Settled-state limit exhausted during CH bucket search");
            visitedStates++;
            if (result != null) {
                for (Bucket bucket : buckets.getOrDefault(label.node(), List.of())) {
                    check();
                    Metrics candidate = add(label.metrics(), bucket.metrics());
                    Metrics previous = result.get(bucket.target());
                    if (previous == null || candidate.weight() < previous.weight())
                        result.put(bucket.target(), candidate);
                }
            }
            RoutingCHEdgeIterator edge = explorer.setBaseNode(label.node());
            while (edge.next()) {
                check();
                if (edge.getEdge() == label.edge() || !upward(edge.getBaseNode(), edge.getAdjNode())) continue;
                double storedWeight = edge.getWeight(reverse);
                if (storedWeight == Double.POSITIVE_INFINITY) continue;
                if (!Double.isFinite(storedWeight) || storedWeight < 0)
                    throw new TargetSetDijkstra.SearchException(INVALID_COST, "Invalid stored CH arc weight");
                Metrics arc = metrics(edge.getEdge(), edge.getAdjNode(), reverse);
                Metrics candidate = add(label.metrics(), arc);
                Label previous = best.get(edge.getAdjNode());
                if (previous != null && candidate.weight() >= previous.metrics().weight()) continue;
                Label next = new Label(edge.getAdjNode(), edge.getEdge(), candidate, sequence++);
                best.put(next.node(), next);
                queue.add(next);
            }
        }
        return best;
    }

    private boolean upward(int base, int adjacent) {
        // Exactly the upstream virtual-node level exception: a virtual node is not an ordinary
        // contracted vertex, and a same-edge virtual chain can contain more than one such node.
        return base >= realNodes || adjacent >= realNodes || graph.getLevel(base) <= graph.getLevel(adjacent);
    }

    private Metrics metrics(int edge, int adjacent, boolean reverse) {
        DirectedArc key = new DirectedArc(edge, adjacent, reverse);
        Metrics cached = metricCache.get(key);
        if (cached != null) return cached;
        if (metricCache.size() >= maxMetricCacheEntries)
            throw new TargetSetDijkstra.SearchException(SEARCH_LIMIT_EXCEEDED,
                    "CH unpacked-metric cache entry limit exhausted: " + maxMetricCacheEntries);
        long started = System.nanoTime();
        unpacked = ZERO;
        if (reverse) unpacker.visitOriginalEdgesBwd(edge, adjacent, false, EdgeIterator.NO_EDGE);
        else unpacker.visitOriginalEdgesFwd(edge, adjacent, false, EdgeIterator.NO_EDGE);
        Metrics result = unpacked;
        metricExtractionNanos += System.nanoTime() - started;
        metricCache.put(key, result);
        return result;
    }

    private static Metrics add(Metrics first, Metrics second) {
        double weight = first.weight() + second.weight();
        double distance = first.distance() + second.distance();
        long millis;
        try {
            millis = Math.addExact(first.timeMillis(), second.timeMillis());
        } catch (ArithmeticException e) {
            throw new TargetSetDijkstra.SearchException(METRIC_OVERFLOW, "CH path duration overflow");
        }
        if (!Double.isFinite(weight) || !Double.isFinite(distance))
            throw new TargetSetDijkstra.SearchException(METRIC_OVERFLOW, "CH path metric overflow");
        return new Metrics(weight, millis, distance);
    }

    private static void validate(double weight, long millis, double distance) {
        if (!Double.isFinite(weight) || weight < 0 || millis < 0 || !Double.isFinite(distance) || distance < 0)
            throw new TargetSetDijkstra.SearchException(INVALID_COST, "Invalid unpacked CH original-edge metric");
    }

    private void requireNode(int node) {
        if (node < 0 || node >= graph.getNodes()) throw new IllegalArgumentException("Invalid graph node: " + node);
    }

    private void check() {
        if (Thread.currentThread().isInterrupted())
            throw new TargetSetDijkstra.SearchException(CANCELLED, "CH bucket search interrupted");
        if (deadline != Long.MAX_VALUE && System.nanoTime() - deadline >= 0)
            throw new TargetSetDijkstra.SearchException(DEADLINE_EXCEEDED, "CH bucket search deadline exceeded");
    }
}
