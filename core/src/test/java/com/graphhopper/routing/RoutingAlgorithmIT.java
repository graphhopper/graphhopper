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

import com.graphhopper.routing.util.TestAlgoCollector;
import com.graphhopper.GraphHopper;
import com.graphhopper.reader.PrinctonReader;
import com.graphhopper.reader.dem.SRTMProvider;
import com.graphhopper.routing.ch.PrepareContractionHierarchies;
import com.graphhopper.routing.util.*;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.TestAlgoCollector.AlgoHelperEntry;
import com.graphhopper.routing.util.TestAlgoCollector.OneRun;
import com.graphhopper.storage.*;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.LocationIndexTree;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.Helper;
import com.graphhopper.util.StopWatch;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPInputStream;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

/**
 * Try algorithms, indices and graph storages with real data
 * <p/>
 * @author Peter Karich
 */
public class RoutingAlgorithmIT
{
    TestAlgoCollector testCollector;

    @Before
    public void setUp()
    {
        testCollector = new TestAlgoCollector("core integration tests");
    }

    List<OneRun> createMonacoCar()
    {
        List<OneRun> list = new ArrayList<OneRun>();
        list.add(new OneRun(43.730729, 7.42135, 43.727697, 7.419199, 2580, 110));
        list.add(new OneRun(43.727687, 7.418737, 43.74958, 7.436566, 3588, 170));
        list.add(new OneRun(43.728677, 7.41016, 43.739213, 7.4277, 2561, 133));
        list.add(new OneRun(43.733802, 7.413433, 43.739662, 7.424355, 2230, 137));
        list.add(new OneRun(43.730949, 7.412338, 43.739643, 7.424542, 2100, 116));
        list.add(new OneRun(43.727592, 7.419333, 43.727712, 7.419333, 0, 1));

        // same special cases where GPS-exact routing could have problems (same edge and neighbor edges)
        list.add(new OneRun(43.727592, 7.419333, 43.727712, 7.41934, 0, 1));
        // on the same edge and very release
        list.add(new OneRun(43.727592, 7.419333, 43.727712, 7.4193, 3, 2));
        // one way stuff
        list.add(new OneRun(43.729445, 7.415063, 43.728856, 7.41472, 103, 4));
        list.add(new OneRun(43.728856, 7.41472, 43.729445, 7.415063, 320, 11));
        return list;
    }

    @Test
    public void testMonaco()
    {
        Graph g = runAlgo(testCollector, "files/monaco.osm.gz", "target/monaco-gh",
                createMonacoCar(), "CAR", true, "CAR", "shortest", false);

        assertEquals(testCollector.toString(), 0, testCollector.errors.size());

        // When OSM file stays unchanged make static edge and node IDs a requirement
        assertEquals(GHUtility.asSet(9, 111, 182), GHUtility.getNeighbors(g.createEdgeExplorer().setBaseNode(10)));
        assertEquals(GHUtility.asSet(19, 21), GHUtility.getNeighbors(g.createEdgeExplorer().setBaseNode(20)));
        assertEquals(GHUtility.asSet(478, 84, 83), GHUtility.getNeighbors(g.createEdgeExplorer().setBaseNode(480)));

        assertEquals(43.736989, g.getNodeAccess().getLat(10), 1e-6);
        assertEquals(7.429758, g.getNodeAccess().getLon(201), 1e-6);
    }

    @Test
    public void testMonacoMotorcycle()
    {
        List<OneRun> list = new ArrayList<OneRun>();
        list.add(new OneRun(43.730729, 7.42135, 43.727697, 7.419199, 2697, 117));
        list.add(new OneRun(43.727687, 7.418737, 43.74958, 7.436566, 3749, 170));
        list.add(new OneRun(43.728677, 7.41016, 43.739213, 7.4277, 3164, 165));
        list.add(new OneRun(43.733802, 7.413433, 43.739662, 7.424355, 2423, 141));
        list.add(new OneRun(43.730949, 7.412338, 43.739643, 7.424542, 2253, 120));
        list.add(new OneRun(43.727592, 7.419333, 43.727712, 7.419333, 0, 1));
        runAlgo(testCollector, "files/monaco.osm.gz", "target/monaco-mc-gh",
                list, "motorcycle", true, "motorcycle", "fastest", true);

        assertEquals(testCollector.toString(), 0, testCollector.errors.size());
    }

