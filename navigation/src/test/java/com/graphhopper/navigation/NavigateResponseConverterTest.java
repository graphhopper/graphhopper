package com.graphhopper.navigation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.jackson.ResponsePathSerializer;
import com.graphhopper.routing.TestProfiles;
import com.graphhopper.util.Helper;
import com.graphhopper.util.Parameters;
import com.graphhopper.util.PointList;
import com.graphhopper.util.TranslationMap;
import com.graphhopper.util.shapes.GHPoint;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Collections;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

public class NavigateResponseConverterTest {

    private static final String graphFolder = "target/graphhopper-test-car";
    private static final String osmFile = "../core/files/andorra.osm.gz";
    private static GraphHopper hopper;
    private static final String profile = "my_car";

    private final TranslationMap trMap = hopper.getTranslationMap();
    private final DistanceConfig distanceConfig = new DistanceConfig(DistanceUtils.Unit.METRIC, trMap, Locale.ENGLISH);

    @BeforeAll
    public static void beforeClass() {
        // make sure we are using fresh files with correct vehicle
        Helper.removeDir(new File(graphFolder));

        hopper = new GraphHopper().
                setOSMFile(osmFile).
                setStoreOnFlush(true).
                setGraphHopperLocation(graphFolder).
                setEncodedValuesString("car_access, car_average_speed").
                setProfiles(TestProfiles.accessAndSpeed(profile, "car")).
                importOrLoad();
    }

    @AfterAll
    public static void afterClass() {
        Helper.removeDir(new File(graphFolder));
    }

    @Test
    public void basicTest() {

        GHResponse rsp = hopper.route(new GHRequest(42.554851, 1.536198, 42.510071, 1.548128).setProfile(profile));

        ObjectNode json = NavigateResponseConverter.convertFromGHResponse(rsp, trMap, Locale.ENGLISH, distanceConfig);

        JsonNode route = json.get("routes").get(0);
        double routeDistance = route.get("distance").asDouble();
        assertTrue(routeDistance > 9000, "distance wasn't correct:" + routeDistance);
        assertTrue(routeDistance < 9500, "distance wasn't correct:" + routeDistance);

        double routeDuration = route.get("duration").asDouble();
        assertTrue(routeDuration > 500, "duration wasn't correct:" + routeDuration);
        assertTrue(routeDuration < 600, "duration wasn't correct:" + routeDuration);

        assertEquals("en", route.get("voiceLocale").asText());

        JsonNode leg = route.get("legs").get(0);
        assertEquals(routeDistance, leg.get("distance").asDouble(), .000001);

        JsonNode steps = leg.get("steps");
        JsonNode step = steps.get(0);
        JsonNode maneuver = step.get("maneuver");
        // Intersection coordinates should be equal to maneuver coordinates
        assertEquals(maneuver.get("location").get(0).asDouble(),
                step.get("intersections").get(0).get("location").get(0).asDouble(), .00001);

        assertEquals("depart", maneuver.get("type").asText());
        assertEquals("straight", maneuver.get("modifier").asText());

        assertEquals("la Callisa", step.get("name").asText());
        double instructionDistance = step.get("distance").asDouble();
        assertTrue(instructionDistance < routeDistance);

        JsonNode voiceInstructions = step.get("voiceInstructions");
        assertEquals(1, voiceInstructions.size());
        JsonNode voiceInstruction = voiceInstructions.get(0);
        assertTrue(voiceInstruction.get("distanceAlongGeometry").asDouble() <= instructionDistance);
        assertEquals("turn sharp left onto la Callisa, then keep left", voiceInstruction.get("announcement").asText());

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
        assertEquals("Arrive at destination", bannerInstruction.get("text").asText());

        JsonNode waypointsJson = json.get("waypoints");
        assertEquals(2, waypointsJson.size());
        JsonNode waypointLoc = waypointsJson.get(0).get("location");
        assertEquals(1.536198, waypointLoc.get(0).asDouble(), .001);

    }

