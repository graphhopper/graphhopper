package com.graphhopper.routing.matrix;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.ResponsePath;
import com.graphhopper.config.Profile;
import com.graphhopper.matrix.MatrixException;
import com.graphhopper.matrix.MatrixRequest;
import com.graphhopper.matrix.MatrixSnappedPoint;
import com.graphhopper.routing.ev.Subnetwork;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.querygraph.QueryRoutingCHGraph;
import com.graphhopper.routing.util.DefaultSnapFilter;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.index.Snap;
import com.graphhopper.storage.RoutingCHGraph;
import com.graphhopper.util.PMap;
import com.graphhopper.util.exceptions.ConnectionNotFoundException;
import com.graphhopper.util.exceptions.MaximumNodesExceededException;
import com.graphhopper.util.exceptions.PointNotFoundException;
import com.graphhopper.util.exceptions.PointOutOfBoundsException;
import com.graphhopper.util.shapes.GHPoint;

import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/** Static street matrices. All mutable routing/search state belongs to a single request. */
public final class MatrixEngine {
    public record Limits(int maxAxisPoints, long maxCells, int maxConcurrentRequests,
                         long timeoutMillis, int maxVisitedStates) {
        public Limits {
            if (maxAxisPoints < 1 || maxCells < 1 || maxConcurrentRequests < 1 ||
                    timeoutMillis < 1 || timeoutMillis > TimeUnit.DAYS.toMillis(1) || maxVisitedStates < 1)
                throw new IllegalArgumentException("Matrix limits must be positive; deadline must be at most one day");
        }

        public static Limits defaults() {
            return new Limits(Integer.getInteger("matrix.max_axis_points", 1000),
                    Long.getLong("matrix.max_cells", 100000),
                    Integer.getInteger("matrix.max_concurrent_requests", 2),
                    Long.getLong("matrix.timeout_ms", 30000),
                    Integer.getInteger("matrix.max_visited_states", 1000000));
        }
    }

    // GHPoint equality uses an epsilon. Exact records deliberately preserve nearby distinct coordinates.
    private record Coordinate(double lat, double lon) {
        static Coordinate of(GHPoint point) { return new Coordinate(point.lat, point.lon); }
    }
    private record Cell(double weight, long millis, double distance) { }
    private record Row(Map<Integer, Cell> cells, long searches, long visited, boolean visitsComplete) { }
    private interface Backend {
        Row search(Context context, int originIndex);
    }
    private record Context(MatrixRequest request, List<GHPoint> origins, List<GHPoint> destinations,
                           Profile profile, Weighting weighting,
                           Map<Coordinate, Snap> snaps, QueryGraph queryGraph, Set<Integer> targets,
                           long deadline, Counters counters) { }
    private static final class Counters {
        long searches;
        long visited;
        boolean visitsComplete = true;
        long backendNanos;
        long coreSearchNanos;
        long outputNanos;
        long chPreparationNanos;
        long chCandidateNanos;
        long chVerificationNanos;
        long certificateSearches;
        long certificateVisited;
        long correctedCells;
    }

    private final GraphHopper hopper;
    private final Limits limits;
    private final Semaphore admission;
    private final MatrixProvenance provenance;

    public MatrixEngine(GraphHopper hopper) { this(hopper, Limits.defaults()); }

    public MatrixEngine(GraphHopper hopper, Limits limits) {
        this.hopper = Objects.requireNonNull(hopper);
        this.limits = Objects.requireNonNull(limits);
        admission = new Semaphore(limits.maxConcurrentRequests());
        provenance = new MatrixProvenance(hopper);
    }

    public Map<String, Object> route(MatrixRequest request) {
        validate(request);
        if (!admission.tryAcquire())
            throw new MatrixException("SERVICE_BUSY", "All matrix request slots are occupied");
        long start = System.nanoTime();
        long deadline = start + TimeUnit.MILLISECONDS.toNanos(limits.timeoutMillis());
        try {
            return execute(request, start, deadline);
        } catch (TargetSetDijkstra.SearchException e) {
            throw new MatrixException(e.reason().name(), e.getMessage(), e);
        } catch (ArithmeticException e) {
            throw new MatrixException("METRIC_OVERFLOW", "Matrix metric accumulation overflowed", e);
        } finally {
            admission.release();
        }
    }

