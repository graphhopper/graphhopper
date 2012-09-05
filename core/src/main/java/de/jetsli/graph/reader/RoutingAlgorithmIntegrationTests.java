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
import de.jetsli.graph.routing.PathWrapperPrio;
import de.jetsli.graph.routing.PathWrapperRef;
import de.jetsli.graph.routing.RoutingAlgorithm;
import de.jetsli.graph.storage.EdgePrioFilter;
import de.jetsli.graph.storage.Graph;
import de.jetsli.graph.storage.Location2IDIndex;
import de.jetsli.graph.storage.Location2IDQuadtree;
import de.jetsli.graph.storage.PriorityGraph;
import de.jetsli.graph.util.EdgeIterator;
import de.jetsli.graph.util.StopWatch;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Peter Karich
 */
public class RoutingAlgorithmIntegrationTests {

    private final Graph unterfrankenGraph;
    private final Location2IDIndex idx;

    public RoutingAlgorithmIntegrationTests(Graph graph) {
        this.unterfrankenGraph = graph;
        idx = new Location2IDQuadtree(unterfrankenGraph).prepareIndex(20000);
    }

    public void start() {
        TestAlgoCollector testCollector = new TestAlgoCollector();
        RoutingAlgorithm[] algos = createAlgos(unterfrankenGraph);
        for (RoutingAlgorithm algo : algos) {
            int failed = testCollector.list.size();
            testCollector.assertDistance(algo, idx.findID(50.0315, 10.5105), idx.findID(50.0303, 10.5070), 0.5613, 20);
            testCollector.assertDistance(algo, idx.findID(49.4000, 9.9690), idx.findID(50.2920, 10.4650), 113.7413, 1802);
            testCollector.assertDistance(algo, idx.findID(49.7260, 9.2550), idx.findID(50.4140, 10.2750), 132.0551, 2138);
            testCollector.assertDistance(algo, idx.findID(50.0780, 9.1570), idx.findID(49.5860, 9.9750), 95.7919, 1343);
            testCollector.assertDistance(algo, idx.findID(50.1100, 10.7530), idx.findID(49.6500, 10.3410), 72.9986, 1229);
            testCollector.assertDistance(algo, idx.findID(50.2800, 9.7190), idx.findID(49.8960, 10.3890), 77.6807, 1217);
            testCollector.assertDistance(algo, idx.findID(49.8020, 9.2470), idx.findID(50.4940, 10.1970), 125.4616, 2135);
            System.out.println("unterfranken " + algo + ": " + (testCollector.list.size() - failed) + " failed");
        }

        if (testCollector.list.size() > 0) {
            System.out.println("\n-------------------------------\n");
            System.out.println(testCollector);
        } else
            System.out.println("SUCCESS!");
    }

    static RoutingAlgorithm createPrioAlgo(Graph g) {
        g = g.clone();
        new PrepareRoutingShortcuts((PriorityGraph) g).doWork();
        DijkstraBidirectionRef dijkstraBi = new DijkstraBidirectionRef(g) {
            @Override public String toString() {
                return "DijkstraBidirectionRef|Shortcut|" + type;
            }
            //TODO NOW
//                @Override protected PathWrapperRef createPathWrapper() {
//                    // expand skipped nodes
//                    return new PathWrapperPrio((PriorityGraph) unterfrankenGraph);
//                }
        };
        dijkstraBi.setEdgeFilter(new EdgePrioFilter((PriorityGraph) g));
        return dijkstraBi;
    }

    public static RoutingAlgorithm[] createAlgos(Graph g) {
        return new RoutingAlgorithm[]{
                    new AStar(g),
                    new DijkstraBidirectionRef(g), new DijkstraBidirection(g), new DijkstraSimple(g),
//                    createPrioAlgo(g)
                };
    }
    private Logger logger = LoggerFactory.getLogger(getClass());

    public void runShortestPathPerf(int runs, String algoStr) throws Exception {
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

        // TODO more algos should support edgepriofilter to skip lengthy paths
        if (unterfrankenGraph instanceof PriorityGraph && algo instanceof DijkstraBidirectionRef) {
            algo = createPrioAlgo(unterfrankenGraph);
            logger.info("[experimental] using shortcuts with bidirectional Dijkstra (ref)");
        } else
            logger.info("running " + algo);

        Random rand = new Random(123);
        StopWatch sw = new StopWatch();

        for (int i = 0; i < runs; i++) {
            double fromLat = rand.nextDouble() * (maxLat - minLat) + minLat;
            double fromLon = rand.nextDouble() * (maxLon - minLon) + minLon;
            int from = idx.findID(fromLat, fromLon);
            double toLat = rand.nextDouble() * (maxLat - minLat) + minLat;
            double toLon = rand.nextDouble() * (maxLon - minLon) + minLon;
            int to = idx.findID(toLat, toLon);
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
                // there are still paths not found as this point unterfrankenGraph.getLatitude(798809) + "," + unterfrankenGraph.getLongitude(798809)
                // is part of a oneway motorway => only routable in one direction
                logger.warn("no route found for i=" + i + " !? "
                        + "graph-from " + from + "(" + fromLat + "," + fromLon + "), "
                        + "graph-to " + to + "(" + toLat + "," + toLon + ")");
                continue;
            }
            if (i % 20 == 0)
                logger.info(i + " " + sw.getSeconds() / (i + 1) + " secs/run (" + p + ")");
        }
    }
}
