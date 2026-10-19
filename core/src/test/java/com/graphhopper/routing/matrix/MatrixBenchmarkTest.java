package com.graphhopper.routing.matrix;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.GraphHopperConfig;
import com.graphhopper.ResponsePath;
import com.graphhopper.config.CHProfile;
import com.graphhopper.config.Profile;
import com.graphhopper.jackson.Jackson;
import com.graphhopper.matrix.MatrixRequest;
import com.graphhopper.routing.ch.PrepareContractionHierarchies;
import com.graphhopper.storage.CHConfig;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.TurnCostsConfig;
import com.graphhopper.util.exceptions.ConnectionNotFoundException;
import com.graphhopper.util.exceptions.PointNotFoundException;
import com.graphhopper.util.exceptions.PointOutOfBoundsException;
import com.graphhopper.util.shapes.GHPoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.lang.reflect.Array;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Opt-in measured fixture benchmark. Timing is not a correctness certificate. */
@EnabledIfSystemProperty(named = "matrix.benchmark", matches = "true")
public class MatrixBenchmarkTest {
    private static final int WARMUPS = 5;
    private static final int REPEATS = 20;
    private static final long SEED = 20261019;
    private static final List<String> METRICS = List.of("times", "times_ms", "distances", "weights");
    private static final int WORKLOAD_COUNT = 8;
    private static final List<String> STAGES = List.of("snapping", "query_graph", "backend_calls", "core_search", "scatter_output", "total");
    private static final ObjectMapper JSON = Jackson.newObjectMapper();
    private static final com.sun.management.ThreadMXBean ALLOCATION_BEAN = allocationBean();
    private static volatile double consumed;

