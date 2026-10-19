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
package com.graphhopper.application.resources;

import com.fasterxml.jackson.databind.JsonNode;
import com.graphhopper.application.GraphHopperApplication;
import com.graphhopper.application.GraphHopperServerConfiguration;
import com.graphhopper.application.util.GraphHopperServerTestConfiguration;
import com.graphhopper.config.CHProfile;
import com.graphhopper.resources.MatrixResource;
import com.graphhopper.routing.TestProfiles;
import com.graphhopper.util.BodyAndStatus;
import com.graphhopper.util.Helper;
import com.graphhopper.util.TurnCostsConfig;
import io.dropwizard.testing.junit5.DropwizardAppExtension;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.util.List;
import java.util.stream.Stream;

import static com.graphhopper.application.resources.Util.postWithStatus;
import static com.graphhopper.application.util.TestUtils.clientTarget;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(DropwizardExtensionsSupport.class)
public class MatrixResourceTest {
    private static final String DIR = "./target/matrix-resource-gh/";
    private static final String DISCONNECTED_DIR = "./target/matrix-resource-disconnected-gh/";
    private static final String A = "[7.409678,43.727879]";
    private static final String B = "[7.429848,43.745987]";
    private static final String ALL_ARRAYS = "[\"times\",\"times_ms\",\"distances\",\"weights\"]";
    private static final DropwizardAppExtension<GraphHopperServerConfiguration> app =
            new DropwizardAppExtension<>(GraphHopperApplication.class, createConfig());
    private static final DropwizardAppExtension<GraphHopperServerConfiguration> disconnectedApp =
            new DropwizardAppExtension<>(GraphHopperApplication.class, createDisconnectedConfig());

    private static GraphHopperServerConfiguration createConfig() {
        GraphHopperServerConfiguration config = new GraphHopperServerTestConfiguration();
        config.getGraphHopperConfiguration()
                .putObject("prepare.min_network_size", 0)
                .putObject("datareader.file", "../core/files/monaco.osm.gz")
                .putObject("graph.location", DIR)
                .putObject("import.osm.ignored_highways", "")
                .putObject("routing.snap_preventions_default", "tunnel,bridge,ferry")
                .putObject("graph.encoded_values", "car_access,car_average_speed,foot_access,foot_average_speed,foot_priority,road_class,road_environment")
                .setProfiles(List.of(TestProfiles.accessAndSpeed("car"),
                        TestProfiles.accessAndSpeed("car_turns", "car").setTurnCostsConfig(TurnCostsConfig.car()),
                        TestProfiles.accessSpeedAndPriority("foot")));
        config.getGraphHopperConfiguration().setCHProfiles(List.of(new CHProfile("foot")));
        return config;
    }

    private static GraphHopperServerConfiguration createDisconnectedConfig() {
        GraphHopperServerConfiguration config = new GraphHopperServerTestConfiguration();
        config.getGraphHopperConfiguration()
                .putObject("prepare.min_network_size", 0)
                .putObject("datareader.file", "src/test/resources/com/graphhopper/application/matrix-disconnected.osm.xml")
                .putObject("graph.location", DISCONNECTED_DIR)
                .putObject("import.osm.ignored_highways", "")
                .putObject("graph.encoded_values", "car_access,car_average_speed,road_class,road_environment")
                .setProfiles(List.of(TestProfiles.accessAndSpeed("car")));
        return config;
    }

    @BeforeAll
    @AfterAll
    public static void cleanUp() {
        Helper.removeDir(new File(DIR));
        Helper.removeDir(new File(DISCONNECTED_DIR));
    }

