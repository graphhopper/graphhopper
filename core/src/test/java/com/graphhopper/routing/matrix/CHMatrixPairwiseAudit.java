package com.graphhopper.routing.matrix;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.ResponsePath;
import com.graphhopper.config.Profile;
import com.graphhopper.routing.AbstractBidirCHAlgo;
import com.graphhopper.routing.Dijkstra;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.SPTEntry;
import com.graphhopper.routing.ch.CHRoutingAlgorithmFactory;
import com.graphhopper.routing.ch.EdgeBasedCHBidirPathExtractor;
import com.graphhopper.routing.ch.NodeBasedCHBidirPathExtractor;
import com.graphhopper.routing.ev.Subnetwork;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.querygraph.QueryRoutingCHGraph;
import com.graphhopper.routing.querygraph.VirtualEdgeIteratorState;
import com.graphhopper.routing.util.DefaultSnapFilter;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.RoutingCHGraph;
import com.graphhopper.storage.index.Snap;
import com.graphhopper.util.*;
import com.graphhopper.util.shapes.GHPoint;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.util.*;

/** Fresh, fail-closed path evidence for ordinary CH versus a request's complete QueryGraph. */
public final class CHMatrixPairwiseAudit {
    public record Audit(boolean proved, Map<String, Object> summary, Map<String, Object> witness) { }
    private record Metrics(double weight, long millis, double distance) { }
    private record Description(Metrics metrics, BigDecimal exactWeight, BigDecimal exactDistance,
                               List<Integer> canonicalKeys, List<Map<String, Object>> edges, boolean legal) { }
    private static final Map<GraphHopper, MatrixProvenance> IDENTITIES = Collections.synchronizedMap(new WeakHashMap<>());

    private CHMatrixPairwiseAudit() { }