    @Test
    void benchmarkMonaco() throws Exception {
        Path root = findRoot();
        Path output = root.resolve("docs/matrix");
        Files.createDirectories(output);
        Path fixture = root.resolve("core/files/monaco.osm.gz");
        TimedHopper hopper = new TimedHopper();
        GraphHopperConfig config = new GraphHopperConfig()
                .putObject("datareader.file", fixture.toString())
                .putObject("graph.location", root.resolve("target/matrix-benchmark-" + UUID.randomUUID()).toString())
                .putObject("graph.dataaccess.default_type", "RAM")
                .putObject("graph.encoded_values", "car_access,car_average_speed,road_access,foot_access,foot_priority,foot_average_speed,foot_road_access,hike_rating,mtb_rating,country,road_class")
                .putObject("prepare.min_network_size", 0)
                .putObject("prepare.ch.threads", 1)
                .putObject("prepare.subnetworks.threads", 1)
                .putObject("import.osm.ignored_highways", "construction")
                .setProfiles(List.of(
                        new Profile("foot").setCustomModel(GHUtility.loadCustomModelFromJar("foot.json")),
                        new Profile("car").setCustomModel(GHUtility.loadCustomModelFromJar("car.json"))
                                .setTurnCostsConfig(TurnCostsConfig.car().setUTurnCosts(60))))
                .setCHProfiles(List.of(new CHProfile("foot"), new CHProfile("car")));
        List<Map<String, Object>> samples = new ArrayList<>();
        Map<String, Object> metadata = new LinkedHashMap<>();
        Map<String, Object> identities = new LinkedHashMap<>();
        metadata.put("matrix_provenance", identities);
        long importStarted = System.nanoTime();
        try {
            hopper.init(config);
            hopper.importOrLoad();
            metadata.put("import_and_prepare_ms", (System.nanoTime() - importStarted) / 1e6);
            metadata.put("osm_reader_ms", hopper.importNanos / 1e6);
            metadata.put("ch_preparation_ms", hopper.chNanos / 1e6);
            metadata.put("graph_nodes", hopper.getBaseGraph().getNodes());
            metadata.put("graph_edges", hopper.getBaseGraph().getEdges());
            MatrixEngine engine = new MatrixEngine(hopper,
                    new MatrixEngine.Limits(1000, 100000, 1, 600000, Integer.MAX_VALUE));
            List<GHPoint> points = fixturePoints(hopper);
            List<Workload> workloads = List.of(
                    new Workload("local_1x10", points.subList(0, 1), nearest(points, points.get(0), 10)),
                    new Workload("1x100", points.subList(0, 1), points.subList(0, 100)),
                    new Workload("1x1000", points.subList(0, 1), points.subList(0, 1000)),
                    new Workload("10x10", points.subList(0, 10), points.subList(10, 20)),
                    new Workload("10x100", points.subList(0, 10), points.subList(0, 100)),
                    new Workload("100x10", points.subList(0, 100), points.subList(0, 10)),
                    new Workload("100x100", points.subList(0, 100), points.subList(0, 100)),
                    new Workload("duplicate_100x100", duplicates(points, 100, 5), duplicates(points, 100, 5)));
            exportRequests(output.resolve("benchmark-requests.json"), workloads);
            String[] backends = {"reference", "flexible", "ch_pairwise"};
            for (String profile : List.of("foot", "car")) {
                for (Workload workload : workloads) {
                    for (int repeat = -WARMUPS; repeat < REPEATS; repeat++) {
                        int rotation = Math.floorMod(repeat + WARMUPS, backends.length);
                        for (int offset = 0; offset < backends.length; offset++) {
                            String backend = backends[(offset + rotation) % backends.length];
                            Map<String, Object> sample = measure(hopper, engine, profile, workload, backend);
                            if (sample.get("graph_fingerprint") != null) {
                                Map<String, Object> identity = new LinkedHashMap<>();
                                for (String key : List.of("graph_fingerprint", "profile_fingerprint", "build_commit"))
                                    identity.put(key, sample.get(key));
                                Object previous = identities.putIfAbsent(profile, identity);
                                assertTrue(previous == null || previous.equals(identity), "Graph/profile/build changed during benchmark");
                            }
                            if (repeat >= 0) {
                                sample.put("repeat", repeat);
                                samples.add(sample);
                            }
                        }
                    }
                    // Keep completed evidence if a later workload fails.
                    writeSamples(output.resolve("benchmark-samples.csv"), samples);
                    writeSummary(output.resolve("benchmark-results.csv"), samples);
                }
            }
        } finally {
            hopper.close();
            metadata.put("created_utc", Instant.now().toString());
            metadata.put("seed", SEED);
            metadata.put("warmups_per_backend_workload", WARMUPS);
            metadata.put("measured_repeats_per_backend_workload", REPEATS);
            metadata.put("matrix_worker_threads", 1);
            metadata.put("benchmark_limits", Map.of("max_axis_points", 1000, "max_cells", 100000,
                    "max_concurrent_requests", 1, "timeout_ms", 600000, "max_visited_states", Integer.MAX_VALUE));
            metadata.put("coordinate_policy", "Seeded imported tower-node coordinates; duplicates exact; local request selects nearest ten.");
            metadata.put("measured_scope", "In-process request wall time and separately timed JSON serialization, plus their per-sample sum for matrix backends. HTTP transport/client parsing and import/CH preparation are excluded. CH is a minimal-output ordinary routing-call baseline with no matrix JSON serialization phase.");
            metadata.put("timing_columns", Map.of("elapsed_ms", "Compatibility alias of elapsed_request_ms",
                    "elapsed_request_ms", "Complete in-process backend request including production validation; excludes harness outcome validation and serialization",
                    "serialization_ms", "Matrix response serialization with a reused Jackson mapper; null for the CH call baseline",
                    "request_plus_serialization_ms", "Per-sample elapsed_request_ms + serialization_ms; null for the CH call baseline",
                    "stage_columns", "Measured info.local_matrix.timings_ms values; absent/unexposed phases remain null. Stage medians need not sum to the median total."));
            metadata.put("memory_scope", "Heap pool peak sums and request GC counts are captured before JSON serialization; separate serialization GC counts follow. Peak sums are not RSS or simultaneous peak heap.");
            metadata.put("thread_allocation_scope", "Current-thread allocated-byte counter deltas bracket each request and serializer timer. One serial matrix worker executes on this thread; other threads, retained heap and RSS are excluded. Tracking is never enabled by this benchmark; unsupported/disabled readings remain null.");
            metadata.put("thread_allocation_supported", ALLOCATION_BEAN != null && ALLOCATION_BEAN.isThreadAllocatedMemorySupported());
            metadata.put("thread_allocation_enabled_at_end", allocationEnabled());
            metadata.put("java_version", System.getProperty("java.version"));
            metadata.put("java_vm", System.getProperty("java.vm.name"));
            metadata.put("jvm_arguments", ManagementFactory.getRuntimeMXBean().getInputArguments());
            metadata.put("os", System.getProperty("os.name") + " " + System.getProperty("os.version") + " " + System.getProperty("os.arch"));
            metadata.put("available_processors", Runtime.getRuntime().availableProcessors());
            metadata.put("fixture_sha256", sha256(fixture));
            metadata.put("custom_model_sha256", Map.of("foot.json", sha256(root.resolve("core/src/main/resources/com/graphhopper/custom_models/foot.json")),
                    "car.json", sha256(root.resolve("core/src/main/resources/com/graphhopper/custom_models/car.json"))));
            metadata.put("completed_measured_samples", samples.size());
            if (Files.exists(output.resolve("benchmark-requests.json")))
                metadata.put("benchmark_requests_sha256", sha256(output.resolve("benchmark-requests.json")));
            metadata.put("status", samples.size() == 2 * WORKLOAD_COUNT * 3 * REPEATS ? "MEASURED" : "INCOMPLETE");
            metadata.put("limitations", List.of("Bundled Monaco fixture benchmark only; separately acquired Greater Manchester data is used for regional correctness and geometry checks.",
                    "HTTP baseline is separate and is not measured by this harness.",
                    "Performance measurements do not waive unresolved correctness discrepancies or certify release gates."));
            Files.writeString(output.resolve("benchmark-metadata.json"), JSON.writerWithDefaultPrettyPrinter().writeValueAsString(metadata) + "\n");
        }
        assertTrue(samples.size() == 2 * WORKLOAD_COUNT * 3 * REPEATS, "Every configured backend/workload/profile repetition must complete");
    }

