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
import com.graphhopper.util.Helper;
import com.graphhopper.util.InstructionList;
import com.graphhopper.util.TranslationMap;
import com.graphhopper.util.TranslationMapTest;
import java.io.File;
import java.util.List;
import java.util.Locale;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * @author Peter Karich
 */
public class GraphHopperIntegrationTest
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
            GraphHopper hopper = new GraphHopper().setInMemory(true, true).setOSMFile(osmFile).
                    setGraphHopperLocation(graphFile).setEncodingManager(new EncodingManager(importVehicles)).
                    importOrLoad();

            Graph g = hopper.getGraph();
            final AbstractFlagEncoder encoder = hopper.getEncodingManager().getEncoder(vehicle);
            WeightCalculation weightCalc = new ShortestCalc();
            if ("fastest".equals(weightCalcStr))
                weightCalc = new FastestCalc(encoder);

            GHResponse rsp = hopper.route(new GHRequest(43.727687, 7.418737, 43.74958, 7.436566).
                    setAlgorithm("astar").setVehicle(vehicle).setType(weightCalc));

            assertEquals(3455, rsp.getDistance(), .1);
            assertEquals(88, rsp.getPoints().getSize());

            InstructionList il = rsp.getInstructions();
            assertEquals(12, il.size());
            List<String> iList = il.createDescription(trMap.getWithFallBack(Locale.US));
            // TODO roundabout fine tuning -> enter + leave roundabout (+ two rounabouts -> is it necessary if we do not leave the street?)
            assertEquals("Continue onto Avenue des Guelfes", iList.get(0));
            assertEquals("Turn slight left onto Avenue des Papalins", iList.get(1));
            assertEquals("Turn sharp right onto Quai Jean-Charles Rey", iList.get(2));
            assertEquals("Turn left", iList.get(3));
            assertEquals("Turn right onto Avenue Albert II", iList.get(4));
        } catch (Exception ex)
        {
            throw new RuntimeException("cannot handle osm file " + osmFile, ex);
        } finally
        {
            Helper.removeDir(new File(graphFile));
        }
    }
}
