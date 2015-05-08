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
package com.graphhopper;

import com.graphhopper.reader.dem.SRTMProvider;
import com.graphhopper.routing.AlgorithmOptions;
import com.graphhopper.routing.RoutingAlgorithmFactorySimple;
import com.graphhopper.routing.util.*;
import com.graphhopper.util.*;
import com.graphhopper.util.shapes.GHPoint;

import java.io.File;
import java.util.List;
import java.util.Map;
import org.junit.*;
import static org.junit.Assert.*;

/**
 * @author Peter Karich
 */
public class GraphHopperIT
{
    private static GraphHopper hopper;
    private static final String graphFileFoot = "target/graphhopperIT-foot";
    private static final String osmFile = "files/monaco.osm.gz";
    private static final String importVehicles = "FOOT";
    private static final String vehicle = "FOOT";
    private static final String weightCalcStr = "shortest";

    private final String tmpGraphFile = "target/graphhopperIT-tmp";

    @Before
    public void setUp()
    {
        Helper.removeDir(new File(tmpGraphFile));
    }

    @After
    public void tearDown()
    {
        Helper.removeDir(new File(tmpGraphFile));
    }

    @BeforeClass
    public static void beforeClass()
    {
        // make sure we are using fresh graphhopper files with correct vehicle
        Helper.removeDir(new File(graphFileFoot));

        hopper = new GraphHopper().
                setStoreOnFlush(true).
                setOSMFile(osmFile).
                setCHEnable(false).
                setGraphHopperLocation(graphFileFoot).
                setEncodingManager(new EncodingManager(importVehicles)).
                importOrLoad();
    }

    @AfterClass
    public static void afterClass()
    {
        Helper.removeDir(new File(graphFileFoot));
    }

    @Test
    public void testMonacoWithInstructions() throws Exception
    {
        GHResponse rsp = hopper.route(new GHRequest(43.727687, 7.418737, 43.74958, 7.436566).
                setAlgorithm(AlgorithmOptions.ASTAR).setVehicle(vehicle).setWeighting(weightCalcStr));

        // identify the number of counts to compare with CH foot route
        assertEquals(698, hopper.getVisitedSum());
        assertEquals(3437.6, rsp.getDistance(), .1);
        assertEquals(89, rsp.getPoints().getSize());

        InstructionList il = rsp.getInstructions();
        assertEquals(13, il.size());

        List<Map<String, Object>> resultJson = il.createJson();
        // TODO roundabout fine tuning -> enter + leave roundabout (+ two rounabouts -> is it necessary if we do not leave the street?)
        assertEquals("Continue onto Avenue des Guelfes", resultJson.get(0).get("text"));
        assertEquals("Continue onto Avenue des Papalins", resultJson.get(1).get("text"));
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
        final long lastEntryMillis = list.get(list.size() - 1).getTime();
        final long totalResponseMillis = rsp.getTime();
        assertEquals(totalResponseMillis, lastEntryMillis);
    }

