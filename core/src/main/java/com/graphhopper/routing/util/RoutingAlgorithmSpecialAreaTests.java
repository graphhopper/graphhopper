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

import com.graphhopper.GraphHopper;
import com.graphhopper.routing.ch.PrepareContractionHierarchies;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.storage.index.Location2IDIndex;
import com.graphhopper.storage.LevelGraph;
import com.graphhopper.storage.LevelGraphStorage;
import com.graphhopper.util.StopWatch;
import static com.graphhopper.routing.util.NoOpAlgorithmPreparation.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
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

    public RoutingAlgorithmSpecialAreaTests(GraphHopper graphhopper) {
        this.unterfrankenGraph = graphhopper.graph();
        StopWatch sw = new StopWatch().start();
        idx = graphhopper.index();
        logger.info(idx.getClass().getSimpleName() + " index. Size:"
                + (float) idx.capacity() / (1 << 20) + " MB, took:" + sw.stop().getSeconds());
    }

    public void start() {
        testIndex();
        testAlgos();
    }

    void testAlgos() {
        if (unterfrankenGraph instanceof LevelGraph)
            throw new IllegalStateException("run testAlgos only with a none-LevelGraph. Use prepare.chShortcuts=false "
                    + "Or use prepare.chShortcuts=shortest and avoid the preparation");

        TestAlgoCollector testCollector = new TestAlgoCollector("testAlgos");
        CarFlagEncoder carEncoder = new CarFlagEncoder();
        Collection<AlgorithmPreparation> prepares = createAlgos(unterfrankenGraph, carEncoder, true);
        for (AlgorithmPreparation prepare : prepares) {
            int failed = testCollector.errors.size();
            
            // using index.highResolution=1000
            testCollector.assertDistance(prepare.createAlgo(), idx.findID(50.0315, 10.5105), idx.findID(50.0303, 10.5070), 561.3, 20);
            testCollector.assertDistance(prepare.createAlgo(), idx.findID(49.51451, 9.967346), idx.findID(50.2920, 10.4650), 107826.9, 1755);
            testCollector.assertDistance(prepare.createAlgo(), idx.findID(50.0780, 9.1570), idx.findID(49.5860, 9.9750), 92535.4, 1335);
            testCollector.assertDistance(prepare.createAlgo(), idx.findID(50.2800, 9.7190), idx.findID(49.8960, 10.3890), 77429.6, 1302);
            testCollector.assertDistance(prepare.createAlgo(), idx.findID(49.8020, 9.2470), idx.findID(50.4940, 10.1970), 125593.6, 2331);
            testCollector.assertDistance(prepare.createAlgo(), idx.findID(49.7260, 9.2550), idx.findID(50.4140, 10.2750),131706.1, 2215);
            testCollector.assertDistance(prepare.createAlgo(), idx.findID(50.1100, 10.7530), idx.findID(49.6500, 10.3410), 73170.2, 1417);

            System.out.println("unterfranken " + prepare.createAlgo() + ": " + (testCollector.errors.size() - failed) + " failed");
        }

        testCollector.printSummary();
    }

    public static Collection<AlgorithmPreparation> createAlgos(Graph g,
            EdgePropertyEncoder encoder, boolean withCh) {
        List<AlgorithmPreparation> prepare = new ArrayList<AlgorithmPreparation>(Arrays.<AlgorithmPreparation>asList(
                createAlgoPrepare(g, "astar", encoder),
                createAlgoPrepare(g, "dijkstraOneToMany", encoder),
                createAlgoPrepare(g, "astarbi", encoder),
                createAlgoPrepare(g, "dijkstraNative", encoder),
                createAlgoPrepare(g, "dijkstrabi", encoder),
                createAlgoPrepare(g, "dijkstra", encoder)));
        if (withCh) {
            LevelGraph graphCH = (LevelGraphStorage) g.copyTo(new GraphBuilder().levelGraphCreate());
            PrepareContractionHierarchies prepareCH = new PrepareContractionHierarchies().
                    graph(graphCH).vehicle(encoder);
            prepareCH.doWork();
            prepare.add(prepareCH);
            // TODO prepare.add(prepareCH.createAStar().approximation(true).approximationFactor(.9));
        }
        return prepare;
    }

    void testIndex() {
        TestAlgoCollector testCollector = new TestAlgoCollector("testIndex");
        testCollector.queryIndex(unterfrankenGraph, idx, 50.081241, 10.124366, 14.0);
        testCollector.queryIndex(unterfrankenGraph, idx, 50.081146, 10.124496, 0.0);
        testCollector.queryIndex(unterfrankenGraph, idx, 49.682000, 9.943000, 602.2);
        testCollector.queryIndex(unterfrankenGraph, idx, 50.079341, 10.167925, 122.6);

        testCollector.printSummary();
    }
}
