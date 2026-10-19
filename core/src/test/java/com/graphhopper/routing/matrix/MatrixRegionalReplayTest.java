package com.graphhopper.routing.matrix;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphhopper.GraphHopper;
import com.graphhopper.GraphHopperConfig;
import com.graphhopper.config.Profile;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.TurnCostsConfig;
import com.graphhopper.util.shapes.GHPoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** Replays only the previously unexplained cells; never overwrites the original failing run. */
@EnabledIfSystemProperty(named = "matrix.regional.replay", matches = "true")
class MatrixRegionalReplayTest {
    @Test
    void reconcileSavedFailuresWithProvenUpstreamCopyIdentity() throws Exception {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.exists(root.resolve("core/pom.xml"))) root = root.getParent();
        assertNotNull(root);
        Path output = root.resolve("docs/matrix");
        Path originalReport = output.resolve("regional-validation.json");
        Path originalReplays = output.resolve("regional-failure-replays.jsonl");
        ObjectMapper json = new ObjectMapper();
        JsonNode previous = json.readTree(originalReport.toFile());
        assertEquals("FAIL", previous.path("status").asText(), "Keep the initial failing report unchanged");
        List<JsonNode> failed = new ArrayList<>();
        for (String line : Files.readAllLines(originalReplays)) {
            JsonNode replay = json.readTree(line);
            if (!replay.path("accepted_with_independent_path_evidence").asBoolean()) failed.add(replay);
        }
        assertEquals(10, failed.size(), "This replay gate is pinned to the initial ten failures");
        String initialReportSha = sha256(originalReport), initialReplaySha = sha256(originalReplays);
        Path pbf = Path.of(previous.path("source_path").asText());
        assertEquals(previous.path("source_sha256").asText(), sha256(pbf));
        for (String model : List.of("car.json", "foot.json"))
            assertEquals(previous.path("profile_custom_model_sha256").path(model).asText(),
                    sha256(root.resolve("core/src/main/resources/com/graphhopper/custom_models/" + model)));
        Map<String, Object> reconciliation = new LinkedHashMap<>();
        reconciliation.put("status", "RUNNING");
        reconciliation.put("started_utc", Instant.now().toString());
        reconciliation.put("original_status", "FAIL");
        reconciliation.put("original_report_sha256", initialReportSha);
        reconciliation.put("original_replays_sha256", initialReplaySha);
        reconciliation.put("original_cells_compared", 2592);
        reconciliation.put("original_explained_mismatches", previous.path("explained_by_path_evidence").asInt());
        reconciliation.put("original_unexplained_mismatches", previous.path("unexplained_mismatches").asInt());
        reconciliation.put("source_sha256", previous.path("source_sha256").asText());
        reconciliation.put("production_algorithm_changed", false);
        reconciliation.put("rule", "Raw edge identities remain recorded. Different edges align only through the actual upstream shared-geometry copy relation, "
                + "with matching directed endpoints, legal reconstructed paths, unchanged actual turn costs and weight per metre, "
                + "exact same-QueryGraph Dijkstra/path metrics, and observed outputs matching the independent path evidence.");
        List<Map<String, Object>> results = new ArrayList<>();
        List<String> unexplained = new ArrayList<>();
        GraphHopper hopper = new GraphHopper();
        try {
            GraphHopperConfig config = new GraphHopperConfig()
                    .putObject("datareader.file", pbf.toString())
                    .putObject("graph.location", root.resolve("target/matrix-regional-replay-" + UUID.randomUUID()).toString())
                    .putObject("graph.dataaccess.default_type", "RAM")
                    .putObject("graph.encoded_values", "car_access,car_average_speed,road_access,foot_access,foot_priority,foot_average_speed,foot_road_access,hike_rating,mtb_rating,country,road_class")
                    .putObject("prepare.min_network_size", 0).putObject("prepare.subnetworks.threads", 1)
                    .putObject("import.osm.ignored_highways", "construction")
                    .setProfiles(List.of(new Profile("foot").setCustomModel(GHUtility.loadCustomModelFromJar("foot.json")),
                            new Profile("car").setCustomModel(GHUtility.loadCustomModelFromJar("car.json"))
                                    .setTurnCostsConfig(TurnCostsConfig.car().setUTurnCosts(60))))
                    .setCHProfiles(List.of()).setLMProfiles(List.of());
            hopper.init(config);
            hopper.importOrLoad();
            assertEquals(previous.path("graph_nodes").asInt(), hopper.getBaseGraph().getNodes());
            assertEquals(previous.path("graph_edges").asInt(), hopper.getBaseGraph().getEdges());
            MatrixProvenance provenance = new MatrixProvenance(hopper);
            reconciliation.put("replay_graph_fingerprint", provenance.graphFingerprint);
            reconciliation.put("replay_profile_fingerprint", provenance.profileFingerprint("car"));
            reconciliation.put("replay_build_commit", provenance.buildCommit);
            reconciliation.put("profile_custom_model_sha256", previous.get("profile_custom_model_sha256"));
            reconciliation.put("graph_nodes", hopper.getBaseGraph().getNodes());
            reconciliation.put("graph_edges", hopper.getBaseGraph().getEdges());
            JsonNode regionalCar = null;
            for (JsonNode run : previous.path("cases"))
                if (run.path("profile").asText().equals("car") && run.path("case").asText().equals("stratified_regional_square")) regionalCar = run;
            assertNotNull(regionalCar);
            List<GHPoint> points = new ArrayList<>();
            regionalCar.path("origin_coordinates_lon_lat").forEach(point -> points.add(new GHPoint(point.get(1).asDouble(), point.get(0).asDouble())));
            int originCount = points.size();
            regionalCar.path("destination_coordinates_lon_lat").forEach(point -> points.add(new GHPoint(point.get(1).asDouble(), point.get(0).asDouble())));
            MatrixPrecisionDiagnostics diagnostics = null;
            int activeSource = -1;
            for (JsonNode saved : failed) {
                int from = saved.path("origin_index").asInt(), to = saved.path("destination_index").asInt();
                // Match the original regional audit's row scope. A diagnostic source retains
                // predecessor labels, so release its helper before progressing to a new source.
                if (from != activeSource) {
                    diagnostics = new MatrixPrecisionDiagnostics(hopper, "car", points);
                    activeSource = from;
                }
                MatrixPrecisionDiagnostics.Evidence evidence = diagnostics.diagnose(from, originCount + to);
                boolean observedReferenceMatches = matches(saved.path("observed_reference"), evidence.details().get("upstream_route_metrics"));
                boolean observedSharedMatches = matches(saved.path("observed_shared"), evidence.details().get("reported_shared_metrics"));
                boolean proved = evidence.explained() && observedReferenceMatches && observedSharedMatches;
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("origin_index", from);
                result.put("destination_index", to);
                result.put("observed_reference_matches_replay", observedReferenceMatches);
                result.put("observed_shared_matches_replay", observedSharedMatches);
                result.put("accepted_with_independent_path_evidence", proved);
                result.put("path_evidence", evidence.details());
                results.add(result);
                if (!proved) unexplained.add(from + "->" + to);
            }
            reconciliation.put("status", unexplained.isEmpty() ? "PASS_WITH_PROVEN_COPY_TIES_AND_SPLIT_PRECISION" : "FAIL");
            reconciliation.put("replayed_cells", results.size());
            reconciliation.put("newly_explained_mismatches", results.size() - unexplained.size());
            reconciliation.put("remaining_unexplained_mismatches", unexplained.size());
            reconciliation.put("unexplained_cells", unexplained);
            reconciliation.put("replays", results);
            assertTrue(unexplained.isEmpty(), "Unexplained replay cells: " + unexplained);
        } catch (Exception | AssertionError failure) {
            reconciliation.put("status", "FAIL");
            reconciliation.put("failure", failure.toString());
            throw failure;
        } catch (Error fatal) {
            reconciliation.put("status", "INCOMPLETE_FATAL_ERROR");
            reconciliation.put("failure", fatal.getClass().getName());
            throw fatal;
        } finally {
            hopper.close();
            assertEquals(initialReportSha, sha256(originalReport), "Original FAIL report changed");
            assertEquals(initialReplaySha, sha256(originalReplays), "Original replay evidence changed");
            reconciliation.put("completed_utc", Instant.now().toString());
            json.writerWithDefaultPrettyPrinter().writeValue(output.resolve("regional-reconciliation.json").toFile(), reconciliation);
        }
    }

    private static boolean matches(JsonNode observed, Object raw) {
        if (!(raw instanceof Map<?, ?> metrics)) return false;
        double weight = observed.path("weight").asDouble(), distance = observed.path("distance_m").asDouble();
        return Math.abs(((Number) metrics.get("weight")).doubleValue() - weight) <= 1e-7 + Math.abs(weight) * 1e-10
                && ((Number) metrics.get("time_ms")).longValue() == observed.path("time_ms").asLong()
                && Math.abs(((Number) metrics.get("distance_m")).doubleValue() - distance) <= 1e-8 + Math.abs(distance) * 1e-12;
    }

    private static String sha256(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(path)) {
            byte[] bytes = new byte[65536];
            int length;
            while ((length = input.read(bytes)) >= 0) digest.update(bytes, 0, length);
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
