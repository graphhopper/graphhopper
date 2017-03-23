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

import com.graphhopper.reader.dem.SRTMProvider;
import com.graphhopper.reader.osm.GraphHopperOSM;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.util.*;
import com.graphhopper.util.Parameters.CH;
import com.graphhopper.util.Parameters.Landmark;
import com.graphhopper.util.Parameters.Routing;
import com.graphhopper.util.exceptions.PointDistanceExceededException;
import com.graphhopper.util.shapes.GHPoint;
import org.junit.*;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.graphhopper.util.Parameters.Algorithms.*;
import static org.junit.Assert.*;

/**
 * @author Peter Karich
 */
public class GraphHopperIT {

    public static final String DIR = "../core/files";
    private static final String graphFileFoot = "target/graphhopperIT-foot";
    private static final String osmFile = DIR + "/monaco.osm.gz";
    private static final String importVehicles = "foot";
    private static final String genericImportVehicles = "generic,foot";
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
                setEncodingManager(new EncodingManager(importVehicles)).
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
    public void testMonacoWithInstructions() throws Exception {
        GHResponse rsp = hopper.route(new GHRequest(43.727687, 7.418737, 43.74958, 7.436566).
                setAlgorithm(ASTAR).setVehicle(vehicle).setWeighting(weightCalcStr));

        // identify the number of counts to compare with CH foot route
        assertEquals(698, rsp.getHints().getLong("visited_nodes.sum", 0));

        PathWrapper arsp = rsp.getBest();
        assertEquals(3437.6, arsp.getDistance(), .1);
        assertEquals(95, arsp.getPoints().getSize());

        assertEquals(43.7276852, arsp.getWaypoints().getLat(0), 1e-7);
        assertEquals(43.7495432, arsp.getWaypoints().getLat(1), 1e-7);

        InstructionList il = arsp.getInstructions();
        assertEquals(21, il.size());

        List<Map<String, Object>> resultJson = il.createJson();
        // TODO roundabout fine tuning -> enter + leave roundabout (+ two rounabouts -> is it necessary if we do not leave the street?)
        assertEquals("Continue onto Avenue des Guelfes", resultJson.get(0).get("text"));
        assertEquals("Continue onto Avenue des Papalins", resultJson.get(1).get("text"));
        assertEquals("Turn sharp right onto Quai Jean-Charles Rey", resultJson.get(4).get("text"));
        assertEquals("Turn left", resultJson.get(5).get("text"));
        assertEquals("Turn right onto Avenue Albert II", resultJson.get(6).get("text"));

        assertEquals(11, (Double) resultJson.get(0).get("distance"), 1);
        assertEquals(96, (Double) resultJson.get(1).get("distance"), 1);
        assertEquals(178, (Double) resultJson.get(2).get("distance"), 1);
        assertEquals(13, (Double) resultJson.get(3).get("distance"), 1);
        assertEquals(10, (Double) resultJson.get(4).get("distance"), 1);
        assertEquals(42, (Double) resultJson.get(5).get("distance"), 1);

        assertEquals(7, (Long) resultJson.get(0).get("time") / 1000);
        assertEquals(69, (Long) resultJson.get(1).get("time") / 1000);
        assertEquals(128, (Long) resultJson.get(2).get("time") / 1000);
        assertEquals(9, (Long) resultJson.get(3).get("time") / 1000);
        assertEquals(7, (Long) resultJson.get(4).get("time") / 1000);
        assertEquals(30, (Long) resultJson.get(5).get("time") / 1000);

        List<GPXEntry> list = arsp.getInstructions().createGPXList();
        assertEquals(95, list.size());
        final long lastEntryMillis = list.get(list.size() - 1).getTime();
        final long totalResponseMillis = arsp.getTime();
        assertEquals(totalResponseMillis, lastEntryMillis);
    }

    @Test
    public void testAlternativeRoutes() {
        GHRequest req = new GHRequest(43.729057, 7.41251, 43.740298, 7.423561).
                setAlgorithm(ALT_ROUTE).setVehicle(vehicle).setWeighting(weightCalcStr);

        GHResponse rsp = hopper.route(req);
        assertFalse(rsp.hasErrors());
        assertEquals(2, rsp.getAll().size());

        assertEquals(1310, rsp.getAll().get(0).getTime() / 1000);
        assertEquals(1356, rsp.getAll().get(1).getTime() / 1000);

        req.getHints().put("alternative_route.max_paths", "3");
        req.getHints().put("alternative_route.min_plateau_factor", "0.1");
        rsp = hopper.route(req);
        assertFalse(rsp.hasErrors());
        assertEquals(3, rsp.getAll().size());

        assertEquals(1310, rsp.getAll().get(0).getTime() / 1000);
        assertEquals(1356, rsp.getAll().get(1).getTime() / 1000);
        assertEquals(1416, rsp.getAll().get(2).getTime() / 1000);
    }

