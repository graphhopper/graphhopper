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
import com.graphhopper.util.*;
import com.graphhopper.util.Translation;
import com.graphhopper.util.shapes.GHPoint;

import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Before;

/**
 * @author Peter Karich
 */
public class GraphHopperIT
{
    String graphFile = "target/graph-GraphHopperIT";
    String osmFile = "files/monaco.osm.gz";
    String vehicle = "FOOT";
    String importVehicles = "FOOT";
    String weightCalcStr = "shortest";

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
        GraphHopper hopper = new GraphHopper().
                setInMemory(true).
                setOSMFile(osmFile).
                disableCHShortcuts().
                setGraphHopperLocation(graphFile).
                setEncodingManager(new EncodingManager(importVehicles)).
                importOrLoad();

        GHResponse rsp = hopper.route(new GHRequest(43.727687, 7.418737, 43.74958, 7.436566).
                setAlgorithm("astar").setVehicle(vehicle).setWeighting(weightCalcStr));

        assertEquals(3437.6, rsp.getDistance(), .1);
        assertEquals(89, rsp.getPoints().getSize());

        InstructionList il = rsp.getInstructions();
        assertEquals(13, il.size());

        List<Map<String, Object>> resultJson = il.createJson();
        // TODO roundabout fine tuning -> enter + leave roundabout (+ two rounabouts -> is it necessary if we do not leave the street?)
        assertEquals("Continue onto Avenue des Guelfes", resultJson.get(0).get("text"));
        assertEquals("Turn slight left onto Avenue des Papalins", resultJson.get(1).get("text"));
        assertEquals("Turn sharp right onto Quai Jean-Charles Rey", resultJson.get(2).get("text"));
        assertEquals("Turn left", resultJson.get(3).get("text"));
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
    }

    @Test
    public void testSRTMWithInstructions() throws Exception
    {
        GraphHopper hopper = new GraphHopper().
                setInMemory(true).
                setOSMFile(osmFile).
                disableCHShortcuts().
                setGraphHopperLocation(graphFile).
                setEncodingManager(new EncodingManager(importVehicles));

        hopper.setElevationProvider(new SRTMProvider().setCacheDir(new File("./files/")));
        hopper.importOrLoad();

        GHResponse rsp = hopper.route(new GHRequest(43.730729, 7.421288, 43.727697, 7.419199).
                setAlgorithm("astar").setVehicle(vehicle).setWeighting(weightCalcStr));

        assertEquals(1634, rsp.getDistance(), .1);
        assertEquals(60, rsp.getPoints().getSize());
        assertTrue(rsp.getPoints().is3D());

        InstructionList il = rsp.getInstructions();
        assertEquals(10, il.size());
        assertTrue(il.get(0).getPoints().is3D());

        String str = rsp.getPoints().toString();
        assertTrue(str,
                str.startsWith("(43.73068455771767,7.421283689825812,66.0), (43.73067957305937,7.421382123709815,66.0), "
                        + "(43.73109792316924,7.421546222751131,66.0), (43.73129908884985,7.421589994913116,66.0), "
                        + "(43.731327028527716,7.421414533736137,66.0), (43.73125047381037,7.421366291225693,66.0), "
                        + "(43.73125457162979,7.421274090288746,66.0), "
                        + "(43.73128213877862,7.421115579183003,66.0), (43.731362232521825,7.421145381506057,66.0), "
                        + "(43.731371359483255,7.421123216028286,66.0), (43.731485725897976,7.42117332118392,45.0), "
                        + "(43.731575132867135,7.420868778695214,52.0), (43.73160605277731,7.420824820268709,52.0), "
                        + "(43.7316401391843,7.420850152243305,52.0), (43.731674039326776,7.421050014072285,45.0)"));
    }

    @Test
    public void testMonacoVia()
    {
        GraphHopper hopper = new GraphHopper().
                setInMemory(true).
                setOSMFile(osmFile).
                disableCHShortcuts().
                setGraphHopperLocation(graphFile).
                setEncodingManager(new EncodingManager(importVehicles)).
                importOrLoad();

        GHResponse rsp = hopper.route(new GHRequest().
                addPoint(new GHPoint(43.727687, 7.418737)).
                addPoint(new GHPoint(43.74958, 7.436566)).
                addPoint(new GHPoint(43.727687, 7.418737)).
                setAlgorithm("astar").setVehicle(vehicle).setWeighting(weightCalcStr));

        assertEquals(6875.1, rsp.getDistance(), .1);
        assertEquals(179, rsp.getPoints().getSize());

        InstructionList il = rsp.getInstructions();
        assertEquals(26, il.size());
        List<Map<String, Object>> resultJson = il.createJson();
        assertEquals("Continue onto Avenue des Guelfes", resultJson.get(0).get("text"));
        assertEquals("Turn slight left onto Avenue des Papalins", resultJson.get(1).get("text"));
        assertEquals("Turn sharp right onto Quai Jean-Charles Rey", resultJson.get(2).get("text"));
        assertEquals("Turn left", resultJson.get(3).get("text"));
        assertEquals("Turn right onto Avenue Albert II", resultJson.get(4).get("text"));

        assertEquals("Stopover 1", resultJson.get(12).get("text"));

        assertEquals("Continue onto Avenue Albert II", resultJson.get(20).get("text"));
        assertEquals("Turn left", resultJson.get(21).get("text"));
        assertEquals("Turn right onto Quai Jean-Charles Rey", resultJson.get(22).get("text"));
        assertEquals("Turn sharp left onto Avenue des Papalins", resultJson.get(23).get("text"));
        assertEquals("Turn slight right onto Avenue des Guelfes", resultJson.get(24).get("text"));
        assertEquals("Finish!", resultJson.get(25).get("text"));

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
    }
}
