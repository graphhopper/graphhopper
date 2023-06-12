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
import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.ResponsePath;
import com.graphhopper.api.GraphHopperWeb;
import com.graphhopper.application.GraphHopperApplication;
import com.graphhopper.application.GraphHopperServerConfiguration;
import com.graphhopper.application.util.GraphHopperServerTestConfiguration;
import com.graphhopper.config.CHProfile;
import com.graphhopper.config.Profile;
import com.graphhopper.routing.ev.RoadClass;
import com.graphhopper.routing.ev.RoadClassLink;
import com.graphhopper.routing.ev.RoadEnvironment;
import com.graphhopper.routing.ev.Surface;
import com.graphhopper.util.Helper;
import com.graphhopper.util.InstructionList;
import com.graphhopper.util.Parameters;
import com.graphhopper.util.details.PathDetail;
import com.graphhopper.util.exceptions.PointOutOfBoundsException;
import com.graphhopper.util.shapes.GHPoint;
import io.dropwizard.testing.junit5.DropwizardAppExtension;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.File;
import java.util.*;

import static com.graphhopper.application.util.TestUtils.clientTarget;
import static com.graphhopper.application.util.TestUtils.clientUrl;
import static com.graphhopper.util.Instruction.FINISH;
import static com.graphhopper.util.Instruction.REACHED_VIA;
import static com.graphhopper.util.Parameters.NON_CH.MAX_NON_CH_POINT_DISTANCE;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Peter Karich
 */
@ExtendWith(DropwizardExtensionsSupport.class)
public class RouteResourceTest {

    // for this test we use a non-standard profile name
    private static final Map<String, String> mapboxResolver = new HashMap<String, String>() {
        {
            put("driving", "my_car");
            put("driving-traffic", "my_car");
        }
    };

    private static final String DIR = "./target/andorra-gh/";
    private static final DropwizardAppExtension<GraphHopperServerConfiguration> app = new DropwizardAppExtension<>(GraphHopperApplication.class, createConfig());

    private static GraphHopperServerConfiguration createConfig() {
        GraphHopperServerConfiguration config = new GraphHopperServerTestConfiguration();
        config.getGraphHopperConfiguration().
                putObject("profiles_mapbox", mapboxResolver).
                putObject("graph.vehicles", "car").
                putObject("prepare.min_network_size", 0).
                putObject("datareader.file", "../core/files/andorra.osm.pbf").
                putObject("graph.encoded_values", "road_class,surface,road_environment,max_speed,country").
                putObject("max_speed_calculator.enabled", true).
                putObject("graph.urban_density.threads", 1). // for max_speed_calculator
                putObject("graph.urban_density.city_radius", 0).
                putObject("import.osm.ignored_highways", "").
                putObject("graph.location", DIR)
                // adding this so the corresponding check is not just skipped...
                .putObject(MAX_NON_CH_POINT_DISTANCE, 10e6)
                .setProfiles(Collections.singletonList(new Profile("my_car").setVehicle("car").setWeighting("fastest")))
                .setCHProfiles(Collections.singletonList(new CHProfile("my_car")));
        return config;
    }

    @BeforeAll
    @AfterAll
    public static void cleanUp() {
        Helper.removeDir(new File(DIR));
    }

    @Test
    public void testBasicQuery() {
        final Response response = clientTarget(app, "/route?profile=my_car&" +
                "point=42.554851,1.536198&point=42.510071,1.548128").request().buildGet().invoke();
        assertEquals(200, response.getStatus());
        JsonNode json = response.readEntity(JsonNode.class);
        JsonNode infoJson = json.get("info");
        assertFalse(infoJson.has("errors"));
        JsonNode path = json.get("paths").get(0);
        double distance = path.get("distance").asDouble();
        assertTrue(distance > 9000, "distance wasn't correct:" + distance);
        assertTrue(distance < 9500, "distance wasn't correct:" + distance);
    }

    @Test
    public void testBasicQuerySamePoint() {
        final Response response = clientTarget(app, "/route?profile=my_car&" +
                "point=42.510071,1.548128&point=42.510071,1.548128").request().buildGet().invoke();
        assertEquals(200, response.getStatus());
        JsonNode path = response.readEntity(JsonNode.class).get("paths").get(0);
        assertEquals(0, path.get("distance").asDouble(), 0.001);
        assertEquals("[1.548191,42.510033,1.548191,42.510033]", path.get("bbox").toString());
    }

