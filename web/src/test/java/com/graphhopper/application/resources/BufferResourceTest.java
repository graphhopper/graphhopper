package com.graphhopper.application.resources;

import static com.graphhopper.application.util.TestUtils.clientTarget;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.Arrays;

import javax.ws.rs.core.Response;

import com.fasterxml.jackson.databind.JsonNode;
import com.graphhopper.application.GraphHopperApplication;
import com.graphhopper.application.GraphHopperServerConfiguration;
import com.graphhopper.application.util.GraphHopperServerTestConfiguration;
import com.graphhopper.routing.TestProfiles;
import com.graphhopper.util.Helper;
import com.graphhopper.util.JsonFeatureCollection;
import com.graphhopper.util.TurnCostsConfig;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.geom.Geometry;

import io.dropwizard.testing.junit5.DropwizardAppExtension;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;

@ExtendWith(DropwizardExtensionsSupport.class)
public class BufferResourceTest {
    private static final String DIR = "./target/andorra-gh/";
    public static final DropwizardAppExtension<GraphHopperServerConfiguration> app = new DropwizardAppExtension<>(GraphHopperApplication.class, createConfig());

    private static GraphHopperServerConfiguration createConfig() {
        GraphHopperServerConfiguration config = new GraphHopperServerTestConfiguration();
        config.getGraphHopperConfiguration()
                .putObject("datareader.file", "../core/files/andorra.osm.pbf")
                .putObject("import.osm.ignored_highways", "")
                .putObject("graph.location", DIR)
                .putObject("graph.encoded_values", "car_access, car_average_speed")
                .setProfiles(Arrays.asList(
                        TestProfiles.accessAndSpeed("fast_car", "car").setTurnCostsConfig(TurnCostsConfig.car()),
                        TestProfiles.constantSpeed("short_car", 35).setTurnCostsConfig(TurnCostsConfig.car()),
                        TestProfiles.accessAndSpeed("fast_car_no_turn_restrictions", "car")));
        return config;
    }

    @BeforeAll
    @AfterAll
    public static void cleanUp() {
        Helper.removeDir(new File(DIR));
    }

    @Test
    public void testBasicBidirectionalStartQuery() {
        JsonFeatureCollection featureCollection;
        try (Response response = clientTarget(app, "/buffer?profile=my_car&"
                + "point=42.54287,1.471&roadName=CG-4&thresholdDistance=2000").request()
                .buildGet().invoke()) {
            assertEquals(200, response.getStatus());

            featureCollection = response.readEntity(JsonFeatureCollection.class);
        }

        assertEquals(2, featureCollection.getFeatures().size());
        Geometry lineString0 = featureCollection.getFeatures().get(0).getGeometry();
        Geometry lineString1 = featureCollection.getFeatures().get(1).getGeometry();

        // Identical start points
        assertEquals(lineString0.getCoordinates()[0], lineString1.getCoordinates()[0]);

        // Different end points
        assertNotEquals(lineString0.getCoordinates()[lineString0.getCoordinates().length - 1],
                lineString1.getCoordinates()[lineString1.getCoordinates().length - 1]);
    }

    @Test
    public void testBasicUnidirectionalStartQuery() {
        JsonFeatureCollection featureCollection;
        try (Response response = clientTarget(app, "/buffer?profile=my_car&"
                + "point=42.57839,1.631823&roadName=CG-2&thresholdDistance=2000").request()
                .buildGet().invoke()) {
            assertEquals(200, response.getStatus());

            featureCollection = response.readEntity(JsonFeatureCollection.class);
        }

        assertEquals(2, featureCollection.getFeatures().size());
        Geometry lineString0 = featureCollection.getFeatures().get(0).getGeometry();
        Geometry lineString1 = featureCollection.getFeatures().get(1).getGeometry();

        // Different start points
        assertNotEquals(lineString0.getCoordinates()[0], lineString1.getCoordinates()[0]);
        // Arbitrary - different endpoints
        assertNotEquals(lineString0.getCoordinates()[lineString0.getCoordinates().length - 1],
                lineString1.getCoordinates()[lineString1.getCoordinates().length - 1]);
    }

