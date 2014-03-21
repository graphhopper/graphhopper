/*
 *  Licensed to GraphHopper and Peter Karich under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper licenses this file to you under the Apache License,
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
package com.graphhopper.routing;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.routing.util.*;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.*;
import com.graphhopper.util.TranslationMap.Translation;

import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * @author Peter Karich
 */
public class GraphHopperIT
{
    TranslationMap trMap = TranslationMapTest.SINGLETON;

    @Test
    public void testMonacoWithInstructions()
    {
        String osmFile = "files/monaco.osm.gz";
        String graphFile = "target/graph-monaco";
        String vehicle = "FOOT";
        String importVehicles = "FOOT";
        String weightCalcStr = "shortest";

        try
        {
            // make sure we are using fresh graphhopper files with correct vehicle
            Helper.removeDir(new File(graphFile));
            GraphHopper hopper = new GraphHopper().setInMemory(true).setOSMFile(osmFile).
                    disableCHShortcuts().
                    setGraphHopperLocation(graphFile).setEncodingManager(new EncodingManager(importVehicles)).
                    importOrLoad();

            Graph g = hopper.getGraph();
            GHResponse rsp = hopper.route(new GHRequest(43.727687, 7.418737, 43.74958, 7.436566).
                    setAlgorithm("astar").setVehicle(vehicle).setWeighting(weightCalcStr));

            assertEquals(3437.6, rsp.getDistance(), .1);
            assertEquals(89, rsp.getPoints().getSize());

            InstructionList il = rsp.getInstructions();
            assertEquals(13, il.size());
            Translation tr = trMap.getWithFallBack(Locale.US);
            List<Map<String, Object>> resultJson = il.createJson(tr);
            // TODO roundabout fine tuning -> enter + leave roundabout (+ two rounabouts -> is it necessary if we do not leave the street?)
            assertEquals("Continue onto Avenue des Guelfes", resultJson.get(0).get("text"));
            assertEquals("Turn slight left onto Avenue des Papalins", resultJson.get(1).get("text"));
            assertEquals("Turn sharp right onto Quai Jean-Charles Rey", resultJson.get(2).get("text"));
            assertEquals("Turn left onto road", resultJson.get(3).get("text"));
            assertEquals("Turn right onto Avenue Albert II", resultJson.get(4).get("text"));

            assertEquals(11, (Double) resultJson.get(0).get("distance"), 1);
            assertEquals(289, (Double) resultJson.get(1).get("distance"), 1);
            assertEquals(10, (Double) resultJson.get(2).get("distance"), 1);
            assertEquals(43, (Double) resultJson.get(3).get("distance"), 1);
            assertEquals(122, (Double) resultJson.get(4).get("distance"), 1);
            assertEquals(447, (Double) resultJson.get(5).get("distance"), 1);

            assertEquals(7, (Long) resultJson.get(0).get("time") / 1000);
            assertEquals(207, (Long) resultJson.get(1).get("time") / 1000);
            assertEquals(7, (Long) resultJson.get(2).get("time") / 1000);
            assertEquals(30, (Long) resultJson.get(3).get("time") / 1000);
            assertEquals(87, (Long) resultJson.get(4).get("time") / 1000);
            assertEquals(321, (Long) resultJson.get(5).get("time") / 1000);

            List<GPXEntry> list = rsp.getInstructions().createGPXList();
            assertEquals(89, list.size());
            final long lastEntryMillis = list.get(list.size() - 1).getMillis();
            final long totalResponseMillis = rsp.getMillis();
            assertEquals(totalResponseMillis, lastEntryMillis);

        } finally
        {
            Helper.removeDir(new File(graphFile));
        }
    }
}
