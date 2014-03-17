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
import com.graphhopper.routing.util.*;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.Helper;
import com.graphhopper.util.StopWatch;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map.Entry;
import java.util.Random;
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
        list.add(new OneRun(43.730729, 7.42135, 43.727697, 7.419199, 2581, 91));
        list.add(new OneRun(43.727687, 7.418737, 43.74958, 7.436566, 3586, 126));
        list.add(new OneRun(43.728677, 7.41016, 43.739213, 7.4277, 2561, 107));
        list.add(new OneRun(43.733802, 7.413433, 43.739662, 7.424355, 2227, 105));
        list.add(new OneRun(43.730949, 7.412338, 43.739643, 7.424542, 2101, 100));
        list.add(new OneRun(43.727592, 7.419333, 43.727712, 7.419333, 0, 1));

        // same special cases where GPS-exact routing could have problems (same edge and neighbor edges)
        list.add(new OneRun(43.727592, 7.419333, 43.727712, 7.41934, 0, 1));
        // on the same edge and very close
        list.add(new OneRun(43.727592, 7.419333, 43.727712, 7.4193, 2, 2));
        // one way stuff
        list.add(new OneRun(43.729445, 7.415063, 43.728856, 7.41472, 107, 4));
        list.add(new OneRun(43.728856, 7.41472, 43.729445, 7.415063, 316, 11));
        return list;
    }

    @Test
    public void testMonaco()
    {
        runAlgo(testCollector, "files/monaco.osm.gz", "target/graph-monaco",
                createMonacoCar(), "CAR", true, "CAR", "shortest");
        assertEquals(testCollector.toString(), 0, testCollector.errors.size());
    }

    @Test
    public void testOneWayCircleBug()
    {
        // export from http://www.openstreetmap.org/export#map=19/51.37605/-0.53155
        List<OneRun> list = new ArrayList<OneRun>();
        // going the bit longer way out of the circle
        list.add(new OneRun(51.376197, -0.531576, 51.376509, -0.530863, 153, 14));
        // now exacle the opposite direction: going into the circle (shorter)
        list.add(new OneRun(51.376509, -0.530863, 51.376197, -0.531576, 75, 13));

        runAlgo(testCollector, "files/circle-bug.osm.gz", "target/graph-circle-bug",
                list, "CAR", true, "CAR", "shortest");
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
        runAlgo(testCollector, "files/moscow.osm.gz", "target/graph-moscow",
                list, "CAR", true, "CAR", "fastest");
        assertEquals(testCollector.toString(), 0, testCollector.errors.size());
    }

    @Test
    public void testMonacoFastest()
    {
        List<OneRun> list = createMonacoCar();
        list.get(0).locs = 95;
        list.get(3).dist = 2276;
        list.get(3).locs = 107;
        list.get(4).dist = 2150;
        list.get(4).locs = 102;
        runAlgo(testCollector, "files/monaco.osm.gz", "target/graph-monaco",
                list, "CAR", true, "CAR", "fastest");
        assertEquals(testCollector.toString(), 0, testCollector.errors.size());
    }

    @Test
    public void testMonacoMixed()
    {
        // Additional locations are inserted because of new crossings from foot to highway paths!
        // Distance is the same.
        List<OneRun> list = createMonacoCar();
        list.get(0).locs = 101;
        list.get(1).locs = 135;
        list.get(2).locs = 110;
        list.get(3).locs = 117;
        list.get(4).locs = 106;

        runAlgo(testCollector, "files/monaco.osm.gz", "target/graph-monaco",
                list, "CAR,FOOT", false, "CAR", "shortest");
        assertEquals(testCollector.toString(), 0, testCollector.errors.size());
    }

    @Test
    public void testMonacoFoot()
    {
        List<OneRun> list = new ArrayList<OneRun>();
        list.add(new OneRun(43.730729, 7.421288, 43.727697, 7.419199, 1566, 84));
        list.add(new OneRun(43.727687, 7.418737, 43.74958, 7.436566, 3435, 123));
        list.add(new OneRun(43.728677, 7.41016, 43.739213, 7.427806, 2085, 89));
        list.add(new OneRun(43.733802, 7.413433, 43.739662, 7.424355, 1421, 78));
        runAlgo(testCollector, "files/monaco.osm.gz", "target/graph-monaco",
                list, "FOOT", true, "FOOT", "shortest");
        assertEquals(testCollector.toString(), 0, testCollector.errors.size());
    }

    @Test
    public void testMonacoBike()
    {
        List<OneRun> list = new ArrayList<OneRun>();
        list.add(new OneRun(43.730864, 7.420771, 43.727687, 7.418737, 1641, 76));
        list.add(new OneRun(43.727687, 7.418737, 43.74958, 7.436566, 3580, 133));
        list.add(new OneRun(43.728677, 7.41016, 43.739213, 7.427806, 2323, 100));
        list.add(new OneRun(43.733802, 7.413433, 43.739662, 7.424355, 1434, 80));
        runAlgo(testCollector, "files/monaco.osm.gz", "target/graph-monaco",
                list, "BIKE", true, "BIKE", "shortest");
        assertEquals(testCollector.toString(), 0, testCollector.errors.size());
    }

    @Test
    public void testMonacoMountainBike()
    {
        List<OneRun> list = new ArrayList<OneRun>();
        list.add(new OneRun(43.730864, 7.420771, 43.727687, 7.418737, 2332, 102));
        list.add(new OneRun(43.727687, 7.418737, 43.74958, 7.436566, 3588, 135));
        list.add(new OneRun(43.728677, 7.41016, 43.739213, 7.427806, 2323, 100));
        list.add(new OneRun(43.733802, 7.413433, 43.739662, 7.424355, 1475, 80));
        runAlgo(testCollector, "files/monaco.osm.gz", "target/graph-monaco",
                list, "MTB", true, "MTB", "fastest");
        assertEquals(testCollector.toString(), 0, testCollector.errors.size());

        runAlgo(testCollector, "files/monaco.osm.gz", "target/graph-monaco",
                list, "BIKE,MTB,RACINGBIKE", false, "MTB", "fastest");
        assertEquals(testCollector.toString(), 0, testCollector.errors.size());
    }

    @Test
    public void testMonacoRacingBike()
    {
        List<OneRun> list = new ArrayList<OneRun>();
        list.add(new OneRun(43.730864, 7.420771, 43.727687, 7.418737, 2597, 110));
        list.add(new OneRun(43.727687, 7.418737, 43.74958, 7.436566, 3615, 155));
        list.add(new OneRun(43.728677, 7.41016, 43.739213, 7.427806, 2323, 100));
        list.add(new OneRun(43.733802, 7.413433, 43.739662, 7.424355, 1490, 74));
        runAlgo(testCollector, "files/monaco.osm.gz", "target/graph-monaco",
                list, "RACINGBIKE", true, "RACINGBIKE", "fastest");
        assertEquals(testCollector.toString(), 0, testCollector.errors.size());

        runAlgo(testCollector, "files/monaco.osm.gz", "target/graph-monaco",
                list, "CAR,BIKE,RACINGBIKE", false, "RACINGBIKE", "fastest");
        assertEquals(testCollector.toString(), 0, testCollector.errors.size());
    }

    @Test
    public void testKremsBikeRelation()
    {
        List<OneRun> list = new ArrayList<OneRun>();
        list.add(new OneRun(48.409523, 15.602394, 48.375466, 15.72916, 12489, 141));
        list.add(new OneRun(48.410061, 15.63951, 48.411386, 15.604899, 3077, 75));
        list.add(new OneRun(48.412294, 15.62007, 48.398306, 15.609667, 3965, 85));

        runAlgo(testCollector, "files/krems.osm.gz", "target/graph-krems",
                list, "BIKE", true, "BIKE", "fastest");
        assertEquals(testCollector.toString(), 0, testCollector.errors.size());

        runAlgo(testCollector, "files/krems.osm.gz", "target/graph-krems",
                list, "CAR,BIKE,MTB", false, "BIKE", "fastest");
        assertEquals(testCollector.toString(), 0, testCollector.errors.size());
    }

    @Test
    public void testKremsMountainBikeRelation()
    {
        List<OneRun> list = new ArrayList<OneRun>();
        list.add(new OneRun(48.409523, 15.602394, 48.375466, 15.72916, 12479, 141));
        list.add(new OneRun(48.410061, 15.63951, 48.411386, 15.604899, 3164, 83));
        list.add(new OneRun(48.412294, 15.62007, 48.398306, 15.609667, 3965, 86));

        runAlgo(testCollector, "files/krems.osm.gz", "target/graph-krems",
                list, "MTB", true, "MTB", "fastest");
        assertEquals(testCollector.toString(), 0, testCollector.errors.size());

        runAlgo(testCollector, "files/krems.osm.gz", "target/graph-krems",
                list, "CAR,BIKE,MTB", false, "MTB", "fastest");
        assertEquals(testCollector.toString(), 0, testCollector.errors.size());
    }

    List<OneRun> createAndorra()
    {
        List<OneRun> list = new ArrayList<OneRun>();
        list.add(new OneRun(42.56819, 1.603231, 42.571034, 1.520662, 17710, 446));
        list.add(new OneRun(42.529176, 1.571302, 42.571034, 1.520662, 11411, 259));
        return list;
    }

    @Test
    public void testAndorra()
    {
        runAlgo(testCollector, "files/andorra.osm.gz", "target/graph-andorra",
                createAndorra(), "CAR", true, "CAR", "shortest");
        assertEquals(testCollector.toString(), 0, testCollector.errors.size());
    }

    @Test
    public void testAndorraPbf()
    {
        runAlgo(testCollector, "files/andorra.osm.pbf", "target/graph-andorra",
                createAndorra(), "CAR", true, "CAR", "shortest");
        assertEquals(testCollector.toString(), 0, testCollector.errors.size());
    }

    @Test
    public void testAndorraFoot()
    {
        List<OneRun> list = createAndorra();
        list.get(0).dist = 16354;
        list.get(0).locs = 523;
        list.get(1).dist = 12704;
        list.get(1).locs = 404;

        runAlgo(testCollector, "files/andorra.osm.gz", "target/graph-andorra",
                list, "FOOT", true, "FOOT", "shortest");
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
        list.add(new OneRun(-20.4, -54.6, -20.6, -54.54, 25515, 253));
        list.add(new OneRun(-20.43, -54.54, -20.537, -54.674, 18009, 234));
        runAlgo(testCollector, "files/campo-grande.osm.gz", "target/graph-campo-grande", list,
                "CAR", false, "CAR", "shortest");
        assertEquals(testCollector.toString(), 0, testCollector.errors.size());
    }

    void runAlgo( TestAlgoCollector testCollector, String osmFile,
            String graphFile, List<OneRun> forEveryAlgo, String importVehicles,
            boolean ch, String vehicle, String weightCalcStr )
    {
        AlgorithmPreparation tmpPrepare = null;
        OneRun tmpOneRun = null;
        try
        {
            Helper.removeDir(new File(graphFile));
            GraphHopper hopper = new GraphHopper().
                    setInMemory(true).
                    setOSMFile(osmFile).
                    disableCHShortcuts().
                    setGraphHopperLocation(graphFile).
                    setEncodingManager(new EncodingManager(importVehicles)).
                    importOrLoad();

            FlagEncoder encoder = hopper.getEncodingManager().getEncoder(vehicle);
            Weighting weighting = new ShortestWeighting();
            if ("fastest".equalsIgnoreCase(weightCalcStr))
                weighting = new FastestWeighting(encoder);

            Collection<Entry<AlgorithmPreparation, LocationIndex>> prepares = RoutingAlgorithmSpecialAreaTests.
                    createAlgos(hopper.getGraph(), hopper.getLocationIndex(), encoder, ch, weighting, hopper.getEncodingManager());
            EdgeFilter edgeFilter = new DefaultEdgeFilter(encoder);
            for (Entry<AlgorithmPreparation, LocationIndex> entry : prepares)
            {
                tmpPrepare = entry.getKey();
                LocationIndex idx = entry.getValue();
                for (OneRun oneRun : forEveryAlgo)
                {
                    tmpOneRun = oneRun;
                    QueryResult from = idx.findClosest(oneRun.fromLat, oneRun.fromLon, edgeFilter);
                    QueryResult to = idx.findClosest(oneRun.toLat, oneRun.toLon, edgeFilter);
                    testCollector.assertDistance(tmpPrepare.createAlgo(), from, to, oneRun.dist, oneRun.locs);
                }
            }
        } catch (Exception ex)
        {
            if (tmpPrepare == null)
                throw new RuntimeException("cannot handle file " + osmFile, ex);

            throw new RuntimeException("cannot handle " + tmpPrepare.toString() + ", for " + tmpOneRun
                    + ", file " + osmFile, ex);
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
        Graph graph = new GraphBuilder(eManager).create();

        String bigFile = "10000EWD.txt.gz";
        new PrinctonReader(graph).setStream(new GZIPInputStream(PrinctonReader.class.getResourceAsStream(bigFile), 8 * (1 << 10))).read();
        Collection<Entry<AlgorithmPreparation, LocationIndex>> prepares = RoutingAlgorithmSpecialAreaTests.
                createAlgos(graph, null, encoder, false, new ShortestWeighting(), eManager);
        for (Entry<AlgorithmPreparation, LocationIndex> entry : prepares)
        {
            AlgorithmPreparation prepare = entry.getKey();
            StopWatch sw = new StopWatch();
            for (int i = 0; i < N; i++)
            {
                int node1 = Math.abs(rand.nextInt(graph.getNodes()));
                int node2 = Math.abs(rand.nextInt(graph.getNodes()));
                RoutingAlgorithm d = prepare.createAlgo();
                if (i >= noJvmWarming)
                    sw.start();

                Path p = d.calcPath(node1, node2);
                // avoid jvm optimization => call p.distance
                if (i >= noJvmWarming && p.getDistance() > -1)
                    sw.stop();

                // System.out.println("#" + i + " " + name + ":" + sw.getSeconds() + " " + p.nodes());
            }

            float perRun = sw.stop().getSeconds() / ((float) (N - noJvmWarming));
            System.out.println("# " + getClass().getSimpleName() + " " + prepare.createAlgo().getName()
                    + ":" + sw.stop().getSeconds() + ", per run:" + perRun);
            assertTrue("speed to low!? " + perRun + " per run", perRun < 0.08);
        }
    }

    @Test
    public void testMonacoParallel() throws IOException
    {
        System.out.println("testMonacoParallel takes a bit time...");
        String graphFile = "target/graph-monaco";
        Helper.removeDir(new File(graphFile));
        final EncodingManager encodingManager = new EncodingManager("CAR");
        GraphHopper hopper = new GraphHopper().setInMemory(true).setEncodingManager(encodingManager).
                disableCHShortcuts().
                setOSMFile("files/monaco.osm.gz").setGraphHopperLocation(graphFile).
                importOrLoad();
        final Graph g = hopper.getGraph();
        final LocationIndex idx = hopper.getLocationIndex();
        final List<OneRun> instances = createMonacoCar();
        List<Thread> threads = new ArrayList<Thread>();
        final AtomicInteger integ = new AtomicInteger(0);
        int MAX = 100;
        FlagEncoder carEncoder = encodingManager.getEncoder("CAR");

        // testing if algorithms are independent. should be. so test only two algorithms. 
        // also the preparing is too costly to be called for every thread
        int algosLength = 2;
        Weighting weighting = new ShortestWeighting();
        final EdgeFilter filter = new DefaultEdgeFilter(carEncoder);
        for (int no = 0; no < MAX; no++)
        {
            for (int instanceNo = 0; instanceNo < instances.size(); instanceNo++)
            {
                RoutingAlgorithm[] algos = new RoutingAlgorithm[]
                {
                    new AStar(g, carEncoder, weighting),
                    new DijkstraBidirectionRef(g, carEncoder, weighting)
                };
                for (final RoutingAlgorithm algo : algos)
                {
                    // an algorithm is not thread safe! reuse via clear() is ONLY appropriated if used from same thread!
                    final int instanceIndex = instanceNo;
                    Thread t = new Thread()
                    {
                        @Override
                        public void run()
                        {
                            OneRun oneRun = instances.get(instanceIndex);
                            QueryResult from = idx.findClosest(oneRun.fromLat, oneRun.fromLon, filter);
                            QueryResult to = idx.findClosest(oneRun.toLat, oneRun.toLon, filter);
                            testCollector.assertDistance(algo, from, to, oneRun.dist, oneRun.locs);
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
    }

    static class OneRun
    {
        double fromLat, fromLon;
        double toLat, toLon;
        double dist;
        int locs;

        public OneRun( double fromLat, double fromLon, double toLat, double toLon, double dist, int locs )
        {
            this.fromLat = fromLat;
            this.fromLon = fromLon;
            this.toLat = toLat;
            this.toLon = toLon;
            this.dist = dist;
            this.locs = locs;
        }

        @Override
        public String toString()
        {
            return fromLat + "," + fromLon + " -> " + toLat + "," + toLon + " with dist " + dist + " and locs " + locs;
        }
    }
}
