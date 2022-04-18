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
import com.graphhopper.config.Profile;
import com.graphhopper.util.Helper;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.dropwizard.testing.junit5.DropwizardAppExtension;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;

@ExtendWith(DropwizardExtensionsSupport.class)
public class BufferResourceTest {
    private static final String DIR = "./target/andorra-gh/";
    public static final DropwizardAppExtension<GraphHopperServerConfiguration> app = new DropwizardAppExtension<>(
            GraphHopperApplication.class, createConfig());

    private static GraphHopperServerConfiguration createConfig() {
        GraphHopperServerConfiguration config = new GraphHopperServerTestConfiguration();
        config.getGraphHopperConfiguration().putObject("graph.flag_encoders", "car|turn_costs=true")
                .putObject("datareader.file", "../core/files/andorra.osm.pbf")
                .putObject("graph.location", DIR)
                .setProfiles(Arrays.asList(
                        new Profile("fast_car").setVehicle("car")
                                .setWeighting("fastest").setTurnCosts(true),
                        new Profile("short_car").setVehicle("car")
                                .setWeighting("shortest")
                                .setTurnCosts(true),
                        new Profile("fast_car_no_turn_restrictions")
                                .setVehicle("car").setWeighting("fastest")
                                .setTurnCosts(false)));
        return config;
    }

    @BeforeAll
    @AfterAll
    public static void cleanUp() {
        Helper.removeDir(new File(DIR));
    }

    @Test
    public void testBasicBidirectionalStartQuery() {
        final Response response = clientTarget(app, "/buffer?profile=my_car&"
                + "point=42.54287,1.471&roadName=CG-4&threshholdDistance=2000").request()
                .buildGet().invoke();
        assertEquals(200, response.getStatus());
        JsonNode json = response.readEntity(JsonNode.class);
        // Identical start points
        assertEquals(json.get("features").get(1).get("geometry").get("coordinates"),
                json.get("features").get(3).get("geometry").get("coordinates"));
        // Different endpoints
        assertNotEquals(json.get("features").get(0).get("geometry").get("coordinates"),
                json.get("features").get(2).get("geometry").get("coordinates"));
    }

    @Test
    public void testBasicUnidirectionalStartQuery() {
        final Response response = clientTarget(app, "/buffer?profile=my_car&"
                + "point=42.57839,1.631823&roadName=CG-2&threshholdDistance=2000").request()
                .buildGet().invoke();
        assertEquals(200, response.getStatus());
        JsonNode json = response.readEntity(JsonNode.class);
        // Different start points
        assertNotEquals(json.get("features").get(1).get("geometry").get("coordinates"),
                json.get("features").get(3).get("geometry").get("coordinates"));
        // Arbitrary - different endpoints
        assertNotEquals(json.get("features").get(0).get("geometry").get("coordinates"),
                json.get("features").get(2).get("geometry").get("coordinates"));
    }

    @Test
    public void testInvalidRoadName() {
        final Response response = clientTarget(app, "/buffer?profile=my_car&"
                + "point=42.57839,1.631823&roadName=INVALID&threshholdDistance=2000")
                .request().buildGet().invoke();
        assertEquals(500, response.getStatus());
        JsonNode json = response.readEntity(JsonNode.class);
        assertTrue(json.has("message"), "There should have been an error response");
        String expected = "Could not find road with that name near the selection.";
        assertTrue(json.get("message").asText().contains(expected),
                "There should be an error containing " + expected + ", but got: "
                        + json.get("message"));
    }

    @Test
    public void testLargerThreshholdDistance() {
        final Response longerResponse = clientTarget(app, "/buffer?profile=my_car&"
                + "point=42.54287,1.471&roadName=CG-4&threshholdDistance=4000").request()
                .buildGet().invoke();
        final Response shorterResponse = clientTarget(app, "/buffer?profile=my_car&"
                + "point=42.54287,1.471&roadName=CG-4&threshholdDistance=2000").request()
                .buildGet().invoke();
        assertEquals(200, longerResponse.getStatus());
        assertEquals(200, shorterResponse.getStatus());
        JsonNode longerJson = longerResponse.readEntity(JsonNode.class);
        JsonNode shorterJson = shorterResponse.readEntity(JsonNode.class);
        // Identical start points
        assertEquals(longerJson.get("features").get(1).get("geometry").get("coordinates"),
                longerJson.get("features").get(3).get("geometry").get("coordinates"));
        assertEquals(longerJson.get("features").get(1).get("geometry").get("coordinates"),
                shorterJson.get("features").get(1).get("geometry").get("coordinates"));
        assertEquals(longerJson.get("features").get(3).get("geometry").get("coordinates"),
                shorterJson.get("features").get(3).get("geometry").get("coordinates"));
        // Different endpoints
        assertNotEquals(longerJson.get("features").get(0).get("geometry").get("coordinates"),
                longerJson.get("features").get(2).get("geometry").get("coordinates"));
        assertNotEquals(longerJson.get("features").get(0).get("geometry").get("coordinates"),
                shorterJson.get("features").get(0).get("geometry").get("coordinates"));
        assertNotEquals(longerJson.get("features").get(0).get("geometry").get("coordinates"),
                shorterJson.get("features").get(2).get("geometry").get("coordinates"));
    }

