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
package com.graphhopper;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.reader.dem.SRTMProvider;
import com.graphhopper.reader.osm.GraphHopperOSM;
import com.graphhopper.routing.ch.CHAlgoFactoryDecorator;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.DefaultFlagEncoderFactory;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.parsers.OSMMaxSpeedParser;
import com.graphhopper.routing.util.parsers.OSMRoadEnvironmentParser;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.*;
import com.graphhopper.util.Parameters.CH;
import com.graphhopper.util.Parameters.Landmark;
import com.graphhopper.util.Parameters.Routing;
import com.graphhopper.util.details.PathDetail;
import com.graphhopper.util.exceptions.PointDistanceExceededException;
import com.graphhopper.util.shapes.GHPoint;
import com.graphhopper.util.shapes.GHPoint3D;
import org.junit.*;

import java.io.File;
import java.util.*;

import static com.graphhopper.util.Parameters.Algorithms.*;
import static com.graphhopper.util.Parameters.Curbsides.*;
import static java.util.Arrays.asList;
import static org.junit.Assert.*;

/**
 * @author Peter Karich
 */
public class GraphHopperIT {

    public static final String DIR = "../core/files";
    private static final String graphFileFoot = "target/graphhopperIT-foot";
    private static final String osmFile = DIR + "/monaco.osm.gz";
    private static final String importVehicles = "foot";
    private static final String importMultipleVehicles = "car,foot";
    private static final String vehicle = "foot";
    private static final String weightCalcStr = "shortest";
    private static GraphHopper hopper;
    private final String tmpGraphFile = "target/graphhopperIT-tmp";

    @BeforeClass
    public static void beforeClass() {
        // make sure we are using fresh graphhopper files with correct vehicle
        Helper.removeDir(new File(graphFileFoot));

        hopper = new GraphHopperOSM().
                setOSMFile(osmFile).
                setStoreOnFlush(true).
                setCHEnabled(false).
                setGraphHopperLocation(graphFileFoot).
                setEncodingManager(EncodingManager.create(importVehicles)).
                importOrLoad();
    }

    @AfterClass
    public static void afterClass() {
        Helper.removeDir(new File(graphFileFoot));
    }

    @Before
    public void setUp() {
        Helper.removeDir(new File(tmpGraphFile));
    }

    @After
    public void tearDown() {
        Helper.removeDir(new File(tmpGraphFile));
    }

    @Test
    public void testMonacoWithInstructions() {
        GHResponse rsp = hopper.route(new GHRequest(43.727687, 7.418737, 43.74958, 7.436566).
                setAlgorithm(ASTAR).setVehicle(vehicle).setWeighting(weightCalcStr));

        // identify the number of counts to compare with CH foot route
        assertEquals(699, rsp.getHints().getLong("visited_nodes.sum", 0));

        PathWrapper arsp = rsp.getBest();
        assertEquals(3437.6, arsp.getDistance(), .1);
        assertEquals(87, arsp.getPoints().getSize());

        assertEquals(43.7276852, arsp.getWaypoints().getLat(0), 1e-7);
        assertEquals(43.7495432, arsp.getWaypoints().getLat(1), 1e-7);

        InstructionList il = arsp.getInstructions();
        assertEquals(20, il.size());

        // TODO roundabout fine tuning -> enter + leave roundabout (+ two roundabouts -> is it necessary if we do not leave the street?)
        Translation tr = hopper.getTranslationMap().getWithFallBack(Locale.US);
        assertEquals("continue onto Avenue des Guelfes", il.get(0).getTurnDescription(tr));
        assertEquals("continue onto Avenue des Papalins", il.get(1).getTurnDescription(tr));
        assertEquals("turn sharp right onto Quai Jean-Charles Rey", il.get(4).getTurnDescription(tr));
        assertEquals("turn left", il.get(5).getTurnDescription(tr));
        assertEquals("turn right onto Avenue Albert II", il.get(6).getTurnDescription(tr));

        assertEquals(11, il.get(0).getDistance(), 1);
        assertEquals(96, il.get(1).getDistance(), 1);
        assertEquals(178, il.get(2).getDistance(), 1);
        assertEquals(13, il.get(3).getDistance(), 1);
        assertEquals(10, il.get(4).getDistance(), 1);
        assertEquals(42, il.get(5).getDistance(), 1);

        assertEquals(7, il.get(0).getTime() / 1000);
        assertEquals(69, il.get(1).getTime() / 1000);
        assertEquals(128, il.get(2).getTime() / 1000);
        assertEquals(9, il.get(3).getTime() / 1000);
        assertEquals(7, il.get(4).getTime() / 1000);
        assertEquals(30, il.get(5).getTime() / 1000);

        assertEquals(87, arsp.getPoints().size());
    }

    @Test
    public void withoutInstructions() {
        GHRequest request = new GHRequest().setAlgorithm(ASTAR).setVehicle(vehicle).setWeighting(weightCalcStr);
        request.addPoint(new GHPoint(43.729584, 7.410965));
        request.addPoint(new GHPoint(43.732499, 7.426758));
        request.getHints().put("instructions", true);
        GHResponse routeRsp = hopper.route(request);
        int withInstructionsPoints = routeRsp.getBest().getPoints().size();

        request.getHints().put("instructions", false);
        routeRsp = hopper.route(request);

        assertTrue("there should not be more points if instructions are disabled due to simplify but was " + withInstructionsPoints + " vs " + routeRsp.getBest().getPoints().size(),
                withInstructionsPoints > routeRsp.getBest().getPoints().size());
    }

    @Test
    public void testUTurn() {
        GraphHopper tmpHopper = new GraphHopperOSM().
                setOSMFile(DIR + "/monaco.osm.gz").
                setCHEnabled(false).
                setGraphHopperLocation(tmpGraphFile).
                setEncodingManager(EncodingManager.create("car"));
        tmpHopper.importOrLoad();
        Translation tr = hopper.getTranslationMap().getWithFallBack(Locale.US);

        GHRequest request = new GHRequest();
        //Force initial U-Turn
        request.addPoint(new GHPoint(43.743887, 7.431151), 200);
        request.addPoint(new GHPoint(43.744007, 7.431076));

        request.setAlgorithm(ASTAR).setVehicle("car").setWeighting(weightCalcStr);
        GHResponse rsp = tmpHopper.route(request);

        assertFalse(rsp.hasErrors());
        PathWrapper arsp = rsp.getBest();
        InstructionList il = arsp.getInstructions();
        assertEquals(3, il.size());

        // Initial U-turn
        assertEquals("make a U-turn onto Avenue Princesse Grace", il.get(0).getTurnDescription(tr));
        // Second U-turn to get to destination
        assertEquals("make a U-turn onto Avenue Princesse Grace", il.get(1).getTurnDescription(tr));
    }

    private void testImportCloseAndLoad(boolean ch, boolean lm, boolean sort) {

        String tmpOsmFile = DIR + "/monaco.osm.gz";
        String tmpImportVehicles = "foot";

        GraphHopper tmpHopper = new GraphHopperOSM().
                setOSMFile(tmpOsmFile).
                setStoreOnFlush(true).
                setCHEnabled(ch).
                setSortGraph(sort).
                setGraphHopperLocation(tmpGraphFile).
                setEncodingManager(EncodingManager.create(tmpImportVehicles));
        if (ch) {
            tmpHopper.getCHFactoryDecorator().setCHProfileStrings(weightCalcStr).setDisablingAllowed(true);
        }
        if (lm) {
            tmpHopper.getLMFactoryDecorator().
                    setEnabled(true).
                    setWeightingsAsStrings(Collections.singletonList(weightCalcStr)).
                    setDisablingAllowed(true);
        }
        tmpHopper.importAndClose();
        tmpHopper = new GraphHopperOSM().
                setOSMFile(tmpOsmFile).
                setStoreOnFlush(true).
                setCHEnabled(ch).
                setGraphHopperLocation(tmpGraphFile).
                setEncodingManager(EncodingManager.create(tmpImportVehicles));
        if (ch) {
            tmpHopper.getCHFactoryDecorator().setCHProfileStrings(weightCalcStr).setDisablingAllowed(true);
        }
        if (lm) {
            tmpHopper.getLMFactoryDecorator().
                    setEnabled(true).
                    setWeightingsAsStrings(Collections.singletonList(weightCalcStr)).
                    setDisablingAllowed(true);
        }
        tmpHopper.importOrLoad();

        // same query as in testMonacoWithInstructions
        // visited nodes >700 for flexible, <120 for CH or LM

        if (ch) {
            GHRequest req = new GHRequest(43.727687, 7.418737, 43.74958, 7.436566).
                    setWeighting(weightCalcStr).
                    setVehicle(tmpImportVehicles);
            req.getHints().put(CH.DISABLE, false);
            req.getHints().put(Landmark.DISABLE, true);
            GHResponse rsp = tmpHopper.route(req);

            PathWrapper bestPath = rsp.getBest();
            long sum = rsp.getHints().getLong("visited_nodes.sum", 0);
            assertNotEquals(sum, 0);
            assertTrue("Too many nodes visited " + sum, sum < 120);
            assertEquals(3437.6, bestPath.getDistance(), .1);
            assertEquals(87, bestPath.getPoints().getSize());
        }

        if (lm) {
            GHRequest req = new GHRequest(43.727687, 7.418737, 43.74958, 7.436566).
                    setVehicle(tmpImportVehicles).
                    setWeighting(weightCalcStr).
                    setAlgorithm(Parameters.Algorithms.ASTAR_BI);
            req.getHints().put(CH.DISABLE, true);
            req.getHints().put(Landmark.DISABLE, false);
            GHResponse rsp = tmpHopper.route(req);

            PathWrapper bestPath = rsp.getBest();
            long sum = rsp.getHints().getLong("visited_nodes.sum", 0);
            assertNotEquals(sum, 0);
            assertTrue("Too many nodes visited " + sum, sum < 120);
            assertEquals(3437.6, bestPath.getDistance(), .1);
            assertEquals(87, bestPath.getPoints().getSize());
        }

        // flexible
        GHRequest req = new GHRequest(43.727687, 7.418737, 43.74958, 7.436566).
                setVehicle(tmpImportVehicles).
                setWeighting(weightCalcStr);
        req.getHints().put(CH.DISABLE, true);
        req.getHints().put(Landmark.DISABLE, true);
        GHResponse rsp = tmpHopper.route(req);

        PathWrapper bestPath = rsp.getBest();
        long sum = rsp.getHints().getLong("visited_nodes.sum", 0);
        assertNotEquals(sum, 0);
        assertTrue("Too few nodes visited " + sum, sum > 120);
        assertEquals(3437.6, bestPath.getDistance(), .1);
        assertEquals(87, bestPath.getPoints().getSize());

        tmpHopper.close();
    }

