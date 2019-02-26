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
import com.graphhopper.PathWrapper;
import com.graphhopper.api.GraphHopperWeb;
import com.graphhopper.http.GraphHopperApplication;
import com.graphhopper.http.GraphHopperServerConfiguration;
import com.graphhopper.util.CmdArgs;
import com.graphhopper.util.Helper;
import com.graphhopper.util.details.PathDetail;
import com.graphhopper.util.exceptions.PointOutOfBoundsException;
import com.graphhopper.util.shapes.GHPoint;
import io.dropwizard.testing.junit.DropwizardAppRule;
import org.junit.AfterClass;
import org.junit.ClassRule;
import org.junit.Test;

import javax.ws.rs.core.Response;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * @author Peter Karich
 */
public class RouteResourceTest {
    private static final String DIR = "./target/andorra-gh/";

    private static final GraphHopperServerConfiguration config = new GraphHopperServerConfiguration();

    static {
        config.getGraphHopperConfiguration().merge(new CmdArgs().
                put("graph.flag_encoders", "car").
                put("prepare.ch.weightings", "fastest").
                put("prepare.min_network_size", "0").
                put("prepare.min_one_way_network_size", "0").
                put("datareader.file", "../core/files/andorra.osm.pbf").
                put("graph.location", DIR));
    }

    @ClassRule
    public static final DropwizardAppRule<GraphHopperServerConfiguration> app = new DropwizardAppRule(
            GraphHopperApplication.class, config);

    @AfterClass
    public static void cleanUp() {
        Helper.removeDir(new File(DIR));
    }

    @Test
    public void testBasicQuery() {
        final Response response = app.client().target("http://localhost:8080/route?point=42.554851,1.536198&point=42.510071,1.548128").request().buildGet().invoke();
        assertEquals(200, response.getStatus());
        JsonNode json = response.readEntity(JsonNode.class);
        JsonNode infoJson = json.get("info");
        assertFalse(infoJson.has("errors"));
        JsonNode path = json.get("paths").get(0);
        double distance = path.get("distance").asDouble();
        assertTrue("distance wasn't correct:" + distance, distance > 9000);
        assertTrue("distance wasn't correct:" + distance, distance < 9500);
    }

    @Test
    public void testWrongPointFormat() {
        final Response response = app.client().target("http://localhost:8080/route?point=1234&point=42.510071,1.548128").request().buildGet().invoke();
        assertEquals(400, response.getStatus());
        JsonNode json = response.readEntity(JsonNode.class);
        assertTrue("There should be an error " + json.get("message"), json.get("message").asText().contains("Cannot parse point '1234'"));
    }

    @Test
    public void testQueryWithDirections() throws Exception {
        // Note, in general specifying directions does not work with CH, but this is an example where it works
        final Response response = app.client().target("http://localhost:8080/route?" + "point=42.496696,1.499323&point=42.497257,1.501501&heading=240&heading=240&ch.force_heading=true").request().buildGet().invoke();
        assertEquals(200, response.getStatus());
        JsonNode json = response.readEntity(JsonNode.class);
        JsonNode infoJson = json.get("info");
        assertFalse(infoJson.has("errors"));
        JsonNode path = json.get("paths").get(0);
        double distance = path.get("distance").asDouble();
        assertTrue("distance wasn't correct:" + distance, distance > 960);
        assertTrue("distance wasn't correct:" + distance, distance < 970);
    }

    @Test
    public void testQueryWithStraightVia() throws Exception {
        // Note, in general specifying pass_through does not work with CH, but this is an example where it works
        final Response response = app.client().target("http://localhost:8080/route?point=42.534133,1.581473&point=42.534781,1.582149&point=42.535042,1.582514&pass_through=true").request().buildGet().invoke();
        assertEquals(200, response.getStatus());
        JsonNode json = response.readEntity(JsonNode.class);
        JsonNode infoJson = json.get("info");
        assertFalse(infoJson.has("errors"));
        JsonNode path = json.get("paths").get(0);
        double distance = path.get("distance").asDouble();
        assertTrue("distance wasn't correct:" + distance, distance > 320);
        assertTrue("distance wasn't correct:" + distance, distance < 325);
    }

