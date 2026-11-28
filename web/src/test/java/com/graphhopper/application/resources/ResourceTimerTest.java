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

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.fasterxml.jackson.databind.JsonNode;
import com.graphhopper.application.GraphHopperApplication;
import com.graphhopper.application.GraphHopperServerConfiguration;
import com.graphhopper.application.util.GraphHopperServerTestConfiguration;
import com.graphhopper.resources.NearestResource;
import com.graphhopper.resources.RouteResource;
import com.graphhopper.routing.TestProfiles;
import com.graphhopper.util.Helper;
import io.dropwizard.testing.junit5.DropwizardAppExtension;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import jakarta.ws.rs.client.Entity;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.File;
import java.util.List;

import static com.graphhopper.application.util.TestUtils.clientTarget;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The resource methods are annotated with @Timed, so Dropwizard records one timer per endpoint.
 */
@ExtendWith(DropwizardExtensionsSupport.class)
public class ResourceTimerTest {
    private static final String dir = "./target/andorra-gh-timer/";
    private static final DropwizardAppExtension<GraphHopperServerConfiguration> app = new DropwizardAppExtension<>(GraphHopperApplication.class, createConfig());

    private static GraphHopperServerConfiguration createConfig() {
        GraphHopperServerConfiguration config = new GraphHopperServerTestConfiguration();
        config.getGraphHopperConfiguration().
                putObject("datareader.file", "../core/files/andorra.osm.pbf").
                putObject("graph.location", dir).
                putObject("import.osm.ignored_highways", "").
                setProfiles(List.of(TestProfiles.constantSpeed("car")));
        return config;
    }

    @BeforeAll
    @AfterAll
    public static void cleanUp() {
        Helper.removeDir(new File(dir));
    }

    private static Timer timer(Class<?> resource, String method) {
        Timer timer = app.getEnvironment().metrics().getTimers().get(MetricRegistry.name(resource, method));
        assertNotNull(timer, "no timer for " + resource.getSimpleName() + "." + method);
        return timer;
    }

    @Test
    public void testTimerPerEndpoint() {
        Timer routeGet = timer(RouteResource.class, "doGet");
        Timer routePost = timer(RouteResource.class, "doPost");
        Timer nearest = timer(NearestResource.class, "doGet");
        long routeGetCount = routeGet.getCount();
        long routePostCount = routePost.getCount();
        long nearestCount = nearest.getCount();

        clientTarget(app, "/route?profile=car&point=42.554851,1.536198&point=42.510071,1.548128").request().get(JsonNode.class);
        assertEquals(routeGetCount + 1, routeGet.getCount());
        assertEquals(routePostCount, routePost.getCount());

        String jsonStr = "{ \"profile\": \"car\", \"points\": [[1.536198,42.554851], [1.548128, 42.510071]] }";
        clientTarget(app, "/route").request().post(Entity.json(jsonStr), JsonNode.class);
        assertEquals(routeGetCount + 1, routeGet.getCount());
        assertEquals(routePostCount + 1, routePost.getCount());

        clientTarget(app, "/nearest?point=42.554851,1.536198").request().get(JsonNode.class);
        assertEquals(nearestCount + 1, nearest.getCount());
    }
}
