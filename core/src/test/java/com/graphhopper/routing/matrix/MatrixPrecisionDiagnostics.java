package com.graphhopper.routing.matrix;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.ResponsePath;
import com.graphhopper.config.Profile;
import com.graphhopper.routing.Dijkstra;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.ev.Subnetwork;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.querygraph.VirtualEdgeIteratorState;
import com.graphhopper.routing.util.DefaultSnapFilter;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.index.Snap;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.DistancePlaneProjection;
import com.graphhopper.util.FetchMode;
import com.graphhopper.util.PMap;
import com.graphhopper.util.shapes.GHPoint;

import java.util.*;

/** Test-only path evidence for independently snapped versus combined QueryGraph routes. */
final class MatrixPrecisionDiagnostics {
    record Evidence(boolean explained, Map<String, Object> details) { }
    private record Coordinate(double lat, double lon) { }
    private record Search(TargetSetDijkstra algorithm, TargetSetDijkstra.Result result) { }
    private record PathMetrics(double weight, long time, double distance) { }
    private record Description(PathMetrics metrics, List<Integer> originalKeys, List<Integer> copyCanonicalKeys,
                               List<Map<String, Object>> edges, boolean legal) { }
    private record CopyIdentity(int canonicalKey, List<Integer> members) { }

    private final GraphHopper hopper;
    private final String profileName;
    private final List<GHPoint> points;
    private final Weighting rawWeighting;
    private final DefaultSnapFilter filter;
    private final TraversalMode mode;
    private final List<Snap> combinedSnaps;
    private final QueryGraph combinedGraph;
    private final Weighting combinedWeighting;
    private final Set<Integer> targets = new HashSet<>();
    private final Map<Integer, Search> searches = new HashMap<>();
    private final Map<Integer, CopyIdentity> copies = new HashMap<>();

    MatrixPrecisionDiagnostics(GraphHopper hopper, String profileName, List<GHPoint> points) {
        this.hopper = hopper;
        this.profileName = profileName;
        this.points = points;
        Profile profile = hopper.getProfile(profileName);
        rawWeighting = hopper.createWeighting(profile, new PMap());
        filter = new DefaultSnapFilter(rawWeighting,
                hopper.getEncodingManager().getBooleanEncodedValue(Subnetwork.key(profileName)));
        mode = profile.hasTurnCosts() ? TraversalMode.EDGE_BASED : TraversalMode.NODE_BASED;
        Map<Coordinate, Snap> distinct = new LinkedHashMap<>();
        combinedSnaps = new ArrayList<>();
        for (GHPoint point : points) {
            Coordinate key = new Coordinate(point.lat, point.lon);
            combinedSnaps.add(distinct.computeIfAbsent(key, ignored -> snap(point)));
        }
        combinedGraph = QueryGraph.create(hopper.getBaseGraph(), distinct.values().stream().filter(Snap::isValid).toList());
        combinedWeighting = combinedGraph.wrapWeighting(rawWeighting);
        combinedSnaps.stream().filter(Snap::isValid).forEach(s -> targets.add(s.getClosestNode()));
    }