    @Test
    public void testImportThenLoadCH() {
        testImportCloseAndLoad(true, false, false);
    }

    @Test
    public void testImportThenLoadLM() {
        testImportCloseAndLoad(false, true, false);
    }

    @Test
    public void testImportThenLoadCHLM() {
        testImportCloseAndLoad(true, true, false);
    }

    @Test
    public void testImportThenLoadCHLMAndSort() {
        testImportCloseAndLoad(true, true, true);
    }

    @Test
    public void testImportThenLoadFlexible() {
        testImportCloseAndLoad(false, false, false);
    }

    @Test
    public void testAlternativeRoutes() {
        GHRequest req = new GHRequest(43.729057, 7.41251, 43.740298, 7.423561).
                setAlgorithm(ALT_ROUTE).setVehicle(vehicle).setWeighting(weightCalcStr);

        GHResponse rsp = hopper.route(req);
        assertFalse(rsp.hasErrors());
        assertEquals(2, rsp.getAll().size());

        assertEquals(1310, rsp.getAll().get(0).getTime() / 1000);
        assertEquals(1432, rsp.getAll().get(1).getTime() / 1000);

        req.getHints().put("alternative_route.max_paths", "3");
        req.getHints().put("alternative_route.min_plateau_factor", "0.1");
        rsp = hopper.route(req);
        assertFalse(rsp.hasErrors());
        assertEquals(3, rsp.getAll().size());

        assertEquals(1310, rsp.getAll().get(0).getTime() / 1000);
        assertEquals(1432, rsp.getAll().get(1).getTime() / 1000);
        assertEquals(1492, rsp.getAll().get(2).getTime() / 1000);
    }

    @Test
    public void testAlternativeRoutesBikeAndCar() {
        GraphHopper tmpHopper = new GraphHopperOSM().
                setOSMFile(DIR + "/north-bayreuth.osm.gz").
                setCHEnabled(false).
                setGraphHopperLocation(tmpGraphFile).
                setEncodingManager(EncodingManager.create("bike, car"));
        tmpHopper.importOrLoad();

        GHRequest req = new GHRequest(50.028917, 11.496506, 49.985228, 11.600876).
                setAlgorithm(ALT_ROUTE).setVehicle("bike").setWeighting("fastest");
        req.getHints().put("alternative_route.max_paths", "3");
        GHResponse rsp = tmpHopper.route(req);
        assertFalse(rsp.getErrors().toString(), rsp.hasErrors());

        assertEquals(3, rsp.getAll().size());
        // via ramsenthal
        assertEquals(2864, rsp.getAll().get(0).getTime() / 1000);
        // via unterwaiz
        assertEquals(3318, rsp.getAll().get(1).getTime() / 1000);
        // via eselslohe -> theta; BTW: here smaller time as 2nd alternative due to priority influences time order
        assertEquals(3094, rsp.getAll().get(2).getTime() / 1000);

        req = new GHRequest(50.023513, 11.548862, 49.969441, 11.537876).
                setAlgorithm(ALT_ROUTE).setVehicle("car").setWeighting("fastest");
        req.getHints().put("alternative_route.max_paths", "3");
        rsp = tmpHopper.route(req);
        assertFalse(rsp.getErrors().toString(), rsp.hasErrors());

        assertEquals(3, rsp.getAll().size());
        // directly via obergräfenthal
        assertEquals(870, rsp.getAll().get(0).getTime() / 1000);
        // via ramsenthal -> lerchenhof
        assertEquals(913, rsp.getAll().get(1).getTime() / 1000);
        // via neudrossenfeld
        assertEquals(958, rsp.getAll().get(2).getTime() / 1000);
    }

    @Test
    public void testPointHint() {
        GraphHopper tmpHopper = new GraphHopperOSM().
                setOSMFile(DIR + "/Laufamholzstrasse.osm.xml").
                setCHEnabled(false).
                setGraphHopperLocation(tmpGraphFile).
                setEncodingManager(EncodingManager.create("car"));
        tmpHopper.importOrLoad();

        GHRequest req = new GHRequest(49.46553, 11.154669, 49.465244, 11.152577).
                setVehicle("car").setWeighting("fastest");

        req.setPointHints(new ArrayList<>(asList("Laufamholzstraße, 90482, Nürnberg, Deutschland", "")));
        GHResponse rsp = tmpHopper.route(req);
        assertFalse(rsp.getErrors().toString(), rsp.hasErrors());
        GHPoint snappedPoint = rsp.getBest().getWaypoints().get(0);
        assertEquals(49.465686, snappedPoint.getLat(), .000001);
        assertEquals(11.154605, snappedPoint.getLon(), .000001);

        req.setPointHints(new ArrayList<>(asList("", "")));
        rsp = tmpHopper.route(req);
        assertFalse(rsp.getErrors().toString(), rsp.hasErrors());
        snappedPoint = rsp.getBest().getWaypoints().get(0);
        assertEquals(49.465502, snappedPoint.getLat(), .000001);
        assertEquals(11.154498, snappedPoint.getLon(), .000001);

        // Match to closest edge, since hint was not found
        req.setPointHints(new ArrayList<>(asList("xy", "")));
        rsp = tmpHopper.route(req);
        assertFalse(rsp.getErrors().toString(), rsp.hasErrors());
        snappedPoint = rsp.getBest().getWaypoints().get(0);
        assertEquals(49.465502, snappedPoint.getLat(), .000001);
        assertEquals(11.154498, snappedPoint.getLon(), .000001);
    }

    @Test
    public void testNorthBayreuthDestination() {
        GraphHopper tmpHopper = new GraphHopperOSM().
                setOSMFile(DIR + "/north-bayreuth.osm.gz").
                setCHEnabled(false).
                setGraphHopperLocation(tmpGraphFile).
                setEncodingManager(new EncodingManager.Builder().addAll(new DefaultFlagEncoderFactory(), "car").build());
        tmpHopper.importOrLoad();

        GHRequest req = new GHRequest(49.985307, 11.50628, 49.985731, 11.507465).
                setVehicle("car").setWeighting("fastest");

        GHResponse rsp = tmpHopper.route(req);
        assertFalse(rsp.getErrors().toString(), rsp.hasErrors());
        assertEquals(550, rsp.getBest().getDistance(), 1);
    }

    @Test
    public void testNorthBayreuthBlockedEdges() {
        GraphHopper tmpHopper = new GraphHopperOSM().
                setOSMFile(DIR + "/north-bayreuth.osm.gz").
                setCHEnabled(false).
                setGraphHopperLocation(tmpGraphFile).
                setEncodingManager(new EncodingManager.Builder().add(new CarFlagEncoder()).build());
        tmpHopper.importOrLoad();

        GHRequest req = new GHRequest(49.985272, 11.506151, 49.986107, 11.507202);

        GHResponse rsp = tmpHopper.route(req);
        assertFalse(rsp.getErrors().toString(), rsp.hasErrors());
        assertEquals(122, rsp.getBest().getDistance(), 1);

        // block point 49.985759,11.50687
        req.getHints().put(Routing.BLOCK_AREA, "49.985759,11.50687");
        rsp = tmpHopper.route(req);
        assertFalse(rsp.getErrors().toString(), rsp.hasErrors());
        assertEquals(365, rsp.getBest().getDistance(), 1);

        req = new GHRequest(49.975845, 11.522598, 50.026821, 11.497364).
                setWeighting("fastest");

        rsp = tmpHopper.route(req);
        assertFalse(rsp.getErrors().toString(), rsp.hasErrors());
        assertEquals(6685, rsp.getBest().getDistance(), 1);

        // block by area
        String someArea = "49.97986,11.472902,50.003946,11.534357";
        req.getHints().put(Routing.BLOCK_AREA, someArea);
        rsp = tmpHopper.route(req);
        assertFalse(rsp.getErrors().toString(), rsp.hasErrors());
        assertEquals(12173, rsp.getBest().getDistance(), 1);

        // Add blocked point to above area, to increase detour        
        req.getHints().put(Routing.BLOCK_AREA, "50.017578,11.547527;" + someArea);
        rsp = tmpHopper.route(req);
        assertFalse(rsp.getErrors().toString(), rsp.hasErrors());
        assertEquals(12787, rsp.getBest().getDistance(), 1);

        // block by edge IDs -> i.e. use small circular area
        req.getHints().put(Routing.BLOCK_AREA, "49.981599,11.517448,100");
        rsp = tmpHopper.route(req);
        assertFalse(rsp.getErrors().toString(), rsp.hasErrors());
        assertEquals(6879, rsp.getBest().getDistance(), 1);

        // block by edge IDs -> i.e. use small rectangular area
        req.getHints().put(Routing.BLOCK_AREA, "49.981875,11.515818,49.981088,11.519423");
        rsp = tmpHopper.route(req);
        assertFalse(rsp.getErrors().toString(), rsp.hasErrors());
        assertEquals(6879, rsp.getBest().getDistance(), 1);

        // blocking works for all weightings
        req = new GHRequest(50.009504, 11.490669, 50.024726, 11.496162).
                setVehicle("car").setWeighting("fastest");
        rsp = tmpHopper.route(req);
        assertFalse(rsp.getErrors().toString(), rsp.hasErrors());
        assertEquals(1807, rsp.getBest().getDistance(), 1);

        // block point 49.985759,11.50687
        req.getHints().put(Routing.BLOCK_AREA, "50.018274,11.492558");
        rsp = tmpHopper.route(req);
        assertFalse(rsp.getErrors().toString(), rsp.hasErrors());
        assertEquals(3363, rsp.getBest().getDistance(), 1);
    }

