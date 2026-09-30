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
import com.graphhopper.application.GraphHopperApplication;
import com.graphhopper.application.GraphHopperServerConfiguration;
import com.graphhopper.application.util.GraphHopperServerTestConfiguration;
import com.graphhopper.routing.TestProfiles;
import com.graphhopper.util.Helper;
import io.dropwizard.testing.junit5.DropwizardAppExtension;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static com.graphhopper.application.util.TestUtils.clientTarget;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Andorra has no barrier=height_restrictor, so this one runs on the small file of the reader
 * test: three restrictor nodes, one without any maxheight, one with its own and one on a way
 * that carries the maxheight.
 */
@ExtendWith(DropwizardExtensionsSupport.class)
public class OSMIssuesRestrictorTest {
    private static final String DIR = "./target/restrictor-issues-gh/";
    private static final String BBOX = "bbox=9.3,50.9,9.5,54.1";
    private static final DropwizardAppExtension<GraphHopperServerConfiguration> app = new DropwizardAppExtension<>(GraphHopperApplication.class, createConfig());

    private static GraphHopperServerConfiguration createConfig() {
        GraphHopperServerConfiguration config = new GraphHopperServerTestConfiguration();
        config.getGraphHopperConfiguration().
                putObject("graph.encoded_values", "road_class,road_environment,max_height,max_weight,osm_way_id").
                putObject("prepare.min_network_size", 0).
                putObject("datareader.file", "../core/src/test/resources/com/graphhopper/reader/osm/test-height-restrictor.xml").
                putObject("import.osm.ignored_highways", "").
                putObject("graph.location", DIR).
                setProfiles(List.of(TestProfiles.constantSpeed("car")));
        return config;
    }

    @BeforeAll
    @AfterAll
    public static void cleanUp() {
        Helper.removeDir(new File(DIR));
    }

    @Test
    public void testRestrictorWithoutMaxHeightIsAnIssue() {
        JsonNode json = query(BBOX + "&types=missing_maxheight_restrictor");
        assertEquals(1, json.get("features").size(), json.toString());
        JsonNode f = json.get("features").get(0);
        JsonNode p = f.get("properties");
        assertEquals("missing_maxheight_restrictor", p.get("type").asText());
        assertEquals("height_restrictor", p.get("barrier").asText());
        assertEquals(15, p.get("node_id").asLong());
        assertEquals(100, p.get("way_id").asInt());
        assertNull(p.get("maxheight"));
        // the marker sits on the node itself
        assertEquals(9.45, f.get("geometry").get("coordinates").get(0).asDouble(), 1e-6);
        assertEquals(51.5, f.get("geometry").get("coordinates").get(1).asDouble(), 1e-6);
    }

    @Test
    public void testRestrictorsWithMaxHeightAreKnown() {
        // the node with its own value and the one on a tagged way count as done
        JsonNode json = query(BBOX + "&types=missing_maxheight_restrictor&tagged=true");
        Map<Long, String> byNode = new TreeMap<>();
        for (JsonNode f : json.get("features"))
            byNode.put(f.get("properties").get("node_id").asLong(), f.get("properties").get("maxheight").asText());
        assertEquals(Map.of(25L, "2.1", 35L, "3.8"), byNode);
    }

    @Test
    public void testGreenLayerShowsTheNodeValueButNotTheInheritedOne() {
        // what the map asks for its "maxheight in OSM" layer
        JsonNode json = query(BBOX + "&types=missing_maxheight,missing_maxheight_tunnel&tagged=true&roads=all");
        Map<String, String> found = new TreeMap<>();
        for (JsonNode f : json.get("features")) {
            JsonNode p = f.get("properties");
            found.put(p.has("node_id") ? "node " + p.get("node_id").asLong() : "way " + p.get("way_id").asInt(),
                    p.get("maxheight").asText());
        }
        // node 35 inherits 3.8 from way 300, that is the way's marker and not a second one
        assertEquals(Map.of("node 25", "2.1", "way 300", "3.8"), found);
    }

    @Test
    public void testRoadFilterAppliesToTheWayThroughTheNode() {
        JsonNode json = query(BBOX + "&types=missing_maxheight_restrictor&roads=motorway,trunk");
        assertEquals(0, json.get("features").size());
        json = query(BBOX + "&types=missing_maxheight_restrictor&roads=residential");
        assertEquals(1, json.get("features").size());
    }

    private JsonNode query(String params) {
        Response rsp = clientTarget(app, "/osm-issues?" + params).request().get();
        if (rsp.getStatus() != 200) fail("status " + rsp.getStatus() + ": " + rsp.readEntity(String.class));
        return rsp.readEntity(JsonNode.class);
    }
}
