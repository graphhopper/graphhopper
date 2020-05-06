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
package com.graphhopper.matching.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.graphhopper.config.Profile;
import com.graphhopper.http.WebHelper;
import com.graphhopper.util.Helper;
import io.dropwizard.testing.junit.DropwizardAppRule;
import org.junit.AfterClass;
import org.junit.ClassRule;
import org.junit.Test;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Response;
import java.io.File;
import java.util.Collections;

import static org.junit.Assert.assertEquals;

/**
 * @author Peter Karich
 */
public class MapMatchingResourceBikeTest {

    private static final String DIR = "../target/mapmatching2test";

    private static MapMatchingServerConfiguration createConfig() {
        MapMatchingServerConfiguration config = new MapMatchingServerConfiguration();
        config.getGraphHopperConfiguration().
                putObject("graph.flag_encoders", "bike").
                putObject("datareader.file", "../map-data/leipzig_germany.osm.pbf").
                putObject("graph.location", DIR).
                setProfiles(Collections.singletonList(new Profile("fast_bike").setVehicle("bike").setWeighting("fastest")));
        return config;
    }

    @ClassRule
    public static final DropwizardAppRule<MapMatchingServerConfiguration> app = new DropwizardAppRule(MapMatchingApplication.class, createConfig());

    @AfterClass
    public static void cleanUp() {
        Helper.removeDir(new File(DIR));
    }

    @Test
    public void testGPX() {
        final Response response = app.client().target("http://localhost:8080/match?vehicle=bike")
                .request()
                .buildPost(Entity.xml(getClass().getResourceAsStream("tour2-with-loop.gpx")))
                .invoke();
        assertEquals("no success", 200, response.getStatus());
        JsonNode json = response.readEntity(JsonNode.class);
        JsonNode path = json.get("paths").get(0);

        assertEquals(5, path.get("instructions").size());
        assertEquals(5, WebHelper.decodePolyline(path.get("points").asText(), 10, false).size());
        // ensure that is actually also is bike! (slower than car)
        assertEquals(162, path.get("time").asLong() / 1000f, 1);
    }
}
