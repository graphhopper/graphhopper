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
package com.graphhopper.application.resources;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.graphhopper.application.GraphHopperApplication;
import com.graphhopper.application.GraphHopperServerConfiguration;
import com.graphhopper.application.util.GraphHopperServerTestConfiguration;
import com.graphhopper.config.Profile;
import com.graphhopper.util.Helper;
import io.dropwizard.testing.junit5.DropwizardAppExtension;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.File;
import java.util.Collections;

import static com.graphhopper.application.util.TestUtils.clientTarget;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author svantulden
 */
@ExtendWith(DropwizardExtensionsSupport.class)
public class NearestResourceWithEleTest {
    private static final String dir = "./target/monaco-gh/";
    private static final DropwizardAppExtension<GraphHopperServerConfiguration> app = new DropwizardAppExtension<>(GraphHopperApplication.class, createConfig());

    private static GraphHopperServerConfiguration createConfig() {
        GraphHopperServerConfiguration config = new GraphHopperServerTestConfiguration();
        config.getGraphHopperConfiguration().
                putObject("graph.elevation.provider", "srtm").
                putObject("graph.elevation.cache_dir", "../core/files/").
                putObject("prepare.min_network_size", 0).
                putObject("graph.vehicles", "car").
                putObject("datareader.file", "../core/files/monaco.osm.gz").
                putObject("import.osm.ignored_highways", "").
                putObject("graph.location", dir).
                setProfiles(Collections.singletonList(new Profile("car").setCustomModel(Helper.createBaseModel("car"))));
        return config;
    }

    @BeforeAll
    @AfterAll
    public static void cleanUp() {
        Helper.removeDir(new File(dir));
    }

    @Test
    public void testWithEleQuery() {
        JsonNode json = clientTarget(app, "/nearest?point=43.730864,7.420771&elevation=true").request().buildGet().invoke().readEntity(JsonNode.class);
        assertFalse(json.has("error"));
        ArrayNode point = (ArrayNode) json.get("coordinates");
        assertEquals(3, point.size(), "returned point is not 3D: " + point);
        double lon = point.get(0).asDouble();
        double lat = point.get(1).asDouble();
        double ele = point.get(2).asDouble();
        assertTrue(lat == 43.73084185257864 && lon == 7.420749379140277 && ele == 59.0, "nearest point wasn't correct: lat=" + lat + ", lon=" + lon + ", ele=" + ele);
    }

    @Test
    public void testWithoutEleQuery() {
        JsonNode json = clientTarget(app, "/nearest?point=43.730864,7.420771&elevation=false").request().buildGet().invoke().readEntity(JsonNode.class);
        assertFalse(json.has("error"));
        ArrayNode point = (ArrayNode) json.get("coordinates");
        assertEquals(2, point.size(), "returned point is not 2D: " + point);
        double lon = point.get(0).asDouble();
        double lat = point.get(1).asDouble();
        assertTrue(lat == 43.73084185257864 && lon == 7.420749379140277, "nearest point wasn't correct: lat=" + lat + ", lon=" + lon);

        // Default elevation is false        
        json = clientTarget(app, "/nearest?point=43.730864,7.420771").request().buildGet().invoke().readEntity(JsonNode.class);
        assertFalse(json.has("error"));
        point = (ArrayNode) json.get("coordinates");
        assertEquals(2, point.size(), "returned point is not 2D: " + point);
        lon = point.get(0).asDouble();
        lat = point.get(1).asDouble();
        assertTrue(lat == 43.73084185257864 && lon == 7.420749379140277, "nearest point wasn't correct: lat=" + lat + ", lon=" + lon);
    }
}
