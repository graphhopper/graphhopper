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
import com.graphhopper.util.CmdArgs;
import com.graphhopper.util.Helper;
import com.graphhopper.util.Parameters;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.*;

/**
 * @author Peter Karich
 */
public class GraphHopperServletWithEleIT extends BaseServletTester {
    private static final String dir = "./target/monaco-gh/";

    @AfterClass
    public static void cleanUp() {
        Helper.removeDir(new File(dir));
        shutdownJetty(true);
    }

    @Before
    public void setUp() {
        CmdArgs args = new CmdArgs().
                put("graph.elevation.provider", "srtm").
                put("graph.elevation.cachedir", "../core/files/").
                put(Parameters.CH.PREPARE + "weightings", "no").
                put("prepare.min_one_way_network_size", "0").
                put("config", "../config-example.properties").
                put("datareader.file", "../core/files/monaco.osm.gz").
                put("graph.location", dir);
        setUpJetty(args);
    }

    @Test
    public void testElevation() throws Exception {
        JsonNode json = query("point=43.730864,7.420771&point=43.727687,7.418737&points_encoded=false&elevation=true", 200);
        JsonNode infoJson = json.get("info");
        assertFalse(infoJson.has("errors"));
        JsonNode path = json.get("paths").get(0);
        double distance = path.get("distance").asDouble();
        assertTrue("distance wasn't correct:" + distance, distance > 2500);
        assertTrue("distance wasn't correct:" + distance, distance < 2700);

        JsonNode cson = path.get("points");
        assertTrue("no elevation?", cson.toString().contains("[7.421392,43.7307,66.0]"));

        // Although we include elevation DO NOT include it in the bbox as bbox.toGeoJSON messes up when reading
        // or reading with and without elevation would be too complex for the client with no real use
        assertEquals(4, path.get("bbox").size());
    }

    @Test
    public void testNoElevation() throws Exception {
        // default is elevation=false
        JsonNode json = query("point=43.730864,7.420771&point=43.727687,7.418737&points_encoded=false", 200);
        JsonNode infoJson = json.get("info");
        assertFalse(infoJson.has("errors"));
        JsonNode path = json.get("paths").get(0);
        double distance = path.get("distance").asDouble();
        assertTrue("distance wasn't correct:" + distance, distance > 2500);
        assertTrue("distance wasn't correct:" + distance, distance < 2700);
        JsonNode cson = path.get("points");
        assertTrue("Elevation should not be included!", cson.toString().contains("[7.421392,43.7307]"));

        // disable elevation
        json = query("point=43.730864,7.420771&point=43.727687,7.418737&points_encoded=false&elevation=false", 200);
        infoJson = json.get("info");
        assertFalse(infoJson.has("errors"));
        path = json.get("paths").get(0);
        cson = path.get("points");
        assertTrue("Elevation should not be included!", cson.toString().contains("[7.421392,43.7307]"));
    }
}
