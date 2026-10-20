package com.graphhopper.routing.matrix;

import com.graphhopper.routing.Dijkstra;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.ch.ShortcutUnpacker;
import com.graphhopper.routing.querygraph.VirtualEdgeIteratorState;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.*;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.FetchMode;
import com.graphhopper.util.PointList;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/** Diagnostic-only reconstruction from the frozen production bucket algorithm's actual labels. */
public final class BucketCHCandidateTrace {
    public record Audit(boolean proved, Map<String, Object> summary, Map<String, Object> witness) { }
    private record Arc(int edge, int adjacent, boolean reverse, int parent, BucketCHMatrix.Metrics parentMetrics,
                       BucketCHMatrix.Metrics currentMetrics, BucketCHMatrix.Metrics arcMetrics) { }
    private record Search(Map<Integer, Object> labels, Map<Integer, BucketCHMatrix.Metrics> result, List<Integer> order) { }
    private record Description(BucketCHMatrix.Metrics metrics, BigDecimal exactWeight, BigDecimal exactDistance,
                               List<Integer> physicalKeys, List<Map<String, Object>> terms, boolean legal) { }

    private final RoutingCHGraph graph;
    private final Graph overlay;
    private final Weighting weighting;
    private final BucketCHMatrix bucket;
    private final Method explore, arcMetrics, add;
    private final Map<Integer, List<Object>> targetBuckets;
    private final Map<Integer, Search> forwardCache = new HashMap<>();
    private final Map<Integer, Map<Integer, Object>> backwardCache = new HashMap<>();
    private final Map<Integer, List<Integer>> copyFamilyCache = new HashMap<>();
    private List<Integer> recording;

    @SuppressWarnings("unchecked")
    public BucketCHCandidateTrace(RoutingCHGraph queryCH, Set<Integer> targets) {
        try {
            graph = queryCH;
            overlay = graph.getBaseGraph();
            weighting = overlay.wrapWeighting(graph.getWeighting());
            require(!graph.isEdgeBased() && !graph.hasTurnCosts(), "Only node-based candidate diagnostics are supported");
            // The only wrapper effect is recording the production explorer's actual settled-node
            // order. Global sorting is insufficient in the presence of newly discovered zero arcs.
            RoutingCHGraph observed = new RoutingCHGraph() {
                public int getNodes() { return graph.getNodes(); }
                public int getEdges() { return graph.getEdges(); }
                public int getShortcuts() { return graph.getShortcuts(); }
                public RoutingCHEdgeExplorer createInEdgeExplorer() { return graph.createInEdgeExplorer(); }
                public RoutingCHEdgeExplorer createOutEdgeExplorer() {
                    RoutingCHEdgeExplorer original = graph.createOutEdgeExplorer();
                    return node -> {
                        if (recording != null) recording.add(node);
                        return original.setBaseNode(node);
                    };
                }
                public RoutingCHEdgeIteratorState getEdgeIteratorState(int edge, int adjacent) { return graph.getEdgeIteratorState(edge, adjacent); }
                public int getLevel(int node) { return graph.getLevel(node); }
                public double getTurnWeight(int in, int via, int out) { return graph.getTurnWeight(in, via, out); }
                public Graph getBaseGraph() { return graph.getBaseGraph(); }
                public boolean hasTurnCosts() { return graph.hasTurnCosts(); }
                public boolean isEdgeBased() { return graph.isEdgeBased(); }
                public Weighting getWeighting() { return graph.getWeighting(); }
                public void close() { throw new UnsupportedOperationException("Borrowed graph must never be closed by diagnostics"); }
            };
            bucket = new BucketCHMatrix(observed);
            bucket.prepare(targets, Long.MAX_VALUE, Integer.MAX_VALUE);
            explore = BucketCHMatrix.class.getDeclaredMethod("explore", int.class, boolean.class, int.class, long.class, Map.class);
            arcMetrics = BucketCHMatrix.class.getDeclaredMethod("metrics", int.class, int.class, boolean.class);
            add = BucketCHMatrix.class.getDeclaredMethod("add", BucketCHMatrix.Metrics.class, BucketCHMatrix.Metrics.class);
            Field buckets = BucketCHMatrix.class.getDeclaredField("buckets");
            for (var method : List.of(explore, arcMetrics, add)) method.setAccessible(true);
            buckets.setAccessible(true);
            targetBuckets = (Map<Integer, List<Object>>) buckets.get(bucket);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Frozen bucket diagnostic reflection failed", error);
        }
    }