    @Test
    public void testJsonRounding() throws Exception {
        final Response response = app.client().target("http://localhost:8080/route?point=42.554851234,1.536198&point=42.510071,1.548128&points_encoded=false").request().buildGet().invoke();
        assertEquals(200, response.getStatus());
        JsonNode json = response.readEntity(JsonNode.class);
        JsonNode cson = json.get("paths").get(0).get("points");
        assertTrue("unexpected precision!", cson.toString().contains("[1.536374,42.554839]"));
    }

    @Test
    public void testFailIfElevationRequestedButNotIncluded() throws Exception {
        final Response response = app.client().target("http://localhost:8080/route?point=42.554851234,1.536198&point=42.510071,1.548128&points_encoded=false&elevation=true").request().buildGet().invoke();
        assertEquals(400, response.getStatus());
        JsonNode json = response.readEntity(JsonNode.class);
        assertTrue(json.has("message"));
        assertEquals("Elevation not supported!", json.get("message").asText());
    }

    @Test
    public void testGraphHopperWeb() throws Exception {
        GraphHopperWeb hopper = new GraphHopperWeb();
        assertTrue(hopper.load("http://localhost:8080/route"));
        GHResponse rsp = hopper.route(new GHRequest(42.554851, 1.536198, 42.510071, 1.548128));
        assertFalse(rsp.getErrors().toString(), rsp.hasErrors());
        assertTrue(rsp.getErrors().toString(), rsp.getErrors().isEmpty());

        PathWrapper arsp = rsp.getBest();
        assertTrue("distance wasn't correct:" + arsp.getDistance(), arsp.getDistance() > 9000);
        assertTrue("distance wasn't correct:" + arsp.getDistance(), arsp.getDistance() < 9500);

        rsp = hopper.route(new GHRequest().
                addPoint(new GHPoint(42.554851, 1.536198)).
                addPoint(new GHPoint(42.531896, 1.553278)).
                addPoint(new GHPoint(42.510071, 1.548128)));
        assertTrue(rsp.getErrors().toString(), rsp.getErrors().isEmpty());
        arsp = rsp.getBest();
        assertTrue("distance wasn't correct:" + arsp.getDistance(), arsp.getDistance() > 20000);
        assertTrue("distance wasn't correct:" + arsp.getDistance(), arsp.getDistance() < 21000);

        List<Map<String, Object>> instructions = arsp.getInstructions().createJson();
        assertEquals(26, instructions.size());
        assertEquals("Continue onto la Callisa", instructions.get(0).get("text"));
        assertEquals("At roundabout, take exit 2", instructions.get(4).get("text"));
        assertEquals(true, instructions.get(4).get("exited"));
        assertEquals(false, instructions.get(24).get("exited"));
    }

