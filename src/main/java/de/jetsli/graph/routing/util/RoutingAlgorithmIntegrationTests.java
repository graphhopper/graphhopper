/*
 *  Copyright 2012 Peter Karich 
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
package de.jetsli.graph.routing.util;

import de.jetsli.graph.routing.AStar;
import de.jetsli.graph.routing.AStarBidirection;
import de.jetsli.graph.routing.DijkstraBidirection;
import de.jetsli.graph.routing.DijkstraBidirectionRef;
import de.jetsli.graph.routing.DijkstraSimple;
import de.jetsli.graph.routing.Path;
import de.jetsli.graph.routing.PathBidirRef;
import de.jetsli.graph.routing.PathPrio;
import de.jetsli.graph.routing.RoutingAlgorithm;
import de.jetsli.graph.storage.Directory;
import de.jetsli.graph.storage.Graph;
import de.jetsli.graph.storage.GraphStorage;
import de.jetsli.graph.storage.Location2IDIndex;
import de.jetsli.graph.storage.Location2IDQuadtree;
import de.jetsli.graph.storage.PriorityGraph;
import de.jetsli.graph.storage.RAMDirectory;
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
        StopWatch sw = new StopWatch().start();
        Location2IDQuadtree index;
        if (graph instanceof GraphStorage) {
            Directory dir = ((GraphStorage) graph).getDirectory();
            index = new Location2IDQuadtree(unterfrankenGraph, dir);
            // logger.info("dir: " + dir.getLocation());
        } else
            index = new Location2IDQuadtree(unterfrankenGraph, new RAMDirectory("loc2idIndex", false));
//      Location2IDPreciseIndex index = new Location2IDPreciseIndex(unterfrankenGraph, dir);
        if (!index.loadExisting()) {
            index.prepareIndex(100000);
            index.flush();
        }
        idx = index;
        logger.info(index.getClass().getSimpleName() + " index. Size:" + idx.calcMemInMB() + " MB, took:" + sw.stop().getSeconds());
    }

    public void start() {
        TestAlgoCollector testCollector = new TestAlgoCollector();
        RoutingAlgorithm[] algos = createAlgos(unterfrankenGraph);
        for (RoutingAlgorithm algo : algos) {
            int failed = testCollector.list.size();
            testCollector.assertDistance(algo, idx.findID(50.0315, 10.5105), idx.findID(50.0303, 10.5070), 0.5613, 20);
            testCollector.assertDistance(algo, idx.findID(49.51451, 9.967346), idx.findID(50.2920, 10.4650), 107.4917, 1673);
            testCollector.assertDistance(algo, idx.findID(49.7260, 9.2550), idx.findID(50.4140, 10.2750), 132.1662, 2138);
            testCollector.assertDistance(algo, idx.findID(50.0780, 9.1570), idx.findID(49.5860, 9.9750), 93.5559, 1278);
            testCollector.assertDistance(algo, idx.findID(50.1100, 10.7530), idx.findID(49.6500, 10.3410), 73.05989, 1229);
            testCollector.assertDistance(algo, idx.findID(50.2800, 9.7190), idx.findID(49.8960, 10.3890), 77.73985, 1217);
            testCollector.assertDistance(algo, idx.findID(49.8020, 9.2470), idx.findID(50.4940, 10.1970), 125.5666, 2135);
            System.out.println("unterfranken " + algo + ": " + (testCollector.list.size() - failed) + " failed");
        }

        if (testCollector.list.size() > 0) {
            System.out.println("\n-------------------------------\n");
            System.out.println(testCollector);
        } else
            System.out.println("SUCCESS!");
    }

    public static RoutingAlgorithm[] createAlgos(Graph g) {
        return new RoutingAlgorithm[]{
                    new AStar(g),
                    new AStarBidirection(g),
                    new DijkstraBidirectionRef(g),
                    new DijkstraBidirection(g),
                    new DijkstraSimple(g), //              TODO , createPrioAlgo(g)
                };
    }

    static RoutingAlgorithm createPrioDijkstraBi(Graph g) {
        g = g.clone();
        new PrepareRoutingShortcuts((PriorityGraph) g).doWork();
        DijkstraBidirectionRef dijkstraBi = new DijkstraBidirectionRef(g) {
            @Override public String toString() {
                return "DijkstraBidirectionRef|Shortcut|" + weightCalc;
            }

            @Override protected PathBidirRef createPath() {
                // expand skipped nodes
                return new PathPrio((PriorityGraph) graph, weightCalc);
            }
        };
        dijkstraBi.setEdgeFilter(new EdgePrioFilter((PriorityGraph) g));
        return dijkstraBi;
    }

    static RoutingAlgorithm createPrioAStarBi(Graph g) {
        g = g.clone();
        new PrepareRoutingShortcuts((PriorityGraph) g).doWork();
        AStarBidirection astar = new AStarBidirection(g) {
            @Override public String toString() {
                return "AStarBidirection|Shortcut|" + weightCalc;
            }

            @Override protected PathBidirRef createPath() {
                // expand skipped nodes
                return new PathPrio((PriorityGraph) graph, weightCalc);
            }
        }.setApproximation(true);
        astar.setEdgeFilter(new EdgePrioFilter((PriorityGraph) g));
        return astar;
    }
    private Logger logger = LoggerFactory.getLogger(getClass());

    public void runShortestPathPerf(int runs, String algoStr) throws Exception {
        double minLat = 49.484186, minLon = 8.974228;
        double maxLat = 50.541363, maxLon = 10.880356;
        RoutingAlgorithm algo;
        if ("dijkstrabi".equalsIgnoreCase(algoStr))
            algo = new DijkstraBidirectionRef(unterfrankenGraph);
        else if ("dijkstranative".equalsIgnoreCase(algoStr))
            algo = new DijkstraBidirection(unterfrankenGraph);
        else if ("dijkstra".equalsIgnoreCase(algoStr))
            algo = new DijkstraSimple(unterfrankenGraph);
        else if ("astarbi".equalsIgnoreCase(algoStr))
            algo = new AStarBidirection(unterfrankenGraph).setApproximation(true);
        else
            algo = new AStar(unterfrankenGraph);

        // TODO more algos should support edgepriofilter to skip lengthy paths
        if (unterfrankenGraph instanceof PriorityGraph) {
            if (algo instanceof DijkstraBidirectionRef)
                algo = createPrioDijkstraBi(unterfrankenGraph);
            else if (algo instanceof AStarBidirection)
                algo = createPrioAStarBi(unterfrankenGraph);
            else
                // priority graph accepts all algorithms but normally we want to use an optimized one
                throw new IllegalStateException("algo which support priority graph not found " + algo);
            logger.info("[experimental] using shortcuts with " + algo);
        } else
            logger.info("running " + algo);

        Random rand = new Random(123);
        StopWatch sw = new StopWatch();

        System.out.println("cap:" + ((GraphStorage) unterfrankenGraph).capacity());
        for (int i = 0; i < runs; i++) {
            double fromLat = rand.nextDouble() * (maxLat - minLat) + minLat;
            double fromLon = rand.nextDouble() * (maxLon - minLon) + minLon;
//            sw.start();
            int from = idx.findID(fromLat, fromLon);
            double toLat = rand.nextDouble() * (maxLat - minLat) + minLat;
            double toLon = rand.nextDouble() * (maxLon - minLon) + minLon;
            int to = idx.findID(toLat, toLon);
//            sw.stop();
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
                logger.info(i + " " + sw.getSeconds() / (i + 1) + " secs/run");// (" + p + ")");
        }
    }
}