    public static Audit audit(GraphHopper hopper, String profileName, GHPoint from, GHPoint to,
                              Map<String, ? extends Number> observedCH,
                              Map<String, ? extends Number> observedFlexible,
                              QueryGraph combined, int combinedFrom, int combinedTo) {
        Map<String, Object> witness = new LinkedHashMap<>();
        List<String> failures = new ArrayList<>();
        witness.put("method", "fresh_default_CH_SPT_quantization_plus_independent_pair_and_shared_Dijkstra");
        witness.put("profile", profileName);
        witness.put("from", List.of(from.lon, from.lat)); witness.put("to", List.of(to.lon, to.lat));
        witness.put("observed_ch", observedCH); witness.put("observed_flexible", observedFlexible);
        witness.put("combined_source_node", combinedFrom); witness.put("combined_target_node", combinedTo);
        witness.put("failures", failures);
        String classification = "UNEXPLAINED";
        try {
            require(combined.getBaseGraph() == hopper.getBaseGraph(), "combined QueryGraph uses a different base graph");
            Profile profile = Objects.requireNonNull(hopper.getProfile(profileName), "unknown configured profile");
            Weighting raw = hopper.createWeighting(profile, new PMap());
            TraversalMode mode = profile.hasTurnCosts() ? TraversalMode.EDGE_BASED : TraversalMode.NODE_BASED;
            MatrixProvenance identity;
            synchronized (IDENTITIES) { identity = IDENTITIES.computeIfAbsent(hopper, MatrixProvenance::new); }
            witness.put("graph_fingerprint", identity.graphFingerprint);
            witness.put("profile_fingerprint", identity.profileFingerprint(profileName));
            witness.put("build_commit", identity.buildCommit);
            witness.put("profile_definition", profile.toString()); witness.put("weighting_class", raw.getClass().getName());
            DefaultSnapFilter filter = new DefaultSnapFilter(raw,
                    hopper.getEncodingManager().getBooleanEncodedValue(Subnetwork.key(profileName)));
            Snap start = hopper.getLocationIndex().findClosest(from.lat, from.lon, filter);
            Snap finish = hopper.getLocationIndex().findClosest(to.lat, to.lon, filter);
            require(start.isValid() && finish.isValid(), "independent snap is invalid");
            require(combinedFrom >= 0 && combinedFrom < combined.getNodes() && combinedTo >= 0 && combinedTo < combined.getNodes(),
                    "combined endpoint node outside graph");
            require(samePosition(start, combined, combinedFrom) && samePosition(finish, combined, combinedTo),
                    "shared endpoints differ from independent snapped coordinates");
            witness.put("independent_snaps", List.of(snap(start), snap(finish)));
            QueryGraph pair = QueryGraph.create(hopper.getBaseGraph(), start, finish);
            witness.put("pair_source_node", start.getClosestNode()); witness.put("pair_target_node", finish.getClosestNode());
            Weighting pairWeighting = pair.wrapWeighting(raw), sharedWeighting = combined.wrapWeighting(raw);
            RoutingCHGraph prepared = Objects.requireNonNull(hopper.getCHGraphs().get(profileName), "no prepared CH profile");
            RoutingCHGraph queryCH = new QueryRoutingCHGraph(prepared, pair);
            AbstractBidirCHAlgo algorithm = (AbstractBidirCHAlgo) new CHRoutingAlgorithmFactory(queryCH).createAlgo(new PMap());
            SPTEntry[] selectedEntries = new SPTEntry[2]; double[] selectedWeight = new double[1];
            algorithm.setPathExtractorSupplier(() -> (forward, backward, weight) -> {
                selectedEntries[0] = forward; selectedEntries[1] = backward; selectedWeight[0] = weight;
                return (prepared.isEdgeBased() ? new EdgeBasedCHBidirPathExtractor(queryCH) : new NodeBasedCHBidirPathExtractor(queryCH))
                        .extract(forward, backward, weight);
            });
            Path selected = algorithm.calcPath(start.getClosestNode(), finish.getClosestNode());
            Path plainPair = new Dijkstra(pair, pairWeighting, mode).calcPath(start.getClosestNode(), finish.getClosestNode());
            Path plainShared = new Dijkstra(combined, sharedWeighting, mode).calcPath(combinedFrom, combinedTo);
            TargetSetDijkstra sharedSearch = new TargetSetDijkstra(combined, sharedWeighting, mode).withDiagnostics();
            TargetSetDijkstra.Result sharedResult = sharedSearch.search(combinedFrom, Set.of(combinedTo), Long.MAX_VALUE, Integer.MAX_VALUE);
            TargetSetDijkstra.Metrics sharedMetric = sharedResult.targets().get(combinedTo);
            GHResponse route = hopper.route(new GHRequest(List.of(from, to)).setProfile(profileName).setSnapPreventions(List.of())
                    .putHint("instructions", false).putHint("calc_points", false));
            witness.put("default_ch_algorithm", algorithm.getName()); witness.put("ordinary_route_debug_info", route.getDebugInfo());
            witness.put("selected_ch_found", selected.isFound()); witness.put("plain_pair_found", plainPair.isFound());
            witness.put("plain_shared_found", plainShared.isFound()); witness.put("shared_search_found", sharedMetric != null);
            if (!selected.isFound() || !plainPair.isFound() || !plainShared.isFound() || sharedMetric == null || route.hasErrors()) {
                witness.put("ordinary_route_errors", route.getErrors().toString());
                require(!selected.isFound() && !plainPair.isFound() && !plainShared.isFound() && sharedMetric == null
                                && route.hasErrors() && missing(observedCH) && missing(observedFlexible),
                        "reachability disagreement or incomplete observed null mask");
                require(route.getErrors().stream().allMatch(error -> error instanceof com.graphhopper.util.exceptions.ConnectionNotFoundException),
                        "ordinary route failure is not a proved disconnection");
                classification = "INDEPENDENTLY_PROVEN_UNREACHABLE";
            } else {
                String expectedAlgorithm = prepared.isEdgeBased() ? "astarbi|ch|edge_based|no_sod" : "dijkstrabi|ch";
                require(algorithm.getName().equals(expectedAlgorithm) && route.getDebugInfo().contains(expectedAlgorithm),
                        "ordinary routing did not select the captured default CH algorithm");
                ResponsePath response = route.getBest();
                Metrics routed = new Metrics(response.getRouteWeight(), response.getTime(), response.getDistance());
                Metrics extracted = new Metrics(selected.getWeight(), selected.getTime(), selected.getDistance());
                Metrics reportedShared = new Metrics(sharedMetric.weight(), sharedMetric.timeMillis(), sharedMetric.distance());
                require(matchesObserved(routed, observedCH), "fresh ordinary CH response does not exactly match observed scalars");
                require(same(routed, extracted), "direct default CH extraction differs from ordinary route");
                require(matchesObserved(reportedShared, observedFlexible), "fresh shared search does not exactly match observed flexible scalars");
                witness.put("actual_observed_scalars_exactly_bound", true);
                Description chPath = describe(pair, pairWeighting, selected.calcEdges(), start.getClosestNode(), finish.getClosestNode());
                Description pairPath = describe(pair, pairWeighting, plainPair.calcEdges(), start.getClosestNode(), finish.getClosestNode());
                Description sharedPlainPath = describe(combined, sharedWeighting, plainShared.calcEdges(), combinedFrom, combinedTo);
                Description sharedPath = describe(combined, sharedWeighting, sharedSearch.diagnosticPath(combinedTo).stream()
                        .map(step -> combined.getEdgeIteratorStateForKey(step.edgeKey())).toList(), combinedFrom, combinedTo);
                witness.put("ch_path", description(chPath)); witness.put("pair_dijkstra_path", description(pairPath));
                witness.put("shared_dijkstra_path", description(sharedPlainPath)); witness.put("shared_selected_path", description(sharedPath));
                CHWeightAudit.Audit precision = CHWeightAudit.analyze(queryCH, selectedEntries[0], selectedEntries[1], selectedWeight[0]);
                witness.put("quantization_audit", precision.details());
                witness.put("exact_ch_minus_pair_dijkstra_objective", chPath.exactWeight.subtract(pairPath.exactWeight).toPlainString());
                witness.put("exact_shared_selected_minus_shared_dijkstra_objective", sharedPath.exactWeight.subtract(sharedPlainPath.exactWeight).toPlainString());
                require(chPath.legal && pairPath.legal && sharedPlainPath.legal && sharedPath.legal, "an expanded directed path is illegal or has wrong endpoints");
                require(chPath.metrics.millis == selected.getTime() && closeAccumulation(chPath.metrics.distance, selected.getDistance(), chPath.edges.size()),
                        "expanded CH path does not reconstruct extracted time/distance");
                require(same(sharedPath.metrics, reportedShared), "actual shared path contributions do not exactly reconstruct observed flexible metrics");
                require(pairPath.exactWeight.compareTo(chPath.exactWeight) == 0,
                        "selected CH actual objective differs from independent pair Dijkstra exact edge/turn sum");
                require(sharedPlainPath.exactWeight.compareTo(sharedPath.exactWeight) == 0,
                        "shared selected actual objective differs from independent shared Dijkstra exact edge/turn sum");
                require(precision.proved(), "fresh selected CH shortcut/storage arithmetic proof failed");
                require(precision.details().get("exact_selected_expanded_terms_sum") instanceof String
                                && chPath.exactWeight.compareTo(new BigDecimal((String) precision.details().get("exact_selected_expanded_terms_sum"))) == 0,
                        "SPT shortcut expansion and actual selected directed path objective differ");
                boolean identicalPhysical = pairPath.canonicalKeys.equals(sharedPath.canonicalKeys);
                boolean equalObjective = pairPath.exactWeight.compareTo(sharedPath.exactWeight) == 0;
                boolean sameStaticContributions = identicalPhysical && sameUnitCostsAndTurns(pairPath, sharedPath);
                witness.put("pair_vs_shared_same_copy_canonical_physical_route", identicalPhysical);
                witness.put("pair_vs_shared_exact_equal_objective", equalObjective);
                witness.put("pair_vs_shared_same_normalized_static_edge_costs_and_turns", sameStaticContributions);
                witness.put("pair_vs_shared_per_original_edge_contributions", deltas(pairPath, sharedPath));
                witness.put("ch_pair_independent_exact_optimal_objective", true);
                witness.put("shared_independent_exact_optimal_objective", true);
                witness.put("actual_observed_scalars_exactly_bound", true);
                require(equalObjective || sameStaticContributions,
                        "pair/shared differences are neither an exact optimal tie nor the same physical route with explicit split contributions");
                classification = sameStaticContributions ? "ACTUAL_CH_QUANTIZATION_AND_SAME_PATH_QUERY_SPLITS"
                        : "ACTUAL_CH_QUANTIZATION_AND_EXACT_OPTIMAL_ALTERNATIVE_PATHS";
            }
        } catch (RuntimeException | AssertionError error) {
            failures.add(error.getClass().getSimpleName() + ": " + error.getMessage());
        }
        boolean proved = failures.isEmpty();
        witness.put("classification", classification); witness.put("proved", proved);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("profile", profileName); summary.put("from", List.of(from.lon, from.lat)); summary.put("to", List.of(to.lon, to.lat));
        summary.put("proved", proved); summary.put("classification", classification); summary.put("failures", failures);
        for (String field : List.of("graph_fingerprint", "profile_fingerprint", "build_commit", "default_ch_algorithm",
                "actual_observed_scalars_exactly_bound", "ch_pair_independent_exact_optimal_objective", "shared_independent_exact_optimal_objective"))
            if (witness.containsKey(field)) summary.put(field, witness.get(field));
        return new Audit(proved, summary, witness);
    }

