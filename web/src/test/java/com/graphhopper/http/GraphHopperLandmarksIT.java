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
import com.graphhopper.GraphHopper;
import com.graphhopper.util.CmdArgs;
import com.graphhopper.util.Helper;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests the creation of Landmarks and parsing the map.geo.json file
 *
 * @author Robin Boldt
 */
public class GraphHopperLandmarksIT extends BaseServletTester {
    private static final String DIR = "./target/landmark-test-gh/";

    @AfterClass
    public static void cleanUp() {
        Helper.removeDir(new File(DIR));
        shutdownJetty(true);
    }

    @Before
    public void setUp() {
        CmdArgs args = new CmdArgs().
                put("config", "../config-example.properties").
                put("prepare.ch.weightings", "fastest").
                put("prepare.lm.weightings", "fastest").
                put("datareader.file", "../core/files/belarus-east.osm.gz").
                put("prepare.min_network_size", 0).
                put("prepare.min_one_way_network_size", 0).
                put("routing.ch.disabling_allowed", true).
                put("graph.location", DIR);

        // force landmark creation even for tiny networks:
        args.put("prepare.lm.min_network_size", 2);

        setUpJetty(args);
    }

    @Test
    public void testQueries() throws Exception {
        JsonNode json = query("point=55.99022,29.129734&point=56.001069,29.150848", 200);
        JsonNode path = json.get("paths").get(0);
        double distance = path.get("distance").asDouble();
        assertEquals("distance wasn't correct:" + distance, 1870, distance, 100);

        json = query("point=55.99022,29.129734&point=56.001069,29.150848&ch.disable=true", 200);
        distance = json.get("paths").get(0).get("distance").asDouble();
        assertEquals("distance wasn't correct:" + distance, 1870, distance, 100);

        GraphHopper hopper = getInstance(GraphHopper.class);
        hopper.getLMFactoryDecorator().setDisablingAllowed(true);
        json = query("point=55.99022,29.129734&point=56.001069,29.150848&ch.disable=true&lm.disable=true", 200);
        distance = json.get("paths").get(0).get("distance").asDouble();
        assertEquals("distance wasn't correct:" + distance, 1870, distance, 100);
        hopper.getLMFactoryDecorator().setDisablingAllowed(false);
    }

    @Test
    public void testLandmarkDisconnect() throws Exception {
        // if one algorithm is disabled then the following chain is executed: CH -> LM -> flexible
        // disconnected for landmarks
        JsonNode json = query("point=55.99022,29.129734&point=56.007787,29.208355&ch.disable=true", 400);
        JsonNode errorJson = json.get("message");
        assertTrue(errorJson.toString(), errorJson.toString().contains("Different subnetworks"));

        // without landmarks it should work
        GraphHopper hopper = getInstance(GraphHopper.class);
        hopper.getLMFactoryDecorator().setDisablingAllowed(true);
        json = query("point=55.99022,29.129734&point=56.007787,29.208355&ch.disable=true&lm.disable=true", 200);
        JsonNode path = json.get("paths").get(0);
        double distance = path.get("distance").asDouble();
        assertEquals("distance wasn't correct:" + distance, 5790, distance, 100);
        hopper.getLMFactoryDecorator().setDisablingAllowed(false);
    }
}