    @Test
    public void testMonacoVia() {
        Translation tr = hopper.getTranslationMap().getWithFallBack(Locale.US);
        GHResponse rsp = hopper.route(new GHRequest().
                addPoint(new GHPoint(43.727687, 7.418737)).
                addPoint(new GHPoint(43.74958, 7.436566)).
                addPoint(new GHPoint(43.727687, 7.418737)).
                setAlgorithm(ASTAR).setVehicle(vehicle).setWeighting(weightCalcStr));

        PathWrapper arsp = rsp.getBest();
        assertEquals(6875.2, arsp.getDistance(), .1);
        assertEquals(173, arsp.getPoints().getSize());

        InstructionList il = arsp.getInstructions();
        assertEquals(36, il.size());
        assertEquals("continue onto Avenue des Guelfes", il.get(0).getTurnDescription(tr));
        assertEquals("continue onto Avenue des Papalins", il.get(1).getTurnDescription(tr));
        assertEquals("turn sharp right onto Quai Jean-Charles Rey", il.get(4).getTurnDescription(tr));
        assertEquals("turn left", il.get(5).getTurnDescription(tr));
        assertEquals("turn right onto Avenue Albert II", il.get(6).getTurnDescription(tr));

        assertEquals("waypoint 1", il.get(19).getTurnDescription(tr));
        assertEquals(Instruction.U_TURN_UNKNOWN, il.get(20).getSign());

        assertEquals("continue onto Avenue Albert II", il.get(29).getTurnDescription(tr));
        assertEquals("turn left", il.get(30).getTurnDescription(tr));
        assertEquals("turn right onto Quai Jean-Charles Rey", il.get(31).getTurnDescription(tr));
        assertEquals("turn sharp left onto Avenue des Papalins", il.get(32).getTurnDescription(tr));
        assertEquals("continue onto Avenue des Guelfes", il.get(34).getTurnDescription(tr));
        assertEquals("arrive at destination", il.get(35).getTurnDescription(tr));

        assertEquals(11, il.get(0).getDistance(), 1);
        assertEquals(97, il.get(1).getDistance(), 1);
        assertEquals(178, il.get(2).getDistance(), 1);
        assertEquals(13, il.get(3).getDistance(), 1);
        assertEquals(10, il.get(4).getDistance(), 1);
        assertEquals(42, il.get(5).getDistance(), 1);

        assertEquals(7, il.get(0).getTime() / 1000);
        assertEquals(69, il.get(1).getTime() / 1000);
        assertEquals(128, il.get(2).getTime() / 1000);
        assertEquals(9, il.get(3).getTime() / 1000);
        assertEquals(7, il.get(4).getTime() / 1000);
        assertEquals(30, il.get(5).getTime() / 1000);

        // special case of identical start and end point
        rsp = hopper.route(new GHRequest().
                addPoint(new GHPoint(43.727687, 7.418737)).
                addPoint(new GHPoint(43.727687, 7.418737)).
                setAlgorithm(ASTAR).setVehicle(vehicle).setWeighting(weightCalcStr));

        arsp = rsp.getBest();
        assertEquals(0, arsp.getDistance(), .1);
        assertEquals(0, arsp.getRouteWeight(), .1);
        assertEquals(1, arsp.getPoints().getSize());
        assertEquals(1, arsp.getInstructions().size());
        assertEquals("arrive at destination", arsp.getInstructions().get(0).getTurnDescription(tr));
        assertEquals(Instruction.FINISH, arsp.getInstructions().get(0).getSign());

        rsp = hopper.route(new GHRequest().
                addPoint(new GHPoint(43.727687, 7.418737)).
                addPoint(new GHPoint(43.727687, 7.418737)).
                addPoint(new GHPoint(43.727687, 7.418737)).
                setAlgorithm(ASTAR).setVehicle(vehicle).setWeighting(weightCalcStr));

        arsp = rsp.getBest();
        assertEquals(0, arsp.getDistance(), .1);
        assertEquals(0, arsp.getRouteWeight(), .1);
        assertEquals(1, arsp.getPoints().getSize());
        assertEquals(2, arsp.getInstructions().size());
        assertEquals(Instruction.REACHED_VIA, arsp.getInstructions().get(0).getSign());
        assertEquals(Instruction.FINISH, arsp.getInstructions().get(1).getSign());
    }

    @Test
    public void testMonacoPathDetails() {
        GHRequest request = new GHRequest();
        request.addPoint(new GHPoint(43.727687, 7.418737));
        request.addPoint(new GHPoint(43.74958, 7.436566));
        request.addPoint(new GHPoint(43.727687, 7.418737));
        request.setAlgorithm(ASTAR).setVehicle(vehicle).setWeighting(weightCalcStr);
        request.setPathDetails(Collections.singletonList(Parameters.Details.AVERAGE_SPEED));

        GHResponse rsp = hopper.route(request);

        PathWrapper arsp = rsp.getBest();
        Map<String, List<PathDetail>> details = arsp.getPathDetails();
        assertEquals(1, details.size());
        List<PathDetail> detailList = details.get(Parameters.Details.AVERAGE_SPEED);
        assertEquals(9, detailList.size());
        assertEquals(5.0, detailList.get(0).getValue());
        assertEquals(0, detailList.get(0).getFirst());
        assertEquals(3.0, detailList.get(1).getValue());
        assertEquals(arsp.getPoints().size() - 1, detailList.get(8).getLast());
    }

    @Test
    public void testMonacoEnforcedDirection() {
        GHRequest req = new GHRequest().
                addPoint(new GHPoint(43.741069, 7.426854), 0.).
                addPoint(new GHPoint(43.744445, 7.429483), 190.).
                setVehicle(vehicle).setWeighting("fastest");
        req.getHints().put(Routing.HEADING_PENALTY, "300");
        GHResponse rsp = hopper.route(req);

        PathWrapper arsp = rsp.getBest();
        assertEquals(839., arsp.getDistance(), 10.);
        assertEquals(27, arsp.getPoints().getSize());
    }

    @Test
    public void testMonacoMaxVisitedNodes() {
        GHPoint from = new GHPoint(43.741069, 7.426854);
        GHPoint to = new GHPoint(43.744445, 7.429483);
        GHRequest req = new GHRequest().
                addPoint(from).
                addPoint(to).
                setVehicle(vehicle).setWeighting("fastest");
        req.getHints().put(Routing.MAX_VISITED_NODES, 5);
        GHResponse rsp = hopper.route(req);

        assertTrue(rsp.hasErrors());

        req = new GHRequest().
                addPoint(from).
                addPoint(to).
                setVehicle(vehicle).setWeighting("fastest");
        rsp = hopper.route(req);

        assertFalse(rsp.hasErrors());
    }

    @Test
    public void testMonacoNonChMaxWaypointDistance() {
        GHPoint from = new GHPoint(43.741069, 7.426854);
        GHPoint to = new GHPoint(43.727697, 7.419199);

        GHRequest req = new GHRequest().
                addPoint(from).
                addPoint(to).
                setVehicle(vehicle).setWeighting("fastest");

        // Fail since points are too far
        hopper.setNonChMaxWaypointDistance(1000);
        GHResponse rsp = hopper.route(req);

        assertTrue(rsp.hasErrors());

        // Suceed since points are not far anymore
        hopper.setNonChMaxWaypointDistance(Integer.MAX_VALUE);
        rsp = hopper.route(req);

        assertFalse(rsp.hasErrors());
    }

