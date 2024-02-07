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
import com.graphhopper.config.CHProfile;
import com.graphhopper.config.LMProfile;
import com.graphhopper.config.Profile;
import com.graphhopper.jackson.ResponsePathDeserializer;
import com.graphhopper.util.Helper;
import io.dropwizard.testing.junit5.DropwizardAppExtension;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.algorithm.distance.DiscreteHausdorffDistance;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Response;
import java.io.File;
import java.util.Arrays;
import java.util.Collections;

import static com.graphhopper.application.util.TestUtils.clientTarget;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author easbar
 */
@ExtendWith(DropwizardExtensionsSupport.class)
public class MapMatchingResourceTurnCostsTest {

    private static final String DIR = "../target/mapmatchingtest";
    public static final DropwizardAppExtension<GraphHopperServerConfiguration> app = new DropwizardAppExtension<>(GraphHopperApplication.class, createConfig());

    private static GraphHopperServerConfiguration createConfig() {
        GraphHopperServerConfiguration config = new GraphHopperServerConfiguration();
        config.getGraphHopperConfiguration().
                putObject("datareader.file", "../map-matching/files/leipzig_germany.osm.pbf").
                putObject("import.osm.ignored_highways", "").
                putObject("graph.location", DIR).
                setProfiles(Arrays.asList(
                        new Profile("car").setVehicle("car").setTurnCosts(true).setCustomModel(Helper.createBaseModel("car")),
                        new Profile("car_no_tc").setVehicle("car").setCustomModel(Helper.createBaseModel("car")),
                        new Profile("bike").setVehicle("bike").setCustomModel(Helper.createBaseModel("bike")))
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

    @BeforeAll
    @AfterAll
    public static void cleanUp() {
        Helper.removeDir(new File(DIR));
    }

    @Test
    public void useProfile() {
        runCar("profile=car");
        runBike("profile=bike");
        runCar("profile=car_no_tc");
    }

    @Test
    public void disableCHLM() {
        runCar("profile=car&lm.disable=true");
        runCar("profile=car&ch.disable=true");
        runBike("profile=bike&lm.disable=true");
        runBike("profile=bike&ch.disable=true");
    }

    @Test
    public void errorOnUnknownProfile() {
        final Response response = clientTarget(app, "/match?profile=xyz")
                .request()
                .buildPost(Entity.xml(getClass().getResourceAsStream("another-tour-with-loop.gpx")))
                .invoke();
        JsonNode json = response.readEntity(JsonNode.class);
        assertTrue(json.has("message"), json.toString());
        assertEquals(400, response.getStatus());
        assertTrue(json.toString().contains("The requested profile 'xyz' does not exist.\\nAvailable profiles: [car, car_no_tc, bike]"), json.toString());
    }

    private void runCar(String urlParams) {
        final Response response = clientTarget(app, "/match?" + urlParams)
                .request()
                .buildPost(Entity.xml(getClass().getResourceAsStream("another-tour-with-loop.gpx")))
                .invoke();
        JsonNode json = response.readEntity(JsonNode.class);
        assertFalse(json.has("message"), json.toString());
        assertEquals(200, response.getStatus());
        JsonNode path = json.get("paths").get(0);

        LineString expectedGeometry = readWktLineString("LINESTRING (12.3607 51.34365, 12.36418 51.34443, 12.36379 51.34538, 12.36082 51.34471, 12.36188 51.34278)");
        LineString actualGeometry = ResponsePathDeserializer.decodePolyline(path.get("points").asText(), 10, false).toLineString(false);
        assertEquals(DiscreteHausdorffDistance.distance(expectedGeometry, actualGeometry), 0.0, 1E-4);
        assertEquals(101, path.get("time").asLong() / 1000f, 1);
        assertEquals(101, json.get("map_matching").get("time").asLong() / 1000f, 1);
        assertEquals(812, path.get("distance").asDouble(), 1);
        assertEquals(812, json.get("map_matching").get("distance").asDouble(), 1);
    }

    private void runBike(String urlParams) {
        final Response response = clientTarget(app, "/match?" + urlParams)
                .request()
                .buildPost(Entity.xml(getClass().getResourceAsStream("another-tour-with-loop.gpx")))
                .invoke();
        JsonNode json = response.readEntity(JsonNode.class);
        assertFalse(json.has("message"), json.toString());
        assertEquals(200, response.getStatus());
        JsonNode path = json.get("paths").get(0);

        LineString expectedGeometry = readWktLineString("LINESTRING (12.3607 51.34365, 12.36418 51.34443, 12.36379 51.34538, 12.36082 51.34471, 12.36188 51.34278)");
        LineString actualGeometry = ResponsePathDeserializer.decodePolyline(path.get("points").asText(), 10, false).toLineString(false);
        assertEquals(DiscreteHausdorffDistance.distance(expectedGeometry, actualGeometry), 0.0, 1E-4);
        assertEquals(162.31, path.get("time").asLong() / 1000f, 0.1);
        assertEquals(162.31, json.get("map_matching").get("time").asLong() / 1000f, 0.1);
        assertEquals(811.56, path.get("distance").asDouble(), 1);
        assertEquals(811.56, json.get("map_matching").get("distance").asDouble(), 1);
    }

    private LineString readWktLineString(String wkt) {
        WKTReader wktReader = new WKTReader();
        LineString expectedGeometry = null;
        try {
            expectedGeometry = (LineString) wktReader.read(wkt);
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return expectedGeometry;
    }

}
