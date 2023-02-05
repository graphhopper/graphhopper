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

import com.graphhopper.config.CHProfile;
import com.graphhopper.config.LMProfile;
import com.graphhopper.config.Profile;
import com.graphhopper.json.Statement;
import com.graphhopper.reader.ReaderWay;
import com.graphhopper.reader.dem.SRTMProvider;
import com.graphhopper.reader.dem.SkadiProvider;
import com.graphhopper.routing.ev.EncodedValueLookup;
import com.graphhopper.routing.ev.RoadEnvironment;
import com.graphhopper.routing.ev.Subnetwork;
import com.graphhopper.routing.util.AllEdgesIterator;
import com.graphhopper.routing.util.DefaultSnapFilter;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.countryrules.CountryRuleFactory;
import com.graphhopper.routing.util.parsers.DefaultTagParserFactory;
import com.graphhopper.routing.util.parsers.OSMRoadEnvironmentParser;
import com.graphhopper.routing.util.parsers.TagParser;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.routing.weighting.custom.CustomProfile;
import com.graphhopper.search.EdgeKVStorage;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.storage.index.LocationIndexTree;
import com.graphhopper.storage.index.Snap;
import com.graphhopper.util.*;
import com.graphhopper.util.Parameters.CH;
import com.graphhopper.util.Parameters.Landmark;
import com.graphhopper.util.Parameters.Routing;
import com.graphhopper.util.details.PathDetail;
import com.graphhopper.util.exceptions.MaximumNodesExceededException;
import com.graphhopper.util.exceptions.PointDistanceExceededException;
import com.graphhopper.util.shapes.BBox;
import com.graphhopper.util.shapes.GHPoint;
import com.graphhopper.util.shapes.GHPoint3D;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;

import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static com.graphhopper.util.Parameters.Algorithms.*;
import static com.graphhopper.util.Parameters.Curbsides.*;
import static com.graphhopper.util.Parameters.Routing.U_TURN_COSTS;
import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Peter Karich
 */
public class GraphHopperTest {

    public static final String DIR = "../core/files";

    // map locations
    private static final String BAYREUTH = DIR + "/north-bayreuth.osm.gz";
    private static final String BAUTZEN = DIR + "/bautzen.osm";
    private static final String BERLIN = DIR + "/berlin-siegessaeule.osm.gz";
    private static final String KREMS = DIR + "/krems.osm.gz";
    private static final String LAUF = DIR + "/Laufamholzstrasse.osm.xml";
    private static final String MONACO = DIR + "/monaco.osm.gz";
    private static final String MOSCOW = DIR + "/moscow.osm.gz";
    private static final String ESSEN = DIR + "/edge_based_subnetwork.osm.xml.gz";

    // when creating GH instances make sure to use this as the GH location such that it will be cleaned between tests
    private static final String GH_LOCATION = "target/graphhopper-test-gh";

    @BeforeEach
    @AfterEach
    public void setup() {
        Helper.removeDir(new File(GH_LOCATION));
    }

    @ParameterizedTest
    @CsvSource({
            DIJKSTRA + ",false,708",
            ASTAR + ",false,363",
            DIJKSTRA_BI + ",false,346",
            ASTAR_BI + ",false,192",
            ASTAR_BI + ",true,46",
            DIJKSTRA_BI + ",true,51"
    })
    public void testMonacoDifferentAlgorithms(String algo, boolean withCH, int expectedVisitedNodes) {
        final String vehicle = "car";
        final String weighting = "fastest";
        GraphHopper hopper = new GraphHopper().
                setGraphHopperLocation(GH_LOCATION).
                setOSMFile(MONACO).
                setProfiles(new Profile("profile").setVehicle(vehicle).setWeighting(weighting)).
                setStoreOnFlush(true);
        hopper.getCHPreparationHandler()
                .setCHProfiles(new CHProfile("profile"));
        hopper.setMinNetworkSize(0);
        hopper.importOrLoad();
        GHRequest req = new GHRequest(43.727687, 7.418737, 43.74958, 7.436566)
                .setAlgorithm(algo)
                .setProfile("profile");
        req.putHint(CH.DISABLE, !withCH);
        GHResponse rsp = hopper.route(req);
        assertFalse(rsp.hasErrors(), rsp.getErrors().toString());
        assertEquals(expectedVisitedNodes, rsp.getHints().getLong("visited_nodes.sum", 0));

        ResponsePath res = rsp.getBest();
        assertEquals(3586.9, res.getDistance(), .1);
        assertEquals(277115, res.getTime(), 10);
        assertEquals(91, res.getPoints().size());

        assertEquals(43.7276852, res.getWaypoints().getLat(0), 1e-7);
        assertEquals(43.7495432, res.getWaypoints().getLat(1), 1e-7);
    }

    @Test
    public void testMonacoWithInstructions() {
        final String profile = "profile";
        final String vehicle = "foot";
        final String weighting = "shortest";
        GraphHopper hopper = new GraphHopper().
                setGraphHopperLocation(GH_LOCATION).
                setOSMFile(MONACO).
                setProfiles(new Profile(profile).setVehicle(vehicle).setWeighting(weighting)).
                setStoreOnFlush(true).
                importOrLoad();

        GHResponse rsp = hopper.route(new GHRequest(43.727687, 7.418737, 43.74958, 7.436566).
                setAlgorithm(ASTAR).setProfile(profile));

        // identify the number of counts to compare with CH foot route
        assertEquals(713, rsp.getHints().getLong("visited_nodes.sum", 0));

        ResponsePath res = rsp.getBest();
        assertEquals(3437.1, res.getDistance(), .1);
        assertEquals(85, res.getPoints().size());

        assertEquals(43.7276852, res.getWaypoints().getLat(0), 1e-7);
        assertEquals(43.7495432, res.getWaypoints().getLat(1), 1e-7);

        InstructionList il = res.getInstructions();
        assertEquals(16, il.size());

        // TODO roundabout fine tuning -> enter + leave roundabout (+ two roundabouts -> is it necessary if we do not leave the street?)
        Translation tr = hopper.getTranslationMap().getWithFallBack(Locale.US);
        assertEquals("continue onto Avenue des Guelfes", il.get(0).getTurnDescription(tr));
        assertEquals("turn slight left onto Avenue des Papalins", il.get(1).getTurnDescription(tr));
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

        assertEquals(85, res.getPoints().size());
    }

    @Test
    public void withoutInstructions() {
        final String profile = "profile";
        final String vehicle = "foot";
        final String weighting = "shortest";
        GraphHopper hopper = new GraphHopper().
                setGraphHopperLocation(GH_LOCATION).
                setOSMFile(MONACO).
                setProfiles(new Profile(profile).setVehicle(vehicle).setWeighting(weighting)).
                setStoreOnFlush(true).
                importOrLoad();

        GHRequest request = new GHRequest().setAlgorithm(ASTAR).setProfile(profile);
        request.addPoint(new GHPoint(43.729584, 7.410965));
        request.addPoint(new GHPoint(43.732499, 7.426758));
        request.getHints().putObject("instructions", true);
        // no simplification
        hopper.getRouterConfig().setSimplifyResponse(false);
        GHResponse routeRsp = hopper.route(request);
        assertEquals(10, routeRsp.getBest().getInstructions().size());
        assertEquals(43, routeRsp.getBest().getPoints().size());

        // with simplification
        hopper.getRouterConfig().setSimplifyResponse(true);
        routeRsp = hopper.route(request);
        assertEquals(10, routeRsp.getBest().getInstructions().size());
        assertEquals(39, routeRsp.getBest().getPoints().size());

        // no instructions
        request.getHints().putObject("instructions", false);
        routeRsp = hopper.route(request);
        // the path is still simplified
        assertEquals(41, routeRsp.getBest().getPoints().size());
    }

    @Test
    public void testUTurn() {
        final String profile = "profile";
        final String vehicle = "car";
        final String weighting = "shortest";
        GraphHopper hopper = new GraphHopper().
                setGraphHopperLocation(GH_LOCATION).
                setOSMFile(MONACO).
                setProfiles(new Profile(profile).setVehicle(vehicle).setWeighting(weighting));
        hopper.importOrLoad();
        Translation tr = hopper.getTranslationMap().getWithFallBack(Locale.US);

        GHRequest request = new GHRequest();
        request.addPoint(new GHPoint(43.743887, 7.431151));
        request.addPoint(new GHPoint(43.744007, 7.431076));
        //Force initial U-Turn
        request.setHeadings(Arrays.asList(200.));

        request.setAlgorithm(ASTAR).setProfile(profile);
        GHResponse rsp = hopper.route(request);

        assertFalse(rsp.hasErrors());
        ResponsePath res = rsp.getBest();
        InstructionList il = res.getInstructions();
        assertEquals(4, il.size());

        // Initial U-turn
        assertEquals("make a U-turn onto Avenue Princesse Grace", il.get(1).getTurnDescription(tr));
        // Second U-turn to get to destination
        assertEquals("make a U-turn onto Avenue Princesse Grace", il.get(2).getTurnDescription(tr));
    }

    private void testImportCloseAndLoad(boolean ch, boolean lm, boolean sort, boolean custom) {
        final String vehicle = "foot";
        final String profileName = "profile";
        GraphHopper hopper = new GraphHopper().
                setGraphHopperLocation(GH_LOCATION).
                setOSMFile(MONACO).
                setStoreOnFlush(true).
                setSortGraph(sort);

        Profile profile = new Profile(profileName).setVehicle(vehicle).setWeighting("fastest");
        if (custom) {
            JsonFeature area51Feature = new JsonFeature();
            area51Feature.setGeometry(new GeometryFactory().createPolygon(new Coordinate[]{
                    new Coordinate(7.4174, 43.7345),
                    new Coordinate(7.4198, 43.7355),
                    new Coordinate(7.4207, 43.7344),
                    new Coordinate(7.4174, 43.7345)}));
            CustomModel customModel = new CustomModel().setDistanceInfluence(0d);
            customModel.getPriority().add(Statement.If("in_area51", Statement.Op.MULTIPLY, "0.1"));
            customModel.getAreas().put("area51", area51Feature);
            profile = new CustomProfile(profileName).setCustomModel(customModel).setVehicle(vehicle);
        }
        hopper.setProfiles(profile);

        if (ch) {
            hopper.getCHPreparationHandler()
                    .setCHProfiles(new CHProfile(profileName));
        }
        if (lm) {
            hopper.getLMPreparationHandler()
                    .setLMProfiles(new LMProfile(profileName));
        }
        hopper.importAndClose();
        hopper = new GraphHopper().
                setGraphHopperLocation(GH_LOCATION).
                setOSMFile(MONACO).
                setProfiles(profile).
                setStoreOnFlush(true).
                setAllowWrites(false);
        if (ch) {
            hopper.getCHPreparationHandler()
                    .setCHProfiles(new CHProfile(profileName));
        }
        if (lm) {
            hopper.getLMPreparationHandler()
                    .setLMProfiles(new LMProfile(profileName));
        }
        hopper.importOrLoad();

        // same query as in testMonacoWithInstructions
        // visited nodes >700 for flexible, <125 for CH or LM

        if (ch) {
            GHRequest req = new GHRequest(43.727687, 7.418737, 43.74958, 7.436566).
                    setProfile(profileName);
            req.putHint(CH.DISABLE, false);
            req.putHint(Landmark.DISABLE, true);
            GHResponse rsp = hopper.route(req);

            ResponsePath bestPath = rsp.getBest();
            long sum = rsp.getHints().getLong("visited_nodes.sum", 0);
            assertNotEquals(sum, 0);
            assertTrue(sum < 155, "Too many nodes visited " + sum);
            assertEquals(3535, bestPath.getDistance(), 1);
            assertEquals(115, bestPath.getPoints().size());
        }

        if (lm) {
            GHRequest req = new GHRequest(43.727687, 7.418737, 43.74958, 7.436566).
                    setProfile(profileName).
                    setAlgorithm(Parameters.Algorithms.ASTAR_BI);
            req.putHint(CH.DISABLE, true);
            req.putHint(Landmark.DISABLE, false);
            GHResponse rsp = hopper.route(req);

            ResponsePath bestPath = rsp.getBest();
            long sum = rsp.getHints().getLong("visited_nodes.sum", 0);
            assertNotEquals(sum, 0);
            assertTrue(sum < 125, "Too many nodes visited " + sum);
            assertEquals(3535, bestPath.getDistance(), 1);
            assertEquals(115, bestPath.getPoints().size());
        }

        // flexible
        GHRequest req = new GHRequest(43.727687, 7.418737, 43.74958, 7.436566).
                setProfile(profileName);
        req.putHint(CH.DISABLE, true);
        req.putHint(Landmark.DISABLE, true);
        GHResponse rsp = hopper.route(req);

        ResponsePath bestPath = rsp.getBest();
        long sum = rsp.getHints().getLong("visited_nodes.sum", 0);
        assertNotEquals(sum, 0);
        assertTrue(sum > 120, "Too few nodes visited " + sum);
        assertEquals(3535, bestPath.getDistance(), 1);
        assertEquals(115, bestPath.getPoints().size());

        hopper.close();
    }

    @Test
    public void testImportThenLoadCH() {
        testImportCloseAndLoad(true, false, false, false);
    }

    @Test
    public void testImportThenLoadLM() {
        testImportCloseAndLoad(false, true, false, false);
    }

    @Test
    public void testImportThenLoadLMWithCustom() {
        testImportCloseAndLoad(false, true, false, true);
    }

    @Test
    public void testImportThenLoadCHLM() {
        testImportCloseAndLoad(true, true, false, false);
    }

    @Test
    public void testImportThenLoadCHLMAndSort() {
        testImportCloseAndLoad(true, true, true, false);
    }

    @Test
    public void testImportThenLoadFlexible() {
        testImportCloseAndLoad(false, false, false, false);
    }

    @Test
    public void testImportWithCHANDCustomProfile() {
        // we cannot unload geometry as it might be required in CustomWeighting when using in_area_xy in condition
        testImportCloseAndLoad(true, false, false, true);
    }

    @Test
    public void testAlternativeRoutes() {
        final String profile = "profile";
        final String vehicle = "foot";
        final String weighting = "shortest";
        GraphHopper hopper = new GraphHopper().
                setGraphHopperLocation(GH_LOCATION).
                setOSMFile(MONACO).
                setProfiles(new Profile(profile).setVehicle(vehicle).setWeighting(weighting)).
                setStoreOnFlush(true).
                importOrLoad();

        GHRequest req = new GHRequest(43.729057, 7.41251, 43.740298, 7.423561).
                setAlgorithm(ALT_ROUTE).setProfile(profile);

        GHResponse rsp = hopper.route(req);
        assertFalse(rsp.hasErrors());
        assertEquals(2, rsp.getAll().size());

        assertEquals(1310, rsp.getAll().get(0).getTime() / 1000);
        assertEquals(1431, rsp.getAll().get(1).getTime() / 1000);

        req.putHint("alternative_route.max_paths", 3);
        req.putHint("alternative_route.min_plateau_factor", 0.1);
        rsp = hopper.route(req);
        assertFalse(rsp.hasErrors());
        assertEquals(3, rsp.getAll().size());

        assertEquals(1310, rsp.getAll().get(0).getTime() / 1000);
        assertEquals(1431, rsp.getAll().get(1).getTime() / 1000);
        assertEquals(1492, rsp.getAll().get(2).getTime() / 1000);
    }