    @Test
    public void testMonacoNonChMaxWaypointDistanceMultiplePoints() {
        GHPoint from = new GHPoint(43.741069, 7.426854);
        GHPoint via = new GHPoint(43.744445, 7.429483);
        GHPoint to = new GHPoint(43.727697, 7.419199);

        GHRequest req = new GHRequest().
                addPoint(from).
                addPoint(via).
                addPoint(to).
                setVehicle(vehicle).setWeighting("fastest");

        // Fail since points are too far
        hopper.setNonChMaxWaypointDistance(1000);
        GHResponse rsp = hopper.route(req);

        assertTrue(rsp.hasErrors());
        PointDistanceExceededException exception = (PointDistanceExceededException) rsp.getErrors().get(0);
        assertEquals(2, exception.getDetails().get("to"));

        // Suceed since points are not far anymore
        hopper.setNonChMaxWaypointDistance(Integer.MAX_VALUE);
        rsp = hopper.route(req);

        assertFalse(rsp.hasErrors());
    }

    @Test
    public void testMonacoStraightVia() {
        GHRequest rq = new GHRequest().
                addPoint(new GHPoint(43.741069, 7.426854)).
                addPoint(new GHPoint(43.740371, 7.426946)).
                addPoint(new GHPoint(43.740794, 7.427294)).
                setVehicle(vehicle).setWeighting("fastest");
        rq.getHints().put(Routing.PASS_THROUGH, true);
        GHResponse rsp = hopper.route(rq);

        PathWrapper arsp = rsp.getBest();
        assertEquals(297, arsp.getDistance(), 5.);
        assertEquals(23, arsp.getPoints().getSize());

        // test if start and first point are identical leading to an empty path, #788
        rq = new GHRequest().
                addPoint(new GHPoint(43.741069, 7.426854)).
                addPoint(new GHPoint(43.741069, 7.426854)).
                addPoint(new GHPoint(43.740371, 7.426946)).
                setVehicle(vehicle).setWeighting("fastest");
        rq.getHints().put(Routing.PASS_THROUGH, true);
        rsp = hopper.route(rq);
        assertEquals(91, rsp.getBest().getDistance(), 5.);
    }

    @Test
    public void testSRTMWithInstructions() {
        GraphHopper tmpHopper = new GraphHopperOSM().
                setOSMFile(osmFile).
                setStoreOnFlush(true).
                setCHEnabled(false).
                setGraphHopperLocation(tmpGraphFile).
                setEncodingManager(EncodingManager.create(importVehicles));

        tmpHopper.setElevationProvider(new SRTMProvider(DIR));
        tmpHopper.importOrLoad();

        GHResponse rsp = tmpHopper.route(new GHRequest(43.730729, 7.421288, 43.727697, 7.419199).
                setAlgorithm(ASTAR).setVehicle(vehicle).setWeighting(weightCalcStr));

        PathWrapper arsp = rsp.getBest();
        assertEquals(1625.4, arsp.getDistance(), .1);
        assertEquals(54, arsp.getPoints().getSize());
        assertTrue(arsp.getPoints().is3D());

        InstructionList il = arsp.getInstructions();
        assertEquals(12, il.size());
        assertTrue(il.get(0).getPoints().is3D());

        String str = arsp.getPoints().toString();

        assertEquals("(43.73068455771767,7.421283689825812,62.0), (43.73067957305937,7.421382123709815,66.0), " +
                        "(43.73109792316924,7.421546222751131,45.0), (43.73129908884985,7.421589994913116,45.0), " +
                        "(43.731327028527716,7.421414533736137,45.0), (43.73125047381037,7.421366291225693,45.0), " +
                        "(43.73128213877862,7.421115579183003,52.0), (43.731362232521825,7.421145381506057,52.0), " +
                        "(43.731371359483255,7.421123216028286,52.0), (43.731485725897976,7.42117332118392,52.0), " +
                        "(43.731575132867135,7.420868778695214,52.0), (43.73160605277731,7.420824820268709,52.0), " +
                        "(43.7316401391843,7.420850152243305,52.0), (43.731674039326776,7.421050014072285,52.0), " +
                        "(43.731627473197,7.4214635213046565,45.0)",
                str.substring(0, 661));

        assertEquals("(43.727778875703635,7.418772930326453,11.0), (43.72768239068275,7.419007064826944,11.0), (43.727680946587874,7.419198768422206,11.0)",
                str.substring(str.length() - 132));

        assertEquals(99, arsp.getAscend(), 1e-1);
        assertEquals(150, arsp.getDescend(), 1e-1);

        assertEquals(54, arsp.getPoints().size());
        assertEquals(new GHPoint3D(43.73068455771767, 7.421283689825812, 62.0), arsp.getPoints().get(0));
        assertEquals(new GHPoint3D(43.727680946587874, 7.4191987684222065, 11.0), arsp.getPoints().get(arsp.getPoints().size() - 1));

        assertEquals(62, arsp.getPoints().get(0).getElevation(), 1e-2);
        assertEquals(66, arsp.getPoints().get(1).getElevation(), 1e-2);
        assertEquals(52, arsp.getPoints().get(10).getElevation(), 1e-2);
    }

    @Test
    public void testSRTMWithoutTunnelInterpolation() {
        GraphHopper tmpHopper = new GraphHopperOSM().setOSMFile(osmFile).setStoreOnFlush(true)
                .setCHEnabled(false).setGraphHopperLocation(tmpGraphFile)
                .setEncodingManager(EncodingManager.start().add(new OSMRoadEnvironmentParser() {
                    @Override
                    public IntsRef handleWayTags(IntsRef edgeFlags, ReaderWay readerWay, EncodingManager.Access access, IntsRef relationFlags) {
                        // do not change RoadEnvironment to avoid triggering tunnel interpolation - is this a valid use case after #TODONOW?
                        return edgeFlags;
                    }
                }).addAll(new DefaultFlagEncoderFactory(), importVehicles).build());

        tmpHopper.setElevationProvider(new SRTMProvider(DIR));
        tmpHopper.importOrLoad();

        GHResponse rsp = tmpHopper.route(new GHRequest(43.74056471749763, 7.4299266210693755,
                43.73790260334179, 7.427984089259056).setAlgorithm(ASTAR)
                .setVehicle(vehicle).setWeighting(weightCalcStr));
        PathWrapper arsp = rsp.getBest();
        assertEquals(356.5, arsp.getDistance(), .1);
        PointList pointList = arsp.getPoints();
        assertEquals(6, pointList.getSize());
        assertTrue(pointList.is3D());

        assertEquals(20.0, pointList.getEle(0), .1);
        assertEquals(23.0, pointList.getEle(1), .1);
        assertEquals(23.0, pointList.getEle(2), .1);
        assertEquals(41.0, pointList.getEle(3), .1);
        assertEquals(19.0, pointList.getEle(4), .1);
        assertEquals(26.5, pointList.getEle(5), .1);
    }

    @Test
    public void testSRTMWithTunnelInterpolation() {
        GraphHopper tmpHopper = new GraphHopperOSM().setOSMFile(osmFile).setStoreOnFlush(true)
                .setCHEnabled(false).setGraphHopperLocation(tmpGraphFile)
                .setEncodingManager(new EncodingManager.Builder().addAll(new DefaultFlagEncoderFactory(), importMultipleVehicles).build());

        tmpHopper.setElevationProvider(new SRTMProvider(DIR));
        tmpHopper.importOrLoad();

        GHResponse rsp = tmpHopper.route(new GHRequest(43.74056471749763, 7.4299266210693755,
                43.73790260334179, 7.427984089259056).setAlgorithm(ASTAR)
                .setVehicle(vehicle).setWeighting(weightCalcStr));
        PathWrapper arsp = rsp.getBest();
        // Without interpolation: 356.5
        assertEquals(351, arsp.getDistance(), .1);
        PointList pointList = arsp.getPoints();
        assertEquals(6, pointList.getSize());
        assertTrue(pointList.is3D());

        assertEquals(18, pointList.getEle(0), .1);
        assertEquals(19.04, pointList.getEle(1), .1);
        assertEquals(21.67, pointList.getEle(2), .1);
        assertEquals(25.03, pointList.getEle(3), .1);
        assertEquals(28.65, pointList.getEle(4), .1);
        assertEquals(31.32, pointList.getEle(5), .1);
    }