    @Test
    public void testInvalidRoadName() {
        JsonNode json;
        try (Response response = clientTarget(app, "/buffer?profile=my_car&"
                + "point=42.57839,1.631823&roadName=INVALID&thresholdDistance=2000")
                .request().buildGet().invoke()) {
            assertEquals(500, response.getStatus());
            json = response.readEntity(JsonNode.class);
        }
        assertTrue(json.has("message"), "There should have been an error response");
        String expected = "Could not find road with that name near the selection.";
        assertTrue(json.get("message").asText().contains(expected),
                "There should be an error containing " + expected + ", but got: "
                        + json.get("message"));
    }

    @Test
    public void testLargerThresholdDistance() {
        JsonFeatureCollection longFeatureCollection;
        try (Response longerResponse = clientTarget(app, "/buffer?profile=my_car&"
                + "point=42.54287,1.471&roadName=CG-4&thresholdDistance=4000").request()
                .buildGet().invoke()) {
            assertEquals(200, longerResponse.getStatus());

            longFeatureCollection = longerResponse.readEntity(JsonFeatureCollection.class);
        }

        JsonFeatureCollection shortFeatureCollection;
        try (Response shorterResponse = clientTarget(app, "/buffer?profile=my_car&"
                + "point=42.54287,1.471&roadName=CG-4&thresholdDistance=2000").request()
                .buildGet().invoke()) {
            assertEquals(200, shorterResponse.getStatus());
            shortFeatureCollection = shorterResponse.readEntity(JsonFeatureCollection.class);
        }

        assertEquals(2, longFeatureCollection.getFeatures().size());
        assertEquals(2, shortFeatureCollection.getFeatures().size());
        Geometry longLineString0 = longFeatureCollection.getFeatures().get(0).getGeometry();
        Geometry longLineString1 = longFeatureCollection.getFeatures().get(1).getGeometry();
        Geometry shortLineString0 = shortFeatureCollection.getFeatures().get(0).getGeometry();
        Geometry shortLineString1 = shortFeatureCollection.getFeatures().get(1).getGeometry();

        // Identical end points
        assertEquals(longLineString0.getCoordinates()[0], longLineString1.getCoordinates()[0]);
        assertEquals(longLineString0.getCoordinates()[0], shortLineString0.getCoordinates()[0]);
        assertEquals(longLineString0.getCoordinates()[0], shortLineString1.getCoordinates()[0]);

        // Different start points
        assertNotEquals(longLineString0.getCoordinates()[longLineString0.getCoordinates().length - 1],
                longLineString1.getCoordinates()[longLineString1.getCoordinates().length - 1]);
        assertNotEquals(longLineString0.getCoordinates()[longLineString0.getCoordinates().length - 1],
                shortLineString0.getCoordinates()[shortLineString0.getCoordinates().length - 1]);
        assertNotEquals(longLineString0.getCoordinates()[longLineString0.getCoordinates().length - 1],
                shortLineString1.getCoordinates()[shortLineString1.getCoordinates().length - 1]);
        assertNotEquals(longLineString1.getCoordinates()[longLineString1.getCoordinates().length - 1],
                shortLineString0.getCoordinates()[shortLineString0.getCoordinates().length - 1]);
        assertNotEquals(longLineString1.getCoordinates()[longLineString1.getCoordinates().length - 1],
                shortLineString1.getCoordinates()[shortLineString1.getCoordinates().length - 1]);
        assertNotEquals(shortLineString0.getCoordinates()[shortLineString0.getCoordinates().length - 1],
                shortLineString1.getCoordinates()[shortLineString1.getCoordinates().length - 1]);
    }

    @Test
    public void testCustomQueryMultiplier() {
        try (Response widerResponse = clientTarget(app, "/buffer?profile=my_car&"
                + "point=42.600114,1.45046&roadName=CG-4&thresholdDistance=2000&queryMultiplier=.2")
                .request().buildGet().invoke()) {
            assertEquals(200, widerResponse.getStatus());
        }

        JsonNode thinnerJson;
        try (Response thinnerResponse = clientTarget(app, "/buffer?profile=my_car&"
                + "point=42.600114,1.45046&roadName=CG-4&thresholdDistance=2000").request()
                .buildGet().invoke()) {
            assertEquals(500, thinnerResponse.getStatus());
            thinnerJson = thinnerResponse.readEntity(JsonNode.class);
        }

        assertTrue(thinnerJson.has("message"), "There should have been an error response");
        String expected = "Could not find road with that name near the selection.";
        assertTrue(thinnerJson.get("message").asText().contains(expected),
                "There should be an error containing " + expected + ", but got: "
                        + thinnerJson.get("message"));
    }

