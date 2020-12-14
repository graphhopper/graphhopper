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
import com.graphhopper.config.Profile;
import com.graphhopper.http.GraphHopperApplication;
import com.graphhopper.http.GraphHopperServerConfiguration;
import com.graphhopper.http.WebHelper;
import com.graphhopper.util.Helper;
import io.dropwizard.testing.junit5.DropwizardAppExtension;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Response;
import java.io.File;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Peter Karich
 */
@ExtendWith(DropwizardExtensionsSupport.class)
public class MapMatchingResourceTest {

    private static final String DIR = "../target/mapmatchingtest";
    public static final DropwizardAppExtension<GraphHopperServerConfiguration> app = new DropwizardAppExtension<>(GraphHopperApplication.class, createConfig());

    private static GraphHopperServerConfiguration createConfig() {
        GraphHopperServerConfiguration config = new GraphHopperServerConfiguration();
        config.getGraphHopperConfiguration().
                putObject("graph.flag_encoders", "car").
                putObject("datareader.file", "../map-data/leipzig_germany.osm.pbf").
                putObject("graph.location", DIR).
                setProfiles(Collections.singletonList(new Profile("profile").setVehicle("car").setWeighting("fastest")));
        return config;
    }

    @AfterAll
    public static void cleanUp() {
        Helper.removeDir(new File(DIR));
    }

    @Test
    public void testGPX() {
        final Response response = app.client().target("http://localhost:8080/match")
                .request()
                .buildPost(Entity.xml(getClass().getResourceAsStream("/tour2-with-loop.gpx")))
                .invoke();
        assertEquals(200, response.getStatus());
        JsonNode json = response.readEntity(JsonNode.class);
        JsonNode path = json.get("paths").get(0);

        assertEquals(5, path.get("instructions").size());
        assertEquals(5, WebHelper.decodePolyline(path.get("points").asText(), 10, false).size());
        assertEquals(106.15, path.get("time").asLong() / 1000f, 0.1);
        assertEquals(106.15, json.get("map_matching").get("time").asLong() / 1000f, 0.1);
        assertEquals(811.56, path.get("distance").asDouble(), 1);
        assertEquals(811.56, json.get("map_matching").get("distance").asDouble(), 1);
    }

    @Test
    public void testGPX10() {
        final Response response = app.client().target("http://localhost:8080/match")
                .request()
                .buildPost(Entity.xml(getClass().getResourceAsStream("gpxv1_0.gpx")))
                .invoke();
        assertEquals(200, response.getStatus());
    }

    @Test
    public void testEmptyGPX() {
        final Response response = app.client().target("http://localhost:8080/match")
                .request()
                .buildPost(Entity.xml(getClass().getResourceAsStream("test-only-wpt.gpx")))
                .invoke();
        assertEquals(400, response.getStatus());
        JsonNode json = response.readEntity(JsonNode.class);
        JsonNode message = json.get("message");
        assertTrue(message.isValueNode());
        assertTrue(message.asText().startsWith("No tracks found"));
    }
}