    @Test
    public void testBike2_issue432()
    {
        List<OneRun> list = new ArrayList<OneRun>();
        list.add(new OneRun(52.349969, 8.013813, 52.349713, 8.013293, 56, 7));
        // reverse route avoids the location
//        list.add(new OneRun(52.349713, 8.013293, 52.349969, 8.013813, 293, 21));
        runAlgo(testCollector, "files/map-bug432.osm.gz", "target/map-bug432-gh",
                list, "bike2", true, "bike2", "fastest", true);

        assertEquals(testCollector.toString(), 0, testCollector.errors.size());
    }

    @Test
    public void testMonacoAllAlgorithmsWithBaseGraph()
    {
        String vehicle = "car";
        String graphFile = "target/monaco-gh";
        String osmFile = "files/monaco.osm.gz";
        String importVehicles = vehicle;

        Helper.removeDir(new File(graphFile));
        GraphHopper hopper = new GraphHopper().
                // avoid that path.getDistance is too different to path.getPoint.calcDistance
                setWayPointMaxDistance(0).
                setOSMFile(osmFile).
                setCHEnable(false).
                setGraphHopperLocation(graphFile).
                setEncodingManager(new EncodingManager(importVehicles));

        hopper.importOrLoad();

        FlagEncoder encoder = hopper.getEncodingManager().getEncoder(vehicle);
        Weighting weighting = hopper.createWeighting(new WeightingMap("shortest"), encoder);

        List<AlgoHelperEntry> prepares = createAlgos(hopper.getGraphHopperStorage(), hopper.getLocationIndex(),
                encoder, true, TraversalMode.NODE_BASED, weighting, hopper.getEncodingManager());
        AlgoHelperEntry chPrepare = prepares.get(prepares.size() - 1);
        if (!(chPrepare.getQueryGraph() instanceof LevelGraph))
            throw new IllegalStateException("Last prepared queryGraph has to be a levelGraph");

        // set all normal algorithms to baseGraph of already prepared to see if all algorithms still work
        Graph baseGraphOfCHPrepared = chPrepare.getBaseGraph();
        for (AlgoHelperEntry ahe : prepares)
        {
            if (!(ahe.getQueryGraph() instanceof LevelGraph))
            {
                ahe.setQueryGraph(baseGraphOfCHPrepared);
            }
        }

        List<OneRun> forEveryAlgo = createMonacoCar();
        EdgeFilter edgeFilter = new DefaultEdgeFilter(encoder);
        for (AlgoHelperEntry entry : prepares)
        {
            LocationIndex idx = entry.getIdx();
            for (OneRun oneRun : forEveryAlgo)
            {
                List<QueryResult> list = oneRun.getList(idx, edgeFilter);
                testCollector.assertDistance(entry, list, oneRun);
            }
        }
    }

    @Test
    public void testOneWayCircleBug()
    {
        // export from http://www.openstreetmap.org/export#map=19/51.37605/-0.53155
        List<OneRun> list = new ArrayList<OneRun>();
        // going the bit longer way out of the circle
        list.add(new OneRun(51.376197, -0.531576, 51.376509, -0.530863, 153, 18));
        // now exacle the opposite direction: going into the circle (shorter)
        list.add(new OneRun(51.376509, -0.530863, 51.376197, -0.531576, 75, 15));

        runAlgo(testCollector, "files/circle-bug.osm.gz", "target/circle-bug-gh",
                list, "CAR", true, "CAR", "shortest", false);
        assertEquals(testCollector.toString(), 0, testCollector.errors.size());
    }

    @Test
    public void testMoscow()
    {
        // extracted via ./graphhopper.sh extract "37.582641,55.805261,37.626929,55.824455"
        List<OneRun> list = new ArrayList<OneRun>();
        // choose perpendicular
        // http://localhost:8989/?point=55.818994%2C37.595354&point=55.819175%2C37.596931
        list.add(new OneRun(55.818994, 37.595354, 55.819175, 37.596931, 1052, 14));
        // should choose the closest road not the other one (opposite direction)
        // http://localhost:8989/?point=55.818898%2C37.59661&point=55.819066%2C37.596374
        list.add(new OneRun(55.818898, 37.59661, 55.819066, 37.596374, 24, 2));
        // respect one way!
        // http://localhost:8989/?point=55.819066%2C37.596374&point=55.818898%2C37.59661
        list.add(new OneRun(55.819066, 37.596374, 55.818898, 37.59661, 1114, 23));
        runAlgo(testCollector, "files/moscow.osm.gz", "target/moscow-gh",
                list, "CAR", true, "CAR", "fastest", false);
        assertEquals(testCollector.toString(), 0, testCollector.errors.size());
    }