    @Test
    public void testBasicPostQuery() {
        String jsonStr = "{ \"profile\": \"my_car\", \"points\": [[1.536198,42.554851], [1.548128, 42.510071]] }";
        Response response = clientTarget(app, "/route").request().post(Entity.json(jsonStr));
        assertEquals(200, response.getStatus());
        JsonNode json = response.readEntity(JsonNode.class);
        JsonNode infoJson = json.get("info");
        assertFalse(infoJson.has("errors"));
        JsonNode path = json.get("paths").get(0);
        double distance = path.get("distance").asDouble();

        assertTrue(distance > 9000, "distance wasn't correct:" + distance);
        assertTrue(distance < 9500, "distance wasn't correct:" + distance);

        // we currently just ignore URL parameters in a POST request (not sure if this is a good or bad thing)
        jsonStr = "{\"points\": [[1.536198,42.554851], [1.548128, 42.510071]], \"profile\": \"my_car\" }";
        response = clientTarget(app, "/route?vehicle=unknown&weighting=unknown").request().post(Entity.json(jsonStr));
        assertEquals(200, response.getStatus());
        assertFalse(response.readEntity(JsonNode.class).get("info").has("errors"));
    }

    @Test
    public void testBasicNavigationQuery() {
        Response response = clientTarget(app, "/navigate/directions/v5/gh/driving/1.537174,42.507145;1.539116,42.511368?" +
                "access_token=pk.my_api_key&alternatives=true&geometries=polyline6&overview=full&steps=true&continue_straight=true&" +
                "annotations=congestion%2Cdistance&language=en&roundabout_exits=true&voice_instructions=true&banner_instructions=true&voice_units=metric").
                request().get();
        assertEquals(200, response.getStatus());
        JsonNode json = response.readEntity(JsonNode.class);
        assertEquals(1256, json.get("routes").get(0).get("distance").asDouble(), 20);
    }

    @Test
    public void testWrongPointFormat() {
        final Response response = clientTarget(app, "/route?profile=my_car&point=1234&point=42.510071,1.548128").request().buildGet().invoke();
        assertEquals(400, response.getStatus());
        JsonNode json = response.readEntity(JsonNode.class);
        assertTrue(json.get("message").asText().contains("Cannot parse point '1234'"), "There should be an error " + json.get("message"));
    }

    @Test
    public void testAcceptOnlyXmlButNoTypeParam() {
        final Response response = clientTarget(app, "/route?profile=my_car&point=42.554851,1.536198&point=42.510071,1.548128")
                .request(MediaType.APPLICATION_XML).buildGet().invoke();
        assertEquals(200, response.getStatus());
        JsonNode json = response.readEntity(JsonNode.class);
        JsonNode infoJson = json.get("info");
        assertFalse(infoJson.has("errors"));
    }

    @Test
    public void testQueryWithoutInstructions() {
        final Response response = clientTarget(app, "/route?profile=my_car&point=42.554851,1.536198&point=42.510071,1.548128&instructions=false").request().buildGet().invoke();
        assertEquals(200, response.getStatus());
        JsonNode json = response.readEntity(JsonNode.class);
        JsonNode infoJson = json.get("info");
        assertFalse(infoJson.has("errors"));
        JsonNode path = json.get("paths").get(0);
        double distance = path.get("distance").asDouble();
        assertTrue(distance > 9000, "distance wasn't correct:" + distance);
        assertTrue(distance < 9500, "distance wasn't correct:" + distance);
    }

    @Test
    public void testCHWithHeading_error() {
        // There are special cases where heading works with node-based CH, but generally it leads to wrong results -> we expect an error
        final Response response = clientTarget(app, "/route?profile=my_car&"
                + "point=42.496696,1.499323&point=42.497257,1.501501&heading=240&heading=240").request().buildGet().invoke();
        assertEquals(400, response.getStatus());
        JsonNode json = response.readEntity(JsonNode.class);
        assertTrue(json.has("message"), "There should have been an error response");
        String expected = "The 'heading' parameter is currently not supported for speed mode, you need to disable speed mode with `ch.disable=true`. See issue #483";
        assertTrue(json.get("message").asText().contains(expected), "There should be an error containing " + expected + ", but got: " + json.get("message"));
    }

    @Test
    public void testCHWithPassThrough_error() {
        // There are special cases where pass_through works with node-based CH, but generally it leads to wrong results -> we expect an error
        final Response response = clientTarget(app, "/route?profile=my_car&" +
                "point=42.534133,1.581473&point=42.534781,1.582149&point=42.535042,1.582514&pass_through=true").request().buildGet().invoke();
        assertEquals(400, response.getStatus());
        JsonNode json = response.readEntity(JsonNode.class);
        assertTrue(json.has("message"), "There should have been an error response");
        String expected = "The '" + Parameters.Routing.PASS_THROUGH + "' parameter is currently not supported for speed mode, you need to disable speed mode with `ch.disable=true`. See issue #1765";
        assertTrue(json.get("message").asText().contains(expected), "There should be an error containing " + expected + ", but got: " + json.get("message"));
    }

