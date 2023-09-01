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
package com.graphhopper.routing;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.ResponsePath;
import com.graphhopper.config.CHProfile;
import com.graphhopper.config.LMProfile;
import com.graphhopper.config.Profile;
import com.graphhopper.jackson.Jackson;
import com.graphhopper.reader.dem.SRTMProvider;
import com.graphhopper.routing.weighting.custom.CustomProfile;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.*;
import com.graphhopper.util.shapes.GHPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static com.graphhopper.json.Statement.If;
import static com.graphhopper.json.Statement.Op.MULTIPLY;
import static com.graphhopper.util.Parameters.Algorithms.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Here we check the routes calculated by GraphHopper for different routing algorithms on real OSM data
 */
public class RoutingAlgorithmWithOSMTest {

    private static final String DIR = "../core/files";

    private static final String ANDORRA = DIR + "/andorra.osm.gz";
    private static final String ANDORRA_PBF = DIR + "/andorra.osm.pbf";
    private static final String BAYREUTH = DIR + "/north-bayreuth.osm.gz";
    private static final String HOHEWARTE = DIR + "/hohe-warte.osm.gz";
    private static final String KREMS = DIR + "/krems.osm.gz";
    private static final String MONACO = DIR + "/monaco.osm.gz";
    private static final String MOSCOW = DIR + "/moscow.osm.gz";

    // when creating GH instances make sure to use this as the GH location such that it will be cleaned between tests
    private static final String GH_LOCATION = "target/routing-algorithm-with-osm-test-gh";
    private final DistanceCalc distCalc = DistanceCalcEarth.DIST_EARTH;

    @BeforeEach
    @AfterEach
    public void setup() {
        Helper.removeDir(new File(GH_LOCATION));
    }

    @Test
    public void testMonaco() {
        GraphHopper hopper = createHopper(MONACO, new CustomProfile("car").setCustomModel(new CustomModel().setDistanceInfluence(10_000d)).setVehicle("car"));
        hopper.importOrLoad();
        checkQueries(hopper, createMonacoCarQueries());
        Graph g = hopper.getBaseGraph();

        // When OSM file stays unchanged make static edge and node IDs a requirement
        assertEquals(GHUtility.asSet(924, 576, 2), GHUtility.getNeighbors(g.createEdgeExplorer().setBaseNode(10)));
        assertEquals(GHUtility.asSet(291, 369, 19), GHUtility.getNeighbors(g.createEdgeExplorer().setBaseNode(20)));
        assertEquals(GHUtility.asSet(45, 497, 488), GHUtility.getNeighbors(g.createEdgeExplorer().setBaseNode(480)));

        assertEquals(43.738776, g.getNodeAccess().getLat(10), 1e-6);
        assertEquals(7.4170402, g.getNodeAccess().getLon(201), 1e-6);
    }

    private List<Query> createMonacoCarQueries() {
        List<Query> queries = new ArrayList<>();
        queries.add(new Query(43.730729, 7.42135, 43.727697, 7.419199, 2580, 110));
        queries.add(new Query(43.727687, 7.418737, 43.74958, 7.436566, 3588, 170));
        queries.add(new Query(43.728677, 7.41016, 43.739213, 7.4277, 2561, 133));
        queries.add(new Query(43.733802, 7.413433, 43.739662, 7.424355, 2230, 137));
        queries.add(new Query(43.730949, 7.412338, 43.739643, 7.424542, 2100, 116));
        queries.add(new Query(43.727592, 7.419333, 43.727712, 7.419333, 0, 1));

        // same special cases where GPS-exact routing could have problems (same edge and neighbor edges)
        queries.add(new Query(43.727592, 7.419333, 43.727712, 7.41934, 0, 1));
        // on the same edge and very release
        queries.add(new Query(43.727592, 7.419333, 43.727712, 7.4193, 3, 2));
        // one way stuff
        queries.add(new Query(43.729445, 7.415063, 43.728856, 7.41472, 103, 4));
        queries.add(new Query(43.728856, 7.41472, 43.729445, 7.415063, 320, 11));
        return queries;
    }