    @Test
    public void testBidirectionalUpstreamPath() {
        JsonFeatureCollection featureCollection;
        try (Response response = clientTarget(app, "/buffer?profile=my_car&"
                + "point=42.54287,1.471&roadName=CG-4&thresholdDistance=2000&buildUpstream=true")
                .request().buildGet().invoke()) {
            assertEquals(200, response.getStatus());

            featureCollection = response.readEntity(JsonFeatureCollection.class);
        }

        assertEquals(2, featureCollection.getFeatures().size());
        Geometry lineString0 = featureCollection.getFeatures().get(0).getGeometry();
        Geometry lineString1 = featureCollection.getFeatures().get(1).getGeometry();

        // Identical start points (reversed index)
        assertEquals(lineString0.getCoordinates()[lineString0.getCoordinates().length - 1],
                lineString1.getCoordinates()[lineString1.getCoordinates().length - 1]);

        // Different endpoints (reversed index)
        assertNotEquals(lineString0.getCoordinates()[0], lineString1.getCoordinates()[0]);
    }

    @Test
    public void testDownstreamAndUpstreamPathAreIdenticalOnPurelyBidirectionalRoad() {
        JsonFeatureCollection downstreamFeatureCollection;
        try (Response downstreamResponse = clientTarget(app, "/buffer?profile=my_car&"
                + "point=42.54287,1.471&roadName=CG-4&thresholdDistance=2000").request()
                .buildGet().invoke()) {
            assertEquals(200, downstreamResponse.getStatus());
            downstreamFeatureCollection = downstreamResponse.readEntity(JsonFeatureCollection.class);
        }

        JsonFeatureCollection upstreamFeatureCollection;
        try (Response upstreamResponse = clientTarget(app, "/buffer?profile=my_car&"
                + "point=42.54287,1.471&roadName=CG-4&thresholdDistance=2000&buildUpstream=true")
                .request().buildGet().invoke()) {
            assertEquals(200, upstreamResponse.getStatus());

            upstreamFeatureCollection = upstreamResponse.readEntity(JsonFeatureCollection.class);
        }

        assertEquals(2, upstreamFeatureCollection.getFeatures().size());
        assertEquals(2, downstreamFeatureCollection.getFeatures().size());
        Geometry upstreamLineString0 = upstreamFeatureCollection.getFeatures().get(0).getGeometry();
        Geometry upstreamLineString1 = upstreamFeatureCollection.getFeatures().get(1).getGeometry();
        Geometry downstreamLineString0 = downstreamFeatureCollection.getFeatures().get(0).getGeometry();
        Geometry downstreamLineString1 = downstreamFeatureCollection.getFeatures().get(1).getGeometry();

        // Since bidirectional road has an arbitrary flow, downstream-start and
        // upstream-end should match
        assertEquals(upstreamLineString1.getCoordinates()[upstreamLineString1.getCoordinates().length - 1],
                downstreamLineString1.getCoordinates()[0]);
        assertEquals(upstreamLineString0.getCoordinates()[upstreamLineString0.getCoordinates().length - 1],
                downstreamLineString0.getCoordinates()[0]);

        // And downstream-end and upstream-start should match
        assertEquals(upstreamLineString0.getCoordinates()[0],
                downstreamLineString0.getCoordinates()[downstreamLineString0.getCoordinates().length - 1]);
        assertEquals(upstreamLineString1.getCoordinates()[0],
                downstreamLineString1.getCoordinates()[downstreamLineString1.getCoordinates().length - 1]);

        // And origin points should match internally
        assertEquals(upstreamLineString0.getCoordinates()[upstreamLineString0.getCoordinates().length - 1],
                upstreamLineString1.getCoordinates()[upstreamLineString1.getCoordinates().length - 1]);
        assertEquals(downstreamLineString0.getCoordinates()[0], downstreamLineString1.getCoordinates()[0]);
    }