    @Test
    public void testJsonRounding() {
        final Response response = clientTarget(app, "/route?profile=my_car&" +
                "point=42.554851234,1.536198&point=42.510071,1.548128&points_encoded=false").request().buildGet().invoke();
        assertEquals(200, response.getStatus());
        JsonNode json = response.readEntity(JsonNode.class);
        JsonNode cson = json.get("paths").get(0).get("points");
        assertTrue(cson.toString().contains("[1.536374,42.554839]"), "unexpected precision!");
    }

    @Test
    public void testFailIfElevationRequestedButNotIncluded() {
        final Response response = clientTarget(app, "/route?profile=my_car&" +
                "point=42.554851234,1.536198&point=42.510071,1.548128&points_encoded=false&elevation=true").request().buildGet().invoke();
        assertEquals(400, response.getStatus());
        JsonNode json = response.readEntity(JsonNode.class);
        assertTrue(json.has("message"));
        assertEquals("Elevation not supported!", json.get("message").asText());
    }

    @Test
    public void testGraphHopperWeb() {
        GraphHopperWeb hopper = new GraphHopperWeb(clientUrl(app, "/route"));
        GHResponse rsp = hopper.route(new GHRequest(42.554851, 1.536198, 42.510071, 1.548128).setProfile("my_car"));
        assertFalse(rsp.hasErrors(), rsp.getErrors().toString());
        assertTrue(rsp.getErrors().isEmpty(), rsp.getErrors().toString());

        ResponsePath res = rsp.getBest();
        assertTrue(res.getDistance() > 9000, "distance wasn't correct:" + res.getDistance());
        assertTrue(res.getDistance() < 9500, "distance wasn't correct:" + res.getDistance());

        rsp = hopper.route(new GHRequest().
                setProfile("my_car").
                addPoint(new GHPoint(42.554851, 1.536198)).
                addPoint(new GHPoint(42.531896, 1.553278)).
                addPoint(new GHPoint(42.510071, 1.548128)));
        assertTrue(rsp.getErrors().isEmpty(), rsp.getErrors().toString());
        res = rsp.getBest();
        assertTrue(res.getDistance() > 20000, "distance wasn't correct:" + res.getDistance());
        assertTrue(res.getDistance() < 21000, "distance wasn't correct:" + res.getDistance());

        InstructionList instructions = res.getInstructions();
        assertEquals(25, instructions.size());
        assertEquals("Continue onto la Callisa", instructions.get(0).getTurnDescription(null));
        assertEquals("At roundabout, take exit 2", instructions.get(4).getTurnDescription(null));
        assertEquals(true, instructions.get(4).getExtraInfoJSON().get("exited"));
        assertEquals(false, instructions.get(23).getExtraInfoJSON().get("exited"));
    }

    @Test
    public void testPathDetailsRoadClass() {
        GraphHopperWeb client = new GraphHopperWeb(clientUrl(app, "/route"));
        GHRequest request = new GHRequest(42.546757, 1.528645, 42.520573, 1.557999).setProfile("my_car");
        request.setPathDetails(Arrays.asList(RoadClass.KEY, Surface.KEY, RoadEnvironment.KEY, "average_speed", RoadClassLink.KEY));
        GHResponse rsp = client.route(request);
        assertFalse(rsp.hasErrors(), rsp.getErrors().toString());
        assertEquals(4, rsp.getBest().getPathDetails().get(RoadClass.KEY).size());
        assertEquals("primary", rsp.getBest().getPathDetails().get(RoadClass.KEY).get(3).getValue());
        assertFalse((Boolean) rsp.getBest().getPathDetails().get(RoadClassLink.KEY).get(0).getValue());

        List<PathDetail> roadEnvList = rsp.getBest().getPathDetails().get(RoadEnvironment.KEY);
        assertEquals(10, roadEnvList.size());
        assertEquals("road", roadEnvList.get(0).getValue());
        assertEquals("tunnel", roadEnvList.get(6).getValue());
    }