    public Audit trace(int source, int target, Map<String, ? extends Number> observedRaw) {
        Map<String, Object> witness = new LinkedHashMap<>(); List<String> failures = new ArrayList<>();
        witness.put("method", "actual_frozen_BucketCHMatrix_private_labels_with_recorded_settlement_order_and_upstream_unpacker");
        witness.put("source_node", source); witness.put("target_node", target); witness.put("observed_raw", observedRaw);
        witness.put("source_coordinate", coordinate(source)); witness.put("target_coordinate", coordinate(target));
        witness.put("failures", failures);
        String classification = "UNEXPLAINED";
        try {
            Search search = forwardCache.computeIfAbsent(source, this::forward);
            BucketCHMatrix.Metrics raw = search.result.get(target);
            require(raw != null && matches(raw, observedRaw), "Fresh actual production raw row does not exactly bind observed candidate scalars");
            BucketCHMatrix.Metrics best = null; int meeting = -1; Object selectedBackwardBucket = null;
            for (int node : search.order) {
                Object label = search.labels.get(node);
                require(label != null, "Settled node has no final production label");
                for (Object targetBucket : targetBuckets.getOrDefault(node, List.of())) {
                    if (integer(targetBucket, "target") != target) continue;
                    BucketCHMatrix.Metrics joined = plus(metrics(label), metrics(targetBucket));
                    if (best == null || joined.weight() < best.weight()) {
                        best = joined; meeting = node; selectedBackwardBucket = targetBucket;
                    }
                }
            }
            require(best != null && best.equals(raw), "Actual target-bucket join does not exactly reconstruct raw row");
            Map<Integer, Object> backward = backwardCache.computeIfAbsent(target, this::backward);
            require(metrics(backward.get(meeting)).equals(metrics(selectedBackwardBucket)), "Actual backward labels differ from the retained target bucket");
            List<Arc> fwd = arcs(search.labels, meeting, source, false), bwd = arcs(backward, meeting, target, true);
            List<EdgeIteratorState> original = new ArrayList<>(); List<Map<String, Object>> selectedArcs = new ArrayList<>();
            ShortcutUnpacker unpacker = new ShortcutUnpacker(graph, (edge, reverse, previous) -> original.add(edge.detach(reverse)), false);
            for (Arc arc : fwd) {
                selectedArcs.add(arcMap(arc));
                unpacker.visitOriginalEdgesFwd(arc.edge, arc.adjacent, false, EdgeIterator.NO_EDGE);
            }
            for (Arc arc : bwd) {
                selectedArcs.add(arcMap(arc));
                unpacker.visitOriginalEdgesBwd(arc.edge, arc.adjacent, true, EdgeIterator.NO_EDGE);
            }
            Description actual = describe(original, source, target);
            List<EdgeIteratorState> normalized = new ArrayList<>(); List<Map<String, Object>> splits = new ArrayList<>();
            for (EdgeIteratorState leaf : original) {
                List<EdgeIteratorState> represented = overlayRepresentation(leaf);
                normalized.addAll(represented);
                if (represented.size() != 1 || represented.get(0).getEdgeKey() != leaf.getEdgeKey()) {
                    Description whole = describe(List.of(leaf), leaf.getBaseNode(), leaf.getAdjNode());
                    Description pieces = describe(represented, leaf.getBaseNode(), leaf.getAdjNode());
                    Map<String, Object> split = new LinkedHashMap<>();
                    split.put("original_leaf", whole.terms.get(0)); split.put("shared_overlay_pieces", pieces.terms);
                    split.put("same_actual_upstream_copy_family_flags_and_unit_metrics", true);
                    split.put("exact_original_minus_shared_weight", whole.exactWeight.subtract(pieces.exactWeight).toPlainString());
                    split.put("exact_original_minus_shared_distance_m", whole.exactDistance.subtract(pieces.exactDistance).toPlainString());
                    split.put("original_minus_shared_time_ms", whole.metrics.timeMillis() - pieces.metrics.timeMillis());
                    splits.add(split);
                }
            }
            Description represented = describe(normalized, source, target);
            Path independent = new Dijkstra(overlay, weighting, TraversalMode.NODE_BASED).calcPath(source, target);
            require(independent.isFound(), "Independent shared-overlay Dijkstra disagrees on reachability");
            Description oracle = describe(independent.calcEdges(), source, target);
            witness.put("meeting_node", meeting); witness.put("raw_join_metrics", map(raw));
            witness.put("forward_meeting_metrics", map(metrics(search.labels.get(meeting))));
            witness.put("backward_meeting_metrics", map(metrics(selectedBackwardBucket)));
            witness.put("selected_ch_arcs", selectedArcs);
            witness.put("actual_original_candidate_path", description(actual));
            witness.put("same_candidate_path_represented_on_complete_overlay", description(represented));
            witness.put("independent_shared_dijkstra_path", description(oracle));
            witness.put("independent_shared_dijkstra_reported_metrics", Map.of("weights", independent.getWeight(), "times_ms", independent.getTime(), "distances", independent.getDistance()));
            witness.put("actual_removed_original_leaf_split_deltas", splits);
            require(actual.legal && represented.legal && oracle.legal, "Actual candidate/represented/oracle path is illegal or has wrong endpoints");
            require(represented.terms.stream().allMatch(term -> Boolean.TRUE.equals(term.get("present_in_shared_overlay_explorer"))),
                    "A represented candidate edge is absent from the actual shared-overlay explorer");
            witness.put("all_represented_edges_present_in_shared_overlay", true);
            require(raw.timeMillis() == actual.metrics.timeMillis(), "Actual selected original edges do not reconstruct candidate time exactly");
            witness.put("raw_weight_regrouping", regrouping(raw.weight(), actual.exactWeight, original.size()));
            witness.put("raw_distance_regrouping", regrouping(raw.distance(), actual.exactDistance, original.size()));
            boolean samePhysical = represented.physicalKeys.equals(oracle.physicalKeys);
            boolean sameExactObjective = represented.exactWeight.compareTo(oracle.exactWeight) == 0;
            boolean sameExactMetrics = sameExactObjective && represented.exactDistance.compareTo(oracle.exactDistance) == 0
                    && represented.metrics.timeMillis() == oracle.metrics.timeMillis();
            witness.put("represented_candidate_same_physical_route_as_oracle", samePhysical);
            witness.put("represented_candidate_exact_objective_equals_oracle", sameExactObjective);
            witness.put("represented_candidate_exact_all_metrics_equal_oracle", sameExactMetrics);
            witness.put("exact_represented_candidate_minus_oracle_objective", represented.exactWeight.subtract(oracle.exactWeight).toPlainString());
            witness.put("exact_original_candidate_minus_represented_objective", actual.exactWeight.subtract(represented.exactWeight).toPlainString());
            classification = samePhysical && sameExactMetrics ? "ACTUAL_CANDIDATE_SAME_PHYSICAL_ROUTE_WITH_EXPLICIT_QUERY_SPLIT_METRICS"
                    : "ACTUAL_CANDIDATE_DIFFERENT_PATH_OR_OBJECTIVE_REJECTED_BY_CERTIFICATE";
            witness.put("candidate_original_objective_optimality_claimed", false);
            witness.put("actual_candidate_path_captured", true);
            witness.put("interpretation", "The raw production label/tree selection and original-edge expansion are captured directly. Removed original edges inside shortcuts retain stored whole-edge metrics; the separately listed actual overlay virtual chains use geometry lengths and per-piece time rounding. This is candidate accounting, never permission to expose an uncertified result or a global tolerance.");
        } catch (RuntimeException error) {
            failures.add(error.getClass().getSimpleName() + ": " + error.getMessage());
        }
        boolean proved = failures.isEmpty();
        witness.put("proved", proved); witness.put("classification", classification);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("proved", proved); summary.put("classification", classification); summary.put("source_node", source); summary.put("target_node", target);
        summary.put("failures", failures); summary.put("candidate_original_objective_optimality_claimed", false);
        for (String field : List.of("meeting_node", "actual_candidate_path_captured", "represented_candidate_same_physical_route_as_oracle",
                "represented_candidate_exact_objective_equals_oracle", "represented_candidate_exact_all_metrics_equal_oracle"))
            if (witness.containsKey(field)) summary.put(field, witness.get(field));
        return new Audit(proved, summary, witness);
    }

