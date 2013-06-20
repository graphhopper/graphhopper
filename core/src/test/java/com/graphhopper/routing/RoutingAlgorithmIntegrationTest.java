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

import com.graphhopper.GraphHopper;
import com.graphhopper.routing.util.*;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.index.Location2IDIndex;
import com.graphhopper.util.Helper;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

/**
 * Try algorithms, indices and graph storages with real data
 * <p/>
 * @author Peter Karich
 */
public class RoutingAlgorithmIntegrationTest
{
    TestAlgoCollector testCollector;

    @Before
    public void setUp()
    {
        testCollector = new TestAlgoCollector("integration tests");
    }

    List<OneRun> createMonacoCar()
    {
        List<OneRun> list = new ArrayList<OneRun>();
        list.add(new OneRun(43.730729, 7.42135, 43.72775, 7.418737, 2524, 87));
        list.add(new OneRun(43.727687, 7.418737, 43.74958, 7.436566, 3605, 126));
        list.add(new OneRun(43.72915, 7.410572, 43.739213, 7.4277, 2490, 102));
        list.add(new OneRun(43.733709, 7.41354, 43.739662, 7.424355, 2303, 108));
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
    public void testMonacoFastest()
    {
        List<OneRun> list = createMonacoCar();
        list.get(3).dist = 2353;
        list.get(3).locs = 110;
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
        list.get(0).locs = 97;
        list.get(1).locs = 135;
        list.get(3).locs = 117;

        // 43.72915, 7.410572, 43.739213, 7.4277 -> cannot route
        // 43.72915, 7.410572, 43.739213, 7.4278 -> all ok
        runAlgo(testCollector, "files/monaco.osm.gz", "target/graph-monaco",
                list, "CAR,FOOT", false, "CAR", "shortest");
        assertEquals(testCollector.toString(), 0, testCollector.errors.size());
    }

    @Test
    public void testMonacoFoot()
    {
        List<OneRun> list = new ArrayList<OneRun>();
        list.add(new OneRun(43.730729, 7.421288, 43.727687, 7.418737, 1536, 80));
        list.add(new OneRun(43.727687, 7.418737, 43.74958, 7.436566, 3455, 123));
        list.add(new OneRun(43.72915, 7.410572, 43.739213, 7.427806, 2018, 89));
        list.add(new OneRun(43.733709, 7.41354, 43.739662, 7.424355, 1434, 80));
        runAlgo(testCollector, "files/monaco.osm.gz", "target/graph-monaco",
                list, "FOOT", true, "FOOT", "shortest");
        assertEquals(testCollector.toString(), 0, testCollector.errors.size());
    }

    @Test
    public void testMonacoBike()
    {
        List<OneRun> list = new ArrayList<OneRun>();
        list.add(new OneRun(43.730729, 7.421288, 43.727687, 7.418737, 2543, 86));
        list.add(new OneRun(43.727687, 7.418737, 43.74958, 7.436566, 3604, 125));
        list.add(new OneRun(43.72915, 7.410572, 43.739213, 7.427806, 2490, 102));
        list.add(new OneRun(43.733709, 7.41354, 43.739662, 7.424355, 2303, 108));
        runAlgo(testCollector, "files/monaco.osm.gz", "target/graph-monaco",
                list, "BIKE", true, "BIKE", "shortest");
        assertEquals(testCollector.toString(), 0, testCollector.errors.size());
    }

    List<OneRun> createAndorra()
    {
        List<OneRun> list = new ArrayList<OneRun>();
        list.add(new OneRun(42.56819, 1.603231, 42.571034, 1.520662, 17345, 435));
        list.add(new OneRun(42.529176, 1.571302, 42.571034, 1.520662, 11093, 250));
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
        list.get(0).dist = 16023;
        list.get(0).locs = 514;
        list.get(1).dist = 12410;
        list.get(1).locs = 391;
        // if we would use double for lat+lon we would get path length 16.466 instead of 16.452
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
        list.add(new OneRun(-20.43, -54.54, -20.537, -54.674, 18020, 238));
        runAlgo(testCollector, "files/campo-grande.osm.gz", "target/graph-campo-grande", list,
                "CAR", false, "CAR", "shortest");
        assertEquals(testCollector.toString(), 0, testCollector.errors.size());
    }

    void runAlgo( TestAlgoCollector testCollector, String osmFile,
            String graphFile, List<OneRun> forEveryAlgo, String importVehicles,
            boolean ch, String vehicle, String weightCalcStr )
    {
        try
        {
            // make sure we are using the latest file format
            Helper.removeDir(new File(graphFile));
            GraphHopper hopper = new GraphHopper().setInMemory(true, true).
                    osmFile(osmFile).graphHopperLocation(graphFile).
                    encodingManager(new EncodingManager(importVehicles)).
                    importOrLoad();

            Graph g = hopper.graph();
            Location2IDIndex idx = hopper.index();
            final AbstractFlagEncoder encoder = hopper.encodingManager().getEncoder(vehicle);
            WeightCalculation weightCalc = new ShortestCalc();
            if ("fastest".equals(weightCalcStr))
            {
                weightCalc = new FastestCalc(encoder);
            }

            Collection<AlgorithmPreparation> prepares = RoutingAlgorithmSpecialAreaTests.
                    createAlgos(g, encoder, ch, weightCalc, hopper.encodingManager());
            EdgeFilter edgeFilter = new DefaultEdgeFilter(encoder);
            for (AlgorithmPreparation prepare : prepares)
            {
                for (OneRun or : forEveryAlgo)
                {
                    int from = idx.findClosest(or.fromLat, or.fromLon, edgeFilter).closestNode();
                    int to = idx.findClosest(or.toLat, or.toLon, edgeFilter).closestNode();
                    testCollector.assertDistance(prepare.createAlgo(), from, to, or.dist, or.locs);
                }
            }
        } catch (Exception ex)
        {
            throw new RuntimeException("cannot handle osm file " + osmFile, ex);
        } finally
        {
            Helper.removeDir(new File(graphFile));
        }
    }

    @Test
    public void testMonacoParallel() throws IOException
    {
        System.out.println("testMonacoParallel takes a bit time...");
        String graphFile = "target/graph-monaco";
        Helper.removeDir(new File(graphFile));
        final EncodingManager encodingManager = new EncodingManager("CAR");
        GraphHopper hopper = new GraphHopper().setInMemory(true, true).
                encodingManager(encodingManager).
                osmFile("files/monaco.osm.gz").graphHopperLocation(graphFile).
                importOrLoad();
        final Graph g = hopper.graph();
        final Location2IDIndex idx = hopper.index();
        final List<OneRun> instances = createMonacoCar();
        List<Thread> threads = new ArrayList<Thread>();
        final AtomicInteger integ = new AtomicInteger(0);
        int MAX = 100;
        FlagEncoder carEncoder = encodingManager.getEncoder("CAR");

        // testing if algorithms are independent. should be. so test only two algorithms. 
        // also the preparing is too costly to be called for every thread
        int algosLength = 2;
        for (int no = 0; no < MAX; no++)
        {
            for (int instanceNo = 0; instanceNo < instances.size(); instanceNo++)
            {
                RoutingAlgorithm[] algos = new RoutingAlgorithm[]
                {
                    new AStar(g, carEncoder),
                    new DijkstraBidirectionRef(g, carEncoder)
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
                            OneRun o = instances.get(instanceIndex);
                            int from = idx.findID(o.fromLat, o.fromLon);
                            int to = idx.findID(o.toLat, o.toLon);
                            testCollector.assertDistance(algo, from, to, o.dist, o.locs);
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

    class OneRun
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
    }
}