    @Test
    public void testAlternativeRoutesBike() {
        final String profile = "profile";
        final String vehicle = "bike";
        final String weighting = "fastest";

        GraphHopper hopper = new GraphHopper().
                setGraphHopperLocation(GH_LOCATION).
                setOSMFile(BAYREUTH).
                setProfiles(new Profile(profile).setVehicle(vehicle).setWeighting(weighting));
        hopper.importOrLoad();

        GHRequest req = new GHRequest(50.028917, 11.496506, 49.985228, 11.600876).
                setAlgorithm(ALT_ROUTE).setProfile(profile);
        req.putHint("alternative_route.max_paths", 3);
        GHResponse rsp = hopper.route(req);
        assertFalse(rsp.hasErrors(), rsp.getErrors().toString());

        assertEquals(3, rsp.getAll().size());
        // via ramsenthal
        assertEquals(2865, rsp.getAll().get(0).getTime() / 1000);
        // via unterwaiz
        assertEquals(3318, rsp.getAll().get(1).getTime() / 1000);
        // via eselslohe -> theta; BTW: here smaller time as 2nd alternative due to priority influences time order
        assertEquals(3093, rsp.getAll().get(2).getTime() / 1000);
    }

    @Test
    public void testAlternativeRoutesCar() {
        final String profile = "profile";
        final String vehicle = "car";
        final String weighting = "fastest";

        GraphHopper hopper = new GraphHopper().
                setGraphHopperLocation(GH_LOCATION).
                setOSMFile(BAYREUTH).
                setProfiles(new Profile(profile).setVehicle(vehicle).setWeighting(weighting));
        hopper.importOrLoad();

        GHRequest req = new GHRequest(50.023513, 11.548862, 49.969441, 11.537876).
                setAlgorithm(ALT_ROUTE).setProfile(profile);
        req.putHint("alternative_route.max_paths", 3);
        req.putHint("alternative_route.max_exploration_factor", 1.2);
        GHResponse rsp = hopper.route(req);
        assertFalse(rsp.hasErrors(), rsp.getErrors().toString());

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
        final String profile = "profile";
        final String vehicle = "car";
        final String weighting = "fastest";

        GraphHopper hopper = new GraphHopper().
                setGraphHopperLocation(GH_LOCATION).
                setOSMFile(LAUF).
                setProfiles(new Profile(profile).setVehicle(vehicle).setWeighting(weighting));
        hopper.importOrLoad();

        GHRequest req = new GHRequest(49.46553, 11.154669, 49.465244, 11.152577).
                setProfile(profile);

        req.setPointHints(new ArrayList<>(asList("Laufamholzstraße, 90482, Nürnberg, Deutschland", "")));
        GHResponse rsp = hopper.route(req);
        assertFalse(rsp.hasErrors(), rsp.getErrors().toString());
        GHPoint snappedPoint = rsp.getBest().getWaypoints().get(0);
        assertEquals(49.465686, snappedPoint.getLat(), .000001);
        assertEquals(11.154605, snappedPoint.getLon(), .000001);

        req.setPointHints(new ArrayList<>(asList("", "")));
        rsp = hopper.route(req);
        assertFalse(rsp.hasErrors(), rsp.getErrors().toString());
        snappedPoint = rsp.getBest().getWaypoints().get(0);
        assertEquals(49.465502, snappedPoint.getLat(), .000001);
        assertEquals(11.154498, snappedPoint.getLon(), .000001);

        // Match to closest edge, since hint was not found
        req.setPointHints(new ArrayList<>(asList("xy", "")));
        rsp = hopper.route(req);
        assertFalse(rsp.hasErrors(), rsp.getErrors().toString());
        snappedPoint = rsp.getBest().getWaypoints().get(0);
        assertEquals(49.465502, snappedPoint.getLat(), .000001);
        assertEquals(11.154498, snappedPoint.getLon(), .000001);
    }

    @Test
    public void testForwardBackwardDestination() {
        final String profile = "profile";
        GraphHopper hopper = new GraphHopper().
                setGraphHopperLocation(GH_LOCATION).
                setOSMFile(BAUTZEN).
                setProfiles(new Profile(profile).setVehicle("car").setWeighting("fastest"));
        hopper.setMinNetworkSize(0);
        hopper.importOrLoad();

        Translation tr = hopper.getTranslationMap().getWithFallBack(Locale.US);

        GHResponse rsp = hopper.route(new GHRequest(51.1915, 14.416, 51.192, 14.412).setProfile(profile));
        assertFalse(rsp.hasErrors(), rsp.getErrors().toString());
        assertEquals("keep right and take B 96 toward Bautzen-West, Hoyerswerda",
                rsp.getBest().getInstructions().get(1).getTurnDescription(tr));
        assertEquals("turn left onto Hoyerswerdaer Straße and drive toward Hoyerswerda, Kleinwelka",
                rsp.getBest().getInstructions().get(2).getTurnDescription(tr));

        rsp = hopper.route(new GHRequest(51.191, 14.414, 51.1884, 14.41).setProfile(profile));
        assertFalse(rsp.hasErrors(), rsp.getErrors().toString());
        assertEquals("turn left and take A 4 toward Dresden", rsp.getBest().getInstructions().get(1).getTurnDescription(tr));
    }

    @Test
    public void testNorthBayreuthAccessDestination() {
        final String profile = "profile";
        final String vehicle = "car";
        final String weighting = "fastest";

        GraphHopper hopper = new GraphHopper().
                setGraphHopperLocation(GH_LOCATION).
                setOSMFile(BAYREUTH).
                setProfiles(new Profile(profile).setVehicle(vehicle).setWeighting(weighting));
        hopper.importOrLoad();

        GHRequest req = new GHRequest(49.985307, 11.50628, 49.985731, 11.507465).
                setProfile(profile);

        GHResponse rsp = hopper.route(req);
        assertFalse(rsp.hasErrors(), rsp.getErrors().toString());
        assertEquals(550, rsp.getBest().getDistance(), 1);
    }

    @Test
    public void testNorthBayreuthBlockedEdges() {
        final String profile = "profile";
        final String vehicle = "car";
        final String weighting = "fastest";

        GraphHopper hopper = new GraphHopper().
                setGraphHopperLocation(GH_LOCATION).
                setOSMFile(BAYREUTH).
                setProfiles(new Profile(profile).setVehicle(vehicle).setWeighting(weighting));
        hopper.importOrLoad();

        GHRequest req = new GHRequest(49.985272, 11.506151, 49.986107, 11.507202).
                setProfile(profile);

        GHResponse rsp = hopper.route(req);
        assertFalse(rsp.hasErrors(), rsp.getErrors().toString());
        assertEquals(122, rsp.getBest().getDistance(), 1);

        // block point 49.985759,11.50687
        req.putHint(Routing.BLOCK_AREA, "49.985759,11.50687");
        rsp = hopper.route(req);
        assertFalse(rsp.hasErrors(), rsp.getErrors().toString());
        assertEquals(365, rsp.getBest().getDistance(), 1);

        req = new GHRequest(49.975845, 11.522598, 50.026821, 11.497364).
                setProfile(profile);

        rsp = hopper.route(req);
        assertFalse(rsp.hasErrors(), rsp.getErrors().toString());
        assertEquals(6685, rsp.getBest().getDistance(), 1);

        // block by area
        String someArea = "49.97986,11.472902,50.003946,11.534357";
        req.putHint(Routing.BLOCK_AREA, someArea);
        rsp = hopper.route(req);
        assertFalse(rsp.hasErrors(), rsp.getErrors().toString());
        assertEquals(13988, rsp.getBest().getDistance(), 1);

        // Add blocked point to above area, to increase detour
        req.putHint(Routing.BLOCK_AREA, "50.017578,11.547527;" + someArea);
        rsp = hopper.route(req);
        assertFalse(rsp.hasErrors(), rsp.getErrors().toString());
        assertEquals(14601, rsp.getBest().getDistance(), 1);

        // block by edge IDs -> i.e. use small circular area
        req.putHint(Routing.BLOCK_AREA, "49.979929,11.520066,200");
        rsp = hopper.route(req);
        assertFalse(rsp.hasErrors(), rsp.getErrors().toString());
        assertEquals(12173, rsp.getBest().getDistance(), 1);

        req.putHint(Routing.BLOCK_AREA, "49.980868,11.516397,150");
        rsp = hopper.route(req);
        assertFalse(rsp.hasErrors(), rsp.getErrors().toString());
        assertEquals(12173, rsp.getBest().getDistance(), 1);

        // block by edge IDs -> i.e. use small rectangular area
        req.putHint(Routing.BLOCK_AREA, "49.981875,11.515818,49.979522,11.521407");
        rsp = hopper.route(req);
        assertFalse(rsp.hasErrors(), rsp.getErrors().toString());
        assertEquals(12173, rsp.getBest().getDistance(), 1);

        // blocking works for all weightings
        req = new GHRequest(50.009504, 11.490669, 50.024726, 11.496162).
                setProfile(profile);
        rsp = hopper.route(req);
        assertFalse(rsp.hasErrors(), rsp.getErrors().toString());
        assertEquals(1807, rsp.getBest().getDistance(), 1);

        req.putHint(Routing.BLOCK_AREA, "50.018277,11.492336");
        rsp = hopper.route(req);
        assertFalse(rsp.hasErrors(), rsp.getErrors().toString());
        assertEquals(3363, rsp.getBest().getDistance(), 1);

        // query point and snapped point are different => block snapped point only => show that block_area changes lookup
        req = new GHRequest(49.984465, 11.507009, 49.986107, 11.507202).
                setProfile(profile);
        rsp = hopper.route(req);
        assertEquals(11.506, rsp.getBest().getWaypoints().getLon(0), 0.001);
        assertFalse(rsp.hasErrors(), rsp.getErrors().toString());
        assertEquals(155, rsp.getBest().getDistance(), 10);

        req.putHint(Routing.BLOCK_AREA, "49.984434,11.505212,49.985394,11.506333");
        rsp = hopper.route(req);
        // we do not snap onto Grüngraben (within the blocked area), but onto Lohweg and then we need to go to Hauptstraße
        // and turn left onto Waldhüttenstraße. Note that if we exclude footway we get an entirely different path, because
        // the start point snaps all the way to the East onto the end of Bergstraße (because Lohweg gets no longer split
        // into several edges...)
        assertEquals(11.506, rsp.getBest().getWaypoints().getLon(0), 0.001);
        assertFalse(rsp.hasErrors(), rsp.getErrors().toString());
        assertEquals(510, rsp.getBest().getDistance(), 10);

        // first point is contained in block_area => error
        req = new GHRequest(49.979, 11.516, 49.986107, 11.507202).
                setProfile(profile);
        req.putHint(Routing.BLOCK_AREA, "49.981875,11.515818,49.979522,11.521407");
        rsp = hopper.route(req);
        assertTrue(rsp.hasErrors(), "expected errors");
    }

    @Test
    public void testCustomModel() {
        final String vehicle = "car";
        final String customCar = "custom_car";
        final String emptyCar = "empty_car";
        CustomModel customModel = new CustomModel();
        customModel.addToSpeed(Statement.If("road_class == TERTIARY || road_class == TRACK", Statement.Op.MULTIPLY, "0.1"));
        GraphHopper hopper = new GraphHopper().
                setGraphHopperLocation(GH_LOCATION).
                setOSMFile(BAYREUTH).
                setProfiles(
                        new CustomProfile(emptyCar).setCustomModel(new CustomModel()).setVehicle(vehicle),
                        new CustomProfile(customCar).setCustomModel(customModel).setVehicle(vehicle)
                ).
                importOrLoad();

        // standard car route
        assertDistance(hopper, emptyCar, null, 8725);
        // the custom car takes a detour in the north to avoid tertiary roads
        assertDistance(hopper, customCar, null, 13223);
        // we can achieve the same by using the empty profile and using a client-side model, we just need to copy the model because of the internal flag
        assertDistance(hopper, emptyCar, new CustomModel(customModel), 13223);
        // now we prevent using unclassified roads as well and the route goes even further north
        CustomModel strictCustomModel = new CustomModel().addToSpeed(
                Statement.If("road_class == TERTIARY || road_class == TRACK || road_class == UNCLASSIFIED", Statement.Op.MULTIPLY, "0.1"));
        assertDistance(hopper, emptyCar, strictCustomModel, 19289);
        // we can achieve the same by 'adding' a rule to the server-side custom model
        CustomModel customModelWithUnclassifiedRule = new CustomModel().addToSpeed(
                Statement.If("road_class == UNCLASSIFIED", Statement.Op.MULTIPLY, "0.1")
        );
        assertDistance(hopper, customCar, customModelWithUnclassifiedRule, 19289);
        // now we use distance influence to avoid the detour
        assertDistance(hopper, customCar, new CustomModel(customModelWithUnclassifiedRule).setDistanceInfluence(200d), 8725);
        assertDistance(hopper, customCar, new CustomModel(customModelWithUnclassifiedRule).setDistanceInfluence(100d), 14475);
    }

    private void assertDistance(GraphHopper hopper, String profile, CustomModel customModel, double expectedDistance) {
        GHRequest req = new GHRequest(50.008732, 11.596413, 49.974361, 11.514509);
        req.setProfile(profile);
        if (customModel != null)
            req.setCustomModel(customModel);
        GHResponse rsp = hopper.route(req);
        assertFalse(rsp.hasErrors(), rsp.getErrors().toString());
        assertEquals(expectedDistance, rsp.getBest().getDistance(), 1);
    }