    @Test
    public void testAlternativeRoutesBikeAndCar() {
        GraphHopper tmpHopper = new GraphHopperOSM().
                setOSMFile(DIR + "/north-bayreuth.osm.gz").
                setCHEnabled(false).
                setGraphHopperLocation(tmpGraphFile).
                setEncodingManager(new EncodingManager("bike, car"));
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
                setEncodingManager(new EncodingManager("car"));
        tmpHopper.importOrLoad();

        GHRequest req = new GHRequest(49.46553, 11.154669, 49.465244, 11.152577).
                setVehicle("car").setWeighting("fastest");

        req.setPointHints(new ArrayList<>(Arrays.asList("Laufamholzstraße, 90482, Nürnberg, Deutschland", "")));
        GHResponse rsp = tmpHopper.route(req);
        assertFalse(rsp.getErrors().toString(), rsp.hasErrors());
        GHPoint snappedPoint = rsp.getBest().getWaypoints().toGHPoint(0);
        assertEquals(49.465686, snappedPoint.getLat(), .000001);
        assertEquals(11.154605, snappedPoint.getLon(), .000001);

        req.setPointHints(new ArrayList<>(Arrays.asList("", "")));
        rsp = tmpHopper.route(req);
        assertFalse(rsp.getErrors().toString(), rsp.hasErrors());
        snappedPoint = rsp.getBest().getWaypoints().toGHPoint(0);
        assertEquals(49.465502, snappedPoint.getLat(), .000001);
        assertEquals(11.154498, snappedPoint.getLon(), .000001);

        // Match to closest edge, since hint was not found
        req.setPointHints(new ArrayList<>(Arrays.asList("xy", "")));
        rsp = tmpHopper.route(req);
        assertFalse(rsp.getErrors().toString(), rsp.hasErrors());
        snappedPoint = rsp.getBest().getWaypoints().toGHPoint(0);
        assertEquals(49.465502, snappedPoint.getLat(), .000001);
        assertEquals(11.154498, snappedPoint.getLon(), .000001);
    }

    @Test
    public void testNorthBayreuthDestination() {
        GraphHopper tmpHopper = new GraphHopperOSM().
                setOSMFile(DIR + "/north-bayreuth.osm.gz").
                setCHEnabled(false).
                setGraphHopperLocation(tmpGraphFile).
                setEncodingManager(new EncodingManager("car,generic", 8));
        tmpHopper.importOrLoad();

        GHRequest req = new GHRequest(49.985307, 11.50628, 49.985731, 11.507465).
                setVehicle("car").setWeighting("fastest");

        GHResponse rsp = tmpHopper.route(req);
        assertFalse(rsp.getErrors().toString(), rsp.hasErrors());
        assertEquals(550, rsp.getBest().getDistance(), 1);

        req = new GHRequest(49.985307, 11.50628, 49.985731, 11.507465).
                setVehicle("generic").setWeighting("generic");

        rsp = tmpHopper.route(req);
        assertFalse(rsp.getErrors().toString(), rsp.hasErrors());
        assertEquals(550, rsp.getBest().getDistance(), 1);
    }

    @Test
    public void testNorthBayreuthBlockeEdges() {
        GraphHopper tmpHopper = new GraphHopperOSM().
                setOSMFile(DIR + "/north-bayreuth.osm.gz").
                setCHEnabled(false).
                setGraphHopperLocation(tmpGraphFile).
                setEncodingManager(new EncodingManager("generic", 8));
        tmpHopper.importOrLoad();

        GHRequest req = new GHRequest(49.985272, 11.506151, 49.986107, 11.507202).
                setVehicle("generic").setWeighting("generic");

        GHResponse rsp = tmpHopper.route(req);
        assertFalse(rsp.getErrors().toString(), rsp.hasErrors());
        assertEquals(122, rsp.getBest().getDistance(), 1);

        // block point 49.985759,11.50687
        req.getHints().put(Routing.BLOCK_AREA, "49.985759,11.50687");
        rsp = tmpHopper.route(req);
        assertFalse(rsp.getErrors().toString(), rsp.hasErrors());
        assertEquals(365, rsp.getBest().getDistance(), 1);

        req = new GHRequest(49.975845, 11.522598, 50.026821, 11.497364).
                setVehicle("generic").setWeighting("generic");

        rsp = tmpHopper.route(req);
        assertFalse(rsp.getErrors().toString(), rsp.hasErrors());
        assertEquals(6684, rsp.getBest().getDistance(), 1);

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
        assertEquals(16674, rsp.getBest().getDistance(), 1);

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
    }