    @Test
    public void testPathDetails() {
        GraphHopperWeb client = new GraphHopperWeb(clientUrl(app, "/route"));
        GHRequest request = new GHRequest(42.554851, 1.536198, 42.510071, 1.548128).setProfile("my_car");
        request.setPathDetails(Arrays.asList("average_speed", "edge_id", "time"));
        GHResponse rsp = client.route(request);
        assertFalse(rsp.hasErrors(), rsp.getErrors().toString());
        assertTrue(rsp.getErrors().isEmpty(), rsp.getErrors().toString());
        Map<String, List<PathDetail>> pathDetails = rsp.getBest().getPathDetails();
        assertFalse(pathDetails.isEmpty());
        assertTrue(pathDetails.containsKey("average_speed"));
        assertTrue(pathDetails.containsKey("edge_id"));
        assertTrue(pathDetails.containsKey("time"));
        List<PathDetail> averageSpeedList = pathDetails.get("average_speed");
        assertEquals(11, averageSpeedList.size());
        assertEquals(30.0, averageSpeedList.get(0).getValue());
        assertEquals(14, averageSpeedList.get(0).getLength());
        assertEquals(60.0, averageSpeedList.get(1).getValue());
        assertEquals(5, averageSpeedList.get(1).getLength());

        List<PathDetail> edgeIdDetails = pathDetails.get("edge_id");
        assertEquals(78, edgeIdDetails.size());
        assertEquals(924L, edgeIdDetails.get(0).getValue());
        assertEquals(2, edgeIdDetails.get(0).getLength());
        assertEquals(925L, edgeIdDetails.get(1).getValue());
        assertEquals(8, edgeIdDetails.get(1).getLength());

        long expectedTime = rsp.getBest().getTime();
        long actualTime = 0;
        List<PathDetail> timeDetails = pathDetails.get("time");
        for (PathDetail pd : timeDetails) {
            actualTime += (Long) pd.getValue();
        }

        assertEquals(expectedTime, actualTime);
    }

    @Test
    public void testPathDetailsSamePoint() {
        GraphHopperWeb hopper = new GraphHopperWeb(clientUrl(app, "/route"));
        GHRequest request = new GHRequest(42.554851, 1.536198, 42.554851, 1.536198)
                .setPathDetails(Arrays.asList("average_speed", "edge_id", "time"))
                .setProfile("my_car");
        GHResponse rsp = hopper.route(request);
        assertFalse(rsp.hasErrors(), rsp.getErrors().toString());
        assertTrue(rsp.getErrors().isEmpty(), rsp.getErrors().toString());
    }

    @Test
    public void testPathDetailsNoConnection() {
        GraphHopperWeb hopper = new GraphHopperWeb(clientUrl(app, "/route"));
        GHRequest request = new GHRequest(42.542078, 1.45586, 42.537841, 1.439981);
        request.setPathDetails(Collections.singletonList("average_speed"));
        request.setProfile("my_car");
        GHResponse rsp = hopper.route(request);
        assertTrue(rsp.hasErrors(), rsp.getErrors().toString());
    }

    @Test
    public void testPathDetailsWithoutGraphHopperWeb() {
        final Response response = clientTarget(app, "/route?profile=my_car&" +
                "point=42.554851,1.536198&point=42.510071,1.548128&details=average_speed&details=edge_id&details=max_speed&details=urban_density").request().buildGet().invoke();
        assertEquals(200, response.getStatus());
        JsonNode json = response.readEntity(JsonNode.class);
        JsonNode infoJson = json.get("info");
        assertFalse(infoJson.has("errors"));
        JsonNode path = json.get("paths").get(0);
        assertTrue(path.has("details"));
        JsonNode details = path.get("details");
        assertTrue(details.has("average_speed"));
        JsonNode averageSpeed = details.get("average_speed");
        assertEquals(30.0, averageSpeed.get(0).get(2).asDouble(), .1);
        assertEquals(14, averageSpeed.get(0).get(1).asInt(), .1);
        assertEquals(60.0, averageSpeed.get(1).get(2).asDouble(), .1);
        assertEquals(19, averageSpeed.get(1).get(1).asInt());
        assertTrue(details.has("edge_id"));
        JsonNode edgeIds = details.get("edge_id");
        int firstLink = edgeIds.get(0).get(2).asInt();
        int lastLink = edgeIds.get(edgeIds.size() - 1).get(2).asInt();
        assertEquals(924, firstLink);
        assertEquals(1584, lastLink);

        JsonNode maxSpeed = details.get("max_speed");
        assertEquals("[0,33,50.0]", maxSpeed.get(0).toString());
        assertEquals("[33,34,60.0]", maxSpeed.get(1).toString());
        assertEquals("[34,38,50.0]", maxSpeed.get(2).toString());
        assertEquals("[38,50,90.0]", maxSpeed.get(3).toString());
        assertEquals("[50,52,50.0]", maxSpeed.get(4).toString());
        assertEquals("[52,78,90.0]", maxSpeed.get(5).toString());

        JsonNode urbanDensityNode = details.get("urban_density");
        assertEquals("[0,53,\"residential\"]", urbanDensityNode.get(0).toString());
        assertEquals("[53,68,\"rural\"]", urbanDensityNode.get(1).toString());
    }

