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
import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopperAPI;
import com.graphhopper.ResponsePath;
import com.graphhopper.api.GraphHopperWeb;
import com.graphhopper.config.CHProfile;
import com.graphhopper.config.Profile;
import com.graphhopper.http.GraphHopperApplication;
import com.graphhopper.http.GraphHopperServerConfiguration;
import com.graphhopper.http.util.GraphHopperServerTestConfiguration;
import com.graphhopper.routing.ev.RoadClass;
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

import static com.graphhopper.http.util.TestUtils.clientTarget;
import static com.graphhopper.http.util.TestUtils.clientUrl;
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
                putObject("graph.flag_encoders", "car").
                putObject("routing.ch.disabling_allowed", true).
                putObject("prepare.min_network_size", 0).
                putObject("datareader.file", "../core/files/andorra.osm.pbf").
                putObject("graph.encoded_values", "road_class,surface,road_environment,max_speed").
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

        // we currently just ignore URL parameters (not sure if this is a good or bad thing)
        jsonStr = "{\"points\": [[1.536198,42.554851], [1.548128, 42.510071]] }";
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
        GraphHopperWeb hopper = new GraphHopperWeb();
        assertTrue(hopper.load(clientUrl(app, "/route")));
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
        assertEquals(24, instructions.size());
        assertEquals("Continue onto la Callisa", instructions.get(0).getTurnDescription(null));
        assertEquals("At roundabout, take exit 2", instructions.get(4).getTurnDescription(null));
        assertEquals(true, instructions.get(4).getExtraInfoJSON().get("exited"));
        assertEquals(false, instructions.get(22).getExtraInfoJSON().get("exited"));
    }

    @Test
    public void testPathDetailsRoadClass() {
        GraphHopperAPI hopper = new com.graphhopper.api.GraphHopperWeb();
        assertTrue(hopper.load(clientUrl(app, "/route")));
        GHRequest request = new GHRequest(42.546757, 1.528645, 42.520573, 1.557999).setProfile("my_car");
        request.setPathDetails(Arrays.asList(RoadClass.KEY, Surface.KEY, RoadEnvironment.KEY, "average_speed"));
        GHResponse rsp = hopper.route(request);
        assertFalse(rsp.hasErrors(), rsp.getErrors().toString());
        assertEquals(4, rsp.getBest().getPathDetails().get(RoadClass.KEY).size());
        assertEquals(RoadClass.PRIMARY.toString(), rsp.getBest().getPathDetails().get(RoadClass.KEY).get(3).getValue());

        List<PathDetail> roadEnvList = rsp.getBest().getPathDetails().get(RoadEnvironment.KEY);
        assertEquals(10, roadEnvList.size());
        assertEquals(RoadEnvironment.ROAD.toString(), roadEnvList.get(0).getValue());
        assertEquals(RoadEnvironment.TUNNEL.toString(), roadEnvList.get(6).getValue());
    }

    @Test
    public void testPathDetails() {
        GraphHopperAPI hopper = new com.graphhopper.api.GraphHopperWeb();
        assertTrue(hopper.load(clientUrl(app, "/route")));
        GHRequest request = new GHRequest(42.554851, 1.536198, 42.510071, 1.548128).setProfile("my_car");
        request.setPathDetails(Arrays.asList("average_speed", "edge_id", "time"));
        GHResponse rsp = hopper.route(request);
        assertFalse(rsp.hasErrors(), rsp.getErrors().toString());
        assertTrue(rsp.getErrors().isEmpty(), rsp.getErrors().toString());
        Map<String, List<PathDetail>> pathDetails = rsp.getBest().getPathDetails();
        assertFalse(pathDetails.isEmpty());
        assertTrue(pathDetails.containsKey("average_speed"));
        assertTrue(pathDetails.containsKey("edge_id"));
        assertTrue(pathDetails.containsKey("time"));
        List<PathDetail> averageSpeedList = pathDetails.get("average_speed");
        assertEquals(14, averageSpeedList.size());
        assertEquals(30.0, averageSpeedList.get(0).getValue());
        assertEquals(14, averageSpeedList.get(0).getLength());
        assertEquals(60.1, averageSpeedList.get(1).getValue());
        assertEquals(5, averageSpeedList.get(1).getLength());

        List<PathDetail> edgeIdDetails = pathDetails.get("edge_id");
        assertEquals(77, edgeIdDetails.size());
        assertEquals(880L, edgeIdDetails.get(0).getValue());
        assertEquals(2, edgeIdDetails.get(0).getLength());
        assertEquals(881L, edgeIdDetails.get(1).getValue());
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
        GraphHopperAPI hopper = new com.graphhopper.api.GraphHopperWeb();
        assertTrue(hopper.load(clientUrl(app, "/route")));
        GHRequest request = new GHRequest(42.554851, 1.536198, 42.554851, 1.536198)
                .setPathDetails(Arrays.asList("average_speed", "edge_id", "time"))
                .setProfile("my_car");
        GHResponse rsp = hopper.route(request);
        assertFalse(rsp.hasErrors(), rsp.getErrors().toString());
        assertTrue(rsp.getErrors().isEmpty(), rsp.getErrors().toString());
    }

    @Test
    public void testPathDetailsNoConnection() {
        GraphHopperAPI hopper = new com.graphhopper.api.GraphHopperWeb();
        assertTrue(hopper.load(clientUrl(app, "/route")));
        GHRequest request = new GHRequest(42.542078, 1.45586, 42.537841, 1.439981);
        request.setPathDetails(Collections.singletonList("average_speed"));
        request.setProfile("my_car");
        GHResponse rsp = hopper.route(request);
        assertTrue(rsp.hasErrors(), rsp.getErrors().toString());
    }

    @Test
    public void testPathDetailsWithoutGraphHopperWeb() {
        final Response response = clientTarget(app, "/route?profile=my_car&" +
                "point=42.554851,1.536198&point=42.510071,1.548128&details=average_speed&details=edge_id&details=max_speed").request().buildGet().invoke();
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
        assertEquals(60.1, averageSpeed.get(1).get(2).asDouble(), .1);
        assertEquals(19, averageSpeed.get(1).get(1).asInt());
        assertTrue(details.has("edge_id"));
        JsonNode edgeIds = details.get("edge_id");
        int firstLink = edgeIds.get(0).get(2).asInt();
        int lastLink = edgeIds.get(edgeIds.size() - 1).get(2).asInt();
        assertEquals(880, firstLink);
        assertEquals(1421, lastLink);

        JsonNode maxSpeed = details.get("max_speed");
        assertEquals(-1, maxSpeed.get(0).get(2).asDouble(-1), .01);
        assertEquals(50, maxSpeed.get(1).get(2).asDouble(-1), .01);
    }

    @Test
    public void testInitInstructionsWithTurnDescription() {
        GraphHopperAPI hopper = new com.graphhopper.api.GraphHopperWeb();
        assertTrue(hopper.load(clientUrl(app, "/route")));
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
        GraphHopperAPI hopper = new com.graphhopper.api.GraphHopperWeb();
        assertTrue(hopper.load(clientUrl(app, "route")));
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
        GraphHopperAPI hopper = new com.graphhopper.api.GraphHopperWeb();
        assertTrue(hopper.load(clientUrl(app, "/route")));
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
        GraphHopperAPI hopper = new GraphHopperWeb().setPostRequest(usePost);
        assertTrue(hopper.load(clientUrl(app, "/route")));

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
        rsp = hopper.route(new GHRequest(points).putHint("vehicle", "SPACE-SHUTTLE"));
        assertFalse(rsp.getErrors().isEmpty(), "Errors expected but not found.");
        ex = rsp.getErrors().get(0);
        assertTrue(ex instanceof IllegalArgumentException, "Wrong exception found: " + ex.getClass().getName()
                + ", IllegalArgumentException expected.");
        assertTrue(ex.getMessage().contains("Vehicle not supported: `space-shuttle`. Supported are: `car`" +
                "\nYou should consider using the `profile` parameter instead of specifying a vehicle." +
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
        assertTrue(str.contains("<gh:distance>1841.8</gh:distance>"));
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
        req.putHint("elevation", false);
        req.putHint("instructions", true);
        req.putHint("calc_points", true);
        req.putHint("type", "gpx");
        req.putHint("gpx.track", false);
        GraphHopperWeb gh = new GraphHopperWeb(clientUrl(app, "/route"))
                // gpx not supported for POST
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
}