    @Test
    public void arriveGeometryTest() {

        GHResponse rsp = hopper.route(new GHRequest(42.554851, 1.536198, 42.510071, 1.548128).setProfile(profile));

        ObjectNode json = NavigateResponseConverter.convertFromGHResponse(rsp, trMap, Locale.ENGLISH, distanceConfig);

        JsonNode steps = json.get("routes").get(0).get("legs").get(0).get("steps");

        // Step 17 is the last before arrival
        JsonNode step = steps.get(17);

        PointList expectedArrivePointList = rsp.getBest().getInstructions().get(17).getPoints().clone(false);
        PointList ghArrive = rsp.getBest().getInstructions().get(18).getPoints();
        // We expect that the Mapbox compatible response builds the geometry to the
        // arrival coordinate
        expectedArrivePointList.add(ghArrive);
        String encodedExpected = ResponsePathSerializer.encodePolyline(expectedArrivePointList, false, 1e6);

        assertEquals(encodedExpected, step.get("geometry").asText());
    }

    @Test
    public void voiceInstructionsTest() {

        GHResponse rsp = hopper.route(new GHRequest(42.554851, 1.536198, 42.510071, 1.548128).setProfile(profile));

        ObjectNode json = NavigateResponseConverter.convertFromGHResponse(rsp, trMap, Locale.ENGLISH, distanceConfig);

        JsonNode steps = json.get("routes").get(0).get("legs").get(0).get("steps");

        // Step 4 is about 240m long
        JsonNode step = steps.get(4);

        JsonNode voiceInstructions = step.get("voiceInstructions");
        assertEquals(2, voiceInstructions.size());
        JsonNode voiceInstruction = voiceInstructions.get(0);
        assertEquals(200, voiceInstruction.get("distanceAlongGeometry").asDouble(), 1);
        assertEquals("In 200 meters At roundabout, take exit 2 onto CS-340, then At roundabout, take exit 2 onto CG-3",
                voiceInstruction.get("announcement").asText());

        // Step 14 is over 3km long
        step = steps.get(14);

        voiceInstructions = step.get("voiceInstructions");
        assertEquals(4, voiceInstructions.size());
        voiceInstruction = voiceInstructions.get(0);
        assertEquals(2000, voiceInstruction.get("distanceAlongGeometry").asDouble(), 1);
        assertEquals("In 2 kilometers keep right", voiceInstruction.get("announcement").asText());

        voiceInstruction = voiceInstructions.get(3);
        assertEquals("keep right", voiceInstruction.get("announcement").asText());
    }

    @Test
    public void voiceInstructionsImperialTest() {

        GHResponse rsp = hopper.route(new GHRequest(42.554851, 1.536198, 42.510071, 1.548128).setProfile(profile));

        ObjectNode json = NavigateResponseConverter.convertFromGHResponse(rsp, trMap, Locale.ENGLISH,
                new DistanceConfig(DistanceUtils.Unit.IMPERIAL, trMap, Locale.ENGLISH));

        JsonNode steps = json.get("routes").get(0).get("legs").get(0).get("steps");

        // Step 4 is about 240m long
        JsonNode step = steps.get(4);
        JsonNode maneuver = step.get("maneuver");

        JsonNode voiceInstructions = step.get("voiceInstructions");
        assertEquals(2, voiceInstructions.size());
        JsonNode voiceInstruction = voiceInstructions.get(0);
        assertEquals(200, voiceInstruction.get("distanceAlongGeometry").asDouble(), 1);
        assertEquals("In 600 feet At roundabout, take exit 2 onto CS-340, then At roundabout, take exit 2 onto CG-3",
                voiceInstruction.get("announcement").asText());

        // Step 14 is over 3km long
        step = steps.get(14);
        maneuver = step.get("maneuver");

        voiceInstructions = step.get("voiceInstructions");
        assertEquals(4, voiceInstructions.size());
        voiceInstruction = voiceInstructions.get(0);
        assertEquals(3220, voiceInstruction.get("distanceAlongGeometry").asDouble(), 1);
        assertEquals("In 2 miles keep right", voiceInstruction.get("announcement").asText());

        voiceInstruction = voiceInstructions.get(3);
        assertEquals("keep right", voiceInstruction.get("announcement").asText());
    }

