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
import java.util.Arrays;
import java.util.List;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class SPTResourceTest {
    private static final String DIR = "./target/spt-gh/";

    private static final GraphHopperServerConfiguration config = new GraphHopperServerConfiguration();

    static {
        config.getGraphHopperConfiguration().merge(new CmdArgs().
                put("prepare.ch.weightings", "no").
                put("graph.flag_encoders", "car").
                put("graph.encoded_values", "max_speed,road_class").
                put("datareader.file", "../core/files/andorra.osm.pbf").
                put("graph.location", DIR));
    }

    @ClassRule
    public static final DropwizardAppRule<GraphHopperServerConfiguration> app = new DropwizardAppRule<>(
            GraphHopperApplication.class, config);

    @AfterClass
    public static void cleanUp() {
        Helper.removeDir(new File(DIR));
    }

    @Test
    public void requestSPT() {
        Response rsp = app.client().target("http://localhost:8080/spt?point=42.531073,1.573792&time_limit=300").request().buildGet().invoke();
        String rspCsvString = rsp.readEntity(String.class);
        String[] lines = rspCsvString.split("\n");
        assertTrue(lines.length > 500);
        List<String> headers = Arrays.asList(lines[0].split(","));
        assertEquals("[longitude, latitude, time, distance]", headers.toString());
        String[] row = lines[1].split(",");
        assertEquals(1.5552, Double.parseDouble(row[0]), 0.0001);
        assertEquals(42.5179, Double.parseDouble(row[1]), 0.0001);
        assertEquals(118, Integer.parseInt(row[2]) / 1000, 1);
        assertEquals(2263, Integer.parseInt(row[3]), 1);

        rsp = app.client().target("http://localhost:8080/spt?point=42.531073,1.573792&columns=prev_time").request().buildGet().invoke();
        rspCsvString = rsp.readEntity(String.class);
        lines = rspCsvString.split("\n");
        assertTrue(lines.length > 500);
        headers = Arrays.asList(lines[0].split(","));
        int prevTimeIndex = headers.indexOf("prev_time");
        assertNotEquals(-1, prevTimeIndex);

        row = lines[1].split(",");
        assertEquals(115, Integer.parseInt(row[prevTimeIndex]) / 1000);
    }

    @Test
    public void requestDetails() {
        Response rsp = app.client().target("http://localhost:8080/spt?point=42.531073,1.573792&time_limit=300&columns=street_name,road_class,max_speed").request().buildGet().invoke();
        String rspCsvString = rsp.readEntity(String.class);
        String[] lines = rspCsvString.split("\n");
        assertTrue(lines.length > 500);

        String[] row = lines[16].split(",");
        assertEquals("", row[0]);
        assertEquals("service", row[1]);
        assertEquals(20, Double.parseDouble(row[2]), .1);

        row = lines[9].split(",");
        assertEquals("Carretera d'Engolasters CS-200", row[0]);
        assertEquals("secondary", row[1]);
        assertTrue(Double.isInfinite(Double.parseDouble(row[2])));
    }
}