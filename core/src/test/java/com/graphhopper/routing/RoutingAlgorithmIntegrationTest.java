/*
 *  Licensed to Peter Karich under one or more contributor license 
 *  agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  Peter Karich licenses this file to you under the Apache License, 
 *  Version 2.0 (the "License"); you may not use this file except 
 *  in compliance with the License. You may obtain a copy of the 
 *  License at
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
import com.graphhopper.reader.OSMReader;
import com.graphhopper.routing.util.AlgorithmPreparation;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.RoutingAlgorithmSpecialAreaTests;
import com.graphhopper.routing.util.TestAlgoCollector;
import com.graphhopper.routing.util.EdgePropertyEncoder;
import com.graphhopper.routing.util.FootFlagEncoder;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.index.Location2IDIndex;
import com.graphhopper.util.CmdArgs;
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
 *
 * @author Peter Karich
 */
public class RoutingAlgorithmIntegrationTest {

    TestAlgoCollector testCollector;

    @Before
    public void setUp() {
        testCollector = new TestAlgoCollector("integration tests");
    }

    List<OneRun> createMonacoInstances() {
        List<OneRun> list = new ArrayList<OneRun>();
        // it is not possible to cross the place du palais and there is a oneway directive:
        // list.add(new OneRun(43.727687, 7.418737, 43.730729, 7.421288, 1.532, 88));
        // but the other way (where no crossing is necessary) is possible:
        list.add(new OneRun(43.730729, 7.421288, 43.727687, 7.418737, 2542, 88));
        list.add(new OneRun(43.727687, 7.418737, 43.74958, 7.436566, 3604, 136));
        list.add(new OneRun(43.72915, 7.410572, 43.739213, 7.427806, 2365, 99));
        return list;
    }

    @Test
    public void testMonaco() {
        runAlgo(testCollector, "files/monaco.osm.gz", "target/graph-monaco",
                createMonacoInstances(), "CAR", true, new CarFlagEncoder());
        assertEquals(testCollector.toString(), 0, testCollector.errors.size());
    }

    @Test
    public void testMonacoMixed() {
        runAlgo(testCollector, "files/monaco.osm.gz", "target/graph-monaco",
                createMonacoInstances(), "CAR,FOOT", false, new CarFlagEncoder());
        assertEquals(testCollector.toString(), 0, testCollector.errors.size());
    }
//
//    @Test
//    public void testMonacoFoot() {
//        List<OneRun> list = new ArrayList<OneRun>();
//        list.add(new OneRun(43.730729, 7.421288, 43.727687, 7.418737, 1542, 88));
//        list.add(new OneRun(43.727687, 7.418737, 43.74958, 7.436566, 2604, 136));
//        list.add(new OneRun(43.72915, 7.410572, 43.739213, 7.427806, 1365, 99));
//        runAlgo(testCollector, "files/monaco.osm.gz", "target/graph-monaco",
//                list, "FOOT", true, new FootFlagEncoder());
//        assertEquals(testCollector.toString(), 0, testCollector.errors.size());
//    }

    @Test
    public void testAndorra() {
        List<OneRun> list = new ArrayList<OneRun>();
        list.add(new OneRun(42.56819, 1.603231, 42.571034, 1.520662, 21122, 699));
        list.add(new OneRun(42.529176, 1.571302, 42.571034, 1.520662, 16089, 510));
        // if we would use double for lat+lon we would get path length 16.466 instead of 16.452
        runAlgo(testCollector, "files/andorra.osm.gz", "target/graph-andorra",
                list, "CAR", true, new CarFlagEncoder());
        assertEquals(testCollector.toString(), 0, testCollector.errors.size());
    }

//    @Test
//    public void testAndorraFoot() {
//        List<OneRun> list = new ArrayList<OneRun>();
//        list.add(new OneRun(42.56819, 1.603231, 42.571034, 1.520662, 16053, 509));
//        list.add(new OneRun(42.529176, 1.571302, 42.571034, 1.520662, 12154, 367));
//        // if we would use double for lat+lon we would get path length 16.466 instead of 16.452
//        runAlgo(testCollector, "files/andorra.osm.gz", "target/graph-andorra",
//                list, "FOOT", true, new FootFlagEncoder());
//        assertEquals(testCollector.toString(), 0, testCollector.errors.size());
//    }