    private static Map<String, Object> measure(GraphHopper hopper, MatrixEngine engine, String profile,
                                              Workload workload, String backend) throws IOException {
        List<MemoryPoolMXBean> pools = ManagementFactory.getMemoryPoolMXBeans().stream()
                .filter(pool -> pool.getType() == MemoryType.HEAP).toList();
        pools.forEach(MemoryPoolMXBean::resetPeakUsage);
        long gcCount = gcCount();
        long gcMillis = gcMillis();
        long allocatedBefore = allocatedBytes();
        long start = System.nanoTime();
        Map<String, Object> body;
        if (backend.equals("ch_pairwise")) {
            Double[][] weights = new Double[workload.origins.size()][workload.destinations.size()];
            Long[][] millis = new Long[weights.length][workload.destinations.size()];
            Double[][] distances = new Double[weights.length][workload.destinations.size()];
            long visited = 0, pairCalls = 0;
            boolean visitsComplete = true;
            for (int i = 0; i < weights.length; i++) {
                for (int j = 0; j < weights[i].length; j++) {
                    GHRequest request = new GHRequest(List.of(workload.origins.get(i), workload.destinations.get(j)))
                            .setProfile(profile).setSnapPreventions(List.of())
                            .putHint("instructions", false).putHint("calc_points", false);
                    GHResponse response = hopper.route(request);
                    pairCalls++;
                    if (response.getHints().has("visited_nodes.sum")) visited += response.getHints().getLong("visited_nodes.sum", 0);
                    else visitsComplete = false;
                    if (response.hasErrors()) {
                        assertTrue(response.getErrors().stream().allMatch(error -> error instanceof ConnectionNotFoundException
                                        || error instanceof PointNotFoundException || error instanceof PointOutOfBoundsException), response.getErrors().toString());
                        continue;
                    }
                    ResponsePath path = response.getBest();
                    weights[i][j] = path.getRouteWeight();
                    millis[i][j] = path.getTime();
                    distances[i][j] = path.getDistance();
                }
            }
            body = new HashMap<>();
            body.put("weights", weights);
            body.put("times_ms", millis);
            body.put("distances", distances);
            Map<String, Object> counters = new LinkedHashMap<>();
            counters.put("pair_calls", pairCalls);
            // Upstream does not expose actual core-search invocation counts on every outcome.
            counters.put("searches", null);
            counters.put("visited_states", visitsComplete ? visited : null);
            counters.put("visited_states_known", visited);
            counters.put("visited_states_complete", visitsComplete);
            body.put("info", Map.of("local_matrix", counters));
        } else {
            body = engine.route(new MatrixRequest(profile, workload.origins, workload.destinations, METRICS, false, backend));
        }
        double elapsedMillis = (System.nanoTime() - start) / 1e6;
        Long requestAllocatedBytes = allocationDelta(allocatedBefore, allocatedBytes());
        long gcCountDelta = gcCount() - gcCount;
        long gcMillisDelta = gcMillis() - gcMillis;
        long heapPeakSum = pools.stream().mapToLong(pool -> pool.getPeakUsage().getUsed()).sum();
        Double serializationMillis = null;
        Integer outputJsonBytes = null;
        Long serializationCollections = null, serializationGcMillis = null;
        Long serializationAllocatedBytes = null;
        if (!backend.equals("ch_pairwise")) {
            long serializationGcCountStart = gcCount(), serializationGcMillisStart = gcMillis();
            long serializationAllocatedBefore = allocatedBytes();
            long serializationStart = System.nanoTime();
            byte[] serialized = JSON.writeValueAsBytes(body);
            serializationMillis = (System.nanoTime() - serializationStart) / 1e6;
            serializationAllocatedBytes = allocationDelta(serializationAllocatedBefore, allocatedBytes());
            outputJsonBytes = serialized.length;
            serializationCollections = gcCount() - serializationGcCountStart;
            serializationGcMillis = gcMillis() - serializationGcMillisStart;
        }
        Outcome outcome = validateOutcome(body, workload, backend);
        consumed = outcome.weightChecksum;
        Map<?, ?> info = (Map<?, ?>) ((Map<?, ?>) body.get("info")).get("local_matrix");
        Map<String, Object> sample = new LinkedHashMap<>();
        for (String key : List.of("graph_fingerprint", "profile_fingerprint", "build_commit"))
            sample.put(key, info.get(key));
        sample.put("profile", profile);
        sample.put("workload", workload.name);
        sample.put("backend", backend);
        sample.put("origins", workload.origins.size());
        sample.put("destinations", workload.destinations.size());
        sample.put("elapsed_ms", elapsedMillis);
        sample.put("elapsed_request_ms", elapsedMillis);
        sample.put("serialization_ms", serializationMillis);
        sample.put("request_plus_serialization_ms", serializationMillis == null ? null : elapsedMillis + serializationMillis);
        sample.put("request_thread_allocated_bytes", requestAllocatedBytes);
        sample.put("serialization_thread_allocated_bytes", serializationAllocatedBytes);
        sample.put("completed_cells", (long) workload.origins.size() * workload.destinations.size());
        sample.put("reachable_cells", outcome.reachable);
        sample.put("null_cells", outcome.nullCells);
        sample.put("pair_calls", info.get("pair_calls"));
        sample.put("searches", info.get("searches"));
        sample.put("visited_states", info.get("visited_states"));
        sample.put("heap_pools_peak_sum_bytes", heapPeakSum);
        sample.put("gc_collections", gcCountDelta);
        sample.put("gc_time_ms", gcMillisDelta);
        sample.put("serialization_gc_collections", serializationCollections);
        sample.put("serialization_gc_time_ms", serializationGcMillis);
        // The CH baseline has no matrix JSON serialization contract.
        sample.put("output_json_bytes", outputJsonBytes);
        Map<?, ?> timings = info.get("timings_ms") instanceof Map ? (Map<?, ?>) info.get("timings_ms") : Map.of();
        for (String stage : STAGES) {
            Object timing = timings.get(stage);
            if (timing != null) assertTrue(timing instanceof Number && Double.isFinite(((Number) timing).doubleValue())
                    && ((Number) timing).doubleValue() >= 0, "Invalid measured phase timing");
            sample.put("stage_" + stage + "_ms", timing);
        }
        sample.put("weight_checksum", outcome.weightChecksum);
        return sample;
    }