    @Test
    public void testInitInstructionsWithTurnDescription() {
        GraphHopperWeb hopper = new GraphHopperWeb(clientUrl(app, "/route"));
        GHRequest request = new GHRequest(42.554851, 1.536198, 42.510071, 1.548128);
        request.setProfile("my_car");
        GHResponse rsp = hopper.route(request);
        assertFalse(rsp.hasErrors(), rsp.getErrors().toString());
        assertEquals("Continue onto Carrer Antoni Fiter i Rossell", rsp.getBest().getInstructions().get(3).getName());

        request.getHints().putObject("turn_description", false);
        rsp = hopper.route(request);
        assertFalse(rsp.hasErrors());
        assertEquals("Carrer Antoni Fiter i Rossell", rsp.getBest().getInstructions().get(3).getName());
    }

    @Test
    public void testSnapPreventions() {
        GraphHopperWeb hopper = new GraphHopperWeb(clientUrl(app, "route"));
        GHRequest request = new GHRequest(42.511139, 1.53285, 42.508165, 1.532271);
        request.setProfile("my_car");
        GHResponse rsp = hopper.route(request);
        assertFalse(rsp.hasErrors(), rsp.getErrors().toString());
        assertEquals(490, rsp.getBest().getDistance(), 2);

        request.setSnapPreventions(Collections.singletonList("tunnel"));
        rsp = hopper.route(request);
        assertEquals(1081, rsp.getBest().getDistance(), 2);
    }

    @Test
    public void testSnapPreventionsAndPointHints() {
        GraphHopperWeb hopper = new GraphHopperWeb(clientUrl(app, "/route"));
        GHRequest request = new GHRequest(42.511139, 1.53285, 42.508165, 1.532271);
        request.setProfile("my_car");
        request.setSnapPreventions(Collections.singletonList("tunnel"));
        request.setPointHints(Arrays.asList("Avinguda Fiter i Rossell", ""));
        GHResponse rsp = hopper.route(request);
        assertFalse(rsp.hasErrors(), rsp.getErrors().toString());
        assertEquals(1590, rsp.getBest().getDistance(), 2);

        // contradicting hints should still allow routing
        request.setSnapPreventions(Collections.singletonList("tunnel"));
        request.setPointHints(Arrays.asList("Tunèl del Pont Pla", ""));
        rsp = hopper.route(request);
        assertEquals(490, rsp.getBest().getDistance(), 2);
    }

    @Test
    public void testPostWithPointHintsAndSnapPrevention() {
        String jsonStr = "{ \"points\": [[1.53285,42.511139], [1.532271,42.508165]], " +
                "\"profile\": \"my_car\", " +
                "\"point_hints\":[\"Avinguda Fiter i Rossell\",\"\"] }";
        Response response = clientTarget(app, "/route").request().post(Entity.json(jsonStr));
        assertEquals(200, response.getStatus());
        JsonNode path = response.readEntity(JsonNode.class).get("paths").get(0);
        assertEquals(1590, path.get("distance").asDouble(), 2);

        jsonStr = "{ \"points\": [[1.53285,42.511139], [1.532271,42.508165]], " +
                "\"profile\": \"my_car\", " +
                "\"point_hints\":[\"Tunèl del Pont Pla\",\"\"], " +
                "\"snap_preventions\": [\"tunnel\"] }";
        response = clientTarget(app, "/route").request().post(Entity.json(jsonStr));
        assertEquals(200, response.getStatus());
        path = response.readEntity(JsonNode.class).get("paths").get(0);
        assertEquals(490, path.get("distance").asDouble(), 2);
    }

