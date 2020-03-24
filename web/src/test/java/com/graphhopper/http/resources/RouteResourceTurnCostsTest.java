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

import javax.ws.rs.core.Response;
import java.io.File;
import java.util.Arrays;

import static com.graphhopper.http.util.TestUtils.clientTarget;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

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
    @ValueSource(strings = {
            "",
            "&ch.disable=true",
            "&ch.disable=true&lm.disable=true"})
    public void getQuery_canToggleTurnCostsOnOff(String hints) {
        String pointsStr = "point=55.813357,37.5958585&point=55.811042,37.594689";

        assertDistanceGet(pointsStr + hints, 1044);
        assertDistanceGet(pointsStr + "&edge_based=true" + hints, 1044);
        assertDistanceGet(pointsStr + "&turn_costs=true" + hints, 1044);
        assertDistanceGet(pointsStr + "&edge_based=false" + hints, 400);
        assertDistanceGet(pointsStr + "&turn_costs=false" + hints, 400);
    }

    private void assertDistanceGet(String urlParams, double expectedDistance) {
        final Response response = clientTarget(app, "/route?" + urlParams).request().buildGet().invoke();
        assertEquals(200, response.getStatus());
        JsonNode json = response.readEntity(JsonNode.class);
        JsonNode infoJson = json.get("info");
        assertFalse(infoJson.has("errors"));
        JsonNode path = json.get("paths").get(0);
        double distance = path.get("distance").asDouble();
        assertEquals(expectedDistance, distance, 1);
    }
}