    @ParameterizedTest
    @ValueSource(strings = {"reference", "flexible", "auto", "ch_experimental"})
    void squareAndDefaults(String algorithm) {
        JsonNode body = success("{\"profile\":\"car\",\"points\":[" + A + "," + B + "," + A + "],"
                + "\"out_arrays\":" + ALL_ARRAYS + ",\"local\":{\"algorithm\":\"" + algorithm + "\"}}");
        assertShape(body, 3, 3, List.of("times", "times_ms", "distances", "weights"));
        for (String metric : List.of("times", "times_ms", "distances", "weights")) {
            assertEquals(body.get(metric).get(0), body.get(metric).get(2));
            assertEquals(0, body.get(metric).get(0).get(0).asDouble());
            assertEquals(0, body.get(metric).get(0).get(2).asDouble());
            for (JsonNode row : body.get(metric))
                assertEquals(row.get(0), row.get(2));
        }
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 3; col++) {
                assertTrue(body.get("times_ms").get(row).get(col).isIntegralNumber());
                assertEquals(body.get("times_ms").get(row).get(col).asLong() / 1000,
                        body.get("times").get(row).get(col).asLong());
            }
        JsonNode metadata = body.path("info").path("local_matrix");
        assertEquals("car", metadata.path("profile").asText());
        assertEquals(3, metadata.path("origin_count").asInt());
        assertEquals(3, metadata.path("destination_count").asInt());
        assertEquals(2, metadata.path("unique_origin_count").asInt());
        assertEquals("reference".equals(algorithm) ? "reference" : "flexible", metadata.path("algorithm").asText());
        assertTrue(metadata.path("searches").asLong() > 0);
        assertTrue(metadata.path("visited_states").asLong() >= 0);
        for (String field : List.of("contract_version", "graph_fingerprint", "profile_fingerprint", "build_commit"))
            assertFalse(metadata.path(field).asText().isBlank(), field);
        assertEquals(3, metadata.path("from_snapped_points").size());
        assertEquals(3, metadata.path("to_snapped_points").size());
        for (String axis : List.of("from_snapped_points", "to_snapped_points"))
            for (JsonNode snap : metadata.path(axis)) {
                assertEquals(2, snap.path("coordinates").size());
                assertTrue(snap.path("coordinates").get(0).isNumber());
                assertTrue(snap.path("coordinates").get(1).isNumber());
                assertTrue(snap.path("snap_distance_m").isNumber());
                assertTrue(snap.path("snap_distance_m").asDouble() >= 0);
            }
        assertTrue(body.path("info").has("copyrights"));