    private static Description describe(Graph graph, Weighting weighting, List<EdgeIteratorState> edges, int source, int target) {
        List<Map<String, Object>> evidence = new ArrayList<>(); List<Integer> keys = new ArrayList<>();
        double weight = 0, distance = 0; long millis = 0; int previous = EdgeIterator.NO_EDGE, node = source;
        BigDecimal exactWeight = BigDecimal.ZERO, exactDistance = BigDecimal.ZERO;
        boolean legal = true;
        for (EdgeIteratorState edge : edges) {
            double ew = weighting.calcEdgeWeight(edge, false), tw = previous == EdgeIterator.NO_EDGE ? 0
                    : weighting.calcTurnWeight(previous, edge.getBaseNode(), edge.getEdge());
            long et = weighting.calcEdgeMillis(edge, false), tt = previous == EdgeIterator.NO_EDGE ? 0
                    : weighting.calcTurnMillis(previous, edge.getBaseNode(), edge.getEdge());
            legal &= edge.getBaseNode() == node && finite(ew) && finite(tw) && finite(edge.getDistance()) && et >= 0 && tt >= 0;
            require(finite(ew) && finite(tw) && finite(edge.getDistance()), "nonfinite actual path contribution");
            int original = edge instanceof VirtualEdgeIteratorState virtual ? virtual.getOriginalEdgeKey() : edge.getEdgeKey();
            int canonical = MatrixPrecisionDiagnostics.canonicalDirectedEdgeKey(graph.getBaseGraph(), original);
            if (keys.isEmpty() || keys.get(keys.size() - 1) != canonical) keys.add(canonical);
            Set<Integer> family = new TreeSet<>();
            EdgeIteratorState originalEdge = graph.getBaseGraph().getEdgeIteratorStateForKey(original);
            graph.getBaseGraph().forEdgeAndCopiesOfEdge(graph.getBaseGraph().createEdgeExplorer(), originalEdge, copy -> {
                if (copy.getBaseNode() == originalEdge.getBaseNode() && copy.getAdjNode() == originalEdge.getAdjNode()) family.add(copy.getEdgeKey());
            });
            Map<String, Object> term = new LinkedHashMap<>();
            term.put("edge_key", edge.getEdgeKey()); term.put("original_edge_key", original); term.put("copy_canonical_edge_key", canonical);
            term.put("upstream_copy_family_directed_keys", family); term.put("from_node", edge.getBaseNode()); term.put("to_node", edge.getAdjNode());
            term.put("edge_weight", ew); term.put("turn_weight", tw); term.put("edge_time_ms", et); term.put("turn_time_ms", tt);
            term.put("distance_m", edge.getDistance()); term.put("virtual", edge instanceof VirtualEdgeIteratorState);
            term.put("geometry_distance_m", DistancePlaneProjection.DIST_PLANE.calcDistance(edge.fetchWayGeometry(FetchMode.ALL)));
            term.put("stored_original_distance_m", originalEdge.getDistance());
            double normalizedWeight = unitWeight(weighting, edge);
            require(finite(normalizedWeight), "nonfinite normalized actual-edge weight");
            term.put("normalized_distance_one_weight", normalizedWeight);
            term.put("encoded_flags_equal_original", edge.getFlags().equals(originalEdge.getFlags()));
            evidence.add(term);
            weight += ew + tw; millis = Math.addExact(millis, Math.addExact(et, tt)); distance += edge.getDistance();
            exactWeight = exactWeight.add(new BigDecimal(ew)).add(new BigDecimal(tw));
            exactDistance = exactDistance.add(new BigDecimal(edge.getDistance()));
            node = edge.getAdjNode(); previous = edge.getEdge();
        }
        legal &= node == target;
        return new Description(new Metrics(weight, millis, distance), exactWeight, exactDistance, keys, evidence, legal);
    }