    @SuppressWarnings("unchecked") private Search forward(int source) {
        try {
            recording = new ArrayList<>();
            BucketCHMatrix.Row ordinary = bucket.search(source, Integer.MAX_VALUE);
            List<Integer> firstOrder = List.copyOf(recording); recording = new ArrayList<>();
            Map<Integer, BucketCHMatrix.Metrics> result = new LinkedHashMap<>();
            Map<Integer, Object> labels = (Map<Integer, Object>) explore.invoke(bucket, source, false, Integer.MAX_VALUE,
                    bucket.statistics().visitedStates(), result);
            List<Integer> order = List.copyOf(recording); recording = null;
            require(firstOrder.equals(order), "Repeated actual forward settlement order changed");
            require(ordinary.targets().equals(result), "Reflected production forward exploration differs from its public row");
            require(new HashSet<>(order).size() == order.size(), "A node settled more than once; diagnostic final-parent assumption is not applicable");
            return new Search(labels, result, order);
        } catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
    }

    @SuppressWarnings("unchecked") private Map<Integer, Object> backward(int target) {
        try {
            return (Map<Integer, Object>) explore.invoke(bucket, target, true, Integer.MAX_VALUE,
                    bucket.statistics().visitedStates(), null);
        } catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
    }

    private List<Arc> arcs(Map<Integer, Object> labels, int node, int root, boolean reverse) {
        try {
            List<Arc> result = new ArrayList<>(); Set<Integer> visited = new HashSet<>();
            while (node != root) {
                require(visited.add(node), "Cycle in selected actual bucket parents");
                Object label = labels.get(node); require(label != null, "Missing actual bucket parent label");
                int edge = integer(label, "edge"); require(EdgeIterator.Edge.isValid(edge), "Parent chain reached a different root");
                RoutingCHEdgeIteratorState state = graph.getEdgeIteratorState(edge, node);
                int parent = state.getBaseNode(); Object previous = labels.get(parent);
                require(previous != null, "Actual selected CH edge has no parent label");
                BucketCHMatrix.Metrics transition = (BucketCHMatrix.Metrics) arcMetrics.invoke(bucket, edge, node, reverse);
                require(plus(metrics(previous), transition).equals(metrics(label)), "Final labels do not reproduce the actual selected predecessor recurrence");
                result.add(new Arc(edge, node, reverse, parent, metrics(previous), metrics(label), transition)); node = parent;
            }
            require(metrics(labels.get(root)).equals(new BucketCHMatrix.Metrics(0, 0, 0)), "Actual search root is not zero");
            if (!reverse) Collections.reverse(result);
            return result;
        } catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
    }