    private Map<String, Object> execute(MatrixRequest request, long start, long deadline) {
        List<GHPoint> origins = request.getOrigins(), destinations = request.getDestinations();
        Profile profile = hopper.getProfile(request.getProfile());
        if (profile == null)
            throw new MatrixException("UNKNOWN_PROFILE", "Unknown configured street profile: " + request.getProfile());
        Weighting weighting = hopper.createWeighting(profile, new PMap());
        DefaultSnapFilter filter = new DefaultSnapFilter(weighting,
                hopper.getEncodingManager().getBooleanEncodedValue(Subnetwork.key(profile.getName())));
        Map<Coordinate, Snap> snaps = new LinkedHashMap<>();
        List<GHPoint> all = new ArrayList<>(origins);
        all.addAll(destinations);
        for (GHPoint point : all) {
            check(deadline);
            Coordinate key = Coordinate.of(point);
            if (snaps.containsKey(key))
                continue;
            Snap snap = hopper.getBaseGraph().getBounds().contains(point.lat, point.lon)
                    ? hopper.getLocationIndex().findClosest(point.lat, point.lon, filter) : new Snap(point.lat, point.lon);
            snaps.put(key, snap);
        }
        List<Integer> invalidFrom = invalidIndexes(origins, snaps);
        List<Integer> invalidTo = invalidIndexes(destinations, snaps);
        if (request.isFailFast() && (!invalidFrom.isEmpty() || !invalidTo.isEmpty()))
            throw new MatrixException("INVALID_SNAP", "One or more matrix points cannot snap to the configured profile");
        long snappedAt = System.nanoTime();

        String algorithm = request.getAlgorithm().equals("auto") ? "flexible" : request.getAlgorithm();
        String fallbackReason = null;
        RoutingCHGraph preparedCH = hopper.getCHGraphs().get(profile.getName());
        if (algorithm.equals("ch_experimental")) {
            if (profile.hasTurnCosts()) fallbackReason = "TURN_COST_PROFILE_UNSUPPORTED";
            else if (preparedCH == null) fallbackReason = "NO_PREPARED_CH";
            else if (preparedCH.isEdgeBased() || preparedCH.hasTurnCosts()) fallbackReason = "EDGE_BASED_CH_UNSUPPORTED";
            if (fallbackReason != null) algorithm = "flexible";
        }
        List<Snap> valid = snaps.values().stream().filter(Snap::isValid).toList();
        QueryGraph queryGraph = !algorithm.equals("reference") && !valid.isEmpty()
                ? QueryGraph.create(hopper.getBaseGraph(), valid) : null;
        Set<Integer> targets = new HashSet<>();
        if (queryGraph != null)
            for (GHPoint point : destinations) {
                Snap snap = snaps.get(Coordinate.of(point));
                if (snap.isValid()) targets.add(snap.getClosestNode());
            }
        long graphAt = System.nanoTime();
        Counters counters = new Counters();
        Context context = new Context(request, origins, destinations, profile, weighting, snaps, queryGraph, targets, deadline, counters);
        CertifiedCHBackend chBackend = algorithm.equals("ch_experimental") ? new CertifiedCHBackend(preparedCH) : null;
        Backend backend = chBackend != null ? chBackend
                : algorithm.equals("flexible") ? new FlexibleBackend() : new ReferenceBackend();
        long allocationStart = System.nanoTime();
        Map<String, Object> response = new LinkedHashMap<>();
        Map<String, List<List<Number>>> outputs = new LinkedHashMap<>();
        int rows = origins.size(), columns = destinations.size();
        for (String metric : request.getOutArrays()) {
            List<List<Number>> matrix = new ArrayList<>(rows);
            for (int i = 0; i < rows; i++) matrix.add(new ArrayList<>(Collections.nCopies(columns, null)));
            outputs.put(metric, matrix);
            response.put(metric, matrix);
        }
        Map<Coordinate, List<Integer>> originRows = new LinkedHashMap<>();
        for (int i = 0; i < rows; i++)
            originRows.computeIfAbsent(Coordinate.of(origins.get(i)), key -> new ArrayList<>()).add(i);
        List<List<Integer>> disconnected = new ArrayList<>();
        counters.outputNanos += System.nanoTime() - allocationStart;
        for (List<Integer> group : originRows.values()) {
            if (algorithm.equals("reference")) {
                for (int i : group) fillRow(context, backend, i, List.of(i), outputs, disconnected);
            } else {
                fillRow(context, backend, group.get(0), group, outputs, disconnected);
            }
        }
        check(deadline);
        long searchedAt = System.nanoTime();
        List<Map<String, Object>> hints = new ArrayList<>();
        if (!invalidFrom.isEmpty() || !invalidTo.isEmpty()) {
            Map<String, Object> hint = new LinkedHashMap<>();
            hint.put("message", "One or more points cannot snap to this profile");
            hint.put("details", "INVALID_SNAP");
            if (!invalidFrom.isEmpty()) hint.put("invalid_from_points", invalidFrom);
            if (!invalidTo.isEmpty()) hint.put("invalid_to_points", invalidTo);
            hints.add(hint);
        }
        if (!disconnected.isEmpty()) hints.add(Map.of("message", "No connection between the listed point pairs",
                "details", "CONNECTION_NOT_FOUND", "point_pairs", disconnected));
        response.put("hints", hints);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("contract_version", "1.0");
        metadata.put("algorithm", algorithm);
        metadata.put("requested_algorithm", request.getAlgorithm());
        if (fallbackReason != null) metadata.put("fallback_reason", fallbackReason);
        if (chBackend != null) {
            metadata.put("experimental", true);
            metadata.put("optimality_verification", "flexible_each_origin");
            metadata.put("ch_preparation", Map.of("graph_fingerprint", provenance.graphFingerprint,
                    "profile_fingerprint", provenance.profileFingerprint(profile.getName()),
                    "nodes", preparedCH.getNodes(), "shortcuts", preparedCH.getShortcuts(),
                    "source", "loaded_fixed_profile_preparation", "traversal", "node"));
            Map<String, Object> ch = new LinkedHashMap<>();
            BucketCHMatrix.Statistics stats = chBackend.statistics();
            ch.put("backward_searches", stats == null ? 0L : stats.backwardSearches());
            ch.put("forward_searches", stats == null ? 0L : stats.forwardSearches());
            ch.put("candidate_visited_states", stats == null ? 0L : stats.visitedStates());
            ch.put("bucket_entries", stats == null ? 0L : stats.bucketEntries());
            ch.put("metric_cache_entries", stats == null ? 0L : stats.metricCacheEntries());
            ch.put("unpacked_original_edges", stats == null ? 0L : stats.unpackedOriginalEdges());
            ch.put("metric_extraction_ms", stats == null ? 0d : ms(stats.metricExtractionNanos()));
            ch.put("certificate_searches", counters.certificateSearches);
            ch.put("certificate_visited_states", counters.certificateVisited);
            ch.put("corrected_cells", counters.correctedCells);
            ch.put("corrected_cells_basis", "distinct_origin_coordinate_by_destination_index");
            ch.put("candidate_optimality_guaranteed", false);
            metadata.put("ch_matrix", ch);
        }
        metadata.put("profile", profile.getName());
        metadata.put("turn_costs", profile.hasTurnCosts());
        metadata.put("origin_count", rows);
        metadata.put("destination_count", columns);
        metadata.put("unique_origin_count", originRows.size());
        metadata.put("searches", counters.searches);
        // Explicit completeness fields avoid inventing measured work if a backend evolves.
        metadata.put("visited_states", counters.visitsComplete ? counters.visited : null);
        metadata.put("visited_states_known", counters.visited);
        metadata.put("visited_states_complete", counters.visitsComplete);
        metadata.put("threads", 1);
        metadata.put("units", Map.of("times", "seconds", "times_ms", "milliseconds", "distances", "metres", "weights", "profile_cost"));
        metadata.put("graph_fingerprint", provenance.graphFingerprint);
        metadata.put("profile_fingerprint", provenance.profileFingerprint(profile.getName()));
        metadata.put("build_commit", provenance.buildCommit);
        metadata.put("from_snapped_points", snapMetadata(origins, snaps));
        metadata.put("to_snapped_points", snapMetadata(destinations, snaps));
        Map<String, Object> timings = new LinkedHashMap<>();
        timings.put("snapping", ms(snappedAt - start));
        timings.put("query_graph", ms(graphAt - snappedAt));
        timings.put("backend_calls", ms(counters.backendNanos));
        if (!algorithm.equals("reference")) timings.put("core_search", ms(counters.coreSearchNanos));
        if (chBackend != null) {
            timings.put("ch_bucket_preparation", ms(counters.chPreparationNanos));
            timings.put("ch_candidates", ms(counters.chCandidateNanos));
            timings.put("ch_verification", ms(counters.chVerificationNanos));
        }
        timings.put("scatter_output", ms(counters.outputNanos));
        timings.put("search_and_output", ms(searchedAt - graphAt));
        timings.put("total", ms(System.nanoTime() - start));
        metadata.put("timings_ms", timings);
        response.put("info", new LinkedHashMap<>(Map.of("local_matrix", metadata)));
        return response;
    }