    private static double unitWeight(Weighting weighting, EdgeIteratorState original) {
        EdgeIteratorState unit = (EdgeIteratorState) Proxy.newProxyInstance(EdgeIteratorState.class.getClassLoader(),
                new Class<?>[]{EdgeIteratorState.class}, (proxy, method, arguments) -> {
                    if (method.getName().equals("getDistance") && method.getParameterCount() == 0) return 1d;
                    try { return method.invoke(original, arguments); }
                    catch (InvocationTargetException error) { throw error.getCause(); }
                });
        return weighting.calcEdgeWeight(unit, false);
    }

    private static boolean sameUnitCostsAndTurns(Description first, Description second) {
        Map<Integer, List<Map<String, Object>>> a = grouped(first), b = grouped(second);
        if (!a.keySet().equals(b.keySet())) return false;
        for (int key : a.keySet()) {
            Set<Double> unitCosts = new HashSet<>();
            for (Map<String, Object> term : a.get(key)) {
                if (!Boolean.TRUE.equals(term.get("encoded_flags_equal_original"))) return false;
                unitCosts.add(((Number) term.get("normalized_distance_one_weight")).doubleValue());
            }
            for (Map<String, Object> term : b.get(key)) {
                if (!Boolean.TRUE.equals(term.get("encoded_flags_equal_original"))) return false;
                unitCosts.add(((Number) term.get("normalized_distance_one_weight")).doubleValue());
            }
            if (unitCosts.size() != 1 || sum(a.get(key), "turn_weight").compareTo(sum(b.get(key), "turn_weight")) != 0
                    || longSum(a.get(key), "turn_time_ms") != longSum(b.get(key), "turn_time_ms")) return false;
        }
        return true;
    }