    private List<EdgeIteratorState> overlayRepresentation(EdgeIteratorState original) {
        if (original instanceof VirtualEdgeIteratorState || present(original)) return List.of(original);
        int node = original.getBaseNode(), target = original.getAdjNode();
        List<Integer> desiredFamily = family(original.getEdgeKey());
        BucketCHMatrix.Metrics originalUnit = unitMetrics(original);
        List<EdgeIteratorState> result = new ArrayList<>(); Set<Integer> visited = new HashSet<>();
        while (node != target) {
            require(visited.add(node), "Cycle in actual replacement virtual chain");
            List<EdgeIteratorState> candidates = new ArrayList<>();
            EdgeIterator edges = overlay.createEdgeExplorer().setBaseNode(node);
            while (edges.next()) {
                EdgeIteratorState edge = edges.detach(false);
                if (edge instanceof VirtualEdgeIteratorState virtual
                        && family(virtual.getOriginalEdgeKey()).equals(desiredFamily))
                    candidates.add(edge);
            }
            require(candidates.size() == 1, "Removed original edge has ambiguous/missing actual directed overlay replacement at node " + node);
            EdgeIteratorState next = candidates.get(0);
            require(next.getFlags().equals(original.getFlags()), "Actual virtual replacement flags differ from the selected full original leaf");
            require(unitMetrics(next).equals(originalUnit), "Actual virtual replacement unit weight/time differs from the selected full original leaf");
            result.add(next); node = next.getAdjNode();
        }
        require(!result.isEmpty(), "Removed original leaf has empty replacement chain");
        return result;
    }