    private static Outcome validateOutcome(Map<String, Object> body, Workload workload, String backend) {
        assertFalse(body.containsKey("message"), "Whole-request failures are not completed benchmarks");
        List<String> metrics = backend.equals("ch_pairwise") ? List.of("weights", "times_ms", "distances") : METRICS;
        int rows = workload.origins.size(), columns = workload.destinations.size();
        for (String metric : metrics) {
            assertTrue(length(body.get(metric)) == rows, "Wrong row count: " + metric);
            for (int i = 0; i < rows; i++) assertTrue(length(element(body.get(metric), i)) == columns, "Wrong column count: " + metric);
        }
        Set<Integer> invalidFrom = new HashSet<>(), invalidTo = new HashSet<>();
        Set<Long> disconnected = new HashSet<>();
        if (!backend.equals("ch_pairwise")) {
            for (Object value : (List<?>) body.getOrDefault("hints", List.of())) {
                Map<?, ?> hint = (Map<?, ?>) value;
                assertTrue(Set.of("INVALID_SNAP", "CONNECTION_NOT_FOUND").contains(hint.get("details")), "Unknown matrix diagnostic");
                for (Object index : values(hint, "invalid_from_points")) invalidFrom.add(diagnosticIndex(index, rows));
                for (Object index : values(hint, "invalid_to_points")) invalidTo.add(diagnosticIndex(index, columns));
                for (Object pair : values(hint, "point_pairs")) {
                    assertTrue(length(pair) == 2, "Malformed pair diagnostic");
                    int i = diagnosticIndex(element(pair, 0), rows), j = diagnosticIndex(element(pair, 1), columns);
                    disconnected.add((long) i * columns + j);
                }
            }
        }
        long reachable = 0, nullCells = 0;
        double checksum = 0;
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < columns; j++) {
                Object weight = element(element(body.get("weights"), i), j);
                boolean isNull = weight == null;
                for (String metric : metrics) {
                    Object value = element(element(body.get(metric), i), j);
                    assertTrue((value == null) == isNull, "Inconsistent null mask");
                    if (!isNull) assertTrue(value instanceof Number && Double.isFinite(((Number) value).doubleValue())
                            && ((Number) value).doubleValue() >= 0, "Nonfinite or negative metric");
                }
                if (isNull) {
                    assertTrue(backend.equals("ch_pairwise") || invalidFrom.contains(i) || invalidTo.contains(j)
                            || disconnected.contains((long) i * columns + j), "Undiagnosed null cell");
                    nullCells++;
                } else {
                    assertFalse(invalidFrom.contains(i) || invalidTo.contains(j) || disconnected.contains((long) i * columns + j),
                            "Numeric route has an invalid/disconnected diagnostic");
                    reachable++;
                    checksum += ((Number) weight).doubleValue();
                }
            }
        }
        return new Outcome(reachable, nullCells, checksum);
    }

    private static List<?> values(Map<?, ?> map, String key) {
        Object value = map.get(key);
        return value == null ? List.of() : (List<?>) value;
    }

    private static int diagnosticIndex(Object value, int bound) {
        assertTrue(value instanceof Number, "Diagnostic index must be numeric");
        int index = ((Number) value).intValue();
        assertTrue(((Number) value).doubleValue() == index && index >= 0 && index < bound, "Invalid diagnostic index");
        return index;
    }

    private static int length(Object collection) {
        return collection instanceof List ? ((List<?>) collection).size() : Array.getLength(collection);
    }

    private static Object element(Object collection, int index) {
        return collection instanceof List ? ((List<?>) collection).get(index) : Array.get(collection, index);
    }

    private static List<GHPoint> fixturePoints(GraphHopper hopper) {
        NodeAccess nodes = hopper.getBaseGraph().getNodeAccess();
        List<Integer> indexes = new ArrayList<>();
        for (int i = 0; i < hopper.getBaseGraph().getNodes(); i++) indexes.add(i);
        java.util.Collections.shuffle(indexes, new Random(SEED));
        assertTrue(indexes.size() >= 1000, "The configured 1x1000 workload needs 1000 imported nodes");
        List<GHPoint> points = new ArrayList<>();
        for (int i = 0; i < Math.min(1000, indexes.size()); i++) {
            int node = indexes.get(i);
            points.add(new GHPoint(nodes.getLat(node), nodes.getLon(node)));
        }
        return points;
    }

    private static List<GHPoint> nearest(List<GHPoint> points, GHPoint source, int count) {
        return points.stream().sorted(Comparator.comparingDouble(point ->
                Math.pow(point.lat - source.lat, 2) + Math.pow((point.lon - source.lon) * Math.cos(Math.toRadians(source.lat)), 2)))
                .limit(count).toList();
    }

    private static List<GHPoint> duplicates(List<GHPoint> points, int count, int distinct) {
        List<GHPoint> result = new ArrayList<>();
        for (int i = 0; i < count; i++) result.add(points.get(i % distinct));
        return result;
    }

    private static void exportRequests(Path file, List<Workload> workloads) throws IOException {
        List<Map<String, Object>> entries = new ArrayList<>();
        for (String profile : List.of("foot", "car")) {
            for (Workload workload : workloads) {
                Map<String, Object> request = new LinkedHashMap<>();
                request.put("profile", profile);
                request.put("from_points", workload.origins.stream().map(point -> List.of(point.lon, point.lat)).toList());
                request.put("to_points", workload.destinations.stream().map(point -> List.of(point.lon, point.lat)).toList());
                request.put("out_arrays", METRICS);
                request.put("fail_fast", false);
                request.put("local", Map.of("algorithm", "flexible"));
                entries.add(Map.of("name", workload.name, "profile", profile, "request", request));
            }
        }
        Files.writeString(file, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(
                Map.of("seed", SEED, "coordinate_order", "longitude,latitude", "workloads", entries)) + "\n");
    }

    private static void writeSamples(Path file, List<Map<String, Object>> samples) throws IOException {
        List<String> columns = new ArrayList<>(List.of("profile", "workload", "backend", "repeat", "origins", "destinations", "elapsed_ms",
                "graph_fingerprint", "profile_fingerprint", "build_commit",
                "elapsed_request_ms", "serialization_ms", "request_plus_serialization_ms", "completed_cells", "reachable_cells", "null_cells",
                "request_thread_allocated_bytes", "serialization_thread_allocated_bytes",
                "pair_calls", "searches", "visited_states", "heap_pools_peak_sum_bytes", "gc_collections", "gc_time_ms",
                "serialization_gc_collections", "serialization_gc_time_ms", "output_json_bytes", "weight_checksum"));
        for (String stage : STAGES) columns.add("stage_" + stage + "_ms");
        writeCsv(file, samples, columns);
    }

    private static void writeSummary(Path file, List<Map<String, Object>> samples) throws IOException {
        Map<String, List<Map<String, Object>>> groups = new LinkedHashMap<>();
        for (Map<String, Object> sample : samples)
            groups.computeIfAbsent(sample.get("profile") + "," + sample.get("workload") + "," + sample.get("backend"), key -> new ArrayList<>()).add(sample);
        List<Map<String, Object>> summaries = new ArrayList<>();
        for (Map.Entry<String, List<Map<String, Object>>> entry : groups.entrySet()) {
            List<Map<String, Object>> rows = entry.getValue();
            Map<String, Object> first = rows.get(0);
            double median = statistic(rows, "elapsed_request_ms", false);
            double cells = ((Number) first.get("origins")).doubleValue() * ((Number) first.get("destinations")).doubleValue();
            long heap = rows.stream().mapToLong(row -> ((Number) row.get("heap_pools_peak_sum_bytes")).longValue()).max().orElse(0);
            long collections = rows.stream().mapToLong(row -> ((Number) row.get("gc_collections")).longValue()).sum();
            long gcTime = rows.stream().mapToLong(row -> ((Number) row.get("gc_time_ms")).longValue()).sum();
            double reachable = ((Number) first.get("reachable_cells")).doubleValue();
            Map<String, Object> summary = new LinkedHashMap<>();
            for (String key : List.of("profile", "workload", "backend")) summary.put(key, first.get(key));
            summary.put("repeats", rows.size());
            summary.put("median_ms", median);
            summary.put("p95_ms", statistic(rows, "elapsed_request_ms", true));
            for (String metric : List.of("elapsed_request_ms", "serialization_ms", "request_plus_serialization_ms")) {
                summary.put("median_" + metric, statistic(rows, metric, false));
                summary.put("p95_" + metric, statistic(rows, metric, true));
            }
            summary.put("completed_cells_per_second", cells * 1000 / median);
            summary.put("reachable_cells", first.get("reachable_cells"));
            summary.put("reachable_cells_per_second", reachable * 1000 / median);
            Double totalMedian = statistic(rows, "request_plus_serialization_ms", false);
            summary.put("request_plus_serialization_completed_cells_per_second", totalMedian == null ? null : cells * 1000 / totalMedian);
            summary.put("request_plus_serialization_reachable_cells_per_second", totalMedian == null ? null : reachable * 1000 / totalMedian);
            for (String key : List.of("null_cells", "pair_calls", "searches", "visited_states")) summary.put(key, first.get(key));
            summary.put("heap_pools_peak_sum_max_bytes", heap);
            summary.put("gc_collections", collections);
            summary.put("gc_time_ms", gcTime);
            summary.put("serialization_gc_collections", sumIfExposed(rows, "serialization_gc_collections"));
            summary.put("serialization_gc_time_ms", sumIfExposed(rows, "serialization_gc_time_ms"));
            summary.put("output_json_bytes", statistic(rows, "output_json_bytes", false));
            for (String metric : List.of("request_thread_allocated_bytes", "serialization_thread_allocated_bytes")) {
                summary.put("median_" + metric, statistic(rows, metric, false));
                summary.put("p95_" + metric, statistic(rows, metric, true));
            }
            for (String stage : STAGES) {
                summary.put("median_stage_" + stage + "_ms", statistic(rows, "stage_" + stage + "_ms", false));
                summary.put("p95_stage_" + stage + "_ms", statistic(rows, "stage_" + stage + "_ms", true));
            }
            summaries.add(summary);
        }
        if (!summaries.isEmpty()) writeCsv(file, summaries, new ArrayList<>(summaries.get(0).keySet()));
    }

    private static Double statistic(List<Map<String, Object>> rows, String metric, boolean p95) {
        if (rows.stream().anyMatch(row -> !(row.get(metric) instanceof Number))) return null;
        double[] values = rows.stream().mapToDouble(row -> ((Number) row.get(metric)).doubleValue()).sorted().toArray();
        return p95 ? values[(int) Math.ceil(values.length * .95) - 1]
                : (values[(values.length - 1) / 2] + values[values.length / 2]) / 2;
    }

    private static Long sumIfExposed(List<Map<String, Object>> rows, String metric) {
        if (rows.stream().anyMatch(row -> !(row.get(metric) instanceof Number))) return null;
        return rows.stream().mapToLong(row -> ((Number) row.get(metric)).longValue()).sum();
    }

    private static void writeCsv(Path file, List<Map<String, Object>> rows, List<String> columns) throws IOException {
        StringBuilder text = new StringBuilder(String.join(",", columns)).append('\n');
        for (Map<String, Object> row : rows)
            text.append(String.join(",", columns.stream().map(key -> row.get(key) == null ? "" : String.valueOf(row.get(key))).toList())).append('\n');
        Files.writeString(file, text);
    }

    private static long gcCount() {
        return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(bean -> Math.max(0, bean.getCollectionCount())).sum();
    }

    private static com.sun.management.ThreadMXBean allocationBean() {
        try {
            java.lang.management.ThreadMXBean bean = ManagementFactory.getThreadMXBean();
            return bean instanceof com.sun.management.ThreadMXBean ? (com.sun.management.ThreadMXBean) bean : null;
        } catch (SecurityException exception) {
            return null;
        }
    }

    private static long allocatedBytes() {
        if (!allocationEnabled()) return -1;
        try {
            return ALLOCATION_BEAN.getThreadAllocatedBytes(Thread.currentThread().getId());
        } catch (UnsupportedOperationException | SecurityException exception) {
            return -1;
        }
    }

    private static boolean allocationEnabled() {
        try {
            return ALLOCATION_BEAN != null && ALLOCATION_BEAN.isThreadAllocatedMemorySupported()
                    && ALLOCATION_BEAN.isThreadAllocatedMemoryEnabled();
        } catch (UnsupportedOperationException | SecurityException exception) {
            return false;
        }
    }

    private static Long allocationDelta(long before, long after) {
        return before < 0 || after < before ? null : after - before;
    }

    private static long gcMillis() {
        return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(bean -> Math.max(0, bean.getCollectionTime())).sum();
    }

    private static Path findRoot() {
        Path path = Path.of("").toAbsolutePath();
        while (path != null && !Files.exists(path.resolve("core/files/monaco.osm.gz"))) path = path.getParent();
        assertFalse(path == null, "Repository root not found");
        return path;
    }

    private static String sha256(Path file) throws Exception {
        return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
    }

    private record Workload(String name, List<GHPoint> origins, List<GHPoint> destinations) {}
    private record Outcome(long reachable, long nullCells, double weightChecksum) {}

    private static class TimedHopper extends GraphHopper {
        long importNanos;
        long chNanos;

        @Override
        protected void importOSM() {
            long start = System.nanoTime();
            try {
                super.importOSM();
            } finally {
                importNanos += System.nanoTime() - start;
            }
        }

        @Override
        protected Map<String, PrepareContractionHierarchies.Result> prepareCH(boolean closeEarly, List<CHConfig> configsToPrepare) {
            long start = System.nanoTime();
            try {
                return super.prepareCH(closeEarly, configsToPrepare);
            } finally {
                chNanos += System.nanoTime() - start;
            }
        }
    }
}