    @Test
    public void testMoscowTurnCosts()
    {
        List<OneRun> list = new ArrayList<OneRun>();
        list.add(new OneRun(55.813357, 37.5958585, 55.811042, 37.594689, 1043.99, 12));
        list.add(new OneRun(55.813159, 37.593884, 55.811278, 37.594217, 1048, 13));
        // TODO include CH
        boolean testAlsoCH = false, is3D = false;
        runAlgo(testCollector, "files/moscow.osm.gz", "target/graph-moscow",
                list, "CAR|turnCosts=true", testAlsoCH, "CAR", "fastest", is3D);

        assertEquals(testCollector.toString(), 0, testCollector.errors.size());
    }

    @Test
    public void testMonacoFastest()
    {
        List<OneRun> list = createMonacoCar();
        list.get(0).setLocs(1, 117);
        list.get(0).setDistance(1, 2584);
        list.get(3).setDistance(1, 2279);
        list.get(3).setLocs(1, 141);
        list.get(4).setDistance(1, 2149);
        list.get(4).setLocs(1, 120);
        runAlgo(testCollector, "files/monaco.osm.gz", "target/monaco-gh",
                list, "CAR", true, "CAR", "fastest", false);
        assertEquals(testCollector.toString(), 0, testCollector.errors.size());
    }

    @Test
    public void testMonacoMixed()
    {
        // Additional locations are inserted because of new crossings from foot to highway paths!
        // Distance is the same.
        List<OneRun> list = createMonacoCar();
        list.get(0).setLocs(1, 110);
        list.get(1).setLocs(1, 170);
        list.get(2).setLocs(1, 132);
        list.get(3).setLocs(1, 137);
        list.get(4).setLocs(1, 116);

        runAlgo(testCollector, "files/monaco.osm.gz", "target/monaco-gh",
                list, "CAR,FOOT", false, "CAR", "shortest", false);
        assertEquals(testCollector.toString(), 0, testCollector.errors.size());
    }

    List<OneRun> createMonacoFoot()
    {
        List<OneRun> list = new ArrayList<OneRun>();
        list.add(new OneRun(43.730729, 7.421288, 43.727697, 7.419199, 1566, 92));
        list.add(new OneRun(43.727687, 7.418737, 43.74958, 7.436566, 3438, 136));
        list.add(new OneRun(43.728677, 7.41016, 43.739213, 7.427806, 2085, 112));
        list.add(new OneRun(43.733802, 7.413433, 43.739662, 7.424355, 1425, 89));
        return list;
    }

    @Test
    public void testMonacoFoot()
    {
        Graph g = runAlgo(testCollector, "files/monaco.osm.gz", "target/monaco-gh",
                createMonacoFoot(), "FOOT", true, "FOOT", "shortest", false);
        assertEquals(testCollector.toString(), 0, testCollector.errors.size());

        // see testMonaco for a similar ID test
        assertEquals(GHUtility.asSet(2, 908, 570), GHUtility.getNeighbors(g.createEdgeExplorer().setBaseNode(10)));
        assertEquals(GHUtility.asSet(443, 954, 739), GHUtility.getNeighbors(g.createEdgeExplorer().setBaseNode(440)));
        assertEquals(GHUtility.asSet(910, 403, 122, 913), GHUtility.getNeighbors(g.createEdgeExplorer().setBaseNode(911)));

        assertEquals(43.743705, g.getNodeAccess().getLat(100), 1e-6);
        assertEquals(7.426362, g.getNodeAccess().getLon(701), 1e-6);
    }

