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
import com.graphhopper.config.ProfileConfig;
import com.graphhopper.http.GraphHopperApplication;
import com.graphhopper.http.GraphHopperServerConfiguration;
import com.graphhopper.http.util.GraphHopperServerTestConfiguration;
import com.graphhopper.util.Helper;
import io.dropwizard.testing.junit5.DropwizardAppExtension;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import javax.ws.rs.core.Response;
import java.io.File;
import java.util.Arrays;
import java.util.List;

import static com.graphhopper.http.util.TestUtils.clientTarget;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(DropwizardExtensionsSupport.class)
public class SPTResourceTest {
    private static final String DIR = "./target/spt-gh/";
    private static final DropwizardAppExtension<GraphHopperServerConfiguration> app = new DropwizardAppExtension<>(GraphHopperApplication.class, createConfig());

    private static GraphHopperServerConfiguration createConfig() {
        GraphHopperServerTestConfiguration config = new GraphHopperServerTestConfiguration();
        config.getGraphHopperConfiguration().
                putObject("graph.flag_encoders", "car|turn_costs=true").
                putObject("graph.encoded_values", "max_speed,road_class").
                putObject("datareader.file", "../core/files/andorra.osm.pbf").
                putObject("graph.location", DIR).
                setProfiles(Arrays.asList(
                        new ProfileConfig("car_without_turncosts").setVehicle("car").setWeighting("fastest"),
                        new ProfileConfig("car_with_turncosts").setVehicle("car").setWeighting("fastest").setTurnCosts(true)
                ));
        return config;
    }

    @BeforeAll
    @AfterAll
    public static void cleanUp() {
        Helper.removeDir(new File(DIR));
    }

    @Test
    public void requestSPT() {
        Response rsp = clientTarget(app, "/spt?profile=car_without_turncosts&point=42.531073,1.573792&time_limit=300").request().buildGet().invoke();
        String rspCsvString = rsp.readEntity(String.class);
        String[] lines = rspCsvString.split("\n");
        assertTrue(lines.length > 500);
        List<String> headers = Arrays.asList(lines[0].split(","));
        assertEquals("[longitude, latitude, time, distance]", headers.toString());
        String[] row = lines[166].split(",");
        assertEquals(1.5552, Double.parseDouble(row[0]), 0.0001);
        assertEquals(42.5179, Double.parseDouble(row[1]), 0.0001);
        assertEquals(118, Integer.parseInt(row[2]) / 1000, 1);
        assertEquals(2263, Integer.parseInt(row[3]), 1);

        rsp = clientTarget(app, "/spt?profile=car_without_turncosts&point=42.531073,1.573792&columns=prev_time").request().buildGet().invoke();
        rspCsvString = rsp.readEntity(String.class);
        lines = rspCsvString.split("\n");
        assertTrue(lines.length > 500);
        headers = Arrays.asList(lines[0].split(","));
        int prevTimeIndex = headers.indexOf("prev_time");
        assertNotEquals(-1, prevTimeIndex);

        row = lines[20].split(",");
        assertEquals(41, Integer.parseInt(row[prevTimeIndex]) / 1000);
    }

    @Test
    public void requestSPTEdgeBased() {
        Response rsp = clientTarget(app, "/spt?profile=car_with_turncosts&point=42.531073,1.573792&time_limit=300&columns=prev_node_id,edge_id,node_id,time,distance").request().buildGet().invoke();
        String rspCsvString = rsp.readEntity(String.class);
        String[] lines = rspCsvString.split("\n");
        assertTrue(lines.length > 500);
        assertEquals("prev_node_id,edge_id,node_id,time,distance", lines[0]);
        assertEquals("-1,-1,1898,0,0", lines[1]);
        assertEquals("1898,2274,1324,3817,74", lines[2]);
        assertEquals("1898,2272,263,13496,262", lines[3]);
    }

    @Test
    public void requestDetails() {
        Response rsp = clientTarget(app, "/spt?profile=car_without_turncosts&point=42.531073,1.573792&time_limit=300&columns=street_name,road_class,max_speed").request().buildGet().invoke();
        String rspCsvString = rsp.readEntity(String.class);
        String[] lines = rspCsvString.split("\n");
        assertTrue(lines.length > 500);

        String[] row = lines[368].split(",");
        assertEquals("", row[0]);
        assertEquals("service", row[1]);
        assertEquals(20, Double.parseDouble(row[2]), .1);

        row = lines[249].split(",");
        assertEquals("Carretera d'Engolasters CS-200", row[0]);
        assertEquals("secondary", row[1]);
        assertTrue(Double.isInfinite(Double.parseDouble(row[2])));
    }

    @Test
    public void missingPoint() {
        Response rsp = clientTarget(app, "/spt").request().buildGet().invoke();
        assertEquals(400, rsp.getStatus());
        JsonNode json = rsp.readEntity(JsonNode.class);
        assertTrue(json.get("message").toString().contains("query param point must not be null"), json.toString());
    }

    @Test
    public void profileWithLegacyParametersNotAllowed() {
        assertNotAllowed("&profile=fast_car&weighting=fastest", "Since you are using the 'profile' parameter, do not use the 'weighting' parameter. You used 'weighting=fastest'");
        assertNotAllowed("&profile=fast_car&vehicle=car", "Since you are using the 'profile' parameter, do not use the 'vehicle' parameter. You used 'vehicle=car'");
    }

    private void assertNotAllowed(String hint, String error) {
        Response rsp = clientTarget(app, "/spt?point=42.531073,1.573792&time_limit=300&columns=street_name,road_class,max_speed" + hint).request().buildGet().invoke();
        assertEquals(400, rsp.getStatus());
        JsonNode json = rsp.readEntity(JsonNode.class);
        assertTrue(json.get("message").toString().contains(error), json.toString());
    }
}