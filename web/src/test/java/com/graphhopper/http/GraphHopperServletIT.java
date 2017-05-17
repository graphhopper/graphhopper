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
package com.graphhopper.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopperAPI;
import com.graphhopper.PathWrapper;
import com.graphhopper.util.CmdArgs;
import com.graphhopper.util.Helper;
import com.graphhopper.util.exceptions.PointOutOfBoundsException;
import com.graphhopper.util.shapes.GHPoint;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * @author Peter Karich
 */
public class GraphHopperServletIT extends BaseServletTester {
    private static final String DIR = "./target/andorra-gh/";

    @AfterClass
    public static void cleanUp() {
        Helper.removeDir(new File(DIR));
        shutdownJetty(true);
    }

    @Before
    public void setUp() {
        CmdArgs args = new CmdArgs().
                put("config", "../config-example.properties").
                put("datareader.file", "../core/files/andorra.osm.pbf").
                put("graph.location", DIR);
        setUpJetty(args);
    }

    @Test
    public void testBasicQuery() throws Exception {
        JsonNode json = query("point=42.554851,1.536198&point=42.510071,1.548128", 200);
        JsonNode infoJson = json.get("info");
        assertFalse(infoJson.has("errors"));
        JsonNode path = json.get("paths").get(0);
        double distance = path.get("distance").asDouble();
        assertTrue("distance wasn't correct:" + distance, distance > 9000);
        assertTrue("distance wasn't correct:" + distance, distance < 9500);
    }

    @Test
    public void testQueryWithDirections() throws Exception {
        // Note, in general specifying directions does not work with CH, but this is an example where it works
        JsonNode json = query("point=42.496696,1.499323&point=42.497257,1.501501&heading=240&heading=240&ch.force_heading=true", 200);
        JsonNode infoJson = json.get("info");
        assertFalse(infoJson.has("errors"));
        JsonNode path = json.get("paths").get(0);
        double distance = path.get("distance").asDouble();
        assertTrue("distance wasn't correct:" + distance, distance > 960);
        assertTrue("distance wasn't correct:" + distance, distance < 970);
    }

    @Test
    public void testQueryWithStraightVia() throws Exception {
        // Note, in general specifying straightvia does not work with CH, but this is an example where it works
        JsonNode json = query(
                "point=42.534133,1.581473&point=42.534781,1.582149&point=42.535042,1.582514&pass_through=true", 200);
        JsonNode infoJson = json.get("info");
        assertFalse(infoJson.has("errors"));
        JsonNode path = json.get("paths").get(0);
        double distance = path.get("distance").asDouble();
        assertTrue("distance wasn't correct:" + distance, distance > 320);
        assertTrue("distance wasn't correct:" + distance, distance < 325);
    }

    @Test
    public void testJsonRounding() throws Exception {
        JsonNode json = query("point=42.554851234,1.536198&point=42.510071,1.548128&points_encoded=false", 200);
        JsonNode cson = json.get("paths").get(0).get("points");
        assertTrue("unexpected precision!", cson.toString().contains("[1.536374,42.554839]"));
    }

    @Test
    public void testFailIfElevationRequestedButNotIncluded() throws Exception {
        JsonNode json = query("point=42.554851234,1.536198&point=42.510071,1.548128&points_encoded=false&elevation=true", 400);
        assertTrue(json.has("message"));
        assertEquals("Elevation not supported!", json.get("message").asText());
        assertEquals("Elevation not supported!", json.get("hints").get(0).get("message").asText());
    }

    @Test
    public void testGraphHopperWeb() throws Exception {
        GraphHopperAPI hopper = new GraphHopperWeb();
        assertTrue(hopper.load(getTestRouteAPIUrl()));
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
    public void testInitInstructionsWithTurnDescription() {
        GraphHopperAPI hopper = new GraphHopperWeb();
        assertTrue(hopper.load(getTestRouteAPIUrl()));
        GHRequest request = new GHRequest(42.554851, 1.536198, 42.510071, 1.548128);
        GHResponse rsp = hopper.route(request);
        assertEquals("Continue onto Carrer Antoni Fiter i Rossell", rsp.getBest().getInstructions().get(3).getName());

        request.getHints().put("turn_description", false);
        rsp = hopper.route(request);
        assertEquals("Carrer Antoni Fiter i Rossell", rsp.getBest().getInstructions().get(3).getName());
    }

    @Test
    public void testGraphHopperWebRealExceptions() {
        GraphHopperAPI hopper = new GraphHopperWeb();
        assertTrue(hopper.load(getTestRouteAPIUrl()));

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
    }

    @Test
    public void testGPX() throws Exception {
        String str = queryString("point=42.554851,1.536198&point=42.510071,1.548128&type=gpx", 200);
        // For backward compatibility we currently export route and track.
        assertTrue(str.contains("<gh:distance>1841.8</gh:distance>"));
        assertFalse(str.contains("<wpt lat=\"42.51003\" lon=\"1.548188\"> <name>Finish!</name></wpt>"));
        assertTrue(str.contains("<trkpt lat=\"42.554839\" lon=\"1.536374\"><time>"));
    }

    @Test
    public void testGPXWithExcludedRouteSelection() throws Exception {
        String str = queryString("point=42.554851,1.536198&point=42.510071,1.548128&type=gpx&gpx.route=false&gpx.waypoints=false", 200);
        assertFalse(str.contains("<gh:distance>115.1</gh:distance>"));
        assertFalse(str.contains("<wpt lat=\"42.51003\" lon=\"1.548188\"> <name>Finish!</name></wpt>"));
        assertTrue(str.contains("<trkpt lat=\"42.554839\" lon=\"1.536374\"><time>"));
    }

    @Test
    public void testGPXWithTrackAndWaypointsSelection() throws Exception {
        String str = queryString("point=42.554851,1.536198&point=42.510071,1.548128&type=gpx&gpx.track=true&gpx.route=false&gpx.waypoints=true", 200);
        assertFalse(str.contains("<gh:distance>115.1</gh:distance>"));
        assertTrue(str.contains("<wpt lat=\"42.51003\" lon=\"1.548188\"> <name>Finish!</name></wpt>"));
        assertTrue(str.contains("<trkpt lat=\"42.554839\" lon=\"1.536374\"><time>"));
    }

    @Test
    public void testGPXWithError() throws Exception {
        String str = queryString("point=42.554851,1.536198&type=gpx", 400);
        assertFalse(str, str.contains("<html>"));
        assertFalse(str, str.contains("{"));
        assertTrue("Expected error but was: " + str, str.contains("<message>At least 2 points have to be specified, but was:1</message>"));
        assertTrue("Expected error but was: " + str, str.contains("<hints><error details=\"java"));
    }

    @Test
    public void testUndefinedPointHeading() throws Exception {
        JsonNode json = query("point=undefined&heading=0", 400);
        assertEquals("You have to pass at least one point", json.get("message").asText());
        json = query("point=42.554851,1.536198&point=undefined&heading=0&heading=0", 400);
        assertEquals("The number of 'heading' parameters must be <= 1 or equal to the number of points (1)", json.get("message").asText());
    }
}
