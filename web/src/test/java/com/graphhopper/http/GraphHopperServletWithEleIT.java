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

import com.graphhopper.util.CmdArgs;
import com.graphhopper.util.Helper;

import java.io.File;

import org.json.JSONObject;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author Peter Karich
 */
public class GraphHopperServletWithEleIT extends BaseServletTester
{
    private static final String dir = "./target/monaco-gh/";

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
                put("graph.elevation.provider", "srtm").
                put("graph.elevation.cachedir", "../core/files/").
                put("prepare.ch.weightings", "no").
                put("config", "../config-example.properties").
                put("osmreader.osm", "../core/files/monaco.osm.gz").
                put("graph.location", dir);
        setUpJetty(args);
    }

    @Test
    public void testElevation() throws Exception
    {
        JSONObject json = query("point=43.730864,7.420771&point=43.727687,7.418737&points_encoded=false&elevation=true", 200);
        JSONObject infoJson = json.getJSONObject("info");
        assertFalse(infoJson.has("errors"));
        JSONObject path = json.getJSONArray("paths").getJSONObject(0);
        double distance = path.getDouble("distance");
        assertTrue("distance wasn't correct:" + distance, distance > 2500);
        assertTrue("distance wasn't correct:" + distance, distance < 2700);

        JSONObject cson = path.getJSONObject("points");
        assertTrue("no elevation?", cson.toString().contains("[7.421392,43.7307,66]"));

        // Although we include elevation DO NOT include it in the bbox as bbox.toGeoJSON messes up when reading
        // or reading with and without elevation would be too complex for the client with no real use
        assertEquals(4, path.getJSONArray("bbox").length());
    }

    @Test
    public void testNoElevation() throws Exception
    {
        // default is elevation=false
        JSONObject json = query("point=43.730864,7.420771&point=43.727687,7.418737&points_encoded=false", 200);
        JSONObject infoJson = json.getJSONObject("info");
        assertFalse(infoJson.has("errors"));
        JSONObject path = json.getJSONArray("paths").getJSONObject(0);
        double distance = path.getDouble("distance");
        assertTrue("distance wasn't correct:" + distance, distance > 2500);
        assertTrue("distance wasn't correct:" + distance, distance < 2700);
        JSONObject cson = path.getJSONObject("points");
        assertTrue("Elevation should not be included!", cson.toString().contains("[7.421392,43.7307]"));

        // disable elevation
        json = query("point=43.730864,7.420771&point=43.727687,7.418737&points_encoded=false&elevation=false", 200);
        infoJson = json.getJSONObject("info");
        assertFalse(infoJson.has("errors"));
        path = json.getJSONArray("paths").getJSONObject(0);
        cson = path.getJSONObject("points");
        assertTrue("Elevation should not be included!", cson.toString().contains("[7.421392,43.7307]"));
    }
}
