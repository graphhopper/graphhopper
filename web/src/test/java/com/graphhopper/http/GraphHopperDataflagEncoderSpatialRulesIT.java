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
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.*;

/**
 * Tests the DataFlagencoder with the SpatialRuleLookup enabled
 *
 * @author Robin Boldt
 */
public class GraphHopperDataflagEncoderSpatialRulesIT extends BaseServletTester {
    private static final String DIR = "./target/north-bayreuth-gh/";

    @AfterClass
    public static void cleanUp() {
        Helper.removeDir(new File(DIR));
        shutdownJetty(true);
    }

    @Before
    public void setUp() {
        CmdArgs args = new CmdArgs().
                put("config", "../config-example.properties").
                put("graph.flag_encoders", "generic").
                put("prepare.ch.weightings", "no").
                put("spatial_rules.location", "../core/files/spatialrules/countries.geo.json").
                put("spatial_rules.max_bbox", "11.4,11.7,49.9,50.1").
                put("datareader.file", "../core/files/north-bayreuth.osm.gz").
                put("graph.location", DIR);
        setUpJetty(args);
    }

    @Test
    public void testDetourToComplyWithSpatialRule() throws Exception {
        JsonNode json = query("point=49.995933,11.54809&point=50.004871,11.517191&vehicle=generic", 200);
        JsonNode infoJson = json.get("info");
        assertFalse(infoJson.has("errors"));
        JsonNode path = json.get("paths").get(0);
        double distance = path.get("distance").asDouble();
        // Makes sure that SpatialRules are enforced. Without SpatialRules we take a shortcut trough the forest
        // so the route would be only 3.31km
        assertTrue("distance wasn't correct:" + distance, distance > 7000);
        assertTrue("distance wasn't correct:" + distance, distance < 7500);
    }

}
