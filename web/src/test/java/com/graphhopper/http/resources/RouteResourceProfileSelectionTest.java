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
public class RouteResourceProfileSelectionTest {
    // todonow: make sure we pick everything we need from master here (discarded it all during merge)
    private static final String DIR = "./target/route-resource-profile-selection-gh/";
    private DropwizardAppExtension<GraphHopperServerTestConfiguration> app = new DropwizardAppExtension(GraphHopperApplication.class, createConfig());

    private static GraphHopperServerTestConfiguration createConfig() {
        GraphHopperServerTestConfiguration config = new GraphHopperServerTestConfiguration();
        config.getGraphHopperConfiguration().
                putObject("graph.flag_encoders", "car,bike,foot").
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
    @ValueSource(strings = {
            "",
            "&ch.disable=true",
            "&ch.disable=true&lm.disable=true"})
    public void selectUsingProfile(String hints) {
        String pointsStr = "point=43.727879,7.409678&point=43.745987,7.429848";
        assertDistanceGet(pointsStr + "&profile=my_car" + hints, 3563);
        assertDistanceGet(pointsStr + "&profile=my_bike" + hints, 3085);
        assertDistanceGet(pointsStr + "&profile=my_feet" + hints, 2935);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "&ch.disable=true"})
    public void selectUsingWeighting(String hints) {
        // for prepared algos the weighting is enough to select a profile
        String pointsStr = "point=43.727879,7.409678&point=43.745987,7.429848";
        assertDistanceGet(pointsStr + "&weighting=fastest" + hints, 3563);
        assertDistanceGet(pointsStr + "&weighting=short_fastest" + hints, 3085);
        assertDistanceGet(pointsStr + "&weighting=shortest" + hints, 2935);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "&ch.disable=true",
            "&ch.disable=true&lm.disable=true"})
    public void selectUsingWeightingNonPrepared(String hints) {
        String pointsStr = "point=43.727879,7.409678&point=43.745987,7.429848";
        // if we do not specify the vehicle it will be the the default vehicle (the first one encoder)
        assertDistanceGet(pointsStr + "&weighting=fastest" + hints, 3563);
        assertDistanceGet(pointsStr + "&vehicle=car&weighting=fastest" + hints, 3563);
        assertDistanceGet(pointsStr + "&vehicle=bike&weighting=short_fastest" + hints, 3085);
        assertDistanceGet(pointsStr + "&vehicle=foot&weighting=shortest" + hints, 2935);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "&ch.disable=true"})
    // note that this does not work for non-prepared algos, because in this case the weighting is always 'fastest'
    public void selectUsingVehicle(String hints) {
        String pointsStr = "point=43.727879,7.409678&point=43.745987,7.429848";
        assertDistanceGet(pointsStr + "&vehicle=car" + hints, 3563);
        assertDistanceGet(pointsStr + "&vehicle=bike" + hints, 3085);
        assertDistanceGet(pointsStr + "&vehicle=foot" + hints, 2935);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "&ch.disable=true",
            "&ch.disable=true&lm.disable=true"})
    public void selectUsingVehicleNonPrepared(String hints) {
        String pointsStr = "point=43.727879,7.409678&point=43.745987,7.429848";
        // if the weighting is fastest we can skip it, because its the default, otherwise we have to pass it along
        assertDistanceGet(pointsStr + "&vehicle=car" + hints, 3563);
        assertDistanceGet(pointsStr + "&vehicle=car&weighting=fastest" + hints, 3563);
        assertDistanceGet(pointsStr + "&vehicle=bike&weighting=short_fastest" + hints, 3085);
        assertDistanceGet(pointsStr + "&vehicle=foot&weighting=shortest" + hints, 2935);
    }

    private void assertDistanceGet(String urlParams, double expectedDistance) {
        final Response response = clientTarget(app, "/route?" + urlParams).request().buildGet().invoke();
        assertEquals(200, response.getStatus());
        JsonNode json = response.readEntity(JsonNode.class);
        JsonNode infoJson = json.get("info");
        assertFalse(infoJson.has("errors"));
        JsonNode path = json.get("paths").get(0);
        double distance = path.get("distance").asDouble();
        assertEquals(expectedDistance, distance, 10);
    }
}
