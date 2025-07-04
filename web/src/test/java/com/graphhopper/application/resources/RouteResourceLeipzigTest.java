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
import com.graphhopper.config.CHProfile;
import com.graphhopper.routing.TestProfiles;
import com.graphhopper.util.BodyAndStatus;
import com.graphhopper.util.Helper;
import io.dropwizard.testing.junit5.DropwizardAppExtension;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static com.graphhopper.application.resources.Util.getWithStatus;
import static com.graphhopper.application.util.TestUtils.clientTarget;
import static com.graphhopper.util.Parameters.Algorithms.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(DropwizardExtensionsSupport.class)
public class RouteResourceLeipzigTest {
    private static final String DIR = "./target/route-resource-leipzig-gh/";
    private static final DropwizardAppExtension<GraphHopperServerConfiguration> app = new DropwizardAppExtension<>(GraphHopperApplication.class, createConfig());

    private static GraphHopperServerConfiguration createConfig() {
        GraphHopperServerConfiguration config = new GraphHopperServerTestConfiguration();
        config.getGraphHopperConfiguration().
                putObject("prepare.min_network_size", 200).
                putObject("datareader.file", "../map-matching/files/leipzig_germany.osm.pbf").
                putObject("import.osm.ignored_highways", "").
                putObject("graph.location", DIR).
                putObject("graph.encoded_values", "car_access, car_average_speed").
                setProfiles(List.of(TestProfiles.accessAndSpeed("my_car", "car"))).
                setCHProfiles(Collections.singletonList(new CHProfile("my_car")));
        return config;
    }

    @BeforeAll
    @AfterAll
    public static void cleanUp() {
        Helper.removeDir(new File(DIR));
    }

    @Test
    void testNoErrors() {
        // we just send a bunch of routing requests to make sure there are no internal server errors
        queryRandomRoutes(100, 51.319685, 51.367294, 12.335525, 12.434745);
        // repeat the same for a very small bounding box to better cover cases where the query points are close together
        queryRandomRoutes(1000, 51.342534, 51.34285, 12.384917, 12.385278);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "91,-1,algorithm=" + DIJKSTRA_BI,
            "107,-1,algorithm=" + ASTAR_BI,
            "30868,1,ch.disable=true&algorithm=" + DIJKSTRA,
            "21181,1,ch.disable=true&algorithm=" + ASTAR,
            "14854,1,ch.disable=true&algorithm=" + DIJKSTRA_BI,
            "10538,1,ch.disable=true&algorithm=" + ASTAR_BI
    })
    void testTimeout(int expectedVisitedNodes, int timeout, String args) {
        {
            // for a long timeout the route calculation works
            long longTimeout = 10_000;
            JsonNode jsonNode = clientTarget(app, "/route?timeout_ms=" + longTimeout + "&profile=my_car&point=51.319685,12.335525&point=51.367294,12.434745&" + args).request().get(JsonNode.class);
            // by checking the visited nodes we make sure different algorithms are used
            assertEquals(expectedVisitedNodes, jsonNode.get("hints").get("visited_nodes.sum").asInt());
        }
        {
            // for a short timeout the route calculation fails, for CH we need to use a negative number, because it is too fast
            BodyAndStatus response = getWithStatus(clientTarget(app, "/route?timeout_ms=" + timeout + "&profile=my_car&point=51.319685,12.335525&point=51.367294,12.434745&" + args));
            assertEquals(400, response.getStatus());
            JsonNode jsonNode = response.getBody();
            assertTrue(jsonNode.get("message").asText().contains("Connection between locations not found"), jsonNode.get("message").asText());
        }
    }

    private static void queryRandomRoutes(int numQueries, double minLat, double maxLat, double minLon, double maxLon) {
        final long seed = System.nanoTime();
        Random rnd = new Random(seed);
        for (int i = 0; i < numQueries; i++) {
            double latFrom = minLat + rnd.nextDouble() * (maxLat - minLat);
            double lonFrom = minLon + rnd.nextDouble() * (maxLon - minLon);
            double latTo = minLat + rnd.nextDouble() * (maxLat - minLat);
            double lonTo = minLon + rnd.nextDouble() * (maxLon - minLon);
            JsonNode json = clientTarget(app, "/route?profile=my_car&" +
                    "point=" + latFrom + "," + lonFrom + "&point=" + latTo + "," + lonTo).request().get(JsonNode.class);
            JsonNode path = json.get("paths").get(0);
            assertTrue(path.get("distance").asDouble() >= 0);
        }
    }
}