    @Test
    public void testMonacoMotorcycleCurvature() {
        List<Query> queries = new ArrayList<>();
        queries.add(new Query(43.730729, 7.42135, 43.727697, 7.419199, 2675, 117));
        queries.add(new Query(43.727687, 7.418737, 43.74958, 7.436566, 3730, 173));
        queries.add(new Query(43.728677, 7.41016, 43.739213, 7.4277, 2769, 167));
        queries.add(new Query(43.733802, 7.413433, 43.739662, 7.424355, 2373, 137));
        queries.add(new Query(43.730949, 7.412338, 43.739643, 7.424542, 2203, 116));
        queries.add(new Query(43.727592, 7.419333, 43.727712, 7.419333, 0, 1));
        GraphHopper hopper = createHopper(MONACO, new CustomProfile("car").setCustomModel(
                CustomModel.merge(getCustomModel("motorcycle.json"), getCustomModel("curvature.json"))).setVehicle("roads"));
        hopper.setVehiclesString("car,roads");
        hopper.setEncodedValuesString("curvature,track_type,surface");
        hopper.setElevationProvider(new SRTMProvider(DIR));
        hopper.importOrLoad();
        checkQueries(hopper, queries);
    }

    @Test
    public void testBike2_issue432() {
        List<Query> queries = new ArrayList<>();
        queries.add(new Query(52.349969, 8.013813, 52.349713, 8.013293, 56, 7));
        // reverse route avoids the location
//        list.add(new OneRun(52.349713, 8.013293, 52.349969, 8.013813, 293, 21));
        GraphHopper hopper = createHopper(DIR + "/map-bug432.osm.gz",
                new CustomProfile("bike2").setVehicle("bike"));
        hopper.setElevationProvider(new SRTMProvider(DIR));
        hopper.importOrLoad();
        checkQueries(hopper, queries);
    }

    @Test
    public void testOneWayCircleBug() {
        // export from http://www.openstreetmap.org/export#map=19/51.37605/-0.53155
        List<Query> queries = new ArrayList<>();
        // going the bit longer way out of the circle
        queries.add(new Query(51.376197, -0.531576, 51.376509, -0.530863, 153, 18));
        // now exacle the opposite direction: going into the circle (shorter)
        queries.add(new Query(51.376509, -0.530863, 51.376197, -0.531576, 75, 15));

        GraphHopper hopper = createHopper(DIR + "/circle-bug.osm.gz",
                new CustomProfile("car").setVehicle("car"));
        hopper.importOrLoad();
        checkQueries(hopper, queries);
    }

    @Test
    public void testMoscow() {
        // extracted from OSM area: "37.582641,55.805261,37.626929,55.824455"
        List<Query> queries = new ArrayList<>();
        // choose perpendicular
        // http://localhost:8989/?point=55.818994%2C37.595354&point=55.819175%2C37.596931
        queries.add(new Query(55.818891, 37.59515, 55.81997, 37.59854, 1052, 14));
        // should choose the closest road not the other one (opposite direction)
        // http://localhost:8989/?point=55.818898%2C37.59661&point=55.819066%2C37.596374
        queries.add(new Query(55.818536, 37.595848, 55.818702, 37.595564, 24, 2));
        // respect one way!
        // http://localhost:8989/?point=55.819066%2C37.596374&point=55.818898%2C37.59661
        queries.add(new Query(55.818702, 37.595564, 55.818536, 37.595848, 1114, 23));
        GraphHopper hopper = createHopper(MOSCOW, new CustomProfile("car").setVehicle("car"));
        hopper.setMinNetworkSize(200);
        hopper.importOrLoad();
        checkQueries(hopper, queries);
    }

    @Test
    public void testMoscowTurnCosts() {
        List<Query> queries = new ArrayList<>();
        queries.add(new Query(55.813357, 37.5958585, 55.811042, 37.594689, 1043.99, 12));
        queries.add(new Query(55.813159, 37.593884, 55.811278, 37.594217, 1048, 13));
        GraphHopper hopper = createHopper(MOSCOW,
                new CustomProfile("car").setVehicle("car").setTurnCosts(true));
        hopper.setMinNetworkSize(200);
        hopper.importOrLoad();
        checkQueries(hopper, queries);
    }

    @Test
    public void testSimpleTurnCosts() {
        List<Query> list = new ArrayList<>();
        list.add(new Query(-0.49, 0.0, 0.0, -0.49, 298792.107, 6));
        GraphHopper hopper = createHopper(DIR + "/test_simple_turncosts.osm.xml",
                new CustomProfile("car").setVehicle("car").setTurnCosts(true));
        hopper.importOrLoad();
        checkQueries(hopper, list);
    }

    @Test
    public void testSimplePTurn() {
        List<Query> list = new ArrayList<>();
        list.add(new Query(0, 0.00099, -0.00099, 0, 664, 6));
        GraphHopper hopper = createHopper(DIR + "/test_simple_pturn.osm.xml",
                new CustomProfile("car").setVehicle("car").setTurnCosts(true));
        hopper.importOrLoad();
        checkQueries(hopper, list);
    }