    Evidence diagnose(int fromIndex, int toIndex) {
        GHPoint from = points.get(fromIndex), to = points.get(toIndex);
        Snap pairFrom = snap(from), pairTo = snap(to);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("profile", profileName);
        details.put("from_index", fromIndex);
        details.put("to_index", toIndex);
        details.put("from", List.of(from.lon, from.lat));
        details.put("to", List.of(to.lon, to.lat));
        details.put("independent_snaps", List.of(describeSnap(pairFrom), describeSnap(pairTo)));
        details.put("combined_snaps", List.of(describeSnap(combinedSnaps.get(fromIndex)), describeSnap(combinedSnaps.get(toIndex))));
        if (!pairFrom.isValid() || !pairTo.isValid()) {
            details.put("classification", "INVALID_SNAP");
            return new Evidence(false, details);
        }
        QueryGraph pairGraph = QueryGraph.create(hopper.getBaseGraph(), pairFrom, pairTo);
        Weighting pairWeighting = pairGraph.wrapWeighting(rawWeighting);
        Path pairPath = new Dijkstra(pairGraph, pairWeighting, mode).calcPath(pairFrom.getClosestNode(), pairTo.getClosestNode());
        int source = combinedSnaps.get(fromIndex).getClosestNode();
        int target = combinedSnaps.get(toIndex).getClosestNode();
        Path combinedPath = new Dijkstra(combinedGraph, combinedWeighting, mode).calcPath(source, target);
        Search shared = searches.computeIfAbsent(source, ignored -> {
            TargetSetDijkstra algorithm = new TargetSetDijkstra(combinedGraph, combinedWeighting, mode).withDiagnostics();
            return new Search(algorithm, algorithm.search(source, targets, Long.MAX_VALUE, Integer.MAX_VALUE));
        });
        TargetSetDijkstra.Metrics sharedMetrics = shared.result.targets().get(target);
        if (!pairPath.isFound() || !combinedPath.isFound() || sharedMetrics == null) {
            details.put("classification", "UNREACHABLE_OR_REACHABILITY_MISMATCH");
            details.put("pair_found", pairPath.isFound());
            details.put("combined_found", combinedPath.isFound());
            details.put("shared_found", sharedMetrics != null);
            return new Evidence(!pairPath.isFound() && !combinedPath.isFound() && sharedMetrics == null, details);
        }

        Description pair = describe(pairGraph, pairWeighting, pairPath.calcEdges());
        Description combined = describe(combinedGraph, combinedWeighting, combinedPath.calcEdges());
        List<EdgeIteratorState> sharedEdges = shared.algorithm.diagnosticPath(target).stream()
                .map(step -> combinedGraph.getEdgeIteratorStateForKey(step.edgeKey())).toList();
        Description selected = describe(combinedGraph, combinedWeighting, sharedEdges);
        GHRequest routeRequest = new GHRequest(List.of(from, to)).setProfile(profileName).setAlgorithm("dijkstra")
                .setSnapPreventions(List.of());
        routeRequest.putHint("ch.disable", true).putHint("lm.disable", true).putHint("instructions", false).putHint("calc_points", false);
        GHResponse route = hopper.route(routeRequest);
        if (route.hasErrors()) {
            details.put("classification", "UPSTREAM_ROUTE_FAILED");
            details.put("route_errors", route.getErrors().toString());
            return new Evidence(false, details);
        }
        ResponsePath routePath = route.getBest();
        PathMetrics routeMetrics = new PathMetrics(routePath.getRouteWeight(), routePath.getTime(), routePath.getDistance());
        PathMetrics selectedMetrics = new PathMetrics(sharedMetrics.weight(), sharedMetrics.timeMillis(), sharedMetrics.distance());
        boolean exactSameQueryGraph = equivalent(new PathMetrics(combinedPath.getWeight(), combinedPath.getTime(), combinedPath.getDistance()), selectedMetrics)
                && equivalent(selected.metrics, selectedMetrics);
        boolean independentMatchesRoute = equivalent(pair.metrics, routeMetrics);
        boolean sameOriginalRoute = pair.originalKeys.equals(selected.originalKeys);
        boolean sameCopyCanonicalRoute = pair.copyCanonicalKeys.equals(selected.copyCanonicalKeys);
        boolean sameSnaps = sameSnap(pairFrom, combinedSnaps.get(fromIndex)) && sameSnap(pairTo, combinedSnaps.get(toIndex));
        boolean stableTurnsAndUnitWeights = stableTurnsAndUnitWeights(pair, selected);
        boolean copySubstitutionsHaveEqualMetrics = copySubstitutionsHaveEqualMetrics(pair, selected);
        details.put("independent_route", pathDescription(pair));
        details.put("combined_dijkstra", pathDescription(combined));
        details.put("shared_selected", pathDescription(selected));
        details.put("upstream_route_metrics", metrics(routeMetrics));
        details.put("reported_shared_metrics", metrics(selectedMetrics));
        details.put("same_query_graph_exact", exactSameQueryGraph);
        details.put("independent_matches_route", independentMatchesRoute);
        details.put("same_collapsed_original_directed_edges", sameOriginalRoute);
        details.put("same_copy_canonical_directed_edges", sameCopyCanonicalRoute);
        details.put("all_reconstructed_paths_legal", pair.legal && combined.legal && selected.legal);
        details.put("same_snapped_coordinates", sameSnaps);
        details.put("same_turn_costs_and_edge_weight_per_metre", stableTurnsAndUnitWeights);
        details.put("changed_copy_edges_have_equal_metrics", copySubstitutionsHaveEqualMetrics);
        details.put("per_original_edge_deltas", contributionDeltas(pair, selected));
        details.put("independent_minus_shared", metrics(new PathMetrics(routeMetrics.weight - selectedMetrics.weight,
                routeMetrics.time - selectedMetrics.time, routeMetrics.distance - selectedMetrics.distance)));

        // Per-edge contribution deltas are evidence, not a blanket tolerance. Equal canonical
        // routes and matching endpoints isolate graph splitting from a route/state/settlement bug.
        boolean explained = exactSameQueryGraph && independentMatchesRoute && sameCopyCanonicalRoute && sameSnaps
                && stableTurnsAndUnitWeights && copySubstitutionsHaveEqualMetrics && pair.legal && combined.legal && selected.legal;
        details.put("classification", explained ? sameOriginalRoute ? "SAME_PATH_QUERY_GRAPH_SPLIT_PRECISION"
                : "PROVEN_EDGE_COPY_TIE_AND_QUERY_GRAPH_SPLIT_PRECISION" : "UNEXPLAINED");
        details.put("explanation", "Independent route uses stored full-edge distances where the combined graph may split those edges. "
                + "Virtual distances are recalculated from geometry; duration is truncated per edge. "
                + "All edge and turn contributions below are recomputed from the actual selected directed paths. "
                + "Different raw edges may align only if BaseGraph.forEdgeAndCopiesOfEdge proves their shared upstream copy family; "
                + "both actual paths must remain legal with unchanged turn costs and weight per metre. Unrelated parallel edges never align.");
        return new Evidence(explained, details);
    }

