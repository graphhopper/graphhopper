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
package com.graphhopper.routing.util;

import com.graphhopper.reader.OSMReader;
import com.graphhopper.routing.AStar;
import com.graphhopper.routing.ch.PrepareContractionHierarchies;
import com.graphhopper.routing.AStarBidirection;
import com.graphhopper.routing.DijkstraBidirection;
import com.graphhopper.routing.DijkstraBidirectionRef;
import com.graphhopper.routing.DijkstraSimple;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.RoutingAlgorithm;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.storage.Location2IDIndex;
import com.graphhopper.storage.LevelGraph;
import com.graphhopper.storage.LevelGraphStorage;
import com.graphhopper.util.DistanceCalc;
import com.graphhopper.util.StopWatch;
import com.graphhopper.util.shapes.BBox;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Integration tests for one bigger area - at the moment Unterfranken (Germany).
 *
 * @author Peter Karich
 */
public class RoutingAlgorithmSpecialAreaTests {

    private Logger logger = LoggerFactory.getLogger(getClass());
    private final Graph unterfrankenGraph;
    private final Location2IDIndex idx;

    public RoutingAlgorithmSpecialAreaTests(OSMReader reader) {
        this.unterfrankenGraph = reader.graph();
        StopWatch sw = new StopWatch().start();
        idx = reader.location2IDIndex();
        logger.info(idx.getClass().getSimpleName() + " index. Size:" + idx.calcMemInMB() + " MB, took:" + sw.stop().getSeconds());
    }

    public void start() {
        testIndex();
        testAlgos();
    }

    void testAlgos() {
        if (unterfrankenGraph instanceof LevelGraph)
            throw new IllegalStateException("run testAlgos only with a none-LevelGraph. Use osmreader.chShortcuts=false "
                    + "Or use osmreader.chShortcuts=shortest and avoid the preparation");

        TestAlgoCollector testCollector = new TestAlgoCollector();
        Collection<RoutingAlgorithm> algos = createAlgos(unterfrankenGraph, false);
        for (RoutingAlgorithm algo : algos) {
            int failed = testCollector.list.size();
            testCollector.assertDistance(algo, idx.findID(50.0315, 10.5105), idx.findID(50.0303, 10.5070), 561.3, 20);
            testCollector.assertDistance(algo, idx.findID(49.51451, 9.967346), idx.findID(50.2920, 10.4650), 107984.2, 1751);
            testCollector.assertDistance(algo, idx.findID(50.0780, 9.1570), idx.findID(49.5860, 9.9750), 92535.4, 1335);
            testCollector.assertDistance(algo, idx.findID(50.2800, 9.7190), idx.findID(49.8960, 10.3890), 77703.5, 1305);
            testCollector.assertDistance(algo, idx.findID(49.8020, 9.2470), idx.findID(50.4940, 10.1970), 125195.1, 2323);
            //different id2location init order: testCollector.assertDistance(algo, idx.findID(49.7260, 9.2550), idx.findID(50.4140, 10.2750), 130815.9, 2115);
            testCollector.assertDistance(algo, idx.findID(49.7260, 9.2550), idx.findID(50.4140, 10.2750), 131362.6, 2229);
            testCollector.assertDistance(algo, idx.findID(50.1100, 10.7530), idx.findID(49.6500, 10.3410), 73170.2, 1417);

            System.out.println("unterfranken " + algo + ": " + (testCollector.list.size() - failed) + " failed");
        }

        if (testCollector.list.size() > 0) {
            System.out.println("\n-------------------------------\n");
            System.out.println(testCollector);
        } else
            System.out.println("SUCCESS!");
    }

    public static Collection<RoutingAlgorithm> createAlgos(Graph g, boolean withCh) {
//        LevelGraph graphTowerNodesSC = (LevelGraph) g.copyTo(new GraphBuilder().levelGraphCreate());
//        PrepareTowerNodesShortcuts prepare = new PrepareTowerNodesShortcuts().graph(graphTowerNodesSC);
//        prepare.doWork();
//        AStarBidirection astarSimpleSC = (AStarBidirection) prepare.createAStar();
//        astarSimpleSC.setApproximation(false);
        List<RoutingAlgorithm> algos = new ArrayList<RoutingAlgorithm>(Arrays.<RoutingAlgorithm>asList(
                new AStar(g), new AStarBidirection(g), new DijkstraBidirectionRef(g),
                new DijkstraBidirection(g), new DijkstraSimple(g)));
        if (withCh) {
            LevelGraph graphCH = (LevelGraphStorage) g.copyTo(new GraphBuilder().levelGraphCreate());
            PrepareContractionHierarchies prepareCH = new PrepareContractionHierarchies().graph(graphCH);
            prepareCH.doWork();
            algos.add(prepareCH.createAlgo());
        }
        return algos;
    }

    public void runShortestPathPerf(int runs, RoutingAlgorithm algo) throws Exception {
        BBox bbox = unterfrankenGraph.bounds();
        double minLat = bbox.minLat, minLon = bbox.minLon;
        double maxLat = bbox.maxLat, maxLon = bbox.maxLon;
        if (unterfrankenGraph instanceof LevelGraph) {
            if (algo instanceof DijkstraBidirectionRef)
                algo = new PrepareContractionHierarchies().graph(unterfrankenGraph).createAlgo();
//                algo = new PrepareTowerNodesShortcuts().graph(unterfrankenGraph).createAlgo();
//            else if (algo instanceof AStarBidirection)
//                algo = new PrepareContractionHierarchies().graph(unterfrankenGraph).createAStar();
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