    @Test
    public void testMonacoVia()
    {
        GHResponse rsp = hopper.route(new GHRequest().
                addPoint(new GHPoint(43.727687, 7.418737)).
                addPoint(new GHPoint(43.74958, 7.436566)).
                addPoint(new GHPoint(43.727687, 7.418737)).
                setAlgorithm(AlgorithmOptions.ASTAR).setVehicle(vehicle).setWeighting(weightCalcStr));

        assertEquals(6875.1, rsp.getDistance(), .1);
        assertEquals(179, rsp.getPoints().getSize());

        InstructionList il = rsp.getInstructions();
        assertEquals(26, il.size());
        List<Map<String, Object>> resultJson = il.createJson();
        assertEquals("Continue onto Avenue des Guelfes", resultJson.get(0).get("text"));
        assertEquals("Continue onto Avenue des Papalins", resultJson.get(1).get("text"));
        assertEquals("Turn sharp right onto Quai Jean-Charles Rey", resultJson.get(2).get("text"));
        assertEquals("Turn left", resultJson.get(3).get("text"));
        assertEquals("Turn right onto Avenue Albert II", resultJson.get(4).get("text"));

        assertEquals("Stopover 1", resultJson.get(12).get("text"));

        assertEquals("Continue onto Avenue Albert II", resultJson.get(20).get("text"));
        assertEquals("Turn left", resultJson.get(21).get("text"));
        assertEquals("Turn right onto Quai Jean-Charles Rey", resultJson.get(22).get("text"));
        assertEquals("Turn sharp left onto Avenue des Papalins", resultJson.get(23).get("text"));
        assertEquals("Continue onto Avenue des Guelfes", resultJson.get(24).get("text"));
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

        // special case of identical start and end point
        rsp = hopper.route(new GHRequest().
                addPoint(new GHPoint(43.727687, 7.418737)).
                addPoint(new GHPoint(43.727687, 7.418737)).
                setAlgorithm(AlgorithmOptions.ASTAR).setVehicle(vehicle).setWeighting(weightCalcStr));
        assertEquals(0, rsp.getDistance(), .1);
        assertEquals(0, rsp.getRouteWeight(), .1);
        assertEquals(1, rsp.getPoints().getSize());
        assertEquals(1, rsp.getInstructions().size());
        assertEquals("Finish!", rsp.getInstructions().createJson().get(0).get("text"));
        assertEquals(Instruction.FINISH, rsp.getInstructions().createJson().get(0).get("sign"));

        rsp = hopper.route(new GHRequest().
                addPoint(new GHPoint(43.727687, 7.418737)).
                addPoint(new GHPoint(43.727687, 7.418737)).
                addPoint(new GHPoint(43.727687, 7.418737)).
                setAlgorithm(AlgorithmOptions.ASTAR).setVehicle(vehicle).setWeighting(weightCalcStr));
        assertEquals(0, rsp.getDistance(), .1);
        assertEquals(0, rsp.getRouteWeight(), .1);
        assertEquals(2, rsp.getPoints().getSize());
        assertEquals(2, rsp.getInstructions().size());
        assertEquals(Instruction.REACHED_VIA, rsp.getInstructions().createJson().get(0).get("sign"));
        assertEquals(Instruction.FINISH, rsp.getInstructions().createJson().get(1).get("sign"));
    }

    @Test
    public void testSRTMWithInstructions() throws Exception
    {
        GraphHopper tmpHopper = new GraphHopper().
                setStoreOnFlush(true).
                setOSMFile(osmFile).
                setCHEnable(false).
                setGraphHopperLocation(tmpGraphFile).
                setEncodingManager(new EncodingManager(importVehicles));

        tmpHopper.setElevationProvider(new SRTMProvider().setCacheDir(new File("./files/")));
        tmpHopper.importOrLoad();

        GHResponse rsp = tmpHopper.route(new GHRequest(43.730729, 7.421288, 43.727697, 7.419199).
                setAlgorithm(AlgorithmOptions.ASTAR).setVehicle(vehicle).setWeighting(weightCalcStr));

        assertEquals(1626.8, rsp.getDistance(), .1);
        assertEquals(60, rsp.getPoints().getSize());
        assertTrue(rsp.getPoints().is3D());

        InstructionList il = rsp.getInstructions();
        assertEquals(10, il.size());
        assertTrue(il.get(0).getPoints().is3D());

        String str = rsp.getPoints().toString();
        assertEquals("(43.73068455771767,7.421283689825812,62.0), (43.73067957305937,7.421382123709815,66.0), "
                + "(43.73109792316924,7.421546222751131,45.0), (43.73129908884985,7.421589994913116,45.0), "
                + "(43.731327028527716,7.421414533736137,45.0), (43.73125047381037,7.421366291225693,45.0), "
                + "(43.73125457162979,7.421274090288746,52.0), "
                + "(43.73128213877862,7.421115579183003,52.0), (43.731362232521825,7.421145381506057,52.0), "
                + "(43.731371359483255,7.421123216028286,52.0), (43.731485725897976,7.42117332118392,52.0), "
                + "(43.731575132867135,7.420868778695214,52.0), (43.73160605277731,7.420824820268709,52.0), "
                + "(43.7316401391843,7.420850152243305,52.0), (43.731674039326776,7.421050014072285,52.0)",
                str.substring(0, 662));

        assertEquals("(43.727778875703635,7.418772930326453,11.0), (43.72768239068275,7.419007064826944,11.0), "
                + "(43.727680946587874,7.419198768422206,11.0)",
                str.substring(str.length() - 132));

        List<GPXEntry> list = rsp.getInstructions().createGPXList();
        assertEquals(60, list.size());
        final long lastEntryMillis = list.get(list.size() - 1).getTime();
        assertEquals(new GPXEntry(43.73068455771767, 7.421283689825812, 62.0, 0), list.get(0));
        assertEquals(new GPXEntry(43.727680946587874, 7.4191987684222065, 11.0, lastEntryMillis), list.get(list.size() - 1));

        assertEquals(62, il.createGPXList().get(0).getElevation(), 1e-2);
        assertEquals(66, il.createGPXList().get(1).getElevation(), 1e-2);
        assertEquals(52, il.createGPXList().get(10).getElevation(), 1e-2);
    }