    private void fillRow(Context context, Backend backend, int originIndex, List<Integer> scatterRows,
                         Map<String, List<List<Number>>> outputs, List<List<Integer>> disconnected) {
        check(context.deadline());
        if (!context.snaps().get(Coordinate.of(context.origins().get(originIndex))).isValid()) return;
        long backendStarted = System.nanoTime();
        Row row = backend.search(context, originIndex);
        context.counters().backendNanos += System.nanoTime() - backendStarted;
        long outputStarted = System.nanoTime();
        context.counters().searches += row.searches();
        context.counters().visited = Math.addExact(context.counters().visited, row.visited());
        context.counters().visitsComplete &= row.visitsComplete();
        for (int j = 0; j < context.destinations().size(); j++) {
            if (!context.snaps().get(Coordinate.of(context.destinations().get(j))).isValid()) continue;
            Cell cell = row.cells().get(j);
            if (cell == null) {
                if (context.request().isFailFast()) throw new MatrixException("CONNECTION_NOT_FOUND", "No connection for origin " + originIndex + ", destination " + j);
                for (int i : scatterRows) disconnected.add(List.of(i, j));
                continue;
            }
            for (Map.Entry<String, List<List<Number>>> entry : outputs.entrySet()) {
                Number value = switch (entry.getKey()) {
                    case "times" -> Long.valueOf(cell.millis() / 1000);
                    case "times_ms" -> Long.valueOf(cell.millis());
                    case "distances" -> Double.valueOf(cell.distance());
                    case "weights" -> Double.valueOf(cell.weight());
                    default -> throw new IllegalStateException("Unknown validated metric");
                };
                for (int i : scatterRows) entry.getValue().get(i).set(j, value);
            }
        }
        context.counters().outputNanos += System.nanoTime() - outputStarted;
    }

