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

import com.graphhopper.reader.OSMReader;
import com.graphhopper.routing.ch.PrepareContractionHierarchies;
import com.graphhopper.routing.AStar;
import com.graphhopper.routing.AStarBidirection;
import com.graphhopper.routing.DijkstraBidirection;
import com.graphhopper.routing.DijkstraBidirectionRef;
import com.graphhopper.routing.DijkstraSimple;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.RoutingAlgorithm;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.Location2IDIndex;
import com.graphhopper.storage.LevelGraph;
import com.graphhopper.storage.LevelGraphStorage;
import com.graphhopper.storage.RAMDirectory;
import com.graphhopper.util.DistanceCalc;
import com.graphhopper.util.StopWatch;
import com.graphhopper.util.shapes.BBox;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Integration tests for one bigger area - at the moment Unterfranken (Germany).
 *
 * @author Peter Karich
 */
public class RoutingAlgorithmSpecialAreaTests {

    private final Graph unterfrankenGraph;
    private final Location2IDIndex idx;

    public RoutingAlgorithmSpecialAreaTests(OSMReader reader) {
        this.unterfrankenGraph = reader.getGraph();
        StopWatch sw = new StopWatch().start();
        idx = reader.getLocation2IDIndex();
        logger.info(idx.getClass().getSimpleName() + " index. Size:" + idx.calcMemInMB() + " MB, took:" + sw.stop().getSeconds());
    }

    public void start() {
        testIndex();
        testAlgos();
    }

    void testAlgos() {
        if (unterfrankenGraph instanceof LevelGraph)
            throw new IllegalStateException("run testAlgos only with a none-LevelGraph. Use osmreader.levelgraph=false");

        TestAlgoCollector testCollector = new TestAlgoCollector();
        RoutingAlgorithm[] algos = createAlgos(unterfrankenGraph);
        for (RoutingAlgorithm algo : algos) {
            int failed = testCollector.list.size();
            testCollector.assertDistance(algo, idx.findID(50.0315, 10.5105), idx.findID(50.0303, 10.5070), 561.3, 20);
            testCollector.assertDistance(algo, idx.findID(49.51451, 9.967346), idx.findID(50.2920, 10.4650), 107491.7, 1673);
            testCollector.assertDistance(algo, idx.findID(50.0780, 9.1570), idx.findID(49.5860, 9.9750), 93555.9, 1278);
            testCollector.assertDistance(algo, idx.findID(50.2800, 9.7190), idx.findID(49.8960, 10.3890), 77739.85, 1217);
            testCollector.assertDistance(algo, idx.findID(49.8020, 9.2470), idx.findID(50.4940, 10.1970), 125566.6, 2135);
            //different id2location init order: testCollector.assertDistance(algo, idx.findID(49.7260, 9.2550), idx.findID(50.4140, 10.2750), 130815.9, 2115);
            testCollector.assertDistance(algo, idx.findID(49.7260, 9.2550), idx.findID(50.4140, 10.2750), 132166.156, 2138);
            testCollector.assertDistance(algo, idx.findID(50.1100, 10.7530), idx.findID(49.6500, 10.3410), 73059.89, 1229);

            System.out.println("unterfranken " + algo + ": " + (testCollector.list.size() - failed) + " failed");
        }

        if (testCollector.list.size() > 0) {
            System.out.println("\n-------------------------------\n");
            System.out.println(testCollector);
        } else
            System.out.println("SUCCESS!");
    }

    public static RoutingAlgorithm[] createAlgos(final Graph g) {
        LevelGraph graphTowerNodesSC = (LevelGraphStorage) g.copyTo(new LevelGraphStorage(new RAMDirectory()).createNew(10));
        PrepareTowerNodesShortcuts prepare = new PrepareTowerNodesShortcuts().setGraph(graphTowerNodesSC);
        prepare.doWork();
        AStarBidirection astarSimpleSC = (AStarBidirection) prepare.createAStar();
        astarSimpleSC.setApproximation(false);
        // TODO preparation takes too long
//        LevelGraph graphCH = (LevelGraphStorage) g.copyTo(new LevelGraphStorage(new RAMDirectory()).createNew(10));
//        PrepareContractionHierarchies prepareCH = new PrepareContractionHierarchies().setGraph(graphCH);
//        prepareCH.doWork();
        return new RoutingAlgorithm[]{
                    new AStar(g), new AStarBidirection(g), new DijkstraBidirectionRef(g), new DijkstraBidirection(g),
                    new DijkstraSimple(g), prepare.createAlgo(), astarSimpleSC
                // , prepareCH.createAlgo()
                };
    }
    private Logger logger = LoggerFactory.getLogger(getClass());

    public void runShortestPathPerf(int runs, RoutingAlgorithm algo) throws Exception {
        BBox bbox = unterfrankenGraph.getBounds();
        double minLat = bbox.minLat, minLon = bbox.minLon;
        double maxLat = bbox.maxLat, maxLon = bbox.maxLon;
        if (unterfrankenGraph instanceof LevelGraph) {
            if (algo instanceof DijkstraBidirectionRef)
                algo = new PrepareContractionHierarchies().setGraph(unterfrankenGraph).createAlgo();
//                algo = new PrepareTowerNodesShortcuts().setGraph(unterfrankenGraph).createAlgo();
            else if (algo instanceof AStarBidirection)
                algo = new PrepareTowerNodesShortcuts().setGraph(unterfrankenGraph).createAStar();
            else
                // level graph accepts all algorithms but normally we want to use an optimized one
                throw new IllegalStateException("algorithm which boosts query time for levelgraph not found " + algo);
            logger.info("[experimental] using shortcuts with " + algo);
        } else
            logger.info("running " + algo);

        Random rand = new Random(123);
        StopWatch sw = new StopWatch();

        // System.out.println("cap:" + ((GraphStorage) unterfrankenGraph).capacity());
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
            if (!p.found()) {
                // there are still paths not found cause of oneway motorways => only routable in one direction
                // e.g. unterfrankenGraph.getLatitude(798809) + "," + unterfrankenGraph.getLongitude(798809)
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
        double dist = new DistanceCalc().calcDist(qLat, qLon, foundLat, foundLon);
        double expectedDist = 5589.2;
        if (Math.abs(dist - expectedDist) > .1)
            System.out.println("ERROR in test index. queried lat,lon=" + (float) qLat + "," + (float) qLon
                    + ", but was " + (float) foundLat + "," + (float) foundLon
                    + "\n   expected distance:" + expectedDist + ", but was:" + dist);
    }
}