    @Test
    public void testKremsCyclewayInstructionsWithWayTypeInfo() {
        String tmpOsmFile = DIR + "/krems.osm.gz";
        String tmpVehicle = "bike";
        String tmpImportVehicles = "foot,bike";
        String tmpWeightCalcStr = "fastest";

        GraphHopper tmpHopper = new GraphHopperOSM().
                setOSMFile(tmpOsmFile).
                setStoreOnFlush(true).
                setCHEnabled(false).
                setGraphHopperLocation(tmpGraphFile).
                setEncodingManager(EncodingManager.create(tmpImportVehicles)).
                importOrLoad();

        Translation tr = tmpHopper.getTranslationMap().getWithFallBack(Locale.US);
        GHResponse rsp = tmpHopper.route(new GHRequest(48.410987, 15.599492, 48.383419, 15.659294).
                setVehicle(tmpVehicle).setWeighting(tmpWeightCalcStr));
        assertFalse(rsp.hasErrors());
        PathWrapper arsp = rsp.getBest();
        assertEquals(6932.2, arsp.getDistance(), .1);
        assertEquals(106, arsp.getPoints().getSize());

        InstructionList il = arsp.getInstructions();
        assertEquals(22, il.size());

        assertEquals("continue onto Obere Landstraße", il.get(0).getTurnDescription(tr));
        assertEquals("get off the bike", il.get(0).getAnnotation().getMessage());
        assertEquals(69.28, (Double) il.get(0).getExtraInfoJSON().get("heading"), .01);
        assertEquals("turn left onto Kirchengasse", il.get(1).getTurnDescription(tr));
        assertEquals("get off the bike", il.get(1).getAnnotation().getMessage());

        assertEquals("turn right onto Pfarrplatz", il.get(2).getTurnDescription(tr));
        assertEquals("turn right onto Margarethenstraße", il.get(3).getTurnDescription(tr));
        assertEquals("keep left onto Hoher Markt", il.get(5).getTurnDescription(tr));
        assertEquals("turn right onto Wegscheid", il.get(7).getTurnDescription(tr));
        assertEquals("turn right onto Ringstraße, L73", il.get(9).getTurnDescription(tr));
        assertEquals("keep left onto Eyblparkstraße", il.get(10).getTurnDescription(tr));
        assertEquals("keep left onto Austraße", il.get(11).getTurnDescription(tr));
        assertEquals("keep left onto Rechte Kremszeile", il.get(12).getTurnDescription(tr));
        //..
        assertEquals("turn right onto Treppelweg", il.get(18).getTurnDescription(tr));
        assertEquals("cycleway", il.get(18).getAnnotation().getMessage());

        // do not return 'get off bike' for foot
        rsp = tmpHopper.route(new GHRequest(48.410987, 15.599492, 48.411172, 15.600371).
                setAlgorithm(ASTAR).setVehicle("foot").setWeighting(tmpWeightCalcStr));
        assertFalse(rsp.hasErrors());
        il = rsp.getBest().getInstructions();
        assertEquals("continue onto Obere Landstraße", il.get(0).getTurnDescription(tr));
        assertEquals("", il.get(0).getAnnotation().getMessage());
    }

    @Test
    public void testRoundaboutInstructionsWithCH() {
        String tmpOsmFile = DIR + "/monaco.osm.gz";
        String tmpVehicle = "car";
        String tmpImportVehicles = "car,bike";
        String tmpWeightCalcStr = "fastest";

        GraphHopper tmpHopper = new GraphHopperOSM().
                setOSMFile(tmpOsmFile).
                setStoreOnFlush(true).
                setGraphHopperLocation(tmpGraphFile).
                setEncodingManager(EncodingManager.create(tmpImportVehicles)).
                importOrLoad();

        assertEquals(tmpVehicle, tmpHopper.getDefaultVehicle().toString());

        assertEquals(2, tmpHopper.getCHFactoryDecorator().getPreparations().size());

        GHResponse rsp = tmpHopper.route(new GHRequest(43.745084, 7.430513, 43.745247, 7.430347)
                .setVehicle(tmpVehicle).setWeighting(tmpWeightCalcStr));

        PathWrapper arsp = rsp.getBest();
        assertEquals(2, ((RoundaboutInstruction) arsp.getInstructions().get(1)).getExitNumber());

        rsp = tmpHopper.route(new GHRequest(43.745968, 7.42907, 43.745832, 7.428614)
                .setVehicle(tmpVehicle).setWeighting(tmpWeightCalcStr));
        arsp = rsp.getBest();
        assertEquals(2, ((RoundaboutInstruction) arsp.getInstructions().get(1)).getExitNumber());

        rsp = tmpHopper.route(new GHRequest(43.745948, 7.42914, 43.746173, 7.428834)
                .setVehicle(tmpVehicle).setWeighting(tmpWeightCalcStr));
        arsp = rsp.getBest();
        assertEquals(1, ((RoundaboutInstruction) arsp.getInstructions().get(1)).getExitNumber());

        rsp = tmpHopper.route(new GHRequest(43.735817, 7.417096, 43.735666, 7.416587)
                .setVehicle(tmpVehicle).setWeighting(tmpWeightCalcStr));
        arsp = rsp.getBest();
        assertEquals(2, ((RoundaboutInstruction) arsp.getInstructions().get(1)).getExitNumber());
    }

    @Test
    public void testCircularJunctionInstructionsWithCH() {
        String tmpOsmFile = DIR + "/berlin-siegessaeule.osm.gz";
        String tmpVehicle = "car";
        String tmpImportVehicles = "car,bike";
        String tmpWeightCalcStr = "fastest";

        GraphHopper tmpHopper = new GraphHopperOSM().
                setOSMFile(tmpOsmFile).
                setStoreOnFlush(true).
                setGraphHopperLocation(tmpGraphFile).
                setEncodingManager(EncodingManager.create(tmpImportVehicles)).
                importOrLoad();

        assertEquals(tmpVehicle, tmpHopper.getDefaultVehicle().toString());

        assertEquals(2, tmpHopper.getCHFactoryDecorator().getPreparations().size());

        GHResponse rsp = tmpHopper.route(new GHRequest(52.513505, 13.350443, 52.513505, 13.350245)
                .setVehicle(tmpVehicle).setWeighting(tmpWeightCalcStr));

        Instruction instr = rsp.getBest().getInstructions().get(1);
        assertTrue(instr instanceof RoundaboutInstruction);
        assertEquals(5, ((RoundaboutInstruction) instr).getExitNumber());
    }


    @Test
    public void testMultipleVehiclesWithCH() {
        String tmpOsmFile = DIR + "/monaco.osm.gz";
        GraphHopper tmpHopper = new GraphHopperOSM().
                setOSMFile(tmpOsmFile).
                setStoreOnFlush(true).
                setGraphHopperLocation(tmpGraphFile).
                setEncodingManager(EncodingManager.create("bike,car")).
                importOrLoad();
        assertEquals("bike", tmpHopper.getDefaultVehicle().toString());
        checkMultiVehiclesWithCH(tmpHopper);
        tmpHopper.close();

        tmpHopper.clean();
        // new instance, try different order, resulting only in different default vehicle
        tmpHopper = new GraphHopperOSM().
                setOSMFile(tmpOsmFile).
                setStoreOnFlush(true).
                setGraphHopperLocation(tmpGraphFile).
                setEncodingManager(EncodingManager.create("car,bike")).
                importOrLoad();
        assertEquals("car", tmpHopper.getDefaultVehicle().toString());
        checkMultiVehiclesWithCH(tmpHopper);
        tmpHopper.close();
    }

    private void checkMultiVehiclesWithCH(GraphHopper tmpHopper) {
        String str = tmpHopper.getEncodingManager().toString();
        GHResponse rsp = tmpHopper.route(new GHRequest(43.73005, 7.415707, 43.741522, 7.42826)
                .setVehicle("car"));
        PathWrapper arsp = rsp.getBest();
        assertFalse("car routing for " + str + " should not have errors:" + rsp.getErrors(), rsp.hasErrors());
        assertEquals(207, arsp.getTime() / 1000f, 1);
        assertEquals(2838, arsp.getDistance(), 1);

        rsp = tmpHopper.route(new GHRequest(43.73005, 7.415707, 43.741522, 7.42826)
                .setVehicle("bike"));
        arsp = rsp.getBest();
        assertFalse("bike routing for " + str + " should not have errors:" + rsp.getErrors(), rsp.hasErrors());
        assertEquals(494, arsp.getTime() / 1000f, 1);
        assertEquals(2192, arsp.getDistance(), 1);

        rsp = tmpHopper.route(new GHRequest(43.73005, 7.415707, 43.741522, 7.42826)
                .setVehicle("foot"));
        assertTrue("only bike and car were imported. foot request should fail", rsp.hasErrors());

        GHRequest req = new GHRequest().
                addPoint(new GHPoint(43.741069, 7.426854), 0.).
                addPoint(new GHPoint(43.744445, 7.429483), 190.).
                setVehicle("bike").setWeighting("fastest");

        rsp = hopper.route(req);
        assertTrue("heading not allowed for CH enabled graph", rsp.hasErrors());
    }

    @Test
    public void testIfCHIsUsed() {
        // route directly after import
        executeCHFootRoute(false);

        // now only load is called
        executeCHFootRoute(false);
    }

    private void executeCHFootRoute(boolean sort) {
        String tmpOsmFile = DIR + "/monaco.osm.gz";
        String tmpImportVehicles = "foot";

        GraphHopper tmpHopper = new GraphHopperOSM().
                setOSMFile(tmpOsmFile).
                setStoreOnFlush(true).
                setSortGraph(sort).
                setGraphHopperLocation(tmpGraphFile).
                setEncodingManager(EncodingManager.create(tmpImportVehicles));
        tmpHopper.getCHFactoryDecorator().setCHProfileStrings(weightCalcStr);
        tmpHopper.importOrLoad();

        // same query as in testMonacoWithInstructions
        GHResponse rsp = tmpHopper.route(new GHRequest(43.727687, 7.418737, 43.74958, 7.436566).
                setVehicle(vehicle));

        PathWrapper bestPath = rsp.getBest();
        // identify the number of counts to compare with none-CH foot route which had nearly 700 counts
        long sum = rsp.getHints().getLong("visited_nodes.sum", 0);
        assertNotEquals(sum, 0);
        assertTrue("Too many nodes visited " + sum, sum < 120);
        assertEquals(3437.6, bestPath.getDistance(), .1);
        assertEquals(87, bestPath.getPoints().getSize());

        tmpHopper.close();
    }

    @Test
    public void testSortWhileImporting() {
        // route after importing a sorted graph
        executeCHFootRoute(true);

        // route after loading a sorted graph
        executeCHFootRoute(false);
    }