    private final class ReferenceBackend implements Backend {
        @Override
        public Row search(Context context, int originIndex) {
            Map<Integer, Cell> cells = new HashMap<>();
            long searches = 0, visited = 0;
            boolean complete = true;
            for (int j = 0; j < context.destinations().size(); j++) {
                GHPoint target = context.destinations().get(j);
                if (!context.snaps().get(Coordinate.of(target)).isValid()) continue;
                check(context.deadline());
                int remainingVisits = remainingVisits(context.counters().visited + visited);
                long remainingMs = Math.max(1, TimeUnit.NANOSECONDS.toMillis(context.deadline() - System.nanoTime()));
                long routeTimeout = Math.min(remainingMs, hopper.getRouterConfig().getTimeoutMillis());
                long routeDeadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(routeTimeout);
                GHRequest query = new GHRequest(List.of(context.origins().get(originIndex), target))
                        .setProfile(context.profile().getName()).setAlgorithm("dijkstra").setSnapPreventions(List.of());
                query.putHint("ch.disable", true).putHint("lm.disable", true).putHint("instructions", false)
                        .putHint("calc_points", false).putHint("timeout_ms", routeTimeout)
                        .putHint("max_visited_nodes", Math.min(remainingVisits, hopper.getRouterConfig().getMaxVisitedNodes()));
                searches++;
                GHResponse result = hopper.route(query);
                check(context.deadline());
                if (System.nanoTime() >= routeDeadline) throw new MatrixException("DEADLINE_EXCEEDED", "Reference route deadline expired");
                if (result.getHints().has("visited_nodes.sum")) visited += result.getHints().getLong("visited_nodes.sum", 0);
                else complete = false;
                if (result.hasErrors()) {
                    for (Throwable error : result.getErrors()) {
                        if (error instanceof MaximumNodesExceededException) throw new MatrixException("SEARCH_LIMIT_EXCEEDED", error.getMessage(), error);
                        if (error instanceof PointNotFoundException || error instanceof PointOutOfBoundsException)
                            throw new MatrixException("INVALID_SNAP", "Independent routing disagreed with the validated snap: " + error.getMessage(), error);
                        if (!(error instanceof ConnectionNotFoundException)) throw new MatrixException("REFERENCE_ROUTING_FAILED", error.getMessage(), error);
                    }
                    if (!complete) throw new MatrixException("REFERENCE_ROUTING_FAILED", "Upstream disconnected route omitted visited-state counters; cannot enforce the aggregate search budget");
                    continue;
                }
                if (!complete) throw new MatrixException("REFERENCE_ROUTING_FAILED", "Upstream route omitted visited-state counters; cannot enforce the aggregate search budget");
                ResponsePath path = result.getBest();
                Cell cell = new Cell(path.getRouteWeight(), path.getTime(), path.getDistance());
                validateCell(cell);
                cells.put(j, cell);
            }
            return new Row(cells, searches, visited, complete);
        }
    }