    @Test
    public void testMonacoVia() {
        final String profile = "profile";
        final String vehicle = "foot";
        final String weighting = "shortest";
        GraphHopper hopper = new GraphHopper().
                setGraphHopperLocation(GH_LOCATION).
                setOSMFile(MONACO).
                setProfiles(new Profile(profile).setVehicle(vehicle).setWeighting(weighting)).
                setStoreOnFlush(true).
                importOrLoad();

        Translation tr = hopper.getTranslationMap().getWithFallBack(Locale.US);
        GHResponse rsp = hopper.route(new GHRequest().
                addPoint(new GHPoint(43.727687, 7.418737)).
                addPoint(new GHPoint(43.74958, 7.436566)).
                addPoint(new GHPoint(43.727687, 7.418737)).
                setAlgorithm(ASTAR).setProfile(profile));

        ResponsePath res = rsp.getBest();
        assertEquals(6874.2, res.getDistance(), .1);
        assertEquals(170, res.getPoints().size());

        InstructionList il = res.getInstructions();
        assertEquals(30, il.size());
        assertEquals("continue onto Avenue des Guelfes", il.get(0).getTurnDescription(tr));
        assertEquals("turn slight left onto Avenue des Papalins", il.get(1).getTurnDescription(tr));
        assertEquals("turn sharp right onto Quai Jean-Charles Rey", il.get(4).getTurnDescription(tr));
        assertEquals("turn left", il.get(5).getTurnDescription(tr));
        assertEquals("turn right onto Avenue Albert II", il.get(6).getTurnDescription(tr));

        assertEquals("waypoint 1", il.get(15).getTurnDescription(tr));
        assertEquals(Instruction.U_TURN_UNKNOWN, il.get(16).getSign());

        assertEquals("continue onto Avenue Albert II", il.get(23).getTurnDescription(tr));
        assertEquals("turn left", il.get(24).getTurnDescription(tr));
        assertEquals("turn right onto Quai Jean-Charles Rey", il.get(25).getTurnDescription(tr));
        assertEquals("turn sharp left onto Avenue des Papalins", il.get(26).getTurnDescription(tr));
        assertEquals("turn slight right onto Avenue des Guelfes", il.get(28).getTurnDescription(tr));
        assertEquals("arrive at destination", il.get(29).getTurnDescription(tr));

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
                setAlgorithm(ASTAR).setProfile(profile));

        res = rsp.getBest();
        assertEquals(0, res.getDistance(), .1);
        assertEquals(0, res.getRouteWeight(), .1);
        assertEquals(1, res.getPoints().size());
        assertEquals(1, res.getInstructions().size());
        assertEquals("arrive at destination", res.getInstructions().get(0).getTurnDescription(tr));
        assertEquals(Instruction.FINISH, res.getInstructions().get(0).getSign());

        rsp = hopper.route(new GHRequest().
                setPoints(Arrays.asList(
                        new GHPoint(43.727687, 7.418737),
                        new GHPoint(43.727687, 7.418737),
                        new GHPoint(43.727687, 7.418737)
                )).
                setAlgorithm(ASTAR).setProfile(profile));

