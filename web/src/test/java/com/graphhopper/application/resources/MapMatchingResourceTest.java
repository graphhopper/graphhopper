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
import com.graphhopper.jackson.ResponsePathDeserializerHelper;
import com.graphhopper.routing.TestProfiles;
import com.graphhopper.util.Helper;
import io.dropwizard.testing.junit5.DropwizardAppExtension;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import jakarta.ws.rs.core.Form;
import jakarta.ws.rs.core.MediaType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.algorithm.distance.DiscreteHausdorffDistance;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;

import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.Response;
import java.io.File;
import java.util.Arrays;
import java.util.Base64;

import static com.graphhopper.application.util.TestUtils.clientTarget;
import static com.graphhopper.resources.MapMatchingResource.readWkbLineString;
import static com.graphhopper.resources.MapMatchingResource.readWktLineString;
import static org.junit.jupiter.api.Assertions.*;

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
                putObject("datareader.file", "../map-matching/files/leipzig_germany.osm.pbf").
                putObject("import.osm.ignored_highways", "").
                putObject("graph.location", DIR).
                putObject("graph.encoded_values", "car_access, car_average_speed, bike_access, bike_priority, bike_average_speed").
                setProfiles(Arrays.asList(
                        TestProfiles.accessAndSpeed("fast_car", "car"),
                        TestProfiles.accessSpeedAndPriority("fast_bike", "bike")));
        return config;
    }

    @AfterAll
    public static void cleanUp() {
        Helper.removeDir(new File(DIR));
    }

    @Test
    public void testGPX() {
        JsonNode json = clientTarget(app, "/match?profile=fast_car")
                .request()
                .post(Entity.xml(getClass().getResourceAsStream("/tour2-with-loop.gpx")), JsonNode.class);
        JsonNode path = json.get("paths").get(0);

        LineString expectedGeometry = readWktLineString("LINESTRING (12.3607 51.34365, 12.36418 51.34443, 12.36379 51.34538, 12.36082 51.34471, 12.36188 51.34278)");
        LineString actualGeometry = ResponsePathDeserializerHelper.decodePolyline(path.get("points").asText(), 10, false, 1e5).toLineString(false);
        assertEquals(0.0, DiscreteHausdorffDistance.distance(expectedGeometry, actualGeometry), 1E-4);
        assertEquals(101, path.get("time").asLong() / 1000f, 1);
        assertEquals(101, json.get("map_matching").get("time").asLong() / 1000f, 1);
        assertEquals(812, path.get("distance").asDouble(), 1);
        assertEquals(812, json.get("map_matching").get("distance").asDouble(), 1);
    }

    @Test
    public void testPolyline5() {
        String polyline = "y`kxHkemjA{CwT}DlAdCpQ`KsE";
        JsonNode json = clientTarget(app, "/match/polyline?profile=fast_car&polyline_multiplier=1e5")
                .request()
                .post(Entity.form(new Form("polyline", polyline)), JsonNode.class);
        JsonNode path = json.get("paths").get(0);

        LineString expectedGeometry = readWktLineString("LINESTRING (12.3607 51.34365, 12.36418 51.34443, 12.36379 51.34538, 12.36082 51.34471, 12.36188 51.34278)");
        LineString actualGeometry = ResponsePathDeserializerHelper.decodePolyline(path.get("points").asText(), 10, false, 1e5).toLineString(false);
        assertEquals(0.0, DiscreteHausdorffDistance.distance(expectedGeometry, actualGeometry), 1E-4);
        assertEquals(101, path.get("time").asLong() / 1000f, 1);
        assertEquals(101, json.get("map_matching").get("time").asLong() / 1000f, 1);
        assertEquals(812, path.get("distance").asDouble(), 1);
        assertEquals(812, json.get("map_matching").get("distance").asDouble(), 1);
    }

    @Test
    public void testPolyline6() {
        String polyline = "cqw|`Bw~lqVwo@oxEkz@jWzh@rxDrwBgaA";
        JsonNode json = clientTarget(app, "/match/polyline?profile=fast_car&polyline_multiplier=1e6")
                .request()
                .post(Entity.form(new Form("polyline", polyline)), JsonNode.class);
        JsonNode path = json.get("paths").get(0);

        LineString expectedGeometry = readWktLineString("LINESTRING (12.3607 51.34365, 12.36418 51.34443, 12.36379 51.34538, 12.36082 51.34471, 12.36188 51.34278)");
        LineString actualGeometry = ResponsePathDeserializerHelper.decodePolyline(path.get("points").asText(), 10, false, 1e5).toLineString(false);
        assertEquals(0.0, DiscreteHausdorffDistance.distance(expectedGeometry, actualGeometry), 1E-4);
        assertEquals(101, path.get("time").asLong() / 1000f, 1);
        assertEquals(101, json.get("map_matching").get("time").asLong() / 1000f, 1);
        assertEquals(812, path.get("distance").asDouble(), 1);
        assertEquals(812, json.get("map_matching").get("distance").asDouble(), 1);
    }

    @Test
    public void testWkt() {
        String wktLinestring = "LINESTRING (12.3607 51.34365, 12.36418 51.34443, 12.36379 51.34538, 12.36082 51.34471, 12.36188 51.34278)";
        JsonNode json = clientTarget(app, "/match/wkt?profile=fast_car")
                .request()
                .post(Entity.form(new Form("wkt", wktLinestring)), JsonNode.class);
        JsonNode path = json.get("paths").get(0);

        LineString expectedGeometry = readWktLineString(wktLinestring);
        LineString actualGeometry = ResponsePathDeserializerHelper.decodePolyline(path.get("points").asText(), 10, false, 1e5).toLineString(false);
        assertEquals(0.0, DiscreteHausdorffDistance.distance(expectedGeometry, actualGeometry), 1E-4);
        assertEquals(101, path.get("time").asLong() / 1000f, 1);
        assertEquals(101, json.get("map_matching").get("time").asLong() / 1000f, 1);
        assertEquals(812, path.get("distance").asDouble(), 1);
        assertEquals(812, json.get("map_matching").get("distance").asDouble(), 1);
    }

    @Test
    public void testWkb() {
        //String wkbHex = "0102000000050000009b559fabadb828409ca223b9fcab4940edb60bcd75ba284072e1404816ac49404339d1ae42ba2840a3586e6935ac4940467c2766bdb82840554d10751fac49402827da5548b9284087dc0c37e0ab4940";
        String wkbBase64 = "AQIAAAAFAAAAm1Wfq624KECcoiO5/KtJQO22C811uihAcuFASBasSUBDOdGuQrooQKNYbmk1rElARnwnZr24KEBVTRB1H6xJQCgn2lVIuShAh9wMN+CrSUA=";

        byte[] wkbBytesFromBase64 = Base64.getDecoder().decode(wkbBase64);

        JsonNode json = clientTarget(app, "/match/wkb?profile=fast_car")
                .request()
                .post(Entity.entity(wkbBytesFromBase64, MediaType.APPLICATION_OCTET_STREAM), JsonNode.class);
        JsonNode path = json.get("paths").get(0);

        LineString expectedGeometry = readWkbLineString(wkbBytesFromBase64);
        LineString actualGeometry = ResponsePathDeserializerHelper.decodePolyline(path.get("points").asText(), 10, false, 1e5).toLineString(false);
        assertEquals(0.0, DiscreteHausdorffDistance.distance(expectedGeometry, actualGeometry), 1E-4);
        assertEquals(101, path.get("time").asLong() / 1000f, 1);
        assertEquals(101, json.get("map_matching").get("time").asLong() / 1000f, 1);
        assertEquals(812, path.get("distance").asDouble(), 1);
        assertEquals(812, json.get("map_matching").get("distance").asDouble(), 1);
    }

    @Test
    public void testBike() throws ParseException {
        WKTReader wktReader = new WKTReader();
        final JsonNode json = clientTarget(app, "/match?profile=fast_bike")
                .request()
                .post(Entity.xml(getClass().getResourceAsStream("another-tour-with-loop.gpx")), JsonNode.class);
        JsonNode path = json.get("paths").get(0);

        LineString expectedGeometry = (LineString) wktReader.read("LINESTRING (12.3607 51.34365, 12.36418 51.34443, 12.36379 51.34538, 12.36082 51.34471, 12.36188 51.34278)");
        LineString actualGeometry = ResponsePathDeserializerHelper.decodePolyline(path.get("points").asText(), 10, false, 1e5).toLineString(false);
        assertEquals(0.0, DiscreteHausdorffDistance.distance(expectedGeometry, actualGeometry), 1E-4);

        // ensure that is actually also is bike! (slower than car)
        assertEquals(162, path.get("time").asLong() / 1000f, 1);
    }

    @Test
    public void testGPX10() {
        JsonNode json = clientTarget(app, "/match?profile=fast_car")
                .request()
                .post(Entity.xml(getClass().getResourceAsStream("gpxv1_0.gpx")), JsonNode.class);
        assertFalse(json.get("paths").isEmpty());
    }

    @Test
    public void testEmptyGPX() {
        try (Response response = clientTarget(app, "/match?profile=fast_car")
                .request()
                .buildPost(Entity.xml(getClass().getResourceAsStream("test-only-wpt.gpx")))
                .invoke()) {
            assertEquals(400, response.getStatus());
            JsonNode json = response.readEntity(JsonNode.class);
            JsonNode message = json.get("message");
            assertTrue(message.isValueNode());
            assertTrue(message.asText().startsWith("No tracks found"));
        }
    }


}