    @Test
    public void testCustomQueryMultiplier() {
        final Response widerResponse = clientTarget(app, "/buffer?profile=my_car&"
                + "point=42.600114,1.45046&roadName=CG-4&threshholdDistance=2000&queryMultiplier=.2")
                .request().buildGet().invoke();
        final Response thinnerResponse = clientTarget(app, "/buffer?profile=my_car&"
                + "point=42.600114,1.45046&roadName=CG-4&threshholdDistance=2000").request()
                .buildGet().invoke();
        assertEquals(200, widerResponse.getStatus());
        assertEquals(500, thinnerResponse.getStatus());
        JsonNode thinnerJson = thinnerResponse.readEntity(JsonNode.class);
        assertTrue(thinnerJson.has("message"), "There should have been an error response");
        String expected = "Could not find road with that name near the selection.";
        assertTrue(thinnerJson.get("message").asText().contains(expected),
                "There should be an error containing " + expected + ", but got: "
                        + thinnerJson.get("message"));
    }

    @Test
    public void testBidirectionalUpstreamPath() {
        final Response response = clientTarget(app, "/buffer?profile=my_car&"
                + "point=42.54287,1.471&roadName=CG-4&threshholdDistance=2000&upstream=true")
                .request().buildGet().invoke();
        assertEquals(200, response.getStatus());
        JsonNode json = response.readEntity(JsonNode.class);
        // Identical start points
        assertEquals(json.get("features").get(1).get("geometry").get("coordinates"),
                json.get("features").get(3).get("geometry").get("coordinates"));
        // Different endpoints
        assertNotEquals(json.get("features").get(0).get("geometry").get("coordinates"),
                json.get("features").get(2).get("geometry").get("coordinates"));
    }

    @Test
    public void testDownstreamAndUpstreamPathAreIdenticalOnPurelyBidirectionalRoad() {
        final Response downstreamResponse = clientTarget(app, "/buffer?profile=my_car&"
                + "point=42.54287,1.471&roadName=CG-4&threshholdDistance=2000").request()
                .buildGet().invoke();
        final Response upstreamResponse = clientTarget(app, "/buffer?profile=my_car&"
                + "point=42.54287,1.471&roadName=CG-4&threshholdDistance=2000&upstream=true")
                .request().buildGet().invoke();
        assertEquals(200, downstreamResponse.getStatus());
        assertEquals(200, upstreamResponse.getStatus());
        JsonNode downstreamJson = downstreamResponse.readEntity(JsonNode.class);
        JsonNode upstreamJson = upstreamResponse.readEntity(JsonNode.class);
        // Since bidirectional road has an arbitrary flow, every aspect should
        // be identical
        assertEquals(downstreamJson.get("features").get(0).get("geometry").get("coordinates"),
                upstreamJson.get("features").get(0).get("geometry").get("coordinates"));
        assertEquals(downstreamJson.get("features").get(1).get("geometry").get("coordinates"),
                upstreamJson.get("features").get(1).get("geometry").get("coordinates"));
        assertEquals(downstreamJson.get("features").get(2).get("geometry").get("coordinates"),
                upstreamJson.get("features").get(2).get("geometry").get("coordinates"));
        assertEquals(downstreamJson.get("features").get(3).get("geometry").get("coordinates"),
                upstreamJson.get("features").get(3).get("geometry").get("coordinates"));
    }

    @Test
    public void testDownstreamAndUpstreamPathAreDifferentWithUnidirectionalStarts() {
        final Response downstreamResponse = clientTarget(app, "/buffer?profile=my_car&"
                + "point=42.57839,1.631823&roadName=CG-2&threshholdDistance=2000").request()
                .buildGet().invoke();
        final Response upstreamResponse = clientTarget(app, "/buffer?profile=my_car&"
                + "point=42.57839,1.631823&roadName=CG-2&threshholdDistance=2000&upstream=true")
                .request().buildGet().invoke();
        assertEquals(200, downstreamResponse.getStatus());
        assertEquals(200, upstreamResponse.getStatus());
        JsonNode downstreamJson = downstreamResponse.readEntity(JsonNode.class);
        JsonNode upstreamJson = upstreamResponse.readEntity(JsonNode.class);
        // Identical startpoints
        assertEquals(downstreamJson.get("features").get(1).get("geometry").get("coordinates"),
                upstreamJson.get("features").get(1).get("geometry").get("coordinates"));
        assertEquals(downstreamJson.get("features").get(3).get("geometry").get("coordinates"),
                upstreamJson.get("features").get(3).get("geometry").get("coordinates"));
        // Different endpoints
        assertNotEquals(downstreamJson.get("features").get(0).get("geometry").get("coordinates"),
                upstreamJson.get("features").get(0).get("geometry").get("coordinates"));
        assertNotEquals(downstreamJson.get("features").get(2).get("geometry").get("coordinates"),
                upstreamJson.get("features").get(2).get("geometry").get("coordinates"));
    }

    @Test
    public void testUnusualRoadNameFormat() {
        final Response response = clientTarget(app, "/buffer?profile=my_car&"
                + "point=42.54287,1.471&roadName=cG4-&threshholdDistance=2000").request()
                .buildGet().invoke();
        assertEquals(200, response.getStatus());
    }

    // Roundabout test? Unnamed roundabout test?
    // More failed tests?
}
