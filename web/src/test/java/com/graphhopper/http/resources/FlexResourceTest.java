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
import com.graphhopper.util.Parameters;
import io.dropwizard.testing.junit.DropwizardAppRule;
import org.junit.AfterClass;
import org.junit.ClassRule;
import org.junit.Test;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Response;
import java.io.File;

import static org.junit.Assert.*;

/**
 * @author Peter Karich
 */
public class FlexResourceTest {
    private static final String dir = "./target/monaco-gh/";

    private static final GraphHopperServerConfiguration config = new GraphHopperServerConfiguration();

    static {
        config.getGraphHopperConfiguration().merge(new CmdArgs().
                put(Parameters.CH.PREPARE + "weightings", "no").
                put("prepare.min_one_way_network_size", "0").
                put("prepare.ch.weightings", "no").
                put("graph.flag_encoders", "foot").
                put("graph.encoding_manager", FlexResourceTest.class.getResource("fire_truck.yml").getPath()).
                put("datareader.file", "../core/files/monaco.osm.gz").
                put("graph.location", dir));
    }

    @ClassRule
    public static final DropwizardAppRule<GraphHopperServerConfiguration> app = new DropwizardAppRule(
            GraphHopperApplication.class, config);

    @AfterClass
    public static void cleanUp() {
        Helper.removeDir(new File(dir));
    }

    @Test
    public void testFireTruck() {
        final Response response = app.client().target("http://localhost:8080/route?point=43.740523,7.425524&point=43.744685,7.430556&vehicle=fire_truck&weighting=fire_truck").
                request().get();
        JsonNode json = response.readEntity(JsonNode.class);
        assertEquals(200, response.getStatus());
        JsonNode infoJson = json.get("info");
        assertFalse(infoJson.has("errors"));
        JsonNode path = json.get("paths").get(0);
        double distance = path.get("distance").asDouble();
        // avoid smaller roads
        assertTrue("distance isn't correct:" + distance, distance > 2000);
        assertTrue("distance isn't correct:" + distance, distance < 2500);
    }

    @Test
    public void testWheelchairBasics() {
        Response response = app.client().target("http://localhost:8080/flex").request().post(Entity.json("{"
                + "\"request\": { \"points\":[[7.421447, 43.731681], [7.419602,43.73224]] },"
                + "\"model\":   { \"base\":\"foot\", \"max_speed\": 5.0 }"
                + "}"
        ));

        JsonNode json = response.readEntity(JsonNode.class);
        assertEquals(200, response.getStatus());
        JsonNode infoJson = json.get("info");
        assertFalse(infoJson.has("errors"));
        JsonNode path = json.get("paths").get(0);
        double distance = path.get("distance").asDouble();
        assertTrue("distance isn't correct:" + distance, distance > 100);
        assertTrue("distance isn't correct:" + distance, distance < 200);

        // TODO use wheelchair.yml directly
        response = app.client().target("http://localhost:8080/flex").request().buildPost(Entity.json("{"
                + "\"request\": { \"points\":[[7.421447, 43.731681], [7.419602,43.73224]] }, "
                + "\"model\":   { \"base\":\"foot\", \"max_speed\": 5.0, \"no_access\": { \"road_class\": [\"steps\"]} } "
                + "}"
        )).invoke();
        json = response.readEntity(JsonNode.class);
        assertEquals(200, response.getStatus());
        path = json.get("paths").get(0);
        distance = path.get("distance").asDouble();
        // now without steps!
        assertTrue("distance isn't correct:" + distance, distance > 1000);
        assertTrue("distance isn't correct:" + distance, distance < 1500);

        // TODO write json parsing error directly in response? Could be a security problem if too much information is provided
    }

    @Test
    public void testScript() {
        Response response = app.client().target("http://localhost:8080/flex").request().post(Entity.entity(
                "request:\n"
                        + " points: [[7.421447,43.731681],[7.419602,43.73224]]\n"
                        + "model:\n"
                        + " base: foot\n"
                        + " max_speed: 50.0\n"
                        + " script: '(edge.get(road_class) == RoadClass.STEPS) ? 10 : 1'\n",
                "text/x-yaml"
        ));

        JsonNode json = response.readEntity(JsonNode.class);
        assertEquals(200, response.getStatus());
        JsonNode infoJson = json.get("info");
        assertFalse(infoJson.has("errors"));
        JsonNode path = json.get("paths").get(0);
        double distance = path.get("distance").asDouble();
        assertTrue("distance isn't correct:" + distance, distance > 470);
        assertTrue("distance isn't correct:" + distance, distance < 490);
    }

    @Test
    public void testMissingCharErrorScript() {
        Response response = app.client().target("http://localhost:8080/flex").request().post(Entity.entity(
                "request:\n"
                        + " points: [[7.421447,43.731681],[7.419602,43.73224]]\n"
                        + "model:\n"
                        + " base: foot\n"
                        + " max_speed: 50.0\n"
                        + " script: (edge.get(road_class) == RoadClass.STEPS) ? 1.1 : 1\n",
                "text/x-yaml"
        ));

        JsonNode json = response.readEntity(JsonNode.class);
        assertTrue("error message is wrong " + json.get("message").toString(), json.get("message").toString().contains("mapping values are not allowed here"));
    }

    @Test
    public void testMultiCommandScript() {
        Response response = app.client().target("http://localhost:8080/flex").request().post(Entity.entity(
                "request:\n"
                        + " points: [[7.421447,43.731681],[7.419602,43.73224]]\n"
                        + "model:\n"
                        + " base: foot\n"
                        + " max_speed: 50.0\n"
                        + " script: 'Enum tmp = edge.get(road_class); return (tmp == RoadClass.STEPS)? 1.5 : 1.0;'\n",
                "text/x-yaml"
        ));

        JsonNode json = response.readEntity(JsonNode.class);
        assertEquals(200, response.getStatus());
        JsonNode infoJson = json.get("info");
        assertFalse(infoJson.has("errors"));
        JsonNode path = json.get("paths").get(0);
        double distance = path.get("distance").asDouble();
        assertTrue("distance isn't correct:" + distance, distance > 150);
        assertTrue("distance isn't correct:" + distance, distance < 170);
    }
}