    @Test
    public void testMonacoFoot3D()
    {
        // most routes have same number of points as testMonaceFoot results but longer distance due to elevation difference
        List<OneRun> list = createMonacoFoot();
        list.get(0).setDistance(1, 1627);
        list.get(2).setDistance(1, 2258);
        list.get(3).setDistance(1, 1482);

        // or slightly longer tour with less nodes: list.get(1).setDistance(1, 3610);
        list.get(1).setDistance(1, 3595);
        list.get(1).setLocs(1, 149);

        runAlgo(testCollector, "files/monaco.osm.gz", "target/monaco-gh",
                list, "FOOT", true, "FOOT", "shortest", true);
        assertEquals(testCollector.toString(), 0, testCollector.errors.size());
    }

    @Test
    public void testNorthBayreuthFootFastestAnd3D()
    {
        List<OneRun> list = new ArrayList<OneRun>();
        // prefer hiking route 'Teufelsloch Unterwaiz' and 'Rotmain-Wanderweg'        
        list.add(new OneRun(49.974972, 11.515657, 49.991022, 11.512299, 2365, 66));
        // prefer hiking route 'Markgrafenweg Bayreuth Kulmbach'
        list.add(new OneRun(49.986111, 11.550407, 50.023182, 11.555386, 5165, 133));
        runAlgo(testCollector, "files/north-bayreuth.osm.gz", "target/north-bayreuth-gh",
                list, "FOOT", true, "FOOT", "fastest", true);
        assertEquals(testCollector.toString(), 0, testCollector.errors.size());
    }

    @Test
    public void testMonacoBike3D_twoSpeedsPerEdge()
    {
        List<OneRun> list = new ArrayList<OneRun>();
        // 1. alternative: go over steps 'Rampe Major' => 1.7km vs. around 2.7km
        list.add(new OneRun(43.730864, 7.420771, 43.727687, 7.418737, 2710, 118));
        // 2.
        list.add(new OneRun(43.728499, 7.417907, 43.74958, 7.436566, 3777, 194));
        // 3.
        list.add(new OneRun(43.728677, 7.41016, 43.739213, 7.427806, 2776, 167));
        // 4.
        list.add(new OneRun(43.733802, 7.413433, 43.739662, 7.424355, 1544, 84));

        // try reverse direction
        // 1.
        list.add(new OneRun(43.727687, 7.418737, 43.730864, 7.420771, 2599, 115));
        list.add(new OneRun(43.74958, 7.436566, 43.728499, 7.417907, 4199, 165));
        list.add(new OneRun(43.739213, 7.427806, 43.728677, 7.41016, 3261, 177));
        // 4. avoid tunnel(s)!
        list.add(new OneRun(43.739662, 7.424355, 43.733802, 7.413433, 2452, 112));
        runAlgo(testCollector, "files/monaco.osm.gz", "target/monaco-gh",
                list, "BIKE2", true, "BIKE2", "fastest", true);
        assertEquals(testCollector.toString(), 0, testCollector.errors.size());
    }

    @Test
    public void testMonacoBike()
    {
        List<OneRun> list = new ArrayList<OneRun>();
        list.add(new OneRun(43.730864, 7.420771, 43.727687, 7.418737, 1642, 87));
        list.add(new OneRun(43.727687, 7.418737, 43.74958, 7.436566, 3580, 168));
        list.add(new OneRun(43.728677, 7.41016, 43.739213, 7.427806, 2323, 121));
        list.add(new OneRun(43.733802, 7.413433, 43.739662, 7.424355, 1434, 89));
        runAlgo(testCollector, "files/monaco.osm.gz", "target/monaco-gh",
                list, "BIKE", true, "BIKE", "shortest", false);
        assertEquals(testCollector.toString(), 0, testCollector.errors.size());
    }

    @Test
    public void testMonacoMountainBike()
    {
        List<OneRun> list = new ArrayList<OneRun>();
        list.add(new OneRun(43.730864, 7.420771, 43.727687, 7.418737, 2322, 110));
        list.add(new OneRun(43.727687, 7.418737, 43.74958, 7.436566, 3613, 178));
        list.add(new OneRun(43.728677, 7.41016, 43.739213, 7.427806, 2331, 121));
        // hard to select between secondard and primary (both are AVOID for mtb)
        list.add(new OneRun(43.733802, 7.413433, 43.739662, 7.424355, 1459, 88));
        runAlgo(testCollector, "files/monaco.osm.gz", "target/monaco-gh",
                list, "MTB", true, "MTB", "fastest", false);
        assertEquals(testCollector.toString(), 0, testCollector.errors.size());

        runAlgo(testCollector, "files/monaco.osm.gz", "target/monaco-gh",
                list, "MTB,RACINGBIKE", false, "MTB", "fastest", false);
        assertEquals(testCollector.toString(), 0, testCollector.errors.size());
    }