    private static List<Map<String, Object>> deltas(Description first, Description second) {
        Map<Integer, List<Map<String, Object>>> a = grouped(first), b = grouped(second);
        Set<Integer> keys = new LinkedHashSet<>(a.keySet()); keys.addAll(b.keySet());
        List<Map<String, Object>> result = new ArrayList<>();
        for (int key : keys) {
            List<Map<String, Object>> left = a.getOrDefault(key, List.of()), right = b.getOrDefault(key, List.of());
            Map<String, Object> row = new LinkedHashMap<>(); row.put("copy_canonical_original_directed_edge_key", key);
            row.put("pair_piece_count", left.size()); row.put("shared_piece_count", right.size());
            for (String field : List.of("distance_m", "edge_weight", "turn_weight"))
                row.put("exact_pair_minus_shared_" + field, sum(left, field).subtract(sum(right, field)).toPlainString());
            for (String field : List.of("edge_time_ms", "turn_time_ms"))
                row.put("pair_minus_shared_" + field, longSum(left, field) - longSum(right, field));
            result.add(row);
        }
        return result;
    }

    private static Map<Integer, List<Map<String, Object>>> grouped(Description description) {
        Map<Integer, List<Map<String, Object>>> groups = new LinkedHashMap<>();
        for (Map<String, Object> term : description.edges)
            groups.computeIfAbsent(((Number) term.get("copy_canonical_edge_key")).intValue(), ignored -> new ArrayList<>()).add(term);
        return groups;
    }
    private static BigDecimal sum(List<Map<String, Object>> terms, String field) {
        BigDecimal result = BigDecimal.ZERO;
        for (Map<String, Object> term : terms) result = result.add(new BigDecimal(((Number) term.get(field)).doubleValue()));
        return result;
    }
    private static long longSum(List<Map<String, Object>> terms, String field) {
        long result = 0;
        for (Map<String, Object> term : terms) result = Math.addExact(result, ((Number) term.get(field)).longValue());
        return result;
    }
    private static Map<String, Object> description(Description path) {
        return Map.of("metrics", metricMap(path.metrics), "exact_objective_terms_sum", path.exactWeight.toPlainString(),
                "exact_distance_terms_sum", path.exactDistance.toPlainString(), "legal_and_requested_endpoints", path.legal,
                "copy_canonical_directed_edge_keys", path.canonicalKeys, "edge_contributions", path.edges);
    }
    private static Map<String, Object> metricMap(Metrics metrics) {
        return Map.of("weights", metrics.weight, "times_ms", metrics.millis, "distances", metrics.distance);
    }
    private static Map<String, Object> snap(Snap snap) {
        return Map.of("original_edge_key", snap.getClosestEdge().getEdgeKey(), "closest_node", snap.getClosestNode(),
                "position", snap.getSnappedPosition().toString(), "coordinate", List.of(snap.getSnappedPoint().lon, snap.getSnappedPoint().lat));
    }
    private static boolean samePosition(Snap snap, Graph graph, int node) {
        return Math.abs(snap.getSnappedPoint().lat - graph.getNodeAccess().getLat(node)) < 1e-12
                && Math.abs(snap.getSnappedPoint().lon - graph.getNodeAccess().getLon(node)) < 1e-12;
    }
    private static boolean missing(Map<String, ? extends Number> metrics) {
        return metrics.containsKey("weights") && metrics.containsKey("times_ms") && metrics.containsKey("distances")
                && metrics.get("weights") == null && metrics.get("times_ms") == null && metrics.get("distances") == null;
    }
    private static boolean matchesObserved(Metrics metrics, Map<String, ? extends Number> observed) {
        return observed.get("weights") != null && observed.get("times_ms") != null && observed.get("distances") != null
                && Double.compare(metrics.weight, observed.get("weights").doubleValue()) == 0
                && metrics.millis == observed.get("times_ms").longValue()
                && Double.compare(metrics.distance, observed.get("distances").doubleValue()) == 0;
    }
    private static boolean same(Metrics first, Metrics second) {
        return Double.compare(first.weight, second.weight) == 0 && first.millis == second.millis
                && Double.compare(first.distance, second.distance) == 0;
    }
    private static boolean finite(double value) { return Double.isFinite(value) && value >= 0; }
    private static boolean closeAccumulation(double a, double b, int terms) {
        // Only a bound on binary regrouping of the same nonnegative distance terms, not a route comparison tolerance.
        return Math.abs(a - b) <= Math.ulp(Math.max(a, b)) * Math.max(1, terms + 1);
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
