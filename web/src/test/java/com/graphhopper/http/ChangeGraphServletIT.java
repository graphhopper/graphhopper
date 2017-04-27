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
import com.fasterxml.jackson.databind.ObjectMapper;
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
public class ChangeGraphServletIT extends BaseServletTester {
    private static final String DIR = "./target/andorra-gh/";

    @AfterClass
    public static void cleanUp() {
        Helper.removeDir(new File(DIR));
        shutdownJetty(true);
    }

    @Before
    public void setUp() {
        CmdArgs args = new CmdArgs().
                put(Parameters.CH.PREPARE + "weightings", "no").
                put("graph.flag_encoders", "car").
                put("graph.location", DIR).
                put("datareader.file", "../core/files/andorra.osm.pbf");
        setUpJetty(args);
    }

    @Test
    public void testBlockAccessViaPoint() throws Exception {
        final ObjectMapper objectMapper = new ObjectMapper();
        JsonNode json = query("point=42.531453,1.518946&point=42.511178,1.54006", 200);
        JsonNode infoJson = json.get("info");
        assertFalse(infoJson.has("errors"));
        JsonNode path = json.get("paths").get(0);
        // System.out.println("\n\n1\n" + path);
        double distance = path.get("distance").asDouble();
        assertTrue("distance wasn't correct:" + distance, distance > 3000);
        assertTrue("distance wasn't correct:" + distance, distance < 3500);

        // block road
        String geoJson = "{"
                + "'type': 'FeatureCollection',"
                + "'features': [{"
                + "  'type': 'Feature',"
                + "  'geometry': {"
                + "    'type': 'Point',"
                + "    'coordinates': [1.521692, 42.522969]"
                + "  },"
                + "  'properties': {"
                + "    'vehicles': ['car'],"
                + "    'access': false"
                + "  }}]}".replaceAll("'", "\"");
        String res = post("/change", 200, geoJson);
        JsonNode jsonObj = objectMapper.readTree(res);
        assertEquals(1, jsonObj.get("updates").asInt());

        // route around blocked road => longer
        json = query("point=42.531453,1.518946&point=42.511178,1.54006", 200);
        infoJson = json.get("info");
        assertFalse(infoJson.has("errors"));
        path = json.get("paths").get(0);

        // System.out.println("\n\n2\n" + path);

        distance = path.get("distance").asDouble();
        assertTrue("distance wasn't correct:" + distance, distance > 5300);
        assertTrue("distance wasn't correct:" + distance, distance < 5800);
    }
}