    @Test
    @Disabled
    public void alternativeRoutesTest() {

        GHResponse rsp = hopper.route(new GHRequest(42.554851, 1.536198, 42.510071, 1.548128).setProfile(profile)
                .setAlgorithm(Parameters.Algorithms.ALT_ROUTE));

        assertEquals(2, rsp.getAll().size());

        ObjectNode json = NavigateResponseConverter.convertFromGHResponse(rsp, trMap, Locale.ENGLISH, distanceConfig);

        JsonNode routes = json.get("routes");
        assertEquals(2, routes.size());

        assertEquals("GraphHopper Route 0", routes.get(0).get("legs").get(0).get("summary").asText());
        assertEquals("Avinguda Sant Antoni, CG-3", routes.get(1).get("legs").get(0).get("summary").asText());
    }

    @Test
    public void voiceInstructionTranslationTest() {

        GHResponse rsp = hopper.route(new GHRequest(42.554851, 1.536198, 42.510071, 1.548128).setProfile(profile));

        ObjectNode json = NavigateResponseConverter.convertFromGHResponse(rsp, trMap, Locale.ENGLISH, distanceConfig);

        JsonNode steps = json.get("routes").get(0).get("legs").get(0).get("steps");
        JsonNode voiceInstruction = steps.get(14).get("voiceInstructions").get(0);
        assertEquals("In 2 kilometers keep right", voiceInstruction.get("announcement").asText());

        rsp = hopper.route(
                new GHRequest(42.554851, 1.536198, 42.510071, 1.548128).setProfile(profile).setLocale(Locale.GERMAN));

        DistanceConfig distanceConfigGerman = new DistanceConfig(DistanceUtils.Unit.METRIC, trMap, Locale.GERMAN);

        json = NavigateResponseConverter.convertFromGHResponse(rsp, trMap, Locale.GERMAN, distanceConfigGerman);

        steps = json.get("routes").get(0).get("legs").get(0).get("steps");
        voiceInstruction = steps.get(14).get("voiceInstructions").get(0);
        assertEquals("In 2 Kilometern rechts halten", voiceInstruction.get("announcement").asText());
    }

    @Test
    public void roundaboutDegreesTest() {

        GHResponse rsp = hopper.route(new GHRequest(42.554851, 1.536198, 42.510071, 1.548128).setProfile(profile));

        ObjectNode json = NavigateResponseConverter.convertFromGHResponse(rsp, trMap, Locale.ENGLISH, distanceConfig);

        JsonNode steps = json.get("routes").get(0).get("legs").get(0).get("steps");

        JsonNode step = steps.get(5);
        JsonNode bannerInstructions = step.get("bannerInstructions");
        JsonNode primary = bannerInstructions.get(0).get("primary");

        assertEquals("roundabout", primary.get("type").asText());
        assertEquals("At roundabout, take exit 2 onto CG-3", primary.get("text").asText());
        assertEquals("right", primary.get("modifier").asText());
        assertEquals(222, primary.get("degrees").asDouble(), 1);

    }

    @Test
    public void intersectionTest() {
        GHResponse rsp = hopper.route(new GHRequest(42.554851, 1.536198, 42.510071, 1.548128).setProfile(profile)
                .setPathDetails(Collections.singletonList("intersection")));

        ObjectNode json = NavigateResponseConverter.convertFromGHResponse(rsp, trMap, Locale.ENGLISH, distanceConfig);

        JsonNode steps = json.get("routes").get(0).get("legs").get(0).get("steps");

        JsonNode step = steps.get(0);

        JsonNode intersection = step.get("intersections").get(0);

        assertFalse(intersection.has("in"));
        assertEquals(1, intersection.get("out").asInt());

        JsonNode location = intersection.get("location");
        // The first intersection to be equal to the first snapped waypoint
        assertEquals(rsp.getBest().getWaypoints().get(0).lon, location.get(0).asDouble(), .000001);
        assertEquals(rsp.getBest().getWaypoints().get(0).lat, location.get(1).asDouble(), .000001);

        step = steps.get(4);
        intersection = step.get("intersections").get(3);
        assertEquals(2, intersection.get("in").asInt());
        assertEquals(0, intersection.get("out").asInt());
        location = intersection.get("location");
        assertEquals(1.534679, location.get(0).asDouble(), .000001);
        assertEquals(42.556444, location.get(1).asDouble(), .000001);
    }

