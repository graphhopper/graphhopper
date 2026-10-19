/*
 * Licensed under the Apache License, Version 2.0. See LICENSE.txt.
 */
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
import com.graphhopper.routing.Dijkstra;
import com.graphhopper.routing.ev.Subnetwork;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.querygraph.QueryRoutingCHGraph;
import com.graphhopper.routing.util.DefaultSnapFilter;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.storage.index.Snap;
import com.graphhopper.util.PMap;
import com.graphhopper.util.TurnCostsConfig;
import com.graphhopper.util.exceptions.ConnectionNotFoundException;
import com.graphhopper.util.exceptions.PointNotFoundException;
import com.graphhopper.util.exceptions.PointOutOfBoundsException;
import com.graphhopper.util.shapes.GHPoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.InputStream;
import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.lang.reflect.Array;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Opt-in CH matrix milestone experiment. Loads an existing prepared graph read-only,
 * and writes new evidence without modifying any previous milestone report.
 * Timings do not waive the independent algorithm correctness gates.
 */
@EnabledIfSystemProperty(named = "matrix.ch.benchmark", matches = "true")
class MatrixCHMatrixBenchmarkTest {
    private static final ObjectMapper JSON = Jackson.newObjectMapper();
    private static final List<String> ARRAYS = List.of("times", "times_ms", "distances", "weights");
    private static final long SEED = 20261019L;
    private static final com.sun.management.ThreadMXBean ALLOCATION = allocationBean();
    private static volatile double consumed;

