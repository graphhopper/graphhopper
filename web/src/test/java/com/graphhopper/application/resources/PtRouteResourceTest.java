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
import com.graphhopper.GHResponse;
import com.graphhopper.application.GraphHopperApplication;
import com.graphhopper.application.GraphHopperServerConfiguration;
import com.graphhopper.application.util.GraphHopperServerTestConfiguration;
import com.graphhopper.config.Profile;
import com.graphhopper.resources.InfoResource;
import com.graphhopper.util.Helper;
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
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the entire app, not the resource, so that the plugging-together
 * of stuff (which is different for PT than for the rest) is under test, too.
 */
@ExtendWith(DropwizardExtensionsSupport.class)
public class PtRouteResourceTest {
    private static final String DIR = "./target/gtfs-app-gh/";
    public static final DropwizardAppExtension<GraphHopperServerConfiguration> app = new DropwizardAppExtension<>(GraphHopperApplication.class, createConfig());

    private static GraphHopperServerConfiguration createConfig() {
        GraphHopperServerConfiguration config = new GraphHopperServerTestConfiguration();
        config.getGraphHopperConfiguration().
                putObject("datareader.file", "../reader-gtfs/files/beatty.osm").
                putObject("gtfs.file", "../reader-gtfs/files/sample-feed").
                putObject("graph.location", DIR).
                putObject("import.osm.ignored_highways", "").
                setProfiles(Collections.singletonList(new Profile("foot").setCustomModel(Helper.createBaseCustomModel("foot", true))));
        return config;
    }

    @BeforeAll
    @AfterAll
    public static void cleanUp() {
        Helper.removeDir(new File(DIR));
    }

    @Test
    public void testStationStationQuery() {
        final Response response = clientTarget(app, "/route")
                .queryParam("point", "Stop(NADAV)")
                .queryParam("point", "Stop(NANAA)")
                .queryParam("profile", "pt")
                .queryParam("pt.earliest_departure_time", "2007-01-01T15:44:00Z")
                .request().buildGet().invoke();
        assertEquals(200, response.getStatus());
        JsonNode jsonNode = response.readEntity(JsonNode.class);
        assertEquals(1, jsonNode.at("/paths/0/legs").size());
    }

    @Test
    public void testStationStationQueryWithOffsetDateTime() {
        final Response response = clientTarget(app, "/route")
                .queryParam("point", "Stop(NADAV)")
                .queryParam("point", "Stop(NANAA)")
                .queryParam("profile", "pt")
                .queryParam("pt.earliest_departure_time", "2007-01-01T07:44:00-08:00")
                .request().buildGet().invoke();
        assertEquals(200, response.getStatus());
        JsonNode jsonNode = response.readEntity(JsonNode.class);
        assertEquals(1, jsonNode.at("/paths/0/legs").size());
    }

    @Test
    public void testPointPointQuery() {
        final Response response = clientTarget(app, "/route")
                .queryParam("point", "36.914893,-116.76821") // NADAV stop
                .queryParam("point", "36.914944,-116.761472") //NANAA stop
                .queryParam("profile", "pt")
                .queryParam("pt.earliest_departure_time", "2007-01-01T15:44:00Z")
                .request().buildGet().invoke();
        assertEquals(200, response.getStatus());
        JsonNode jsonNode = response.readEntity(JsonNode.class);
        assertEquals(1, jsonNode.at("/paths/0/legs").size());
    }

    @Test
    public void testWalkQuery() {
        final Response response = clientTarget(app, "/route")
                .queryParam("point", "36.914893,-116.76821")
                .queryParam("point", "36.914944,-116.761472")
                .queryParam("profile", "foot")
                .request().buildGet().invoke();
        assertEquals(200, response.getStatus());
        GHResponse ghResponse = response.readEntity(GHResponse.class);
        assertFalse(ghResponse.hasErrors());
    }

    @Test
    public void testNoPoints() {
        final Response response = clientTarget(app, "/route")
                .queryParam("profile", "pt")
                .request().buildGet().invoke();
        assertEquals(400, response.getStatus());
    }

    @Test
    public void testOnePoint() {
        final Response response = clientTarget(app, "/route")
                .queryParam("point", "36.914893,-116.76821")
                .queryParam("profile", "pt")
                .queryParam("pt.earliest_departure_time", "2007-01-01T08:00:00Z")
                .request().buildGet().invoke();
        assertEquals(400, response.getStatus());
        JsonNode json = response.readEntity(JsonNode.class);
        assertEquals("query param point size must be between 2 and 2", json.get("message").asText());
    }

    @Test
    public void testBadPoints() {
        final Response response = clientTarget(app, "/route")
                .queryParam("point", "pups")
                .queryParam("profile", "pt")
                .queryParam("pt.earliest_departure_time", "2007-01-01T08:00:00Z")
                .request().buildGet().invoke();
        assertEquals(400, response.getStatus());
    }

    @Test
    public void testNoTime() {
        final Response response = clientTarget(app, "/route")
                .queryParam("point", "36.914893,-116.76821") // NADAV stop
                .queryParam("point", "36.914944,-116.761472") //NANAA stop
                .queryParam("profile", "pt")
                .request().buildGet().invoke();
        assertEquals(400, response.getStatus());
        JsonNode json = response.readEntity(JsonNode.class);
        // Would prefer a "must not be null" message here, but is currently the same as for a bad time (see below).
        // I DO NOT want to manually catch this, I want to figure out how to fix this upstream, or live with it.
        assertTrue(json.get("message").asText().startsWith("query param pt.earliest_departure_time must"));
    }

    @Test
    public void testBadTime() {
        final Response response = clientTarget(app, "/route")
                .queryParam("point", "36.914893,-116.76821") // NADAV stop
                .queryParam("point", "36.914944,-116.761472") //NANAA stop
                .queryParam("profile", "pt")
                .queryParam("pt.earliest_departure_time", "wurst")
                .request().buildGet().invoke();
        assertEquals(400, response.getStatus());
        JsonNode json = response.readEntity(JsonNode.class);
        assertEquals("query param pt.earliest_departure_time must be in a ISO-8601 format.", json.get("message").asText());
    }

    @Test
    public void testInfo() {
        final Response response = clientTarget(app, "/info")
                .request().buildGet().invoke();
        assertEquals(200, response.getStatus());
        InfoResource.Info info = response.readEntity(InfoResource.Info.class);
        assertTrue(info.profiles.stream().anyMatch(p -> p.name.equals("pt")));
    }

}
