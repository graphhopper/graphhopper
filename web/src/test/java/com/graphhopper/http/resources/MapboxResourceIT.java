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
import org.junit.ClassRule;
import org.junit.Test;

import javax.ws.rs.core.Response;
import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Robin Boldt
 */
public class MapboxResourceIT {
    private static final String DIR = "./target/andorra-gh/";

    private static final GraphHopperServerConfiguration config = new GraphHopperServerConfiguration();

    static {
        config.getGraphHopperConfiguration().merge(new CmdArgs().
                put("graph.flag_encoders", "car").
                put("prepare.ch.weightings", "fastest").
                put("prepare.min_network_size", "0").
                put("prepare.min_one_way_network_size", "0").
                put("datareader.file", "../core/files/andorra.osm.pbf").
                put("graph.location", DIR));
    }

    @ClassRule
    public static final DropwizardAppRule<GraphHopperServerConfiguration> app = new DropwizardAppRule(
            GraphHopperApplication.class, config);

    @AfterClass
    public static void cleanUp() {
        Helper.removeDir(new File(DIR));
    }

    @Test
    public void testBasicQuery() {
        final Response response = app.client().target("http://localhost:8080/mapbox/directions/v5/mapbox/driving-traffic/1.536198,42.554851;1.548128,42.510071?geometries=polyline6&steps=true&roundabout_exits=true&voice_instructions=true&banner_instructions=true").request().buildGet().invoke();
        assertEquals(response.toString(), 200, response.getStatus());
        JsonNode json = response.readEntity(JsonNode.class);
        System.out.println(json);

        JsonNode route = json.get("routes").get(0);
        double routeDistance = route.get("distance").asDouble();
        assertTrue("distance wasn't correct:" + routeDistance, routeDistance > 9000);
        assertTrue("distance wasn't correct:" + routeDistance, routeDistance < 9500);

        double routeDuration = route.get("duration").asDouble();
        assertTrue("duration wasn't correct:" + routeDuration, routeDuration > 500);
        assertTrue("duration wasn't correct:" + routeDuration, routeDuration < 600);

        assertEquals("en", route.get("voiceLocale").asText());

        JsonNode leg = route.get("legs").get(0);
        assertEquals(routeDistance, leg.get("distance").asDouble(), .000001);

        JsonNode steps = leg.get("steps");
        JsonNode step = steps.get(0);
        JsonNode maneuver = step.get("maneuver");
        // Intersection coordinates should be equal to maneuver coordinates
        assertEquals(maneuver.get("location").get(0).asDouble(), step.get("intersections").get(0).get("location").get(0).asDouble(), .00001);

        assertEquals("depart", maneuver.get("type").asText());
        assertEquals("straight", maneuver.get("modifier").asText());

        assertEquals("la Callisa", step.get("name").asText());
        double instructionDistance = step.get("distance").asDouble();
        assertTrue(instructionDistance < routeDistance);

        JsonNode voiceInstructions = step.get("voiceInstructions");
        assertEquals(1, voiceInstructions.size());
        JsonNode voiceInstruction = voiceInstructions.get(0);
        assertTrue(voiceInstruction.get("distanceAlongGeometry").asDouble() < instructionDistance);
        assertEquals("turn sharp left onto la Callisa", voiceInstruction.get("announcement").asText());

        JsonNode bannerInstructions = step.get("bannerInstructions");
        assertEquals(1, bannerInstructions.size());
        JsonNode bannerInstruction = bannerInstructions.get(0).get("primary");
        assertEquals("la Callisa", bannerInstruction.get("text").asText());
        assertEquals("turn", bannerInstruction.get("type").asText());
        assertEquals("sharp left", bannerInstruction.get("modifier").asText());
        JsonNode bannerInstructionComponent = bannerInstruction.get("components");
        assertEquals("la Callisa", bannerInstructionComponent.get("text").asText());

        JsonNode waypointsJson = json.get("waypoints");
        assertEquals(2, waypointsJson.size());
        JsonNode waypointLoc = waypointsJson.get(0).get("location");
        assertEquals(1.536198, waypointLoc.get(0).asDouble(), .001);
    }

}