    @Test
    void compareDedicatedCHMatrixWithFlexibleAndOrdinaryPreparedCH() throws Exception {
        Path root = findRoot();
        String dataset = System.getProperty("matrix.ch.benchmark.dataset", "monaco");
        assertTrue(List.of("monaco", "manchester").contains(dataset));
        String experimental = System.getProperty("matrix.ch.benchmark.algorithm", "ch_experimental");
        int warmups = Integer.getInteger("matrix.ch.benchmark.warmups", dataset.equals("monaco") ? 5 : 2);
        int repeats = Integer.getInteger("matrix.ch.benchmark.repeats", dataset.equals("monaco") ? 20 : 5);
        assertTrue(warmups >= 1 && repeats >= 3, "Measured comparisons need warmups and at least three repeats");
        Path graph = configured(root, "matrix.ch.benchmark.graph", "target/matrix-" + dataset + "-server");
        Path output = configured(root, "matrix.ch.benchmark.output", "docs/matrix/ch-matrix-benchmark-" + dataset);
        assertFalse(Files.exists(output.resolve("metadata.json")), "Preserve prior evidence; choose a new output directory: " + output);
        Files.createDirectories(output);
        List<Map<String, Object>> samples = new ArrayList<>();
        List<Map<String, Object>> comparisons = new ArrayList<>();
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("status", "RUNNING");
        metadata.put("started_utc", Instant.now().toString());
        metadata.put("dataset", dataset);
        metadata.put("seed", SEED);
        metadata.put("warmups", warmups);
        metadata.put("repeats", repeats);
        metadata.put("experimental_algorithm", experimental);
        metadata.put("prepared_graph", graph.toString());
        metadata.put("prepared_graph_read_only", true);
        metadata.put("new_imports", 0);
        metadata.put("new_ch_preparations", 0);
        metadata.put("thresholds", Map.of("weight_absolute", 1e-7, "weight_relative", 1e-10,
                "distance_m", .002, "duration_ms", 1));
        metadata.put("timing_scope", "Single-JVM serial in-process complete requests; JSON serialization is timed separately for matrix responses. Ordinary CH includes one GraphHopper routing call and path extraction per cell. Import and CH preparation are excluded and were not rerun.");
        metadata.put("memory_scope", "Current-thread allocated bytes are allocation volume, not retained heap or RSS. Heap pool peak sums are not simultaneous peak heap or RSS. Counter access and result checks occur outside request timers.");
        metadata.put("comparison_scope", "First measured outcome per workload/backend is exported in full and compared cell by cell. Every measured repetition is checked against its own backend's first outcome. Pairwise CH differences remain raw; no rounding or alternative-path explanation is inferred from small magnitude alone.");
        metadata.put("jvm_arguments", ManagementFactory.getRuntimeMXBean().getInputArguments());
        metadata.put("java_version", System.getProperty("java.version"));
        metadata.put("java_vm", System.getProperty("java.vm.name"));
        metadata.put("os", System.getProperty("os.name") + " " + System.getProperty("os.version") + " " + System.getProperty("os.arch"));
        metadata.put("available_processors", Runtime.getRuntime().availableProcessors());
        metadata.put("thread_allocation_enabled", allocatedBytes() >= 0);
        metadata.put("source_sha256", sourceHashes(root));
        metadata.put("compiled_class_sha256", compiledClassHashes(root));
        GraphHopper hopper = new GraphHopper();
        try {
            long loadStart = System.nanoTime();
            hopper.init(config(graph));
            hopper.setAllowWrites(false);
            assertTrue(hopper.load(), "An existing prepared graph is required; this benchmark does not import or prepare");
            metadata.put("prepared_graph_load_ms", (System.nanoTime() - loadStart) / 1e6);
            assertEquals(java.util.Set.of("foot", "car"), hopper.getCHGraphs().keySet());
            metadata.put("graph_nodes", hopper.getBaseGraph().getNodes());
            metadata.put("graph_edges", hopper.getBaseGraph().getEdges());
            MatrixProvenance provenance = new MatrixProvenance(hopper);
            metadata.put("graph_fingerprint", provenance.graphFingerprint);
            metadata.put("profile_fingerprints", Map.of("foot", provenance.profileFingerprint("foot"), "car", provenance.profileFingerprint("car")));
            metadata.put("build_commit", provenance.buildCommit);
            metadata.put("cache_file_manifest", cacheManifest(graph));
            MatrixEngine engine = new MatrixEngine(hopper, new MatrixEngine.Limits(1000, 100000, 1, 600000, Integer.MAX_VALUE));
            List<Workload> workloads = workloads(hopper, dataset);
            exportRequests(output.resolve("requests.json"), workloads, experimental);
            metadata.put("requests_sha256", sha256(output.resolve("requests.json")));
            metadata.put("coordinate_policy", dataset.equals("monaco")
                    ? "Seed-shuffled imported tower-node coordinates; first 100 are used, plus a nearest-ten local axis and exact duplicates."
                    : "100 imported tower nodes nearest Manchester town-hall coordinates, shuffled with the fixed seed. 1x3 uses supplied city coordinates. Local-node workloads do not represent the entire extract's route-length distribution.");
            List<String> backendNames = List.of("flexible", experimental, "ch_pairwise");
            Map<String, Object> outcomes = new LinkedHashMap<>();
            int experimentalVsFlexibleMismatches = 0;
            boolean experimentalVsFlexibleExactlyEqual = true;
            for (Workload workload : workloads) {
                Map<String, Map<String, Object>> firstBodies = new LinkedHashMap<>();
                for (int repeat = -warmups; repeat < repeats; repeat++) {
                    int rotation = Math.floorMod(repeat + warmups, backendNames.size());
                    for (int index = 0; index < backendNames.size(); index++) {
                        String backend = backendNames.get((rotation + index) % backendNames.size());
                        Measured measured = measure(hopper, engine, "foot", workload, backend);
                        if (repeat < 0) continue;
                        if (repeat == 0) firstBodies.put(backend, measured.body);
                        else assertEquals(JSON.valueToTree(metrics(firstBodies.get(backend))), JSON.valueToTree(metrics(measured.body)),
                                "Every raw cell must be stable across repeats: " + workload.name + "/" + backend + "/" + repeat);
                        measured.sample.put("repeat", repeat);
                        samples.add(measured.sample);
                    }
                }
                outcomes.put(workload.name, firstBodies.entrySet().stream().collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey, entry -> metrics(entry.getValue()), (a, b) -> a, LinkedHashMap::new)));
                Map<String, Object> candidateComparison = compare(workload, experimental, firstBodies.get(experimental), "flexible", firstBodies.get("flexible"));
                comparisons.add(candidateComparison);
                experimentalVsFlexibleMismatches += ((Number) candidateComparison.get("mismatched_cells")).intValue();
                experimentalVsFlexibleExactlyEqual &= JSON.valueToTree(metrics(firstBodies.get(experimental)))
                        .equals(JSON.valueToTree(metrics(firstBodies.get("flexible"))));
                comparisons.add(compare(workload, "ch_pairwise", firstBodies.get("ch_pairwise"), "flexible", firstBodies.get("flexible")));
                comparisons.add(compare(workload, experimental, firstBodies.get(experimental), "ch_pairwise", firstBodies.get("ch_pairwise")));
                writeJson(output.resolve("first-outcomes.json"), outcomes);
                writeJson(output.resolve("comparisons.json"), comparisons);
                writeCsv(output.resolve("samples.csv"), samples);
                System.out.println("CH matrix benchmark " + dataset + "/" + workload.name + " completed; experimental/flexible mismatched cells="
                        + candidateComparison.get("mismatched_cells"));
            }
            Workload fallback = workloads.get(0);
            Map<String, Object> carFlexible = engine.route(new MatrixRequest("car", fallback.origins, fallback.destinations, ARRAYS, false, "flexible"));
            Map<String, Object> carRequestedCH = engine.route(new MatrixRequest("car", fallback.origins, fallback.destinations, ARRAYS, false, experimental));
            Map<String, Object> fallbackCheck = compare(fallback, experimental, carRequestedCH, "flexible", carFlexible);
            fallbackCheck.put("profile", "car");
            fallbackCheck.put("requested_ch_info", localInfo(carRequestedCH));
            fallbackCheck.put("flexible_info", localInfo(carFlexible));
            fallbackCheck.put("requested_ch_metrics", metrics(carRequestedCH));
            fallbackCheck.put("flexible_metrics", metrics(carFlexible));
            writeJson(output.resolve("car-fallback.json"), fallbackCheck);
            assertEquals(0, ((Number) fallbackCheck.get("mismatched_cells")).intValue(), "Turn-aware car fallback must preserve flexible results");
            assertEquals("flexible", localInfo(carRequestedCH).get("algorithm"));
            assertEquals(experimental, localInfo(carRequestedCH).get("requested_algorithm"));
            assertEquals("TURN_COST_PROFILE_UNSUPPORTED", localInfo(carRequestedCH).get("fallback_reason"));
            assertEquals(JSON.valueToTree(metrics(carFlexible)), JSON.valueToTree(metrics(carRequestedCH)));
            // All independent candidate diagnostics run after the timed experiments.
            // Their work is never included in, or subtracted from, an API measurement.
            Map<String, Object> rawCandidates = new LinkedHashMap<>();
            int independentOracleFlags = 0;
            List<Map<String, Object>> pairProofs = new ArrayList<>(), indexedPairProofs = new ArrayList<>();
            try (BufferedWriter proofWriter = new BufferedWriter(new OutputStreamWriter(new GZIPOutputStream(
                    Files.newOutputStream(output.resolve("pairwise-ch-witnesses.jsonl.gz"))), StandardCharsets.UTF_8))) {
                for (Workload workload : workloads) {
                    @SuppressWarnings("unchecked") Map<String, Object> first = (Map<String, Object>) outcomes.get(workload.name);
                    @SuppressWarnings("unchecked") Map<String, Object> flexible = (Map<String, Object>) first.get("flexible");
                    @SuppressWarnings("unchecked") Map<String, Object> ordinaryCH = (Map<String, Object>) first.get("ch_pairwise");
                    Map<String, Object> diagnostic = rawCandidateOracle(hopper, workload, flexible, ordinaryCH, proofWriter, pairProofs, indexedPairProofs);
                    rawCandidates.put(workload.name, diagnostic);
                    @SuppressWarnings("unchecked") Map<String, Object> oracleComparison = (Map<String, Object>) diagnostic.get("independent_oracle_vs_flexible");
                    independentOracleFlags += ((Number) oracleComparison.get("mismatched_cells")).intValue();
                    writeJson(output.resolve("raw-candidates.json"), rawCandidates);
                    writeJson(output.resolve("pairwise-ch-reconciliation.json"), Map.of("unique_case_proofs", pairProofs, "indexed_flag_cases", indexedPairProofs,
                            "unexplained_unique_case_proofs", pairProofs.stream().filter(proof -> !Boolean.TRUE.equals(proof.get("proved"))).count()));
                    System.out.println("Raw candidate oracle " + dataset + "/" + workload.name + " completed");
                }
            }
            metadata.put("independent_oracle_vs_flexible_flagged_cells", independentOracleFlags);
            metadata.put("pairwise_ch_indexed_flag_cases", indexedPairProofs.size());
            metadata.put("pairwise_ch_unique_case_proofs", pairProofs.size());
            long unexplainedPairwise = pairProofs.stream().filter(proof -> !Boolean.TRUE.equals(proof.get("proved"))).count();
            metadata.put("pairwise_ch_unexplained_unique_case_proofs", unexplainedPairwise);
            metadata.put("experimental_vs_flexible_mismatched_cells", experimentalVsFlexibleMismatches);
            metadata.put("experimental_vs_flexible_exact_metric_equality", experimentalVsFlexibleExactlyEqual);
            metadata.put("completed_measured_samples", samples.size());
            metadata.put("expected_measured_samples", workloads.size() * backendNames.size() * repeats);
            metadata.put("status", experimentalVsFlexibleMismatches == 0 ? "MEASURED_NO_EXPERIMENTAL_FLEXIBLE_FLAGS" : "MEASURED_WITH_EXPERIMENTAL_FLEXIBLE_FLAGS");
            assertEquals(workloads.size() * backendNames.size() * repeats, samples.size());
            assertEquals(0, experimentalVsFlexibleMismatches, "Certified API results must pass the fixed flexible comparison; inspect the complete raw mismatch ledger");
            assertTrue(experimentalVsFlexibleExactlyEqual, "Certified API must return exactly the flexible metrics on the same shared overlay");
            assertEquals(0, independentOracleFlags, "Independent plain Dijkstra must validate certified flexible outputs at fixed thresholds");
            assertEquals(0, unexplainedPairwise, "Every new ordinary CH baseline flag needs fresh independent path evidence; inspect the compressed witnesses");
        } catch (Exception | AssertionError failure) {
            metadata.put("status", "FAIL");
            metadata.put("failure", failure.toString());
            throw failure;
        } finally {
            hopper.close();
            if (metadata.containsKey("cache_file_manifest")) {
                List<Map<String, Object>> after = cacheManifest(graph);
                boolean unchanged = metadata.get("cache_file_manifest").equals(after);
                metadata.put("cache_file_bytes_unchanged_after_close", unchanged);
                if (!unchanged) metadata.put("status", "FAIL_PREPARED_CACHE_CHANGED");
            }
            metadata.put("completed_utc", Instant.now().toString());
            metadata.put("completed_measured_samples", samples.size());
            Map<String, String> evidenceHashes = new LinkedHashMap<>();
            for (String name : List.of("requests.json", "first-outcomes.json", "comparisons.json", "samples.csv", "car-fallback.json", "raw-candidates.json", "pairwise-ch-reconciliation.json", "pairwise-ch-witnesses.jsonl.gz"))
                if (Files.exists(output.resolve(name))) evidenceHashes.put(name, sha256(output.resolve(name)));
            metadata.put("evidence_sha256", evidenceHashes);
            writeJson(output.resolve("metadata.json"), metadata);
        }
    }

    private static Measured measure(GraphHopper hopper, MatrixEngine engine, String profile, Workload work, String backend) throws Exception {
        List<MemoryPoolMXBean> pools = ManagementFactory.getMemoryPoolMXBeans().stream().filter(p -> p.getType() == MemoryType.HEAP).toList();
        pools.forEach(MemoryPoolMXBean::resetPeakUsage);
        long gcBefore = gcCount(), allocatedBefore = allocatedBytes();
        long start = System.nanoTime();
        Map<String, Object> body = backend.equals("ch_pairwise") ? pairwiseCH(hopper, profile, work)
                : engine.route(new MatrixRequest(profile, work.origins, work.destinations, ARRAYS, false, backend));
        double elapsed = (System.nanoTime() - start) / 1e6;
        Long allocated = allocationDelta(allocatedBefore, allocatedBytes());
        long collections = gcCount() - gcBefore;
        long heapPools = pools.stream().mapToLong(p -> p.getPeakUsage().getUsed()).sum();
        validate(body, work);
        Map<String, Object> sample = new LinkedHashMap<>();
        sample.put("profile", profile); sample.put("workload", work.name); sample.put("backend", backend);
        sample.put("origins", work.origins.size()); sample.put("destinations", work.destinations.size());
        sample.put("elapsed_request_ms", elapsed); sample.put("request_thread_allocated_bytes", allocated);
        sample.put("request_gc_collections", collections); sample.put("heap_pools_peak_sum_bytes", heapPools);
        sample.put("serialization_ms", null); sample.put("serialization_thread_allocated_bytes", null);
        sample.put("request_plus_serialization_ms", null); sample.put("output_json_bytes", null);
        if (!backend.equals("ch_pairwise")) {
            long serializationAllocatedBefore = allocatedBytes(), serializationStart = System.nanoTime();
            byte[] bytes = JSON.writeValueAsBytes(body);
            double serialization = (System.nanoTime() - serializationStart) / 1e6;
            sample.put("serialization_ms", serialization);
            sample.put("serialization_thread_allocated_bytes", allocationDelta(serializationAllocatedBefore, allocatedBytes()));
            sample.put("request_plus_serialization_ms", elapsed + serialization);
            sample.put("output_json_bytes", bytes.length);
        }
        Map<?, ?> info = localInfo(body);
        if (!backend.equals("ch_pairwise")) {
            assertEquals(backend, info.get("algorithm"), "A measured backend must actually be selected");
            assertEquals(backend, info.get("requested_algorithm"));
            assertFalse(info.containsKey("fallback_reason"));
            if (backend.equals("ch_experimental")) {
                assertEquals(true, info.get("experimental"));
                assertEquals("flexible_each_origin", info.get("optimality_verification"));
                Map<?, ?> ch = (Map<?, ?>) info.get("ch_matrix");
                assertEquals(false, ch.get("candidate_optimality_guaranteed"));
                assertEquals("distinct_origin_coordinate_by_destination_index", ch.get("corrected_cells_basis"));
                assertEquals(ch.get("forward_searches"), ch.get("certificate_searches"));
                assertTrue(((Number) ch.get("backward_searches")).longValue() > 0);
                Map<?, ?> timings = (Map<?, ?>) info.get("timings_ms");
                for (String key : List.of("ch_bucket_preparation", "ch_candidates", "ch_verification"))
                    assertTrue(timings.get(key) instanceof Number && ((Number) timings.get(key)).doubleValue() >= 0);
            }
        }
        // All production counters and phase timings are retained in a separate JSON column,
        // rather than pretending unknown counters are zero or narrowing future instrumentation.
        sample.put("local_matrix_info_json", JSON.writeValueAsString(info));
        double checksum = 0; long reachable = 0;
        for (int i = 0; i < work.origins.size(); i++) for (int j = 0; j < work.destinations.size(); j++) {
            Number value = cell(body, "weights", i, j);
            if (value != null) { checksum += value.doubleValue(); reachable++; }
        }
        consumed = checksum;
        sample.put("weight_checksum", checksum); sample.put("reachable_cells", reachable);
        sample.put("null_cells", (long) work.origins.size() * work.destinations.size() - reachable);
        return new Measured(body, sample);
    }

    private static Map<String, Object> pairwiseCH(GraphHopper hopper, String profile, Workload work) {
        Double[][] weights = new Double[work.origins.size()][work.destinations.size()];
        Double[][] distances = new Double[weights.length][work.destinations.size()];
        Long[][] millis = new Long[weights.length][work.destinations.size()];
        Long[][] seconds = new Long[weights.length][work.destinations.size()];
        long calls = 0, visited = 0; boolean completeVisited = true;
        for (int i = 0; i < weights.length; i++) for (int j = 0; j < weights[i].length; j++) {
            GHResponse response = hopper.route(new GHRequest(List.of(work.origins.get(i), work.destinations.get(j)))
                    .setProfile(profile).setSnapPreventions(List.of()).putHint("instructions", false).putHint("calc_points", false));
            calls++;
            if (response.getHints().has("visited_nodes.sum")) visited += response.getHints().getLong("visited_nodes.sum", 0);
            else completeVisited = false;
            if (response.hasErrors()) {
                assertTrue(response.getErrors().stream().allMatch(error -> error instanceof ConnectionNotFoundException
                        || error instanceof PointNotFoundException || error instanceof PointOutOfBoundsException), response.getErrors().toString());
                continue;
            }
            assertTrue(response.getDebugInfo().contains("dijkstrabi|ch"), "Ordinary node-based foot baseline must use prepared CH Dijkstra: " + response.getDebugInfo());
            ResponsePath path = response.getBest();
            weights[i][j] = path.getRouteWeight(); distances[i][j] = path.getDistance(); millis[i][j] = path.getTime(); seconds[i][j] = path.getTime() / 1000;
        }
        Map<String, Object> counters = new LinkedHashMap<>();
        counters.put("pair_calls", calls); counters.put("searches", null);
        counters.put("visited_states", completeVisited ? visited : null); counters.put("visited_states_known", visited);
        counters.put("visited_states_complete", completeVisited);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("weights", weights); body.put("distances", distances); body.put("times_ms", millis); body.put("times", seconds);
        body.put("info", Map.of("local_matrix", counters));
        return body;
    }

    private static Map<String, Object> rawCandidateOracle(GraphHopper hopper, Workload work, Map<String, Object> flexible,
                                                         Map<String, Object> ordinaryCH, BufferedWriter proofWriter,
                                                         List<Map<String, Object>> pairProofs, List<Map<String, Object>> indexedPairProofs) throws Exception {
        long started = System.nanoTime();
        Weighting raw = hopper.createWeighting(hopper.getProfile("foot"), new PMap());
        DefaultSnapFilter filter = new DefaultSnapFilter(raw, hopper.getEncodingManager().getBooleanEncodedValue(Subnetwork.key("foot")));
        Map<Coordinate, Snap> snaps = new LinkedHashMap<>();
        List<GHPoint> all = new ArrayList<>(work.origins); all.addAll(work.destinations);
        for (GHPoint point : all) snaps.computeIfAbsent(Coordinate.of(point), ignored -> hopper.getBaseGraph().getBounds().contains(point.lat, point.lon)
                ? hopper.getLocationIndex().findClosest(point.lat, point.lon, filter) : new Snap(point.lat, point.lon));
        List<Snap> valid = snaps.values().stream().filter(Snap::isValid).toList();
        assertFalse(valid.isEmpty());
        assertEquals(snaps.size(), valid.size(), "This performance grid uses valid snaps; invalid-snap semantics are tested separately");
        QueryGraph query = QueryGraph.create(hopper.getBaseGraph(), valid);
        Weighting weighting = query.wrapWeighting(raw);
        Set<Integer> targets = new HashSet<>();
        for (GHPoint point : work.destinations) if (snaps.get(Coordinate.of(point)).isValid()) targets.add(snaps.get(Coordinate.of(point)).getClosestNode());
        BucketCHMatrix buckets = new BucketCHMatrix(new QueryRoutingCHGraph(hopper.getCHGraphs().get("foot"), query));
        buckets.prepare(targets, System.nanoTime() + java.util.concurrent.TimeUnit.MINUTES.toNanos(10), Integer.MAX_VALUE);
        Map<Coordinate, BucketCHMatrix.Row> candidateRows = new HashMap<>();
        Map<Long, com.graphhopper.routing.Path> oraclePaths = new HashMap<>();
        Map<CoordinatePair, Map<String, Object>> workloadProofs = new HashMap<>();
        Map<String, Object> candidate = emptyMetrics(work.origins.size(), work.destinations.size());
        Map<String, Object> oracle = emptyMetrics(work.origins.size(), work.destinations.size());
        long plainDijkstraSearches = 0;
        for (int i = 0; i < work.origins.size(); i++) {
            Coordinate coordinate = Coordinate.of(work.origins.get(i)); Snap from = snaps.get(coordinate);
            if (!from.isValid()) continue;
            BucketCHMatrix.Row row = candidateRows.computeIfAbsent(coordinate, ignored -> buckets.search(from.getClosestNode(), Integer.MAX_VALUE));
            for (int j = 0; j < work.destinations.size(); j++) {
                Snap to = snaps.get(Coordinate.of(work.destinations.get(j)));
                if (!to.isValid()) continue;
                BucketCHMatrix.Metrics cell = row.targets().get(to.getClosestNode());
                if (cell != null) putMetric(candidate, i, j, cell.weight(), cell.timeMillis(), cell.distance());
                long pair = ((long) from.getClosestNode() << 32) | (to.getClosestNode() & 0xffffffffL);
                com.graphhopper.routing.Path path = oraclePaths.get(pair);
                if (path == null) {
                    path = new Dijkstra(query, weighting, TraversalMode.NODE_BASED).calcPath(from.getClosestNode(), to.getClosestNode());
                    oraclePaths.put(pair, path); plainDijkstraSearches++;
                }
                if (path.isFound()) putMetric(oracle, i, j, path.getWeight(), path.getTime(), path.getDistance());
                if (mismatches(ordinaryCH, flexible, i, j)) {
                    CoordinatePair pairCoordinates = new CoordinatePair(coordinate, Coordinate.of(work.destinations.get(j)));
                    Map<String, Object> summary = workloadProofs.get(pairCoordinates);
                    if (summary == null) {
                        CHMatrixPairwiseAudit.Audit audit = CHMatrixPairwiseAudit.audit(hopper, "foot", work.origins.get(i), work.destinations.get(j),
                                numericTriple(ordinaryCH, i, j), numericTriple(flexible, i, j), query, from.getClosestNode(), to.getClosestNode());
                        String id = work.name + "-proof-" + pairProofs.size();
                        Map<String, Object> witness = new LinkedHashMap<>(audit.witness());
                        witness.put("proof_id", id); witness.put("workload", work.name);
                        String line = JSON.writeValueAsString(witness);
                        proofWriter.write(line); proofWriter.newLine();
                        summary = new LinkedHashMap<>(audit.summary());
                        summary.put("proof_id", id); summary.put("workload", work.name); summary.put("proved", audit.proved());
                        summary.put("witness_line_sha256", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(line.getBytes(StandardCharsets.UTF_8))));
                        pairProofs.add(summary); workloadProofs.put(pairCoordinates, summary);
                    }
                    indexedPairProofs.add(Map.of("workload", work.name, "origin_index", i, "destination_index", j,
                            "proof_id", summary.get("proof_id"), "proved", summary.get("proved"),
                            "observed_ch", numericTriple(ordinaryCH, i, j), "observed_flexible", numericTriple(flexible, i, j)));
                }
            }
        }
        proofWriter.flush();
        validate(candidate, work); validate(oracle, work);
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("status", "RAW_CANDIDATE_ONLY_NOT_AN_EXACT_API_BACKEND");
        report.put("scope", "Untimed original BucketCHMatrix results on a reconstruction of the identical complete shared QueryGraph, before mandatory API verification/correction. Plain upstream Dijkstra is independently instantiated per distinct snapped-node pair on that same overlay. No geometry, path extraction, or original-edge expansion cost is removed from measured API requests.");
        report.put("candidate_metrics", candidate); report.put("independent_plain_dijkstra_metrics", oracle);
        report.put("raw_candidate_vs_independent_oracle", compare(work, "raw_bucket_ch_candidate", candidate, "independent_plain_dijkstra", oracle));
        report.put("independent_oracle_vs_flexible", compare(work, "independent_plain_dijkstra", oracle, "flexible", flexible));
        report.put("plain_dijkstra_searches", plainDijkstraSearches);
        report.put("raw_bucket_statistics", buckets.statistics());
        report.put("diagnostic_elapsed_ms", (System.nanoTime() - started) / 1e6);
        return report;
    }

    private static boolean mismatches(Map<String, Object> actual, Map<String, Object> expected, int i, int j) {
        Number a = cell(actual, "weights", i, j), b = cell(expected, "weights", i, j);
        if ((a == null) != (b == null)) return true;
        return a != null && (Math.abs(a.doubleValue() - b.doubleValue()) > 1e-7 + Math.abs(b.doubleValue()) * 1e-10
                || Math.abs(cell(actual, "distances", i, j).doubleValue() - cell(expected, "distances", i, j).doubleValue()) > .002
                || Math.abs(cell(actual, "times_ms", i, j).longValue() - cell(expected, "times_ms", i, j).longValue()) > 1);
    }
    private static Map<String, Number> numericTriple(Map<String, Object> body, int i, int j) {
        Map<String, Number> triple = new LinkedHashMap<>();
        for (String metric : List.of("weights", "times_ms", "distances")) triple.put(metric, cell(body, metric, i, j));
        return triple;
    }

    private static Map<String, Object> emptyMetrics(int rows, int columns) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String metric : ARRAYS) {
            List<List<Number>> values = new ArrayList<>();
            for (int i = 0; i < rows; i++) values.add(new ArrayList<>(java.util.Collections.nCopies(columns, null)));
            result.put(metric, values);
        }
        return result;
    }
    @SuppressWarnings("unchecked")
    private static void putMetric(Map<String, Object> body, int i, int j, double weight, long time, double distance) {
        ((List<List<Number>>) body.get("weights")).get(i).set(j, weight);
        ((List<List<Number>>) body.get("times_ms")).get(i).set(j, time);
        ((List<List<Number>>) body.get("times")).get(i).set(j, time / 1000);
        ((List<List<Number>>) body.get("distances")).get(i).set(j, distance);
    }

    private static void validate(Map<String, Object> body, Workload work) {
        assertFalse(body.containsKey("message"), "Whole-request failure is not a completed benchmark");
        for (String metric : ARRAYS) {
            assertEquals(work.origins.size(), length(body.get(metric)));
            for (int i = 0; i < work.origins.size(); i++) assertEquals(work.destinations.size(), length(element(body.get(metric), i)));
        }
        for (int i = 0; i < work.origins.size(); i++) for (int j = 0; j < work.destinations.size(); j++) {
            boolean missing = cell(body, "weights", i, j) == null;
            for (String metric : ARRAYS) {
                Number value = cell(body, metric, i, j);
                assertEquals(missing, value == null, "Inconsistent null mask");
                if (value != null) assertTrue(Double.isFinite(value.doubleValue()) && value.doubleValue() >= 0);
            }
            if (!missing) assertEquals(cell(body, "times_ms", i, j).longValue() / 1000, cell(body, "times", i, j).longValue());
        }
    }

    private static Map<String, Object> compare(Workload work, String actualName, Map<String, Object> actual,
                                               String expectedName, Map<String, Object> expected) {
        List<Map<String, Object>> mismatches = new ArrayList<>();
        long reachable = 0, nulls = 0;
        for (int i = 0; i < work.origins.size(); i++) for (int j = 0; j < work.destinations.size(); j++) {
            Number a = cell(actual, "weights", i, j), b = cell(expected, "weights", i, j);
            List<String> issues = new ArrayList<>();
            if ((a == null) != (b == null)) issues.add("null_mask");
            if (b == null) nulls++; else reachable++;
            if (a != null && b != null) {
                if (Math.abs(a.doubleValue() - b.doubleValue()) > 1e-7 + Math.abs(b.doubleValue()) * 1e-10) issues.add("weight");
                if (Math.abs(cell(actual, "distances", i, j).doubleValue() - cell(expected, "distances", i, j).doubleValue()) > .002) issues.add("distance");
                if (Math.abs(cell(actual, "times_ms", i, j).longValue() - cell(expected, "times_ms", i, j).longValue()) > 1) issues.add("duration");
            }
            if (!issues.isEmpty()) mismatches.add(Map.of("origin_index", i, "destination_index", j,
                    "issues", issues, "actual", cellMetrics(actual, i, j), "expected", cellMetrics(expected, i, j)));
        }
        Map<String, Object> comparison = new LinkedHashMap<>();
        comparison.put("workload", work.name); comparison.put("profile", "foot"); comparison.put("actual", actualName); comparison.put("expected", expectedName);
        comparison.put("cells", work.origins.size() * work.destinations.size()); comparison.put("expected_reachable_cells", reachable);
        comparison.put("expected_null_cells", nulls); comparison.put("mismatched_cells", mismatches.size()); comparison.put("all_mismatches", mismatches);
        comparison.put("status", mismatches.isEmpty() ? "NO_FLAGS_AT_FIXED_THRESHOLDS" : "RAW_MISMATCHES_REQUIRE_INDEPENDENT_EXPLANATION");
        return comparison;
    }

    private static List<Workload> workloads(GraphHopper hopper, String dataset) {
        NodeAccess nodes = hopper.getBaseGraph().getNodeAccess();
        List<Integer> ids = new ArrayList<>();
        for (int i = 0; i < hopper.getBaseGraph().getNodes(); i++) ids.add(i);
        if (dataset.equals("manchester")) {
            ids.sort(Comparator.comparingDouble(n -> squaredDistance(nodes.getLat(n), nodes.getLon(n), 53.4795, -2.2453)));
            ids = new ArrayList<>(ids.subList(0, 100));
        }
        java.util.Collections.shuffle(ids, new Random(SEED));
        assertTrue(ids.size() >= 100);
        List<GHPoint> points = ids.subList(0, 100).stream().map(n -> new GHPoint(nodes.getLat(n), nodes.getLon(n))).toList();
        List<GHPoint> local = points.stream().sorted(Comparator.comparingDouble(p -> squaredDistance(p.lat, p.lon, points.get(0).lat, points.get(0).lon))).limit(10).toList();
        List<GHPoint> one = dataset.equals("manchester") ? List.of(new GHPoint(53.4774, -2.2309)) : points.subList(0, 1);
        List<GHPoint> three = dataset.equals("manchester") ? List.of(new GHPoint(53.4808, -2.2426), new GHPoint(53.4875, -2.242), new GHPoint(53.4761, -2.2541)) : points.subList(0, 3);
        return List.of(new Workload("1x3", one, three),
                new Workload("1x10", points.subList(0, 1), local),
                new Workload("1x100", points.subList(0, 1), points),
                new Workload("10x10", points.subList(0, 10), points.subList(10, 20)),
                new Workload("10x100", points.subList(0, 10), points),
                new Workload("100x10", points, points.subList(0, 10)),
                new Workload("100x100", points, points),
                new Workload("duplicate_100x100", duplicates(points), duplicates(points)));
    }

    private static List<GHPoint> duplicates(List<GHPoint> points) {
        List<GHPoint> result = new ArrayList<>(); for (int i = 0; i < 100; i++) result.add(points.get(i % 5)); return result;
    }
    private static double squaredDistance(double lat, double lon, double fromLat, double fromLon) {
        return Math.pow(lat - fromLat, 2) + Math.pow((lon - fromLon) * Math.cos(Math.toRadians(fromLat)), 2);
    }
    private static GraphHopperConfig config(Path graph) {
        return new GraphHopperConfig().putObject("graph.location", graph.toString()).putObject("graph.dataaccess.default_type", "RAM_STORE")
                .putObject("graph.encoded_values", "car_access,car_average_speed,road_access,foot_access,foot_average_speed,foot_priority,foot_road_access,hike_rating,mtb_rating,country,road_class")
                .putObject("prepare.min_network_size", 0).putObject("prepare.subnetworks.threads", 1).putObject("prepare.ch.threads", 1)
                .putObject("import.osm.ignored_highways", "construction")
                .setProfiles(List.of(fileProfile("foot"), fileProfile("car").setTurnCostsConfig(TurnCostsConfig.car().setUTurnCosts(60))))
                .setCHProfiles(List.of(new CHProfile("foot"), new CHProfile("car"))).setLMProfiles(List.of());
    }
    private static Profile fileProfile(String name) {
        Profile profile = new Profile(name); profile.getHints().remove("custom_model"); return profile.putHint("custom_model_files", List.of(name + ".json"));
    }
    private static Map<?, ?> localInfo(Map<String, Object> body) { return (Map<?, ?>) ((Map<?, ?>) body.get("info")).get("local_matrix"); }
    private static Number cell(Map<String, Object> body, String metric, int i, int j) { return (Number) element(element(body.get(metric), i), j); }
    private static int length(Object values) { return values instanceof List<?> list ? list.size() : Array.getLength(values); }
    private static Object element(Object values, int index) { return values instanceof List<?> list ? list.get(index) : Array.get(values, index); }
    private static Map<String, Object> cellMetrics(Map<String, Object> body, int i, int j) {
        Map<String, Object> values = new LinkedHashMap<>(); for (String metric : ARRAYS) values.put(metric, cell(body, metric, i, j)); return values;
    }
    private static Map<String, Object> metrics(Map<String, Object> body) {
        Map<String, Object> values = new LinkedHashMap<>(); for (String metric : ARRAYS) values.put(metric, body.get(metric)); return values;
    }
    private static void exportRequests(Path path, List<Workload> workloads, String experimental) throws Exception {
        List<Map<String, Object>> requests = new ArrayList<>();
        for (Workload workload : workloads) for (String algorithm : List.of("flexible", experimental)) requests.add(Map.of("workload", workload.name, "backend", algorithm,
                "request", Map.of("profile", "foot", "from_points", coordinates(workload.origins), "to_points", coordinates(workload.destinations),
                        "out_arrays", ARRAYS, "fail_fast", false, "local", Map.of("algorithm", algorithm))));
        writeJson(path, Map.of("seed", SEED, "coordinate_order", "longitude,latitude", "workloads", requests));
    }
    private static List<List<Double>> coordinates(List<GHPoint> points) { return points.stream().map(p -> List.of(p.lon, p.lat)).toList(); }
    private static void writeJson(Path path, Object body) throws Exception { JSON.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), body); }
    private static void writeCsv(Path path, List<Map<String, Object>> rows) throws Exception {
        if (rows.isEmpty()) return;
        List<String> columns = new ArrayList<>(rows.get(0).keySet());
        StringBuilder csv = new StringBuilder(String.join(",", columns)).append('\n');
        for (Map<String, Object> row : rows) {
            List<String> values = new ArrayList<>();
            for (String column : columns) {
                Object raw = row.get(column); String value = raw == null ? "" : raw.toString();
                values.add(value.contains(",") || value.contains("\"") || value.contains("\n") ? "\"" + value.replace("\"", "\"\"") + "\"" : value);
            }
            csv.append(String.join(",", values)).append('\n');
        }
        Files.writeString(path, csv.toString());
    }
    private static Map<String, String> sourceHashes(Path root) throws Exception {
        Map<String, String> hashes = new LinkedHashMap<>();
        try (var files = Files.list(root.resolve("core/src/main/java/com/graphhopper/routing/matrix"))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).sorted().toList()) hashes.put(root.relativize(file).toString().replace('\\', '/'), sha256(file));
        }
        Path test = root.resolve("core/src/test/java/com/graphhopper/routing/matrix/MatrixCHMatrixBenchmarkTest.java"); hashes.put(root.relativize(test).toString().replace('\\', '/'), sha256(test));
        for (String name : List.of("CHWeightAudit.java", "CHMatrixPairwiseAudit.java", "MatrixPrecisionDiagnostics.java")) {
            Path helper = test.getParent().resolve(name);
            if (Files.exists(helper)) hashes.put(root.relativize(helper).toString().replace('\\', '/'), sha256(helper));
        }
        return hashes;
    }
    private static List<Map<String, Object>> cacheManifest(Path graph) throws Exception {
        List<Map<String, Object>> manifest = new ArrayList<>();
        try (var files = Files.list(graph)) { for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
            if (file.getFileName().toString().equals("gh.lock")) continue;
            manifest.add(Map.of("file", file.getFileName().toString(), "bytes", Files.size(file), "sha256", sha256(file)));
        } }
        return manifest;
    }
    private static Map<String, String> compiledClassHashes(Path root) throws Exception {
        Map<String, String> hashes = new LinkedHashMap<>();
        for (String directory : List.of("core/target/classes/com/graphhopper/routing/matrix", "core/target/test-classes/com/graphhopper/routing/matrix",
                "web-api/target/classes/com/graphhopper/matrix")) {
            Path classes = root.resolve(directory);
            assertTrue(Files.isDirectory(classes), "Compiled classes are required: " + classes);
            try (var files = Files.list(classes)) {
                for (Path file : files.filter(p -> p.toString().endsWith(".class")).sorted().toList()) {
                    String name = file.getFileName().toString();
                    if (directory.contains("test-classes") && List.of("MatrixCHMatrixBenchmarkTest", "CHWeightAudit", "CHMatrixPairwiseAudit", "MatrixPrecisionDiagnostics")
                            .stream().noneMatch(prefix -> name.equals(prefix + ".class") || name.startsWith(prefix + "$"))) continue;
                    hashes.put(root.relativize(file).toString().replace('\\', '/'), sha256(file));
                }
            }
        }
        return hashes;
    }
    private static String sha256(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256"); try (InputStream input = Files.newInputStream(path)) {
            byte[] bytes = new byte[65536]; int length; while ((length = input.read(bytes)) >= 0) digest.update(bytes, 0, length);
        } return HexFormat.of().formatHex(digest.digest());
    }
    private static long gcCount() { return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(p -> Math.max(0, p.getCollectionCount())).sum(); }
    private static com.sun.management.ThreadMXBean allocationBean() {
        try { return ManagementFactory.getThreadMXBean() instanceof com.sun.management.ThreadMXBean bean ? bean : null; }
        catch (SecurityException unsupported) { return null; }
    }
    private static long allocatedBytes() {
        try { return ALLOCATION != null && ALLOCATION.isThreadAllocatedMemorySupported() && ALLOCATION.isThreadAllocatedMemoryEnabled()
                ? ALLOCATION.getThreadAllocatedBytes(Thread.currentThread().getId()) : -1; }
        catch (SecurityException | UnsupportedOperationException unsupported) { return -1; }
    }
    private static Long allocationDelta(long before, long after) { return before < 0 || after < before ? null : after - before; }
    private static Path configured(Path root, String property, String fallback) { Path path = Path.of(System.getProperty(property, fallback)); return path.isAbsolute() ? path : root.resolve(path); }
    private static Path findRoot() { Path path = Path.of("").toAbsolutePath(); while (path != null && !Files.exists(path.resolve("core/pom.xml"))) path = path.getParent(); assertNotNull(path); return path; }
    private record Workload(String name, List<GHPoint> origins, List<GHPoint> destinations) { }
    private record Measured(Map<String, Object> body, Map<String, Object> sample) { }
    private record Coordinate(double lat, double lon) { static Coordinate of(GHPoint point) { return new Coordinate(point.lat, point.lon); } }
    private record CoordinatePair(Coordinate from, Coordinate to) { }
}