    @Test
    public void testRoundTour() {
        GHRequest rq = new GHRequest().
                addPoint(new GHPoint(43.741069, 7.426854), 50).
                setVehicle(vehicle).setWeighting("fastest").
                setAlgorithm(ROUND_TRIP);
        rq.getHints().put(RoundTrip.DISTANCE, 1000);
        rq.getHints().put(RoundTrip.SEED, 0);

        GHResponse rsp = hopper.route(rq);

        assertEquals(1, rsp.getAll().size());
        PathWrapper pw = rsp.getBest();
        assertEquals(1.49, rsp.getBest().getDistance() / 1000f, .01);
        assertEquals(19, rsp.getBest().getTime() / 1000f / 60, 1);
        assertEquals(68, pw.getPoints().size());
    }

    @Test
    public void testPathDetails1216() {
        GraphHopper tmpHopper = new GraphHopperOSM().
                setOSMFile(DIR + "/north-bayreuth.osm.gz").
                setCHEnabled(false).
                setGraphHopperLocation(tmpGraphFile).
                setEncodingManager(EncodingManager.create("car"));
        tmpHopper.importOrLoad();

        GHRequest req = new GHRequest().
                addPoint(new GHPoint(49.984352, 11.498802)).
                // This is exactly between two edges with different speed values
                        addPoint(new GHPoint(49.984565, 11.499188)).
                        addPoint(new GHPoint(49.9847, 11.499612)).
                        setVehicle("car").setWeighting("fastest").
                        setPathDetails(Collections.singletonList(Parameters.Details.AVERAGE_SPEED));

        GHResponse rsp = tmpHopper.route(req);

        assertFalse(rsp.hasErrors());
    }

    @Test
    public void testPathDetailsSamePoint() {
        GraphHopper tmpHopper = new GraphHopperOSM().
                setOSMFile(DIR + "/north-bayreuth.osm.gz").
                setCHEnabled(false).
                setGraphHopperLocation(tmpGraphFile).
                setEncodingManager(EncodingManager.create("car"));
        tmpHopper.importOrLoad();

        GHRequest req = new GHRequest().
                addPoint(new GHPoint(49.984352, 11.498802)).
                addPoint(new GHPoint(49.984352, 11.498802)).
                setVehicle("car").setWeighting("fastest").
                setPathDetails(Collections.singletonList(Parameters.Details.AVERAGE_SPEED));

        GHResponse rsp = tmpHopper.route(req);

        assertFalse(rsp.hasErrors());
    }

    @Test
    public void testFlexMode_631() {
        String tmpOsmFile = DIR + "/monaco.osm.gz";

        GraphHopper tmpHopper = new GraphHopperOSM().
                setOSMFile(tmpOsmFile).
                setStoreOnFlush(true).
                setGraphHopperLocation(tmpGraphFile).
                setEncodingManager(EncodingManager.create("car"));

        tmpHopper.getCHFactoryDecorator().setEnabled(true).
                setCHProfilesAsStrings(Collections.singletonList("fastest")).
                setDisablingAllowed(true);

        tmpHopper.getLMFactoryDecorator().setEnabled(true).
                setWeightingsAsStrings(Collections.singletonList("fastest|maximum=2000")).
                setDisablingAllowed(true);

        tmpHopper.importOrLoad();

        GHRequest req = new GHRequest(43.727687, 7.418737, 43.74958, 7.436566).
                setVehicle("car");
        req.getHints().put(Landmark.DISABLE, true);
        req.getHints().put(CH.DISABLE, false);

        GHResponse rsp = tmpHopper.route(req);
        long chSum = rsp.getHints().getLong("visited_nodes.sum", 0);
        assertTrue("Too many visited nodes for ch mode " + chSum, chSum < 60);
        PathWrapper bestPath = rsp.getBest();
        assertEquals(3587, bestPath.getDistance(), 1);
        assertEquals(89, bestPath.getPoints().getSize());

        // request flex mode
        req.setAlgorithm(Parameters.Algorithms.ASTAR_BI);
        req.getHints().put(Landmark.DISABLE, true);
        req.getHints().put(CH.DISABLE, true);
        rsp = tmpHopper.route(req);
        long flexSum = rsp.getHints().getLong("visited_nodes.sum", 0);
        assertTrue("Too few visited nodes for flex mode " + flexSum, flexSum > 60);

        bestPath = rsp.getBest();
        assertEquals(3587, bestPath.getDistance(), 1);
        assertEquals(89, bestPath.getPoints().getSize());

        // request hybrid mode
        req.getHints().put(Landmark.DISABLE, false);
        req.getHints().put(CH.DISABLE, true);
        rsp = tmpHopper.route(req);

        long hSum = rsp.getHints().getLong("visited_nodes.sum", 0);
        // hybrid is better than CH: 40 vs. 42 !
        assertTrue("Visited nodes for hybrid mode should be different to CH but " + hSum + "==" + chSum, hSum != chSum);
        assertTrue("Too many visited nodes for hybrid mode " + hSum + ">=" + flexSum, hSum < flexSum);

        bestPath = rsp.getBest();
        assertEquals(3587, bestPath.getDistance(), 1);
        assertEquals(89, bestPath.getPoints().getSize());

        // combining hybrid & speed mode is currently not possible and should be avoided: #1082
    }

    @Test
    public void testTurnCostsOnOff() {
        GraphHopper tmpHopper = new GraphHopperOSM().
                setOSMFile(DIR + "/moscow.osm.gz").
                setStoreOnFlush(true).
                setCHEnabled(false).
                setGraphHopperLocation(tmpGraphFile).
                setEncodingManager(EncodingManager.create("car|turn_costs=true"));
        tmpHopper.importOrLoad();

        // no edge_based parameter -> use edge-based (since encoder supports it and no CH)
        assertMoscowEdgeBased(tmpHopper, "none", false);
        // edge_based=false -> use node-based
        assertMoscowNodeBased(tmpHopper, "false", false);
        // edge_based=true -> use edge-based
        assertMoscowEdgeBased(tmpHopper, "true", false);
    }

    @Test
    public void testTurnCostsOnOffCH() {
        GraphHopper tmpHopper = new GraphHopperOSM().
                setOSMFile(DIR + "/moscow.osm.gz").
                setStoreOnFlush(true).
                setCHEnabled(true).
                setGraphHopperLocation(tmpGraphFile).
                setEncodingManager(EncodingManager.create("car|turn_costs=true"));
        tmpHopper.getCHFactoryDecorator().setDisablingAllowed(true);
        tmpHopper.getCHFactoryDecorator().setEdgeBasedCHMode(CHAlgoFactoryDecorator.EdgeBasedCHMode.EDGE_AND_NODE);
        tmpHopper.importOrLoad();

        // no edge_based parameter -> use edge-based (because its there)
        assertMoscowEdgeBased(tmpHopper, "none", true);
        // edge_based=false -> use node-based
        assertMoscowNodeBased(tmpHopper, "false", true);
        // edge_based=true -> use edge-based
        assertMoscowEdgeBased(tmpHopper, "true", true);
    }

    @Test
    public void testCHOnOffWithTurnCosts() {
        GraphHopper tmpHopper = new GraphHopperOSM().
                setOSMFile(DIR + "/moscow.osm.gz").
                setStoreOnFlush(true).
                setCHEnabled(true).
                setGraphHopperLocation(tmpGraphFile).
                setEncodingManager(EncodingManager.create("car|turn_costs=true"));
        tmpHopper.getCHFactoryDecorator()
                .setEdgeBasedCHMode(CHAlgoFactoryDecorator.EdgeBasedCHMode.EDGE_OR_NODE)
                .setDisablingAllowed(true);
        tmpHopper.importOrLoad();

        // with CH -> edge-based
        GHResponse rsp1 = assertMoscowEdgeBased(tmpHopper, "true", false);
        // without CH -> also edge-based
        GHResponse rsp2 = assertMoscowEdgeBased(tmpHopper, "true", true);
        // just a quick check that we did not run the same algorithm twice
        assertNotEquals(rsp1.getHints().get("visited_nodes.sum", "_"), rsp2.getHints().get("visited_nodes.sum", "_"));
    }

    @Test
    public void testNodeBasedCHOnlyButTurnCostForNonCH() {
        // before edge-based CH was added a common case was to use edge-based without CH and CH for node-based
        GraphHopper tmpHopper = new GraphHopperOSM().
                setOSMFile(DIR + "/moscow.osm.gz").
                setStoreOnFlush(true).
                setCHEnabled(true).
                setGraphHopperLocation(tmpGraphFile).
                setEncodingManager(EncodingManager.create("car|turn_costs=true"));
        tmpHopper.getCHFactoryDecorator()
                .setEdgeBasedCHMode(CHAlgoFactoryDecorator.EdgeBasedCHMode.OFF)
                .setDisablingAllowed(true);
        tmpHopper.importOrLoad();

        // without CH -> use edge-based unless disabled explicitly
        assertMoscowEdgeBased(tmpHopper, "none", false);
        assertMoscowEdgeBased(tmpHopper, "true", false);
        assertMoscowNodeBased(tmpHopper, "false", false);

        // with CH -> use node-based unless edge_based is enabled explicitly (which should give an error)
        assertMoscowNodeBased(tmpHopper, "none", true);
        assertMoscowNodeBased(tmpHopper, "false", true);
        GHResponse rsp = runMoscow(tmpHopper, "true", true);
        assertEquals(1, rsp.getErrors().size());
        assertTrue("unexpected error: " + rsp.getErrors().toString(), rsp.getErrors().toString().contains(
                "Cannot find matching CH profile for your request.\nrequested:  *|car|edge_based=true|u_turn_costs=*\navailable: [fastest|car|edge_based=false|u_turn_costs=-1]"));
    }

