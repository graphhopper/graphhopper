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
package com.graphhopper.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.graphhopper.config.CHProfileConfig;
import com.graphhopper.config.LMProfileConfig;
import com.graphhopper.config.ProfileConfig;
import com.graphhopper.http.util.GraphHopperServerTestConfiguration;
import com.graphhopper.util.Helper;
import io.dropwizard.testing.junit.DropwizardAppRule;
import org.junit.AfterClass;
import org.junit.ClassRule;
import org.junit.Test;

import javax.ws.rs.core.Response;
import java.io.File;
import java.util.Collections;

import static com.graphhopper.http.util.TestUtils.clientTarget;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests the creation of Landmarks and parsing the map.geo.json file
 *
 * @author Robin Boldt
 */
public class GraphHopperLandmarksTest {
    private static final String DIR = "./target/landmark-test-gh/";

    private static final GraphHopperServerTestConfiguration config = new GraphHopperServerTestConfiguration();

    static {
        config.getGraphHopperConfiguration().
                putObject("graph.flag_encoders", "car").
                putObject("datareader.file", "../core/files/belarus-east.osm.gz").
                putObject("prepare.min_network_size", 0).
                putObject("prepare.min_one_way_network_size", 0).
                putObject("routing.ch.disabling_allowed", true).
                putObject("routing.lm.disabling_allowed", true).
                putObject("graph.location", DIR)
                // force landmark creation even for tiny networks
                .putObject("prepare.lm.min_network_size", 2)
                .setProfiles(Collections.singletonList(
                        new ProfileConfig("car_profile").setVehicle("car").setWeighting("fastest")
                ))
                .setCHProfiles(Collections.singletonList(
                        new CHProfileConfig("car_profile")
                ))
                .setLMProfiles(Collections.singletonList(
                        new LMProfileConfig("car_profile")
                ));
    }

    @ClassRule
    public static final DropwizardAppRule<GraphHopperServerTestConfiguration> app = new DropwizardAppRule(
            GraphHopperApplication.class, config);

    @AfterClass
    public static void cleanUp() {
        Helper.removeDir(new File(DIR));
    }

    @Test
    public void testQueries() {
        Response response = clientTarget(app, "/route?profile=car_profile&" +
                "point=55.99022,29.129734&point=56.001069,29.150848").request().buildGet().invoke();
        assertEquals(200, response.getStatus());
        JsonNode json = response.readEntity(JsonNode.class);
        JsonNode path = json.get("paths").get(0);
        double distance = path.get("distance").asDouble();
        assertEquals("distance wasn't correct:" + distance, 1870, distance, 100);

        response = clientTarget(app, "/route?profile=car_profile&" +
                "point=55.99022,29.129734&point=56.001069,29.150848&ch.disable=true").request().buildGet().invoke();
        json = response.readEntity(JsonNode.class);
        distance = json.get("paths").get(0).get("distance").asDouble();
        assertEquals("distance wasn't correct:" + distance, 1870, distance, 100);
    }

    @Test
    public void testLandmarkDisconnect() {
        // if one algorithm is disabled then the following chain is executed: CH -> LM -> flexible
        // disconnected for landmarks
        Response response = clientTarget(app, "/route?profile=car_profile&" +
                "point=55.99022,29.129734&point=56.007787,29.208355&ch.disable=true").request().buildGet().invoke();
        assertEquals(400, response.getStatus());
        JsonNode json = response.readEntity(JsonNode.class);
        assertTrue(json.get("message").toString().contains("Different subnetworks"));

        // without landmarks it should work
        response = clientTarget(app, "/route?profile=car_profile&" +
                "point=55.99022,29.129734&point=56.007787,29.208355&ch.disable=true&lm.disable=true").request().buildGet().invoke();
        assertEquals(200, response.getStatus());
        json = response.readEntity(JsonNode.class);
        double distance = json.get("paths").get(0).get("distance").asDouble();
        assertEquals("distance wasn't correct:" + distance, 5790, distance, 100);
    }
}