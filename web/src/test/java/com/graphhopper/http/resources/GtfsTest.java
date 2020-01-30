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
import com.graphhopper.http.GraphHopperServerConfiguration;
import com.graphhopper.reader.gtfs.GtfsGraphLogger;
import com.graphhopper.resources.InfoResource;
import com.graphhopper.util.CmdArgs;
import com.graphhopper.util.Helper;
import io.dropwizard.testing.junit.DropwizardAppRule;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import javax.ws.rs.core.Response;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;

import static org.junit.Assert.*;

/**
 * Similar to PtRouteResourceTest, but tests the entire app, not the resource, so that the plugging-together
 * of stuff (which is different for PT than for the rest) is under test, too.
 */
public class GtfsTest {
    private static final String DIR = "./target/gtfs-app-gh/";

    private static final GraphHopperServerConfiguration config = new GraphHopperServerConfiguration();

    static {
        config.getGraphHopperConfiguration().merge(new CmdArgs().
                put("graph.flag_encoders", "foot").
                put("datareader.file", "../reader-gtfs/files/beatty.osm").
                put("gtfs.file", "../reader-gtfs/files/sample-feed.zip").
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
    public void testStationStationQuery() {
        final Response response = app.client().target("http://localhost:8080/route")
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
        final Response response = app.client().target("http://localhost:8080/route")
                .queryParam("point","36.914893,-116.76821") // NADAV stop
                .queryParam("point","36.914944,-116.761472") //NANAA stop
                .queryParam("vehicle", "pt")
                .queryParam("pt.earliest_departure_time", "2007-01-01T08:00:00Z")
                .request().buildGet().invoke();
        assertEquals(200, response.getStatus());
        GHResponse ghResponse = response.readEntity(GHResponse.class);
        assertFalse(ghResponse.hasErrors());
    }

    @Test
    public void testWalkQuery() {
        final Response response = app.client().target("http://localhost:8080/route")
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
        final Response response = app.client().target("http://localhost:8080/info")
                .request().buildGet().invoke();
        assertEquals(200, response.getStatus());
        InfoResource.Info info = response.readEntity(InfoResource.Info.class);
        assertTrue(info.supported_vehicles.contains("pt"));
        assertTrue(info.features.containsKey("pt"));
    }


    @Test
    public void testGtfsGraphmlCreation() throws ParserConfigurationException {
        final String gtfsGraphmlFileName = "./target/gtfsTenGraph.graphml";
        File graphmlFile = new File(gtfsGraphmlFileName);
        if (graphmlFile.exists()) {
            graphmlFile.delete();
        }

        final GtfsGraphLogger graphLogger = new GtfsGraphLogger(gtfsGraphmlFileName);
        graphLogger.addNode("node1", 0, 0, GtfsGraphLogger.NodeLogType.DEPARTURE_STOP_TIME_NODE, "7:00");
        graphLogger.addNode("node2", 50, 50, GtfsGraphLogger.NodeLogType.ARRIVAL_STOP_TIME_NODE, "8:00");
        graphLogger.addEdge("HOP", "edge1", "node1", "node2");
        graphLogger.exportGraphmlToFile();

        assertTrue(graphmlFile.exists());
        graphmlFile.delete();
    }
}
