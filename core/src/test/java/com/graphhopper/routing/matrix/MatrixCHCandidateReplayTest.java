package com.graphhopper.routing.matrix;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphhopper.GraphHopper;
import com.graphhopper.GraphHopperConfig;
import com.graphhopper.config.CHProfile;
import com.graphhopper.config.Profile;
import com.graphhopper.routing.ev.Subnetwork;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.querygraph.QueryRoutingCHGraph;
import com.graphhopper.routing.util.DefaultSnapFilter;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.index.Snap;
import com.graphhopper.util.Constants;
import com.graphhopper.util.PMap;
import com.graphhopper.util.TurnCostsConfig;
import com.graphhopper.util.shapes.GHPoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/** Fresh diagnostic replay only. Original benchmark reports and source files are never changed. */
@EnabledIfSystemProperty(named = "matrix.ch.candidate.audit", matches = "true")
class MatrixCHCandidateReplayTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void traceEveryRecordedRawCandidateFlagUsingFrozenProductionAlgorithm() throws Exception {
        Path root = findRoot();
        String selected = System.getProperty("matrix.ch.candidate.audit.dataset", "both");
        assertTrue(Set.of("both", "monaco", "manchester").contains(selected));
        for (String dataset : List.of("monaco", "manchester"))
            if (selected.equals("both") || selected.equals(dataset)) replay(root, dataset);
    }

    private static void replay(Path root, String dataset) throws Exception {
        Path directory = root.resolve("docs/matrix/ch-matrix-benchmark-" + dataset);
        String suffix = System.getProperty("matrix.ch.candidate.audit.output_suffix", "");
        assertTrue(suffix.matches("[-a-zA-Z0-9_]*"));
        Path output = directory.resolve("candidate-path-reconciliation" + suffix + ".json");
        Path witnessOutput = directory.resolve("candidate-path-witnesses" + suffix + ".jsonl.gz");
        assertFalse(Files.exists(output) || Files.exists(witnessOutput), "Preserve existing diagnostic replay; use a separate output path for another attempt");
        JsonNode metadata = JSON.readTree(directory.resolve("metadata.json").toFile());
        JsonNode raw = JSON.readTree(directory.resolve("raw-candidates.json").toFile());
        JsonNode requests = JSON.readTree(directory.resolve("requests.json").toFile());
        JsonNode outcomes = JSON.readTree(directory.resolve("first-outcomes.json").toFile());
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("status", "RUNNING"); report.put("dataset", dataset); report.put("started_utc", Instant.now().toString());
        report.put("method", "Fresh replay of the unchanged actual BucketCHMatrix prepare/search/private explore; reflected final labels, buckets and join results identify the selected CH arcs. This captures replay paths, not the original benchmark paths.");
        report.put("production_algorithm_changed", false);
        report.put("original_reports_modified", false);
        Map<String, String> originalHashes = new LinkedHashMap<>();
        for (String name : List.of("metadata.json", "raw-candidates.json", "first-outcomes.json", "requests.json", "samples.csv"))
            originalHashes.put(name, sha256(directory.resolve(name)));
        report.put("original_evidence_sha256", originalHashes);
        Map<String, String> sourceBridge = new LinkedHashMap<>();
        var sourceFields = metadata.path("source_sha256").fields();
        while (sourceFields.hasNext()) {
            var entry = sourceFields.next(); String actual = sha256(root.resolve(entry.getKey()));
            assertEquals(entry.getValue().asText(), actual, "Measured source changed: " + entry.getKey());
            sourceBridge.put(entry.getKey(), actual);
        }
        report.put("unchanged_measured_source_sha256", sourceBridge);
        Map<String, String> classBridge = new LinkedHashMap<>();
        var classFields = metadata.path("compiled_class_sha256").fields();
        while (classFields.hasNext()) {
            var entry = classFields.next(); String actual = sha256(root.resolve(entry.getKey()));
            assertEquals(entry.getValue().asText(), actual, "Measured class changed: " + entry.getKey());
            classBridge.put(entry.getKey(), actual);
        }
        report.put("unchanged_measured_class_sha256", classBridge);
        Map<String, String> diagnosticSources = new LinkedHashMap<>();
        for (String name : List.of("MatrixCHCandidateReplayTest.java", "BucketCHCandidateTrace.java"))
            diagnosticSources.put(name, sha256(root.resolve("core/src/test/java/com/graphhopper/routing/matrix/" + name)));
        report.put("new_diagnostic_source_sha256", diagnosticSources);
        Map<String, String> diagnosticClasses = new LinkedHashMap<>();
        try (var files = Files.list(root.resolve("core/target/test-classes/com/graphhopper/routing/matrix"))) {
            for (Path file : files.filter(p -> p.getFileName().toString().endsWith(".class") &&
                    (p.getFileName().toString().startsWith("MatrixCHCandidateReplayTest") || p.getFileName().toString().startsWith("BucketCHCandidateTrace"))).sorted().toList())
                diagnosticClasses.put(file.getFileName().toString(), sha256(file));
        }
        report.put("new_diagnostic_class_sha256", diagnosticClasses);
        List<Map<String, Object>> summaries = new ArrayList<>(), indexed = new ArrayList<>();
        report.put("unique_case_proofs", summaries); report.put("indexed_raw_flags", indexed);
        GraphHopper hopper = new GraphHopper();
        Path graph = Path.of(metadata.path("prepared_graph").asText());
        long expectedFlags = 0;
        for (JsonNode workload : raw) expectedFlags += workload.path("raw_candidate_vs_independent_oracle").path("all_mismatches").size();
        report.put("expected_indexed_raw_flags", expectedFlags);
        try {
            verifyCache(graph, metadata.path("cache_file_manifest"));
            hopper.init(config(graph)); hopper.setAllowWrites(false); assertTrue(hopper.load());
            assertEquals(metadata.path("graph_nodes").asInt(), hopper.getBaseGraph().getNodes());
            assertEquals(metadata.path("graph_edges").asInt(), hopper.getBaseGraph().getEdges());
            MatrixProvenance identity = new MatrixProvenance(hopper);
            report.put("replay_graph_fingerprint", identity.graphFingerprint); report.put("replay_build_commit", identity.buildCommit);
            report.put("recorded_graph_fingerprint", metadata.path("graph_fingerprint").asText());
            report.put("recorded_build_commit", metadata.path("build_commit").asText());
            report.put("build_identity_bridge", "A later source checkpoint may change build-bound fingerprints. Exact unchanged production/helper class/source bytes and unchanged complete prepared-cache bytes are checked independently. The original profile definition is checked against its recorded fingerprint using the original graph identity.");
            assertEquals(metadata.path("profile_fingerprints").path("foot").asText(),
                    profileFingerprint(metadata.path("graph_fingerprint").asText(), hopper.getProfile("foot")), "Profile definition/model changed");
            report.put("profile_definition", hopper.getProfile("foot").toString());
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new GZIPOutputStream(Files.newOutputStream(witnessOutput)), StandardCharsets.UTF_8))) {
                var workloads = raw.fields();
                while (workloads.hasNext()) {
                    var workload = workloads.next(); String name = workload.getKey();
                    JsonNode flagged = workload.getValue().path("raw_candidate_vs_independent_oracle").path("all_mismatches");
                    if (flagged.isEmpty()) continue;
                    JsonNode request = request(requests, name);
                    List<GHPoint> origins = axis(request.path("from_points")), destinations = axis(request.path("to_points"));
                    Weighting weighting = hopper.createWeighting(hopper.getProfile("foot"), new PMap());
                    DefaultSnapFilter filter = new DefaultSnapFilter(weighting, hopper.getEncodingManager().getBooleanEncodedValue(Subnetwork.key("foot")));
                    Map<Coordinate, Snap> snaps = new LinkedHashMap<>();
                    List<GHPoint> all = new ArrayList<>(origins); all.addAll(destinations);
                    for (GHPoint point : all) snaps.computeIfAbsent(Coordinate.of(point), ignored -> hopper.getLocationIndex().findClosest(point.lat, point.lon, filter));
                    assertTrue(snaps.values().stream().allMatch(Snap::isValid));
                    QueryGraph query = QueryGraph.create(hopper.getBaseGraph(), new ArrayList<>(snaps.values()));
                    Set<Integer> targets = new HashSet<>();
                    for (GHPoint point : destinations) targets.add(snaps.get(Coordinate.of(point)).getClosestNode());
                    BucketCHCandidateTrace trace = new BucketCHCandidateTrace(new QueryRoutingCHGraph(hopper.getCHGraphs().get("foot"), query), targets);
                    Map<NodePair, Map<String, Object>> cache = new HashMap<>();
                    for (JsonNode issue : flagged) {
                        int i = issue.path("origin_index").asInt(), j = issue.path("destination_index").asInt();
                        Map<String, Number> observedRaw = triple(workload.getValue().path("candidate_metrics"), i, j);
                        Map<String, Number> observedOracle = triple(workload.getValue().path("independent_plain_dijkstra_metrics"), i, j);
                        Map<String, Number> certified = triple(outcomes.path(name).path(metadata.path("experimental_algorithm").asText()), i, j);
                        Map<String, Number> flexible = triple(outcomes.path(name).path("flexible"), i, j);
                        assertEquals(certified, flexible);
                        int source = snaps.get(Coordinate.of(origins.get(i))).getClosestNode();
                        int target = snaps.get(Coordinate.of(destinations.get(j))).getClosestNode();
                        NodePair pair = new NodePair(source, target);
                        Map<String, Object> summary = cache.get(pair);
                        if (summary == null) {
                            BucketCHCandidateTrace.Audit audit = trace.trace(source, target, observedRaw);
                            String id = dataset + "-" + name + "-" + summaries.size();
                            Map<String, Object> witness = new LinkedHashMap<>(audit.witness());
                            witness.put("proof_id", id); witness.put("dataset", dataset); witness.put("workload", name);
                            witness.put("observed_raw_candidate", observedRaw); witness.put("recorded_independent_oracle", observedOracle);
                            witness.put("returned_certified", certified); witness.put("returned_flexible", flexible);
                            String line = JSON.writeValueAsString(witness);
                            writer.write(line); writer.newLine();
                            summary = new LinkedHashMap<>(audit.summary());
                            summary.put("proof_id", id); summary.put("workload", name); summary.put("proved", audit.proved());
                            summary.put("witness_line_sha256", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(line.getBytes(StandardCharsets.UTF_8))));
                            summaries.add(summary); cache.put(pair, summary);
                        }
                        indexed.add(Map.of("workload", name, "origin_index", i, "destination_index", j,
                                "from", List.of(origins.get(i).lon, origins.get(i).lat), "to", List.of(destinations.get(j).lon, destinations.get(j).lat),
                                "source_node", source, "target_node", target, "proof_id", summary.get("proof_id"), "proved", summary.get("proved"),
                                "observed_raw_candidate", observedRaw));
                    }
                    writer.flush();
                    JSON.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), report);
                    System.out.println("Candidate path trace " + dataset + "/" + name + " flags=" + flagged.size());
                }
            }
            long failed = summaries.stream().filter(row -> !Boolean.TRUE.equals(row.get("proved"))).count();
            report.put("unexplained_unique_case_proofs", failed);
            report.put("status", failed == 0 ? "FRESH_ACTUAL_CANDIDATE_PATHS_PROVEN" : "FAIL");
            assertEquals(expectedFlags, indexed.size()); assertEquals(0, failed, "Inspect candidate-path witnesses; no raw candidate failure is waived");
        } catch (Exception | AssertionError failure) {
            report.put("status", "FAIL"); report.put("failure", failure.toString()); throw failure;
        } finally {
            hopper.close(); verifyCache(graph, metadata.path("cache_file_manifest"));
            for (Map.Entry<String, String> entry : originalHashes.entrySet()) assertEquals(entry.getValue(), sha256(directory.resolve(entry.getKey())));
            report.put("completed_utc", Instant.now().toString()); report.put("actual_indexed_raw_flags", indexed.size()); report.put("actual_unique_case_proofs", summaries.size());
            if (Files.exists(witnessOutput)) report.put("witnesses_sha256", sha256(witnessOutput));
            JSON.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), report);
        }
    }

    private static GraphHopperConfig config(Path graph) {
        return new GraphHopperConfig().putObject("graph.location", graph.toString()).putObject("graph.dataaccess.default_type", "RAM_STORE")
                .putObject("graph.encoded_values", "car_access,car_average_speed,road_access,foot_access,foot_average_speed,foot_priority,foot_road_access,hike_rating,mtb_rating,country,road_class")
                .putObject("prepare.min_network_size", 0).putObject("prepare.subnetworks.threads", 1).putObject("prepare.ch.threads", 1)
                .putObject("import.osm.ignored_highways", "construction")
                .setProfiles(List.of(fileProfile("foot"), fileProfile("car").setTurnCostsConfig(TurnCostsConfig.car().setUTurnCosts(60))))
                .setCHProfiles(List.of(new CHProfile("foot"), new CHProfile("car"))).setLMProfiles(List.of());
    }
    private static Profile fileProfile(String name) { Profile profile = new Profile(name); profile.getHints().remove("custom_model"); return profile.putHint("custom_model_files", List.of(name + ".json")); }
    private static JsonNode request(JsonNode requests, String name) { for (JsonNode request : requests.path("workloads")) if (request.path("workload").asText().equals(name) && request.path("backend").asText().equals("flexible")) return request.path("request"); throw new AssertionError(name); }
    private static List<GHPoint> axis(JsonNode points) { List<GHPoint> axis = new ArrayList<>(); for (JsonNode point : points) axis.add(new GHPoint(point.get(1).asDouble(), point.get(0).asDouble())); return axis; }
    private static Map<String, Number> triple(JsonNode body, int i, int j) { Map<String, Number> triple = new LinkedHashMap<>(); for (String metric : List.of("weights", "times_ms", "distances")) { JsonNode value = body.path(metric).get(i).get(j); if (value.isNull()) triple.put(metric, null); else if (metric.equals("times_ms")) triple.put(metric, Long.valueOf(value.asLong())); else triple.put(metric, Double.valueOf(value.asDouble())); } return triple; }
    private static String profileFingerprint(String graph, Profile profile) throws Exception { MessageDigest digest = MessageDigest.getInstance("SHA-256"); for (String text : List.of(graph, Constants.VERSION, profile.toString())) { byte[] bytes = text.getBytes(StandardCharsets.UTF_8); digest.update(ByteBuffer.allocate(4).putInt(bytes.length).array()); digest.update(bytes); } return HexFormat.of().formatHex(digest.digest()); }
    private static void verifyCache(Path graph, JsonNode manifest) throws Exception { for (JsonNode row : manifest) { Path file = graph.resolve(row.path("file").asText()); assertEquals(row.path("bytes").asLong(), Files.size(file)); assertEquals(row.path("sha256").asText(), sha256(file)); } }
    private static String sha256(Path path) throws Exception { MessageDigest digest = MessageDigest.getInstance("SHA-256"); try (InputStream stream = Files.newInputStream(path)) { byte[] bytes = new byte[65536]; int n; while ((n = stream.read(bytes)) >= 0) digest.update(bytes, 0, n); } return HexFormat.of().formatHex(digest.digest()); }
    private static Path findRoot() { Path root = Path.of("").toAbsolutePath(); while (root != null && !Files.exists(root.resolve("core/pom.xml"))) root = root.getParent(); assertNotNull(root); return root; }
    private record Coordinate(double lat, double lon) { static Coordinate of(GHPoint point) { return new Coordinate(point.lat, point.lon); } }
    private record NodePair(int from, int to) { }
}