    @Test
    public void testMonacoRacingBike()
    {
        List<OneRun> list = new ArrayList<OneRun>();
        list.add(new OneRun(43.730864, 7.420771, 43.727687, 7.418737, 2594, 111));
        list.add(new OneRun(43.727687, 7.418737, 43.74958, 7.436566, 3588, 170));
        list.add(new OneRun(43.728677, 7.41016, 43.739213, 7.427806, 2572, 135));
        list.add(new OneRun(43.733802, 7.413433, 43.739662, 7.424355, 1490, 84));
        runAlgo(testCollector, "files/monaco.osm.gz", "target/monaco-gh",
                list, "RACINGBIKE", true, "RACINGBIKE", "fastest", false);
        assertEquals(testCollector.toString(), 0, testCollector.errors.size());

        runAlgo(testCollector, "files/monaco.osm.gz", "target/monaco-gh",
                list, "BIKE,RACINGBIKE", false, "RACINGBIKE", "fastest", false);
        assertEquals(testCollector.toString(), 0, testCollector.errors.size());
    }

    @Test
    public void testKremsBikeRelation()
    {
        List<OneRun> list = new ArrayList<OneRun>();
        list.add(new OneRun(48.409523, 15.602394, 48.375466, 15.72916, 12491, 159));
        list.add(new OneRun(48.410061, 15.63951, 48.411386, 15.604899, 3113, 87));
        list.add(new OneRun(48.412294, 15.62007, 48.398306, 15.609667, 3965, 94));

        runAlgo(testCollector, "files/krems.osm.gz", "target/krems-gh",
                list, "BIKE", true, "BIKE", "fastest", false);
        assertEquals(testCollector.toString(), 0, testCollector.errors.size());

        runAlgo(testCollector, "files/krems.osm.gz", "target/krems-gh",
                list, "CAR,BIKE", false, "BIKE", "fastest", false);
        assertEquals(testCollector.toString(), 0, testCollector.errors.size());
    }

    @Test
    public void testKremsMountainBikeRelation()
    {
        List<OneRun> list = new ArrayList<OneRun>();
        list.add(new OneRun(48.409523, 15.602394, 48.375466, 15.72916, 12574, 169));
        list.add(new OneRun(48.410061, 15.63951, 48.411386, 15.604899, 3101, 94));
        list.add(new OneRun(48.412294, 15.62007, 48.398306, 15.609667, 3965, 95));

        runAlgo(testCollector, "files/krems.osm.gz", "target/krems-gh",
                list, "MTB", true, "MTB", "fastest", false);
        assertEquals(testCollector.toString(), 0, testCollector.errors.size());

        runAlgo(testCollector, "files/krems.osm.gz", "target/krems-gh",
                list, "BIKE,MTB", false, "MTB", "fastest", false);
        assertEquals(testCollector.toString(), 0, testCollector.errors.size());
    }

    List<OneRun> createAndorra()
    {
        List<OneRun> list = new ArrayList<OneRun>();
        list.add(new OneRun(42.56819, 1.603231, 42.571034, 1.520662, 17708, 524));
        list.add(new OneRun(42.529176, 1.571302, 42.571034, 1.520662, 11408, 305));
        return list;
    }

    @Test
    public void testAndorra()
    {
        runAlgo(testCollector, "files/andorra.osm.gz", "target/andorra-gh",
                createAndorra(), "CAR", true, "CAR", "shortest", false);
        assertEquals(testCollector.toString(), 0, testCollector.errors.size());
    }

    @Test
    public void testAndorraPbf()
    {
        runAlgo(testCollector, "files/andorra.osm.pbf", "target/andorra-gh",
                createAndorra(), "CAR", true, "CAR", "shortest", false);
        assertEquals(testCollector.toString(), 0, testCollector.errors.size());
    }

