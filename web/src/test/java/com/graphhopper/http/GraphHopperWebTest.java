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
package com.graphhopper.http;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.PathWrapper;
import com.graphhopper.util.Downloader;
import com.graphhopper.util.Helper;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.util.Arrays;

import static org.junit.Assert.*;

/**
 * @author Peter Karich
 */
public class GraphHopperWebTest {
    // see also GraphHopperServletIT.testGraphHopperWeb for real routes against local jetty service    

    @Test
    public void testReadEncoded() throws Exception {
        Downloader downloader = new Downloader("GraphHopper Test") {
            @Override
            public InputStream fetch(HttpURLConnection conn, boolean readErrorStreamNoException) throws IOException {
                return getClass().getResourceAsStream("test_encoded.json");
            }
        };
        GraphHopperWeb instance = new GraphHopperWeb();
        instance.setDownloader(downloader);
        GHResponse rsp = instance.route(new GHRequest(52.47379, 13.362808, 52.4736925, 13.3904394));
        PathWrapper arsp = rsp.getBest();
        assertEquals(2138.3, arsp.getDistance(), 1e-1);
        assertEquals(17, arsp.getPoints().getSize());
        assertEquals(5, arsp.getInstructions().size());
        assertEquals("(0,Geradeaus auf A 100,1268.519329705091,65237)", arsp.getInstructions().get(0).toString());
        assertEquals(11, arsp.getInstructions().get(0).getPoints().size());

        assertEquals(43.73595, arsp.getWaypoints().getLat(0), 1e-4);
        assertEquals(7.42015, arsp.getWaypoints().getLon(0), 1e-4);
        assertEquals(43.73761, arsp.getWaypoints().getLat(1), 1e-4);
    }

    @Test
    public void testCreateURL() throws Exception {
        Downloader downloader = new Downloader("GraphHopper Test") {
            @Override
            public String downloadAsString(String url, boolean readErrorStreamNoException) throws IOException {
                assertFalse(url.contains("xy"));
                assertFalse(url.contains("algo1"));
                assertTrue(url.contains("alternative_route.max_paths=4"));

                assertEquals("https://graphhopper.com/api/1/route?point=52.0,13.0&point=52.0,14.0&&type=json&instructions=true&points_encoded=true&calc_points=true&algorithm=&locale=en_US&elevation=false&key=blup&alternative_route.max_paths=4", url);
                return Helper.isToString(getClass().getResourceAsStream("test_encoded.json"));
            }
        };
        GraphHopperWeb instance = new GraphHopperWeb();
        instance.setKey("blup");
        instance.setDownloader(downloader);
        GHRequest req = new GHRequest(52, 13, 52, 14);

        // should be ignored, use GraphHopperWeb or GHRequest directly instead
        req.getHints().put("key", "xy");
        req.getHints().put("algorithm", "algo1");
        req.getHints().put("alternative_route.max_paths", "4");
        instance.route(req);
    }

    @Test
    public void testSimpleToStringStream() {
        assertEquals("12;2", Arrays.asList("12", "2").stream().reduce("", (s1, s2) -> s1.isEmpty() ? s2 : s1 + ";" + s2));
        assertEquals("2", Arrays.asList("2").stream().reduce("", (s1, s2) -> s1.isEmpty() ? s2 : s1 + ";" + s2));
    }
}
