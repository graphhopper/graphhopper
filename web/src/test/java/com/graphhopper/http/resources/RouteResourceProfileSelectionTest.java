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
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(DropwizardExtensionsSupport.class)
public class RouteResourceProfileSelectionTest {
    private static final String DIR = "./target/route-resource-profile-selection-gh/";
    private DropwizardAppExtension<GraphHopperServerTestConfiguration> app = new DropwizardAppExtension(GraphHopperApplication.class, createConfig());

    private static GraphHopperServerTestConfiguration createConfig() {
        GraphHopperServerTestConfiguration config = new GraphHopperServerTestConfiguration();
        config.getGraphHopperConfiguration().
                putObject("graph.flag_encoders", "bike,car,foot").
                putObject("routing.ch.disabling_allowed", true).
                putObject("prepare.min_network_size", 0).
                putObject("prepare.min_one_way_network_size", 0).
                putObject("datareader.file", "../core/files/monaco.osm.gz").
                putObject("graph.encoded_values", "road_class,surface,road_environment,max_speed").
                putObject("graph.location", DIR)
                .setProfiles(Arrays.asList(
                        new ProfileConfig("my_car").setVehicle("car").setWeighting("fastest"),
                        new ProfileConfig("my_bike").setVehicle("bike").setWeighting("short_fastest"),
                        new ProfileConfig("my_feet").setVehicle("foot").setWeighting("shortest")
                ))
                .setCHProfiles(Arrays.asList(
                        new CHProfileConfig("my_car"),
                        new CHProfileConfig("my_bike"),
                        new CHProfileConfig("my_feet")
                ))
                .setLMProfiles(Arrays.asList(
                        new LMProfileConfig("my_car"),
                        new LMProfileConfig("my_bike"),
                        new LMProfileConfig("my_feet")
                ));
        return config;
    }

    @BeforeAll
    @AfterAll
    public static void cleanUp() {
        Helper.removeDir(new File(DIR));
    }

    @ParameterizedTest
    @ValueSource(strings = {"CH", "LM", "flex"})
    public void missingVehicleOrWeighting(String mode) {
        assertDistance("car", "fastest", mode, 3563);
        assertDistance("foot", "shortest", mode, 2935);
        assertDistance("bike", "short_fastest", mode, 3085);
        // we can also skip the vehicles, because the weighting is enough to distinguish a single profile
        assertDistance(null, "fastest", mode, 3563);
        assertDistance(null, "shortest", mode, 2935);
        assertDistance(null, "short_fastest", mode, 3085);
        // the same goes for the weighting
        assertDistance("car", null, mode, 3563);
        assertDistance("foot", null, mode, 2935);
        assertDistance("bike", null, mode, 3085);
        // not giving any information does not work however, because all three profiles match
        assertError((String) null, null, mode, "multiple", "profiles matching your request");

    }

    private void assertDistance(String vehicle, String weighting, String mode, double expectedDistance) {
        assertDistance(doGet(vehicle, weighting, mode), expectedDistance);
        assertDistance(doPost(vehicle, weighting, mode), expectedDistance);
    }

    private void assertError(String vehicle, String weighting, String mode, String... expectedErrors) {
        assertError(doGet(vehicle, weighting, mode), expectedErrors);
        assertError(doPost(vehicle, weighting, mode), expectedErrors);
    }

    private Response doGet(String vehicle, String weighting, String mode) {
        String urlParams = "point=43.727879,7.409678&point=43.745987,7.429848";
        if (vehicle != null)
            urlParams += "&vehicle=" + vehicle;
        if (weighting != null)
            urlParams += "&weighting=" + weighting;
        if (mode.equals("LM") || mode.equals("flex"))
            urlParams += "&ch.disable=true";
        if (mode.equals("flex"))
            urlParams += "&lm.disable=true";
        return clientTarget(app, "/route?" + urlParams).request().buildGet().invoke();
    }

    private Response doPost(String vehicle, String weighting, String mode) {
        String jsonStr = "{\"points\": [[7.409678,43.727879], [7.429848, 43.745987]]";
        if (vehicle != null)
            jsonStr += ",\"vehicle\": \"" + vehicle + "\"";
        if (weighting != null)
            jsonStr += ",\"weighting\": \"" + weighting + "\"";
        if (mode.equals("LM") || mode.equals("flex"))
            jsonStr += ",\"ch.disable\": true";
        if (mode.equals("flex"))
            jsonStr += ",\"lm.disable\": true";
        jsonStr += " }";
        return clientTarget(app, "/route").request().post(Entity.json(jsonStr));
    }

    private void assertDistance(Response response, double expectedDistance) {
        JsonNode json = response.readEntity(JsonNode.class);
        assertEquals(200, response.getStatus(), (json.has("message") ? json.get("message").toString() : ""));
        JsonNode infoJson = json.get("info");
        assertFalse(infoJson.has("errors"));
        JsonNode path = json.get("paths").get(0);
        double distance = path.get("distance").asDouble();
        assertEquals(expectedDistance, distance, 10);
    }

    private void assertError(Response response, String... expectedErrors) {
        if (expectedErrors.length == 0)
            throw new IllegalArgumentException("there should be at least one expected error");
        JsonNode json = response.readEntity(JsonNode.class);
        assertEquals(400, response.getStatus(), "there should have been an error containing: " + Arrays.toString(expectedErrors));
        assertTrue(json.has("message"));
        for (String expectedError : expectedErrors)
            assertTrue(json.get("message").toString().contains(expectedError), json.get("message").toString());
    }
}
