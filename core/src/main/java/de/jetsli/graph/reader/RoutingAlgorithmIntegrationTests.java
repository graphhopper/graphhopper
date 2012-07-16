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
package de.jetsli.graph.reader;

import de.jetsli.graph.routing.AStar;
import de.jetsli.graph.routing.DijkstraBidirection;
import de.jetsli.graph.routing.DijkstraBidirectionRef;
import de.jetsli.graph.routing.DijkstraSimple;
import de.jetsli.graph.routing.Path;
import de.jetsli.graph.routing.RoutingAlgorithm;
import de.jetsli.graph.storage.Graph;
import de.jetsli.graph.storage.Location2IDIndex;
import de.jetsli.graph.storage.Location2IDQuadtree;
import de.jetsli.graph.util.CmdArgs;
import de.jetsli.graph.util.EdgeIdIterator;
import de.jetsli.graph.util.Helper;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Peter Karich
 */
public class RoutingAlgorithmIntegrationTests {

    private final Graph unterfrankenGraph;

    public RoutingAlgorithmIntegrationTests(Graph graph) {
        this.unterfrankenGraph = graph;
    }

    public void start() {
        Collector testCollector = new Collector();

        //  
        List<OneRun> list = new ArrayList<OneRun>();
        list.add(new OneRun(43.727687, 7.418737, 43.730729, 7.421288, 1.532, 88));
        list.add(new OneRun(43.727687, 7.418737, 43.74958, 7.436566, 3.448, 136));
        runAlgo(testCollector, "files/monaco.osm.gz", "target/graph-monaco", list);

        //
        list = new ArrayList<OneRun>();
        list.add(new OneRun(42.56819, 1.603231, 42.571034, 1.520662, 16.3845, 636));
        list.add(new OneRun(42.529176, 1.571302, 42.571034, 1.520662, 12.4408, 397));
        runAlgo(testCollector, "files/andorra.osm.gz", "target/graph-andorra", list);

        //
        testUnterfranken(testCollector);

        if (testCollector.list.size() > 0) {
            System.out.println("\n-------------------------------\n");
            System.out.println("FOUND " + testCollector.list.size() + " ERRORS");
            for (String s : testCollector.list) {
                System.out.println(s);
            }
        } else
            System.out.println("SUCCESS!");
    }

    private void testUnterfranken(Collector testCollector) {
        RoutingAlgorithm[] algos = createAlgos(unterfrankenGraph);
        for (RoutingAlgorithm algo : algos) {
            int failed = testCollector.list.size();
            testCollector.assertNull(algo, 773352, 858026);
            testCollector.assertNull(algo, 696295, 773352);

            testCollector.assertDistance(algo, 424236, 794975, 115.438, 2094);
            testCollector.assertDistance(algo, 331738, 111807, 121.364, 2328);
            testCollector.assertDistance(algo, 501620, 155552, 78.042, 1126);
            testCollector.assertDistance(algo, 399826, 269920, 53.053, 1041);
            testCollector.assertDistance(algo, 665211, 246823, 35.36, 710);
            testCollector.assertDistance(algo, 783718, 696695, 66.330, 1283);
            testCollector.assertDistance(algo, 811865, 641256, 113.343, 1729);
            testCollector.assertDistance(algo, 513676, 22669, 168.442, 2817);
            testCollector.assertDistance(algo, 896965, 769132, 5.983, 131);
            testCollector.assertDistance(algo, 115253, 430074, 56.564, 967);
            System.out.println("unterfranken " + algo.getClass().getSimpleName()
                    + ": " + (testCollector.list.size() - failed) + " failed");
        }
    }

    RoutingAlgorithm[] createAlgos(Graph g) {
        return new RoutingAlgorithm[]{new AStar(g), new DijkstraBidirectionRef(g),
                    new DijkstraBidirection(g), new DijkstraSimple(g)};
    }

    public static class Collector {

        List<String> list = new ArrayList<String>();

        public Collector assertNull(RoutingAlgorithm algo, int from, int to) {
            Path p = algo.clear().calcShortestPath(from, to);
            if (p != null)
                list.add(algo.getClass().getSimpleName() + " returns value where null is expected. "
                        + "from:" + from + ", to:" + to);
            return this;
        }

        public Collector assertDistance(RoutingAlgorithm algo, int from, int to, double distance, int locations) {
            Path p = algo.clear().calcShortestPath(from, to);
            if (p == null)
                list.add(algo.getClass().getSimpleName() + " returns no path for "
                        + "from:" + from + ", to:" + to);
            else if (Math.abs(p.distance() - distance) > 1e-3)
                list.add(algo.getClass().getSimpleName() + " returns path not matching the expected "
                        + "distance of " + distance + "\t Returned was " + p.distance()
                        + "\t (expected locations " + locations + ", was " + p.locations() + ") "
                        + "from:" + from + ", to:" + to);

            // Yes, there are indeed real world instances where A-B-C is identical to A-C (in meter precision).
            // And for from:501620, to:155552 the location difference of astar to bi-dijkstra gets even bigger (7!).            
            if (Math.abs(p.locations() - locations) > 7)
                list.add(algo.getClass().getSimpleName() + " returns path not matching the expected "
                        + "locations of " + locations + "\t Returned was " + p.locations()
                        + "\t (expected distance " + distance + ", was " + p.distance() + ") "
                        + "from:" + from + ", to:" + to);
            return this;
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

    void runAlgo(Collector testCollector, String osmFile, String graphFile, List<OneRun> forEveryAlgo) {
        try {
            // make sure we are using the latest file format
            Helper.deleteDir(new File(graphFile));
            Graph g = OSMReader.osm2Graph(new CmdArgs().put("osm", osmFile).put("graph", graphFile));
            System.out.println(osmFile + " - all locations " + g.getNodes());
            Location2IDIndex idx = new Location2IDQuadtree(g).prepareIndex(2000);
            RoutingAlgorithm[] algos = createAlgos(g);
            for (RoutingAlgorithm algo : algos) {
                int failed = testCollector.list.size();

                for (OneRun or : forEveryAlgo) {
                    int from = idx.findID(or.fromLat, or.fromLon);
                    int to = idx.findID(or.toLat, or.toLon);
                    testCollector.assertDistance(algo, from, to, or.dist, or.locs);
                }

                System.out.println(osmFile + " " + algo.getClass().getSimpleName()
                        + ": " + (testCollector.list.size() - failed) + " failed");
            }
        } catch (Exception ex) {
            throw new RuntimeException("cannot handle osm file " + osmFile, ex);
        } finally {
            Helper.deleteDir(new File(graphFile));
        }
    }
}