    private final class FlexibleBackend implements Backend {
        @Override
        public Row search(Context context, int originIndex) {
            if (context.targets().isEmpty()) return new Row(Map.of(), 0, 0, true);
            int source = context.snaps().get(Coordinate.of(context.origins().get(originIndex))).getClosestNode();
            TargetSetDijkstra search = new TargetSetDijkstra(context.queryGraph(), context.queryGraph().wrapWeighting(context.weighting()),
                    context.profile().hasTurnCosts() ? TraversalMode.EDGE_BASED : TraversalMode.NODE_BASED);
            long searchStarted = System.nanoTime();
            TargetSetDijkstra.Result result = search.search(source, context.targets(), context.deadline(), remainingVisits(context.counters().visited));
            context.counters().coreSearchNanos += System.nanoTime() - searchStarted;
            Map<Integer, Cell> cells = new HashMap<>();
            for (int j = 0; j < context.destinations().size(); j++) {
                Snap snap = context.snaps().get(Coordinate.of(context.destinations().get(j)));
                if (!snap.isValid()) continue;
                TargetSetDijkstra.Metrics metric = result.targets().get(snap.getClosestNode());
                if (metric != null) {
                    Cell cell = new Cell(metric.weight(), metric.timeMillis(), metric.distance());
                    validateCell(cell);
                    cells.put(j, cell);
                }
            }
            return new Row(cells, 1, result.visitedStates(), true);
        }
    }

    /** Existing CH preparation can lose original-double near ties. Never expose uncertified candidates. */
    private final class CertifiedCHBackend implements Backend {
        private final RoutingCHGraph preparedGraph;
        private BucketCHMatrix buckets;

        CertifiedCHBackend(RoutingCHGraph preparedGraph) { this.preparedGraph = preparedGraph; }

        BucketCHMatrix.Statistics statistics() { return buckets == null ? null : buckets.statistics(); }

        @Override
        public Row search(Context context, int originIndex) {
            if (context.targets().isEmpty()) return new Row(Map.of(), 0, 0, true);
            long visited = 0, searches = 0;
            if (buckets == null) {
                long started = System.nanoTime();
                buckets = new BucketCHMatrix(new QueryRoutingCHGraph(preparedGraph, context.queryGraph()));
                buckets.prepare(context.targets(), context.deadline(), remainingVisits(context.counters().visited));
                context.counters().chPreparationNanos += System.nanoTime() - started;
                visited = buckets.statistics().visitedStates();
                searches = buckets.statistics().backwardSearches();
            }
            int source = context.snaps().get(Coordinate.of(context.origins().get(originIndex))).getClosestNode();
            long candidateStarted = System.nanoTime();
            BucketCHMatrix.Row candidates = buckets.search(source, remainingVisits(context.counters().visited + visited));
            context.counters().chCandidateNanos += System.nanoTime() - candidateStarted;
            visited = Math.addExact(visited, candidates.visitedStates());
            searches++;

            // Certification uses the validated original-weight search on the identical complete overlay.
            // Every candidate outcome is checked, including reachability and all binary metric values.
            TargetSetDijkstra verifier = new TargetSetDijkstra(context.queryGraph(),
                    context.queryGraph().wrapWeighting(context.weighting()), TraversalMode.NODE_BASED);
            long verificationStarted = System.nanoTime();
            TargetSetDijkstra.Result certified = verifier.search(source, context.targets(), context.deadline(),
                    remainingVisits(context.counters().visited + visited));
            context.counters().chVerificationNanos += System.nanoTime() - verificationStarted;
            context.counters().certificateSearches++;
            context.counters().certificateVisited += certified.visitedStates();
            visited = Math.addExact(visited, certified.visitedStates());
            searches++;
            context.counters().coreSearchNanos = context.counters().chPreparationNanos
                    + context.counters().chCandidateNanos + context.counters().chVerificationNanos;
            Map<Integer, Cell> cells = new HashMap<>();
            for (int j = 0; j < context.destinations().size(); j++) {
                check(context.deadline());
                Snap snap = context.snaps().get(Coordinate.of(context.destinations().get(j)));
                if (!snap.isValid()) continue;
                TargetSetDijkstra.Metrics exact = certified.targets().get(snap.getClosestNode());
                BucketCHMatrix.Metrics candidate = candidates.targets().get(snap.getClosestNode());
                boolean equal = exact == null ? candidate == null : candidate != null
                        && Double.compare(exact.weight(), candidate.weight()) == 0
                        && exact.timeMillis() == candidate.timeMillis()
                        && Double.compare(exact.distance(), candidate.distance()) == 0;
                if (!equal) context.counters().correctedCells++;
                if (exact != null) {
                    Cell cell = new Cell(exact.weight(), exact.timeMillis(), exact.distance());
                    validateCell(cell);
                    cells.put(j, cell);
                }
            }
            return new Row(cells, searches, visited, true);
        }
    }

