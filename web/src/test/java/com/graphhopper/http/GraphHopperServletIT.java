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
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * @author Peter Karich
 */
public class GraphHopperServletIT extends BaseServletTest {

    @Before
    public void setUp() {
        setUpJetty();
    }

    @Test
    public void testBasicQuery() throws Exception {
        JSONObject json = query("point=42.554851,1.536198&point=42.510071,1.548128");
        JSONObject infoJson = json.getJSONObject("info");
        assertFalse(infoJson.has("errors"));
        double distance = json.getJSONArray("paths").getJSONObject(0).getDouble("distance");
        assertTrue("distance wasn't correct:" + distance, distance > 9000);
        assertTrue("distance wasn't correct:" + distance, distance < 9500);
    }

    @Test
    public void testGraphHopperWeb() throws Exception {
        GraphHopperAPI hopper = new GraphHopperWeb();
        assertTrue(hopper.load(getTestAPIUrl()));
        GHResponse rsp = hopper.route(new GHRequest(42.554851, 1.536198, 42.510071, 1.548128));
        assertTrue(rsp.getErrors().toString(), rsp.getErrors().isEmpty());
        assertTrue("distance wasn't correct:" + rsp.getDistance(), rsp.getDistance() > 9000);
        assertTrue("distance wasn't correct:" + rsp.getDistance(), rsp.getDistance() < 9500);
    }
}