    static CustomModel getCustomModel(String file) {
        try {
            String string = Helper.readJSONFileWithoutComments(new InputStreamReader(GHUtility.class.getResourceAsStream("/com/graphhopper/custom_models/" + file)));
            return Jackson.newObjectMapper().readValue(string, CustomModel.class);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    @Test
    public void testSidewalkNo() {
        List<Query> queries = new ArrayList<>();
        // roundabout contains sidewalk=no which should be avoided
        queries.add(new Query(57.154888, -2.101822, 57.153445, -2.099869, 329, 31));
        // longer path should go through tertiary, see discussion in #476
        queries.add(new Query(57.154888, -2.101822, 57.147299, -2.096286, 1118, 68));

        Profile profile = new CustomProfile("hike").setVehicle("foot");
        GraphHopper hopper = createHopper(DIR + "/map-sidewalk-no.osm.gz", profile);
        hopper.importOrLoad();
        checkQueries(hopper, queries);
    }

    @Test
    public void testMonacoFastest() {
        List<Query> queries = createMonacoCarQueries();
        queries.get(0).getPoints().get(1).expectedDistance = 2584;
        queries.get(0).getPoints().get(1).expectedPoints = 117;
        queries.get(3).getPoints().get(1).expectedDistance = 2279;
        queries.get(3).getPoints().get(1).expectedPoints = 141;
        queries.get(4).getPoints().get(1).expectedDistance = 2149;
        queries.get(4).getPoints().get(1).expectedPoints = 120;
        GraphHopper hopper = createHopper(MONACO, new CustomProfile("car").setVehicle("car"));
        hopper.importOrLoad();
        checkQueries(hopper, queries);
    }

    @Test
    public void testMonacoMixed() {
        // Additional locations are inserted because of new crossings from foot to highway paths!
        // Distance is the same.
        List<Query> queries = createMonacoCarQueries();
        queries.get(0).getPoints().get(1).expectedPoints = 110;
        queries.get(1).getPoints().get(1).expectedPoints = 170;
        queries.get(2).getPoints().get(1).expectedPoints = 132;
        queries.get(3).getPoints().get(1).expectedPoints = 137;
        queries.get(4).getPoints().get(1).expectedPoints = 116;

        GraphHopper hopper = createHopper(MONACO,
                new CustomProfile("car").setCustomModel(new CustomModel().setDistanceInfluence(10_000d)).setVehicle("car"),
                new CustomProfile("foot").setCustomModel(new CustomModel().setDistanceInfluence(10_000d)).setVehicle("foot"));
        hopper.importOrLoad();
        checkQueries(hopper, queries);
    }

    @Test
    public void testMonacoFoot() {
        GraphHopper hopper = createHopper(MONACO, new CustomProfile("foot").setCustomModel(new CustomModel().setDistanceInfluence(10_000d)).setVehicle("foot"));
        hopper.importOrLoad();
        checkQueries(hopper, createMonacoFoot());
        Graph g = hopper.getBaseGraph();

        // see testMonaco for a similar ID test
        assertEquals(GHUtility.asSet(924, 576, 2), GHUtility.getNeighbors(g.createEdgeExplorer().setBaseNode(10)));
        assertEquals(GHUtility.asSet(440, 442), GHUtility.getNeighbors(g.createEdgeExplorer().setBaseNode(441)));
        assertEquals(GHUtility.asSet(913, 914, 911), GHUtility.getNeighbors(g.createEdgeExplorer().setBaseNode(912)));

        assertEquals(43.7467818, g.getNodeAccess().getLat(100), 1e-6);
        assertEquals(7.4312824, g.getNodeAccess().getLon(702), 1e-6);
    }

    @Test
    public void testMonacoFoot3D() {
        // most routes have same number of points as testMonaceFoot results but longer distance due to elevation difference
        List<Query> queries = createMonacoFoot();
        queries.get(0).getPoints().get(1).expectedDistance = 1627;
        queries.get(2).getPoints().get(1).expectedDistance = 2250;
        queries.get(3).getPoints().get(1).expectedDistance = 1482;

        // or slightly longer tour with less nodes: list.get(1).setDistance(1, 3610);
        queries.get(1).getPoints().get(1).expectedDistance = 3573;
        queries.get(1).getPoints().get(1).expectedPoints = 149;

        GraphHopper hopper = createHopper(MONACO, new CustomProfile("foot").setCustomModel(new CustomModel().setDistanceInfluence(10_000d)).setVehicle("foot"));
        hopper.setElevationProvider(new SRTMProvider(DIR));
        hopper.importOrLoad();
        checkQueries(hopper, queries);
    }

    private List<Query> createMonacoFoot() {
        List<Query> list = new ArrayList<>();
        list.add(new Query(43.730729, 7.421288, 43.727697, 7.419199, 1566, 92));
        list.add(new Query(43.727687, 7.418737, 43.74958, 7.436566, 3438, 136));
        list.add(new Query(43.728677, 7.41016, 43.739213, 7.427806, 2085, 112));
        list.add(new Query(43.733802, 7.413433, 43.739662, 7.424355, 1425, 89));
        return list;
    }

    @Test
    public void testNorthBayreuthHikeFastestAnd3D() {
        List<Query> queries = new ArrayList<>();
        // prefer hiking route 'Teufelsloch Unterwaiz' and 'Rotmain-Wanderweg'        
        queries.add(new Query(49.974972, 11.515657, 49.991022, 11.512299, 2365, 67));
        // prefer hiking route 'Markgrafenweg Bayreuth Kulmbach' but avoid tertiary highway from Pechgraben
        queries.add(new Query(49.990967, 11.545258, 50.023182, 11.555386, 5636, 97));
        GraphHopper hopper = createHopper(BAYREUTH, new CustomProfile("hike").setCustomModel(getCustomModel("hike.json")).setVehicle("roads"));
        hopper.setVehiclesString("roads,foot");
        hopper.setElevationProvider(new SRTMProvider(DIR));
        hopper.importOrLoad();
        checkQueries(hopper, queries);
    }

    @Test
    public void testHikeCanUseExtremeSacScales() {
        GraphHopper hopper = createHopper(HOHEWARTE, new CustomProfile("hike").setCustomModel(getCustomModel("hike.json")).setVehicle("roads"));
        hopper.setVehiclesString("foot,roads");
        // do not pull elevation data: hopper.setElevationProvider(new SRTMProvider(DIR));
        hopper.importOrLoad();
        GHResponse res = hopper.route(new GHRequest(47.290322, 11.333889, 47.301593, 11.333489).setProfile("hike"));
        assertEquals(3604, res.getBest().getTime() / 1000.0, 60); // 6100sec with srtm data
        assertEquals(2000, res.getBest().getDistance(), 10); // 2536m with srtm data
    }

    @Test
    public void testMonacoBike3D() {
        List<Query> queries = new ArrayList<>();
        // 1. alternative: go over steps 'Rampe Major' => 1.7km vs. around 2.7km
        queries.add(new Query(43.730864, 7.420771, 43.727687, 7.418737, 2702, 111));
        // 2.
        queries.add(new Query(43.728499, 7.417907, 43.74958, 7.436566, 4208, 228));
        // 3.
        queries.add(new Query(43.728677, 7.41016, 43.739213, 7.427806, 2776, 167));
        // 4.
        queries.add(new Query(43.733802, 7.413433, 43.739662, 7.424355, 1593, 85));

        // try reverse direction
        // 1.
        queries.add(new Query(43.727687, 7.418737, 43.730864, 7.420771, 2598, 115));
        queries.add(new Query(43.74958, 7.436566, 43.728499, 7.417907, 4250, 165));
        queries.add(new Query(43.739213, 7.427806, 43.728677, 7.41016, 2806, 145));
        // 4. avoid tunnel(s)!
        queries.add(new Query(43.739662, 7.424355, 43.733802, 7.413433, 1901, 116));
        // atm the custom model is intended to be used with 'roads' vehicle when allowing reverse direction for oneways
        // but tests here still assert that reverse oneways are excluded
        GraphHopper hopper = createHopper(MONACO,
                new CustomProfile("bike").setCustomModel(CustomModel.merge(getCustomModel("bike.json"), getCustomModel("bike_elevation.json")).
                        addToPriority(If("!bike_access", MULTIPLY, "0"))).setVehicle("roads"));
        hopper.setVehiclesString("roads,bike");
        hopper.setElevationProvider(new SRTMProvider(DIR));
        hopper.importOrLoad();
        checkQueries(hopper, queries);
    }

    @Test
    public void testLandmarkBug() {
        List<Query> queries = new ArrayList<>();
        Query run = new Query();
        run.add(50.016923, 11.514187, 0, 0);
        run.add(50.019129, 11.500325, 0, 0);
        run.add(50.023623, 11.56929, 7069, 178);
        queries.add(run);

        GraphHopper hopper = createHopper(BAYREUTH, new CustomProfile("bike").setVehicle("bike"));
        hopper.importOrLoad();
        checkQueries(hopper, queries);
    }

    @Test
    public void testBug1014() {
        List<Query> queries = new ArrayList<>();
        Query query = new Query();
        query.add(50.015861, 11.51041, 0, 0);
        query.add(50.019129, 11.500325, 0, 0);
        query.add(50.023623, 11.56929, 6777, 175);
        queries.add(query);

        GraphHopper hopper = createHopper(BAYREUTH, new CustomProfile("bike").setVehicle("bike"));
        hopper.importOrLoad();
        checkQueries(hopper, queries);
    }

    @Test
    public void testMonacoBike() {
        List<Query> queries = new ArrayList<>();
        queries.add(new Query(43.730864, 7.420771, 43.727687, 7.418737, 1642, 87));
        queries.add(new Query(43.727687, 7.418737, 43.74958, 7.436566, 3580, 168));
        queries.add(new Query(43.728677, 7.41016, 43.739213, 7.427806, 2323, 121));
        queries.add(new Query(43.733802, 7.413433, 43.739662, 7.424355, 1446, 91));
        GraphHopper hopper = createHopper(MONACO, new CustomProfile("bike").
                setCustomModel(new CustomModel().setDistanceInfluence(7000d)).setVehicle("bike"));
        hopper.importOrLoad();
        checkQueries(hopper, queries);
    }

    @Test
    public void testMonacoMountainBike() {
        List<Query> queries = new ArrayList<>();
        // for mtb it is also ok to go over steps (43.7318,7.423) -> 1900m vs 2600m (in latest OSM data all bikes are forbidden and steps aren't taken)
        queries.add(new Query(43.730864, 7.420771, 43.727687, 7.418737, 2594, 111));
        queries.add(new Query(43.727687, 7.418737, 43.74958, 7.436566, 3655, 185));
        queries.add(new Query(43.728677, 7.41016, 43.739213, 7.427806, 2651, 167));
        // hard to select between secondary and primary (both are AVOID for mtb)
        queries.add(new Query(43.733802, 7.413433, 43.739662, 7.424355, 1867, 107));

        GraphHopper hopper = createHopper(MONACO, new CustomProfile("mtb").setVehicle("mtb"));
        hopper.importOrLoad();
        checkQueries(hopper, queries);

        Helper.removeDir(new File(GH_LOCATION));

        hopper = createHopper(MONACO,
                new CustomProfile("mtb").setVehicle("mtb"),
                new CustomProfile("racingbike").setVehicle("racingbike"));
        hopper.importOrLoad();
        checkQueries(hopper, queries);
    }

    @Test
    public void testMonacoRacingBike() {
        List<Query> queries = new ArrayList<>();
        queries.add(new Query(43.730864, 7.420771, 43.727687, 7.418737, 2594, 111));
        queries.add(new Query(43.727687, 7.418737, 43.74958, 7.436566, 3615, 184));
        queries.add(new Query(43.728677, 7.41016, 43.739213, 7.427806, 2651, 167));
        queries.add(new Query(43.733802, 7.413433, 43.739662, 7.424355, 1516, 86));

        GraphHopper hopper = createHopper(MONACO, new CustomProfile("racingbike").setVehicle("racingbike"));
        hopper.importOrLoad();
        checkQueries(hopper, queries);

        Helper.removeDir(new File(GH_LOCATION));

        hopper = createHopper(MONACO,
                new CustomProfile("racingbike").setVehicle("racingbike"),
                new CustomProfile("bike").setVehicle("bike")
        );
        hopper.importOrLoad();
        checkQueries(hopper, queries);
    }

    @Test
    public void testKremsBikeRelation() {
        List<Query> queries = new ArrayList<>();
        queries.add(new Query(48.409523, 15.602394, 48.375466, 15.72916, 12491, 159));
        queries.add(new Query(48.410061, 15.63951, 48.411386, 15.604899, 3077, 79));
        queries.add(new Query(48.412294, 15.62007, 48.398306, 15.609667, 3965, 94));

        GraphHopper hopper = createHopper(KREMS,
                new CustomProfile("bike").
                        setCustomModel(new CustomModel().setDistanceInfluence(70d)).setVehicle("bike"));
        hopper.importOrLoad();
        checkQueries(hopper, queries);
        hopper.getBaseGraph();

        Helper.removeDir(new File(GH_LOCATION));

        hopper = createHopper(KREMS,
                new CustomProfile("bike").
                        setCustomModel(new CustomModel().setDistanceInfluence(70d)).setVehicle("bike"),
                new CustomProfile("car").setVehicle("car"));
        hopper.importOrLoad();
        checkQueries(hopper, queries);
    }

    @Test
    public void testKremsMountainBikeRelation() {
        List<Query> queries = new ArrayList<>();
        queries.add(new Query(48.409523, 15.602394, 48.375466, 15.72916, 12574, 169));
        queries.add(new Query(48.410061, 15.63951, 48.411386, 15.604899, 3101, 94));
        queries.add(new Query(48.412294, 15.62007, 48.398306, 15.609667, 3965, 95));

        GraphHopper hopper = createHopper(KREMS, new CustomProfile("mtb").
                setCustomModel(new CustomModel().setDistanceInfluence(70d)).setVehicle("mtb"));
        hopper.importOrLoad();
        checkQueries(hopper, queries);

        Helper.removeDir(new File(GH_LOCATION));

        hopper = createHopper(KREMS,
                new CustomProfile("mtb").
                        setCustomModel(new CustomModel().setDistanceInfluence(70d)).setVehicle("mtb"),
                new CustomProfile("bike").setVehicle("bike"));
        hopper.importOrLoad();
        checkQueries(hopper, queries);

    }

    private List<Query> createAndorraQueries() {
        List<Query> queries = new ArrayList<>();
        queries.add(new Query(42.56819, 1.603231, 42.571034, 1.520662, 17708, 524));
        queries.add(new Query(42.529176, 1.571302, 42.571034, 1.520662, 11408, 305));
        return queries;
    }

    @Test
    public void testAndorra() {
        Profile profile = new CustomProfile("car").setVehicle("car");
        GraphHopper hopper = createHopper(ANDORRA, profile);
        hopper.importOrLoad();
        checkQueries(hopper, createAndorraQueries());
    }

    @Test
    public void testAndorraPbf() {
        Profile profile = new CustomProfile("car").setVehicle("car");
        GraphHopper hopper = createHopper(ANDORRA_PBF, profile);
        hopper.importOrLoad();
        checkQueries(hopper, createAndorraQueries());
    }

    @Test
    public void testAndorraFoot() {
        List<Query> queries = createAndorraQueries();
        queries.get(0).getPoints().get(1).expectedDistance = 16460;
        queries.get(0).getPoints().get(1).expectedPoints = 653;
        queries.get(1).getPoints().get(1).expectedDistance = 12839;
        queries.get(1).getPoints().get(1).expectedPoints = 435;

        queries.add(new Query(42.521269, 1.52298, 42.50418, 1.520662, 3223, 107));

        GraphHopper hopper = createHopper(ANDORRA, new CustomProfile("foot").setVehicle("foot"));
        hopper.importOrLoad();
        checkQueries(hopper, queries);
    }

    @Test
    public void testCampoGrande() {
        // test not only NE quadrant of earth!

        // bzcat campo-grande.osm.bz2 
        //   | ./bin/osmosis --read-xml enableDateParsing=no file=- --bounding-box top=-20.4 left=-54.6 bottom=-20.6 right=-54.5 --write-xml file=- 
        //   | bzip2 > campo-grande.extracted.osm.bz2
        List<Query> queries = new ArrayList<>();
        queries.add(new Query(-20.4001, -54.5999, -20.598, -54.54, 25323, 271));
        queries.add(new Query(-20.43, -54.54, -20.537, -54.5999, 16233, 226));
        GraphHopper hopper = createHopper(DIR + "/campo-grande.osm.gz",
                new CustomProfile("car").setCustomModel(new CustomModel().setDistanceInfluence(1_000d)).setVehicle("car"));
        hopper.importOrLoad();
        checkQueries(hopper, queries);
    }

    @Test
    public void testMonacoVia() {
        Query query = new Query();
        query.add(43.730729, 7.42135, 0, 0);
        query.add(43.727697, 7.419199, 2581, 110);
        query.add(43.726387, 7.405, 3001, 90);

        List<Query> queries = new ArrayList<>();
        queries.add(query);

        GraphHopper hopper = createHopper(MONACO, new CustomProfile("car").setCustomModel(new CustomModel().setDistanceInfluence(10_000d)).setVehicle("car"));
        hopper.importOrLoad();
        checkQueries(hopper, queries);
    }

    @Test
    public void testHarsdorf() {
        List<Query> queries = new ArrayList<>();
        // TODO somehow the bigger road is take even if we make it less preferred (e.g. introduce AVOID AT ALL costs for lanes=2&&maxspeed>50)
        queries.add(new Query(50.004333, 11.600254, 50.044449, 11.543434, 6951, 190));

        // choose Unterloher Weg and the following residential + cycleway
        // list.add(new OneRun(50.004333, 11.600254, 50.044449, 11.543434, 6931, 184));
        GraphHopper hopper = createHopper(BAYREUTH, new CustomProfile("bike").setVehicle("bike"));
        hopper.importOrLoad();
        checkQueries(hopper, queries);
    }

    @Test
    public void testNeudrossenfeld() {
        List<Query> list = new ArrayList<>();
        // choose cycleway (Dreschenauer Straße)
        list.add(new Query(49.987132, 11.510496, 50.018839, 11.505024, 3985, 106));

        GraphHopper hopper = createHopper(BAYREUTH, new CustomProfile("bike").setVehicle("bike"));
        hopper.setElevationProvider(new SRTMProvider(DIR));
        hopper.importOrLoad();
        checkQueries(hopper, list);

        Helper.removeDir(new File(GH_LOCATION));

        hopper = createHopper(BAYREUTH, new CustomProfile("bike2").setVehicle("bike"));
        hopper.setElevationProvider(new SRTMProvider(DIR));
        hopper.importOrLoad();
        checkQueries(hopper, list);
    }

    @Test
    public void testBikeBayreuth_UseBikeNetwork() {
        List<Query> list = new ArrayList<>();
        list.add(new Query(49.979667, 11.521019, 49.987415, 11.510577, 1288, 45));

        GraphHopper hopper = createHopper(BAYREUTH, new CustomProfile("bike").setCustomModel(
                CustomModel.merge(getCustomModel("bike.json"), getCustomModel("bike_elevation.json"))).setVehicle("roads"));
        hopper.setVehiclesString("bike,roads");
        hopper.setElevationProvider(new SRTMProvider(DIR));
        hopper.importOrLoad();
        checkQueries(hopper, list);
    }

    @Test
    public void testDisconnectedAreaAndMultiplePoints() {
        Query query = new Query();
        query.add(53.753177, 9.435968, 10, 10);
        query.add(53.751299, 9.386959, 10, 10);
        query.add(53.751299, 9.3869, 10, 10);

        GraphHopper hopper = createHopper(DIR + "/krautsand.osm.gz",
                new CustomProfile("car").setVehicle("car"));
        hopper.importOrLoad();

        for (Function<Query, GHRequest> requestFactory : createRequestFactories()) {
            GHRequest request = requestFactory.apply(query);
            request.setProfile(hopper.getProfiles().get(0).getName());
            GHResponse res = hopper.route(request);
            assertTrue(res.hasErrors());
            assertTrue(res.getErrors().toString().contains("ConnectionNotFound"), res.getErrors().toString());
        }
    }

    @Test
    public void testMonacoParallel() throws InterruptedException {
        GraphHopper hopper = createHopper(MONACO, new CustomProfile("car").setCustomModel(new CustomModel().setDistanceInfluence(10_000d)).setVehicle("car"));
        hopper.getReaderConfig().setMaxWayPointDistance(0);
        hopper.getRouterConfig().setSimplifyResponse(false);
        hopper.importOrLoad();
        final List<Query> queries = createMonacoCarQueries();
        List<Thread> threads = new ArrayList<>();
        final AtomicInteger routeCount = new AtomicInteger(0);
        // testing if algorithms are independent. should be. so test only two algorithms.
        List<Function<Query, GHRequest>> requestFactories = Arrays.asList(
                q -> createRequest(q).setAlgorithm(DIJKSTRA_BI).setProfile("car"),
                q -> createRequest(q).setAlgorithm(ASTAR_BI).setProfile("car")
        );
        int loops = 100;
        for (int i = 0; i < loops; i++) {
            for (Query query : queries) {
                for (Function<Query, GHRequest> requestFactory : requestFactories) {
                    GHRequest req = requestFactory.apply(query);
                    Thread t = new Thread(() -> {
                        GHResponse res = hopper.route(req);
                        checkResponse(res, query);
                        routeCount.incrementAndGet();
                    });
                    t.start();
                    threads.add(t);
                }
            }
        }

        for (Thread t : threads)
            t.join();
        assertEquals(loops * queries.size() * requestFactories.size(), routeCount.get());
    }

    private static class Query {
        private final List<ViaPoint> points = new ArrayList<>();

        public Query() {
        }

        public Query(double fromLat, double fromLon, double toLat, double toLon, double expectedDistance, int expectedPoints) {
            add(fromLat, fromLon, 0, 0);
            add(toLat, toLon, expectedDistance, expectedPoints);
        }

        public Query add(double lat, double lon, double dist, int locs) {
            points.add(new ViaPoint(lat, lon, dist, locs));
            return this;
        }

        public List<ViaPoint> getPoints() {
            return points;
        }

        @Override
        public String toString() {
            return points.toString();
        }
    }

    private static class ViaPoint {
        double lat, lon;
        int expectedPoints;
        double expectedDistance;

        public ViaPoint(double lat, double lon, double expectedDistance, int expectedPoints) {
            this.lat = lat;
            this.lon = lon;
            this.expectedPoints = expectedPoints;
            this.expectedDistance = expectedDistance;
        }

        @Override
        public String toString() {
            return lat + ", " + lon + ", expectedPoints:" + expectedPoints + ", expectedDistance:" + expectedDistance;
        }
    }

    /**
     * Creates a {@link GraphHopper} instance with some default settings for this test. The settings can
     * be adjusted before calling {@link GraphHopper#importOrLoad()}
     */
    private GraphHopper createHopper(String osmFile, Profile... profiles) {
        GraphHopper hopper = new GraphHopper().
                setStoreOnFlush(false).
                setOSMFile(osmFile).
                setProfiles(profiles).
                setEncodedValuesString("average_slope,max_slope,hike_rating").
                setGraphHopperLocation(GH_LOCATION);
        hopper.getRouterConfig().setSimplifyResponse(false);
        hopper.setMinNetworkSize(0);
        hopper.getReaderConfig().setMaxWayPointDistance(0);
        hopper.getLMPreparationHandler().setLMProfiles(new LMProfile(profiles[0].getName()));
        hopper.getCHPreparationHandler().setCHProfiles(new CHProfile(profiles[0].getName()));
        return hopper;
    }

    /**
     * Runs the given queries on the given GraphHopper instance and checks the expectations.
     * All queries will use the first profile.
     */
    private void checkQueries(GraphHopper hopper, List<Query> queries) {
        for (Function<Query, GHRequest> requestFactory : createRequestFactories()) {
            for (Query query : queries) {
                GHRequest request = requestFactory.apply(query);
                Profile profile = hopper.getProfiles().get(0);
                request.setProfile(profile.getName());
                GHResponse res = hopper.route(request);
                checkResponse(res, query);
                String expectedAlgo = request.getHints().getString("expected_algo", "no_expected_algo");
                // for edge-based routing we expect a slightly different algo name for CH
                if (profile.isTurnCosts())
                    expectedAlgo = expectedAlgo.replaceAll("\\|ch-routing", "|ch|edge_based|no_sod-routing");
                assertTrue(res.getBest().getDebugInfo().contains(expectedAlgo),
                        "Response does not contain expected algo string. Expected: '" + expectedAlgo +
                                "', got: '" + res.getBest().getDebugInfo() + "'");
            }
        }
    }

    private void checkResponse(GHResponse res, Query query) {
        assertFalse(res.hasErrors(), res.getErrors().toString());
        ResponsePath responsePath = res.getBest();
        assertFalse(responsePath.hasErrors(), responsePath.getErrors().toString());
        assertEquals(distCalc.calcDistance(responsePath.getPoints()), responsePath.getDistance(), 2,
                "responsePath.getDistance does not equal point list distance");
        assertEquals(query.getPoints().stream().mapToDouble(a -> a.expectedDistance).sum(), responsePath.getDistance(), 2, "unexpected distance");
        // We check the number of points to make sure we found the expected route.
        // There are real world instances where A-B-C is identical to A-C (in meter precision).
        assertEquals(query.getPoints().stream().mapToInt(a -> a.expectedPoints).sum(), responsePath.getPoints().size(), 1, "unexpected point list size");
    }

    private List<Function<Query, GHRequest>> createRequestFactories() {
        // here we setup different kinds of requests to calculate routes with different algorithms
        return Arrays.asList(
                // flex
                q -> createRequest(q).putHint("expected_algo", "dijkstra-routing")
                        .putHint("ch.disable", true).putHint("lm.disable", true).setAlgorithm(DIJKSTRA),
                q -> createRequest(q).putHint("expected_algo", "astar|beeline-routing")
                        .putHint("ch.disable", true).putHint("lm.disable", true).setAlgorithm(ASTAR),
                q -> createRequest(q).putHint("expected_algo", "dijkstrabi-routing")
                        .putHint("ch.disable", true).putHint("lm.disable", true).setAlgorithm(DIJKSTRA_BI),
                q -> createRequest(q).putHint("expected_algo", "astarbi|beeline-routing")
                        .putHint("ch.disable", true).putHint("lm.disable", true).setAlgorithm(ASTAR_BI)
                        .putHint(ASTAR_BI + ".approximation", "BeelineSimplification"),
                // LM
                q -> createRequest(q).putHint("expected_algo", "astarbi|landmarks-routing")
                        .putHint("ch.disable", true)
                        .setAlgorithm(ASTAR_BI).putHint(ASTAR_BI + ".approximation", "BeelineSimplification"),
                // CH
                q -> createRequest(q).putHint("expected_algo", "dijkstrabi|ch-routing")
                        .setAlgorithm(DIJKSTRA_BI),
                q -> createRequest(q).putHint("expected_algo", "astarbi|ch-routing")
                        .setAlgorithm(ASTAR_BI)
        );
    }

    private GHRequest createRequest(Query query) {
        GHRequest req = new GHRequest();
        for (ViaPoint assumption : query.points) {
            req.addPoint(new GHPoint(assumption.lat, assumption.lon));
        }
        return req;
    }

}
