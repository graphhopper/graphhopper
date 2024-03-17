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
import com.graphhopper.config.Profile;
import com.graphhopper.jackson.ResponsePathDeserializerHelper;
import com.graphhopper.util.Helper;
import io.dropwizard.testing.junit5.DropwizardAppExtension;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import org.junit.jupiter.api.AfterAll;
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

import static com.graphhopper.application.util.TestUtils.clientTarget;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
                putObject("graph.vehicles", "car,bike").
                putObject("datareader.file", "../map-matching/files/leipzig_germany.osm.pbf").
                putObject("import.osm.ignored_highways", "").
                putObject("graph.location", DIR).
                setProfiles(Arrays.asList(
                        new Profile("fast_car").setVehicle("car"),
                        new Profile("fast_bike").setVehicle("bike")));
        return config;
    }

    @AfterAll
    public static void cleanUp() {
        Helper.removeDir(new File(DIR));
    }

    @Test
    public void testGPX() {
        final Response response = clientTarget(app, "/match?profile=fast_car")
                .request()
                .buildPost(Entity.xml(getClass().getResourceAsStream("/tour2-with-loop.gpx")))
                .invoke();
        assertEquals(200, response.getStatus());
        JsonNode json = response.readEntity(JsonNode.class);
        JsonNode path = json.get("paths").get(0);

        LineString expectedGeometry = readWktLineString("LINESTRING (12.3607 51.34365, 12.36418 51.34443, 12.36379 51.34538, 12.36082 51.34471, 12.36188 51.34278)");
        LineString actualGeometry = ResponsePathDeserializerHelper.decodePolyline(path.get("points").asText(), 10, false, 1e5).toLineString(false);
        assertEquals(DiscreteHausdorffDistance.distance(expectedGeometry, actualGeometry), 0.0, 1E-4);
        assertEquals(101, path.get("time").asLong() / 1000f, 1);
        assertEquals(101, json.get("map_matching").get("time").asLong() / 1000f, 1);
        assertEquals(812, path.get("distance").asDouble(), 1);
        assertEquals(812, json.get("map_matching").get("distance").asDouble(), 1);
    }

    @Test
    public void testBike() throws ParseException {
        WKTReader wktReader = new WKTReader();
        final Response response = clientTarget(app, "/match?profile=fast_bike")
                .request()
                .buildPost(Entity.xml(getClass().getResourceAsStream("another-tour-with-loop.gpx")))
                .invoke();
        assertEquals(200, response.getStatus(), "no success");
        JsonNode json = response.readEntity(JsonNode.class);
        JsonNode path = json.get("paths").get(0);

        LineString expectedGeometry = (LineString) wktReader.read("LINESTRING (12.3607 51.34365, 12.36418 51.34443, 12.36379 51.34538, 12.36082 51.34471, 12.36188 51.34278)");
        LineString actualGeometry = ResponsePathDeserializerHelper.decodePolyline(path.get("points").asText(), 10, false, 1e5).toLineString(false);
        assertEquals(DiscreteHausdorffDistance.distance(expectedGeometry, actualGeometry), 0.0, 1E-4);

        // ensure that is actually also is bike! (slower than car)
        assertEquals(162, path.get("time").asLong() / 1000f, 1);
    }

    @Test
    public void testGPX10() {
        final Response response = clientTarget(app, "/match?profile=fast_car")
                .request()
                .buildPost(Entity.xml(getClass().getResourceAsStream("gpxv1_0.gpx")))
                .invoke();
        assertEquals(200, response.getStatus());
    }

    @Test
    public void testEmptyGPX() {
        final Response response = clientTarget(app, "/match?profile=fast_car")
                .request()
                .buildPost(Entity.xml(getClass().getResourceAsStream("test-only-wpt.gpx")))
                .invoke();
        assertEquals(400, response.getStatus());
        JsonNode json = response.readEntity(JsonNode.class);
        JsonNode message = json.get("message");
        assertTrue(message.isValueNode());
        assertTrue(message.asText().startsWith("No tracks found"));
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
