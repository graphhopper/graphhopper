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

import com.graphhopper.GHResponse;
import com.graphhopper.http.GraphHopperApplication;
import com.graphhopper.http.util.GraphHopperServerTestConfiguration;
import com.graphhopper.resources.InfoResource;
import com.graphhopper.util.Helper;
import io.dropwizard.testing.junit.DropwizardAppRule;
import java.io.File;
import javax.ws.rs.core.Response;
import org.junit.AfterClass;
import static org.junit.Assert.*;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import static com.graphhopper.http.util.TestUtils.clientTarget;

/**
 * Similar to PtRouteResourceTest, but tests the entire app, not the resource, so that the plugging-together
 * of stuff (which is different for PT than for the rest) is under test, too.
 */
public class GtfsTest {
    private static final String DIR = "./target/gtfs-app-gh/";

    private static final GraphHopperServerTestConfiguration config = new GraphHopperServerTestConfiguration();

    static {
        config.getGraphHopperConfiguration().
                putObject("graph.flag_encoders", "foot").
                putObject("datareader.file", "../reader-gtfs/files/beatty.osm").
                putObject("gtfs.file", "../reader-gtfs/files/sample-feed.zip").
                putObject("graph.location", DIR);
    }

    @ClassRule
    public static final DropwizardAppRule<GraphHopperServerTestConfiguration> app = new DropwizardAppRule(GraphHopperApplication.class, config);

    @BeforeClass
    @AfterClass
    public static void cleanUp() {
        Helper.removeDir(new File(DIR));
    }

    @Test
    public void testStationStationQuery() {
        final Response response = clientTarget(app, "/route")
                .queryParam("point", "Stop(NADAV)")
                .queryParam("point", "Stop(NANAA)")
                .queryParam("vehicle", "pt")
                .queryParam("pt.earliest_departure_time", "2007-01-01T08:00:00Z")
                .request().buildGet().invoke();
        assertEquals(200, response.getStatus());
        GHResponse ghResponse = response.readEntity(GHResponse.class);
        assertFalse(ghResponse.hasErrors());
    }

    @Test
    public void testPointPointQuery() {
        final Response response = clientTarget(app, "/route")
                .queryParam("point", "36.914893,-116.76821") // NADAV stop
                .queryParam("point", "36.914944,-116.761472") //NANAA stop
                .queryParam("vehicle", "pt")
                .queryParam("pt.earliest_departure_time", "2007-01-01T08:00:00Z")
                .request().buildGet().invoke();
        assertEquals(200, response.getStatus());
        GHResponse ghResponse = response.readEntity(GHResponse.class);
        assertFalse(ghResponse.hasErrors());
    }

    @Test
    public void testWalkQuery() {
        final Response response = clientTarget(app, "/route")
                .queryParam("point", "36.914893,-116.76821")
                .queryParam("point", "36.914944,-116.761472")
                .queryParam("vehicle", "foot")
                .request().buildGet().invoke();
        assertEquals(200, response.getStatus());
        GHResponse ghResponse = response.readEntity(GHResponse.class);
        assertFalse(ghResponse.hasErrors());
    }

    @Test
    public void testInfo() {
        final Response response = clientTarget(app, "/info")
                .request().buildGet().invoke();
        assertEquals(200, response.getStatus());
        InfoResource.Info info = response.readEntity(InfoResource.Info.class);
        assertTrue(info.supported_vehicles.contains("pt"));
        assertTrue(info.features.containsKey("pt"));
    }

}