    private Snap snap(GHPoint point) {
        return hopper.getLocationIndex().findClosest(point.lat, point.lon, filter);
    }

    private Description describe(Graph graph, Weighting weighting, List<EdgeIteratorState> path) {
        List<Map<String, Object>> edges = new ArrayList<>();
        List<Integer> originalKeys = new ArrayList<>();
        List<Integer> canonicalKeys = new ArrayList<>();
        double totalWeight = 0, totalDistance = 0;
        long totalTime = 0;
        int previous = EdgeIterator.NO_EDGE;
        int previousNode = -1;
        boolean legal = true;
        for (EdgeIteratorState edge : path) {
            int originalKey = edge instanceof VirtualEdgeIteratorState virtual ? virtual.getOriginalEdgeKey() : edge.getEdgeKey();
            CopyIdentity identity = copies.computeIfAbsent(originalKey, key -> copyIdentity(graph.getBaseGraph(), key));
            if (originalKeys.isEmpty() || originalKeys.get(originalKeys.size() - 1) != originalKey)
                originalKeys.add(originalKey);
            if (canonicalKeys.isEmpty() || canonicalKeys.get(canonicalKeys.size() - 1) != identity.canonicalKey)
                canonicalKeys.add(identity.canonicalKey);
            double edgeWeight = weighting.calcEdgeWeight(edge, false);
            long edgeTime = weighting.calcEdgeMillis(edge, false);
            double turnWeight = previous == EdgeIterator.NO_EDGE ? 0 : weighting.calcTurnWeight(previous, edge.getBaseNode(), edge.getEdge());
            long turnTime = previous == EdgeIterator.NO_EDGE ? 0 : weighting.calcTurnMillis(previous, edge.getBaseNode(), edge.getEdge());
            legal &= (previousNode < 0 || previousNode == edge.getBaseNode())
                    && Double.isFinite(edgeWeight) && edgeWeight >= 0 && Double.isFinite(turnWeight) && turnWeight >= 0
                    && Double.isFinite(edge.getDistance()) && edge.getDistance() >= 0 && edgeTime >= 0 && turnTime >= 0;
            Map<String, Object> contribution = new LinkedHashMap<>();
            contribution.put("edge_key", edge.getEdgeKey());
            contribution.put("original_edge_key", originalKey);
            contribution.put("copy_canonical_edge_key", identity.canonicalKey);
            if (identity.members.size() > 1)
                contribution.put("upstream_copy_family_directed_keys", identity.members);
            contribution.put("from_node", edge.getBaseNode());
            contribution.put("to_node", edge.getAdjNode());
            contribution.put("virtual", edge instanceof VirtualEdgeIteratorState);
            contribution.put("distance_m", edge.getDistance());
            contribution.put("geometry_distance_m", DistancePlaneProjection.DIST_PLANE.calcDistance(edge.fetchWayGeometry(FetchMode.ALL)));
            contribution.put("stored_original_distance_m", graph.getBaseGraph().getEdgeIteratorStateForKey(originalKey).getDistance());
            contribution.put("edge_weight", edgeWeight);
            contribution.put("edge_time_ms", edgeTime);
            contribution.put("turn_weight", turnWeight);
            contribution.put("turn_time_ms", turnTime);
            edges.add(contribution);
            totalWeight += edgeWeight + turnWeight;
            totalTime = Math.addExact(totalTime, Math.addExact(edgeTime, turnTime));
            totalDistance += edge.getDistance();
            previous = edge.getEdge();
            previousNode = edge.getAdjNode();
        }
        return new Description(new PathMetrics(totalWeight, totalTime, totalDistance), originalKeys, canonicalKeys, edges, legal);
    }