    @Test
    public void testAndorraFoot()
    {
        List<OneRun> list = createAndorra();
        list.get(0).setDistance(1, 16354);
        list.get(0).setLocs(1, 648);
        list.get(1).setDistance(1, 12701);
        list.get(1).setLocs(1, 431);

        runAlgo(testCollector, "files/andorra.osm.gz", "target/andorra-gh",
                list, "FOOT", true, "FOOT", "shortest", false);
        assertEquals(testCollector.toString(), 0, testCollector.errors.size());
    }

    @Test
    public void testCampoGrande()
    {
        // test not only NE quadrant of earth!

        // bzcat campo-grande.osm.bz2 
        //   | ./bin/osmosis --read-xml enableDateParsing=no file=- --bounding-box top=-20.4 left=-54.6 bottom=-20.6 right=-54.5 --write-xml file=- 
        //   | bzip2 > campo-grande.extracted.osm.bz2
        List<OneRun> list = new ArrayList<OneRun>();
        list.add(new OneRun(-20.4, -54.6, -20.6, -54.54, 25516, 271));
        list.add(new OneRun(-20.43, -54.54, -20.537, -54.674, 18009, 237));
        runAlgo(testCollector, "files/campo-grande.osm.gz", "target/campo-grande-gh", list,
                "CAR", false, "CAR", "shortest", false);
        assertEquals(testCollector.toString(), 0, testCollector.errors.size());
    }

    @Test
    public void testMonacoVia()
    {
        OneRun oneRun = new OneRun();
        oneRun.add(43.730729, 7.42135, 0, 0);
        oneRun.add(43.727697, 7.419199, 2581, 110);
        oneRun.add(43.726387, 7.4, 3001, 90);

        List<OneRun> list = new ArrayList<OneRun>();
        list.add(oneRun);

        runAlgo(testCollector, "files/monaco.osm.gz", "target/monaco-gh",
                list, "CAR", true, "CAR", "shortest", false);
        assertEquals(testCollector.toString(), 0, testCollector.errors.size());
    }

    @Test
    public void testHarsdorf()
    {
        List<OneRun> list = new ArrayList<OneRun>();
        // choose Unterloher Weg and the following residential + cycleway
        list.add(new OneRun(50.004333, 11.600254, 50.044449, 11.543434, 6931, 184));
        runAlgo(testCollector, "files/north-bayreuth.osm.gz", "target/north-bayreuth-gh",
                list, "bike", true, "bike", "fastest", false);
        assertEquals(testCollector.toString(), 0, testCollector.errors.size());
    }

    @Test
    public void testNeudrossenfeld()
    {
        List<OneRun> list = new ArrayList<OneRun>();
        // choose cycleway (Dreschenauer Straße)
        list.add(new OneRun(49.987132, 11.510496, 50.018839, 11.505024, 3985, 106));

        runAlgo(testCollector, "files/north-bayreuth.osm.gz", "target/north-bayreuth-gh",
                list, "bike", true, "bike", "fastest", true);

        runAlgo(testCollector, "files/north-bayreuth.osm.gz", "target/north-bayreuth-gh",
                list, "bike2", true, "bike2", "fastest", true);
        assertEquals(testCollector.toString(), 0, testCollector.errors.size());
    }

