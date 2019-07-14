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
package com.graphhopper.http.resources;

import com.fasterxml.jackson.databind.JsonNode;
import com.graphhopper.http.GraphHopperApplication;
import com.graphhopper.http.GraphHopperServerConfiguration;
import com.graphhopper.util.CmdArgs;
import com.graphhopper.util.Helper;
import io.dropwizard.testing.junit.DropwizardAppRule;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.File;

import static org.junit.Assert.*;

/**
 * @author Peter Karich
 */
public class FlexResourceTest {
    private static final String DIR = "./target/andorra-gh/";
    private static final GraphHopperServerConfiguration config = new GraphHopperServerConfiguration();

    static {
        config.getGraphHopperConfiguration().merge(new CmdArgs().
                put("graph.flag_encoders", "car").
                put("prepare.ch.weightings", "no").
                put("routing.scripting", "true").
                put("prepare.min_network_size", "0").
                put("prepare.min_one_way_network_size", "0").
                put("datareader.file", "../core/files/andorra.osm.pbf").
                put("graph.encoded_values", "road_class,surface,road_environment,max_speed").
                put("graph.location", DIR));
    }

    @ClassRule
    public static final DropwizardAppRule<GraphHopperServerConfiguration> app = new DropwizardAppRule<>(GraphHopperApplication.class, config);

    @BeforeClass
    @AfterClass
    public static void cleanUp() {
        Helper.removeDir(new File(DIR));
    }

    @Test
    public void testBasicQuery() {
        String jsonQuery = "{" +
                "\"request\": {" +
                " \"points\": [[1.518946,42.531453],[1.54006,42.511178]] }," +
                " \"model\": { \"base\": \"car\" }" +
                "}";
        final Response response = app.client().target("http://localhost:8080/flex").request().post(Entity.json(jsonQuery));
        assertEquals(200, response.getStatus());
        JsonNode json = response.readEntity(JsonNode.class);
        JsonNode infoJson = json.get("info");
        assertFalse(infoJson.has("errors"));
        JsonNode path = json.get("paths").get(0);
        assertBetween("distance wasn't correct", path.get("distance").asDouble(), 3100, 3300);
    }

    @Test
    public void testYamlQuery() {
        String yamlQuery = "request:\n" +
                "  points: [[1.518946,42.531453],[1.54006,42.511178]]\n" +
                "model:\n" +
                "  base: car\n";
        JsonNode yamlNode = queryYaml(yamlQuery, 200).readEntity(JsonNode.class);
        JsonNode infoElement = yamlNode.get("info");
        assertFalse(infoElement.has("errors"));
        JsonNode path = yamlNode.get("paths").get(0);
        assertBetween("distance wasn't correct", path.get("distance").asDouble(), 3100, 3300);
    }

    @Test
    public void testCustomWeight() {
        String yamlQuery = "request:\n" +
                "  points: [[1.518946,42.531453],[1.54006,42.511178]]\n" +
                "model:\n" +
                "  base: car\n" +
                "  factor:\n" +
                "    road_class:\n" +
                "      secondary: 5\n";
        JsonNode yamlNode = queryYaml(yamlQuery, 200).readEntity(JsonNode.class);
        JsonNode path = yamlNode.get("paths").get(0);
        assertBetween("distance wasn't correct", path.get("distance").asDouble(), 3400, 3600);
    }

    static void assertBetween(String msg, double val, double from, double to) {
        assertTrue(msg + " :" + val, val > from);
        assertTrue(msg + " :" + val, val < to);
    }

    Response queryYaml(String yamlStr, int code) {
        Response response = app.client().target("http://localhost:8080/flex").request().post(Entity.entity(yamlStr, new MediaType("application", "x-yaml")));
        assertEquals(code, response.getStatus());
        return response;
    }
}
