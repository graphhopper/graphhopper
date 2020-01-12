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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;

import javax.ws.rs.core.Response;

import com.graphhopper.GHResponse;
import com.graphhopper.http.GraphHopperApplication;
import com.graphhopper.http.GraphHopperServerConfiguration;
import com.graphhopper.util.CmdArgs;
import com.graphhopper.util.Helper;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import io.dropwizard.testing.junit.DropwizardAppRule;

public class GtfsWalkingHopBetweenTransitsTest {
    private static final String DIR = "./target/gtfs_pei_simple_cache2/";

    private static final GraphHopperServerConfiguration config = new GraphHopperServerConfiguration();

    static {
        config.getGraphHopperConfiguration().merge(new CmdArgs().
                put("graph.flag_encoders", "foot").
                put("datareader.file", "../core/files/prince-edward-island-latest.osm.pbf").
                put("gtfs.file", "../core/files/simple-bus-rail-with-transfers-txt.zip").
                put("graph.location", DIR).
                put("prepare.ch.weightings", "no").
                put("outing.max_visited_nodes", "1000000"));
    }

    @ClassRule
    public static final DropwizardAppRule<GraphHopperServerConfiguration> app = new DropwizardAppRule<>(GraphHopperApplication.class, config);

    @BeforeClass
    @AfterClass
    public static void cleanUp() {
        Helper.removeDir(new File(DIR));
    }

    @Test
    public void testTwoDisconnectTransitOver40kmTripQuery() {
        final Response response = app.client().target("http://localhost:8080/route")
                .queryParam("point","46.436038,-63.639194") //SB1
                .queryParam("point","46.273173,-63.153361") //SR2
                .queryParam("vehicle", "pt")
                .queryParam("pt.earliest_departure_time", "2020-01-06T11:45:00Z")
                .request().buildGet().invoke();
        assertEquals(200, response.getStatus());
        GHResponse ghResponse = response.readEntity(GHResponse.class);
        assertFalse(ghResponse.hasErrors());
        //The walking distance between SB2 and SR1 stops is about 350 meters, so one solution should have a
        //walking distance less then 1 kilometer.
        assertTrue(ghResponse.getAll().stream().anyMatch( t -> t.getDistance() < 1000));
    }
}