    @Test
    public void testPathDetails() throws Exception {
        GraphHopperAPI hopper = new com.graphhopper.api.GraphHopperWeb();
        assertTrue(hopper.load("http://localhost:8080/route"));
        GHRequest request = new GHRequest(42.554851, 1.536198, 42.510071, 1.548128);
        request.setPathDetails(Arrays.asList("average_speed", "edge_id", "time"));
        GHResponse rsp = hopper.route(request);
        assertFalse(rsp.getErrors().toString(), rsp.hasErrors());
        assertTrue(rsp.getErrors().toString(), rsp.getErrors().isEmpty());
        Map<String, List<PathDetail>> pathDetails = rsp.getBest().getPathDetails();
        assertFalse(pathDetails.isEmpty());
        assertTrue(pathDetails.containsKey("average_speed"));
        assertTrue(pathDetails.containsKey("edge_id"));
        assertTrue(pathDetails.containsKey("time"));
        List<PathDetail> averageSpeedList = pathDetails.get("average_speed");
        assertEquals(9, averageSpeedList.size());
        assertEquals(30.0, averageSpeedList.get(0).getValue());
        assertEquals(14, averageSpeedList.get(0).getLength());
        assertEquals(60.0, averageSpeedList.get(1).getValue());
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
    public void testPathDetailsSamePoint() throws Exception {
        GraphHopperAPI hopper = new com.graphhopper.api.GraphHopperWeb();
        assertTrue(hopper.load("http://localhost:8080/route"));
        GHRequest request = new GHRequest(42.554851, 1.536198, 42.554851, 1.536198);
        request.setPathDetails(Arrays.asList("average_speed", "edge_id", "time"));
        GHResponse rsp = hopper.route(request);
        assertFalse(rsp.getErrors().toString(), rsp.hasErrors());
        assertTrue(rsp.getErrors().toString(), rsp.getErrors().isEmpty());
    }

    @Test
    public void testPathDetailsNoConnection() throws Exception {
        GraphHopperAPI hopper = new com.graphhopper.api.GraphHopperWeb();
        assertTrue(hopper.load("http://localhost:8080/route"));
        GHRequest request = new GHRequest(42.542078, 1.45586, 42.537841, 1.439981);
        request.setPathDetails(Arrays.asList("average_speed"));
        GHResponse rsp = hopper.route(request);
        assertTrue(rsp.getErrors().toString(), rsp.hasErrors());
    }

    @Test
    public void testPathDetailsWithoutGraphHopperWeb() throws Exception {
        final Response response = app.client().target("http://localhost:8080/route?point=42.554851,1.536198&point=42.510071,1.548128&details=average_speed&details=edge_id").request().buildGet().invoke();
        assertEquals(200, response.getStatus());
        JsonNode json = response.readEntity(JsonNode.class);
        JsonNode infoJson = json.get("info");
        assertFalse(infoJson.has("errors"));
        JsonNode path = json.get("paths").get(0);
        assertTrue(path.has("details"));
        JsonNode details = path.get("details");
        assertTrue(details.has("average_speed"));
        JsonNode averageSpeed = details.get("average_speed");
        assertEquals(30.0, averageSpeed.get(0).get(2).asDouble(), .01);
        assertEquals(14, averageSpeed.get(0).get(1).asInt());
        assertEquals(60.0, averageSpeed.get(1).get(2).asDouble(), .01);
        assertEquals(19, averageSpeed.get(1).get(1).asInt());
        assertTrue(details.has("edge_id"));
        JsonNode edgeIds = details.get("edge_id");
        int firstLink = edgeIds.get(0).get(2).asInt();
        int lastLink = edgeIds.get(edgeIds.size() - 1).get(2).asInt();
        assertEquals(880, firstLink);
        assertEquals(1421, lastLink);
    }

    @Test
    public void testInitInstructionsWithTurnDescription() {
        GraphHopperAPI hopper = new com.graphhopper.api.GraphHopperWeb();
        assertTrue(hopper.load("http://localhost:8080/route"));
        GHRequest request = new GHRequest(42.554851, 1.536198, 42.510071, 1.548128);
        GHResponse rsp = hopper.route(request);
        assertEquals("Continue onto Carrer Antoni Fiter i Rossell", rsp.getBest().getInstructions().get(3).getName());

        request.getHints().put("turn_description", false);
        rsp = hopper.route(request);
        assertEquals("Carrer Antoni Fiter i Rossell", rsp.getBest().getInstructions().get(3).getName());
    }

    @Test
    public void testGraphHopperWebRealExceptions() {
        GraphHopperAPI hopper = new com.graphhopper.api.GraphHopperWeb();
        assertTrue(hopper.load("http://localhost:8080/route"));

        // IllegalArgumentException (Wrong Request)
        GHResponse rsp = hopper.route(new GHRequest());
        assertFalse("Errors expected but not found.", rsp.getErrors().isEmpty());

        Throwable ex = rsp.getErrors().get(0);
        assertTrue("Wrong exception found: " + ex.getClass().getName()
                + ", IllegalArgumentException expected.", ex instanceof IllegalArgumentException);

        // IllegalArgumentException (Wrong Points)
        rsp = hopper.route(new GHRequest(0.0, 0.0, 0.0, 0.0));
        assertFalse("Errors expected but not found.", rsp.getErrors().isEmpty());

        List<Throwable> errs = rsp.getErrors();
        for (int i = 0; i < errs.size(); i++) {
            assertEquals(((PointOutOfBoundsException) errs.get(i)).getPointIndex(), i);
        }

        // IllegalArgumentException (Vehicle not supported)
        rsp = hopper.route(new GHRequest(42.554851, 1.536198, 42.510071, 1.548128).setVehicle("SPACE-SHUTTLE"));
        assertFalse("Errors expected but not found.", rsp.getErrors().isEmpty());

        ex = rsp.getErrors().get(0);
        assertTrue("Wrong exception found: " + ex.getClass().getName()
                + ", IllegalArgumentException expected.", ex instanceof IllegalArgumentException);

        // an IllegalArgumentException from inside the core is written as JSON
        final Response response = app.client().target("http://localhost:8080/route?vehicle=SPACE-SHUTTLE&point=42.554851,1.536198&point=42.510071,1.548128").request().buildGet().invoke();
        assertEquals(400, response.getStatus());
        String msg = (String) response.readEntity(Map.class).get("message");
        assertTrue(msg, msg.contains("Vehicle not supported:"));
    }

    @Test
    public void testGPX() {
        final Response response = app.client().target("http://localhost:8080/route?point=42.554851,1.536198&point=42.510071,1.548128&type=gpx").request().buildGet().invoke();
        assertEquals(200, response.getStatus());
        String str = response.readEntity(String.class);
        // For backward compatibility we currently export route and track.
        assertTrue(str.contains("<gh:distance>1841.8</gh:distance>"));
        assertFalse(str.contains("<wpt lat=\"42.51003\" lon=\"1.548188\"> <name>Finish!</name></wpt>"));
        assertTrue(str.contains("<trkpt lat=\"42.554839\" lon=\"1.536374\"><time>"));
    }

    @Test
    public void testGPXWithExcludedRouteSelection() throws Exception {
        final Response response = app.client().target("http://localhost:8080/route?point=42.554851,1.536198&point=42.510071,1.548128&type=gpx&gpx.route=false&gpx.waypoints=false").request().buildGet().invoke();
        assertEquals(200, response.getStatus());
        String str = response.readEntity(String.class);
        assertFalse(str.contains("<gh:distance>115.1</gh:distance>"));
        assertFalse(str.contains("<wpt lat=\"42.51003\" lon=\"1.548188\"> <name>Finish!</name></wpt>"));
        assertTrue(str.contains("<trkpt lat=\"42.554839\" lon=\"1.536374\"><time>"));
    }

    @Test
    public void testGPXWithTrackAndWaypointsSelection() throws Exception {
        final Response response = app.client().target("http://localhost:8080/route?point=42.554851,1.536198&point=42.510071,1.548128&type=gpx&gpx.track=true&gpx.route=false&gpx.waypoints=true").request().buildGet().invoke();
        assertEquals(200, response.getStatus());
        String str = response.readEntity(String.class);
        assertFalse(str.contains("<gh:distance>115.1</gh:distance>"));
        assertTrue(str.contains("<wpt lat=\"42.51003\" lon=\"1.548188\"> <name>arrive at destination</name></wpt>"));
        assertTrue(str.contains("<trkpt lat=\"42.554839\" lon=\"1.536374\"><time>"));
    }

    @Test
    public void testGPXWithError() {
        final Response response = app.client().target("http://localhost:8080/route?point=42.554851,1.536198&type=gpx").request().buildGet().invoke();
        assertEquals(400, response.getStatus());
        String str = response.readEntity(String.class);
        assertFalse(str, str.contains("<html>"));
        assertFalse(str, str.contains("{"));
        assertTrue("Expected error but was: " + str, str.contains("<message>At least 2 points have to be specified, but was:1</message>"));
        assertTrue("Expected error but was: " + str, str.contains("<hints><error details=\"java"));
    }

    @Test
    public void testWithError() throws Exception {
        final Response response = app.client().target("http://localhost:8080/route?point=42.554851,1.536198").request().buildGet().invoke();
        assertEquals(400, response.getStatus());
    }

    @Test
    public void testNoPoint() {
        JsonNode json = app.client().target("http://localhost:8080/route?heading=0").request().buildGet().invoke().readEntity(JsonNode.class);
        assertEquals("You have to pass at least one point", json.get("message").asText());
    }

    @Test
    public void testTooManyHeadings() {
        final Response response = app.client().target("http://localhost:8080/route?point=42.554851,1.536198&heading=0&heading=0").request().buildGet().invoke();
        assertEquals(400, response.getStatus());
        JsonNode json = response.readEntity(JsonNode.class);
        assertEquals("The number of 'heading' parameters must be <= 1 or equal to the number of points (1)", json.get("message").asText());
    }

}
