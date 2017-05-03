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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphhopper.http.WebHelper;
import com.graphhopper.util.CmdArgs;
import com.graphhopper.util.Helper;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 *
 * @author Peter Karich
 */
public class MatchServletTest extends BaseServletTester {

    private static final String PBF = "../map-data/leipzig_germany.osm.pbf";
    private static final String DIR = "../target/mapmatchingtest";

    @AfterClass
    public static void cleanUp() {
        // do not remove imported graph
        // Helper.removeDir(new File(dir));
        shutdownJetty(true);
    }

    @Before
    public void setUp() {
        CmdArgs args = new CmdArgs().
                put("graph.flag_encoders", "car").
                put("prepare.ch.weightings", "no").
                put("datareader.file", PBF).
                put("graph.location", DIR);
        setUpJetty(args);
    }

    @Test
    public void testDoPost() throws Exception {
        final ObjectMapper objectMapper = new ObjectMapper();
        String xmlStr = Helper.isToString(getClass().getResourceAsStream("tour2-with-loop.gpx"));
        String jsonStr = post("/match", 200, xmlStr);
        JsonNode json = objectMapper.readTree(jsonStr);

        // {"hints":{},
        //  "paths":[{"instructions":[{"distance":417.326,"sign":0,"interval":[0,3],"text":"Continue onto Gustav-Adolf-Straße","time":60093},{"distance":108.383,"sign":-2,"interval":[3,4],"text":"Turn left onto Leibnizstraße","time":15607},{"distance":218.914,"sign":-2,"interval":[4,6],"text":"Turn left onto Hinrichsenstraße","time":26269},{"distance":257.727,"sign":-2,"interval":[6,8],"text":"Turn left onto Tschaikowskistraße","time":30926},{"distance":0,"sign":4,"interval":[8,8],"text":"Finish!","time":0}],
        //  "descend":0,"ascend":0,"distance":1002.35,"bbox":[12.35853,51.342524,12.36419,51.345381],"weight":1002.35,"time":132895,"points_encoded":true,"points":"{}jxHwwljAsBuOaA{GcAyH}DlAhAdIz@jGvDeB|FiC"}],
        //  "info":{"copyrights":["GraphHopper","OpenStreetMap contributors"]}
        // }
        JsonNode path = json.get("paths").get(0);
        assertEquals(5, path.get("instructions").size());
        assertEquals(9, WebHelper.decodePolyline(path.get("points").asText(), 10, false).size());

        assertEquals(132.9, path.get("time").asLong() / 1000f, 0.1);
        assertEquals(1002, path.get("distance").asDouble(), 1);
    }
}
