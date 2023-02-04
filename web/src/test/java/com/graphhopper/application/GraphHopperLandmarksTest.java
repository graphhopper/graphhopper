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
package com.graphhopper.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.graphhopper.application.util.GraphHopperServerTestConfiguration;
import com.graphhopper.config.CHProfile;
import com.graphhopper.config.LMProfile;
import com.graphhopper.config.Profile;
import com.graphhopper.core.util.Helper;
import io.dropwizard.testing.junit5.DropwizardAppExtension;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import javax.ws.rs.core.Response;
import java.io.File;
import java.util.Collections;

import static com.graphhopper.application.util.TestUtils.clientTarget;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the creation of Landmarks and parsing the map.geo.json file
 *
 * @author Robin Boldt
 */
@ExtendWith(DropwizardExtensionsSupport.class)
public class GraphHopperLandmarksTest {
    private static final String DIR = "./target/landmark-test-gh/";
    private static final DropwizardAppExtension<GraphHopperServerConfiguration> app = new DropwizardAppExtension<>(GraphHopperApplication.class, createConfig());

    private static GraphHopperServerConfiguration createConfig() {
        GraphHopperServerConfiguration config = new GraphHopperServerTestConfiguration();
        config.getGraphHopperConfiguration().
                putObject("graph.vehicles", "car").
                putObject("datareader.file", "../core/files/belarus-east.osm.gz").
                putObject("prepare.min_network_size", 0).
                putObject("import.osm.ignored_highways", "").
                putObject("graph.location", DIR)
                // force landmark creation even for tiny networks
                .putObject("prepare.lm.min_network_size", 2)
                .setProfiles(Collections.singletonList(
                        new Profile("car_profile").setVehicle("car").setWeighting("fastest")
                ))
                .setCHProfiles(Collections.singletonList(
                        new CHProfile("car_profile")
                ))
                .setLMProfiles(Collections.singletonList(
                        new LMProfile("car_profile")
                ));
        return config;
    }

    @BeforeAll
    @AfterAll
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
        assertEquals(1870, distance, 100, "distance wasn't correct:" + distance);

        response = clientTarget(app, "/route?profile=car_profile&" +
                "point=55.99022,29.129734&point=56.001069,29.150848&ch.disable=true").request().buildGet().invoke();
        json = response.readEntity(JsonNode.class);
        distance = json.get("paths").get(0).get("distance").asDouble();
        assertEquals(1870, distance, 100, "distance wasn't correct:" + distance);
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
        assertEquals(5790, distance, 100, "distance wasn't correct:" + distance);
    }
}