/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.routing.matrix;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphhopper.GraphHopper;
import com.graphhopper.GraphHopperConfig;
import com.graphhopper.config.Profile;
import com.graphhopper.matrix.MatrixRequest;
import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.util.AllEdgesIterator;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.util.FetchMode;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.PointList;
import com.graphhopper.util.TurnCostsConfig;
import com.graphhopper.util.shapes.GHPoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.BufferedWriter;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Opt-in assurance on a separately acquired, checksum-pinned regional extract. */
@EnabledIfSystemProperty(named = "matrix.regional", matches = "true")
class MatrixRegionalTest {
    private static final long SEED = 20261019L;
    private static final List<String> METRICS = List.of("times", "times_ms", "distances", "weights");
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void compareRegionalProfilesAndSuppliedManchesterExamples() throws Exception {
        Path root = findRoot();
        Path fixture = Path.of(requiredProperty("matrix.regional.pbf")).toAbsolutePath();
        String expectedSha = requiredProperty("matrix.regional.sha256").toLowerCase();
        assertTrue(Files.isRegularFile(fixture), "Missing regional fixture: " + fixture);
        assertEquals(expectedSha, sha256(fixture), "Regional fixture must match the acquisition manifest");
        Path output = root.resolve("docs/matrix");
        Files.createDirectories(output);
        Path replay = output.resolve("regional-failure-replays.jsonl");
        Map<String, Object> report = new LinkedHashMap<>();
        List<Map<String, Object>> cases = new ArrayList<>();
        report.put("status", "RUNNING");
        report.put("started_utc", Instant.now().toString());
        report.put("source_path", fixture.toString());
        report.put("source_sha256", expectedSha);
        report.put("seed", SEED);
        report.put("actual_matrix_workers", 1);
        report.put("ch_preparations", 0);
        report.put("lm_preparations", 0);
        report.put("tolerances", Map.of("weight_absolute", 1e-7, "weight_relative", 1e-10,
                "distance_m", .002, "duration_ms", 1));
        report.put("cases", cases);
        report.put("failure_replay_path", replay.toString());
        report.put("limitations", List.of("A regional extract can clip routes near its boundary; comparisons use the identical imported graph.",
                "The stratified sample is not exhaustive proof over the imported network."));
        GraphHopper hopper = new GraphHopper();
        long importedAt = System.nanoTime();
        try (BufferedWriter evidence = Files.newBufferedWriter(replay, StandardCharsets.UTF_8)) {
            GraphHopperConfig config = new GraphHopperConfig()
                    .putObject("datareader.file", fixture.toString())
                    .putObject("graph.location", root.resolve("target/matrix-regional-" + UUID.randomUUID()).toString())
                    .putObject("graph.dataaccess.default_type", "RAM")
                    .putObject("graph.encoded_values", "car_access,car_average_speed,road_access,foot_access,foot_priority,foot_average_speed,foot_road_access,hike_rating,mtb_rating,country,road_class")
                    .putObject("prepare.min_network_size", 0)
                    .putObject("prepare.subnetworks.threads", 1)
                    .putObject("import.osm.ignored_highways", "construction")
                    .setProfiles(List.of(new Profile("foot").setCustomModel(GHUtility.loadCustomModelFromJar("foot.json")),
                            new Profile("car").setCustomModel(GHUtility.loadCustomModelFromJar("car.json"))
                                    .setTurnCostsConfig(TurnCostsConfig.car().setUTurnCosts(60))))
                    .setCHProfiles(List.of()).setLMProfiles(List.of());
            hopper.init(config);
            hopper.importOrLoad();
            report.put("import_ms", (System.nanoTime() - importedAt) / 1e6);
            report.put("graph_nodes", hopper.getBaseGraph().getNodes());
            report.put("graph_edges", hopper.getBaseGraph().getEdges());
            report.put("graph_location", hopper.getGraphHopperLocation());
            report.put("car_u_turn_cost_seconds", 60);
            report.put("profile_custom_model_sha256", Map.of(
                    "foot.json", sha256(root.resolve("core/src/main/resources/com/graphhopper/custom_models/foot.json")),
                    "car.json", sha256(root.resolve("core/src/main/resources/com/graphhopper/custom_models/car.json"))));
            MatrixEngine engine = new MatrixEngine(hopper,
                    new MatrixEngine.Limits(1000, 100000, 1,
                            Long.getLong("matrix.regional.timeout_ms", 900000L), Integer.MAX_VALUE));
            Map<String, Object> strata = new LinkedHashMap<>();
            List<GHPoint> points = stratifiedPoints(hopper, strata);
            report.put("strata", strata);
            report.put("stratified_point_count", points.size());
            int uniquePoints = new HashSet<>(coordinates(points)).size();
            report.put("unique_stratified_point_count", uniquePoints);
            report.put("unique_stratified_pairs_per_profile", (long) uniquePoints * uniquePoints);
            assertTrue((long) uniquePoints * uniquePoints >= 1000);
            int unexplained = 0;
            for (String profile : List.of("foot", "car")) {
                Map<String, Object> result = compareCase(hopper, engine, "stratified_regional_square", profile,
                        points, points, METRICS, false, evidence);
                cases.add(result);
                unexplained += ((Number) result.get("unexplained_mismatches")).intValue();
            }
            for (String name : List.of("manchester_square.json", "manchester_rectangular.json", "manchester_one_to_many.json")) {
                byte[] bytes;
                try (InputStream resource = getClass().getResourceAsStream(name)) {
                    assertNotNull(resource, name);
                    bytes = resource.readAllBytes();
                }
                JsonNode request = JSON.readTree(bytes);
                List<GHPoint> origins = axis(request.has("points") ? request.get("points") : request.get("from_points"));
                List<GHPoint> destinations = axis(request.has("points") ? request.get("points") : request.get("to_points"));
                List<String> arrays = new ArrayList<>();
                request.path("out_arrays").forEach(value -> arrays.add(value.asText()));
                Map<String, Object> result = compareCase(hopper, engine, name, request.path("profile").asText(),
                        origins, destinations, arrays, request.path("fail_fast").asBoolean(true), evidence);
                result.put("request_sha256", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
                cases.add(result);
                unexplained += ((Number) result.get("unexplained_mismatches")).intValue();
            }
            report.put("unexplained_mismatches", unexplained);
            int classified = cases.stream().mapToInt(result -> ((Number) result.get("explained_by_path_evidence")).intValue()).sum();
            report.put("explained_by_path_evidence", classified);
            report.put("status", unexplained != 0 ? "FAIL" : classified == 0 ? "PASS" : "PASS_WITH_CLASSIFIED_SPLIT_PRECISION");
            assertEquals(0, unexplained, "Inspect " + replay);
        } catch (Exception | AssertionError failure) {
            report.put("status", "FAIL");
            report.put("failure", failure.toString());
            throw failure;
        } finally {
            hopper.close();
            report.put("completed_utc", Instant.now().toString());
            JSON.writerWithDefaultPrettyPrinter().writeValue(output.resolve("regional-validation.json").toFile(), report);
        }
    }

    private static Map<String, Object> compareCase(GraphHopper hopper, MatrixEngine engine, String name, String profile,
                                                   List<GHPoint> origins, List<GHPoint> destinations,
                                                   List<String> requestedArrays, boolean failFast, BufferedWriter evidence) throws Exception {
        System.out.println("Regional case " + name + " profile=" + profile + " pairs=" + origins.size() * destinations.size());
        long started = System.nanoTime();
        Map<String, Object> flexible = engine.route(new MatrixRequest(profile, origins, destinations, METRICS, failFast, "flexible"));
        double flexibleMs = (System.nanoTime() - started) / 1e6;
        started = System.nanoTime();
        Map<String, Object> reference = engine.route(new MatrixRequest(profile, origins, destinations, METRICS, failFast, "reference"));
        double referenceMs = (System.nanoTime() - started) / 1e6;
        int reachable = 0, nullPairs = 0, mismatches = 0, explained = 0, unexplained = 0, asymmetric = 0;
        List<GHPoint> combined = new ArrayList<>(origins);
        combined.addAll(destinations);
        for (int i = 0; i < origins.size(); i++) {
            MatrixPrecisionDiagnostics diagnostics = null;
            for (int j = 0; j < destinations.size(); j++) {
                Number expected = cell(reference, "weights", i, j), actual = cell(flexible, "weights", i, j);
                if (expected == null) nullPairs++; else reachable++;
                boolean mismatch = (expected == null) != (actual == null);
                for (String metric : METRICS) {
                    Number value = cell(flexible, metric, i, j);
                    assertEquals(actual == null, value == null, name + " null mask " + metric + " " + i + "/" + j);
                }
                if (expected != null && actual != null) {
                    mismatch |= Math.abs(expected.doubleValue() - actual.doubleValue()) > 1e-7 + Math.abs(expected.doubleValue()) * 1e-10;
                    mismatch |= Math.abs(cell(reference, "distances", i, j).doubleValue() - cell(flexible, "distances", i, j).doubleValue()) > .002;
                    mismatch |= Math.abs(cell(reference, "times_ms", i, j).longValue() - cell(flexible, "times_ms", i, j).longValue()) > 1;
                    assertEquals(cell(flexible, "times_ms", i, j).longValue() / 1000,
                            cell(flexible, "times", i, j).longValue(), name + " seconds identity");
                    if (origins == destinations && i < j) {
                        Number reverse = cell(flexible, "weights", j, i);
                        if (reverse == null || Math.abs(actual.doubleValue() - reverse.doubleValue()) > 1e-7) asymmetric++;
                    }
                }
                if (mismatch) {
                    mismatches++;
                    if (diagnostics == null) diagnostics = new MatrixPrecisionDiagnostics(hopper, profile, combined);
                    MatrixPrecisionDiagnostics.Evidence diagnosis = diagnostics.diagnose(i, origins.size() + j);
                    Map<String, Object> observedReference = observedMetrics(reference, i, j);
                    Map<String, Object> observedShared = observedMetrics(flexible, i, j);
                    boolean referenceMatches = matchesDiagnostic(diagnosis.details().get("upstream_route_metrics"), observedReference);
                    boolean sharedMatches = matchesDiagnostic(diagnosis.details().get("reported_shared_metrics"), observedShared);
                    boolean classified = diagnosis.explained() && referenceMatches && sharedMatches;
                    if (classified) explained++; else unexplained++;
                    Map<String, Object> replay = new LinkedHashMap<>();
                    replay.put("case", name);
                    replay.put("seed", SEED);
                    replay.put("origin_index", i);
                    replay.put("destination_index", j);
                    replay.put("observed_reference", observedReference);
                    replay.put("observed_shared", observedShared);
                    replay.put("observed_reference_matches_diagnostic", referenceMatches);
                    replay.put("observed_shared_matches_diagnostic", sharedMatches);
                    replay.put("accepted_with_independent_path_evidence", classified);
                    replay.put("path_evidence", diagnosis.details());
                    evidence.write(JSON.writeValueAsString(replay));
                    evidence.newLine();
                    evidence.flush();
                }
            }
        }
        if (!requestedArrays.equals(METRICS)) {
            Map<String, Object> selected = engine.route(new MatrixRequest(profile, origins, destinations, requestedArrays, failFast, "flexible"));
            for (String metric : METRICS) {
                assertEquals(requestedArrays.contains(metric), selected.containsKey(metric));
                if (requestedArrays.contains(metric)) assertEquals(flexible.get(metric), selected.get(metric));
            }
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("case", name);
        summary.put("profile", profile);
        summary.put("origin_coordinates_lon_lat", coordinates(origins));
        summary.put("destination_coordinates_lon_lat", coordinates(destinations));
        summary.put("requested_arrays", requestedArrays);
        summary.put("pairs", origins.size() * destinations.size());
        summary.put("reachable_pairs", reachable);
        summary.put("null_pairs", nullPairs);
        summary.put("asymmetric_reciprocal_weight_pairs", asymmetric);
        summary.put("mismatches_above_fixed_thresholds", mismatches);
        summary.put("explained_by_path_evidence", explained);
        summary.put("unexplained_mismatches", unexplained);
        summary.put("flexible_ms", flexibleMs);
        summary.put("reference_ms", referenceMs);
        summary.put("flexible_metadata", metadata(flexible));
        summary.put("reference_metadata", metadata(reference));
        summary.put("status", unexplained != 0 ? "FAIL" : explained == 0 ? "PASS" : "PASS_WITH_CLASSIFIED_SPLIT_PRECISION");
        return summary;
    }

    private static Map<String, Object> observedMetrics(Map<String, Object> matrix, int row, int column) {
        Number weight = cell(matrix, "weights", row, column);
        if (weight == null) return Map.of("reachable", false);
        return Map.of("reachable", true, "weight", weight.doubleValue(),
                "time_ms", cell(matrix, "times_ms", row, column).longValue(),
                "distance_m", cell(matrix, "distances", row, column).doubleValue());
    }

    private static boolean matchesDiagnostic(Object raw, Map<String, Object> observed) {
        if (!(raw instanceof Map<?, ?> metrics) || !(observed.get("weight") instanceof Number weight)
                || !(metrics.get("weight") instanceof Number diagnosticWeight)
                || !(metrics.get("time_ms") instanceof Number diagnosticTime)
                || !(metrics.get("distance_m") instanceof Number diagnosticDistance)) return false;
        double distance = ((Number) observed.get("distance_m")).doubleValue();
        return Math.abs(diagnosticWeight.doubleValue() - weight.doubleValue()) <= 1e-7 + Math.abs(weight.doubleValue()) * 1e-10
                && diagnosticTime.longValue() == ((Number) observed.get("time_ms")).longValue()
                && Math.abs(diagnosticDistance.doubleValue() - distance) <= 1e-8 + Math.abs(distance) * 1e-12;
    }

    private static List<GHPoint> stratifiedPoints(GraphHopper hopper, Map<String, Object> strata) {
        List<GHPoint> points = new ArrayList<>(List.of(new GHPoint(53.4774, -2.2309), new GHPoint(53.4808, -2.2426),
                new GHPoint(53.4761, -2.2541), new GHPoint(53.4875, -2.242)));
        points.add(points.get(0));
        points.add(points.get(1));
        NodeAccess nodes = hopper.getBaseGraph().getNodeAccess();
        int count = hopper.getBaseGraph().getNodes();
        assertTrue(count > 0);
        int south = 0, north = 0, west = 0, east = 0;
        for (int node = 1; node < count; node++) {
            if (nodes.getLat(node) < nodes.getLat(south)) south = node;
            if (nodes.getLat(node) > nodes.getLat(north)) north = node;
            if (nodes.getLon(node) < nodes.getLon(west)) west = node;
            if (nodes.getLon(node) > nodes.getLon(east)) east = node;
        }
        for (int node : new int[]{south, north, west, east})
            points.add(new GHPoint(nodes.getLat(node), nodes.getLon(node)));
        strata.put("boundary_extreme_node_ids_south_north_west_east", List.of(south, north, west, east));
        BooleanEncodedValue access = hopper.getEncodingManager().getBooleanEncodedValue("car_access");
        AllEdgesIterator edges = hopper.getBaseGraph().getAllEdges();
        boolean directed = false;
        while (edges.next() && !directed) {
            if (edges.get(access) == edges.getReverse(access)) continue;
            PointList geometry = edges.fetchWayGeometry(FetchMode.ALL);
            for (int index = 1; index < geometry.size(); index++) {
                double lat0 = geometry.getLat(index - 1), lon0 = geometry.getLon(index - 1);
                double lat1 = geometry.getLat(index), lon1 = geometry.getLon(index);
                if (lat0 == lat1 && lon0 == lon1) continue;
                points.add(new GHPoint(lat0 + (lat1 - lat0) / 3, lon0 + (lon1 - lon0) / 3));
                points.add(new GHPoint(lat0 + 2 * (lat1 - lat0) / 3, lon0 + 2 * (lon1 - lon0) / 3));
                strata.put("one_way_edge_key", edges.getEdgeKey());
                strata.put("one_way_forward_access", edges.get(access));
                directed = true;
                break;
            }
        }
        assertTrue(directed, "Expected a directed road in the regional car network");
        Random random = new Random(SEED);
        for (int i = 0; i < 8; i++)
            points.add(new GHPoint(53.4808 + (random.nextDouble() - .5) * .02, -2.2426 + (random.nextDouble() - .5) * .02));
        List<Integer> sampled = new ArrayList<>();
        Set<List<Double>> distinct = new HashSet<>(coordinates(points));
        while (distinct.size() < 34) {
            int node = random.nextInt(count);
            GHPoint point = new GHPoint(nodes.getLat(node), nodes.getLon(node));
            if (!distinct.add(List.of(point.lon, point.lat))) continue;
            sampled.add(node);
            points.add(point);
        }
        strata.put("seeded_random_node_ids", sampled);
        strata.put("point_index_ranges", Map.of("supplied_manchester_anchors", List.of(0, 3), "duplicate_anchors", List.of(4, 5),
                "boundary_extremes", List.of(6, 9), "same_directed_edge_interior", List.of(10, 11),
                "local_city_jitter", List.of(12, 19), "seeded_regional_nodes", List.of(20, points.size() - 1)));
        return points;
    }

    @SuppressWarnings("unchecked")
    private static Number cell(Map<String, Object> matrix, String metric, int row, int column) {
        return ((List<List<Number>>) matrix.get(metric)).get(row).get(column);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> metadata(Map<String, Object> matrix) {
        return (Map<String, Object>) ((Map<String, Object>) matrix.get("info")).get("local_matrix");
    }

    private static List<GHPoint> axis(JsonNode array) {
        List<GHPoint> result = new ArrayList<>();
        array.forEach(point -> result.add(new GHPoint(point.get(1).asDouble(), point.get(0).asDouble())));
        return result;
    }

    private static List<List<Double>> coordinates(List<GHPoint> points) {
        return points.stream().map(point -> List.of(point.lon, point.lat)).toList();
    }

    private static Path findRoot() {
        Path path = Path.of("").toAbsolutePath();
        while (path != null && !Files.exists(path.resolve("core/pom.xml"))) path = path.getParent();
        assertNotNull(path, "Repository root not found");
        return path;
    }

    private static String requiredProperty(String name) {
        String value = System.getProperty(name);
        assertNotNull(value, "Required opt-in property: " + name);
        assertFalse(value.isBlank(), name);
        return value;
    }

    private static String sha256(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[1024 * 1024];
            int length;
            while ((length = input.read(buffer)) >= 0) digest.update(buffer, 0, length);
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