    @Test
    public void testMonacoVia() {
        GHResponse rsp = hopper.route(new GHRequest().
                addPoint(new GHPoint(43.727687, 7.418737)).
                addPoint(new GHPoint(43.74958, 7.436566)).
                addPoint(new GHPoint(43.727687, 7.418737)).
                setAlgorithm(ASTAR).setVehicle(vehicle).setWeighting(weightCalcStr));

        PathWrapper arsp = rsp.getBest();
        assertEquals(6875.2, arsp.getDistance(), .1);
        assertEquals(190, arsp.getPoints().getSize());

        InstructionList il = arsp.getInstructions();
        assertEquals(38, il.size());
        List<Map<String, Object>> resultJson = il.createJson();
        assertEquals("Continue onto Avenue des Guelfes", resultJson.get(0).get("text"));
        assertEquals("Continue onto Avenue des Papalins", resultJson.get(1).get("text"));
        assertEquals("Turn sharp right onto Quai Jean-Charles Rey", resultJson.get(4).get("text"));
        assertEquals("Turn left", resultJson.get(5).get("text"));
        assertEquals("Turn right onto Avenue Albert II", resultJson.get(6).get("text"));

        assertEquals("Stopover 1", resultJson.get(20).get("text"));

        assertEquals("Continue onto Avenue Albert II", resultJson.get(31).get("text"));
        assertEquals("Turn left", resultJson.get(32).get("text"));
        assertEquals("Turn right onto Quai Jean-Charles Rey", resultJson.get(33).get("text"));
        assertEquals("Turn sharp left onto Avenue des Papalins", resultJson.get(34).get("text"));
        assertEquals("Continue onto Avenue des Guelfes", resultJson.get(36).get("text"));
        assertEquals("Finish!", resultJson.get(37).get("text"));

        assertEquals(11, (Double) resultJson.get(0).get("distance"), 1);
        assertEquals(97, (Double) resultJson.get(1).get("distance"), 1);
        assertEquals(178, (Double) resultJson.get(2).get("distance"), 1);
        assertEquals(13, (Double) resultJson.get(3).get("distance"), 1);
        assertEquals(10, (Double) resultJson.get(4).get("distance"), 1);
        assertEquals(42, (Double) resultJson.get(5).get("distance"), 1);

        assertEquals(7, (Long) resultJson.get(0).get("time") / 1000);
        assertEquals(69, (Long) resultJson.get(1).get("time") / 1000);
        assertEquals(128, (Long) resultJson.get(2).get("time") / 1000);
        assertEquals(9, (Long) resultJson.get(3).get("time") / 1000);
        assertEquals(7, (Long) resultJson.get(4).get("time") / 1000);
        assertEquals(30, (Long) resultJson.get(5).get("time") / 1000);

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
        assertEquals("Finish!", arsp.getInstructions().createJson().get(0).get("text"));
        assertEquals(Instruction.FINISH, arsp.getInstructions().createJson().get(0).get("sign"));

        rsp = hopper.route(new GHRequest().
                addPoint(new GHPoint(43.727687, 7.418737)).
                addPoint(new GHPoint(43.727687, 7.418737)).
                addPoint(new GHPoint(43.727687, 7.418737)).
                setAlgorithm(ASTAR).setVehicle(vehicle).setWeighting(weightCalcStr));

        arsp = rsp.getBest();
        assertEquals(0, arsp.getDistance(), .1);
        assertEquals(0, arsp.getRouteWeight(), .1);
        assertEquals(2, arsp.getPoints().getSize());
        assertEquals(2, arsp.getInstructions().size());
        assertEquals(Instruction.REACHED_VIA, arsp.getInstructions().createJson().get(0).get("sign"));
        assertEquals(Instruction.FINISH, arsp.getInstructions().createJson().get(1).get("sign"));
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
        assertEquals(874., arsp.getDistance(), 10.);
        assertEquals(33, arsp.getPoints().getSize());
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
        assertEquals(26, arsp.getPoints().getSize());

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
    public void testSRTMWithInstructions() throws Exception {
        GraphHopper tmpHopper = new GraphHopperOSM().
                setOSMFile(osmFile).
                setStoreOnFlush(true).
                setCHEnabled(false).
                setGraphHopperLocation(tmpGraphFile).
                setEncodingManager(new EncodingManager(importVehicles));

        tmpHopper.setElevationProvider(new SRTMProvider().setCacheDir(new File(DIR)));
        tmpHopper.importOrLoad();

        GHResponse rsp = tmpHopper.route(new GHRequest(43.730729, 7.421288, 43.727697, 7.419199).
                setAlgorithm(ASTAR).setVehicle(vehicle).setWeighting(weightCalcStr));

        PathWrapper arsp = rsp.getBest();
        assertEquals(1626.8, arsp.getDistance(), .1);
        assertEquals(61, arsp.getPoints().getSize());
        assertTrue(arsp.getPoints().is3D());

        InstructionList il = arsp.getInstructions();
        assertEquals(13, il.size());
        assertTrue(il.get(0).getPoints().is3D());

        String str = arsp.getPoints().toString();

        assertEquals("(43.73068455771767,7.421283689825812,62.0), (43.73067957305937,7.421382123709815,66.0), "
                        + "(43.73109792316924,7.421546222751131,45.0), (43.73129908884985,7.421589994913116,45.0), "
                        + "(43.731327028527716,7.421414533736137,45.0), (43.73125047381037,7.421366291225693,45.0), "
                        + "(43.73125457162979,7.421274090288746,52.0), "
                        + "(43.73128213877862,7.421115579183003,52.0), (43.731362232521825,7.421145381506057,52.0), "
                        + "(43.731371359483255,7.421123216028286,52.0), (43.731485725897976,7.42117332118392,52.0), "
                        + "(43.731575132867135,7.420868778695214,52.0), (43.73160605277731,7.420824820268709,52.0), "
                        + "(43.7316401391843,7.420850152243305,52.0), (43.731674039326776,7.421050014072285,52.0)",
                str.substring(0, 662));

        assertEquals("(43.72771927105753,7.418905923193081,11.0), (43.72768239068275,7.419007064826944,11.0), (43.727680946587874,7.419198768422206,11.0)",
                str.substring(str.length() - 131));

        assertEquals(84, arsp.getAscend(), 1e-1);
        assertEquals(135, arsp.getDescend(), 1e-1);

        List<GPXEntry> list = arsp.getInstructions().createGPXList();
        assertEquals(61, list.size());
        final long lastEntryMillis = list.get(list.size() - 1).getTime();
        assertEquals(new GPXEntry(43.73068455771767, 7.421283689825812, 62.0, 0), list.get(0));
        assertEquals(new GPXEntry(43.727680946587874, 7.4191987684222065, 11.0, lastEntryMillis), list.get(list.size() - 1));

        assertEquals(62, il.createGPXList().get(0).getElevation(), 1e-2);
        assertEquals(66, il.createGPXList().get(1).getElevation(), 1e-2);
        assertEquals(52, il.createGPXList().get(10).getElevation(), 1e-2);
    }

    @Test
    public void testSRTMWithoutTunnelInterpolation() throws Exception {
        GraphHopper tmpHopper = new GraphHopperOSM().setOSMFile(osmFile).setStoreOnFlush(true)
                .setCHEnabled(false).setGraphHopperLocation(tmpGraphFile)
                .setEncodingManager(new EncodingManager(importVehicles, 8));

        tmpHopper.setElevationProvider(new SRTMProvider().setCacheDir(new File(DIR)));
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
    public void testSRTMWithTunnelInterpolation() throws Exception {
        GraphHopper tmpHopper = new GraphHopperOSM().setOSMFile(osmFile).setStoreOnFlush(true)
                .setCHEnabled(false).setGraphHopperLocation(tmpGraphFile)
                .setEncodingManager(new EncodingManager(genericImportVehicles, 8));

        tmpHopper.setElevationProvider(new SRTMProvider().setCacheDir(new File(DIR)));
        tmpHopper.importOrLoad();

        GHResponse rsp = tmpHopper.route(new GHRequest(43.74056471749763, 7.4299266210693755,
                43.73790260334179, 7.427984089259056).setAlgorithm(ASTAR)
                .setVehicle(vehicle).setWeighting(weightCalcStr));
        PathWrapper arsp = rsp.getBest();
        // Without interpolation: 356.5
        assertEquals(351.4, arsp.getDistance(), .1);
        PointList pointList = arsp.getPoints();
        assertEquals(6, pointList.getSize());
        assertTrue(pointList.is3D());

        assertEquals(17, pointList.getEle(0), .1);
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
        String tmpImportVehicles = "car,bike";
        String tmpWeightCalcStr = "fastest";

        GraphHopper tmpHopper = new GraphHopperOSM().
                setOSMFile(tmpOsmFile).
                setStoreOnFlush(true).
                setCHEnabled(false).
                setGraphHopperLocation(tmpGraphFile).
                setEncodingManager(new EncodingManager(tmpImportVehicles)).
                importOrLoad();

        GHResponse rsp = tmpHopper.route(new GHRequest(48.410987, 15.599492, 48.383419, 15.659294).
                setAlgorithm(ASTAR).setVehicle(tmpVehicle).setWeighting(tmpWeightCalcStr));

        PathWrapper arsp = rsp.getBest();
        assertEquals(6932.2, arsp.getDistance(), .1);
        assertEquals(114, arsp.getPoints().getSize());

        InstructionList il = arsp.getInstructions();
        assertEquals(24, il.size());
        List<Map<String, Object>> resultJson = il.createJson();

        assertEquals("Continue onto Obere Landstraße", resultJson.get(0).get("text"));
        assertEquals("get off the bike", resultJson.get(0).get("annotation_text"));
        assertEquals("Turn left onto Kirchengasse", resultJson.get(1).get("text"));
        assertEquals("get off the bike", resultJson.get(1).get("annotation_text"));

        assertEquals("Turn right onto Pfarrplatz", resultJson.get(2).get("text"));
        assertEquals("Turn right onto Margarethenstraße", resultJson.get(3).get("text"));
        assertEquals("Turn slight left onto Hoher Markt", resultJson.get(5).get("text"));
        assertEquals("Turn right onto Wegscheid", resultJson.get(7).get("text"));
        assertEquals("Turn right onto Ringstraße, L73", resultJson.get(9).get("text"));
        assertEquals("Turn slight left onto Eyblparkstraße", resultJson.get(10).get("text"));
        assertEquals("Turn slight left onto Austraße", resultJson.get(11).get("text"));
        assertEquals("Turn slight left onto Rechte Kremszeile", resultJson.get(12).get("text"));
        //..
        assertEquals("Turn right onto Treppelweg", resultJson.get(19).get("text"));
        assertEquals("cycleway", resultJson.get(19).get("annotation_text"));
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
                setEncodingManager(new EncodingManager(tmpImportVehicles)).
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
    public void testMultipleVehiclesWithCH() {
        String tmpOsmFile = DIR + "/monaco.osm.gz";
        GraphHopper tmpHopper = new GraphHopperOSM().
                setOSMFile(tmpOsmFile).
                setStoreOnFlush(true).
                setGraphHopperLocation(tmpGraphFile).
                setEncodingManager(new EncodingManager("bike,car")).
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
                setEncodingManager(new EncodingManager("car,bike")).
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
    public void testIfCHIsUsed() throws Exception {
        // route directly after import
        executeCHFootRoute();

        // now only load is called
        executeCHFootRoute();
    }

    private void executeCHFootRoute() {
        String tmpOsmFile = DIR + "/monaco.osm.gz";
        String tmpImportVehicles = "foot";

        GraphHopper tmpHopper = new GraphHopperOSM().
                setOSMFile(tmpOsmFile).
                setStoreOnFlush(true).
                setGraphHopperLocation(tmpGraphFile).
                setEncodingManager(new EncodingManager(tmpImportVehicles));
        tmpHopper.getCHFactoryDecorator().setWeightingsAsStrings(weightCalcStr);
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
        assertEquals(95, bestPath.getPoints().getSize());

        tmpHopper.close();
    }

    @Test
    public void testRoundTour() {
        GHRequest rq = new GHRequest().
                addPoint(new GHPoint(43.741069, 7.426854)).
                setVehicle(vehicle).setWeighting("fastest").
                setAlgorithm(ROUND_TRIP);
        rq.getHints().put(RoundTrip.HEADING, 50);
        rq.getHints().put(RoundTrip.DISTANCE, 1000);
        rq.getHints().put(RoundTrip.SEED, 0);

        GHResponse rsp = hopper.route(rq);

        assertEquals(1, rsp.getAll().size());
        PathWrapper pw = rsp.getBest();
        assertEquals(1.45, rsp.getBest().getDistance() / 1000f, .01);
        assertEquals(17, rsp.getBest().getTime() / 1000f / 60, 1);
        assertEquals(66, pw.getPoints().size());
    }

    @Test
    public void testFlexMode_631() {
        String tmpOsmFile = DIR + "/monaco.osm.gz";

        GraphHopper tmpHopper = new GraphHopperOSM().
                setOSMFile(tmpOsmFile).
                setStoreOnFlush(true).
                setGraphHopperLocation(tmpGraphFile).
                setEncodingManager(new EncodingManager("car"));

        tmpHopper.getCHFactoryDecorator().setEnabled(true).
                setWeightingsAsStrings(Arrays.asList("fastest")).
                setDisablingAllowed(true);

        tmpHopper.getLMFactoryDecorator().setEnabled(true).
                setWeightingsAsStrings(Arrays.asList("fastest")).
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
        assertEquals(90, bestPath.getPoints().getSize());

        // request flex mode
        req.setAlgorithm(Parameters.Algorithms.ASTAR_BI);
        req.getHints().put(Landmark.DISABLE, true);
        req.getHints().put(CH.DISABLE, true);
        rsp = tmpHopper.route(req);
        long flexSum = rsp.getHints().getLong("visited_nodes.sum", 0);
        assertTrue("Too few visited nodes for flex mode " + flexSum, flexSum > 60);

        bestPath = rsp.getBest();
        assertEquals(3587, bestPath.getDistance(), 1);
        assertEquals(90, bestPath.getPoints().getSize());

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
        assertEquals(90, bestPath.getPoints().getSize());

        // speed² mode is currently less optimal than CH so just check different nodes and correctness
        req.getHints().put(Landmark.DISABLE, false);
        req.getHints().put(CH.DISABLE, false);
        rsp = tmpHopper.route(req);

        long speed2Sum = rsp.getHints().getLong("visited_nodes.sum", 0);
        assertTrue("Visited nodes for speed² mode should be different but " + speed2Sum + " == " + chSum, speed2Sum != chSum);
        assertTrue("Visited nodes for speed² mode should be different but " + speed2Sum + " == " + flexSum, speed2Sum != flexSum);

        bestPath = rsp.getBest();
        assertEquals(3587, bestPath.getDistance(), 1);
        assertEquals(90, bestPath.getPoints().getSize());
    }

    @Test
    public void testTurnCostsOnOff() {
        GraphHopper tmpHopper = new GraphHopperOSM().
                setOSMFile(DIR + "/moscow.osm.gz").
                setStoreOnFlush(true).
                setCHEnabled(false).
                setGraphHopperLocation(tmpGraphFile).
                setEncodingManager(new EncodingManager("car|turn_costs=true"));
        tmpHopper.importOrLoad();

        // with turn costs (default if none-CH and turn cost enabled)
        GHRequest req = new GHRequest(55.813357, 37.5958585, 55.811042, 37.594689);
        GHResponse rsp = tmpHopper.route(req);
        assertEquals(1044, rsp.getBest().getDistance(), 1);

        // without turn costs
        req.getHints().put(Routing.EDGE_BASED, "false");
        rsp = tmpHopper.route(req);
        assertEquals(400, rsp.getBest().getDistance(), 1);

        // with turn costs
        req.getHints().put(Routing.EDGE_BASED, "true");
        rsp = tmpHopper.route(req);
        assertEquals(1044, rsp.getBest().getDistance(), 1);
    }

    @Test
    public void testCHAndTurnCostsWithFlexmode() {
        GraphHopper tmpHopper = new GraphHopperOSM().
                setOSMFile(DIR + "/moscow.osm.gz").
                setStoreOnFlush(true).
                setGraphHopperLocation(tmpGraphFile).
                setEncodingManager(new EncodingManager("car|turn_costs=true"));
        tmpHopper.getCHFactoryDecorator().setDisablingAllowed(true);
        tmpHopper.importOrLoad();

        // without turn costs (default for CH)
        GHRequest req = new GHRequest(55.813357, 37.5958585, 55.811042, 37.594689);
        GHResponse rsp = tmpHopper.route(req);
        assertFalse(rsp.getErrors().toString(), rsp.hasErrors());
        assertEquals(400, rsp.getBest().getDistance(), 1);

        // with turn costs                
        req.getHints().put(CH.DISABLE, "true");
        req.getHints().put(Routing.EDGE_BASED, "true");
        rsp = tmpHopper.route(req);
        assertEquals(1044, rsp.getBest().getDistance(), 1);
    }
}