    @Test
    public void testKremsCyclewayInstructionsWithWayTypeInfo()
    {
        String tmpOsmFile = "files/krems.osm.gz";
        String tmpVehicle = "BIKE";
        String tmpImportVehicles = "CAR,BIKE";
        String tmpWeightCalcStr = "fastest";

        GraphHopper tmpHopper = new GraphHopper().
                setStoreOnFlush(true).
                setOSMFile(tmpOsmFile).
                setCHEnable(false).
                setGraphHopperLocation(tmpGraphFile).
                setEncodingManager(new EncodingManager(tmpImportVehicles)).
                importOrLoad();

        GHResponse rsp = tmpHopper.route(new GHRequest(48.410987, 15.599492, 48.383419, 15.659294).
                setAlgorithm(AlgorithmOptions.ASTAR).setVehicle(tmpVehicle).setWeighting(tmpWeightCalcStr));

        assertEquals(6932.24, rsp.getDistance(), .1);
        assertEquals(110, rsp.getPoints().getSize());

        InstructionList il = rsp.getInstructions();
        assertEquals(19, il.size());
        List<Map<String, Object>> resultJson = il.createJson();

        assertEquals("Continue onto Obere Landstraße", resultJson.get(0).get("text"));
        assertEquals("get off the bike", resultJson.get(0).get("annotation_text"));
        assertEquals("Turn left onto Kirchengasse", resultJson.get(1).get("text"));
        assertEquals("get off the bike", resultJson.get(1).get("annotation_text"));

        assertEquals("Turn right onto Pfarrplatz", resultJson.get(2).get("text"));
        assertEquals("Turn right onto Margarethenstraße", resultJson.get(3).get("text"));
        assertEquals("Turn slight left onto Hoher Markt", resultJson.get(4).get("text"));
        assertEquals("Turn right onto Wegscheid", resultJson.get(5).get("text"));
        assertEquals("Turn slight left onto Untere Landstraße", resultJson.get(6).get("text"));
        assertEquals("Turn right onto Ringstraße, L73", resultJson.get(7).get("text"));
        assertEquals("Continue onto Eyblparkstraße", resultJson.get(8).get("text"));
        assertEquals("Turn slight left onto Austraße", resultJson.get(9).get("text"));
        assertEquals("Turn slight left onto Rechte Kremszeile", resultJson.get(10).get("text"));
        //..
        assertEquals("Turn right onto Treppelweg", resultJson.get(15).get("text"));
        assertEquals("cycleway", resultJson.get(15).get("annotation_text"));
    }

