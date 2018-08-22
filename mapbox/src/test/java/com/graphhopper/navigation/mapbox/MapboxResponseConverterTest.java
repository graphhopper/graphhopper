package com.graphhopper.navigation.mapbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.reader.osm.GraphHopperOSM;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.util.Helper;
import com.graphhopper.util.TranslationMap;
import com.graphhopper.util.shapes.GHPoint;
import org.junit.*;

import java.io.File;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MapboxResponseConverterTest {

    public static final String DIR = "files";
    private static final String graphFileFoot = "target/graphhopperIT-car";
    private static final String osmFile = DIR + "/andorra.osm.gz";
    private static final String importVehicles = "car";
    private static GraphHopper hopper;
    private final String tmpGraphFile = "target/graphhopperIT-tmp";

    private final TranslationMap trMap = new TranslationMap().doImport();

    @BeforeClass
    public static void beforeClass() {
        // make sure we are using fresh graphhopper files with correct vehicle
        Helper.removeDir(new File(graphFileFoot));

        hopper = new GraphHopperOSM().
                setOSMFile(osmFile).
                setStoreOnFlush(true).
                setCHEnabled(false).
                setGraphHopperLocation(graphFileFoot).
                setEncodingManager(new EncodingManager(importVehicles)).
                importOrLoad();
    }

    @AfterClass
    public static void afterClass() {
        Helper.removeDir(new File(graphFileFoot));
    }

    @Before
    public void setUp() {
        Helper.removeDir(new File(tmpGraphFile));
    }

    @After
    public void tearDown() {
        Helper.removeDir(new File(tmpGraphFile));
    }

    @Test
    public void basicTest() {

        GHResponse rsp = hopper.route(new GHRequest(42.554851, 1.536198, 42.510071, 1.548128).
                setVehicle("car"));

        ObjectNode json = MapboxResponseConverter.convertFromGHResponse(rsp, trMap, Locale.ENGLISH);

        JsonNode route = json.get("routes").get(0);
        double routeDistance = route.get("distance").asDouble();
        assertTrue("distance wasn't correct:" + routeDistance, routeDistance > 9000);
        assertTrue("distance wasn't correct:" + routeDistance, routeDistance < 9500);

        double routeDuration = route.get("duration").asDouble();
        assertTrue("duration wasn't correct:" + routeDuration, routeDuration > 500);
        assertTrue("duration wasn't correct:" + routeDuration, routeDuration < 600);

        assertEquals("en", route.get("voiceLocale").asText());

        JsonNode leg = route.get("legs").get(0);
        assertEquals(routeDistance, leg.get("distance").asDouble(), .000001);

        JsonNode steps = leg.get("steps");
        JsonNode step = steps.get(0);
        JsonNode maneuver = step.get("maneuver");
        // Intersection coordinates should be equal to maneuver coordinates
        assertEquals(maneuver.get("location").get(0).asDouble(), step.get("intersections").get(0).get("location").get(0).asDouble(), .00001);

        assertEquals("depart", maneuver.get("type").asText());
        assertEquals("straight", maneuver.get("modifier").asText());

        assertEquals("la Callisa", step.get("name").asText());
        double instructionDistance = step.get("distance").asDouble();
        assertTrue(instructionDistance < routeDistance);

        JsonNode voiceInstructions = step.get("voiceInstructions");
        assertEquals(1, voiceInstructions.size());
        JsonNode voiceInstruction = voiceInstructions.get(0);
        assertTrue(voiceInstruction.get("distanceAlongGeometry").asDouble() <= instructionDistance);
        assertEquals("turn sharp left onto la Callisa", voiceInstruction.get("announcement").asText());

        JsonNode bannerInstructions = step.get("bannerInstructions");
        assertEquals(1, bannerInstructions.size());
        JsonNode bannerInstruction = bannerInstructions.get(0).get("primary");
        assertEquals("la Callisa", bannerInstruction.get("text").asText());
        assertEquals("turn", bannerInstruction.get("type").asText());
        assertEquals("sharp left", bannerInstruction.get("modifier").asText());
        JsonNode bannerInstructionComponent = bannerInstruction.get("components").get(0);
        assertEquals("la Callisa", bannerInstructionComponent.get("text").asText());

        // Get the second last step (and the last banner/voice instruction)
        step = steps.get(steps.size() - 2);

        voiceInstructions = step.get("voiceInstructions");
        assertEquals(1, voiceInstructions.size());
        voiceInstruction = voiceInstructions.get(0);
        assertTrue(voiceInstruction.get("distanceAlongGeometry").asDouble() < instructionDistance);

        bannerInstructions = step.get("bannerInstructions");
        assertEquals(1, bannerInstructions.size());
        bannerInstruction = bannerInstructions.get(0).get("primary");
        assertEquals("arrive at destination", bannerInstruction.get("text").asText());

        JsonNode waypointsJson = json.get("waypoints");
        assertEquals(2, waypointsJson.size());
        JsonNode waypointLoc = waypointsJson.get(0).get("location");
        assertEquals(1.536198, waypointLoc.get(0).asDouble(), .001);

    }

    @Test
    public void testMultipleWaypoints() {

        GHRequest request = new GHRequest();
        request.addPoint(new GHPoint(42.504606, 1.522438));
        request.addPoint(new GHPoint(42.504776, 1.527209));
        request.addPoint(new GHPoint(42.505144, 1.526113));
        request.addPoint(new GHPoint(42.50529, 1.527218));
        request.setVehicle("car");

        GHResponse rsp = hopper.route(request);

        ObjectNode json = MapboxResponseConverter.convertFromGHResponse(rsp, trMap, Locale.ENGLISH);

        JsonNode waypointsJson = json.get("waypoints");
        assertEquals(4, waypointsJson.size());

        JsonNode waypointLoc = waypointsJson.get(0).get("location");
        assertEquals(1.522438, waypointLoc.get(0).asDouble(), .00001);

        waypointLoc = waypointsJson.get(1).get("location");
        assertEquals(1.527209, waypointLoc.get(0).asDouble(), .00001);

        waypointLoc = waypointsJson.get(2).get("location");
        assertEquals(1.526113, waypointLoc.get(0).asDouble(), .00001);

        waypointLoc = waypointsJson.get(3).get("location");
        assertEquals(1.527218, waypointLoc.get(0).asDouble(), .00001);
    }

    @Test
    public void testError() {
        GHResponse rsp = hopper.route(new GHRequest(42.554851, 111.536198, 42.510071, 1.548128).
                setVehicle("car"));

        ObjectNode json = MapboxResponseConverter.convertFromGHResponseError(rsp);

        assertEquals("InvalidInput", json.get("code").asText());
        assertEquals("Point 0 is out of bounds: 42.554851,111.536198", json.get("message").asText());
    }

}