    private boolean present(EdgeIteratorState selected) {
        EdgeIterator edges = overlay.createEdgeExplorer().setBaseNode(selected.getBaseNode());
        while (edges.next()) if (edges.getEdgeKey() == selected.getEdgeKey() && edges.getAdjNode() == selected.getAdjNode()) return true;
        return false;
    }

    private Description describe(List<EdgeIteratorState> edges, int source, int target) {
        List<Map<String, Object>> terms = new ArrayList<>(); List<Integer> physical = new ArrayList<>();
        double weight = 0, distance = 0; long time = 0; int node = source;
        BigDecimal exactWeight = BigDecimal.ZERO, exactDistance = BigDecimal.ZERO; boolean legal = true;
        for (EdgeIteratorState edge : edges) {
            double cost = weighting.calcEdgeWeight(edge, false); long millis = weighting.calcEdgeMillis(edge, false);
            require(Double.isFinite(cost) && cost >= 0 && millis >= 0 && Double.isFinite(edge.getDistance()) && edge.getDistance() >= 0, "Invalid selected edge contribution");
            legal &= node == edge.getBaseNode();
            int original = edge instanceof VirtualEdgeIteratorState virtual ? virtual.getOriginalEdgeKey() : edge.getEdgeKey();
            List<Integer> actualFamily = family(original);
            int canonical = actualFamily.get(0);
            if (physical.isEmpty() || physical.get(physical.size()-1) != canonical) physical.add(canonical);
            Map<String, Object> term = new LinkedHashMap<>();
            term.put("edge_key", edge.getEdgeKey()); term.put("original_edge_key", original); term.put("copy_canonical_edge_key", canonical);
            term.put("upstream_copy_family_directed_keys", actualFamily);
            term.put("from_node", edge.getBaseNode()); term.put("to_node", edge.getAdjNode()); term.put("edge_weight", cost); term.put("turn_weight", 0d);
            term.put("from_coordinate", coordinate(edge.getBaseNode())); term.put("to_coordinate", coordinate(edge.getAdjNode()));
            term.put("edge_time_ms", millis); term.put("turn_time_ms", 0L); term.put("distance_m", edge.getDistance());
            term.put("virtual", edge instanceof VirtualEdgeIteratorState); term.put("present_in_shared_overlay_explorer", present(edge));
            term.put("encoded_flags", edge.getFlags().toString());
            term.put("encoded_flags_equal_original", edge.getFlags().equals(overlay.getBaseGraph().getEdgeIteratorStateForKey(original).getFlags()));
            term.put("normalized_distance_one_metrics", map(unitMetrics(edge)));
            term.put("geometry_sha256", geometryHash(edge));
            terms.add(term); weight += cost; distance += edge.getDistance(); time = Math.addExact(time, millis);
            exactWeight = exactWeight.add(new BigDecimal(cost)); exactDistance = exactDistance.add(new BigDecimal(edge.getDistance()));
            node = edge.getAdjNode();
        }
        legal &= node == target;
        return new Description(new BucketCHMatrix.Metrics(weight, time, distance), exactWeight, exactDistance, physical, terms, legal);
    }

    private List<Integer> family(int originalKey) {
        return copyFamilyCache.computeIfAbsent(originalKey, key -> {
            EdgeIteratorState original = overlay.getBaseGraph().getEdgeIteratorStateForKey(key);
            Set<Integer> members = new TreeSet<>();
            overlay.getBaseGraph().forEdgeAndCopiesOfEdge(overlay.getBaseGraph().createEdgeExplorer(), original, copy -> {
                if (copy.getBaseNode() == original.getBaseNode() && copy.getAdjNode() == original.getAdjNode()) members.add(copy.getEdgeKey());
            });
            require(members.contains(key), "Actual upstream copy-family traversal omitted its original edge");
            return List.copyOf(members);
        });
    }

    private BucketCHMatrix.Metrics unitMetrics(EdgeIteratorState original) {
        EdgeIteratorState unit = (EdgeIteratorState) Proxy.newProxyInstance(EdgeIteratorState.class.getClassLoader(),
                new Class<?>[]{EdgeIteratorState.class}, (proxy, method, arguments) -> {
                    if (method.getName().equals("getDistance") && method.getParameterCount() == 0) return 1d;
                    try { return method.invoke(original, arguments); }
                    catch (InvocationTargetException error) { throw error.getCause(); }
                });
        double cost = weighting.calcEdgeWeight(unit, false); long time = weighting.calcEdgeMillis(unit, false);
        require(Double.isFinite(cost) && cost >= 0 && time >= 0, "Invalid actual normalized distance-one metric");
        return new BucketCHMatrix.Metrics(cost, time, 1);
    }