        res = rsp.getBest();
        assertEquals(0, res.getDistance(), .1);
        assertEquals(0, res.getRouteWeight(), .1);
        assertEquals(1, res.getPoints().size());
        assertEquals(2, res.getInstructions().size());
        assertEquals(Instruction.REACHED_VIA, res.getInstructions().get(0).getSign());
        assertEquals(Instruction.FINISH, res.getInstructions().get(1).getSign());
    }

    @Test
    public void testMonacoPathDetails() {
        final String profile = "profile";
        final String vehicle = "foot";
        final String weighting = "shortest";
        GraphHopper hopper = new GraphHopper().
                setGraphHopperLocation(GH_LOCATION).
                setOSMFile(MONACO).
                setProfiles(new Profile(profile).setVehicle(vehicle).setWeighting(weighting)).
                setStoreOnFlush(true).
                importOrLoad();

        GHRequest request = new GHRequest();
        request.addPoint(new GHPoint(43.727687, 7.418737));
        request.addPoint(new GHPoint(43.74958, 7.436566));
        request.addPoint(new GHPoint(43.727687, 7.418737));
        request.setAlgorithm(ASTAR).setProfile(profile);
        request.setPathDetails(Collections.singletonList(Parameters.Details.AVERAGE_SPEED));

        GHResponse rsp = hopper.route(request);

        ResponsePath res = rsp.getBest();
        Map<String, List<PathDetail>> details = res.getPathDetails();
        assertEquals(1, details.size());
        List<PathDetail> detailList = details.get(Parameters.Details.AVERAGE_SPEED);
        assertEquals(10, detailList.size());
        assertEquals(5.0, detailList.get(0).getValue());
        assertEquals(0, detailList.get(0).getFirst());
        assertEquals(3.0, detailList.get(1).getValue());
        assertEquals(res.getPoints().size() - 1, detailList.get(9).getLast());
    }

    @Test
    public void testMonacoEnforcedDirection() {
        final String profile = "profile";
        final String vehicle = "foot";
        final String weighting = "fastest";
        GraphHopper hopper = new GraphHopper().
                setGraphHopperLocation(GH_LOCATION).
                setOSMFile(MONACO).
                setProfiles(new Profile(profile).setVehicle(vehicle).setWeighting(weighting)).
                setStoreOnFlush(true).
                importOrLoad();

        GHRequest req = new GHRequest().
                addPoint(new GHPoint(43.741069, 7.426854)).
                addPoint(new GHPoint(43.744445, 7.429483)).
                setHeadings(Arrays.asList(0., 190.)).
                setProfile(profile);
        req.putHint(Routing.HEADING_PENALTY, "400");
        GHResponse rsp = hopper.route(req);

        ResponsePath res = rsp.getBest();
        assertEquals(921, res.getDistance(), 10.);
        assertEquals(36, res.getPoints().size());

        // headings must be in [0, 360)
        req = new GHRequest().
                addPoint(new GHPoint(43.741069, 7.426854)).
                addPoint(new GHPoint(43.744445, 7.429483)).
                setHeadings(Arrays.asList(10., 370.)).
                setProfile(profile);
        rsp = hopper.route(req);
        assertTrue(rsp.hasErrors());
        assertTrue(rsp.getErrors().toString().contains("Heading for point 1 must be in range [0,360) or NaN, but was: 370"),
                rsp.getErrors().toString());

        // the number of headings must match the number of points
        req = new GHRequest().
                addPoint(new GHPoint(43.741069, 7.426854)).
                addPoint(new GHPoint(43.742069, 7.427854)).
                addPoint(new GHPoint(43.744445, 7.429483)).
                setHeadings(Arrays.asList(0., 190.)).
                setProfile(profile);
        rsp = hopper.route(req);
        assertTrue(rsp.hasErrors());
        assertTrue(rsp.getErrors().toString().contains("The number of 'heading' parameters must be zero, one or equal to the number of points"),
                rsp.getErrors().toString());
    }

    @Test
    public void testMonacoMaxVisitedNodes() {
        final String profile = "profile";
        final String vehicle = "foot";
        final String weighting = "fastest";
        GraphHopper hopper = new GraphHopper().
                setGraphHopperLocation(GH_LOCATION).
                setOSMFile(MONACO).
                setProfiles(new Profile(profile).setVehicle(vehicle).setWeighting(weighting)).
                setStoreOnFlush(true).
                importOrLoad();

        GHPoint from = new GHPoint(43.741069, 7.426854);
        GHPoint to = new GHPoint(43.744445, 7.429483);
        GHRequest req = new GHRequest(from, to).setProfile(profile);
        req.putHint(Routing.MAX_VISITED_NODES, 5);
        GHResponse rsp = hopper.route(req);

        assertTrue(rsp.hasErrors());
        Throwable throwable = rsp.getErrors().get(0);
        assertTrue(throwable instanceof MaximumNodesExceededException);
        Object nodesDetail = ((MaximumNodesExceededException) throwable).getDetails().get(MaximumNodesExceededException.NODES_KEY);
        assertEquals(5, nodesDetail);

        req = new GHRequest(from, to).setProfile(profile);
        rsp = hopper.route(req);

        assertFalse(rsp.hasErrors(), rsp.getErrors().toString());
    }

    @Test
    public void testMonacoNonChMaxWaypointDistance() {
        final String profile = "profile";
        final String vehicle = "foot";
        final String weighting = "fastest";
        GraphHopper hopper = new GraphHopper().
                setGraphHopperLocation(GH_LOCATION).
                setOSMFile(MONACO).
                setProfiles(new Profile(profile).setVehicle(vehicle).setWeighting(weighting)).
                setStoreOnFlush(true).
                importOrLoad();

        GHPoint from = new GHPoint(43.741069, 7.426854);
        GHPoint to = new GHPoint(43.727697, 7.419199);

        GHRequest req = new GHRequest(from, to).setProfile(profile);

        // Fail since points are too far apart
        hopper.getRouterConfig().setNonChMaxWaypointDistance(1000);
        GHResponse rsp = hopper.route(req);

        assertTrue(rsp.hasErrors());
        String errorString = rsp.getErrors().toString();
        assertTrue(errorString.contains("Point 1 is too far from Point 0"), errorString);

        // Succeed since points are not far anymore
        hopper.getRouterConfig().setNonChMaxWaypointDistance(Integer.MAX_VALUE);
        rsp = hopper.route(req);

        assertFalse(rsp.hasErrors(), rsp.getErrors().toString());
    }

    @Test
    public void testMonacoNonChMaxWaypointDistanceMultiplePoints() {
        final String profile = "profile";
        final String vehicle = "foot";
        final String weighting = "fastest";
        GraphHopper hopper = new GraphHopper().
                setGraphHopperLocation(GH_LOCATION).
                setOSMFile(MONACO).
                setProfiles(new Profile(profile).setVehicle(vehicle).setWeighting(weighting)).
                setStoreOnFlush(true).
                importOrLoad();

        GHPoint from = new GHPoint(43.741069, 7.426854);
        GHPoint via = new GHPoint(43.744445, 7.429483);
        GHPoint to = new GHPoint(43.727697, 7.419199);

        GHRequest req = new GHRequest().
                setPoints(Arrays.asList(from, via, to)).
                setProfile(profile);

        // Fail since points are too far
        hopper.getRouterConfig().setNonChMaxWaypointDistance(1000);
        GHResponse rsp = hopper.route(req);

        assertTrue(rsp.hasErrors());
        String errorString = rsp.getErrors().toString();
        assertTrue(errorString.contains("Point 2 is too far from Point 1"), errorString);

        PointDistanceExceededException exception = (PointDistanceExceededException) rsp.getErrors().get(0);
        assertEquals(1, exception.getDetails().get("from"));
        assertEquals(2, exception.getDetails().get("to"));

        // Succeed since points are not far anymore
        hopper.getRouterConfig().setNonChMaxWaypointDistance(Integer.MAX_VALUE);
        rsp = hopper.route(req);

        assertFalse(rsp.hasErrors(), rsp.getErrors().toString());
    }

    @Test
    public void testMonacoStraightVia() {
        final String profile = "profile";
        final String vehicle = "foot";
        final String weighting = "fastest";
        GraphHopper hopper = new GraphHopper().
                setGraphHopperLocation(GH_LOCATION).
                setOSMFile(MONACO).
                setProfiles(new Profile(profile).setVehicle(vehicle).setWeighting(weighting)).
                setStoreOnFlush(true).
                importOrLoad();

        GHRequest rq = new GHRequest().
                addPoint(new GHPoint(43.741069, 7.426854)).
                addPoint(new GHPoint(43.740371, 7.426946)).
                addPoint(new GHPoint(43.740794, 7.427294)).
                setProfile(profile);
        rq.putHint(Routing.PASS_THROUGH, true);
        GHResponse rsp = hopper.route(rq);

        ResponsePath res = rsp.getBest();
        assertEquals(297, res.getDistance(), 5.);
        assertEquals(23, res.getPoints().size());

        // test if start and first point are identical leading to an empty path, #788
        rq = new GHRequest().
                addPoint(new GHPoint(43.741069, 7.426854)).
                addPoint(new GHPoint(43.741069, 7.426854)).
                addPoint(new GHPoint(43.740371, 7.426946)).
                setProfile(profile);
        rq.putHint(Routing.PASS_THROUGH, true);
        rsp = hopper.route(rq);
        assertEquals(91, rsp.getBest().getDistance(), 5.);
    }

    @Test
    public void testSRTMWithInstructions() {
        final String profile = "profile";
        final String vehicle = "foot";
        final String weighting = "shortest";

        GraphHopper hopper = new GraphHopper().
                setGraphHopperLocation(GH_LOCATION).
                setOSMFile(MONACO).
                setProfiles(new Profile(profile).setVehicle(vehicle).setWeighting(weighting)).
                setStoreOnFlush(true);

        hopper.setElevationProvider(new SRTMProvider(DIR));
        hopper.importOrLoad();

        GHResponse rsp = hopper.route(new GHRequest(43.730729, 7.421288, 43.727697, 7.419199).
                setAlgorithm(ASTAR).setProfile(profile));

        ResponsePath res = rsp.getBest();
        assertEquals(1614.3, res.getDistance(), .1);
        assertEquals(55, res.getPoints().size());
        assertTrue(res.getPoints().is3D());

        InstructionList il = res.getInstructions();
        assertEquals(12, il.size());
        assertTrue(il.get(0).getPoints().is3D());

        String str = res.getPoints().toString();

        assertEquals("(43.730684662577524,7.421283725164733,62.0), (43.7306797,7.4213823,66.0), " +
                "(43.731098,7.4215463,45.0), (43.7312991,7.42159,45.0), (43.7313271,7.4214147,45.0), " +
                "(43.7312506,7.4213664,45.0), (43.7312822,7.4211156,52.0), (43.7313624,7.4211455,52.0), " +
                "(43.7313714,7.4211233,52.0), (43.7314858,7.4211734,52.0), (43.7315753,7.4208688,52.0), " +
                "(43.7316061,7.4208249,52.0), (43.7316404,7.4208503,52.0), (43.7316741,7.4210502,52.0), " +
                "(43.7316276,7.4214636,45.0), (43.7316391,7.4215065,45.0), (43.7316664,7.4214904,45.0), " +
                "(43.7317185,7.4211861,52.0), (43.7319676,7.4206159,19.0), (43.732038,7.4203936,20.0), " +
                "(43.7322266,7.4196414,26.0), (43.7323236,7.4192656,26.0), (43.7323374,7.4190461,26.0), " +
                "(43.7323875,7.4189195,26.0), (43.731974,7.4181688,29.0), (43.7316421,7.4173042,23.0), " +
                "(43.7315686,7.4170356,31.0), (43.7314269,7.4166815,31.0), (43.7312401,7.4163184,49.0), " +
                "(43.7308286,7.4157613,29.399999618530273), (43.730662,7.4155599,22.0), " +
                "(43.7303643,7.4151193,51.0), (43.729579,7.4137274,40.0), (43.7295167,7.4137244,40.0), " +
                "(43.7294669,7.4137725,40.0), (43.7285987,7.4149068,23.0), (43.7285167,7.4149272,22.0), " +
                "(43.7283974,7.4148646,22.0), (43.7285619,7.4151365,23.0), (43.7285774,7.4152444,23.0), " +
                "(43.7285763,7.4159759,21.0), (43.7285238,7.4161982,20.0), (43.7284592,7.4163655,18.0), " +
                "(43.7281669,7.4168192,18.0), (43.7281442,7.4169449,18.0), (43.7281684,7.4172435,14.0), " +
                "(43.7282784,7.4179606,14.0), (43.7282757,7.418175,11.0), (43.7282319,7.4183683,11.0), " +
                "(43.7281482,7.4185473,11.0), (43.7280654,7.4186535,11.0), (43.7279259,7.418748,11.0), " +
                "(43.727779,7.4187731,11.0), (43.7276825,7.4190072,11.0), " +
                "(43.72767974015672,7.419198523220426,11.0)", str);

        assertEquals(84, res.getAscend(), 1e-1);
        assertEquals(135, res.getDescend(), 1e-1);

        assertEquals(55, res.getPoints().size());
        assertEquals(new GHPoint3D(43.73068455771767, 7.421283689825812, 62.0), res.getPoints().get(0));
        assertEquals(new GHPoint3D(43.727679637988224, 7.419198521975086, 11.0), res.getPoints().get(res.getPoints().size() - 1));

        assertEquals(62, res.getPoints().get(0).getEle(), 1e-2);
        assertEquals(66, res.getPoints().get(1).getEle(), 1e-2);
        assertEquals(52, res.getPoints().get(10).getEle(), 1e-2);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testSRTMWithTunnelInterpolation(boolean withTunnelInterpolation) {
        final String profile = "profile";
        final String vehicle = "foot";
        final String weighting = "shortest";

        GraphHopper hopper = new GraphHopper().
                setGraphHopperLocation(GH_LOCATION).
                setOSMFile(MONACO).
                setProfiles(new Profile(profile).setVehicle(vehicle).setWeighting(weighting)).
                setStoreOnFlush(true);

        if (!withTunnelInterpolation) {
            hopper.setTagParserFactory(new DefaultTagParserFactory() {
                @Override
                public TagParser create(EncodedValueLookup lookup, String name, PMap properties) {
                    TagParser parser = super.create(lookup, name, properties);
                    if (name.equals("road_environment"))
                        parser = new OSMRoadEnvironmentParser(lookup.getEnumEncodedValue(RoadEnvironment.KEY, RoadEnvironment.class)) {
                            @Override
                            public void handleWayTags(IntsRef edgeFlags, ReaderWay readerWay, IntsRef relationFlags) {
                                // do not change RoadEnvironment to avoid triggering tunnel interpolation
                            }
                        };
                    return parser;
                }
            });
            hopper.setEncodedValuesString("road_environment");
        }

        hopper.setElevationProvider(new SRTMProvider(DIR));
        hopper.importOrLoad();

        GHPoint from = new GHPoint(43.7405647, 7.4299266);
        GHPoint to = new GHPoint(43.7378990, 7.4279780);

        // make sure we hit tower nodes, because all we really want is test the elevation interpolation
        assertEquals(Snap.Position.TOWER, hopper.getLocationIndex().findClosest(from.lat, from.lon, EdgeFilter.ALL_EDGES).getSnappedPosition());
        assertEquals(Snap.Position.TOWER, hopper.getLocationIndex().findClosest(to.lat, to.lon, EdgeFilter.ALL_EDGES).getSnappedPosition());

        GHResponse rsp = hopper.route(new GHRequest(from, to)
                .setProfile(profile));
        ResponsePath res = rsp.getBest();
        PointList pointList = res.getPoints();
        assertEquals(6, pointList.size());
        assertTrue(pointList.is3D());

        if (withTunnelInterpolation) {
            assertEquals(351.8, res.getDistance(), .1);
            assertEquals(17, pointList.getEle(0), .1);
            assertEquals(19.04, pointList.getEle(1), .1);
            assertEquals(21.67, pointList.getEle(2), .1);
            assertEquals(25.03, pointList.getEle(3), .1);
            assertEquals(28.65, pointList.getEle(4), .1);
            assertEquals(34.00, pointList.getEle(5), .1);
        } else {
            assertEquals(358.3, res.getDistance(), .1);
            assertEquals(17.0, pointList.getEle(0), .1);
            assertEquals(23.0, pointList.getEle(1), .1);
            assertEquals(23.0, pointList.getEle(2), .1);
            assertEquals(41.0, pointList.getEle(3), .1);
            assertEquals(19.0, pointList.getEle(4), .1);
            assertEquals(34.0, pointList.getEle(5), .1);
        }
    }

    @Test
    public void testSRTMWithLongEdgeSampling() {
        final String profile = "profile";
        final String vehicle = "foot";
        final String weighting = "shortest";

        GraphHopper hopper = new GraphHopper().
                setGraphHopperLocation(GH_LOCATION).
                setOSMFile(MONACO).
                setStoreOnFlush(true).
                setProfiles(new Profile("profile").setVehicle(vehicle).setWeighting(weighting));
        hopper.getRouterConfig().setElevationWayPointMaxDistance(1.);
        hopper.getReaderConfig().
                setElevationMaxWayPointDistance(1.).
                setLongEdgeSamplingDistance(30);

        SRTMProvider elevationProvider = new SRTMProvider(DIR);
        elevationProvider.setInterpolate(true);
        hopper.setElevationProvider(elevationProvider);
        hopper.importOrLoad();

        GHResponse rsp = hopper.route(new GHRequest(43.730729, 7.421288, 43.727697, 7.419199).
                setAlgorithm(ASTAR).setProfile(profile));

        ResponsePath arsp = rsp.getBest();
        assertEquals(1569.7, arsp.getDistance(), .1);
        assertEquals(60, arsp.getPoints().size());
        assertTrue(arsp.getPoints().is3D());

        InstructionList il = arsp.getInstructions();
        assertEquals(12, il.size());
        assertTrue(il.get(0).getPoints().is3D());

        String str = arsp.getPoints().toString();

        assertEquals(23.8, arsp.getAscend(), 1e-1);
        assertEquals(67.4, arsp.getDescend(), 1e-1);

        assertEquals(60, arsp.getPoints().size());
        assertEquals(new GHPoint3D(43.73068455771767, 7.421283689825812, 55.82900047302246), arsp.getPoints().get(0));
        assertEquals(new GHPoint3D(43.727679637988224, 7.419198521975086, 12.274499893188477), arsp.getPoints().get(arsp.getPoints().size() - 1));

        assertEquals(55.83, arsp.getPoints().get(0).getEle(), 1e-2);
        assertEquals(57.78, arsp.getPoints().get(1).getEle(), 1e-2);
        assertEquals(52.43, arsp.getPoints().get(10).getEle(), 1e-2);
    }

    @Disabled
    @Test
    public void testSkadiElevationProvider() {
        final String profile = "profile";
        final String vehicle = "foot";
        final String weighting = "shortest";

        GraphHopper hopper = new GraphHopper().
                setGraphHopperLocation(GH_LOCATION).
                setOSMFile(MONACO).
                setProfiles(
                        new Profile(profile).setVehicle(vehicle).setWeighting(weighting)
                ).
                setStoreOnFlush(true);

        hopper.setElevationProvider(new SkadiProvider(DIR));
        hopper.importOrLoad();

        GHResponse rsp = hopper.route(new GHRequest(43.730729, 7.421288, 43.727697, 7.419199)
                .setProfile(profile));

        ResponsePath res = rsp.getBest();
        assertEquals(1601.6, res.getDistance(), .1);
        assertEquals(55, res.getPoints().size());
        assertTrue(res.getPoints().is3D());
        assertEquals(69, res.getAscend(), 1e-1);
        assertEquals(121, res.getDescend(), 1e-1);
        assertEquals(64.5, res.getPoints().get(0).getEle(), 1e-2);
    }

    @Test
    public void testKremsCyclewayInstructionsWithWayTypeInfo() {
        final String profile1 = "foot_profile";
        final String profile2 = "bike_profile";
        final String vehicle1 = "foot";
        final String vehicle2 = "bike";
        final String weighting = "fastest";

        GraphHopper hopper = new GraphHopper().
                setGraphHopperLocation(GH_LOCATION).
                setOSMFile(KREMS).
                setProfiles(
                        new Profile(profile1).setVehicle(vehicle1).setWeighting(weighting),
                        new Profile(profile2).setVehicle(vehicle2).setWeighting(weighting)).
                setStoreOnFlush(true).
                importOrLoad();

        Translation tr = hopper.getTranslationMap().getWithFallBack(Locale.US);
        GHResponse rsp = hopper.route(new GHRequest(48.410987, 15.599492, 48.383419, 15.659294).
                setProfile(profile2));
        assertFalse(rsp.hasErrors());
        ResponsePath res = rsp.getBest();
        assertEquals(6931.8, res.getDistance(), .1);
        assertEquals(103, res.getPoints().size());

        InstructionList il = res.getInstructions();
        assertEquals(19, il.size());

        assertEquals("continue onto Obere Landstraße", il.get(0).getTurnDescription(tr));
        assertEquals(69.28, (Double) il.get(0).getExtraInfoJSON().get("heading"), .01);
        assertEquals("turn left onto Kirchengasse", il.get(1).getTurnDescription(tr));

        assertEquals("turn right onto Pfarrplatz", il.get(2).getTurnDescription(tr));
        assertEquals("turn right onto Margarethenstraße", il.get(3).getTurnDescription(tr));
        assertEquals("keep left onto Hoher Markt", il.get(4).getTurnDescription(tr));
        assertEquals("turn right onto Wegscheid", il.get(6).getTurnDescription(tr));
        assertEquals("continue onto Wegscheid", il.get(7).getTurnDescription(tr));
        assertEquals("turn right onto Ringstraße", il.get(8).getTurnDescription(tr));
        assertEquals("keep left onto Eyblparkstraße", il.get(9).getTurnDescription(tr));
        assertEquals("keep left onto Austraße", il.get(10).getTurnDescription(tr));
        assertEquals("keep left onto Rechte Kremszeile", il.get(11).getTurnDescription(tr));
        //..
        assertEquals("turn right onto Treppelweg", il.get(15).getTurnDescription(tr));

        // do not return 'get off bike' for foot
        rsp = hopper.route(new GHRequest(48.410987, 15.599492, 48.411172, 15.600371).
                setAlgorithm(ASTAR).setProfile(profile1));
        assertFalse(rsp.hasErrors());
        il = rsp.getBest().getInstructions();
        assertEquals("continue onto Obere Landstraße", il.get(0).getTurnDescription(tr));
    }

    @Test
    public void testRoundaboutInstructionsWithCH() {
        final String profile1 = "my_profile";
        final String profile2 = "your_profile";
        final String vehicle1 = "car";
        final String vehicle2 = "bike";
        final String weighting = "fastest";

        GraphHopper hopper = new GraphHopper().
                setGraphHopperLocation(GH_LOCATION).
                setOSMFile(MONACO).
                setProfiles(Arrays.asList(
                        new Profile(profile1).setVehicle(vehicle1).setWeighting(weighting),
                        new Profile(profile2).setVehicle(vehicle2).setWeighting(weighting))
                ).
                setStoreOnFlush(true);
        hopper.getCHPreparationHandler().setCHProfiles(
                new CHProfile(profile1),
                new CHProfile(profile2)
        );
        hopper.setMinNetworkSize(0);
        hopper.importOrLoad();

        assertEquals(2, hopper.getCHGraphs().size());

        GHResponse rsp = hopper.route(new GHRequest(43.745084, 7.430513, 43.745247, 7.430347)
                .setProfile(profile1));

        ResponsePath res = rsp.getBest();
        assertEquals(2, ((RoundaboutInstruction) res.getInstructions().get(1)).getExitNumber());

        rsp = hopper.route(new GHRequest(43.745968, 7.42907, 43.745832, 7.428614)
                .setProfile(profile1));
        res = rsp.getBest();
        assertEquals(2, ((RoundaboutInstruction) res.getInstructions().get(1)).getExitNumber());

        rsp = hopper.route(new GHRequest(43.745948, 7.42914, 43.746173, 7.428834)
                .setProfile(profile1));
        res = rsp.getBest();
        assertEquals(1, ((RoundaboutInstruction) res.getInstructions().get(1)).getExitNumber());

        rsp = hopper.route(new GHRequest(43.735817, 7.417096, 43.735666, 7.416587)
                .setProfile(profile1));
        res = rsp.getBest();
        assertEquals(2, ((RoundaboutInstruction) res.getInstructions().get(1)).getExitNumber());
    }

    @Test
    public void testCircularJunctionInstructionsWithCH() {
        String profile1 = "profile1";
        String profile2 = "profile2";
        String vehicle1 = "car";
        String vehicle2 = "bike";
        String weighting = "fastest";
        GraphHopper hopper = new GraphHopper().
                setGraphHopperLocation(GH_LOCATION).
                setOSMFile(BERLIN).
                setProfiles(
                        new Profile(profile1).setVehicle(vehicle1).setWeighting(weighting),
                        new Profile(profile2).setVehicle(vehicle2).setWeighting(weighting)
                ).
                setStoreOnFlush(true);
        hopper.getCHPreparationHandler().setCHProfiles(
                new CHProfile(profile1),
                new CHProfile(profile2)
        );
        hopper.setMinNetworkSize(0);
        hopper.importOrLoad();

        assertEquals(2, hopper.getCHGraphs().size());
        GHResponse rsp = hopper.route(new GHRequest(52.513505, 13.350443, 52.513505, 13.350245)
                .setProfile(profile1));

        assertFalse(rsp.hasErrors(), rsp.getErrors().toString());
        Instruction instr = rsp.getBest().getInstructions().get(1);
        assertTrue(instr instanceof RoundaboutInstruction);
        assertEquals(5, ((RoundaboutInstruction) instr).getExitNumber());
    }

    @Test
    public void testMultipleVehiclesWithCH() {
        final String bikeProfile = "bike_profile";
        final String carProfile = "car_profile";
        final String weighting = "fastest";
        List<Profile> profiles = asList(
                new Profile(bikeProfile).setVehicle("bike").setWeighting(weighting),
                new Profile(carProfile).setVehicle("car").setWeighting(weighting)
        );
        GraphHopper hopper = new GraphHopper().
                setGraphHopperLocation(GH_LOCATION).
                setOSMFile(MONACO).
                setProfiles(profiles).
                setStoreOnFlush(true);
        hopper.getCHPreparationHandler().setCHProfiles(
                new CHProfile(bikeProfile),
                new CHProfile(carProfile)
        );
        hopper.importOrLoad();
        GHResponse rsp = hopper.route(new GHRequest(43.73005, 7.415707, 43.741522, 7.42826)
                .setProfile(carProfile));
        ResponsePath res = rsp.getBest();
        assertFalse(rsp.hasErrors(), rsp.getErrors().toString());
        assertEquals(207, res.getTime() / 1000f, 1);
        assertEquals(2837, res.getDistance(), 1);

        rsp = hopper.route(new GHRequest(43.73005, 7.415707, 43.741522, 7.42826)
                .setProfile(bikeProfile));
        res = rsp.getBest();
        assertFalse(rsp.hasErrors(), rsp.getErrors().toString());
        assertEquals(511, res.getTime() / 1000f, 1);
        assertEquals(2481, res.getDistance(), 1);

        rsp = hopper.route(new GHRequest(43.73005, 7.415707, 43.741522, 7.42826)
                .setProfile("profile3"));
        assertTrue(rsp.hasErrors(), "only car_profile and bike_profile exist, request for profile3 should fail");

        GHRequest req = new GHRequest().
                addPoint(new GHPoint(43.741069, 7.426854)).
                addPoint(new GHPoint(43.744445, 7.429483)).
                setHeadings(Arrays.asList(0., 190.)).
                setProfile(bikeProfile);

        rsp = hopper.route(req);
        assertTrue(rsp.hasErrors(), "heading not allowed for CH enabled graph");
    }

    @Test
    public void testIfCHIsUsed() {
        // route directly after import
        executeCHFootRoute(false);

        // now only load is called
        executeCHFootRoute(false);
    }

    private void executeCHFootRoute(boolean sort) {
        final String profile = "profile";
        final String vehicle = "foot";
        final String weighting = "shortest";
        GraphHopper hopper = new GraphHopper().
                setGraphHopperLocation(GH_LOCATION).
                setOSMFile(MONACO).
                setProfiles(new Profile(profile).setVehicle(vehicle).setWeighting(weighting)).
                setStoreOnFlush(true).
                setSortGraph(sort);
        hopper.getCHPreparationHandler().setCHProfiles(new CHProfile(profile));
        hopper.importOrLoad();

        // same query as in testMonacoWithInstructions
        GHResponse rsp = hopper.route(new GHRequest(43.727687, 7.418737, 43.74958, 7.436566).
                setProfile(profile));

        ResponsePath bestPath = rsp.getBest();
        // identify the number of counts to compare with none-CH foot route which had nearly 700 counts
        long sum = rsp.getHints().getLong("visited_nodes.sum", 0);
        assertNotEquals(sum, 0);
        assertTrue(sum < 147, "Too many nodes visited " + sum);
        assertEquals(3437.1, bestPath.getDistance(), .1);
        assertEquals(85, bestPath.getPoints().size());

        hopper.close();
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
        final String profile = "profile";
        final String vehicle = "foot";
        final String weighting = "fastest";
        GraphHopper hopper = new GraphHopper().
                setGraphHopperLocation(GH_LOCATION).
                setOSMFile(MONACO).
                setProfiles(new Profile(profile).setVehicle(vehicle).setWeighting(weighting)).
                setStoreOnFlush(true).
                importOrLoad();

        GHRequest rq = new GHRequest().
                addPoint(new GHPoint(43.741069, 7.426854)).
                setHeadings(Collections.singletonList(50.)).
                setProfile(profile).
                setAlgorithm(ROUND_TRIP);
        rq.putHint(RoundTrip.DISTANCE, 1000);
        rq.putHint(RoundTrip.SEED, 0);

        GHResponse rsp = hopper.route(rq);

        assertEquals(1, rsp.getAll().size());
        ResponsePath res = rsp.getBest();
        assertEquals(1.49, rsp.getBest().getDistance() / 1000f, .01);
        assertEquals(19, rsp.getBest().getTime() / 1000f / 60, 1);
        assertEquals(66, res.getPoints().size());
    }

    @Test
    public void testPathDetails1216() {
        final String profile = "profile";
        final String vehicle = "car";
        final String weighting = "fastest";

        GraphHopper hopper = new GraphHopper().
                setGraphHopperLocation(GH_LOCATION).
                setOSMFile(BAYREUTH).
                setProfiles(new Profile(profile).setVehicle(vehicle).setWeighting(weighting));
        hopper.importOrLoad();

        GHRequest req = new GHRequest().
                setPoints(Arrays.asList(
                        new GHPoint(49.984352, 11.498802),
                        // This is exactly between two edges with different speed values
                        new GHPoint(49.984565, 11.499188),
                        new GHPoint(49.9847, 11.499612)
                )).
                setProfile(profile).
                setPathDetails(Collections.singletonList(Parameters.Details.AVERAGE_SPEED));

        GHResponse rsp = hopper.route(req);
        assertFalse(rsp.hasErrors(), rsp.getErrors().toString());
    }

    @Test
    public void testPathDetailsSamePoint() {
        final String profile = "profile";
        final String vehicle = "car";
        final String weighting = "fastest";
        GraphHopper hopper = new GraphHopper().
                setGraphHopperLocation(GH_LOCATION).
                setOSMFile(BAYREUTH).
                setProfiles(new Profile(profile).setVehicle(vehicle).setWeighting(weighting));
        hopper.importOrLoad();

        GHRequest req = new GHRequest().
                addPoint(new GHPoint(49.984352, 11.498802)).
                addPoint(new GHPoint(49.984352, 11.498802)).
                setProfile(profile).
                setPathDetails(Collections.singletonList(Parameters.Details.AVERAGE_SPEED));

        GHResponse rsp = hopper.route(req);

        assertFalse(rsp.hasErrors());
    }

    @Test
    public void testFlexMode_631() {
        final String profile = "car_profile";
        final String vehicle = "car";
        final String weighting = "fastest";
        GraphHopper hopper = new GraphHopper().
                setGraphHopperLocation(GH_LOCATION).
                setOSMFile(MONACO).
                setProfiles(new Profile(profile).setVehicle(vehicle).setWeighting(weighting)).
                setStoreOnFlush(true);

        hopper.getCHPreparationHandler().
                setCHProfiles(new CHProfile(profile));

        hopper.getLMPreparationHandler().
                setLMProfiles(new LMProfile(profile).setMaximumLMWeight(2000));

        hopper.importOrLoad();

        GHRequest req = new GHRequest(43.727687, 7.418737, 43.74958, 7.436566).
                setProfile(profile);
        // request speed mode
        req.putHint(Landmark.DISABLE, true);
        req.putHint(CH.DISABLE, false);

        GHResponse rsp = hopper.route(req);
        long chSum = rsp.getHints().getLong("visited_nodes.sum", 0);
        assertTrue(chSum < 70, "Too many visited nodes for ch mode " + chSum);
        ResponsePath bestPath = rsp.getBest();
        assertEquals(3587, bestPath.getDistance(), 1);
        assertEquals(91, bestPath.getPoints().size());

        // request flex mode
        req.setAlgorithm(Parameters.Algorithms.ASTAR_BI);
        req.putHint(Landmark.DISABLE, true);
        req.putHint(CH.DISABLE, true);
        rsp = hopper.route(req);
        long flexSum = rsp.getHints().getLong("visited_nodes.sum", 0);
        assertTrue(flexSum > 60, "Too few visited nodes for flex mode " + flexSum);

        bestPath = rsp.getBest();
        assertEquals(3587, bestPath.getDistance(), 1);
        assertEquals(91, bestPath.getPoints().size());

        // request hybrid mode
        req.putHint(Landmark.DISABLE, false);
        req.putHint(CH.DISABLE, true);
        rsp = hopper.route(req);

        long hSum = rsp.getHints().getLong("visited_nodes.sum", 0);
        // hybrid is better than CH: 40 vs. 42 !
        assertTrue(hSum != chSum, "Visited nodes for hybrid mode should be different to CH but " + hSum + "==" + chSum);
        assertTrue(hSum < flexSum, "Too many visited nodes for hybrid mode " + hSum + ">=" + flexSum);

        bestPath = rsp.getBest();
        assertEquals(3587, bestPath.getDistance(), 1);
        assertEquals(91, bestPath.getPoints().size());

        // note: combining hybrid & speed mode is currently not possible and should be avoided: #1082
    }

    @Test
    public void testCrossQuery() {
        final String profile1 = "p1";
        final String profile2 = "p2";
        final String profile3 = "p3";
        GraphHopper hopper = new GraphHopper().
                setGraphHopperLocation(GH_LOCATION).
                setOSMFile(MONACO).
                setProfiles(
                        new Profile(profile1).setVehicle("car").setWeighting("short_fastest").putHint("short_fastest.distance_factor", 0.07),
                        new Profile(profile2).setVehicle("car").setWeighting("short_fastest").putHint("short_fastest.distance_factor", 0.10),
                        new Profile(profile3).setVehicle("car").setWeighting("short_fastest").putHint("short_fastest.distance_factor", 0.15)
                ).
                setStoreOnFlush(true);

        hopper.getLMPreparationHandler().
                setLMProfiles(
                        // we have an LM setup for each profile, but only one LM preparation that we use for all of them!
                        // this works because profile1's weight is the lowest for every edge
                        new LMProfile(profile1),
                        new LMProfile(profile2).setPreparationProfile(profile1),
                        new LMProfile(profile3).setPreparationProfile(profile1)
                );
        hopper.setMinNetworkSize(0);
        hopper.importOrLoad();

        // flex
        testCrossQueryAssert(profile1, hopper, 528.3, 196, true);
        testCrossQueryAssert(profile2, hopper, 635.8, 198, true);
        testCrossQueryAssert(profile3, hopper, 815.2, 198, true);

        // LM (should be the same as flex, but with less visited nodes!)
        testCrossQueryAssert(profile1, hopper, 528.3, 108, false);
        testCrossQueryAssert(profile2, hopper, 635.8, 162, false);
        // this is actually interesting: the number of visited nodes increases: cross-querying 'works',
        // but can even perform *worse*, because the landmarks were not customized for the weighting in use.
        // Creating a separate LM preparation for profile3 yields 108 (not shown)
        testCrossQueryAssert(profile3, hopper, 815.2, 236, false);
    }

    private void testCrossQueryAssert(String profile, GraphHopper hopper, double expectedWeight, int expectedVisitedNodes, boolean disableLM) {
        GHResponse response = hopper.route(new GHRequest(43.727687, 7.418737, 43.74958, 7.436566).
                setProfile(profile).putHint("lm.disable", disableLM));
        assertEquals(expectedWeight, response.getBest().getRouteWeight(), 0.1);
        int visitedNodes = response.getHints().getInt("visited_nodes.sum", 0);
        assertEquals(expectedVisitedNodes, visitedNodes);
    }

    @Test
    public void testLMConstraintsForCustomProfiles() {
        GraphHopper hopper = new GraphHopper().
                setGraphHopperLocation(GH_LOCATION).
                setOSMFile(MONACO).
                setProfiles(
                        new CustomProfile("p1").setCustomModel(new CustomModel().setDistanceInfluence(100d)).setVehicle("car"),
                        new CustomProfile("p2").setCustomModel(new CustomModel().setDistanceInfluence(100d)).setVehicle("car")).
                setStoreOnFlush(true);

        hopper.getLMPreparationHandler().setLMProfiles(new LMProfile("p1"));
        hopper.setMinNetworkSize(0);
        hopper.importOrLoad();

        GHResponse response = hopper.route(new GHRequest(43.727687, 7.418737, 43.74958, 7.436566).
                setProfile("p1").putHint("lm.disable", false));
        assertEquals(3587, response.getBest().getDistance(), 1);

        // use smaller distance influence to force violating the LM constraint
        final CustomModel customModel = new CustomModel().setDistanceInfluence(0d);
        response = hopper.route(new GHRequest(43.727687, 7.418737, 43.74958, 7.436566).
                setCustomModel(customModel).
                setProfile("p1").putHint("lm.disable", false));
        assertTrue(response.hasErrors(), response.getErrors().toString());
        assertEquals(IllegalArgumentException.class, response.getErrors().get(0).getClass());

        // but disabling LM must make it working as no LM is used
        response = hopper.route(new GHRequest(43.727687, 7.418737, 43.74958, 7.436566).
                setCustomModel(customModel).
                setProfile("p1").putHint("lm.disable", true));
        assertFalse(response.hasErrors(), response.getErrors().toString());
        assertEquals(3587, response.getBest().getDistance(), 1);

        // currently required to disable LM for p2 too, see #1904 (default is LM for *all* profiles once LM preparation is enabled for any profile)
        response = hopper.route(new GHRequest(43.727687, 7.418737, 43.74958, 7.436566).
                setCustomModel(customModel).
                setProfile("p2"));
        assertTrue(response.getErrors().get(0).toString().contains("Cannot find LM preparation for the requested profile: 'p2'"), response.getErrors().toString());
        assertEquals(IllegalArgumentException.class, response.getErrors().get(0).getClass());

        response = hopper.route(new GHRequest(43.727687, 7.418737, 43.74958, 7.436566).
                setCustomModel(customModel).
                setProfile("p2").putHint("lm.disable", true));
        assertFalse(response.hasErrors(), response.getErrors().toString());
        assertEquals(3587, response.getBest().getDistance(), 1);
    }

    @Test
    public void testCreateWeightingHintsMerging() {
        final String profile = "profile";
        final String vehicle = "mtb";
        final String weighting = "shortest";

        GraphHopper hopper = new GraphHopper().
                setGraphHopperLocation(GH_LOCATION).
                setOSMFile(MONACO).
                setProfiles(new Profile(profile).setVehicle(vehicle).setWeighting(weighting).setTurnCosts(true).putHint(U_TURN_COSTS, 123));
        hopper.importOrLoad();

        // if we do not pass u_turn_costs with the request hints we get those from the profile
        Weighting w = hopper.createWeighting(hopper.getProfiles().get(0), new PMap());
        assertEquals(123.0, w.calcTurnWeight(5, 6, 5));

        // we can overwrite the u_turn_costs given in the profile
        w = hopper.createWeighting(hopper.getProfiles().get(0), new PMap().putObject(U_TURN_COSTS, 46));
        assertEquals(46.0, w.calcTurnWeight(5, 6, 5));
    }

    @Test
    public void testPreparedProfileNotAvailable() {
        final String profile1 = "fast_profile";
        final String profile2 = "short_fast_profile";
        final String vehicle = "car";
        final String weighting1 = "fastest";
        final String weighting2 = "short_fastest";

        GraphHopper hopper = new GraphHopper().
                setGraphHopperLocation(GH_LOCATION).
                setOSMFile(MONACO).
                setProfiles(
                        new Profile(profile1).setVehicle(vehicle).setWeighting(weighting1),
                        new Profile(profile2).setVehicle(vehicle).setWeighting(weighting2)
                ).
                setStoreOnFlush(true);

        hopper.getCHPreparationHandler().
                setCHProfiles(new CHProfile(profile1));

        hopper.getLMPreparationHandler().
                setLMProfiles(new LMProfile(profile1).setMaximumLMWeight(2000));

        hopper.importOrLoad();
        // request a profile that was not prepared
        GHRequest req = new GHRequest(43.727687, 7.418737, 43.74958, 7.436566).
                setProfile(profile2);

        // try with CH
        req.putHint(CH.DISABLE, false);
        req.putHint(Landmark.DISABLE, false);
        GHResponse res = hopper.route(req);
        assertTrue(res.hasErrors(), res.getErrors().toString());
        assertTrue(res.getErrors().get(0).getMessage().contains("Cannot find CH preparation for the requested profile: 'short_fast_profile'"), res.getErrors().toString());

        // try with LM
        req.putHint(CH.DISABLE, true);
        req.putHint(Landmark.DISABLE, false);
        res = hopper.route(req);
        assertTrue(res.hasErrors(), res.getErrors().toString());
        assertTrue(res.getErrors().get(0).getMessage().contains("Cannot find LM preparation for the requested profile: 'short_fast_profile'"), res.getErrors().toString());

        // falling back to non-prepared algo works
        req.putHint(CH.DISABLE, true);
        req.putHint(Landmark.DISABLE, true);
        res = hopper.route(req);
        assertFalse(res.hasErrors(), res.getErrors().toString());
        assertEquals(3587, res.getBest().getDistance(), 1);
    }

    @Test
    public void testDisablingLM() {
        // setup GH with LM preparation but no CH preparation
        final String profile = "profile";
        final String vehicle = "car";
        final String weighting = "fastest";
        // note that the pure presence of the bike profile leads to 'ghost' junctions with the bike network even for
        // cars such that the number of visited nodes depends on the bike profile added here or not, #1910
        GraphHopper hopper = new GraphHopper().
                setGraphHopperLocation(GH_LOCATION).
                setOSMFile(MONACO).
                setProfiles(new Profile(profile).setVehicle(vehicle).setWeighting(weighting),
                        new Profile("bike").setVehicle("bike").setWeighting(weighting)).
                setStoreOnFlush(true);
        hopper.getLMPreparationHandler().
                setLMProfiles(new LMProfile(profile).setMaximumLMWeight(2000));
        hopper.importOrLoad();

        // we can switch LM on/off
        GHRequest req = new GHRequest(43.727687, 7.418737, 43.74958, 7.436566).
                setProfile(profile);

        req.putHint(Landmark.DISABLE, false);
        GHResponse res = hopper.route(req);
        assertTrue(res.getHints().getInt("visited_nodes.sum", 0) < 150);

        req.putHint(Landmark.DISABLE, true);
        res = hopper.route(req);
        assertTrue(res.getHints().getInt("visited_nodes.sum", 0) > 170);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testCompareAlgos(boolean turnCosts) {
        final String profile = "car";
        final String vehicle = "car";
        final String weighting = "fastest";
        GraphHopper hopper = new GraphHopper().
                setGraphHopperLocation(GH_LOCATION).
                setOSMFile(MOSCOW).
                setProfiles(new Profile(profile).setVehicle(vehicle).setWeighting(weighting).setTurnCosts(turnCosts));
        hopper.getCHPreparationHandler().setCHProfiles(new CHProfile(profile));
        hopper.getLMPreparationHandler().setLMProfiles(new LMProfile(profile));
        hopper.importOrLoad();

        long seed = System.nanoTime();
        Random rnd = new Random(seed);
        for (int i = 0; i < 100; i++) {
            BBox bounds = hopper.getBaseGraph().getBounds();
            double lat1 = bounds.minLat + rnd.nextDouble() * (bounds.maxLat - bounds.minLat);
            double lat2 = bounds.minLat + rnd.nextDouble() * (bounds.maxLat - bounds.minLat);
            double lon1 = bounds.minLon + rnd.nextDouble() * (bounds.maxLon - bounds.minLon);
            double lon2 = bounds.minLon + rnd.nextDouble() * (bounds.maxLon - bounds.minLon);
            GHRequest req = new GHRequest(lat1, lon1, lat2, lon2);
            req.setProfile(profile);
            req.getHints().putObject(CH.DISABLE, false).putObject(Landmark.DISABLE, true);
            ResponsePath pathCH = hopper.route(req).getBest();
            req.getHints().putObject(CH.DISABLE, true).putObject(Landmark.DISABLE, false);
            ResponsePath pathLM = hopper.route(req).getBest();
            req.getHints().putObject(CH.DISABLE, true).putObject(Landmark.DISABLE, true);
            ResponsePath path = hopper.route(req).getBest();

            String failMessage = "seed: " + seed + ", i=" + i;
            assertEquals(path.hasErrors(), pathCH.hasErrors(), failMessage);
            assertEquals(path.hasErrors(), pathLM.hasErrors(), failMessage);

            if (!path.hasErrors()) {
                assertEquals(path.getDistance(), pathCH.getDistance(), 0.1, failMessage);
                assertEquals(path.getDistance(), pathLM.getDistance(), 0.1, failMessage);

                assertEquals(path.getTime(), pathCH.getTime(), failMessage);
                assertEquals(path.getTime(), pathLM.getTime(), failMessage);
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testAStarCHBug(boolean turnCosts) {
        final String profile = "car";
        final String vehicle = "car";
        final String weighting = "fastest";
        GraphHopper hopper = new GraphHopper().
                setGraphHopperLocation(GH_LOCATION).
                setOSMFile(MOSCOW).
                setProfiles(new Profile(profile).setVehicle(vehicle).setWeighting(weighting).setTurnCosts(turnCosts));
        hopper.getCHPreparationHandler().setCHProfiles(new CHProfile(profile));
        hopper.importOrLoad();

        GHRequest req = new GHRequest(55.821744463888116, 37.60380604129401, 55.82608197039734, 37.62055856655137);
        req.setProfile(profile);
        req.getHints().putObject(CH.DISABLE, false);
        ResponsePath pathCH = hopper.route(req).getBest();
        req.getHints().putObject(CH.DISABLE, true);
        ResponsePath path = hopper.route(req).getBest();

        assertFalse(path.hasErrors());
        assertFalse(pathCH.hasErrors());
        assertEquals(path.getDistance(), pathCH.getDistance(), 0.1);
        assertEquals(path.getTime(), pathCH.getTime());
    }

    @Test
    public void testIssue1960() {
        final String profile = "car";
        final String vehicle = "car";
        final String weighting = "fastest";
        GraphHopper hopper = new GraphHopper().
                setGraphHopperLocation(GH_LOCATION).
                setOSMFile(MOSCOW).
                setProfiles(new Profile(profile).setVehicle(vehicle).setWeighting(weighting).setTurnCosts(true));
        hopper.getCHPreparationHandler().setCHProfiles(new CHProfile(profile));
        hopper.getLMPreparationHandler().setLMProfiles(new LMProfile(profile));

        hopper.importOrLoad();

        GHRequest req = new GHRequest(55.815670, 37.604613, 55.806151, 37.617823);
        req.setProfile("car");
        req.getHints().putObject(CH.DISABLE, false).putObject(Landmark.DISABLE, true);
        ResponsePath pathCH = hopper.route(req).getBest();
        req.getHints().putObject(CH.DISABLE, true).putObject(Landmark.DISABLE, false);
        ResponsePath pathLM = hopper.route(req).getBest();
        req.getHints().putObject(CH.DISABLE, true).putObject(Landmark.DISABLE, true);
        ResponsePath path = hopper.route(req).getBest();

        assertEquals(1995.38, pathCH.getDistance(), 0.1);
        assertEquals(1995.38, pathLM.getDistance(), 0.1);
        assertEquals(1995.38, path.getDistance(), 0.1);

        assertEquals(149504, pathCH.getTime());
        assertEquals(149504, pathLM.getTime());
        assertEquals(149504, path.getTime());
    }

    @Test
    public void testTurnCostsOnOff() {
        final String profile1 = "profile_no_turn_costs";
        final String profile2 = "profile_turn_costs";
        final String vehicle = "car";
        final String weighting = "fastest";
        GraphHopper hopper = new GraphHopper().
                setGraphHopperLocation(GH_LOCATION).
                setOSMFile(MOSCOW).
                // add profile with turn costs first when no flag encoder is explicitly added
                        setProfiles(
                        new Profile(profile2).setVehicle(vehicle).setWeighting(weighting).setTurnCosts(true).putHint(U_TURN_COSTS, 30),
                        new Profile(profile1).setVehicle(vehicle).setWeighting(weighting).setTurnCosts(false)
                ).
                setStoreOnFlush(true);
        hopper.importOrLoad();

        GHRequest req = new GHRequest(55.813357, 37.5958585, 55.811042, 37.594689);
        req.setPathDetails(Arrays.asList("distance", "time")).getHints().putObject("instructions", true);
        req.setProfile("profile_no_turn_costs");
        ResponsePath best = hopper.route(req).getBest();
        assertEquals(400, best.getDistance(), 1);
        consistenceCheck(best);

        req.setProfile("profile_turn_costs");
        best = hopper.route(req).getBest();
        assertEquals(476, best.getDistance(), 1);
        consistenceCheck(best);
    }

    @Test
    public void testTurnCostsOnOffCH() {
        final String profile1 = "profile_turn_costs";
        final String profile2 = "profile_no_turn_costs";
        final String vehicle = "car";
        final String weighting = "fastest";
        GraphHopper hopper = new GraphHopper().
                setGraphHopperLocation(GH_LOCATION).
                setOSMFile(MOSCOW).
                setProfiles(Arrays.asList(
                        new Profile(profile1).setVehicle(vehicle).setWeighting(weighting).setTurnCosts(true),
                        new Profile(profile2).setVehicle(vehicle).setWeighting(weighting).setTurnCosts(false)
                )).
                setStoreOnFlush(true);
        hopper.getCHPreparationHandler().setCHProfiles(
                new CHProfile(profile1),
                new CHProfile(profile2)
        );
        hopper.importOrLoad();

        GHRequest req = new GHRequest(55.813357, 37.5958585, 55.811042, 37.594689);
        req.setProfile("profile_no_turn_costs");
        assertEquals(400, hopper.route(req).getBest().getDistance(), 1);
        req.setProfile("profile_turn_costs");
        assertEquals(1044, hopper.route(req).getBest().getDistance(), 1);
    }

    @Test
    public void testCHOnOffWithTurnCosts() {
        final String profile = "my_car";
        final String vehicle = "car";
        final String weighting = "fastest";
        GraphHopper hopper = new GraphHopper().
                setGraphHopperLocation(GH_LOCATION).
                setOSMFile(MOSCOW).
                setProfiles(new Profile(profile).setVehicle(vehicle).setWeighting(weighting).setTurnCosts(true)).
                setStoreOnFlush(true);
        hopper.getCHPreparationHandler()
                .setCHProfiles(new CHProfile(profile));
        hopper.importOrLoad();

        GHRequest req = new GHRequest(55.813357, 37.5958585, 55.811042, 37.594689);
        req.setProfile("my_car");
        // with CH
        req.putHint(CH.DISABLE, true);
        GHResponse rsp1 = hopper.route(req);
        assertEquals(1044, rsp1.getBest().getDistance(), 1);
        // without CH
        req.putHint(CH.DISABLE, false);
        GHResponse rsp2 = hopper.route(req);
        assertEquals(1044, rsp2.getBest().getDistance(), 1);
        // just a quick check that we did not run the same algorithm twice
        assertNotEquals(rsp1.getHints().getInt("visited_nodes.sum", -1), rsp2.getHints().getInt("visited_nodes.sum", -1));
    }

    @Test
    public void testNodeBasedCHOnlyButTurnCostForNonCH() {
        final String profile1 = "car_profile_tc";
        final String profile2 = "car_profile_notc";
        final String weighting = "fastest";
        // before edge-based CH was added a common case was to use edge-based without CH and CH for node-based
        GraphHopper hopper = new GraphHopper().
                setGraphHopperLocation(GH_LOCATION).
                setOSMFile(MOSCOW).
                setProfiles(Arrays.asList(
                        new Profile(profile1).setVehicle("car").setWeighting(weighting).setTurnCosts(true),
                        new Profile(profile2).setVehicle("car").setWeighting(weighting).setTurnCosts(false))).
                setStoreOnFlush(true);
        hopper.getCHPreparationHandler()
                // we only do the CH preparation for the profile without turn costs
                .setCHProfiles(new CHProfile(profile2));
        hopper.importOrLoad();

        GHRequest req = new GHRequest(55.813357, 37.5958585, 55.811042, 37.594689);
        // without CH, turn turn costs on and off
        req.putHint(CH.DISABLE, true);
        req.setProfile(profile1);
        assertEquals(1044, hopper.route(req).getBest().getDistance(), 1);
        req.setProfile(profile2);
        assertEquals(400, hopper.route(req).getBest().getDistance(), 1);

        // with CH, turn turn costs on and off, since turn costs not supported for CH throw an error
        req.putHint(CH.DISABLE, false);
        req.setProfile(profile2);
        assertEquals(400, hopper.route(req).getBest().getDistance(), 1);
        req.setProfile(profile1);
        GHResponse rsp = hopper.route(req);
        assertEquals(1, rsp.getErrors().size());
        String expected = "Cannot find CH preparation for the requested profile: 'car_profile_tc'" +
                "\nYou can try disabling CH using ch.disable=true" +
                "\navailable CH profiles: [car_profile_notc]";
        assertTrue(rsp.getErrors().toString().contains(expected), "unexpected error:\n" + rsp.getErrors().toString() + "\nwhen expecting an error containing:\n" + expected
        );
    }

    @Test
    public void testEncoderWithTurnCostSupport_stillAllows_nodeBasedRouting() {
        // see #1698
        final String profile = "profile";
        final String vehicle = "foot";
        final String weighting = "fastest";
        GraphHopper hopper = new GraphHopper().
                setGraphHopperLocation(GH_LOCATION).
                setOSMFile(MOSCOW).
                setProfiles(new Profile(profile).setVehicle(vehicle).setWeighting(weighting),
                        new Profile("car").setVehicle("car").setWeighting(weighting).setTurnCosts(true));

        hopper.importOrLoad();
        GHPoint p = new GHPoint(55.813357, 37.5958585);
        GHPoint q = new GHPoint(55.811042, 37.594689);
        GHRequest req = new GHRequest(p, q);
        req.setProfile(profile);
        GHResponse rsp = hopper.route(req);
        assertEquals(0, rsp.getErrors().size(), "there should not be an error, but was: " + rsp.getErrors());
    }

    @Test
    public void testOneWaySubnetwork_issue1807() {
        // There is a straight-only turn restriction at the junction of Franziskastraße and Gudulastraße, which restricts
        // turning onto Gudulastraße. However, Gudulastraße can also not be accessed from the south/west, because
        // it is a one-way. This creates a subnetwork that is not accessible at all. We can only detect this if we
        // consider the turn restrictions during the subnetwork search.
        GraphHopper hopper = new GraphHopper().
                setGraphHopperLocation(GH_LOCATION).
                setOSMFile(ESSEN).
                setMinNetworkSize(50).
                setProfiles(
                        new Profile("foot").setVehicle("foot").setWeighting("fastest"),
                        new Profile("car").setVehicle("car").setWeighting("fastest").setTurnCosts(true)
                );

        hopper.importOrLoad();
        GHPoint p = new GHPoint(51.433417, 7.009395);
        GHPoint q = new GHPoint(51.432872, 7.010066);
        GHRequest req = new GHRequest(p, q);
        // using the foot profile we do not care about the turn restriction
        req.setProfile("foot");
        GHResponse rsp = hopper.route(req);
        assertFalse(rsp.hasErrors(), rsp.getErrors().toString());
        assertEquals(95, rsp.getBest().getDistance(), 1);

        // Using the car profile there is no way we can reach the destination and the subnetwork is supposed to be removed
        // such that the destination snaps to a point that can be reached.
        req.setProfile("car");
        rsp = hopper.route(req);
        assertFalse(rsp.hasErrors(), rsp.getErrors().toString());
        assertEquals(658, rsp.getBest().getDistance(), 1);
    }

    @Test
    public void testEdgeCount() {
        GraphHopper hopper = new GraphHopper().
                setGraphHopperLocation(GH_LOCATION).
                setOSMFile(BAYREUTH).
                setMinNetworkSize(50).
                setProfiles(new Profile("car").setVehicle("car").setWeighting("fastest"));
        hopper.importOrLoad();
        int count = 0;
        AllEdgesIterator iter = hopper.getBaseGraph().getAllEdges();
        while (iter.next())
            count++;
        assertEquals(hopper.getBaseGraph().getEdges(), count);
    }

    @Test
    public void testCurbsides() {
        GraphHopper h = new GraphHopper().
                setGraphHopperLocation(GH_LOCATION).
                setOSMFile(BAYREUTH).
                setProfiles(new Profile("my_profile").setVehicle("car").setWeighting("fastest").setTurnCosts(true));
        h.getCHPreparationHandler()
                .setCHProfiles(new CHProfile("my_profile"));
        h.importOrLoad();

        // depending on the curbside parameters we take very different routes
        GHPoint p = new GHPoint(50.015072, 11.499145);
        GHPoint q = new GHPoint(50.014141, 11.497552);
        final String itz = "Itzgrund";
        final String rotmain = "An den Rotmainauen";
        final String bayreuth = "Bayreuther Straße";
        final String kulmbach = "Kulmbacher Straße";
        final String adamSeiler = "Adam-Seiler-Straße";
        final String friedhof = "Friedhofsweg";
        assertCurbsidesPath(h, p, q, asList(CURBSIDE_RIGHT, CURBSIDE_RIGHT), 344, asList(itz, rotmain, rotmain));
        assertCurbsidesPath(h, p, q, asList(CURBSIDE_RIGHT, CURBSIDE_LEFT), 1564, asList(itz, rotmain, rotmain, bayreuth, adamSeiler, adamSeiler, friedhof, kulmbach, rotmain));
        assertCurbsidesPath(h, p, q, asList(CURBSIDE_LEFT, CURBSIDE_RIGHT), 1199, asList(itz, bayreuth, adamSeiler, adamSeiler, friedhof, kulmbach, itz, rotmain, rotmain));
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
        assertCurbsidesPath(h, p, p, asList(CURBSIDE_RIGHT, CURBSIDE_LEFT), 1908, asList(itz, rotmain, rotmain, bayreuth, adamSeiler, adamSeiler, friedhof, kulmbach, rotmain, rotmain, itz));
        assertCurbsidesPath(h, p, p, asList(CURBSIDE_LEFT, CURBSIDE_RIGHT), 855, asList(itz, bayreuth, adamSeiler, adamSeiler, friedhof, kulmbach, itz));
    }

    @Test
    public void testForceCurbsides() {
        final String profile = "my_profile";
        final String vehicle = "car";
        final String weighting = "fastest";
        GraphHopper h = new GraphHopper().
                setGraphHopperLocation(GH_LOCATION).
                setOSMFile(MONACO).
                setProfiles(new Profile(profile).setVehicle(vehicle).setWeighting(weighting).setTurnCosts(true));
        h.getCHPreparationHandler()
                .setCHProfiles(new CHProfile(profile));
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

    private void assertCurbsidesPath(GraphHopper hopper, GHPoint source, GHPoint target, List<String> curbsides, int expectedDistance, List<String> expectedStreets) {
        assertCurbsidesPath(hopper, source, target, curbsides, expectedDistance, expectedStreets, true);
    }

    private void assertCurbsidesPath(GraphHopper hopper, GHPoint source, GHPoint target, List<String> curbsides, int expectedDistance, List<String> expectedStreets, boolean force) {
        GHResponse rsp = calcCurbsidePath(hopper, source, target, curbsides, force);
        assertFalse(rsp.hasErrors(), rsp.getErrors().toString());
        ResponsePath path = rsp.getBest();
        List<String> streets = new ArrayList<>(path.getInstructions().size());
        for (Instruction instruction : path.getInstructions()) {
            if (!Helper.isEmpty(instruction.getName())) {
                streets.add(instruction.getName());
            }
        }
        assertEquals(expectedStreets, streets);
        assertEquals(expectedDistance, path.getDistance(), 1);
    }

    private void assertCurbsidesPathError(GraphHopper hopper, GHPoint source, GHPoint target, List<String> curbsides, String errorMessage, boolean force) {
        GHResponse rsp = calcCurbsidePath(hopper, source, target, curbsides, force);
        assertTrue(rsp.hasErrors());
        assertTrue(rsp.getErrors().toString().contains(errorMessage), "unexpected error. expected message containing: " + errorMessage + ", but got: " +
                rsp.getErrors());
    }

    private GHResponse calcCurbsidePath(GraphHopper hopper, GHPoint source, GHPoint target, List<String> curbsides, boolean force) {
        GHRequest req = new GHRequest(source, target);
        req.putHint(Routing.FORCE_CURBSIDE, force);
        req.setProfile("my_profile");
        req.setCurbsides(curbsides);
        return hopper.route(req);
    }

    @Test
    public void testCHWithFiniteUTurnCosts() {
        GraphHopper h = new GraphHopper().
                setGraphHopperLocation(GH_LOCATION).
                setOSMFile(MONACO).
                setProfiles(new Profile("my_profile").setVehicle("car").setWeighting("fastest").
                        setTurnCosts(true).putHint(U_TURN_COSTS, 40));
        h.getCHPreparationHandler()
                .setCHProfiles(new CHProfile("my_profile"));
        h.importOrLoad();

        GHPoint p = new GHPoint(43.73397, 7.414173);
        GHPoint q = new GHPoint(43.73222, 7.415557);
        GHRequest req = new GHRequest(p, q);
        req.setProfile("my_profile");
        // we force the start/target directions such that there are u-turns right after we start and right before
        // we reach the target. at the start location we do a u-turn at the crossing with the *steps* ('ghost junction')
        req.setCurbsides(Arrays.asList("right", "right"));
        GHResponse res = h.route(req);
        assertFalse(res.hasErrors(), "routing should not fail");
        assertEquals(242.9, res.getBest().getRouteWeight(), 0.1);
        assertEquals(1917, res.getBest().getDistance(), 1);
        assertEquals(243000, res.getBest().getTime(), 1000);
    }

    @Test
    public void simplifyWithInstructionsAndPathDetails() {
        final String profile = "profile";
        GraphHopper hopper = new GraphHopper();
        hopper.setOSMFile(BAYREUTH).
                setProfiles(new Profile(profile).setVehicle("car").setWeighting("fastest")).
                setGraphHopperLocation(GH_LOCATION);
        hopper.importOrLoad();

        GHRequest req = new GHRequest()
                .addPoint(new GHPoint(50.026932, 11.493201))
                .addPoint(new GHPoint(50.016895, 11.4923))
                .addPoint(new GHPoint(50.003464, 11.49157))
                .setProfile(profile)
                .setPathDetails(Arrays.asList(EdgeKVStorage.KeyValue.STREET_REF, "max_speed"));
        req.putHint("elevation", true);

        GHResponse rsp = hopper.route(req);
        assertFalse(rsp.hasErrors(), rsp.getErrors().toString());

        ResponsePath path = rsp.getBest();

        // check path was simplified (without it would be more like 58)
        assertEquals(41, path.getPoints().size());

        // check instructions
        InstructionList instructions = path.getInstructions();
        int totalLength = 0;
        for (Instruction instruction : instructions) {
            totalLength += instruction.getLength();
        }
        assertEquals(40, totalLength);
        assertInstruction(instructions.get(0), "KU 11", "[0, 4[", 4, 4);
        assertInstruction(instructions.get(1), "B 85", "[4, 16[", 12, 12);
        // via instructions have length = 0, but the point list must not be empty!
        assertInstruction(instructions.get(2), null, "[16, 17[", 0, 1);
        assertInstruction(instructions.get(3), "B 85", "[16, 32[", 16, 16);
        assertInstruction(instructions.get(4), null, "[32, 34[", 2, 2);
        assertInstruction(instructions.get(5), "KU 18", "[34, 37[", 3, 3);
        assertInstruction(instructions.get(6), "St 2189", "[37, 38[", 1, 1);
        assertInstruction(instructions.get(7), null, "[38, 40[", 2, 2);
        // finish instructions have length = 0, but the point list must not be empty!
        assertInstruction(instructions.get(8), null, "[40, 41[", 0, 1);

        // check max speeds
        List<PathDetail> speeds = path.getPathDetails().get("max_speed");
        assertDetail(speeds.get(0), "null [0, 4]");
        assertDetail(speeds.get(1), "70.0 [4, 6]");
        assertDetail(speeds.get(2), "100.0 [6, 16]");
        assertDetail(speeds.get(3), "100.0 [16, 31]"); // we do not merge path details at via points
        assertDetail(speeds.get(4), "80.0 [31, 32]");
        assertDetail(speeds.get(5), "null [32, 37]");
        assertDetail(speeds.get(6), "50.0 [37, 38]");
        assertDetail(speeds.get(7), "null [38, 40]");

        // check street names
        List<PathDetail> streetNames = path.getPathDetails().get(EdgeKVStorage.KeyValue.STREET_REF);
        assertDetail(streetNames.get(0), "KU 11 [0, 4]");
        assertDetail(streetNames.get(1), "B 85 [4, 16]");
        assertDetail(streetNames.get(2), "B 85 [16, 32]");
        assertDetail(streetNames.get(3), "null [32, 34]");
        assertDetail(streetNames.get(4), "KU 18 [34, 37]");
        assertDetail(streetNames.get(5), "St 2189 [37, 38]");
        assertDetail(streetNames.get(6), "null [38, 40]");
    }

    private void assertInstruction(Instruction instruction, String expectedRef, String expectedInterval, int expectedLength, int expectedPoints) {
        assertEquals(expectedRef, instruction.getExtraInfoJSON().get(EdgeKVStorage.KeyValue.STREET_REF));
        assertEquals(expectedInterval, ((ShallowImmutablePointList) instruction.getPoints()).getIntervalString());
        assertEquals(expectedLength, instruction.getLength());
        assertEquals(expectedPoints, instruction.getPoints().size());
    }

    private void assertDetail(PathDetail detail, String expected) {
        assertEquals(expected, detail.toString());
    }

    @ParameterizedTest
    @CsvSource(value = {"true,true", "true,false", "false,true", "false,false"})
    public void simplifyKeepsWaypoints(boolean elevation, boolean instructions) {
        GraphHopper h = new GraphHopper().
                setGraphHopperLocation(GH_LOCATION).
                setOSMFile(MONACO).
                setProfiles(new Profile("car").setVehicle("car").setWeighting("fastest"));
        if (elevation)
            h.setElevationProvider(new SRTMProvider(DIR));
        h.importOrLoad();

        List<GHPoint> reqPoints = asList(
                new GHPoint(43.741736, 7.428043),
                new GHPoint(43.741248, 7.4274),
                new GHPoint(43.73906, 7.426694),
                new GHPoint(43.736337, 7.420592),
                new GHPoint(43.735585, 7.419734),
                new GHPoint(43.734857, 7.41909),
                new GHPoint(43.73389, 7.418578),
                new GHPoint(43.733204, 7.418755),
                new GHPoint(43.731969, 7.416949)
        );
        GHRequest req = new GHRequest(reqPoints).setProfile("car");
        req.putHint("instructions", instructions);
        GHResponse res = h.route(req);
        assertFalse(res.hasErrors());
        assertEquals(elevation ? 1828 : 1793, res.getBest().getDistance(), 1);
        PointList points = res.getBest().getPoints();
        PointList wayPoints = res.getBest().getWaypoints();
        assertEquals(reqPoints.size(), wayPoints.size());
        assertEquals(points.is3D(), wayPoints.is3D());
        assertPointlistContainsSublist(points, wayPoints);
    }

    private static void assertPointlistContainsSublist(PointList pointList, PointList subList) {
        // we check if all points in sublist exist in pointlist, in the order given by sublist
        int j = 0;
        for (int i = 0; i < pointList.size(); i++)
            if (pointList.getLat(i) == subList.getLat(j) && pointList.getLon(i) == subList.getLon(j) && (!pointList.is3D() || pointList.getEle(i) == subList.getEle(j)))
                j++;
        if (j != subList.size())
            fail("point list does not contain point " + j + " of sublist: " + subList.get(j) +
                    "\npoint list: " + pointList +
                    "\nsublist   : " + subList);
    }

    @Test
    public void testNoLoad() {
        String profile = "profile";
        String vehicle = "car";
        String weighting = "fastest";
        final GraphHopper hopper = new GraphHopper().
                setProfiles(new Profile(profile).setVehicle(vehicle).setWeighting(weighting));
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> hopper.route(new GHRequest(42, 10.4, 42, 10).setProfile(profile)));
        assertTrue(e.getMessage().startsWith("Do a successful call to load or importOrLoad before routing"), e.getMessage());
    }

    @Test
    public void connectionNotFound() {
        final String profile = "profile";
        final String vehicle = "car";
        final String weighting = "fastest";
        GraphHopper hopper = new GraphHopper().
                setGraphHopperLocation(GH_LOCATION).
                setOSMFile(BAYREUTH).
                setProfiles(new Profile("profile").setVehicle(vehicle).setWeighting(weighting)).
                setStoreOnFlush(true);
        hopper.getCHPreparationHandler()
                .setCHProfiles(new CHProfile("profile"));
        hopper.setMinNetworkSize(0);
        hopper.importOrLoad();
        // here from and to both snap to small subnetworks that are disconnected from the main graph and
        // since we set min network size to 0 we expect a connection not found error
        GHPoint from = new GHPoint(49.97964, 11.539593);
        GHPoint to = new GHPoint(50.029247, 11.582851);
        GHRequest req = new GHRequest(from, to).setProfile(profile);
        GHResponse res = hopper.route(req);
        assertEquals("[com.graphhopper.util.exceptions.ConnectionNotFoundException: Connection between locations not found]",
                res.getErrors().toString());
    }

    @Test
    public void testDoNotInterpolateTwice1645() {
        final AtomicInteger counter = new AtomicInteger(0);
        {
            GraphHopper hopper = new GraphHopper() {
                @Override
                void interpolateBridgesTunnelsAndFerries() {
                    counter.incrementAndGet();
                    super.interpolateBridgesTunnelsAndFerries();
                }
            }.
                    setGraphHopperLocation(GH_LOCATION).
                    setOSMFile(BAYREUTH).
                    setProfiles(new Profile("profile")).
                    setElevation(true).
                    setStoreOnFlush(true);
            hopper.importOrLoad();
            hopper.flush();
            hopper.close();
        }
        assertEquals(1, counter.get());
        {
            GraphHopper hopper = new GraphHopper() {
                @Override
                void interpolateBridgesTunnelsAndFerries() {
                    counter.incrementAndGet();
                    super.interpolateBridgesTunnelsAndFerries();
                }
            }.
                    setProfiles(new Profile("profile")).
                    setElevation(true).
                    setGraphHopperLocation(GH_LOCATION);
            hopper.load();
        }
        assertEquals(1, counter.get());
    }

    @Test
    public void issue2306_1() {
        final String profile = "profile";
        final String vehicle = "car";
        GraphHopper hopper = new GraphHopper().
                setGraphHopperLocation(GH_LOCATION).
                setOSMFile("../map-matching/files/leipzig_germany.osm.pbf").
                setProfiles(new Profile("profile").setVehicle(vehicle).setWeighting("fastest")).
                setMinNetworkSize(200);
        hopper.importOrLoad();
        Weighting weighting = hopper.createWeighting(hopper.getProfile(profile), new PMap());
        EdgeFilter edgeFilter = new DefaultSnapFilter(weighting, hopper.getEncodingManager().getBooleanEncodedValue(Subnetwork.key(profile)));
        LocationIndexTree locationIndex = ((LocationIndexTree) hopper.getLocationIndex());
        locationIndex.setMaxRegionSearch(6); // have to increase the default search radius to find our snap
        Snap snap = locationIndex.findClosest(51.229248, 12.328892, edgeFilter);
        assertTrue(snap.isValid());
        assertTrue(snap.getQueryDistance() < 3_000);
    }

    @Test
    public void issue2306_2() {
        // This is the same test as above, but without increasing the search radius.
        // As I am writing this, we find _no_ match here. But since the search radius
        // is a meta-parameter that could go away at some point, I say that _if_ we find a match,
        // it should be a close one. (And not a far away one, as happened in issue2306.)
        final String profile = "profile";
        final String vehicle = "car";
        GraphHopper hopper = new GraphHopper().
                setGraphHopperLocation(GH_LOCATION).
                setOSMFile("../map-matching/files/leipzig_germany.osm.pbf").
                setProfiles(new Profile("profile").setVehicle(vehicle).setWeighting("fastest")).
                setMinNetworkSize(200);
        hopper.importOrLoad();
        Weighting weighting = hopper.createWeighting(hopper.getProfile(profile), new PMap());
        EdgeFilter edgeFilter = new DefaultSnapFilter(weighting, hopper.getEncodingManager().getBooleanEncodedValue(Subnetwork.key(profile)));
        Snap snap = hopper.getLocationIndex().findClosest(51.229248, 12.328892, edgeFilter);
        if (snap.isValid()) {
            assertTrue(snap.getQueryDistance() < 3_000);
        }
    }

    @Test
    public void testBarriers() {
        GraphHopper hopper = new GraphHopper().
                setGraphHopperLocation(GH_LOCATION).
                setOSMFile("../map-matching/files/leipzig_germany.osm.pbf").
                setProfiles(
                        new Profile("car").setVehicle("car").setWeighting("fastest"),
                        new Profile("bike").setVehicle("bike").setWeighting("fastest"),
                        new Profile("foot").setVehicle("foot").setWeighting("fastest")
                ).
                setMinNetworkSize(0);
        hopper.importOrLoad();

        {
            // the bollard blocks the road for bikes, and we need to take a big detour. note that this bollard connects
            // two ways
            GHResponse bikeRsp = hopper.route(new GHRequest(51.257709, 12.309269, 51.257594, 12.308882).setProfile("bike"));
            assertEquals(1185, bikeRsp.getBest().getDistance(), 1);
            // pedestrians can just pass the bollard
            GHResponse footRsp = hopper.route(new GHRequest(51.257709, 12.309269, 51.257594, 12.308882).setProfile("foot"));
            assertEquals(28, footRsp.getBest().getDistance(), 1);
        }

        {
            // here the bollard blocks the road for cars
            GHResponse carRsp = hopper.route(new GHRequest(51.301113, 12.432168, 51.30123, 12.431728).setProfile("car"));
            assertEquals(368, carRsp.getBest().getDistance(), 1);
            // ... but not for bikes
            GHResponse bikeRsp = hopper.route(new GHRequest(51.301113, 12.432168, 51.30123, 12.431728).setProfile("bike"));
            assertEquals(48, bikeRsp.getBest().getDistance(), 1);
        }

        {
            // cars need to take a detour to the south (on newer maps an even bigger detour going north is necessary)
            GHResponse carRsp = hopper.route(new GHRequest(51.350105, 12.289968, 51.350246, 12.287779).setProfile("car"));
            assertEquals(285, carRsp.getBest().getDistance(), 1);
            // ... bikes can just pass the bollard
            GHResponse bikeRsp = hopper.route(new GHRequest(51.350105, 12.289968, 51.350246, 12.287779).setProfile("bike"));
            assertEquals(152, bikeRsp.getBest().getDistance(), 1);
        }

        {
            // these are bollards that are located right on a junction. this should never happen according to OSM mapping
            // rules, but it still does. the problem with such barriers is that we can only block one direction and it
            // is unclear which one is right. therefore we simply ignore such barriers.

            // here the barrier node actually disconnected a dead-end road that should rather be connected before we
            // started ignoring barriers at junctions.
            GHResponse carRsp = hopper.route(new GHRequest(51.327121, 12.572396, 51.327173, 12.574038).setProfile("car"));
            assertEquals(124, carRsp.getBest().getDistance(), 1);
            GHResponse bikeRsp = hopper.route(new GHRequest(51.327121, 12.572396, 51.327173, 12.574038).setProfile("bike"));
            assertEquals(124, bikeRsp.getBest().getDistance(), 1);

            // Here the barrier could prevent us from travelling straight along Pufendorfstraße. But it could also
            // prevent us from turning from Pufendorfstraße onto 'An der Streuobstwiese' (or vice versa). What should
            // be allowed depends on whether the barrier is before or behind the junction. And since we can't tell
            // we just ignore this barrier. Note that the mapping was fixed in newer OSM versions, so the barrier is no
            // longer at the junction
            carRsp = hopper.route(new GHRequest(51.344134, 12.317986, 51.344231, 12.317482).setProfile("car"));
            assertEquals(36, carRsp.getBest().getDistance(), 1);
            bikeRsp = hopper.route(new GHRequest(51.344134, 12.317986, 51.344231, 12.317482).setProfile("bike"));
            assertEquals(36, bikeRsp.getBest().getDistance(), 1);

            // Here we'd have to go all the way around, but the bollard node could also mean that continuing on Adenauerallee
            // is fine, and we just cannot enter the little path. Since we cannot tell we just ignore this barrier.
            carRsp = hopper.route(new GHRequest(51.355455, 12.40202, 51.355318, 12.401741).setProfile("car"));
            assertEquals(24, carRsp.getBest().getDistance(), 1);
            bikeRsp = hopper.route(new GHRequest(51.355455, 12.40202, 51.355318, 12.401741).setProfile("bike"));
            assertEquals(24, bikeRsp.getBest().getDistance(), 1);
        }
    }

    @Test
    public void germanyCountryRuleAvoidsTracks() {
        final String profile = "profile";

        // first we try without country rules (the default)
        GraphHopper hopper = new GraphHopper()
                .setProfiles(new Profile(profile).setVehicle("car").setWeighting("fastest"))
                .setCountryRuleFactory(null)
                .setGraphHopperLocation(GH_LOCATION)
                .setOSMFile(BAYREUTH);
        hopper.importOrLoad();
        GHRequest request = new GHRequest(50.010373, 11.51792, 50.005146, 11.516633);
        request.setProfile(profile);
        GHResponse response = hopper.route(request);
        assertFalse(response.hasErrors());
        double distance = response.getBest().getDistance();
        // The route takes a shortcut through the forest
        assertEquals(1447, distance, 1);

        // this time we enable country rules
        hopper.clean();
        hopper = new GraphHopper()
                .setProfiles(new Profile(profile).setVehicle("car").setWeighting("fastest"))
                .setGraphHopperLocation(GH_LOCATION)
                .setCountryRuleFactory(new CountryRuleFactory())
                .setOSMFile(BAYREUTH);
        hopper.importOrLoad();
        request = new GHRequest(50.010373, 11.51792, 50.005146, 11.516633);
        request.setProfile(profile);
        response = hopper.route(request);
        assertFalse(response.hasErrors());
        distance = response.getBest().getDistance();
        // since GermanyCountryRule avoids TRACK roads the route will now be much longer as it goes around the forest
        assertEquals(4186, distance, 1);
    }

    @Test
    void curbsideWithSubnetwork_issue2502() {
        final String profile = "profile";
        GraphHopper hopper = new GraphHopper()
                .setProfiles(new Profile(profile).setVehicle("car").setWeighting("fastest").setTurnCosts(true)
                        .setTurnCosts(true).putHint(U_TURN_COSTS, 80))
                .setGraphHopperLocation(GH_LOCATION)
                .setMinNetworkSize(200)
                .setOSMFile(DIR + "/one_way_dead_end.osm.pbf");
        hopper.importOrLoad();
        GHPoint pointA = new GHPoint(28.77428, -81.61593);
        GHPoint pointB = new GHPoint(28.773038, -81.611595);
        {
            // A->B
            GHRequest request = new GHRequest(pointA, pointB);
            request.setProfile(profile);
            request.setCurbsides(Arrays.asList("right", "right"));
            GHResponse response = hopper.route(request);
            assertFalse(response.hasErrors(), response.getErrors().toString());
            double distance = response.getBest().getDistance();
            assertEquals(382, distance, 1);
        }
        {
            // B->A
            // point B is close to a tiny one-way dead end street. it should be marked as subnetwork and excluded
            // when the curbside constraints are evaluated. this should make the snap a tower snap such that the curbside
            // constraint won't result in a connection not found error
            GHRequest request = new GHRequest(pointB, pointA);
            request.setProfile(profile);
            request.setCurbsides(Arrays.asList("right", "right"));
            GHResponse response = hopper.route(request);
            assertFalse(response.hasErrors(), response.getErrors().toString());
            double distance = response.getBest().getDistance();
            assertEquals(2318, distance, 1);
        }
    }

    @Test
    void averageSpeedPathDetailBug() {
        final String profile = "profile";
        GraphHopper hopper = new GraphHopper()
                .setProfiles(new Profile(profile).setVehicle("car").setWeighting("fastest").setTurnCosts(true).putHint(U_TURN_COSTS, 80))
                .setGraphHopperLocation(GH_LOCATION)
                .setMinNetworkSize(200)
                .setOSMFile(BAYREUTH);
        hopper.importOrLoad();
        GHPoint pointA = new GHPoint(50.020562, 11.500196);
        GHPoint pointB = new GHPoint(50.019935, 11.500567);
        GHPoint pointC = new GHPoint(50.022027, 11.498255);
        GHRequest request = new GHRequest(Arrays.asList(pointA, pointB, pointC));
        request.setProfile(profile);
        request.setPathDetails(Collections.singletonList("average_speed"));
        // this used to fail, because we did not wrap the weighting for query graph and so we tried calculating turn costs for virtual nodes
        GHResponse response = hopper.route(request);
        assertFalse(response.hasErrors(), response.getErrors().toString());
        double distance = response.getBest().getDistance();
        assertEquals(467, distance, 1);
    }

    @Test
    void timeDetailBug() {
        final String profile = "profile";
        GraphHopper hopper = new GraphHopper()
                .setProfiles(new Profile(profile).setVehicle("car").setWeighting("fastest").setTurnCosts(true).putHint(U_TURN_COSTS, 80))
                .setGraphHopperLocation(GH_LOCATION)
                .setMinNetworkSize(200)
                .setOSMFile(BAYREUTH);
        hopper.importOrLoad();
        GHRequest request = new GHRequest(Arrays.asList(
                new GHPoint(50.020838, 11.494918),
                new GHPoint(50.024795, 11.498973),
                new GHPoint(50.023141, 11.496441)));
        request.setProfile(profile);
        request.getHints().putObject("instructions", true);
        request.setPathDetails(Arrays.asList("distance", "time"));
        GHResponse response = hopper.route(request);
        assertFalse(response.hasErrors(), response.getErrors().toString());

        consistenceCheck(response.getBest());
    }

    private void consistenceCheck(ResponsePath path) {
        double distance = path.getDistance();
        long time = path.getTime();

        double instructionDistance = 0;
        long instructionTime = 0;
        for (Instruction i : path.getInstructions()) {
            instructionDistance += i.getDistance();
            instructionTime += i.getTime();
        }

        assertEquals(time, instructionTime);
        assertEquals(distance, instructionDistance, 1e-3);

        double pathDetailDistance = 0;
        for (PathDetail pd : path.getPathDetails().get("distance")) {
            pathDetailDistance += (Double) pd.getValue();
        }
        assertEquals(distance, pathDetailDistance, 1e-3);

        long pathDetailTime = 0;
        for (PathDetail pd : path.getPathDetails().get("time")) {
            pathDetailTime += (Long) pd.getValue();
        }
        assertEquals(time, pathDetailTime);
    }

    @Test
    public void testLoadGraph_implicitEncodedValues_issue1862() {
        GraphHopper hopper = new GraphHopper()
                .setProfiles(
                        new Profile("p_car").setVehicle("car").setWeighting("fastest"),
                        new Profile("p_bike").setVehicle("bike").setWeighting("fastest")
                )
                .setGraphHopperLocation(GH_LOCATION)
                .setOSMFile(BAYREUTH);
        hopper.importOrLoad();
        int nodes = hopper.getBaseGraph().getNodes();
        hopper.close();

        // load without configured graph.vehicles
        hopper = new GraphHopper();
        hopper.setProfiles(Arrays.asList(
                new Profile("p_car").setVehicle("car").setWeighting("fastest"),
                new Profile("p_bike").setVehicle("bike").setWeighting("fastest"))
        );
        hopper.setGraphHopperLocation(GH_LOCATION);
        assertTrue(hopper.load());
        hopper.getBaseGraph();
        assertEquals(nodes, hopper.getBaseGraph().getNodes());
        hopper.close();

        // load via explicitly configured graph.vehicles
        hopper = new GraphHopper();
        hopper.setVehiclesString("car,bike");
        hopper.setProfiles(Arrays.asList(
                new Profile("p_car").setVehicle("car").setWeighting("fastest"),
                new Profile("p_bike").setVehicle("bike").setWeighting("fastest"))
        );
        hopper.setGraphHopperLocation(GH_LOCATION);
        assertTrue(hopper.load());
        assertEquals(nodes, hopper.getBaseGraph().getNodes());
        hopper.close();
    }

    @Test
    void testLoadingWithAnotherSpeedFactorWorks() {
        {
            GraphHopper hopper = new GraphHopper()
                    .setVehiclesString("car|speed_factor=7")
                    .setProfiles(new Profile("car").setVehicle("car").setWeighting("fastest"))
                    .setGraphHopperLocation(GH_LOCATION)
                    .setOSMFile(BAYREUTH);
            hopper.importOrLoad();
        }
        {
            // now we use another speed_factor, but changing the flag encoder string has no effect when we are loading
            // a graph. This API is a bit confusing, but we have been mixing configuration options that only matter
            // during import with those that only matter when routing for some time already. At some point we should
            // separate the 'import' from the 'routing' config (and split the GraphHopper class).
            GraphHopper hopper = new GraphHopper()
                    .setVehiclesString("car|speed_factor=9")
                    .setProfiles(new Profile("car").setVehicle("car").setWeighting("fastest"))
                    .setGraphHopperLocation(GH_LOCATION);
            hopper.load();
            assertEquals(2969, hopper.getBaseGraph().getNodes());
        }
    }

}

