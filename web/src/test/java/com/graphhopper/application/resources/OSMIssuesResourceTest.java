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

import static com.graphhopper.application.util.TestUtils.clientTarget;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(DropwizardExtensionsSupport.class)
public class OSMIssuesResourceTest {
    private static final String DIR = "./target/andorra-issues-gh/";
    private static final DropwizardAppExtension<GraphHopperServerConfiguration> app = new DropwizardAppExtension<>(GraphHopperApplication.class, createConfig());

    private static GraphHopperServerConfiguration createConfig() {
        GraphHopperServerConfiguration config = new GraphHopperServerTestConfiguration();
        config.getGraphHopperConfiguration().
                putObject("graph.encoded_values", "road_class,road_environment,max_height,max_weight,osm_way_id").
                putObject("prepare.min_network_size", 0).
                putObject("datareader.file", "../core/files/andorra.osm.pbf").
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
    public void testIssuesInAndorraLaVella() {
        JsonNode json = query("bbox=1.50,42.50,1.55,42.53");
        assertEquals("FeatureCollection", json.get("type").asText());
        JsonNode features = json.get("features");
        // "Avinguda Doctor Mitjavila" passes below the bridge "Vial de la Unio" and has no maxheight tag
        JsonNode maxHeight = null;
        for (JsonNode f : features)
            if (f.get("properties").get("way_id").asLong() == 24362802) maxHeight = f.get("properties");
        assertNotNull(maxHeight, "no issue for way 24362802 in " + features);
        assertEquals("missing_maxheight", maxHeight.get("type").asText());
        // the way that needs the tag comes first, the bridge above it second
        assertEquals("Avinguda Doctor Mitjavila", maxHeight.get("way_name").asText());
        assertEquals(208585098, maxHeight.get("other_way_id").asLong());
        assertEquals("Vial de la Unio", maxHeight.get("other_way_name").asText().replace("\u00f2", "o"));
        // scored even without elevation: an unknown clearance does not count, but the road and the
        // bridge do
        assertTrue(maxHeight.get("p_sign").asDouble() > 0, maxHeight.toString());
        assertEquals("primary", maxHeight.get("over").asText());
    }

    @Test
    public void testTunnelsAreScoredToo() {
        JsonNode json = query("bbox=1.40,42.42,1.80,42.66&types=missing_maxheight_tunnel");
        assertTrue(json.get("features").size() > 5, "" + json.get("features").size());
        double last = 1;
        for (JsonNode f : json.get("features")) {
            JsonNode p = f.get("properties");
            assertEquals("missing_maxheight_tunnel", p.get("type").asText());
            assertTrue(List.of("tunnel", "covered").contains(p.get("over").asText()), p.toString());
            assertNull(p.get("clearance"));
            assertNull(p.get("neighbours"));
            // no clearance and no neighbours, still ranked by the road, the country and the tunnel
            double pSign = p.get("p_sign").asDouble();
            assertTrue(pSign > 0 && pSign <= last, p.toString());
            last = pSign;
        }
    }

    @Test
    public void testMaxWeightAlsoForBridgesOverRivers() {
        // a bridge needs a max_weight even if it does not cross another road
        JsonNode json = query("bbox=1.50,42.50,1.55,42.53&types=missing_maxweight");
        assertTrue(json.get("features").size() > 5, "" + json.get("features").size());
        for (JsonNode f : json.get("features")) {
            assertEquals("missing_maxweight", f.get("properties").get("type").asText());
            assertNull(f.get("properties").get("other_way_id"));
        }
    }

    @Test
    public void testRoadClassFilter() {
        // maxheight has cases in several road classes here
        String bbox = "bbox=1.50,42.50,1.55,42.53&types=missing_maxheight";
        int all = query(bbox).get("features").size();

        // the parameter takes the road class names, so the UI decides how to group them
        JsonNode some = query(bbox + "&roads=primary,residential");
        assertTrue(some.get("features").size() < all, some.get("features").size() + " vs " + all);
        assertTrue(some.get("features").size() > 0);
        for (JsonNode f : some.get("features"))
            assertTrue(List.of("primary", "residential").contains(f.get("properties").get("road_class").asText()),
                    f.toString());

        // unknown names simply match nothing, "all" and an empty value match everything
        assertEquals(0, query(bbox + "&roads=does_not_exist").get("features").size());
        assertEquals(all, query(bbox + "&roads=all").get("features").size());
    }

    @Test
    public void testTypeFilter() {
        JsonNode json = query("bbox=1.50,42.50,1.55,42.53&types=missing_bridge");
        for (JsonNode f : json.get("features"))
            assertEquals("missing_bridge", f.get("properties").get("type").asText());
    }

    @Test
    public void testInvalidBBox() {
        Response rsp = clientTarget(app, "/osm-issues?bbox=1.50,42.50").request().get();
        assertEquals(400, rsp.getStatus());
    }

    private JsonNode query(String params) {
        Response rsp = clientTarget(app, "/osm-issues?" + params).request().get();
        if (rsp.getStatus() != 200) fail("status " + rsp.getStatus() + ": " + rsp.readEntity(String.class));
        return rsp.readEntity(JsonNode.class);
    }
}
