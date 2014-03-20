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
import com.graphhopper.reader.dem.SRTMProvider;
import com.graphhopper.routing.util.*;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.*;
import com.graphhopper.util.TranslationMap.Translation;

import java.io.File;
import java.util.List;
import java.util.Locale;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Before;

/**
 * @author Peter Karich
 */
public class GraphHopperIT
{
    TranslationMap trMap = TranslationMapTest.SINGLETON;
    String graphFile = "target/graph-GraphHopperIT";

    @Before
    public void setUp()
    {
        // make sure we are using fresh graphhopper files with correct vehicle
        Helper.removeDir(new File(graphFile));
    }

    @After
    public void tearDown()
    {
        Helper.removeDir(new File(graphFile));
    }

    @Test
    public void testMonacoWithInstructions() throws Exception
    {
        String osmFile = "files/monaco.osm.gz";
        String vehicle = "FOOT";
        String importVehicles = "FOOT";
        String weightCalcStr = "shortest";
        GraphHopper hopper = new GraphHopper().setInMemory(true).setOSMFile(osmFile).
                disableCHShortcuts().
                setGraphHopperLocation(graphFile).setEncodingManager(new EncodingManager(importVehicles)).
                importOrLoad();

        Graph g = hopper.getGraph();
        GHResponse rsp = hopper.route(new GHRequest(43.727687, 7.418737, 43.74958, 7.436566).
                setAlgorithm("astar").setVehicle(vehicle).setWeighting(weightCalcStr));

        assertEquals(3437.6, rsp.getDistance(), .1);
        assertEquals(87, rsp.getPoints().getSize());

        InstructionList il = rsp.getInstructions();
        assertEquals(13, il.size());
        Translation tr = trMap.getWithFallBack(Locale.US);
        List<String> iList = il.createDescription(tr);
        // TODO roundabout fine tuning -> enter + leave roundabout (+ two rounabouts -> is it necessary if we do not leave the street?)
        assertEquals("Continue onto Avenue des Guelfes", iList.get(0));
        assertEquals("Turn slight left onto Avenue des Papalins", iList.get(1));
        assertEquals("Turn sharp right onto Quai Jean-Charles Rey", iList.get(2));
        assertEquals("Turn left onto road", iList.get(3));
        assertEquals("Turn right onto Avenue Albert II", iList.get(4));

        List<Double> dists = il.createDistances();
        assertEquals(11, dists.get(0), 1);
        assertEquals(289, dists.get(1), 1);
        assertEquals(10, dists.get(2), 1);
        assertEquals(43, dists.get(3), 1);
        assertEquals(122, dists.get(4), 1);
        assertEquals(447, dists.get(5), 1);

        List<Long> times = il.createMillis();
        assertEquals(7, times.get(0) / 1000);
        assertEquals(207, times.get(1) / 1000);
        assertEquals(7, times.get(2) / 1000);
        assertEquals(30, times.get(3) / 1000);
        assertEquals(87, times.get(4) / 1000);
        assertEquals(321, times.get(5) / 1000);

        List<GPXEntry> list = rsp.getInstructions().createGPXList();
        assertEquals(123, list.size());
        final long lastEntryMillis = list.get(list.size() - 1).getMillis();
        final long totalResponseMillis = rsp.getMillis();
        assertEquals(totalResponseMillis, lastEntryMillis);
    }

    @Test
    public void testSRTMWithInstructions() throws Exception
    {
        String osmFile = "files/monaco.osm.gz";
        String vehicle = "FOOT";
        String importVehicles = "FOOT";
        String weightCalcStr = "shortest";

        GraphHopper hopper = new GraphHopper().setInMemory(true).setOSMFile(osmFile).
                disableCHShortcuts().
                setGraphHopperLocation(graphFile).setEncodingManager(new EncodingManager(importVehicles));

        hopper.setElevationProvider(new SRTMProvider().setCacheDir(new File("./files/")));
        hopper.importOrLoad();

        Graph g = hopper.getGraph();
        GHResponse rsp = hopper.route(new GHRequest(43.730729, 7.421288, 43.727697, 7.419199).
                setAlgorithm("astar").setVehicle(vehicle).setWeighting(weightCalcStr));

        assertEquals(1634, rsp.getDistance(), .1);
        assertEquals(54, rsp.getPoints().getSize());

        InstructionList il = rsp.getInstructions();
        assertEquals(10, il.size());
    }
}
