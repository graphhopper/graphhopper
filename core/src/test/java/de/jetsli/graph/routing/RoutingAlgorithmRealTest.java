/*
 *  Copyright 2012 Peter Karich info@jetsli.de
 * 
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package de.jetsli.graph.routing;

import de.jetsli.graph.reader.OSMReader;
import de.jetsli.graph.reader.RoutingAlgorithmIntegrationTests;
import de.jetsli.graph.reader.TestAlgoCollector;
import de.jetsli.graph.storage.Graph;
import de.jetsli.graph.storage.Location2IDIndex;
import de.jetsli.graph.storage.Location2IDQuadtree;
import de.jetsli.graph.util.CmdArgs;
import de.jetsli.graph.util.Helper;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * @author Peter Karich
 */
public class RoutingAlgorithmRealTest {

    TestAlgoCollector testCollector;

    @Before
    public void setUp() {
        testCollector = new TestAlgoCollector();
    }
    
    List<OneRun> createMonacoInstances() {
        List<OneRun> list = new ArrayList<OneRun>();

        // it is not possible to cross the place du palais and there is a oneway directive:
        // list.add(new OneRun(43.727687, 7.418737, 43.730729, 7.421288, 1.532, 88));
        // but the other way (where no crossing is necessary) is possible:
        list.add(new OneRun(43.730729, 7.421288, 43.727687, 7.418737, 2.536, 107));
        list.add(new OneRun(43.727687, 7.418737, 43.74958, 7.436566, 3.594, 179));
        list.add(new OneRun(43.72915, 7.410572, 43.739213, 7.427806, 2.371, 128));
        return list;
    }

    @Test
    public void testMonaco() {        
        runAlgo(testCollector, "files/monaco.osm.gz", "target/graph-monaco", createMonacoInstances());
        assertEquals(testCollector.toString(), 0, testCollector.list.size());
    }

    @Test
    public void testAndorra() {
        List<OneRun> list = new ArrayList<OneRun>();
        list.add(new OneRun(42.56819, 1.603231, 42.571034, 1.520662, 19.199, 814));
        list.add(new OneRun(42.529176, 1.571302, 42.571034, 1.520662, 16.42, 603));
        runAlgo(testCollector, "files/andorra.osm.gz", "target/graph-andorra", list);

        assertEquals(testCollector.toString(), 0, testCollector.list.size());
    }

    @Test
    public void testMonacoParallel() throws IOException {
        String graphFile = "target/graph-monaco";
        Helper.deleteDir(new File(graphFile));
        Graph g = OSMReader.osm2Graph(new CmdArgs().put("osm", "files/monaco.osm.gz").put("graph", graphFile));
        final Location2IDIndex idx = new Location2IDQuadtree(g).prepareIndex(2000);
        final List<OneRun> instances = createMonacoInstances();
        List<Thread> threads = new ArrayList<Thread>();
        final AtomicInteger integ = new AtomicInteger(0);
        int MAX = 500;
        int algosLength = RoutingAlgorithmIntegrationTests.createAlgos(g).length;
        for (int no = 0; no < MAX; no++) {
            for (int i = 0; i < instances.size(); i++) {
                RoutingAlgorithm[] algos = RoutingAlgorithmIntegrationTests.createAlgos(g);
                for (final RoutingAlgorithm algo : algos) {
                    // not thread safe
                    // algo.clear();
                    final int tmp = i;
                    Thread t = new Thread() {
                        @Override public void run() {
                            OneRun o = instances.get(tmp);
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
        assertEquals(testCollector.toString(), 0, testCollector.list.size());
    }

    void runAlgo(TestAlgoCollector testCollector, String osmFile,
            String graphFile, List<OneRun> forEveryAlgo) {
        try {
            // make sure we are using the latest file format
            Helper.deleteDir(new File(graphFile));
            Graph g = OSMReader.osm2Graph(new CmdArgs().put("osm", osmFile).put("graph", graphFile));
            // System.out.println(osmFile + " - all locations " + g.getNodes());
            Location2IDIndex idx = new Location2IDQuadtree(g).prepareIndex(2000);
            RoutingAlgorithm[] algos = RoutingAlgorithmIntegrationTests.createAlgos(g);
            for (RoutingAlgorithm algo : algos) {
                int failed = testCollector.list.size();

                for (OneRun or : forEveryAlgo) {
                    int from = idx.findID(or.fromLat, or.fromLon);
                    int to = idx.findID(or.toLat, or.toLon);
                    testCollector.assertDistance(algo, from, to, or.dist, or.locs);
                }

//                System.out.println(osmFile + " " + algo.getClass().getSimpleName()
//                        + ": " + (testCollector.list.size() - failed) + " failed");
            }
        } catch (Exception ex) {
            throw new RuntimeException("cannot handle osm file " + osmFile, ex);
        } finally {
            Helper.deleteDir(new File(graphFile));
        }
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
