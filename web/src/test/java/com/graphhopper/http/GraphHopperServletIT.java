/*
 *  Licensed to GraphHopper and Peter Karich under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  GraphHopper licenses this file to you under the Apache License, 
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

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopperAPI;
import com.graphhopper.util.CmdArgs;
import com.graphhopper.util.Helper;
import com.graphhopper.util.shapes.GHPoint;
import java.io.File;
import java.util.List;
import java.util.Map;
import org.json.JSONObject;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * @author Peter Karich
 */
public class GraphHopperServletIT extends BaseServletTester
{
    private static final String dir = "./target/andorra-gh/";

    @AfterClass
    public static void cleanUp()
    {
        Helper.removeDir(new File(dir));
        shutdownJetty(true);
    }

    @Before
    public void setUp()
    {
        CmdArgs args = new CmdArgs().
                put("config", "../config-example.properties").
                put("osmreader.osm", "../core/files/andorra.osm.pbf").
                put("graph.location", dir);
        setUpJetty(args);
    }

    @Test
    public void testBasicQuery() throws Exception
    {
        JSONObject json = query("point=42.554851,1.536198&point=42.510071,1.548128");
        JSONObject infoJson = json.getJSONObject("info");
        assertFalse(infoJson.has("errors"));
        JSONObject path = json.getJSONArray("paths").getJSONObject(0);
        double distance = path.getDouble("distance");
        assertTrue("distance wasn't correct:" + distance, distance > 9000);
        assertTrue("distance wasn't correct:" + distance, distance < 9500);
    }

    @Test
    public void testJsonRounding() throws Exception
    {
        JSONObject json = query("point=42.554851234,1.536198&point=42.510071,1.548128&points_encoded=false");
        JSONObject cson = json.getJSONArray("paths").getJSONObject(0).getJSONObject("points");
        assertTrue("unexpected precision!", cson.toString().contains("[1.536374,42.554839]"));
    }

    @Test
    public void testFailIfElevationRequestedButNotIncluded() throws Exception
    {
        JSONObject json = query("point=42.554851234,1.536198&point=42.510071,1.548128&points_encoded=false&elevation=true");
        JSONObject infoJson = json.getJSONObject("info");
        assertTrue(infoJson.has("errors"));
        assertEquals("Elevation not supported!", infoJson.getJSONArray("errors").getJSONObject(0).getString("message"));
    }

    @Test
    public void testGraphHopperWeb() throws Exception
    {
        GraphHopperAPI hopper = new GraphHopperWeb();
        assertTrue(hopper.load(getTestRouteAPIUrl()));
        GHResponse rsp = hopper.route(new GHRequest(42.554851, 1.536198, 42.510071, 1.548128));
        assertTrue(rsp.getErrors().toString(), rsp.getErrors().isEmpty());
        assertTrue("distance wasn't correct:" + rsp.getDistance(), rsp.getDistance() > 9000);
        assertTrue("distance wasn't correct:" + rsp.getDistance(), rsp.getDistance() < 9500);

        rsp = hopper.route(new GHRequest().
                addPoint(new GHPoint(42.554851, 1.536198)).
                addPoint(new GHPoint(42.531896, 1.553278)).
                addPoint(new GHPoint(42.510071, 1.548128)));
        assertTrue(rsp.getErrors().toString(), rsp.getErrors().isEmpty());
        assertTrue("distance wasn't correct:" + rsp.getDistance(), rsp.getDistance() > 20000);
        assertTrue("distance wasn't correct:" + rsp.getDistance(), rsp.getDistance() < 21000);

        List<Map<String, Object>> instructions = rsp.getInstructions().createJson();
        assertEquals(23, instructions.size());
        assertEquals("Continue onto la Callisa", instructions.get(0).get("text"));
        assertEquals("At roundabout, take exit 2", instructions.get(3).get("text"));
    }

    @Test
    public void testGraphHopperWebRealExceptions()
    {
        GHResponse rsp;
        Throwable ex;

        GraphHopperAPI hopper = new GraphHopperWeb();
        assertTrue(hopper.load(getTestRouteAPIUrl()));

        // IllegalStateException (Wrong Request)
        rsp = hopper.route(new GHRequest());
        assertFalse("Errors expected but not found.", rsp.getErrors().isEmpty());

        ex = rsp.getErrors().get(0);
        assertTrue("Wrong Exception found: " + ex.getClass().getName()
                + ", IllegalStateException expected.", ex instanceof IllegalStateException);

        // IllegalArgumentException (Wrong Points)
        rsp = hopper.route(new GHRequest(0.0, 0.0, 0.0, 0.0));
        assertFalse("Errors expected but not found.", rsp.getErrors().isEmpty());

        ex = rsp.getErrors().get(0);
        assertTrue("Wrong Exception found: " + ex.getClass().getName()
                + ", IllegalArgumentException expected.", ex instanceof IllegalArgumentException);

        // IllegalArgumentException (Vehicle not supported)
        rsp = hopper.route(new GHRequest(42.554851, 1.536198, 42.510071, 1.548128).setVehicle("SPACE-SHUTTLE"));
        assertFalse("Errors expected but not found.", rsp.getErrors().isEmpty());

        ex = rsp.getErrors().get(0);
        assertTrue("Wrong Exception found: " + ex.getClass().getName()
                + ", IllegalArgumentException expected.", ex instanceof IllegalArgumentException);

        // UnsupportedOperationException
        // RuntimeException
        // Exception
    }
}