    @ParameterizedTest(name = "POST = {0}")
    @ValueSource(booleans = {false, true})
    public void testGraphHopperWebRealExceptions(boolean usePost) {
        GraphHopperWeb hopper = new GraphHopperWeb(clientUrl(app, "/route")).setPostRequest(usePost);

        // this one actually works
        List<GHPoint> points = Arrays.asList(new GHPoint(42.554851, 1.536198), new GHPoint(42.510071, 1.548128));
        GHResponse rsp = hopper.route(new GHRequest(points).setProfile("my_car"));
        assertEquals(9204, rsp.getBest().getDistance(), 10);

        // unknown profile
        rsp = hopper.route(new GHRequest(points).setProfile("space_shuttle"));
        assertTrue(rsp.hasErrors(), rsp.getErrors().toString());
        assertTrue(rsp.getErrors().get(0).getMessage().contains(
                "The requested profile 'space_shuttle' does not exist"), rsp.getErrors().toString());

        // unknown profile via web api
        Response response = clientTarget(app, "/route?profile=SPACE-SHUTTLE&point=42.554851,1.536198&point=42.510071,1.548128").request().buildGet().invoke();
        assertEquals(400, response.getStatus());
        String msg = (String) response.readEntity(Map.class).get("message");
        assertTrue(msg.contains("The requested profile 'SPACE-SHUTTLE' does not exist"), msg);

        // no points
        rsp = hopper.route(new GHRequest().setProfile("my_car"));
        assertFalse(rsp.getErrors().isEmpty(), "Errors expected but not found.");
        Throwable ex = rsp.getErrors().get(0);
        assertTrue(ex instanceof IllegalArgumentException, "Wrong exception found: " + ex.getClass().getName()
                + ", IllegalArgumentException expected.");
        assertTrue(ex.getMessage().contains("You have to pass at least one point"), ex.getMessage());

        // no points without CH
        rsp = hopper.route(new GHRequest().setProfile("my_car").putHint(Parameters.CH.DISABLE, true));
        assertFalse(rsp.getErrors().isEmpty(), "Errors expected but not found.");
        ex = rsp.getErrors().get(0);
        assertTrue(ex instanceof IllegalArgumentException, "Wrong exception found: " + ex.getClass().getName()
                + ", IllegalArgumentException expected.");
        assertTrue(ex.getMessage().contains("You have to pass at least one point"), ex.getMessage());

        // points out of bounds
        rsp = hopper.route(new GHRequest(0.0, 0.0, 0.0, 0.0).setProfile("my_car"));
        assertFalse(rsp.getErrors().isEmpty(), "Errors expected but not found.");
        List<Throwable> errs = rsp.getErrors();
        for (int i = 0; i < errs.size(); i++) {
            assertEquals(((PointOutOfBoundsException) errs.get(i)).getPointIndex(), i);
            assertTrue(errs.get(i).getMessage().contains("Point 0 is out of bounds: 0.0,0.0"), errs.get(i).getMessage());
        }

        // todo: add a check with too few headings, but client-hc does not support headings, #2009

        // too many curbsides
        rsp = hopper.route(new GHRequest(points).setCurbsides(Arrays.asList("right", "left", "right")).setProfile("my_car"));
        assertFalse(rsp.getErrors().isEmpty(), "Errors expected but not found.");
        assertTrue(rsp.getErrors().toString().contains("If you pass curbside, you need to pass exactly one curbside for every point"), rsp.getErrors().toString());

        // too few point hints
        rsp = hopper.route(new GHRequest(points).setPointHints(Collections.singletonList("foo")).setProfile("my_car"));
        assertFalse(rsp.getErrors().isEmpty(), "Errors expected but not found.");
        assertTrue(rsp.getErrors().toString().contains("If you pass point_hint, you need to pass exactly one hint for every point"), rsp.getErrors().toString());

        // unknown vehicle
        rsp = hopper.route(new GHRequest(points).setProfile("SPACE-SHUTTLE"));
        assertFalse(rsp.getErrors().isEmpty(), "Errors expected but not found.");
        ex = rsp.getErrors().get(0);
        assertTrue(ex instanceof IllegalArgumentException, "Wrong exception found: " + ex.getClass().getName()
                + ", IllegalArgumentException expected.");
        assertTrue(ex.getMessage().contains("The requested profile 'SPACE-SHUTTLE' does not exist." +
                "\nAvailable profiles: [my_car]"), ex.getMessage());

        // an IllegalArgumentException from inside the core is written as JSON, unknown profile
        response = clientTarget(app, "/route?profile=SPACE-SHUTTLE&point=42.554851,1.536198&point=42.510071,1.548128").request().buildGet().invoke();
        assertEquals(400, response.getStatus());
        msg = (String) response.readEntity(Map.class).get("message");
        assertTrue(msg.contains("The requested profile 'SPACE-SHUTTLE' does not exist"), msg);
    }

    @Test
    public void testGPX() {
        final Response response = clientTarget(app, "/route?profile=my_car&" +
                "point=42.554851,1.536198&point=42.510071,1.548128&type=gpx").request().buildGet().invoke();
        assertEquals(200, response.getStatus());
        String str = response.readEntity(String.class);
        // For backward compatibility we currently export route and track.
        assertTrue(str.contains("<gh:distance>1841.5</gh:distance>"), str);
        assertFalse(str.contains("<wpt lat=\"42.51003\" lon=\"1.548188\"> <name>Finish!</name></wpt>"));
        assertTrue(str.contains("<trkpt lat=\"42.554839\" lon=\"1.536374\"><time>"));
    }

