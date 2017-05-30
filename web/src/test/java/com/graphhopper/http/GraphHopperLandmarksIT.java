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
import org.junit.Before;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.*;

/**
 * Tests the creation of Landmarks and parsing the map.geo.json file
 *
 * @author Robin Boldt
 */
public class GraphHopperLandmarksIT extends BaseServletTester {
    private static final String DIR = "./target/landmark-test-gh/";

    @Before
    public void setUp() {
        Helper.removeDir(new File(DIR));
    }

    @Test
    public void testSimpleQuery() throws Exception {
        CmdArgs args = new CmdArgs().
                put("config", "../config-example.properties").
                put("prepare.ch.weightings", "no").
                put("prepare.lm.weightings", "fastest").
                put("datareader.file", "../core/files/north-bayreuth.osm.gz").
                put("graph.location", DIR);
        setUpJetty(args);

        JsonNode json = query("point=49.995933,11.54809&point=50.004871,11.517191", 200);
        JsonNode infoJson = json.get("info");
        JsonNode path = json.get("paths").get(0);
        double distance = path.get("distance").asDouble();
        assertTrue("distance wasn't correct:" + distance, distance > 7000);
        assertTrue("distance wasn't correct:" + distance, distance < 7500);

        shutdownJetty(true);
    }

    @Test
    public void testLandmarkDisconnect() throws Exception {
        CmdArgs args = new CmdArgs().
                put("config", "../config-example.properties").
                put("prepare.ch.weightings", "no").
                put("prepare.lm.weightings", "fastest").
                put("datareader.file", "../core/files/belarus-east.osm.gz").
                put("prepare.min_network_size", 0).
                put("prepare.min_one_way_network_size", 0).
                put("routing.lm.disabling_allowed", true).
                put("graph.location", DIR).
                // force landmark creation even for tiny networks:
                        put("prepare.lm.min_network_size", 2);
        setUpJetty(args);

        JsonNode json = query("point=55.99022,29.129734&point=56.001069,29.150848", 200);
        JsonNode path = json.get("paths").get(0);
        double distance = path.get("distance").asDouble();
        assertEquals("distance wasn't correct:" + distance, 1870, distance, 100);

        // disconnected for landmarks
        json = query("point=55.99022,29.129734&point=56.007787,29.208355", 400);
        JsonNode errorJson = json.get("message");
        assertTrue(errorJson.toString(), errorJson.toString().contains("Different subnetworks"));

        // without lm it should work
        json = query("point=55.99022,29.129734&point=56.007787,29.208355&lm.disable=true", 200);
        path = json.get("paths").get(0);
        distance = path.get("distance").asDouble();
        assertEquals("distance wasn't correct:" + distance, 5790, distance, 100);

        shutdownJetty(true);
    }
}
