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
import com.graphhopper.config.Profile;
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
 * @author Peter Karich
 */
@ExtendWith(DropwizardExtensionsSupport.class)
public class RouteResourceWithEleTest {
    private static final String dir = "./target/monaco-gh/";
    private static final DropwizardAppExtension<GraphHopperServerConfiguration> app = new DropwizardAppExtension<>(GraphHopperApplication.class, createConfig());

    private static GraphHopperServerConfiguration createConfig() {
        GraphHopperServerConfiguration config = new GraphHopperServerTestConfiguration();
        config.getGraphHopperConfiguration().
                putObject("graph.elevation.provider", "srtm").
                putObject("graph.elevation.cache_dir", "../core/files/").
                putObject("prepare.min_network_size", 0).
                putObject("graph.vehicles", "car").
                putObject("datareader.file", "../core/files/monaco.osm.gz").
                putObject("graph.location", dir).
                putObject("import.osm.ignored_highways", "").
                setProfiles(Collections.singletonList(
                        new Profile("profile").setVehicle("car")
                ));
        return config;
    }

    @BeforeAll
    @AfterAll
    public static void cleanUp() {
        Helper.removeDir(new File(dir));
    }

    @Test
    public void testElevation() {
        final Response response = clientTarget(app, "/route?profile=profile&" +
                "point=43.730864,7.420771&point=43.727687,7.418737&points_encoded=false&elevation=true").request().buildGet().invoke();
        assertEquals(200, response.getStatus());
        JsonNode json = response.readEntity(JsonNode.class);
        JsonNode infoJson = json.get("info");
        assertFalse(infoJson.has("errors"));
        JsonNode path = json.get("paths").get(0);
        double distance = path.get("distance").asDouble();
        assertTrue(distance > 2500, "distance wasn't correct:" + distance);
        assertTrue(distance < 2700, "distance wasn't correct:" + distance);

        JsonNode cson = path.get("points");
        assertTrue(cson.toString().contains("[7.421392,43.7307,66.0]"), "no elevation?");

        // Although we include elevation DO NOT include it in the bbox as bbox.toGeoJSON messes up when reading
        // or reading with and without elevation would be too complex for the client with no real use
        assertEquals(4, path.get("bbox").size());
    }

    @Test
    public void testNoElevation() {
        // default is elevation=false
        Response response = clientTarget(app, "/route?profile=profile&" +
                "point=43.730864,7.420771&point=43.727687,7.418737&points_encoded=false").request().buildGet().invoke();
        assertEquals(200, response.getStatus());
        JsonNode json = response.readEntity(JsonNode.class);
        JsonNode infoJson = json.get("info");
        assertFalse(infoJson.has("errors"));
        JsonNode path = json.get("paths").get(0);
        double distance = path.get("distance").asDouble();
        assertTrue(distance > 2500, "distance wasn't correct:" + distance);
        assertTrue(distance < 2700, "distance wasn't correct:" + distance);
        JsonNode cson = path.get("points");
        assertTrue(cson.toString().contains("[7.421392,43.7307]"), "Elevation should not be included!");

        // disable elevation
        response = clientTarget(app, "/route?profile=profile&" +
                "point=43.730864,7.420771&point=43.727687,7.418737&points_encoded=false&elevation=false").request().buildGet().invoke();
        assertEquals(200, response.getStatus());
        json = response.readEntity(JsonNode.class);
        infoJson = json.get("info");
        assertFalse(infoJson.has("errors"));
        path = json.get("paths").get(0);
        cson = path.get("points");
        assertTrue(cson.toString().contains("[7.421392,43.7307]"), "Elevation should not be included!");
    }
}