    /** Uses upstream shared-geometry copy identity, never endpoint/metric similarity. */
    static int canonicalDirectedEdgeKey(BaseGraph graph, int edgeKey) {
        return copyIdentity(graph, edgeKey).canonicalKey;
    }

    private static CopyIdentity copyIdentity(BaseGraph graph, int edgeKey) {
        EdgeIteratorState original = graph.getEdgeIteratorStateForKey(edgeKey);
        Set<Integer> family = new TreeSet<>();
        graph.forEdgeAndCopiesOfEdge(graph.createEdgeExplorer(), original, candidate -> {
            if (candidate.getBaseNode() == original.getBaseNode() && candidate.getAdjNode() == original.getAdjNode())
                family.add(candidate.getEdgeKey());
        });
        if (!family.contains(edgeKey))
            throw new IllegalStateException("Upstream copy family omitted its original directed edge " + edgeKey);
        return new CopyIdentity(family.iterator().next(), List.copyOf(family));
    }

    private static List<Map<String, Object>> contributionDeltas(Description independent, Description combined) {
        Map<Integer, double[]> independentGroups = group(independent);
        Map<Integer, double[]> combinedGroups = group(combined);
        Set<Integer> keys = new LinkedHashSet<>(independentGroups.keySet());
        keys.addAll(combinedGroups.keySet());
        List<Map<String, Object>> changes = new ArrayList<>();
        for (int key : keys) {
            double[] a = independentGroups.getOrDefault(key, new double[6]);
            double[] b = combinedGroups.getOrDefault(key, new double[6]);
            if (Arrays.equals(a, b)) continue;
            Map<String, Object> delta = new LinkedHashMap<>();
            delta.put("copy_canonical_original_directed_edge_key", key);
            delta.put("independent_piece_count", (int) a[5]);
            delta.put("combined_piece_count", (int) b[5]);
            delta.put("independent_distance_m", a[0]);
            delta.put("combined_distance_m", b[0]);
            delta.put("distance_delta_m", a[0] - b[0]);
            delta.put("edge_weight_delta", a[1] - b[1]);
            delta.put("edge_time_delta_ms", (long) (a[2] - b[2]));
            delta.put("turn_weight_delta", a[3] - b[3]);
            delta.put("turn_time_delta_ms", (long) (a[4] - b[4]));
            changes.add(delta);
        }
        return changes;
    }

    private static Map<Integer, double[]> group(Description description) {
        Map<Integer, double[]> groups = new LinkedHashMap<>();
        for (Map<String, Object> edge : description.edges) {
            double[] sum = groups.computeIfAbsent((Integer) edge.get("copy_canonical_edge_key"), ignored -> new double[6]);
            sum[0] += ((Number) edge.get("distance_m")).doubleValue();
            sum[1] += ((Number) edge.get("edge_weight")).doubleValue();
            sum[2] += ((Number) edge.get("edge_time_ms")).doubleValue();
            sum[3] += ((Number) edge.get("turn_weight")).doubleValue();
            sum[4] += ((Number) edge.get("turn_time_ms")).doubleValue();
            sum[5]++;
        }
        return groups;
    }