    @Test
    public void testRoundaboutInstructionsWithCH()
    {
        String tmpOsmFile = "files/monaco.osm.gz";
        String tmpVehicle = "car";
        String tmpImportVehicles = "car,bike";
        String tmpWeightCalcStr = "fastest";

        GraphHopper tmpHopper = new GraphHopper().
                setStoreOnFlush(true).
                setOSMFile(tmpOsmFile).
                setGraphHopperLocation(tmpGraphFile).
                setEncodingManager(new EncodingManager(tmpImportVehicles)).
                importOrLoad();

        assertEquals(tmpVehicle, tmpHopper.getDefaultVehicle().toString());
        assertFalse(RoutingAlgorithmFactorySimple.class.isAssignableFrom(tmpHopper.getAlgorithmFactory().getClass()));

        GHResponse rsp = tmpHopper.route(new GHRequest(43.745084, 7.430513, 43.745247, 7.430347)
                .setVehicle(tmpVehicle).setWeighting(tmpWeightCalcStr));
        assertEquals(2, ((RoundaboutInstruction) rsp.getInstructions().get(1)).getExitNumber());

        rsp = tmpHopper.route(new GHRequest(43.745968, 7.42907, 43.745832, 7.428614)
                .setVehicle(tmpVehicle).setWeighting(tmpWeightCalcStr));
        assertEquals(2, ((RoundaboutInstruction) rsp.getInstructions().get(1)).getExitNumber());

        rsp = tmpHopper.route(new GHRequest(43.745948, 7.42914, 43.746173, 7.428834)
                .setVehicle(tmpVehicle).setWeighting(tmpWeightCalcStr));
        assertEquals(1, ((RoundaboutInstruction) rsp.getInstructions().get(1)).getExitNumber());

        rsp = tmpHopper.route(new GHRequest(43.735817, 7.417096, 43.735666, 7.416587)
                .setVehicle(tmpVehicle).setWeighting(tmpWeightCalcStr));
        assertEquals(2, ((RoundaboutInstruction) rsp.getInstructions().get(1)).getExitNumber());
    }

    @Test
    public void testMultipleVehiclesAndDoCHForBike()
    {
        String tmpOsmFile = "files/monaco.osm.gz";
        String tmpImportVehicles = "bike,car";

        GraphHopper tmpHopper = new GraphHopper().
                setStoreOnFlush(true).
                setOSMFile(tmpOsmFile).
                setGraphHopperLocation(tmpGraphFile).
                setEncodingManager(new EncodingManager(tmpImportVehicles)).
                importOrLoad();
        assertEquals("bike", tmpHopper.getDefaultVehicle().toString());

        GHResponse rsp = tmpHopper.route(new GHRequest(43.73005, 7.415707, 43.741522, 7.42826)
                .setVehicle("car"));
        assertEquals(207, rsp.getTime() / 1000f, 1);
        assertEquals(2838, rsp.getDistance(), 1);

        rsp = tmpHopper.route(new GHRequest(43.73005, 7.415707, 43.741522, 7.42826)
                .setVehicle("bike"));
        assertEquals(494, rsp.getTime() / 1000f, 1);
        assertEquals(2192, rsp.getDistance(), 1);

        rsp = tmpHopper.route(new GHRequest(43.73005, 7.415707, 43.741522, 7.42826)
                .setVehicle("foot"));
        assertTrue("only bike and car were imported. foot request should fail", rsp.hasErrors());
    }

    @Test
    public void testIfCHIsUsed() throws Exception
    {
        // route directly after import
        executeCHFootRoute();

        // now only load is called
        executeCHFootRoute();
    }

    private void executeCHFootRoute()
    {
        String tmpOsmFile = "files/monaco.osm.gz";
        String tmpImportVehicles = "foot";

        GraphHopper tmpHopper = new GraphHopper().
                setStoreOnFlush(true).
                setOSMFile(tmpOsmFile).
                setCHWeighting(weightCalcStr).
                setGraphHopperLocation(tmpGraphFile).
                setEncodingManager(new EncodingManager(tmpImportVehicles)).
                importOrLoad();

        // same query as in testMonacoWithInstructions
        GHResponse rsp = tmpHopper.route(new GHRequest(43.727687, 7.418737, 43.74958, 7.436566).
                setVehicle(vehicle));

        // identify the number of counts to compare with none-CH foot route which had nearly 700 counts
        assertTrue("Too many nodes visited " + tmpHopper.getVisitedSum(), tmpHopper.getVisitedSum() < 120);
        assertEquals(3437.6, rsp.getDistance(), .1);
        assertEquals(89, rsp.getPoints().getSize());
        tmpHopper.close();
    }
}