    private List<Double> coordinate(int node) {
        return List.of(overlay.getNodeAccess().getLon(node), overlay.getNodeAccess().getLat(node));
    }

    private static String geometryHash(EdgeIteratorState edge) {
        try {
            PointList points = edge.fetchWayGeometry(FetchMode.ALL);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(ByteBuffer.allocate(8).putInt(points.size()).putInt(points.is3D() ? 3 : 2).array());
            for (int index = 0; index < points.size(); index++) {
                digest.update(ByteBuffer.allocate(16).putDouble(points.getLat(index)).putDouble(points.getLon(index)).array());
                if (points.is3D()) digest.update(ByteBuffer.allocate(8).putDouble(points.getEle(index)).array());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }

    private static Map<String, Object> regrouping(double reported, BigDecimal exact, int leaves) {
        int operations = Math.max(1, 2 * leaves + 1); double u = Math.scalb(1d, -53);
        double gamma = Math.nextUp(operations * u / (1 - operations * u));
        BigDecimal bound = new BigDecimal(gamma).multiply(exact);
        BigDecimal residual = new BigDecimal(reported).subtract(exact);
        require(residual.abs().compareTo(bound) <= 0, "Raw selected-path metric exceeds its source-derived nonnegative addition regrouping bound");
        return Map.of("reported", reported, "exact_selected_terms_sum", exact.toPlainString(), "exact_reported_minus_terms", residual.toPlainString(),
                "addition_operation_bound", operations, "operation_bound_derivation", "n actual nonnegative leaf occurrences; cached arc sums plus source/target label sums and the meeting join use a binary grouping of those leaves, at most n-1 nontrivial additions; 2*n+1 also bounds explicit zero-root additions without relying on their exactness",
                "unit_roundoff", u, "derived_absolute_bound", bound.toPlainString(), "proved", true);
    }
    private static Map<String, Object> description(Description path) {
        return Map.of("metrics", map(path.metrics), "exact_objective_terms_sum", path.exactWeight.toPlainString(), "exact_distance_terms_sum", path.exactDistance.toPlainString(),
                "legal_and_requested_endpoints", path.legal, "copy_canonical_directed_edge_keys", path.physicalKeys, "edge_contributions", path.terms);
    }
    private static Map<String, Object> arcMap(Arc arc) {
        return Map.of("ch_edge", arc.edge, "adjacent", arc.adjacent, "parent_node", arc.parent, "reverse_search", arc.reverse,
                "parent_metrics", map(arc.parentMetrics), "current_metrics", map(arc.currentMetrics), "cached_actual_arc_metrics", map(arc.arcMetrics));
    }
    private static Map<String, Object> map(BucketCHMatrix.Metrics metrics) { return Map.of("weights", metrics.weight(), "times_ms", metrics.timeMillis(), "distances", metrics.distance()); }
    private static boolean matches(BucketCHMatrix.Metrics metrics, Map<String, ? extends Number> observed) {
        return observed.get("weights") != null && observed.get("times_ms") != null && observed.get("distances") != null
                && Double.compare(metrics.weight(), observed.get("weights").doubleValue()) == 0 && metrics.timeMillis() == observed.get("times_ms").longValue()
                && Double.compare(metrics.distance(), observed.get("distances").doubleValue()) == 0;
    }
    private BucketCHMatrix.Metrics plus(BucketCHMatrix.Metrics first, BucketCHMatrix.Metrics second) {
        try { return (BucketCHMatrix.Metrics) add.invoke(null, first, second); }
        catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
    }
    private static Object get(Object object, String field) {
        require(object != null, "Missing actual record: " + field);
        try { Method accessor = object.getClass().getDeclaredMethod(field); accessor.setAccessible(true); return accessor.invoke(object); }
        catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
    }
    private static int integer(Object object, String field) { return ((Number) get(object, field)).intValue(); }
    private static BucketCHMatrix.Metrics metrics(Object object) { return (BucketCHMatrix.Metrics) get(object, "metrics"); }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