    private static boolean stableTurnsAndUnitWeights(Description independent, Description combined) {
        Map<Integer, double[]> a = group(independent), b = group(combined);
        if (!a.keySet().equals(b.keySet())) return false;
        for (int key : a.keySet()) {
            double[] first = a.get(key), second = b.get(key);
            if (Math.abs(first[3] - second[3]) > 1e-9 || first[4] != second[4]) return false;
            if (first[0] > 0 && second[0] > 0) {
                double firstPerMetre = first[1] / first[0], secondPerMetre = second[1] / second[0];
                if (Math.abs(firstPerMetre - secondPerMetre) > 1e-10 + Math.abs(firstPerMetre) * 1e-12) return false;
            }
        }
        return true;
    }

    private static boolean copySubstitutionsHaveEqualMetrics(Description independent, Description combined) {
        Map<Integer, Set<Integer>> firstKeys = rawKeysByCopyGroup(independent), secondKeys = rawKeysByCopyGroup(combined);
        Map<Integer, double[]> first = group(independent), second = group(combined);
        if (!firstKeys.keySet().equals(secondKeys.keySet())) return false;
        for (int canonical : firstKeys.keySet()) {
            if (firstKeys.get(canonical).equals(secondKeys.get(canonical))) continue;
            double[] a = first.get(canonical), b = second.get(canonical);
            // This diagnostic gate proves a concrete equal-metric copy substitution. It does
            // not waive arbitrary equal-weight alternatives with different time or distance.
            if (Math.abs(a[0] - b[0]) > 1e-9 || Math.abs(a[1] - b[1]) > 1e-12 || a[2] != b[2]
                    || Math.abs(a[3] - b[3]) > 1e-12 || a[4] != b[4]) return false;
        }
        return true;
    }

    private static Map<Integer, Set<Integer>> rawKeysByCopyGroup(Description description) {
        Map<Integer, Set<Integer>> keys = new HashMap<>();
        for (Map<String, Object> edge : description.edges)
            keys.computeIfAbsent((Integer) edge.get("copy_canonical_edge_key"), ignored -> new HashSet<>())
                    .add((Integer) edge.get("original_edge_key"));
        return keys;
    }

    private static boolean sameSnap(Snap a, Snap b) {
        return a.isValid() && b.isValid() && Math.abs(a.getSnappedPoint().lat - b.getSnappedPoint().lat) < 1e-12
                && Math.abs(a.getSnappedPoint().lon - b.getSnappedPoint().lon) < 1e-12;
    }

    private static Map<String, Object> describeSnap(Snap snap) {
        if (!snap.isValid()) return Map.of("valid", false);
        return Map.of("valid", true, "node", snap.getClosestNode(), "edge_key", snap.getClosestEdge().getEdgeKey(),
                "coordinate", List.of(snap.getSnappedPoint().lon, snap.getSnappedPoint().lat),
                "displacement_m", snap.getQueryDistance(), "position", snap.getSnappedPosition().toString());
    }

    private static boolean equivalent(PathMetrics a, PathMetrics b) {
        return Math.abs(a.weight - b.weight) <= 1e-7 + Math.abs(a.weight) * 1e-10
                && a.time == b.time && Math.abs(a.distance - b.distance) <= 1e-8 + Math.abs(a.distance) * 1e-12;
    }

    private static Map<String, Object> metrics(PathMetrics metrics) {
        return Map.of("weight", metrics.weight, "time_ms", metrics.time, "distance_m", metrics.distance);
    }

    private static Map<String, Object> pathDescription(Description description) {
        return Map.of("metrics", metrics(description.metrics), "original_directed_edge_keys", description.originalKeys,
                "copy_canonical_directed_edge_keys", description.copyCanonicalKeys,
                "legal", description.legal, "edge_contributions", description.edges);
    }
}
