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
import de.jetsli.graph.util.StopWatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Peter Karich
 */
public class RoutingAlgorithmIntegrationTests {

    private final Graph unterfrankenGraph;

    public RoutingAlgorithmIntegrationTests(Graph graph) {
        this.unterfrankenGraph = graph;
    }

    public void start() {
        TestAlgoCollector testCollector = new TestAlgoCollector();
        RoutingAlgorithm[] algos = createAlgos(unterfrankenGraph);
        for (RoutingAlgorithm algo : algos) {
            int failed = testCollector.list.size();
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

            // without deleting subnetwork the following would produce empty paths!
            testCollector.assertDistance(algo, 773352, 858026, 47.936, 696);
            testCollector.assertDistance(algo, 696295, 773352, 69.520, 1288);

            System.out.println("unterfranken " + algo.getClass().getSimpleName()
                    + ": " + (testCollector.list.size() - failed) + " failed");
        }

        if (testCollector.list.size() > 0) {
            System.out.println("\n-------------------------------\n");
            System.out.println(testCollector);
        } else
            System.out.println("SUCCESS!");
    }

    public static RoutingAlgorithm[] createAlgos(Graph g) {
        return new RoutingAlgorithm[]{new AStar(g), new DijkstraBidirectionRef(g),
                    new DijkstraBidirection(g), new DijkstraSimple(g)};
    }

    public static class TestAlgoCollector {

        public List<String> list = new ArrayList<String>();

        public TestAlgoCollector assertNull(RoutingAlgorithm algo, int from, int to) {
            Path p = algo.clear().calcPath(from, to);
            if (p != null)
                list.add(algo.getClass().getSimpleName() + " returns value where null is expected. "
                        + "from:" + from + ", to:" + to);
            return this;
        }

        public TestAlgoCollector assertDistance(RoutingAlgorithm algo, int from, int to, double distance, int locations) {
            Path p = algo.clear().calcPath(from, to);
            if (p == null) {
                list.add(algo.getClass().getSimpleName() + " returns no path for "
                        + "from:" + from + ", to:" + to);
                return this;
            } else if (Math.abs(p.distance() - distance) > 1e-2)
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

        @Override
        public String toString() {
            String str = "";
            str += "FOUND " + list.size() + " ERRORS.\n";
            for (String s : list) {
                str += s + ".\n";
            }
            return str;
        }
    }
    private Logger logger = LoggerFactory.getLogger(getClass());

    public void runShortestPathPerf(int runs, String algoStr) throws Exception {
        Location2IDIndex index = new Location2IDQuadtree(unterfrankenGraph).prepareIndex(20000);
        double minLat = 49.484186, minLon = 8.974228;
        double maxLat = 50.541363, maxLon = 10.880356;
        RoutingAlgorithm algo;

        if ("dijkstraref".equalsIgnoreCase(algoStr))
            algo = new DijkstraBidirectionRef(unterfrankenGraph);
        else if ("dijkstrabi".equalsIgnoreCase(algoStr))
            algo = new DijkstraBidirection(unterfrankenGraph);
        else if ("dijkstra".equalsIgnoreCase(algoStr))
            algo = new DijkstraSimple(unterfrankenGraph);
        else
            algo = new AStar(unterfrankenGraph);

        logger.info("running shortest path with " + algo.getClass().getSimpleName());
        Random rand = new Random(123);
        StopWatch sw = new StopWatch();
        for (int i = 0; i < runs; i++) {
            double fromLat = rand.nextDouble() * (maxLat - minLat) + minLat;
            double fromLon = rand.nextDouble() * (maxLon - minLon) + minLon;
            int from = index.findID(fromLat, fromLon);
            double toLat = rand.nextDouble() * (maxLat - minLat) + minLat;
            double toLon = rand.nextDouble() * (maxLon - minLon) + minLon;
            int to = index.findID(toLat, toLon);
//                logger.info(i + " " + sw + " from (" + from + ")" + fromLat + ", " + fromLon + " to (" + to + ")" + toLat + ", " + toLon);
            if (from == to) {
                logger.warn("skipping i " + i + " from==to " + from);
                continue;
            }

            algo.clear();
            sw.start();
            Path p = algo.calcPath(from, to);
            sw.stop();
            if (p == null) {
                logger.warn("no route found for i=" + i + " !?" + " graph-from " + from + ", graph-to " + to);
                continue;
            }
            if (i % 20 == 0)
                logger.info(i + " " + sw.getSeconds() / (i + 1) + " secs/run (distance:"
                        + p.distance() + ",length:" + p.locations() + ")");
        }
    }
}