    @Test
    public void testGPXWithExcludedRouteSelection() {
        final Response response = clientTarget(app, "/route?profile=my_car&" +
                "point=42.554851,1.536198&point=42.510071,1.548128&type=gpx&gpx.route=false&gpx.waypoints=false").request().buildGet().invoke();
        assertEquals(200, response.getStatus());
        String str = response.readEntity(String.class);
        assertFalse(str.contains("<gh:distance>115.1</gh:distance>"));
        assertFalse(str.contains("<wpt lat=\"42.51003\" lon=\"1.548188\"> <name>Finish!</name></wpt>"));
        assertTrue(str.contains("<trkpt lat=\"42.554839\" lon=\"1.536374\"><time>"));
    }

    @Test
    public void testGPXWithTrackAndWaypointsSelection() {
        final Response response = clientTarget(app, "/route?profile=my_car&" +
                "point=42.554851,1.536198&point=42.510071,1.548128&type=gpx&gpx.track=true&gpx.route=false&gpx.waypoints=true").request().buildGet().invoke();
        assertEquals(200, response.getStatus());
        String str = response.readEntity(String.class);
        assertFalse(str.contains("<gh:distance>115.1</gh:distance>"));
        assertTrue(str.contains("<wpt lat=\"42.510033\" lon=\"1.548191\"> <name>arrive at destination</name></wpt>"));
        assertTrue(str.contains("<trkpt lat=\"42.554839\" lon=\"1.536374\"><time>"));
    }

    @Test
    public void testGPXWithError() {
        final Response response = clientTarget(app, "/route?profile=my_car&" +
                "point=42.554851,1.536198&type=gpx").request().buildGet().invoke();
        assertEquals(400, response.getStatus());
        String str = response.readEntity(String.class);
        assertFalse(str.contains("<html>"), str);
        assertFalse(str.contains("{"), str);
        assertTrue(str.contains("<message>At least 2 points have to be specified, but was:1</message>"), "Expected error but was: " + str);
        assertTrue(str.contains("<hints><error details=\"java"), "Expected error but was: " + str);
    }

    @Test
    public void testGPXExport() {
        GHRequest req = new GHRequest(42.554851, 1.536198, 42.510071, 1.548128);
        req.setProfile("my_car");
        req.putHint("elevation", false);
        req.putHint("instructions", true);
        req.putHint("calc_points", true);
        req.putHint("gpx.millis", "300000000");
        req.putHint("type", "gpx");
        GraphHopperWeb gh = new GraphHopperWeb(clientUrl(app, "/route"))
                // gpx not supported for POST
                .setPostRequest(false);
        String res = gh.export(req);
        assertTrue(res.contains("<gpx"));
        assertTrue(res.contains("<rtept lat="));
        assertTrue(res.contains("<trk><name>GraphHopper Track</name><trkseg>"));
        assertTrue(res.endsWith("</gpx>"));
        // this is due to `gpx.millis` we set (dates are shifted by the given (ms!) value from 1970-01-01)
        assertTrue(res.contains("1970-01-04"));
    }

    @Test
    public void testExportWithoutTrack() {
        GHRequest req = new GHRequest(42.554851, 1.536198, 42.510071, 1.548128);
        req.setProfile("my_car");
        req.putHint("elevation", false);
        req.putHint("instructions", true);
        req.putHint("calc_points", true);
        req.putHint("type", "gpx");
        req.putHint("gpx.track", false);
        GraphHopperWeb gh = new GraphHopperWeb(clientUrl(app, "/route"))
                // gpx is not supported for POST
                .setPostRequest(false);
        String res = gh.export(req);
        assertTrue(res.contains("<gpx"));
        assertTrue(res.contains("<rtept lat="));
        assertFalse(res.contains("<trk><name>GraphHopper Track</name><trkseg>"));
        assertTrue(res.endsWith("</gpx>"));
    }

    @Test
    public void testWithError() {
        final Response response = clientTarget(app, "/route?profile=my_car&" +
                "point=42.554851,1.536198").request().buildGet().invoke();
        assertEquals(400, response.getStatus());
        String rsp = response.readEntity(String.class);
        assertTrue(rsp.contains("At least 2 points have to be specified, but was:1"), rsp);

    }

    @Test
    public void testNoPoint() {
        Response response = clientTarget(app, "/route?profile=my_car&heading=0").request().buildGet().invoke();
        JsonNode json = response.readEntity(JsonNode.class);
        assertEquals(400, response.getStatus());
        assertEquals("You have to pass at least one point", json.get("message").asText());
    }