    @Test
    public void testEdgeBasedByDefaultIfOnlyEdgeBased() {
        // when there is only one edge-based CH profile, there is no need to specify edge_based=true explicitly,
        // see #1637
        GraphHopper tmpHopper = new GraphHopperOSM().
                setOSMFile(DIR + "/moscow.osm.gz").
                setStoreOnFlush(true).
                setCHEnabled(true).
                setGraphHopperLocation(tmpGraphFile).
                setEncodingManager(EncodingManager.create("car|turn_costs=true"));
        tmpHopper.getCHFactoryDecorator().setDisablingAllowed(true);
        tmpHopper.getCHFactoryDecorator().setEdgeBasedCHMode(CHAlgoFactoryDecorator.EdgeBasedCHMode.EDGE_OR_NODE);
        tmpHopper.importOrLoad();

        // even when we omit the edge_based parameter we get edge-based CH, unless we disable it explicitly
        assertMoscowEdgeBased(tmpHopper, "none", true);
        assertMoscowEdgeBased(tmpHopper, "true", true);
        GHResponse rsp = runMoscow(tmpHopper, "false", true);
        assertTrue(rsp.hasErrors());
        assertTrue("unexpected error: " + rsp.getErrors(), rsp.getErrors().toString().contains(
                "Cannot find matching CH profile for your request.\nrequested:  *|car|edge_based=false|u_turn_costs=*\navailable: [fastest|car|edge_based=true|u_turn_costs=-1]"));
    }

    private GHResponse assertMoscowNodeBased(GraphHopper tmpHopper, String edgeBasedParam, boolean ch) {
        GHResponse rsp = runMoscow(tmpHopper, edgeBasedParam, ch);
        assertEquals(400, rsp.getBest().getDistance(), 1);
        return rsp;
    }

    private GHResponse assertMoscowEdgeBased(GraphHopper tmpHopper, String edgeBasedParam, boolean ch) {
        GHResponse rsp = runMoscow(tmpHopper, edgeBasedParam, ch);
        assertEquals(1044, rsp.getBest().getDistance(), 1);
        return rsp;
    }

    private GHResponse runMoscow(GraphHopper tmpHopper, String edgeBasedParam, boolean ch) {
        GHRequest req = new GHRequest(55.813357, 37.5958585, 55.811042, 37.594689);
        if (edgeBasedParam.equals("true") || edgeBasedParam.equals("false")) {
            req.getHints().put(Routing.EDGE_BASED, edgeBasedParam);
        } else {
            req.getHints().remove(Routing.EDGE_BASED);
        }
        req.getHints().put(CH.DISABLE, !ch);
        return tmpHopper.route(req);
    }

    @Test
    public void testEdgeBasedRequiresTurnCostSupport() {
        GHPoint p = new GHPoint(43.727687, 7.418737);
        GHPoint q = new GHPoint(43.74958, 7.436566);
        GHRequest req = new GHRequest(p, q);
        req.getHints().put(Routing.EDGE_BASED, true);
        req.setVehicle("foot");
        GHResponse rsp = hopper.route(req);
        assertTrue(rsp.hasErrors());
        assertTrue("using edge-based for encoder without turncost support should be an error, but got:\n" + rsp.getErrors(),
                rsp.getErrors().toString().contains("You need to set up a turn cost storage to make use of edge_based=true, e.g. use car|turn_costs=true"));
    }

    @Test
    public void testEncoderWithTurnCostSupport_stillAllows_nodeBasedRouting() {
        // see #1698
        GraphHopper tmpHopper = new GraphHopperOSM().
                setOSMFile(DIR + "/moscow.osm.gz").
                setGraphHopperLocation(tmpGraphFile).
                setCHEnabled(false).
                setEncodingManager(EncodingManager.create("foot,car|turn_costs=true"));
        tmpHopper.importOrLoad();
        GHPoint p = new GHPoint(55.813357, 37.5958585);
        GHPoint q = new GHPoint(55.811042, 37.594689);
        GHRequest req = new GHRequest(p, q);
        req.setVehicle("foot");
        GHResponse rsp = tmpHopper.route(req);
        assertEquals("there should not be an error, but was: " + rsp.getErrors(), 0, rsp.getErrors().size());
    }

    @Test
    public void testCurbsides() {
        GraphHopper h = new GraphHopperOSM().
                setOSMFile(DIR + "/north-bayreuth.osm.gz").
                setCHEnabled(true).
                setGraphHopperLocation(tmpGraphFile).
                setEncodingManager(EncodingManager.create("car|turn_costs=true"));
        h.getCHFactoryDecorator()
                .setEdgeBasedCHMode(CHAlgoFactoryDecorator.EdgeBasedCHMode.EDGE_OR_NODE);
        h.importOrLoad();

        // depending on the curbside parameters we take very different routes
        GHPoint p = new GHPoint(50.015072, 11.499145);
        GHPoint q = new GHPoint(50.014141, 11.497552);
        final String itz = "Itzgrund";
        final String rotmain = "An den Rotmainauen";
        final String bayreuth = "Bayreuther Straße, KU 18";
        final String kulmbach = "Kulmbacher Straße, KU 18";
        final String adamSeiler = "Adam-Seiler-Straße";
        final String friedhof = "Friedhofsweg";
        assertCurbsidesPath(h, p, q, asList(CURBSIDE_RIGHT, CURBSIDE_RIGHT), 344, asList(itz, rotmain, rotmain));
        assertCurbsidesPath(h, p, q, asList(CURBSIDE_RIGHT, CURBSIDE_LEFT), 1564, asList(itz, rotmain, rotmain, bayreuth, kulmbach, adamSeiler, adamSeiler, friedhof, kulmbach, rotmain));
        assertCurbsidesPath(h, p, q, asList(CURBSIDE_LEFT, CURBSIDE_RIGHT), 1199, asList(itz, bayreuth, kulmbach, adamSeiler, adamSeiler, friedhof, kulmbach, itz, rotmain, rotmain));
        assertCurbsidesPath(h, p, q, asList(CURBSIDE_LEFT, CURBSIDE_LEFT), 266, asList(itz, bayreuth, rotmain));
        // without restricting anything we get the shortest path
        assertCurbsidesPath(h, p, q, asList(CURBSIDE_ANY, CURBSIDE_ANY), 266, asList(itz, bayreuth, rotmain));
        assertCurbsidesPath(h, p, q, asList(CURBSIDE_ANY, ""), 266, asList(itz, bayreuth, rotmain));
        assertCurbsidesPath(h, p, q, Collections.<String>emptyList(), 266, asList(itz, bayreuth, rotmain));

        // when the start/target is the same its a bit unclear how to interpret the curbside parameters. here we decided
        // to return an empty path if the curbsides of start/target are the same or one of the is not specified
        assertCurbsidesPath(h, p, p, asList(CURBSIDE_RIGHT, CURBSIDE_RIGHT), 0, Collections.<String>emptyList());
        assertCurbsidesPath(h, p, p, asList(CURBSIDE_RIGHT, CURBSIDE_ANY), 0, Collections.<String>emptyList());
        assertCurbsidesPath(h, p, p, asList(CURBSIDE_RIGHT, ""), 0, Collections.<String>emptyList());
        assertCurbsidesPath(h, p, p, asList(CURBSIDE_ANY, CURBSIDE_RIGHT), 0, Collections.<String>emptyList());
        assertCurbsidesPath(h, p, p, asList("", CURBSIDE_RIGHT), 0, Collections.<String>emptyList());
        assertCurbsidesPath(h, p, p, asList(CURBSIDE_LEFT, CURBSIDE_LEFT), 0, Collections.<String>emptyList());
        assertCurbsidesPath(h, p, p, asList(CURBSIDE_LEFT, CURBSIDE_ANY), 0, Collections.<String>emptyList());
        assertCurbsidesPath(h, p, p, asList(CURBSIDE_LEFT, ""), 0, Collections.<String>emptyList());
        assertCurbsidesPath(h, p, p, asList(CURBSIDE_ANY, CURBSIDE_LEFT), 0, Collections.<String>emptyList());
        assertCurbsidesPath(h, p, p, asList("", CURBSIDE_LEFT), 0, Collections.<String>emptyList());

        // when going from p to p and one curbside is right and the other is left, we expect driving a loop back to
        // where we came from
        assertCurbsidesPath(h, p, p, asList(CURBSIDE_RIGHT, CURBSIDE_LEFT), 1908, asList(itz, rotmain, rotmain, bayreuth, kulmbach, adamSeiler, adamSeiler, friedhof, kulmbach, rotmain, rotmain, itz));
        assertCurbsidesPath(h, p, p, asList(CURBSIDE_LEFT, CURBSIDE_RIGHT), 855, asList(itz, bayreuth, kulmbach, adamSeiler, adamSeiler, friedhof, kulmbach, itz));
    }