    /**
     * @param testAlsoCH if true also the CH algorithms will be tested which needs preparation and
     * takes a bit longer
     */
    Graph runAlgo( TestAlgoCollector testCollector, String osmFile,
                   String graphFile, List<OneRun> forEveryAlgo, String importVehicles,
                   boolean testAlsoCH, String vehicle, String weightStr, boolean is3D )
    {
        AlgoHelperEntry algoEntry = null;
        OneRun tmpOneRun = null;
        try
        {
            Helper.removeDir(new File(graphFile));
            GraphHopper hopper = new GraphHopper().
                    setStoreOnFlush(true).
                    // avoid that path.getDistance is too different to path.getPoint.calcDistance
                    setWayPointMaxDistance(0).
                    setOSMFile(osmFile).
                    setCHEnable(false).
                    setGraphHopperLocation(graphFile).
                    setEncodingManager(new EncodingManager(importVehicles));
            if (is3D)
                hopper.setElevationProvider(new SRTMProvider().setCacheDir(new File("./files")));

            hopper.importOrLoad();

            TraversalMode tMode = importVehicles.toLowerCase().contains("turncosts=true")
                    ? TraversalMode.EDGE_BASED_1DIR : TraversalMode.NODE_BASED;
            FlagEncoder encoder = hopper.getEncodingManager().getEncoder(vehicle);
            Weighting weighting = hopper.createWeighting(new WeightingMap(weightStr), encoder);

            Collection<AlgoHelperEntry> prepares = createAlgos(hopper.getGraphHopperStorage(),
                    hopper.getLocationIndex(), encoder, testAlsoCH, tMode, weighting, hopper.getEncodingManager());
            EdgeFilter edgeFilter = new DefaultEdgeFilter(encoder);
            for (AlgoHelperEntry entry : prepares)
            {
                algoEntry = entry;
                LocationIndex idx = entry.getIdx();
                for (OneRun oneRun : forEveryAlgo)
                {
                    tmpOneRun = oneRun;
                    List<QueryResult> list = oneRun.getList(idx, edgeFilter);
                    testCollector.assertDistance(algoEntry, list, oneRun);
                }
            }

            return hopper.getGraphHopperStorage();
        } catch (Exception ex)
        {
            if (algoEntry == null)
                throw new RuntimeException("cannot handle file " + osmFile + ", " + ex.getMessage(), ex);

            throw new RuntimeException("cannot handle " + algoEntry.toString() + ", for " + tmpOneRun
                    + ", file " + osmFile + ", " + ex.getMessage(), ex);
        } finally
        {
            // Helper.removeDir(new File(graphFile));
        }
    }

    @Test
    public void testPerformance() throws IOException
    {
        int N = 10;
        int noJvmWarming = N / 4;

        Random rand = new Random(0);
        EncodingManager eManager = new EncodingManager("CAR");
        FlagEncoder encoder = eManager.getEncoder("CAR");
        GraphHopperStorage graph = new GraphBuilder(eManager).create();

        String bigFile = "10000EWD.txt.gz";
        new PrinctonReader(graph).setStream(new GZIPInputStream(PrinctonReader.class.getResourceAsStream(bigFile))).read();
        Collection<AlgoHelperEntry> prepares = createAlgos(graph, null, encoder, false, TraversalMode.NODE_BASED,
                new ShortestWeighting(), eManager);
        for (AlgoHelperEntry entry : prepares)
        {
            StopWatch sw = new StopWatch();
            for (int i = 0; i < N; i++)
            {
                int node1 = Math.abs(rand.nextInt(graph.getNodes()));
                int node2 = Math.abs(rand.nextInt(graph.getNodes()));
                RoutingAlgorithm d = entry.createAlgo(graph);
                if (i >= noJvmWarming)
                    sw.start();

                Path p = d.calcPath(node1, node2);
                // avoid jvm optimization => call p.distance
                if (i >= noJvmWarming && p.getDistance() > -1)
                    sw.stop();

                // System.out.println("#" + i + " " + name + ":" + sw.getSeconds() + " " + p.nodes());
            }

            float perRun = sw.stop().getSeconds() / ((float) (N - noJvmWarming));
            System.out.println("# " + getClass().getSimpleName() + " " + entry
                    + ":" + sw.stop().getSeconds() + ", per run:" + perRun);
            assertTrue("speed to low!? " + perRun + " per run", perRun < 0.08);
        }
    }

