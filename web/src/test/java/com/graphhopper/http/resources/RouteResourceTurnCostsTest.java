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
import com.graphhopper.config.CHProfileConfig;
import com.graphhopper.config.LMProfileConfig;
import com.graphhopper.config.ProfileConfig;
import com.graphhopper.http.GraphHopperApplication;
import com.graphhopper.http.util.GraphHopperServerTestConfiguration;
import com.graphhopper.util.Helper;
import io.dropwizard.testing.junit5.DropwizardAppExtension;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Response;
import java.io.File;
import java.util.Arrays;

import static com.graphhopper.http.util.TestUtils.clientTarget;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@ExtendWith(DropwizardExtensionsSupport.class)
public class RouteResourceTurnCostsTest {

    private static final String DIR = "./target/route-resource-turn-costs-gh/";
    private DropwizardAppExtension<GraphHopperServerTestConfiguration> app = new DropwizardAppExtension(GraphHopperApplication.class, createConfig());

    private static GraphHopperServerTestConfiguration createConfig() {
        GraphHopperServerTestConfiguration config = new GraphHopperServerTestConfiguration();
        config.getGraphHopperConfiguration().
                putObject("graph.flag_encoders", "car|turn_costs=true").
                putObject("routing.ch.disabling_allowed", true).
                putObject("prepare.min_network_size", 0).
                putObject("prepare.min_one_way_network_size", 0).
                putObject("datareader.file", "../core/files/moscow.osm.gz").
                putObject("graph.encoded_values", "road_class,surface,road_environment,max_speed").
                putObject("graph.location", DIR)
                .setProfiles(Arrays.asList(
                        new ProfileConfig("my_car_turn_costs").setVehicle("car").setWeighting("fastest").setTurnCosts(true),
                        new ProfileConfig("my_car_no_turn_costs").setVehicle("car").setWeighting("fastest").setTurnCosts(false)
                ))
                .setCHProfiles(Arrays.asList(
                        new CHProfileConfig("my_car_turn_costs"),
                        new CHProfileConfig("my_car_no_turn_costs")
                ))
                .setLMProfiles(Arrays.asList(
                        new LMProfileConfig("my_car_turn_costs"),
                        new LMProfileConfig("my_car_no_turn_costs")
                ));
        return config;
    }

    @BeforeAll
    @AfterAll
    public static void cleanUp() {
        Helper.removeDir(new File(DIR));
    }

    @ParameterizedTest
    @ValueSource(strings = {"flex", "LM", "CH"})
    public void canToggleTurnCostsOnOff(String mode) {
        assertDistance(mode, null, null, 1044);
        assertDistance(mode, true, null, 1044);
        assertDistance(mode, null, true, 1044);
        assertDistance(mode, false, null, 400);
        assertDistance(mode, null, false, 400);
    }

    private void assertDistance(String mode, Boolean edgeBased, Boolean turnCosts, double expectedDistance) {
        assertDistanceGet(mode, edgeBased, turnCosts, expectedDistance);
        assertDistancePost(mode, edgeBased, turnCosts, expectedDistance);
    }

    private void assertDistanceGet(String mode, Boolean edgeBased, Boolean turnCosts, double expectedDistance) {
        String urlParams = "point=55.813357,37.5958585&point=55.811042,37.594689";
        if (mode.equals("LM"))
            urlParams += "&ch.disable=true";
        if (mode.equals("flex"))
            urlParams += "&ch.disable=true&lm.disable=true";
        if (edgeBased != null)
            urlParams += "&edge_based=" + edgeBased;
        if (turnCosts != null)
            urlParams += "&turn_costs=" + turnCosts;
        final Response response = clientTarget(app, "/route?" + urlParams).request().buildGet().invoke();
        assertDistance(response, expectedDistance);
    }

    private void assertDistancePost(String mode, Boolean edgeBased, Boolean turnCosts, double expectedDistance) {
        String jsonStr = "{";
        jsonStr += "\"points\": [[37.5958585,55.813357],[37.594689,55.811042]]";
        if (mode.equals("LM"))
            jsonStr += ", \"ch.disable\": true";
        if (mode.equals("flex"))
            jsonStr += ", \"ch.disable\": true, \"lm.disable\": true";
        if (edgeBased != null)
            jsonStr += ", \"edge_based\": " + edgeBased;
        if (turnCosts != null)
            jsonStr += ", \"turn_costs\": " + turnCosts;
        jsonStr += "}";
        final Response response = clientTarget(app, "/route?").request().post(Entity.json(jsonStr));
        assertDistance(response, expectedDistance);
    }

    private void assertDistance(Response response, double expectedDistance) {
        JsonNode json = response.readEntity(JsonNode.class);
        assertEquals(200, response.getStatus(), json.asText());
        JsonNode infoJson = json.get("info");
        assertFalse(infoJson.has("errors"));
        JsonNode path = json.get("paths").get(0);
        double distance = path.get("distance").asDouble();
        assertEquals(expectedDistance, distance, 1);
    }
}
