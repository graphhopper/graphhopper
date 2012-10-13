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
package com.graphhopper.routing.util;

import com.graphhopper.routing.AStar;
import com.graphhopper.routing.AStarBidirection;
import com.graphhopper.routing.DijkstraBidirection;
import com.graphhopper.routing.DijkstraBidirectionRef;
import com.graphhopper.routing.DijkstraSimple;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.PathBidirRef;
import com.graphhopper.routing.Path4Level;
import com.graphhopper.routing.RoutingAlgorithm;
import com.graphhopper.storage.Directory;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphStorage;
import com.graphhopper.storage.Location2IDIndex;
import com.graphhopper.storage.Location2IDQuadtree;
import com.graphhopper.storage.LevelGraph;
import com.graphhopper.storage.LevelGraphStorage;
import com.graphhopper.storage.RAMDirectory;
import com.graphhopper.util.DistanceCalc;
import com.graphhopper.util.StopWatch;
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
        // Location2IDPreciseIndex index = new Location2IDPreciseIndex(unterfrankenGraph, dir);        
        // Location2IDFullIndex index = new Location2IDFullIndex(graph);
        if (!index.loadExisting()) {
            index.prepareIndex(200000);
            index.flush();
        }
        idx = index;
        logger.info(index.getClass().getSimpleName() + " index. Size:" + idx.calcMemInMB() + " MB, took:" + sw.stop().getSeconds());
    }

    public void start() {
        testIndex();
        testAlgos();
    }

    void testAlgos() {

        TestAlgoCollector testCollector = new TestAlgoCollector();
        RoutingAlgorithm[] algos = createAlgos(unterfrankenGraph);
        for (RoutingAlgorithm algo : algos) {
            int failed = testCollector.list.size();
            testCollector.assertDistance(algo, idx.findID(50.0315, 10.5105), idx.findID(50.0303, 10.5070), 0.5613, 20);
            testCollector.assertDistance(algo, idx.findID(49.51451, 9.967346), idx.findID(50.2920, 10.4650), 107.4917, 1673);
            testCollector.assertDistance(algo, idx.findID(50.0780, 9.1570), idx.findID(49.5860, 9.9750), 93.5559, 1278);
            testCollector.assertDistance(algo, idx.findID(50.2800, 9.7190), idx.findID(49.8960, 10.3890), 77.73985, 1217);
            testCollector.assertDistance(algo, idx.findID(49.8020, 9.2470), idx.findID(50.4940, 10.1970), 125.5666, 2135);
            testCollector.assertDistance(algo, idx.findID(49.7260, 9.2550), idx.findID(50.4140, 10.2750), 132.1662, 2138);
            testCollector.assertDistance(algo, idx.findID(50.1100, 10.7530), idx.findID(49.6500, 10.3410), 73.05989, 1229);

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
                    new DijkstraSimple(g),
//                    createLevelDijkstraBi((LevelGraph) g)
                };
    }

    static RoutingAlgorithm createLevelDijkstraBi(LevelGraph g) {
        return new PrepareContractionHierarchies(g).createDijkstraBi();
//        DijkstraBidirectionRef dijkstraBi = new DijkstraBidirectionRef(g) {
//            @Override public String toString() {
//                return "DijkstraBidirectionRef|Shortcut|" + weightCalc;
//            }
//
//            @Override protected PathBidirRef createPath() {
//                // expand skipped nodes
//                return new Path4Level(graph, weightCalc);
//            }
//        };
//        dijkstraBi.setEdgeFilter(new EdgeLevelFilter(g));
//        return dijkstraBi;
    }

    static RoutingAlgorithm createLevelAStarBi(LevelGraph g) {
        AStarBidirection astar = new AStarBidirection(g) {
            @Override
            public String toString() {
                return "AStarBidirection|Shortcut|" + weightCalc;
            }

            @Override protected PathBidirRef createPath() {
                // expand skipped nodes
                return new Path4Level(graph, weightCalc);
            }
        }.setApproximation(true);
        astar.setEdgeFilter(new EdgeLevelFilter(g));
        return astar;
    }
    private Logger logger = LoggerFactory.getLogger(getClass());

    public void runShortestPathPerf(int runs, String algoStr) throws Exception {
        double minLat = 49.484186, minLon = 8.974228;
        double maxLat = 50.541363, maxLon = 10.880356;
        RoutingAlgorithm algo;
        if ("dijkstrabi".equalsIgnoreCase(algoStr))
            algo = new DijkstraBidirectionRef(unterfrankenGraph);
        else if ("dijkstraNative".equalsIgnoreCase(algoStr))
            algo = new DijkstraBidirection(unterfrankenGraph);
        else if ("dijkstra".equalsIgnoreCase(algoStr))
            algo = new DijkstraSimple(unterfrankenGraph);
        else if ("astarbi".equalsIgnoreCase(algoStr))
            algo = new AStarBidirection(unterfrankenGraph).setApproximation(true);
        else
            algo = new AStar(unterfrankenGraph);

        if (unterfrankenGraph instanceof LevelGraph) {
            if (algo instanceof DijkstraBidirectionRef)
                algo = createLevelDijkstraBi((LevelGraph) unterfrankenGraph);
            else if (algo instanceof AStarBidirection)
                algo = createLevelAStarBi((LevelGraph) unterfrankenGraph);
            else
                // level graph accepts all algorithms but normally we want to use an optimized one
                throw new IllegalStateException("algo which supports levelgraph not found " + algo);
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

    void testIndex() {
        // query outside        
        double qLat = 49.4000;
        double qLon = 9.9690;
        int id = idx.findID(qLat, qLon);
        double foundLat = unterfrankenGraph.getLatitude(id);
        double foundLon = unterfrankenGraph.getLongitude(id);
        double dist = new DistanceCalc().calcDistKm(qLat, qLon, foundLat, foundLon);
        double expectedDist = 5.5892;
        if (Math.abs(dist - expectedDist) > 1e-4)
            System.out.println("ERROR in test index. queried lat,lon=" + (float) qLat + "," + (float) qLon
                    + ", but was " + (float) foundLat + "," + (float) foundLon
                    + "\n   expected distance:" + expectedDist + ", but was:" + dist);
    }
}
