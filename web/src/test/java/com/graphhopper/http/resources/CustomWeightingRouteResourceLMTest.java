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
package com.graphhopper.http.resources;

import com.fasterxml.jackson.databind.JsonNode;
import com.graphhopper.config.LMProfile;
import com.graphhopper.config.Profile;
import com.graphhopper.http.GraphHopperApplication;
import com.graphhopper.http.GraphHopperServerConfiguration;
import com.graphhopper.http.util.GraphHopperServerTestConfiguration;
import com.graphhopper.routing.weighting.custom.CustomProfile;
import com.graphhopper.util.CustomModel;
import com.graphhopper.util.Helper;
import io.dropwizard.testing.junit5.DropwizardAppExtension;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Response;
import java.io.File;
import java.util.Arrays;

import static com.graphhopper.http.resources.CustomWeightingRouteResourceTest.yamlToJson;
import static com.graphhopper.http.util.TestUtils.clientTarget;
import static org.junit.Assert.*;

/**
 * @author Peter Karich
 */
@ExtendWith(DropwizardExtensionsSupport.class)
public class CustomWeightingRouteResourceLMTest {
    private static final String DIR = "./target/andorra-gh/";
    private static final DropwizardAppExtension<GraphHopperServerConfiguration> app = new DropwizardAppExtension<>(GraphHopperApplication.class, createConfig());

    private static GraphHopperServerConfiguration createConfig() {
        GraphHopperServerConfiguration config = new GraphHopperServerTestConfiguration();
        config.getGraphHopperConfiguration().
                putObject("graph.flag_encoders", "car,foot").
                putObject("prepare.min_network_size", 0).
                putObject("datareader.file", "../core/files/andorra.osm.pbf").
                putObject("graph.encoded_values", "surface").
                putObject("graph.location", DIR)
                .setProfiles(Arrays.asList(
                        // give strange profile names to ensure that we do not mix vehicle and profile:
                        new CustomProfile("car_custom").setCustomModel(new CustomModel()).setVehicle("car"),
                        new Profile("foot_profile").setVehicle("foot").setWeighting("fastest"),
                        new CustomProfile("foot_custom").setCustomModel(new CustomModel()).setVehicle("foot"))).
                setLMProfiles(Arrays.asList(new LMProfile("car_custom"), new LMProfile("foot_custom")));
        return config;
    }

    @BeforeAll
    @AfterAll
    public static void cleanUp() {
        Helper.removeDir(new File(DIR));
    }

    @Test
    public void testCustomWeightingJson() {
        String jsonQuery = "{" +
                " \"points\": [[1.518946,42.531453],[1.54006,42.511178]]," +
                " \"profile\": \"car_custom\"" +
                "}";
        final Response response = clientTarget(app, "/route-custom").request().post(Entity.json(jsonQuery));
        assertEquals(200, response.getStatus());
        JsonNode json = response.readEntity(JsonNode.class);
        JsonNode infoJson = json.get("info");
        assertFalse(infoJson.has("errors"));
        JsonNode path = json.get("paths").get(0);
        assertEquals(path.get("distance").asDouble(), 3180, 10);
        assertEquals(path.get("time").asLong(), 182_000, 1_000);
    }

    @Test
    public void testCustomWeighting() {
        String yamlQuery = "points: [[1.529106,42.506567], [1.54006,42.511178]]\n" +
                "profile: car_custom\n" +
                "priority:\n" +
                "  - if: road_class != SECONDARY\n" +
                "    multiply by: 0.5\n";
        JsonNode yamlNode = queryYaml(yamlQuery, 200).readEntity(JsonNode.class);
        JsonNode path = yamlNode.get("paths").get(0);
        assertEquals(path.get("distance").asDouble(), 1317, 5);

        // now prefer primary roads via special yaml-map notation
        yamlQuery = "points: [[1.5274,42.506211], [1.54006,42.511178]]\n" +
                "profile: car_custom\n" +
                "priority:\n" +
                "  - if: road_class == RESIDENTIAL\n" +
                "    multiply by: 0.8\n" +
                "  - else if: road_class == PRIMARY\n" +
                "    multiply by: 1\n" +
                "  - else:\n" +
                "    multiply by: 0.66\n";
        yamlNode = queryYaml(yamlQuery, 200).readEntity(JsonNode.class);
        path = yamlNode.get("paths").get(0);
        assertEquals(path.get("distance").asDouble(), 1707, 5);
    }

    @Test
    public void testCustomWeightingAvoidTunnels() {
        String yamlQuery = "points: [[1.533365, 42.506211], [1.523924, 42.520605]]\n" +
                "profile: car_custom\n" +
                "priority:\n" +
                "  - if: road_environment == TUNNEL\n" +
                "    multiply by: 0.1\n";
        JsonNode yamlNode = queryYaml(yamlQuery, 200).readEntity(JsonNode.class);
        JsonNode path = yamlNode.get("paths").get(0);
        assertEquals(path.get("distance").asDouble(), 2437, 5);
    }

    @Test
    public void testUnknownProfile() {
        String yamlQuery = "points: [[1.540875,42.510672], [1.54212,42.511131]]\n" +
                "profile: unknown";
        JsonNode yamlNode = queryYaml(yamlQuery, 400).readEntity(JsonNode.class);
        assertTrue(yamlNode.get("message").asText().startsWith("profile 'unknown' not found"));
    }

    @Test
    public void testCustomWeightingRequired() {
        String yamlQuery = "points: [[1.540875,42.510672], [1.54212,42.511131]]\n" +
                "profile: foot_profile";
        JsonNode yamlNode = queryYaml(yamlQuery, 400).readEntity(JsonNode.class);
        assertEquals("profile 'foot_profile' cannot be used for a custom request because it has weighting=fastest", yamlNode.get("message").asText());
    }

    @Test
    public void testCustomWeightingSimplisticWheelchair() {
        String yamlQuery = "points: [[1.540875,42.510672], [1.54212,42.511131]]\n" +
                "profile: foot_custom\n" +
                "priority:\n" +
                "  - if: road_class == STEPS\n" +
                "    multiply by: 0\n";
        JsonNode yamlNode = queryYaml(yamlQuery, 200).readEntity(JsonNode.class);
        JsonNode path = yamlNode.get("paths").get(0);
        assertEquals(path.get("distance").asDouble(), 328, 5);
    }

    Response queryYaml(String yamlStr, int code) {
        Response response = clientTarget(app, "/route-custom").request().post(Entity.json(yamlToJson(yamlStr)));
        assertEquals(code, response.getStatus());
        return response;
    }
}