    @Test
    public void barrierTest() {
        // There is a barrier https://www.openstreetmap.org/node/2206610569 on the route
        GHResponse rsp = hopper.route(new GHRequest(42.601991, 1.687227, 42.601616, 1.687888).setProfile(profile)
                .setPathDetails(Collections.singletonList("intersection")));

        ObjectNode json = NavigateResponseConverter.convertFromGHResponse(rsp, trMap, Locale.ENGLISH, distanceConfig);

        JsonNode steps = json.get("routes").get(0).get("legs").get(0).get("steps");

        JsonNode step = steps.get(1);

        JsonNode intersection = step.get("intersections").get(1);

        // checking order of entries
        assertEquals(0, intersection.get("out").asInt());

        JsonNode location = intersection.get("location");
        // The location of the barrier
        assertEquals(location.get(0).asDouble(), 1.6878903, .000001);
        assertEquals(location.get(1).asDouble(), 42.601764, .000001);

        int inPosition = intersection.get("in").asInt();
        int outPosition = intersection.get("out").asInt();
        JsonNode entry = intersection.get("entry");
        assertEquals(false, entry.get(inPosition).asBoolean());
        assertEquals(true, entry.get(outPosition).asBoolean());

        JsonNode bearings = intersection.get("bearings");
        double inBearing = bearings.get(inPosition).asDouble();
        double outBearing = bearings.get(outPosition).asDouble();

        // and these should be the bearings
        assertEquals(353, inBearing);
        assertEquals(171, outBearing);

        // and no additional intersection
        assertEquals(step.get("intersections").size(), 2);
    }

    @Test
    public void testMultipleWaypoints() {

        GHRequest request = new GHRequest();
        request.addPoint(new GHPoint(42.504606, 1.522438));
        request.addPoint(new GHPoint(42.504776, 1.527209));
        request.addPoint(new GHPoint(42.505144, 1.526113));
        request.addPoint(new GHPoint(42.50529, 1.527218));
        request.setProfile(profile);

        GHResponse rsp = hopper.route(request);

        ObjectNode json = NavigateResponseConverter.convertFromGHResponse(rsp, trMap, Locale.ENGLISH, distanceConfig);

        // Check that all waypoints are there and in the right order
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

        // Check that there are 3 legs
        JsonNode route = json.get("routes").get(0);
        JsonNode legs = route.get("legs");
        assertEquals(3, legs.size());

        double duration = 0;
        double distance = 0;

        for (int i = 0; i < 3; i++) {
            JsonNode leg = legs.get(i);

            duration += leg.get("duration").asDouble();
            distance += leg.get("distance").asDouble();

            JsonNode steps = leg.get("steps");
            JsonNode step = steps.get(0);
            JsonNode maneuver = step.get("maneuver");
            assertEquals("depart", maneuver.get("type").asText());

            maneuver = steps.get(steps.size() - 1).get("maneuver");
            assertEquals("arrive", maneuver.get("type").asText());
        }

        // Check if the duration and distance of the legs sum up to the overall route
        // distance and duration
        assertEquals(route.get("duration").asDouble(), duration, 1);
        assertEquals(route.get("distance").asDouble(), distance, 1);
    }

    @Test
    public void testError() {
        GHResponse rsp = hopper.route(new GHRequest(42.554851, 111.536198, 42.510071, 1.548128).setProfile(profile));

        ObjectNode json = NavigateResponseConverter.convertFromGHResponseError(rsp);

        assertEquals("InvalidInput", json.get("code").asText());
        assertTrue(json.get("message").asText().startsWith("Point 0 is out of bounds: 42.554851,111.536198"));
    }

}
