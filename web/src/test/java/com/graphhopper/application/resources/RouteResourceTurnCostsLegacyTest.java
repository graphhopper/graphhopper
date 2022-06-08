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
import com.graphhopper.config.LMProfile;
import com.graphhopper.config.Profile;
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
import java.util.List;

import static com.graphhopper.application.util.TestUtils.clientTarget;
import static java.util.Collections.emptyList;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(DropwizardExtensionsSupport.class)
public class RouteResourceTurnCostsLegacyTest {
    private static final String DIR = "./target/route-resource-turn-costs-legacy-gh/";
    private static final DropwizardAppExtension<GraphHopperServerConfiguration> app = new DropwizardAppExtension<>(GraphHopperApplication.class, createConfig());

    private static GraphHopperServerConfiguration createConfig() {
        GraphHopperServerConfiguration config = new GraphHopperServerTestConfiguration();
        config.getGraphHopperConfiguration().
                putObject("graph.flag_encoders", "car|turn_costs=true").
                putObject("prepare.min_network_size", 0).
                putObject("datareader.file", "../core/files/moscow.osm.gz").
                putObject("graph.encoded_values", "road_class,surface,road_environment,max_speed").
                putObject("graph.location", DIR)
                .setProfiles(Arrays.asList(
                        new Profile("my_car_turn_costs").setVehicle("car").setWeighting("fastest").setTurnCosts(true),
                        new Profile("my_car_no_turn_costs").setVehicle("car").setWeighting("fastest").setTurnCosts(false)
                ))
                .setCHProfiles(Arrays.asList(
                        new CHProfile("my_car_turn_costs"),
                        new CHProfile("my_car_no_turn_costs")
                ))
                .setLMProfiles(Arrays.asList(
                        new LMProfile("my_car_no_turn_costs"),
                        // no need for a second LM preparation: we can just cross query here
                        new LMProfile("my_car_turn_costs").setPreparationProfile("my_car_no_turn_costs")
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
    public void canToggleTurnCostsOnOff_legacy(String mode) {
        // profile+legacy is not allowed
        assertError(mode, "my_car_turn_costs", true, null, emptyList(),
                "Since you are using the 'profile' parameter, do not use the 'edge_based' parameter. You used 'edge_based=true'");
        assertError(mode, "my_car_turn_costs", false, null, emptyList(),
                "Since you are using the 'profile' parameter, do not use the 'edge_based' parameter. You used 'edge_based=false'");
        assertError(mode, "my_car_turn_costs", null, true, emptyList(),
                "Since you are using the 'profile' parameter, do not use the 'turn_costs' parameter. You used 'turn_costs=true'");
        assertError(mode, "my_car_turn_costs", null, false, emptyList(),
                "Since you are using the 'profile' parameter, do not use the 'turn_costs' parameter. You used 'turn_costs=false");

        assertDistance(mode, null, null, emptyList(), 1044);
        assertDistance(mode, true, null, emptyList(), 1044);
        assertDistance(mode, null, true, emptyList(), 1044);
        assertDistance(mode, false, null, emptyList(), 400);
        assertDistance(mode, null, false, emptyList(), 400);
    }

    @ParameterizedTest
    @ValueSource(strings = {"flex", "LM", "CH"})
    public void curbsides_legacy(String mode) {
        assertDistance(mode, null, null, Arrays.asList("left", "left"), 1459);
        assertDistance(mode, true, null, Arrays.asList("left", "left"), 1459);
        assertDistance(mode, null, true, Arrays.asList("left", "left"), 1459);
        assertError(mode, null, false, null, Arrays.asList("left", "left"), "Disabling 'edge_based' when using 'curbside' is not allowed");
        assertError(mode, null, null, false, Arrays.asList("left", "left"), "Disabling 'turn_costs' when using 'curbside' is not allowed");
    }

    private void assertDistance(String mode, Boolean edgeBased, Boolean turnCosts, List<String> curbsides, double expectedDistance) {
        assertDistance(doGet(mode, null, edgeBased, turnCosts, curbsides), expectedDistance);
        assertDistance(doPost(mode, null, edgeBased, turnCosts, curbsides), expectedDistance);
    }

    private void assertError(String mode, String profile, Boolean edgeBased, Boolean turnCosts, List<String> curbsides, String... expectedErrors) {
        assertError(doGet(mode, profile, edgeBased, turnCosts, curbsides), expectedErrors);
        assertError(doPost(mode, profile, edgeBased, turnCosts, curbsides), expectedErrors);
    }

    private void assertDistance(Response response, double expectedDistance) {
        JsonNode json = response.readEntity(JsonNode.class);
        assertEquals(200, response.getStatus(), json.toString());
        JsonNode infoJson = json.get("info");
        assertFalse(infoJson.has("errors"));
        JsonNode path = json.get("paths").get(0);
        double distance = path.get("distance").asDouble();
        assertEquals(expectedDistance, distance, 1);
    }

    private void assertError(Response response, String... expectedErrors) {
        assert expectedErrors.length > 0;
        JsonNode json = response.readEntity(JsonNode.class);
        assertEquals(400, response.getStatus(), json.toString());
        for (String e : expectedErrors) {
            assertTrue(json.get("message").toString().contains(e), json.get("message").toString());
        }
    }

    private String getUrlParams(String mode, String profile, Boolean edgeBased, Boolean turnCosts, List<String> curbsides) {
        String urlParams = "point=55.813357,37.5958585&point=55.811042,37.594689";
        for (String curbside : curbsides)
            urlParams += "&curbside=" + curbside;
        if (mode.equals("LM"))
            urlParams += "&ch.disable=true";
        if (mode.equals("flex"))
            urlParams += "&ch.disable=true&lm.disable=true";
        if (edgeBased != null)
            urlParams += "&edge_based=" + edgeBased;
        if (turnCosts != null)
            urlParams += "&turn_costs=" + turnCosts;
        if (profile != null)
            urlParams += "&profile=" + profile;
        return urlParams;
    }

    private String getJsonStr(String mode, String profile, Boolean edgeBased, Boolean turnCosts, List<String> curbsides) {
        String jsonStr = "{";
        jsonStr += "\"points\": [[37.5958585,55.813357],[37.594689,55.811042]]";
        if (!curbsides.isEmpty()) {
            assert curbsides.size() == 2;
            jsonStr += ", \"curbsides\": [\"" + curbsides.get(0) + "\",\"" + curbsides.get(1) + "\"]";
        }
        if (mode.equals("LM"))
            jsonStr += ", \"ch.disable\": true";
        if (mode.equals("flex"))
            jsonStr += ", \"ch.disable\": true, \"lm.disable\": true";
        if (edgeBased != null)
            jsonStr += ", \"edge_based\": " + edgeBased;
        if (turnCosts != null)
            jsonStr += ", \"turn_costs\": " + turnCosts;
        if (profile != null)
            jsonStr += ", \"profile\": \"" + profile + "\"";
        jsonStr += "}";
        return jsonStr;
    }

    private Response doGet(String mode, String profile, Boolean edgeBased, Boolean turnCosts, List<String> curbsides) {
        return clientTarget(app, "/route?" + getUrlParams(mode, profile, edgeBased, turnCosts, curbsides)).request().buildGet().invoke();
    }

    private Response doPost(String mode, String profile, Boolean edgeBased, Boolean turnCosts, List<String> curbsides) {
        return clientTarget(app, "/route?").request().post(Entity.json(getJsonStr(mode, profile, edgeBased, turnCosts, curbsides)));
    }
}
