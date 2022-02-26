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
import com.graphhopper.config.LMProfile;
import com.graphhopper.config.Profile;
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

import static com.graphhopper.application.util.TestUtils.clientTarget;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * @author Peter Karich
 */
@ExtendWith(DropwizardExtensionsSupport.class)
public class RouteResourceCustomModelLMTest {
    private static final String DIR = "./target/andorra-gh/";
    private static final DropwizardAppExtension<GraphHopperServerConfiguration> app = new DropwizardAppExtension<>(GraphHopperApplication.class, createConfig());

    private static GraphHopperServerConfiguration createConfig() {
        GraphHopperServerConfiguration config = new GraphHopperServerTestConfiguration();
        config.getGraphHopperConfiguration().
                putObject("graph.flag_encoders", "car,foot").
                putObject("datareader.file", "../core/files/andorra.osm.pbf").
                putObject("graph.location", DIR).
                putObject("graph.encoded_values", "surface").
                setProfiles(Arrays.asList(
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
    public void testCustomProfile() {
        String jsonQuery = "{" +
                " \"points\": [[1.518946,42.531453],[1.54006,42.511178]]," +
                " \"profile\": \"car_custom\"" +
                "}";
        Response response = query(jsonQuery, 200);
        JsonNode json = response.readEntity(JsonNode.class);
        JsonNode infoJson = json.get("info");
        assertFalse(infoJson.has("errors"));
        JsonNode path = json.get("paths").get(0);
        assertEquals(path.get("distance").asDouble(), 3180, 10);
        assertEquals(path.get("time").asLong(), 182_000, 1_000);
    }

    @Test
    public void testCustomWeighting() {
        String body = "{\"points\": [[1.529106,42.506567], [1.54006,42.511178]]," +
                " \"profile\": \"car_custom\", \"custom_model\":{" +
                " \"priority\": [{\"if\": \"road_class != SECONDARY\", \"multiply_by\": 0.5}]}" +
                "}";
        JsonNode jsonNode = query(body, 200).readEntity(JsonNode.class);
        JsonNode path = jsonNode.get("paths").get(0);
        assertEquals(path.get("distance").asDouble(), 1317, 5);

        // now prefer primary roads
        body = "{\"points\": [[1.5274,42.506211], [1.54006,42.511178]]," +
                "\"profile\": \"car_custom\"," +
                "\"custom_model\": {" +
                "\"priority\": [" +
                "{\"if\": \"road_class == RESIDENTIAL\", \"multiply_by\": 0.8}," +
                "{\"else_if\": \"road_class == PRIMARY\", \"multiply_by\": 1}," +
                "{\"else\": \"\", \"multiply_by\": 0.66}" +
                "]}" +
                "}";
        jsonNode = query(body, 200).readEntity(JsonNode.class);
        path = jsonNode.get("paths").get(0);
        assertEquals(path.get("distance").asDouble(), 1707, 5);
    }

    @Test
    public void testCustomWeightingAvoidTunnels() {
        String body = "{\"points\": [[1.533365, 42.506211], [1.523924, 42.520605]]," +
                "\"profile\": \"car_custom\"," +
                "\"custom_model\": {" +
                "  \"priority\": [" +
                "    {\"if\": \"road_environment == TUNNEL\", \"multiply_by\": 0.1}" +
                "  ]" +
                "}" +
                "}";
        JsonNode jsonNode = query(body, 200).readEntity(JsonNode.class);
        JsonNode path = jsonNode.get("paths").get(0);
        assertEquals(path.get("distance").asDouble(), 2437, 5);
    }

    @Test
    public void testCustomWeightingSimplisticWheelchair() {
        String body = "{\"points\": [[1.540875,42.510672], [1.54212,42.511131]]," +
                "\"profile\": \"foot_custom\"," +
                "\"custom_model\": {" +
                "\"priority\":[" +
                " {\"if\": \"road_class == STEPS\", \"multiply_by\": 0}]}" +
                "}";
        JsonNode jsonNode = query(body, 200).readEntity(JsonNode.class);
        JsonNode path = jsonNode.get("paths").get(0);
        assertEquals(path.get("distance").asDouble(), 328, 5);
    }

    Response query(String body, int code) {
        Response response = clientTarget(app, "/route").request().post(Entity.json(body));
        assertEquals(code, response.getStatus());
        return response;
    }
}
