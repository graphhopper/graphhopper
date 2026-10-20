package com.graphhopper.routing.matrix;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphhopper.GraphHopper;
import com.graphhopper.GraphHopperConfig;
import com.graphhopper.config.CHProfile;
import com.graphhopper.config.Profile;
import com.graphhopper.matrix.MatrixRequest;
import com.graphhopper.routing.AbstractBidirCHAlgo;
import com.graphhopper.routing.Dijkstra;
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
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.RoutingCHGraph;
import com.graphhopper.storage.index.Snap;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.Helper;
import com.graphhopper.util.PMap;
import com.graphhopper.util.TurnCostsConfig;
import com.graphhopper.util.shapes.GHPoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/** Independent offline proof for the already recorded HTTP CH scalar discrepancies. */
@EnabledIfSystemProperty(named = "matrix.ch.audit", matches = "true")
class MatrixCHHttpAuditTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern WEIGHT_ISSUE = Pattern.compile("weight: route=([^,]+), matrix=([^,]+), tolerance=([^,]+)");
    private static final List<String> ARRAYS = List.of("times", "times_ms", "distances", "weights");
    private record Pair(String profile, double fromLat, double fromLon, double toLat, double toLon) { }
    private record PathProof(boolean legal, double weight, long time, double distance,
                             List<Integer> canonicalKeys, List<Map<String, Object>> edges) { }
    private record CHProof(String id, boolean proved, double roundedCHWeight, Map<String, Object> details) { }

    @Test
    void explainRecordedWeightsUsingActualSelectedCHPathsAndStoragePrecision() throws Exception {
        Path root = findRoot();
        Path directory = configured(root, "matrix.ch.audit.directory", "docs/matrix/regional-http-benchmark");
        Path graphPath = configured(root, "matrix.ch.audit.graph", "target/matrix-manchester-server");
        Path destination = configured(root, "matrix.ch.audit.output", root.relativize(directory.resolve("ch-weight-reconciliation.json")).toString());
        Path rawReport = directory.resolve("http-benchmark-report.json");
        Path requestsFile = System.getProperty("matrix.ch.audit.requests") != null
                ? configured(root, "matrix.ch.audit.requests", "")
                : directory.resolve(Files.exists(directory.resolve("requests.json")) ? "requests.json" : "benchmark-requests.json");
        Path sampleFile = directory.resolve("http-benchmark-samples.csv");
        String originalReportSha = sha256(rawReport), originalRequestsSha = sha256(requestsFile), originalSamplesSha = sha256(sampleFile);
        JsonNode report = JSON.readTree(rawReport.toFile()), requests = JSON.readTree(requestsFile.toFile());
        assertEquals(report.path("requests_sha256").asText(), originalRequestsSha, "Recorded request artifact changed");
        Map<String, Object> ledger = new LinkedHashMap<>();
        ledger.put("status", "RUNNING");
        ledger.put("started_utc", Instant.now().toString());
        ledger.put("original_report_status", report.path("status").asText());
        ledger.put("original_report_sha256", originalReportSha);
        ledger.put("original_requests_sha256", originalRequestsSha);
        ledger.put("original_samples_sha256", originalSamplesSha);
        ledger.put("prepared_graph", graphPath.toString());
        ledger.put("historic_evidence_scope", "Fresh paths are reconstructed for each unique profile/OD and linked to every flagged indexed cell. "
                + "All retained original issue scalars and aggregate counts are checked; capped issue lists do not contain every repetition's path or scalar. "
                + "Stable aggregate CSV checksums cannot alone exclude compensating per-cell changes in omitted records, which are not directly reverified. "
                + "Original absence of time/distance/null flags establishes only the original HTTP thresholds; fresh replay checks actual full-precision path metrics.");
        ledger.put("production_algorithm_changed", false);
        List<Map<String, Object>> indexedCases = new ArrayList<>(), uniqueProofs = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        ledger.put("unique_pair_proofs", uniqueProofs);
        ledger.put("indexed_cases", indexedCases);
        ledger.put("unexplained_cases", failures);
        GraphHopper hopper = new GraphHopper();
        try {
            hopper.init(new GraphHopperConfig()
                    .putObject("graph.location", graphPath.toString()).putObject("graph.dataaccess.default_type", "RAM_STORE")
                    .putObject("graph.encoded_values", "car_access,car_average_speed,road_access,foot_access,foot_average_speed,foot_priority,foot_road_access,hike_rating,mtb_rating,country,road_class")
                    .putObject("prepare.min_network_size", 0).putObject("prepare.subnetworks.threads", 1).putObject("prepare.ch.threads", 1)
                    .putObject("import.osm.ignored_highways", "construction")
                    .setProfiles(List.of(fileProfile("foot"), fileProfile("car").setTurnCostsConfig(TurnCostsConfig.car().setUTurnCosts(60))))
                    .setCHProfiles(List.of(new CHProfile("foot"), new CHProfile("car"))).setLMProfiles(List.of()));
            hopper.setAllowWrites(false);
            assertTrue(hopper.load(), "The already prepared server graph must load without import or preparation");
            assertEquals(Set.of("foot", "car"), hopper.getCHGraphs().keySet());
            MatrixProvenance provenance = new MatrixProvenance(hopper);
            ledger.put("replay_graph_fingerprint", provenance.graphFingerprint);
            ledger.put("replay_build_commit", provenance.buildCommit);
            Map<String, String> profileIdentities = new LinkedHashMap<>();
            for (String profile : List.of("foot", "car")) {
                profileIdentities.put(profile, provenance.profileFingerprint(profile));
                JsonNode original = report.path("matrix_provenance").path(profile);
                assertEquals(original.path("graph_fingerprint").asText(), provenance.graphFingerprint, "Loaded graph identity differs");
                assertEquals(original.path("profile_fingerprint").asText(), provenance.profileFingerprint(profile), "Loaded profile identity differs");
                assertEquals(original.path("build_commit").asText(), provenance.buildCommit);
            }
            ledger.put("replay_profile_fingerprints", profileIdentities);
            ledger.put("csv_repeat_weight_checksums_stable", stableChecksums(sampleFile, report.path("repeats").asInt()));
            Map<Pair, CHProof> proofs = new LinkedHashMap<>();
            MatrixEngine engine = new MatrixEngine(hopper, new MatrixEngine.Limits(1000, 100000, 1, 120000, Integer.MAX_VALUE));
            long retainedRecords = 0, aggregateFlags = 0;
            for (JsonNode comparison : report.path("comparisons")) {
                String profile = comparison.path("profile").asText(), name = comparison.path("workload").asText();
                JsonNode request = findRequest(requests, profile, name);
                List<GHPoint> origins = axis(request.path("from_points")), targets = axis(request.path("to_points"));
                Map<String, Object> matrix = engine.route(new MatrixRequest(profile, origins, targets, ARRAYS, false, "flexible"));
                List<GHPoint> combined = new ArrayList<>(origins);
                combined.addAll(targets);
                MatrixPrecisionDiagnostics diagnostics = new MatrixPrecisionDiagnostics(hopper, profile, combined);
                Map<String, Map<String, Object>> groupCases = new LinkedHashMap<>();
                for (JsonNode issueRecord : comparison.path("mismatches_first_100")) {
                    assertEquals(1, issueRecord.path("issues").size(), "Every original mismatch must be weight-only");
                    String issueText = issueRecord.path("issues").get(0).asText();
                    Matcher issue = WEIGHT_ISSUE.matcher(issueText);
                    assertTrue(issue.matches(), "Unexpected original issue: " + issueText);
                    double observedCH = Double.parseDouble(issue.group(1)), observedMatrix = Double.parseDouble(issue.group(2));
                    int i = issueRecord.path("from_index").asInt(), j = issueRecord.path("to_index").asInt();
                    GHPoint from = origins.get(i), to = targets.get(j);
                    Pair key = new Pair(profile, from.lat, from.lon, to.lat, to.lon);
                    CHProof proof = proofs.get(key);
                    if (proof == null) {
                        proof = provePair(hopper, key, "pair-" + proofs.size());
                        proofs.put(key, proof);
                        uniqueProofs.add(proof.details);
                    }
                    assertEquals(proof.roundedCHWeight, observedCH, 0, "Original rounded CH weight differs from replay");
                    double replayMatrix = cell(matrix, "weights", i, j).doubleValue();
                    assertEquals(replayMatrix, observedMatrix, 1e-10, "Original matrix scalar differs from replay");
                    String index = i + ":" + j;
                    if (!groupCases.containsKey(index)) {
                        assertEquals(0, issueRecord.path("repeat").asInt(), "Each indexed case must be present in retained repeat-zero evidence");
                        MatrixPrecisionDiagnostics.Evidence pairEvidence = diagnostics.diagnose(i, origins.size() + j);
                        boolean matrixPathProved = pairEvidence.explained() && matches(pairEvidence.details().get("reported_shared_metrics"),
                                replayMatrix, cell(matrix, "times_ms", i, j).longValue(), cell(matrix, "distances", i, j).doubleValue());
                        Map<String, Object> link = new LinkedHashMap<>();
                        link.put("profile", profile); link.put("workload", name); link.put("from_index", i); link.put("to_index", j);
                        link.put("unique_pair_proof_id", proof.id); link.put("observed_ch_weight", observedCH); link.put("observed_matrix_weight", observedMatrix);
                        link.put("original_report_sha256", originalReportSha);
                        link.put("retained_original_scalar_records_checked", 0);
                        link.put("exact_route_serializer_round6_matches", true);
                        link.put("matrix_matches_actual_recomputed_path", matrixPathProved);
                        link.put("ch_path_and_quantization_proved", proof.proved);
                        link.put("matrix_vs_independent_path_evidence", pairEvidence.details());
                        link.put("accepted_with_independent_path_evidence", proof.proved && matrixPathProved);
                        groupCases.put(index, link); indexedCases.add(link);
                        if (!(proof.proved && matrixPathProved)) failures.add(profile + "/" + name + "/" + index);
                    }
                    Map<String, Object> indexed = groupCases.get(index);
                    indexed.put("retained_original_scalar_records_checked", ((Number) indexed.get("retained_original_scalar_records_checked")).intValue() + 1);
                    retainedRecords++;
                }
                long aggregate = comparison.path("mismatched_cells_across_repetitions").asLong();
                assertEquals((long) groupCases.size() * report.path("repeats").asLong(), aggregate,
                        "Retained repeat-zero index coverage does not account for aggregate mismatches");
                aggregateFlags += aggregate;
            }
            ledger.put("retained_original_issue_records_checked", retainedRecords);
            ledger.put("aggregate_original_flag_instances", aggregateFlags);
            ledger.put("indexed_flagged_cases", indexedCases.size());
            ledger.put("unique_profile_coordinate_pairs", uniqueProofs.size());
            ledger.put("unique_pair_proofs", uniqueProofs);
            ledger.put("indexed_cases", indexedCases);
            ledger.put("unexplained_cases", failures);
            ledger.put("remaining_unexplained_cases", failures.size());
            ledger.put("status", failures.isEmpty() ? "PASS_WITH_PATH_PROVEN_CH_STORAGE_PRECISION" : "FAIL");
            assertTrue(failures.isEmpty(), "Unexplained CH comparisons: " + failures);
        } catch (Exception | AssertionError failure) {
            ledger.put("status", "FAIL"); ledger.put("failure", failure.toString()); throw failure;
        } catch (Error fatal) {
            ledger.put("status", "INCOMPLETE_FATAL_ERROR"); ledger.put("failure", fatal.toString()); throw fatal;
        } finally {
            hopper.close();
            assertEquals(originalReportSha, sha256(rawReport));
            assertEquals(originalRequestsSha, sha256(requestsFile));
            assertEquals(originalSamplesSha, sha256(sampleFile));
            ledger.put("completed_utc", Instant.now().toString());
            Files.createDirectories(destination.getParent());
            JSON.writerWithDefaultPrettyPrinter().writeValue(destination.toFile(), ledger);
        }
    }

    private static CHProof provePair(GraphHopper hopper, Pair pair, String id) {
        Profile profile = hopper.getProfile(pair.profile);
        Weighting raw = hopper.createWeighting(profile, new PMap());
        DefaultSnapFilter filter = new DefaultSnapFilter(raw, hopper.getEncodingManager().getBooleanEncodedValue(Subnetwork.key(pair.profile)));
        Snap from = hopper.getLocationIndex().findClosest(pair.fromLat, pair.fromLon, filter);
        Snap to = hopper.getLocationIndex().findClosest(pair.toLat, pair.toLon, filter);
        assertTrue(from.isValid() && to.isValid());
        QueryGraph query = QueryGraph.create(hopper.getBaseGraph(), from, to);
        Weighting weighting = query.wrapWeighting(raw);
        RoutingCHGraph chGraph = new QueryRoutingCHGraph(hopper.getCHGraphs().get(pair.profile), query);
        AbstractBidirCHAlgo algo = (AbstractBidirCHAlgo) new CHRoutingAlgorithmFactory(chGraph).createAlgo(new PMap());
        SPTEntry[] entries = new SPTEntry[2];
        double[] reported = new double[1];
        algo.setPathExtractorSupplier(() -> (fwd, bwd, weight) -> {
            entries[0] = fwd; entries[1] = bwd; reported[0] = weight;
            return (profile.hasTurnCosts() ? new EdgeBasedCHBidirPathExtractor(chGraph) : new NodeBasedCHBidirPathExtractor(chGraph))
                    .extract(fwd, bwd, weight);
        });
        com.graphhopper.routing.Path selected = algo.calcPath(from.getClosestNode(), to.getClosestNode());
        assertTrue(selected.isFound());
        com.graphhopper.routing.Path oracle = new Dijkstra(query, weighting,
                profile.hasTurnCosts() ? TraversalMode.EDGE_BASED : TraversalMode.NODE_BASED).calcPath(from.getClosestNode(), to.getClosestNode());
        assertTrue(oracle.isFound());
        PathProof selectedProof = describe(query, weighting, selected.calcEdges());
        PathProof oracleProof = describe(query, weighting, oracle.calcEdges());
        CHWeightAudit.Audit precision = CHWeightAudit.analyze(chGraph, entries[0], entries[1], reported[0]);
        BigDecimal exactExpanded = BigDecimal.ZERO;
        for (Map<String, Object> edge : selectedProof.edges)
            exactExpanded = exactExpanded.add(new BigDecimal(((Number) edge.get("edge_weight")).doubleValue()))
                    .add(new BigDecimal(((Number) edge.get("turn_weight")).doubleValue()));
        BigDecimal exactOracle = exactPathSum(oracleProof.edges, "edge_weight", "turn_weight");
        BigDecimal exactSelectedDistance = exactPathSum(selectedProof.edges, "distance_m");
        BigDecimal exactOracleDistance = exactPathSum(oracleProof.edges, "distance_m");
        List<Map<String, Object>> equalMetricParallelChoices = new ArrayList<>();
        boolean perEdgeEqualParallel = exactMetricParallelChoices(selectedProof.edges, oracleProof.edges, equalMetricParallelChoices);
        boolean exactOptimalAlternative = exactExpanded.compareTo(exactOracle) == 0
                && exactSelectedDistance.compareTo(exactOracleDistance) == 0 && selectedProof.time == oracleProof.time
                && perEdgeEqualParallel;
        boolean helperExpansionMatchesActualPath = precision.details().get("exact_selected_expanded_terms_sum") instanceof String exact
                && exactExpanded.compareTo(new BigDecimal(exact)) == 0;
        boolean exactMetrics = close(selectedProof.weight, oracle.getWeight()) && selectedProof.time == oracle.getTime()
                && Math.abs(selectedProof.distance - oracle.getDistance()) <= 1e-8 + Math.abs(oracle.getDistance()) * 1e-12;
        boolean internallyConsistent = selectedProof.time == selected.getTime()
                && Math.abs(selectedProof.distance - selected.getDistance()) <= 1e-8 + Math.abs(selected.getDistance()) * 1e-12;
        boolean sameCanonical = selectedProof.canonicalKeys.equals(oracleProof.canonicalKeys);
        boolean actualEdgeEndpoints = selectedProof.edges.isEmpty() ? from.getClosestNode() == to.getClosestNode()
                : ((Number) selectedProof.edges.get(0).get("from_node")).intValue() == from.getClosestNode()
                && ((Number) selectedProof.edges.get(selectedProof.edges.size() - 1).get("to_node")).intValue() == to.getClosestNode();
        boolean endpoints = selected.getFromNode() == from.getClosestNode() && selected.getEndNode() == to.getClosestNode() && actualEdgeEndpoints;
        boolean proved = selectedProof.legal && oracleProof.legal && precision.proved() && helperExpansionMatchesActualPath
                && exactMetrics && internallyConsistent && exactOptimalAlternative && endpoints;
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("id", id); details.put("profile", pair.profile);
        details.put("from", List.of(pair.fromLon, pair.fromLat)); details.put("to", List.of(pair.toLon, pair.toLat));
        details.put("algorithm", algo.getName()); details.put("selected_ch_reported_weight", selected.getWeight());
        details.put("selected_ch_http_round6_weight", Helper.round6(selected.getWeight()));
        details.put("unrounded_expanded_base_weight", selectedProof.weight);
        details.put("plain_dijkstra_weight", oracle.getWeight()); details.put("fresh_time_ms", selectedProof.time); details.put("fresh_distance_m", selectedProof.distance);
        details.put("expanded_path_legal", selectedProof.legal); details.put("plain_path_legal", oracleProof.legal);
        details.put("expanded_metrics_match_plain_dijkstra", exactMetrics); details.put("expanded_metrics_match_ch_extracted_time_distance", internallyConsistent);
        details.put("same_copy_canonical_expanded_path", sameCanonical); details.put("path_endpoints_match", endpoints);
        details.put("explicit_exact_metric_optimal_alternative", exactOptimalAlternative);
        details.put("divergent_equal_metric_parallel_segments", equalMetricParallelChoices);
        details.put("exact_plain_path_cost_terms_sum", exactOracle.toPlainString());
        details.put("exact_ch_path_distance_terms_sum", exactSelectedDistance.toPlainString());
        details.put("exact_plain_path_distance_terms_sum", exactOracleDistance.toPlainString());
        details.put("shortcut_expansion_exactly_matches_actual_path_cost_terms", helperExpansionMatchesActualPath);
        details.put("exact_actual_path_cost_terms_sum", exactExpanded.toPlainString());
        details.put("expanded_ch_edges", selectedProof.edges); details.put("plain_dijkstra_edges", oracleProof.edges);
        details.put("quantization_audit", precision.details()); details.put("accepted_with_independent_path_evidence", proved);
        return new CHProof(id, proved, Helper.round6(selected.getWeight()), details);
    }

    private static PathProof describe(Graph graph, Weighting weighting, List<EdgeIteratorState> edges) {
        List<Map<String, Object>> evidence = new ArrayList<>(); List<Integer> keys = new ArrayList<>();
        int previous = EdgeIterator.NO_EDGE, previousNode = -1; double weight = 0, distance = 0; long time = 0; boolean legal = true;
        for (EdgeIteratorState edge : edges) {
            double edgeWeight = weighting.calcEdgeWeight(edge, false);
            double turnWeight = previous == EdgeIterator.NO_EDGE ? 0 : weighting.calcTurnWeight(previous, edge.getBaseNode(), edge.getEdge());
            long edgeTime = weighting.calcEdgeMillis(edge, false), turnTime = previous == EdgeIterator.NO_EDGE ? 0 : weighting.calcTurnMillis(previous, edge.getBaseNode(), edge.getEdge());
            legal &= (previousNode < 0 || previousNode == edge.getBaseNode()) && Double.isFinite(edgeWeight) && edgeWeight >= 0
                    && Double.isFinite(turnWeight) && turnWeight >= 0 && edgeTime >= 0 && turnTime >= 0;
            weight += edgeWeight + turnWeight; time = Math.addExact(time, Math.addExact(edgeTime, turnTime)); distance += edge.getDistance();
            int original = edge instanceof VirtualEdgeIteratorState virtual ? virtual.getOriginalEdgeKey() : edge.getEdgeKey();
            int canonical = MatrixPrecisionDiagnostics.canonicalDirectedEdgeKey(graph.getBaseGraph(), original);
            if (keys.isEmpty() || keys.get(keys.size() - 1) != canonical) keys.add(canonical);
            evidence.add(Map.of("edge_key", edge.getEdgeKey(), "original_edge_key", original, "copy_canonical_edge_key", canonical,
                    "from_node", edge.getBaseNode(), "to_node", edge.getAdjNode(), "edge_weight", edgeWeight,
                    "turn_weight", turnWeight, "edge_time_ms", edgeTime, "turn_time_ms", turnTime, "distance_m", edge.getDistance()));
            previous = edge.getEdge(); previousNode = edge.getAdjNode();
        }
        return new PathProof(legal, weight, time, distance, keys, evidence);
    }

    private static boolean close(double a, double b) { return Math.abs(a - b) <= 1e-7 + Math.abs(b) * 1e-10; }
    private static BigDecimal exactPathSum(List<Map<String, Object>> path, String... fields) {
        BigDecimal result = BigDecimal.ZERO;
        for (Map<String, Object> edge : path)
            for (String field : fields) result = result.add(new BigDecimal(((Number) edge.get(field)).doubleValue()));
        return result;
    }
    private static boolean exactMetricParallelChoices(List<Map<String, Object>> selected, List<Map<String, Object>> oracle,
                                                       List<Map<String, Object>> differences) {
        if (selected.size() != oracle.size()) return false;
        boolean proved = true;
        List<String> equalFields = List.of("from_node", "to_node", "edge_weight", "turn_weight", "edge_time_ms", "turn_time_ms", "distance_m");
        for (int i = 0; i < selected.size(); i++) {
            Map<String, Object> first = selected.get(i), second = oracle.get(i);
            boolean equal = equalFields.stream().allMatch(field -> first.get(field).equals(second.get(field)));
            proved &= equal;
            if (!first.get("copy_canonical_edge_key").equals(second.get("copy_canonical_edge_key"))) {
                Map<String, Object> difference = new LinkedHashMap<>();
                difference.put("path_edge_index", i);
                difference.put("ch_segment", first);
                difference.put("plain_dijkstra_segment", second);
                difference.put("directed_endpoints_and_all_actual_metrics_exactly_equal", equal);
                // Every index is compared, including the next edge's actual turn context.
                if (i + 1 < selected.size()) {
                    difference.put("ch_next_turn_weight", selected.get(i + 1).get("turn_weight"));
                    difference.put("plain_next_turn_weight", oracle.get(i + 1).get("turn_weight"));
                    difference.put("ch_next_turn_time_ms", selected.get(i + 1).get("turn_time_ms"));
                    difference.put("plain_next_turn_time_ms", oracle.get(i + 1).get("turn_time_ms"));
                }
                differences.add(difference);
            }
        }
        return proved;
    }
    private static Profile fileProfile(String name) {
        Profile profile = new Profile(name);
        // YAML constructs Profile without its public Java constructor's default model. Remove
        // that entry completely so retained hints have the same order/content as the server.
        profile.getHints().remove("custom_model");
        return profile.putHint("custom_model_files", List.of(name + ".json"));
    }
    private static boolean matches(Object raw, double weight, long time, double distance) {
        if (!(raw instanceof Map<?, ?> metrics)) return false;
        return close(((Number) metrics.get("weight")).doubleValue(), weight) && ((Number) metrics.get("time_ms")).longValue() == time
                && Math.abs(((Number) metrics.get("distance_m")).doubleValue() - distance) <= 1e-8 + Math.abs(distance) * 1e-12;
    }
    @SuppressWarnings("unchecked") private static Number cell(Map<String, Object> body, String metric, int i, int j) {
        return ((List<List<Number>>) body.get(metric)).get(i).get(j);
    }
    private static JsonNode findRequest(JsonNode requests, String profile, String name) {
        for (JsonNode workload : requests.path("workloads"))
            if (workload.path("profile").asText().equals(profile) && workload.path("name").asText().equals(name)) return workload.path("request");
        throw new AssertionError("Missing request " + profile + "/" + name);
    }
    private static List<GHPoint> axis(JsonNode node) {
        List<GHPoint> points = new ArrayList<>(); node.forEach(p -> points.add(new GHPoint(p.get(1).asDouble(), p.get(0).asDouble()))); return points;
    }
    private static boolean stableChecksums(Path file, int repeats) throws Exception {
        Map<String, Set<String>> values = new LinkedHashMap<>(); Map<String, Integer> counts = new LinkedHashMap<>();
        List<String> lines = Files.readAllLines(file); String[] header = lines.get(0).split(",");
        int checksum = Arrays.asList(header).indexOf("weight_checksum"); assertTrue(checksum >= 0);
        for (String line : lines.subList(1, lines.size())) {
            String[] columns = line.split(",", -1); String key = columns[0] + "/" + columns[1] + "/" + columns[2];
            values.computeIfAbsent(key, ignored -> new HashSet<>()).add(columns[checksum]); counts.merge(key, 1, Integer::sum);
        }
        for (String key : values.keySet()) { assertEquals(1, values.get(key).size(), "Unstable recorded checksum " + key); assertEquals(repeats, counts.get(key)); }
        return true;
    }
    private static Path configured(Path root, String key, String fallback) { Path path = Path.of(System.getProperty(key, fallback)); return path.isAbsolute() ? path : root.resolve(path); }
    private static Path findRoot() { Path p = Path.of("").toAbsolutePath(); while (p != null && !Files.exists(p.resolve("core/pom.xml"))) p = p.getParent(); assertNotNull(p); return p; }
    private static String sha256(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256"); try (InputStream in = Files.newInputStream(path)) {
            byte[] b = new byte[65536]; int n; while ((n = in.read(b)) >= 0) digest.update(b, 0, n);
        } return HexFormat.of().formatHex(digest.digest());
    }
}