    private int remainingVisits(long visited) {
        if (visited >= limits.maxVisitedStates()) throw new MatrixException("SEARCH_LIMIT_EXCEEDED", "Whole-request visited-state budget exhausted");
        return (int) (limits.maxVisitedStates() - visited);
    }

    private void validate(MatrixRequest request) {
        if (request == null || request.getOrigins().isEmpty() || request.getDestinations().isEmpty()) throw new MatrixException("INVALID_REQUEST", "Nonempty axes required");
        if (request.getOrigins().size() > limits.maxAxisPoints() || request.getDestinations().size() > limits.maxAxisPoints() ||
                (long) request.getOrigins().size() * request.getDestinations().size() > limits.maxCells())
            throw new MatrixException("REQUEST_LIMIT_EXCEEDED", "Matrix exceeds configured axis or cell budget");
        if (!Set.of("auto", "reference", "flexible", "ch_experimental").contains(request.getAlgorithm())) throw new MatrixException("INVALID_REQUEST", "Unknown matrix algorithm");
        if (request.getOutArrays().isEmpty() || !Set.of("times", "times_ms", "distances", "weights").containsAll(request.getOutArrays()) ||
                new HashSet<>(request.getOutArrays()).size() != request.getOutArrays().size()) throw new MatrixException("INVALID_REQUEST", "Invalid output arrays");
        for (List<GHPoint> axis : List.of(request.getOrigins(), request.getDestinations()))
            for (GHPoint point : axis)
                if (point == null || !Double.isFinite(point.lat) || !Double.isFinite(point.lon) || Math.abs(point.lat) > 90 || Math.abs(point.lon) > 180)
                    throw new MatrixException("INVALID_REQUEST", "Invalid longitude/latitude coordinate");
    }

    private static List<Integer> invalidIndexes(List<GHPoint> axis, Map<Coordinate, Snap> snaps) {
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < axis.size(); i++) if (!snaps.get(Coordinate.of(axis.get(i))).isValid()) result.add(i);
        return result;
    }

    private static List<MatrixSnappedPoint> snapMetadata(List<GHPoint> axis, Map<Coordinate, Snap> snaps) {
        List<MatrixSnappedPoint> result = new ArrayList<>();
        for (GHPoint point : axis) {
            Snap snap = snaps.get(Coordinate.of(point));
            result.add(new MatrixSnappedPoint(snap.isValid() ? List.of(snap.getSnappedPoint().lon, snap.getSnappedPoint().lat) : null,
                    snap.isValid() ? snap.getQueryDistance() : null));
        }
        return result;
    }

    private static void validateCell(Cell cell) {
        if (!Double.isFinite(cell.weight()) || cell.weight() < 0 || cell.millis() < 0 || !Double.isFinite(cell.distance()) || cell.distance() < 0)
            throw new MatrixException("INVALID_COST", "Route has a negative or nonfinite metric");
    }

    private static void check(long deadline) {
        if (Thread.currentThread().isInterrupted()) throw new MatrixException("CANCELLED", "Matrix computation interrupted");
        if (System.nanoTime() >= deadline) throw new MatrixException("DEADLINE_EXCEEDED", "Whole-request matrix deadline expired");
    }

    private static double ms(long nanos) { return nanos / 1000000d; }
}