    @Test
    public void testCampoGrande() {
        // test not only NE quadrant of earth!

        // bzcat campo-grande.osm.bz2 
        //   | ./bin/osmosis --read-xml enableDateParsing=no file=- --bounding-box top=-20.4 left=-54.6 bottom=-20.6 right=-54.5 --write-xml file=- 
        //   | bzip2 > campo-grande.extracted.osm.bz2

        List<OneRun> list = new ArrayList<OneRun>();
        list.add(new OneRun(-20.4, -54.6, -20.6, -54.54, 25515, 253));
        list.add(new OneRun(-20.43, -54.54, -20.537, -54.674, 18020, 238));
        runAlgo(testCollector, "files/campo-grande.osm.gz", "target/graph-campo-grande", list,
                "CAR", false, new CarFlagEncoder());
        assertEquals(testCollector.toString(), 0, testCollector.errors.size());
    }

    void runAlgo(TestAlgoCollector testCollector, String osmFile,
            String graphFile, List<OneRun> forEveryAlgo, String vehicles,
            boolean ch, EdgePropertyEncoder encoder) {
        try {
            // make sure we are using the latest file format
            Helper.removeDir(new File(graphFile));
            GraphHopper hopper = new GraphHopper().
                    init(new CmdArgs().put("osmreader.osm", osmFile).
                    put("osmreader.graph-location", graphFile).
                    put("osmreader.vehicles", vehicles).
                    put("osmreader.dataaccess", "inmemory")).
                    importOrLoad();

            Graph g = hopper.graph();
            Location2IDIndex idx = hopper.index();

            Collection<AlgorithmPreparation> prepares = RoutingAlgorithmSpecialAreaTests.
                    createAlgos(g, encoder, ch);
            for (AlgorithmPreparation prepare : prepares) {
                for (OneRun or : forEveryAlgo) {
                    int from = idx.findID(or.fromLat, or.fromLon);
                    int to = idx.findID(or.toLat, or.toLon);
                    testCollector.assertDistance(prepare.createAlgo(), from, to, or.dist, or.locs);
                }
            }
        } catch (Exception ex) {
            throw new RuntimeException("cannot handle osm file " + osmFile, ex);
        } finally {
            Helper.removeDir(new File(graphFile));
        }
    }

    @Test
    public void testMonacoParallel() throws IOException {
        System.out.println("testMonacoParallel takes a bit time...");
        String graphFile = "target/graph-monaco";
        Helper.removeDir(new File(graphFile));
        GraphHopper hopper = new GraphHopper().
                init(new CmdArgs().put("osmreader.osm", "files/monaco.osm.gz").
                put("osmreader.graph-location", graphFile).
                put("osmreader.dataaccess", "inmemory")).
                importOrLoad();
        final Graph g = hopper.graph();
        final Location2IDIndex idx = hopper.index();
        final List<OneRun> instances = createMonacoInstances();
        List<Thread> threads = new ArrayList<Thread>();
        final AtomicInteger integ = new AtomicInteger(0);
        int MAX = 100;
        EdgePropertyEncoder carEncoder = new CarFlagEncoder();

        // testing if algorithms are independent. should be. so test only two algorithms. 
        // also the preparing is too costly to be called for every thread
        int algosLength = 2;
        for (int no = 0; no < MAX; no++) {
            for (int instanceNo = 0; instanceNo < instances.size(); instanceNo++) {
                RoutingAlgorithm[] algos = new RoutingAlgorithm[]{new AStar(g, carEncoder),
                    new DijkstraBidirectionRef(g, carEncoder)};
                for (final RoutingAlgorithm algo : algos) {
                    // an algorithm is not thread safe! reuse via clear() is ONLY appropriated if used from same thread!
                    final int instanceIndex = instanceNo;
                    Thread t = new Thread() {
                        @Override public void run() {
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

        for (Thread t : threads) {
            try {
                t.join();
            } catch (InterruptedException ex) {
                throw new RuntimeException(ex);
            }
        }

        assertEquals(MAX * algosLength * instances.size(), integ.get());
        assertEquals(testCollector.toString(), 0, testCollector.errors.size());
    }

    class OneRun {

        double fromLat, fromLon;
        double toLat, toLon;
        double dist;
        int locs;

        public OneRun(double fromLat, double fromLon, double toLat, double toLon, double dist, int locs) {
            this.fromLat = fromLat;
            this.fromLon = fromLon;
            this.toLat = toLat;
            this.toLon = toLon;
            this.dist = dist;
            this.locs = locs;
        }
    }
}