    @Test
    public void testBadPoint() {
        Response response = clientTarget(app, "/route?profile=my_car&heading=0&point=pups").request().buildGet().invoke();
        JsonNode json = response.readEntity(JsonNode.class);
        assertEquals(400, response.getStatus());
        assertEquals("query param point is invalid: Cannot parse point 'pups'", json.get("message").asText());
    }

    @Test
    public void testTooManyHeadings() {
        final Response response = clientTarget(app, "/route?profile=my_car&" +
                "point=42.554851,1.536198&heading=0&heading=0").request().buildGet().invoke();
        assertEquals(400, response.getStatus());
        JsonNode json = response.readEntity(JsonNode.class);
        assertEquals("The number of 'heading' parameters must be zero, one or equal to the number of points (1)", json.get("message").asText());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void legDetailsAndPointIndices(boolean instructions) {
        final long seed = 123L;
        Random rnd = new Random(seed);
        List<String> legDetails = Arrays.asList("leg_time", "leg_distance", "leg_weight");
        int errors = 0;
        for (int numPoints = 2; numPoints < 10; numPoints++) {
            String url = "/route?profile=my_car&points_encoded=false&instructions=" + instructions;
            for (int i = 0; i < numPoints; i++) {
                double lat = 42.493748 + rnd.nextDouble() * (42.565155 - 42.493748);
                double lon = 1.487522 + rnd.nextDouble() * (1.557285 - 1.487522);
                url += "&point=" + lat + "," + lon;
            }
            for (String legDetail : legDetails)
                url += "&details=" + legDetail;
            final Response response = clientTarget(app, url).request().buildGet().invoke();
            JsonNode json = response.readEntity(JsonNode.class);
            if (response.getStatus() != 200) {
                // sometimes there can be connection-not-found for example, also because we set min_network_size to 0 in this test
                errors++;
                continue;
            }
            assertFalse(json.has("message"));
            JsonNode path = json.get("paths").get(0);
            JsonNode points = path.get("points");
            JsonNode snappedWaypoints = path.get("snapped_waypoints");
            assertEquals(numPoints, snappedWaypoints.get("coordinates").size());

            assertEquals(path.get("time").asDouble(), sumDetail(path.get("details").get("leg_time")), 1);
            assertEquals(path.get("distance").asDouble(), sumDetail(path.get("details").get("leg_distance")), 1);
            assertEquals(path.get("weight").asDouble(), sumDetail(path.get("details").get("leg_weight")), 1);

            for (String detail : legDetails) {
                JsonNode legDetail = path.get("details").get(detail);
                assertEquals(numPoints - 1, legDetail.size());
                assertEquals(snappedWaypoints.get("coordinates").get(0), points.get("coordinates").get(legDetail.get(0).get(0).asInt()));
                for (int i = 1; i < numPoints; i++)
                    // we make sure that the intervals defined by the leg details start/end at the snapped waypoints
                    assertEquals(snappedWaypoints.get("coordinates").get(i), points.get("coordinates").get(legDetail.get(i - 1).get(1).asInt()));

                if (instructions) {
                    // we can find the way point indices also from the instructions, so we check if this yields the same
                    List<Integer> waypointIndicesFromInstructions = getWaypointIndicesFromInstructions(path.get("instructions"));
                    List<Integer> waypointIndicesFromLegDetails = getWaypointIndicesFromLegDetails(legDetail);
                    assertEquals(waypointIndicesFromInstructions, waypointIndicesFromLegDetails);
                }
            }
        }
        if (errors > 3)
            fail("too many errors");
    }

    private static List<Integer> getWaypointIndicesFromInstructions(JsonNode instructions) {
        List<Integer> result = new ArrayList<>();
        result.add(instructions.get(0).get("interval").get(0).asInt());
        for (int i = 0; i < instructions.size(); i++) {
            int sign = instructions.get(i).get("sign").asInt();
            if (sign == REACHED_VIA || sign == FINISH)
                result.add(instructions.get(i).get("interval").get(1).asInt());
        }
        return result;
    }

    private static List<Integer> getWaypointIndicesFromLegDetails(JsonNode detail) {
        List<Integer> result = new ArrayList<>();
        result.add(detail.get(0).get(0).asInt());
        for (int i = 0; i < detail.size(); i++) {
            if (i > 0)
                assertEquals(detail.get(i - 1).get(1).asInt(), detail.get(i).get(0).asInt());
            result.add(detail.get(i).get(1).asInt());
        }
        return result;
    }

    private double sumDetail(JsonNode detail) {
        double result = 0;
        for (int i = 0; i < detail.size(); i++)
            result += detail.get(i).get(2).asDouble();
        return result;
    }
}