        JsonNode defaults = success("{\"profile\":\"car\",\"points\":[" + A + "]}");
        assertShape(defaults, 1, 1, List.of("times", "distances"));
        assertFalse(defaults.has("weights"));
        assertFalse(defaults.has("times_ms"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"reference", "flexible", "ch_experimental"})
    void asymmetricOrderDuplicatesAndSingleAxes(String algorithm) {
        String suffix = ",\"out_arrays\":" + ALL_ARRAYS + ",\"local\":{\"algorithm\":\"" + algorithm + "\"}}";
        JsonNode base = success("{\"profile\":\"car\",\"points\":[" + A + "," + B + "]" + suffix);
        JsonNode matrix = success("{\"profile\":\"car\",\"from_points\":[" + B + "," + A + "," + B + "],"
                + "\"to_points\":[" + A + "," + B + "," + A + "," + A + "," + B + "," + A + "," + B + "]" + suffix);
        assertShape(matrix, 3, 7, List.of("times", "times_ms", "distances", "weights"));
        int[] from = {1, 0, 1};
        int[] to = {0, 1, 0, 0, 1, 0, 1};
        for (String metric : List.of("times", "times_ms", "distances", "weights"))
            for (int row = 0; row < from.length; row++)
                for (int col = 0; col < to.length; col++)
                    assertEquals(base.get(metric).get(from[row]).get(to[col]), matrix.get(metric).get(row).get(col));

        JsonNode oneToMany = success("{\"profile\":\"car\",\"from_points\":[" + A + "],\"to_points\":[" + B + "," + A + "]" + suffix);
        JsonNode manyToOne = success("{\"profile\":\"car\",\"from_points\":[" + A + "," + B + "],\"to_points\":[" + A + "]" + suffix);
        assertShape(oneToMany, 1, 2, List.of("times", "times_ms", "distances", "weights"));
        assertShape(manyToOne, 2, 1, List.of("times", "times_ms", "distances", "weights"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"foot", "car", "car_turns"})
    void experimentalCHSelectionCertificateAndExplicitFallbacks(String profile) {
        String axes = "{\"profile\":\"" + profile + "\",\"from_points\":[" + A + "," + B + "," + A
                + "],\"to_points\":[" + B + "," + A + "],\"out_arrays\":" + ALL_ARRAYS;
        JsonNode exact = success(axes + ",\"local\":{\"algorithm\":\"flexible\"}}");
        JsonNode result = success(axes + ",\"local\":{\"algorithm\":\"ch_experimental\"}}");
        for (String metric : List.of("times", "times_ms", "distances", "weights"))
            assertEquals(exact.get(metric), result.get(metric), metric);
        assertEquals(exact.get("hints"), result.get("hints"));
        JsonNode info = result.path("info").path("local_matrix");
        assertEquals("ch_experimental", info.path("requested_algorithm").asText());
        if (profile.equals("foot")) {
            assertEquals("ch_experimental", info.path("algorithm").asText());
            assertTrue(info.path("experimental").asBoolean());
            assertEquals("flexible_each_origin", info.path("optimality_verification").asText());
            assertEquals(2, info.path("ch_matrix").path("backward_searches").asInt());
            assertEquals(2, info.path("ch_matrix").path("forward_searches").asInt());
            assertEquals(2, info.path("ch_matrix").path("certificate_searches").asInt());
            assertEquals(6, info.path("searches").asInt());
            assertFalse(info.has("fallback_reason"));
        } else {
            assertEquals("flexible", info.path("algorithm").asText());
            assertEquals(profile.equals("car_turns") ? "TURN_COST_PROFILE_UNSUPPORTED" : "NO_PREPARED_CH",
                    info.path("fallback_reason").asText());
            assertFalse(info.has("ch_matrix"));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"times", "times_ms", "distances", "weights"})
    void selectiveArrays(String requested) {
        JsonNode result = success("{\"profile\":\"car\",\"points\":[" + A + "," + B + "],\"out_arrays\":[\"" + requested + "\"]}");
        JsonNode complete = success("{\"profile\":\"car\",\"points\":[" + A + "," + B + "],\"out_arrays\":" + ALL_ARRAYS + "}");
        assertShape(result, 2, 2, List.of(requested));
        assertEquals(complete.get(requested), result.get(requested));
        if (requested.equals("times"))
            assertEquals(complete.path("times_ms").get(0).get(1).asLong() / 1000, result.path("times").get(0).get(1).asLong());
        for (String metric : List.of("times", "times_ms", "distances", "weights"))
            assertEquals(requested.equals(metric), result.has(metric));
    }

    @ParameterizedTest
    @ValueSource(strings = {"car", "car_turns", "foot"})
    void agreesWithOrdinaryIndependentRoute(String profile) {
        JsonNode matrix = success("{\"profile\":\"" + profile + "\",\"from_points\":[" + A + "],\"to_points\":[" + B + "],"
                + "\"out_arrays\":" + ALL_ARRAYS + ",\"local\":{\"algorithm\":\"flexible\"}}");
        BodyAndStatus route = postWithStatus(clientTarget(app, "/route"), "{\"profile\":\"" + profile + "\",\"points\":[" + A + "," + B + "],"
                + "\"algorithm\":\"dijkstra\",\"ch.disable\":true,\"lm.disable\":true,\"instructions\":false,\"calc_points\":false,\"snap_preventions\":[]}");
        assertEquals(200, route.getStatus(), route.getBody().toString());
        JsonNode path = route.getBody().path("paths").get(0);
        assertEquals(path.path("time").asLong(), matrix.path("times_ms").get(0).get(0).asLong());
        assertEquals(path.path("distance").asDouble(), matrix.path("distances").get(0).get(0).asDouble(), 0.002);
        assertEquals(path.path("weight").asDouble(), matrix.path("weights").get(0).get(0).asDouble(), 1e-6);
        assertEquals("car_turns".equals(profile), matrix.path("info").path("local_matrix").path("turn_costs").asBoolean());
    }

    @ParameterizedTest
    @ValueSource(strings = {"reference", "flexible", "ch_experimental"})
    void invalidPointsKeepExactNullMaskAndInvalidDiagonal(String algorithm) {
        JsonNode body = success("{\"profile\":\"car\",\"points\":[[0,0]," + A + "],\"fail_fast\":false,"
                + "\"out_arrays\":" + ALL_ARRAYS + ",\"local\":{\"algorithm\":\"" + algorithm + "\"}}");
        for (String metric : List.of("times", "times_ms", "distances", "weights")) {
            assertTrue(body.path(metric).get(0).get(0).isNull());
            assertTrue(body.path(metric).get(0).get(1).isNull());
            assertTrue(body.path(metric).get(1).get(0).isNull());
            assertEquals(0, body.path(metric).get(1).get(1).asDouble());
        }
        assertInvalidIndexes(body, "invalid_from_points", 0);
        assertInvalidIndexes(body, "invalid_to_points", 0);
        for (String axis : List.of("from_snapped_points", "to_snapped_points")) {
            JsonNode invalid = body.path("info").path("local_matrix").path(axis).get(0);
            assertTrue(invalid.has("coordinates"));
            assertTrue(invalid.has("snap_distance_m"));
            assertTrue(invalid.get("coordinates").isNull());
            assertTrue(invalid.get("snap_distance_m").isNull());
        }
        JsonNode allInvalid = success("{\"profile\":\"car\",\"points\":[[0,0]],\"fail_fast\":false,\"local\":{\"algorithm\":\"" + algorithm + "\"}}");
        assertTrue(allInvalid.path("times").get(0).get(0).isNull());
        assertTrue(allInvalid.path("distances").get(0).get(0).isNull());
        BodyAndStatus failFast = post("{\"profile\":\"car\",\"points\":[[0,0]],\"local\":{\"algorithm\":\"" + algorithm + "\"}}");
        assertError(failFast, 400);
    }

    @ParameterizedTest
    @ValueSource(strings = {"reference", "flexible", "ch_experimental"})
    void disconnectedPairsAreDistinctFromInvalidPoints(String algorithm) {
        String request = "{\"profile\":\"car\",\"from_points\":[[0.005,0]],\"to_points\":[[0.035,0.03]],"
                + "\"out_arrays\":" + ALL_ARRAYS + ",\"local\":{\"algorithm\":\"" + algorithm + "\"},\"fail_fast\":";
        BodyAndStatus response = postWithStatus(clientTarget(disconnectedApp, "/matrix"), request + "false}");
        assertEquals(200, response.getStatus(), response.getBody().toString());
        JsonNode body = response.getBody();
        for (String metric : List.of("times", "times_ms", "distances", "weights")) {
            assertShape(body, 1, 1, List.of(metric));
            assertTrue(body.path(metric).get(0).get(0).isNull());
        }
        assertEquals(1, body.path("hints").size());
        JsonNode hint = body.path("hints").get(0);
        assertEquals("CONNECTION_NOT_FOUND", hint.path("details").asText());
        assertEquals(1, hint.path("point_pairs").size());
        assertEquals(0, hint.path("point_pairs").get(0).get(0).asInt());
        assertEquals(0, hint.path("point_pairs").get(0).get(1).asInt());
        assertFalse(hint.has("invalid_from_points"));
        assertFalse(hint.has("invalid_to_points"));
        for (String axis : List.of("from_snapped_points", "to_snapped_points"))
            assertTrue(body.path("info").path("local_matrix").path(axis).get(0).path("coordinates").isArray());
        assertError(postWithStatus(clientTarget(disconnectedApp, "/matrix"), request + "true}"), 400);
    }

    @ParameterizedTest
    @MethodSource("invalidRequests")
    void rejectsInvalidRequests(String json) {
        assertError(post(json), 400);
    }

    static Stream<String> invalidRequests() {
        String valid = "{\"profile\":\"car\",\"points\":[" + A + "]";
        return Stream.of("", "null", "[]", "{", valid + "}{}", valid + ",\"profile\":\"car\"}",
                "{\"points\":[" + A + "]}", "{\"profile\":null,\"points\":[" + A + "]}",
                "{\"profile\":false,\"points\":[" + A + "]}", "{\"profile\":\" \",\"points\":[" + A + "]}",
                "{\"profile\":\"unknown\",\"points\":[" + A + "]}", "{\"profile\":\"car\"}",
                "{\"profile\":\"car\",\"points\":[]}", "{\"profile\":\"car\",\"points\":null}",
                "{\"profile\":\"car\",\"from_points\":[" + A + "]}",
                valid + ",\"from_points\":[" + A + "],\"to_points\":[" + A + "]}",
                "{\"profile\":\"car\",\"points\":[[true,43]]}", "{\"profile\":\"car\",\"points\":[[\"7\",43]]}",
                "{\"profile\":\"car\",\"points\":[[7,null]]}", "{\"profile\":\"car\",\"points\":[[7,43,0]]}",
                "{\"profile\":\"car\",\"points\":[[181,43]]}", "{\"profile\":\"car\",\"points\":[[7,91]]}",
                "{\"profile\":\"car\",\"points\":[[1e400,43]]}", "{\"profile\":\"car\",\"points\":[[NaN,43]]}",
                valid + ",\"out_arrays\":[]}", valid + ",\"out_arrays\":null}", valid + ",\"out_arrays\":[\"times\",\"times\"]}",
                valid + ",\"out_arrays\":[\"speeds\"]}", valid + ",\"out_arrays\":[true]}",
                valid + ",\"fail_fast\":\"false\"}", valid + ",\"fail_fast\":null}",
                valid + ",\"local\":null}", valid + ",\"local\":[]}", valid + ",\"local\":{\"algorithm\":null}}",
                valid + ",\"local\":{\"algorithm\":\"ch\"}}", valid + ",\"local\":{\"threads\":2}}",
                valid + ",\"headings\":[]}", valid + ",\"curbsides\":[]}", valid + ",\"custom_model\":{}}",
                valid + ",\"departure_time\":\"2026-10-19T10:00:00Z\"}", valid + ",\"point_hints\":[]}",
                valid + ",\"snap_preventions\":[]}", valid + ",\"key\":\"unused\"}", valid + ",\"unknown\":false}");
    }

    @Test
    void boundsBodyAxesAndCellsBeforeAllocation() {
        BodyAndStatus body = post("{\"profile\":\"car\",\"points\":[" + A + "]}" + " ".repeat(MatrixResource.MAX_BODY_BYTES));
        assertError(body, 413);
        String thousandAndOne = (A + ",").repeat(1000) + A;
        assertError(post("{\"profile\":\"car\",\"from_points\":[" + thousandAndOne + "],\"to_points\":[" + A + "]}"), 413);
        String origins = (A + ",").repeat(316) + A;
        String destinations = (B + ",").repeat(316) + B;
        assertError(post("{\"profile\":\"car\",\"from_points\":[" + origins + "],\"to_points\":[" + destinations + "]}"), 413);
    }

    @Test
    void rejectsQueryRestrictionsInsteadOfSilentlyIgnoringThem() {
        BodyAndStatus response = postWithStatus(clientTarget(app, "/matrix").queryParam("headings", "90"),
                "{\"profile\":\"car\",\"points\":[" + A + "]}");
        assertError(response, 400);
    }

    private static BodyAndStatus post(String json) {
        return postWithStatus(clientTarget(app, "/matrix"), json);
    }

    private static JsonNode success(String json) {
        BodyAndStatus response = post(json);
        assertEquals(200, response.getStatus(), response.getBody().toString());
        return response.getBody();
    }

    private static void assertShape(JsonNode body, int rows, int cols, List<String> metrics) {
        for (String metric : metrics) {
            assertEquals(rows, body.path(metric).size(), metric);
            for (JsonNode row : body.path(metric))
                assertEquals(cols, row.size(), metric);
        }
    }

    private static void assertError(BodyAndStatus response, int expected) {
        assertEquals(expected, response.getStatus(), response.getBody().toString());
        assertFalse(response.getBody().path("message").asText().isBlank());
        assertFalse(response.getBody().path("hints").get(0).path("details").asText().isBlank());
        for (String metric : List.of("times", "times_ms", "distances", "weights"))
            assertFalse(response.getBody().has(metric));
    }

    private static void assertInvalidIndexes(JsonNode body, String name, int expected) {
        for (JsonNode hint : body.path("hints")) {
            if (hint.has(name)) {
                assertEquals(1, hint.get(name).size());
                assertEquals(expected, hint.get(name).get(0).asInt());
                assertFalse(hint.path("details").asText().isBlank());
                return;
            }
        }
        fail("Missing " + name + " in " + body);
    }
}