    @Test
    public void testDownstreamAndUpstreamPathAreDifferentWithUnidirectionalStarts() {
        JsonFeatureCollection downstreamFeatureCollection;
        try (Response downstreamResponse = clientTarget(app, "/buffer?profile=my_car&"
                + "point=42.57839,1.631823&roadName=CG-2&thresholdDistance=2000").request()
                .buildGet().invoke()) {
            assertEquals(200, downstreamResponse.getStatus());
            downstreamFeatureCollection = downstreamResponse.readEntity(JsonFeatureCollection.class);
        }

        JsonFeatureCollection upstreamFeatureCollection;
        try (Response upstreamResponse = clientTarget(app, "/buffer?profile=my_car&"
                + "point=42.57839,1.631823&roadName=CG-2&thresholdDistance=2000&buildUpstream=true")
                .request().buildGet().invoke()) {
            assertEquals(200, upstreamResponse.getStatus());

            upstreamFeatureCollection = upstreamResponse.readEntity(JsonFeatureCollection.class);
        }

        assertEquals(2, upstreamFeatureCollection.getFeatures().size());
        assertEquals(2, downstreamFeatureCollection.getFeatures().size());
        Geometry upstreamLineString0 = upstreamFeatureCollection.getFeatures().get(0).getGeometry();
        Geometry upstreamLineString1 = upstreamFeatureCollection.getFeatures().get(1).getGeometry();
        Geometry downstreamLineString0 = downstreamFeatureCollection.getFeatures().get(0).getGeometry();
        Geometry downstreamLineString1 = downstreamFeatureCollection.getFeatures().get(1).getGeometry();

        // Identical start points despite reversed direction
        assertEquals(upstreamLineString0.getCoordinates()[upstreamLineString0.getCoordinates().length - 1],
                downstreamLineString0.getCoordinates()[0]);
        assertEquals(upstreamLineString1.getCoordinates()[upstreamLineString1.getCoordinates().length - 1],
                downstreamLineString1.getCoordinates()[0]);

        // Different endpoints
        assertNotEquals(upstreamLineString0.getCoordinates()[0],
                downstreamLineString0.getCoordinates()[downstreamLineString0.getCoordinates().length - 1]);
        assertNotEquals(upstreamLineString1.getCoordinates()[0],
                downstreamLineString1.getCoordinates()[downstreamLineString1.getCoordinates().length - 1]);
    }

    @Test
    public void testBidirectionalStartQueryWithSmallerThresholdDistance() {
        JsonFeatureCollection featureCollection;
        try (Response response = clientTarget(app, "/buffer?profile=my_car&"
                + "point=42.54287,1.471&roadName=CG-4&thresholdDistance=100").request()
                .buildGet().invoke()) {
            assertEquals(200, response.getStatus());

            featureCollection = response.readEntity(JsonFeatureCollection.class);
        }

        assertEquals(2, featureCollection.getFeatures().size());
        Geometry lineString0 = featureCollection.getFeatures().get(0).getGeometry();
        Geometry lineString1 = featureCollection.getFeatures().get(1).getGeometry();

        // Identical start points
        assertEquals(lineString0.getCoordinates()[0], lineString1.getCoordinates()[0]);

        // Different end points
        assertNotEquals(lineString0.getCoordinates()[lineString0.getCoordinates().length - 1],
                lineString1.getCoordinates()[lineString1.getCoordinates().length - 1]);
    }

    @Test
    public void testBidirectionalStartQueryWithTooSmallThresholdDistance() {
        JsonNode json;
        try (Response response = clientTarget(app, "/buffer?profile=my_car&"
                + "point=42.54287,1.471&roadName=CG-4&thresholdDistance=50").request()
                .buildGet().invoke()) {

            assertEquals(500, response.getStatus());
            json = response.readEntity(JsonNode.class);
        }
        assertTrue(json.has("message"), "There should have been an error response");
        String expected = "Threshold distance is too short to construct a valid path.";
        assertTrue(json.get("message").asText().contains(expected),
                "There should be an error containing " + expected + ", but got: "
                        + json.get("message"));
    }

    @Test
    public void testUnusualRoadNameFormat() {
        try (Response response = clientTarget(app, "/buffer?profile=my_car&"
                + "point=42.54287,1.471&roadName=cG4-&thresholdDistance=2000").request()
                .buildGet().invoke()) {
            assertEquals(200, response.getStatus());
        }
    }
}
