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
import com.graphhopper.config.Profile;
import com.graphhopper.util.Helper;
import io.dropwizard.testing.junit5.DropwizardAppExtension;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import javax.ws.rs.core.Response;
import java.io.File;
import java.util.Collections;
import java.util.Random;

import static com.graphhopper.application.util.TestUtils.clientTarget;
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
                putObject("graph.location", DIR)
                .setProfiles(Collections.singletonList(new Profile("my_car").setVehicle("car").setWeighting("fastest")))
                .setCHProfiles(Collections.singletonList(new CHProfile("my_car")));
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
        double minLat = 51.319685;
        double maxLat = 51.367294;
        double minLon = 12.335525;
        double maxLon = 12.434745;
        final long seed = System.nanoTime();
//        final long seed = 1342503266951775l; // todo: with this one the strict waypoint check failed
        Random rnd = new Random(seed);
        for (int i = 0; i < 1000; i++) {
            double latFrom = minLat + rnd.nextDouble() * (maxLat - minLat);
            double lonFrom = minLon + rnd.nextDouble() * (maxLon - minLon);
            double latTo = minLat + rnd.nextDouble() * (maxLat - minLat);
            double lonTo = minLon + rnd.nextDouble() * (maxLon - minLon);
            final Response response = clientTarget(app, "/route?profile=my_car&" +
                    "point=" + latFrom + "," + lonFrom + "&point=" + latTo + "," + lonTo).request().buildGet().invoke();
            assertEquals(200, response.getStatus());
            JsonNode path = response.readEntity(JsonNode.class).get("paths").get(0);
            assertTrue(path.get("distance").asDouble() >= 0);
        }
    }
}