    @Test
    public void testMonacoParallel() throws IOException
    {
        System.out.println("testMonacoParallel takes a bit time...");
        String graphFile = "target/monaco-gh";
        Helper.removeDir(new File(graphFile));
        final EncodingManager encodingManager = new EncodingManager("CAR");
        GraphHopper hopper = new GraphHopper().
                setStoreOnFlush(true).
                setEncodingManager(encodingManager).
                setCHEnable(false).
                setWayPointMaxDistance(0).
                setOSMFile("files/monaco.osm.gz").
                setGraphHopperLocation(graphFile).
                importOrLoad();
        final Graph g = hopper.getGraphHopperStorage();
        final LocationIndex idx = hopper.getLocationIndex();
        final List<OneRun> instances = createMonacoCar();
        List<Thread> threads = new ArrayList<Thread>();
        final AtomicInteger integ = new AtomicInteger(0);
        int MAX = 100;
        final FlagEncoder carEncoder = encodingManager.getEncoder("CAR");

        // testing if algorithms are independent. should be. so test only two algorithms. 
        // also the preparing is too costly to be called for every thread
        int algosLength = 2;
        final Weighting weighting = new ShortestWeighting();
        final EdgeFilter filter = new DefaultEdgeFilter(carEncoder);
        for (int no = 0; no < MAX; no++)
        {
            for (int instanceNo = 0; instanceNo < instances.size(); instanceNo++)
            {
                String[] algos = new String[]
                {
                    "astar", "dijkstrabi"
                };
                for (final String algoStr : algos)
                {
                    // an algorithm is not thread safe! reuse via clear() is ONLY appropriated if used from same thread!
                    final int instanceIndex = instanceNo;
                    Thread t = new Thread()
                    {
                        @Override
                        public void run()
                        {
                            OneRun oneRun = instances.get(instanceIndex);
                            AlgorithmOptions opts = AlgorithmOptions.start().flagEncoder(carEncoder).weighting(weighting).algorithm(algoStr).build();
                            testCollector.assertDistance(new AlgoHelperEntry(g, g, opts, idx),
                                    oneRun.getList(idx, filter), oneRun);
                            integ.addAndGet(1);
                        }
                    };
                    t.start();
                    threads.add(t);
                }
            }
        }

        for (Thread t : threads)
        {
            try
            {
                t.join();
            } catch (InterruptedException ex)
            {
                throw new RuntimeException(ex);
            }
        }

        assertEquals(MAX * algosLength * instances.size(), integ.get());
        assertEquals(testCollector.toString(), 0, testCollector.errors.size());
        hopper.close();
    }

    static List<AlgoHelperEntry> createAlgos( GraphHopperStorage ghStorage,
                                              LocationIndex idx, final FlagEncoder encoder, boolean withCh,
                                              final TraversalMode tMode, final Weighting weighting, 
                                              final EncodingManager manager )
    {
        List<AlgoHelperEntry> prepare = new ArrayList<AlgoHelperEntry>();
        prepare.add(new AlgoHelperEntry(ghStorage, ghStorage, new AlgorithmOptions(AlgorithmOptions.ASTAR, encoder, weighting, tMode), idx));
        // later: include dijkstraOneToMany        
        prepare.add(new AlgoHelperEntry(ghStorage, ghStorage, new AlgorithmOptions(AlgorithmOptions.DIJKSTRA, encoder, weighting, tMode), idx));

        final AlgorithmOptions astarbiOpts = new AlgorithmOptions(AlgorithmOptions.ASTAR_BI, encoder, weighting, tMode);
        astarbiOpts.getHints().put(AlgorithmOptions.ASTAR_BI + ".approximation", "BeelineSimplification");
        final AlgorithmOptions dijkstrabiOpts = new AlgorithmOptions(AlgorithmOptions.DIJKSTRA_BI, encoder, weighting, tMode);
        prepare.add(new AlgoHelperEntry(ghStorage, ghStorage, astarbiOpts, idx));
        prepare.add(new AlgoHelperEntry(ghStorage, ghStorage, dijkstrabiOpts, idx));

        if (withCh)
        {
            GraphHopperStorage storageCopy = new GraphBuilder(manager).
                    set3D(ghStorage.getNodeAccess().is3D()).
                    setLevelGraph(true).
                    create();
            ghStorage.copyTo(storageCopy);
            final LevelGraph graphCH = storageCopy.getGraph(LevelGraph.class);
            final PrepareContractionHierarchies prepareCH = new PrepareContractionHierarchies(
                    new GHDirectory("", DAType.RAM_INT), storageCopy, graphCH, encoder, weighting, tMode);
            prepareCH.doWork();
            LocationIndex idxCH = new LocationIndexTree(storageCopy, new RAMDirectory()).prepareIndex();
            prepare.add(new AlgoHelperEntry(graphCH, storageCopy, dijkstrabiOpts, idxCH)
            {
                @Override
                public RoutingAlgorithm createAlgo( Graph qGraph )
                {
                    return prepareCH.createAlgo(qGraph, dijkstrabiOpts);
                }
            });

            prepare.add(new AlgoHelperEntry(graphCH, storageCopy, astarbiOpts, idxCH)
            {
                @Override
                public RoutingAlgorithm createAlgo( Graph qGraph )
                {
                    return prepareCH.createAlgo(qGraph, astarbiOpts);
                }
            });
        }
        return prepare;
    }
}
