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

import static com.graphhopper.http.BaseServletTester.shutdownJetty;
import com.graphhopper.util.CmdArgs;
import com.graphhopper.util.Helper;
import java.io.File;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * @author svantulden
 */
public class NearestServletIT extends BaseServletTester
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
    public void testBasicNearestQuery() throws Exception
    {
        JSONObject json = nearestQuery("point=42.554851,1.536198");
        assertFalse(json.has("error"));
        JSONArray point = json.getJSONArray("coordinates");
        assertTrue("returned point is not 2D: " + point, point.length() == 2);
        double lon = point.getDouble(0);
        double lat = point.getDouble(1);
        assertTrue("nearest point wasn't correct: lat=" + lat + ", lon=" + lon, lat == 42.55483907636756 && lon == 1.5363742288086868);
    }
}
