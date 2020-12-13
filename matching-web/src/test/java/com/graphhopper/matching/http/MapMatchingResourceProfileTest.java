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
package com.graphhopper.matching.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.graphhopper.config.CHProfile;
import com.graphhopper.config.LMProfile;
import com.graphhopper.config.Profile;
import com.graphhopper.http.WebHelper;
import com.graphhopper.util.Helper;
import com.graphhopper.util.Parameters;
import io.dropwizard.testing.junit.DropwizardAppRule;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Response;
import java.io.File;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.*;

/**
 * @author easbar
 */
public class MapMatchingResourceProfileTest {

    private static final String DIR = "../target/mapmatchingtest";

    private static MapMatchingServerConfiguration createConfig() {
        MapMatchingServerConfiguration config = new MapMatchingServerConfiguration();
        config.getGraphHopperConfiguration().
                putObject("graph.flag_encoders", "car|turn_costs=true,bike").
                putObject("datareader.file", "../map-data/leipzig_germany.osm.pbf").
                putObject("graph.location", DIR).
                putObject(Parameters.CH.INIT_DISABLING_ALLOWED, true).
                putObject(Parameters.Landmark.INIT_DISABLING_ALLOWED, true).
                setProfiles(Arrays.asList(
                        new Profile("car").setVehicle("car").setWeighting("fastest").setTurnCosts(true),
                        new Profile("car_no_tc").setVehicle("car").setWeighting("fastest"),
                        new Profile("bike").setVehicle("bike").setWeighting("fastest"))
                ).
                setLMProfiles(Arrays.asList(
                        new LMProfile("car"),
                        new LMProfile("bike"),
                        new LMProfile("car_no_tc").setPreparationProfile("car")
                )).
                setCHProfiles(Collections.singletonList(
                        new CHProfile("car_no_tc")
                ));
        return config;
    }

    @ClassRule
    public static final DropwizardAppRule<MapMatchingServerConfiguration> app = new DropwizardAppRule<>(MapMatchingApplication.class, createConfig());

    @BeforeClass
    @AfterClass
    public static void cleanUp() {
        Helper.removeDir(new File(DIR));
    }

    @Test
    public void useVehicle() {
        // see map-matching/#178
        runCar("vehicle=car");
        runBike("vehicle=bike");
    }

    @Test
    public void useProfile() {
        runCar("profile=car");
        runBike("profile=bike");
        runCar("profile=car_no_tc");
    }

    @Test
    public void disableCHLM() {
        runCar("vehicle=car&lm.disable=true");
        runCar("vehicle=car&ch.disable=true");
        runBike("vehicle=bike&lm.disable=true");
        runBike("vehicle=bike&ch.disable=true");
    }

    @Test
    public void errorOnUnknownProfile() {
        final Response response = app.client().target("http://localhost:8080/match?profile=xyz")
                .request()
                .buildPost(Entity.xml(getClass().getResourceAsStream("tour2-with-loop.gpx")))
                .invoke();
        JsonNode json = response.readEntity(JsonNode.class);
        assertTrue(json.toString(), json.has("message"));
        assertEquals(400, response.getStatus());
        assertTrue(json.toString().contains("Could not find profile 'xyz', choose one of: [car, car_no_tc, bike]"));
    }

    private void runCar(String urlParams) {
        final Response response = app.client().target("http://localhost:8080/match?" + urlParams)
                .request()
                .buildPost(Entity.xml(getClass().getResourceAsStream("tour2-with-loop.gpx")))
                .invoke();
        JsonNode json = response.readEntity(JsonNode.class);
        assertFalse(json.toString(), json.has("message"));
        assertEquals(200, response.getStatus());
        JsonNode path = json.get("paths").get(0);

        assertEquals(5, path.get("instructions").size());
        assertEquals(5, WebHelper.decodePolyline(path.get("points").asText(), 10, false).size());
        assertEquals(106.15, path.get("time").asLong() / 1000f, 0.1);
        assertEquals(106.15, json.get("map_matching").get("time").asLong() / 1000f, 0.1);
        assertEquals(811.56, path.get("distance").asDouble(), 1);
        assertEquals(811.56, json.get("map_matching").get("distance").asDouble(), 1);
    }

    private void runBike(String urlParams) {
        final Response response = app.client().target("http://localhost:8080/match?" + urlParams)
                .request()
                .buildPost(Entity.xml(getClass().getResourceAsStream("tour2-with-loop.gpx")))
                .invoke();
        JsonNode json = response.readEntity(JsonNode.class);
        assertFalse(json.toString(), json.has("message"));
        assertEquals(200, response.getStatus());
        JsonNode path = json.get("paths").get(0);

        assertEquals(5, path.get("instructions").size());
        assertEquals(5, WebHelper.decodePolyline(path.get("points").asText(), 10, false).size());
        assertEquals(162.31, path.get("time").asLong() / 1000f, 0.1);
        assertEquals(162.31, json.get("map_matching").get("time").asLong() / 1000f, 0.1);
        assertEquals(811.56, path.get("distance").asDouble(), 1);
        assertEquals(811.56, json.get("map_matching").get("distance").asDouble(), 1);
    }

}