    @Test
    public void testForceCurbsides() {
        GraphHopper h = new GraphHopperOSM().
                setOSMFile(DIR + "/monaco.osm.gz").
                setCHEnabled(true).
                setGraphHopperLocation(tmpGraphFile).
                setEncodingManager(EncodingManager.create("car|turn_costs=true"));
        h.getCHFactoryDecorator()
                .setEdgeBasedCHMode(CHAlgoFactoryDecorator.EdgeBasedCHMode.EDGE_OR_NODE);
        h.importOrLoad();

        // depending on the curbside parameters we take very different routes
        //    p
        //    ---->----
        //            q
        GHPoint p = new GHPoint(43.738399, 7.420782);
        GHPoint q = new GHPoint(43.737949, 7.423523);
        final String boulevard = "Boulevard de Suisse";
        final String avenue = "Avenue de la Costa";
        assertCurbsidesPathError(h, p, q, asList(CURBSIDE_RIGHT, CURBSIDE_RIGHT), "Impossible curbside constraint: 'curbside=right' at point 0", true);
        assertCurbsidesPathError(h, p, q, asList(CURBSIDE_RIGHT, CURBSIDE_LEFT), "Impossible curbside constraint: 'curbside=right' at point 0", true);
        assertCurbsidesPath(h, p, q, asList(CURBSIDE_LEFT, CURBSIDE_RIGHT), 463, asList(boulevard, avenue));
        assertCurbsidesPathError(h, p, q, asList(CURBSIDE_LEFT, CURBSIDE_LEFT), "Impossible curbside constraint: 'curbside=left' at point 1", true);
        // without restricting anything we get the shortest path
        assertCurbsidesPath(h, p, q, asList(CURBSIDE_ANY, CURBSIDE_ANY), 463, asList(boulevard, avenue));
        assertCurbsidesPath(h, p, q, Collections.<String>emptyList(), 463, asList(boulevard, avenue));
        // if we set force_curbside to false impossible curbside constraints will be ignored
        assertCurbsidesPath(h, p, q, asList(CURBSIDE_RIGHT, CURBSIDE_RIGHT), 463, asList(boulevard, avenue), false);
        assertCurbsidesPath(h, p, q, asList(CURBSIDE_RIGHT, CURBSIDE_LEFT), 463, asList(boulevard, avenue), false);
        assertCurbsidesPath(h, p, q, asList(CURBSIDE_LEFT, CURBSIDE_RIGHT), 463, asList(boulevard, avenue), false);
        assertCurbsidesPath(h, p, q, asList(CURBSIDE_LEFT, CURBSIDE_LEFT), 463, asList(boulevard, avenue), false);
    }

    private void assertCurbsidesPath(GraphHopper tmpHopper, GHPoint source, GHPoint target, List<String> curbsides, int expectedDistance, List<String> expectedStreets) {
        assertCurbsidesPath(tmpHopper, source, target, curbsides, expectedDistance, expectedStreets, true);
    }

    private void assertCurbsidesPath(GraphHopper tmpHopper, GHPoint source, GHPoint target, List<String> curbsides, int expectedDistance, List<String> expectedStreets, boolean force) {
        GHResponse rsp = calcCurbsidePath(tmpHopper, source, target, curbsides, force);
        assertFalse(rsp.getErrors().toString(), rsp.hasErrors());
        PathWrapper path = rsp.getBest();
        List<String> streets = new ArrayList<>(path.getInstructions().size());
        for (Instruction instruction : path.getInstructions()) {
            if (!Helper.isEmpty(instruction.getName())) {
                streets.add(instruction.getName());
            }
        }
        assertEquals(expectedStreets, streets);
        assertEquals(expectedDistance, path.getDistance(), 1);
    }

    private void assertCurbsidesPathError(GraphHopper tmpHopper, GHPoint source, GHPoint target, List<String> curbsides, String errorMessage, boolean force) {
        GHResponse rsp = calcCurbsidePath(tmpHopper, source, target, curbsides, force);
        assertTrue(rsp.hasErrors());
        assertTrue("unexpected error. expected message containing: " + errorMessage + ", but got: " +
                rsp.getErrors(), rsp.getErrors().toString().contains(errorMessage));
    }

    private GHResponse calcCurbsidePath(GraphHopper tmpHopper, GHPoint source, GHPoint target, List<String> curbsides, boolean force) {
        GHRequest req = new GHRequest(source, target);
        req.getHints().put(Routing.EDGE_BASED, true);
        req.getHints().put(Routing.FORCE_CURBSIDE, force);
        req.setCurbsides(curbsides);
        return tmpHopper.route(req);
    }

    @Test
    public void testCHWithFiniteUTurnCostsAndMissingWeighting() {
        GraphHopper h = new GraphHopperOSM().
                setDataReaderFile(DIR + "/monaco.osm.gz").
                setCHEnabled(true).
                setGraphHopperLocation(tmpGraphFile).
                setEncodingManager(EncodingManager.create("car|turn_costs=true"));
        h.getCHFactoryDecorator()
                .setCHProfileStrings("fastest|u_turn_costs=40")
                .setEdgeBasedCHMode(CHAlgoFactoryDecorator.EdgeBasedCHMode.EDGE_OR_NODE);
        h.importOrLoad();

        GHPoint p = new GHPoint(43.73397, 7.414173);
        GHPoint q = new GHPoint(43.73222, 7.415557);
        GHRequest req = new GHRequest(p, q);
        // note that we do *not* set the weighting on the request, it will be determined automatically from the
        // CH profile, see #1788
        // we force the start/target directions such that there are u-turns right after we start and right before
        // we reach the target
        req.setCurbsides(Arrays.asList("right", "right"));
        GHResponse res = h.route(req);
        assertFalse("routing should not fail", res.hasErrors());
        assertEquals(266.8, res.getBest().getRouteWeight(), 0.1);
        assertEquals(2116, res.getBest().getDistance(), 1);
        assertEquals(266800, res.getBest().getTime(), 1000);
    }

    @Test
    public void simplifyWithInstructionsAndPathDetails() {
        GraphHopper hopper = new GraphHopperOSM().
                setOSMFile(DIR + "/north-bayreuth.osm.gz").
                setCHEnabled(false).
                setGraphHopperLocation(tmpGraphFile).
                forServer();
        EncodingManager em = new EncodingManager.Builder()
                .setEnableInstructions(true)
                .add(new OSMMaxSpeedParser())
                .add(new CarFlagEncoder())
                .build();
        hopper.setEncodingManager(em);
        hopper.importOrLoad();

        GHRequest req = new GHRequest()
                .addPoint(new GHPoint(50.026932, 11.493201))
                .addPoint(new GHPoint(50.016895, 11.4923))
                .addPoint(new GHPoint(50.003464, 11.49157))
                .setPathDetails(Arrays.asList("street_name", "max_speed"));
        req.putHint("elevation", "true");

        GHResponse rsp = hopper.route(req);
        assertFalse(rsp.getErrors().toString(), rsp.hasErrors());

        PathWrapper path = rsp.getBest();

        // check path was simplified (without it would be more like 58)
        assertEquals(44, path.getPoints().size());

        // check instructions
        InstructionList instructions = path.getInstructions();
        int totalLength = 0;
        for (Instruction instruction : instructions) {
            totalLength += instruction.getLength();
        }
        assertEquals(43, totalLength);
        assertInstruction(instructions.get(0), "KU 11", "[0, 4[", 4, 4);
        assertInstruction(instructions.get(1), "B 85", "[4, 18[", 14, 14);
        // via instructions have length = 0, but the point list must not be empty!
        assertInstruction(instructions.get(2), "", "[18, 19[", 0, 1);
        assertInstruction(instructions.get(3), "B 85", "[18, 35[", 17, 17);
        assertInstruction(instructions.get(4), "", "[35, 37[", 2, 2);
        assertInstruction(instructions.get(5), "KU 18", "[37, 40[", 3, 3);
        assertInstruction(instructions.get(6), "St 2189", "[40, 41[", 1, 1);
        assertInstruction(instructions.get(7), "", "[41, 43[", 2, 2);
        // finish instructions have length = 0, but the point list must not be empty!
        assertInstruction(instructions.get(8), "", "[43, 44[", 0, 1);

        // check max speeds
        List<PathDetail> speeds = path.getPathDetails().get("max_speed");
        assertDetail(speeds.get(0), "null [0, 4]");
        assertDetail(speeds.get(1), "70.0 [4, 7]");
        assertDetail(speeds.get(2), "100.0 [7, 34]");
        assertDetail(speeds.get(3), "80.0 [34, 35]");
        assertDetail(speeds.get(4), "null [35, 40]");
        assertDetail(speeds.get(5), "50.0 [40, 41]");
        assertDetail(speeds.get(6), "null [41, 43]");

        // check street_names
        List<PathDetail> streetNames = path.getPathDetails().get("street_name");
        assertDetail(streetNames.get(0), "KU 11 [0, 4]");
        assertDetail(streetNames.get(1), "B 85 [4, 35]");
        assertDetail(streetNames.get(2), " [35, 37]");
        assertDetail(streetNames.get(3), "KU 18 [37, 40]");
        assertDetail(streetNames.get(4), "St 2189 [40, 41]");
        assertDetail(streetNames.get(5), " [41, 43]");
    }

    private void assertInstruction(Instruction instruction, String expectedName, String expectedInterval, int expectedLength, int expectedPoints) {
        assertEquals(expectedName, instruction.getName());
        assertEquals(expectedInterval, ((ShallowImmutablePointList) instruction.getPoints()).getIntervalString());
        assertEquals(expectedLength, instruction.getLength());
        assertEquals(expectedPoints, instruction.getPoints().size());
    }

    private void assertDetail(PathDetail detail, String expected) {
        assertEquals(expected, detail.toString());
    }

}
