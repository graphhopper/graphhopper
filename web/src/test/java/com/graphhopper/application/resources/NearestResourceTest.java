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

import com.graphhopper.application.GraphHopperApplication;
import com.graphhopper.application.GraphHopperServerConfiguration;
import com.graphhopper.application.util.GraphHopperServerTestConfiguration;
import com.graphhopper.resources.NearestResource;
import com.graphhopper.routing.TestProfiles;
import com.graphhopper.util.Helper;
import io.dropwizard.testing.junit5.DropwizardAppExtension;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import static com.graphhopper.application.util.TestUtils.clientTarget;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author svantulden
 */
@ExtendWith(DropwizardExtensionsSupport.class)
public class NearestResourceTest {
    private static final String dir = "./target/andorra-gh/";
    private static final DropwizardAppExtension<GraphHopperServerConfiguration> app = new DropwizardAppExtension<>(GraphHopperApplication.class, createConfig());

    private static GraphHopperServerConfiguration createConfig() {
        GraphHopperServerConfiguration config = new GraphHopperServerTestConfiguration();
        config.getGraphHopperConfiguration()
                        .putObject("datareader.file", "../core/files/andorra.osm.pbf")
                        .putObject("graph.location", dir)
                        .putObject("import.osm.ignored_highways", "")
                        .putObject("graph.encoded_values",
                                        "car_access,car_average_speed,foot_access,foot_average_speed,road_class,road_environment")
                        .setProfiles(Arrays.asList(TestProfiles.accessAndSpeed("car", "car"),
                                        TestProfiles.accessAndSpeed("foot", "foot")));
        return config;
    }

    @BeforeAll
    @AfterAll
    public static void cleanUp() {
        Helper.removeDir(new File(dir));
    }

    private void testWithProfile(double lat, double lon, double[] expectedSnap, String profile, double distance) {
        String url = "/nearest?point=" + lat + "," + lon + "&profile=" + profile;
        NearestResource.Response json = clientTarget(app, url).request().get(NearestResource.Response.class);
        assertArrayEquals(expectedSnap, json.coordinates, 0.00001, "nearest point (profile " + profile + ")");
        assertEquals(distance, json.distance, 1.0, "distance from nearest point (profile " + profile + ")");
    }

    @Test
    public void testBasicNearestQuery() {
        NearestResource.Response json = clientTarget(app, "/nearest?point=42.554851,1.536198").request().get(NearestResource.Response.class);
        assertArrayEquals(new double[]{1.5363743623376815, 42.554839049600155}, json.coordinates, "nearest point");
    }

    @Test
    public void testNearestQueryWithProfile() {
        testWithProfile(42.543384, 1.5108401, new double[] { 1.51081, 42.5434317 }, "foot", 6.0);
        testWithProfile(42.543384, 1.5108401, new double[] { 1.51075, 42.5432716 }, "car", 14.0);
    }

    @Test
    public void testNearestQueryWithSnapPreventoin() {
        NearestResource.Response json = clientTarget(app,
                        "/nearest?point=42.5285314,1.5239083&profile=car&snap_prevention=tunnel")
                                        .request().get(NearestResource.Response.class);
        assertArrayEquals(new double[] { 1.5240604, 42.5289345 }, json.coordinates,
                        "nearest point");
    }
}
