/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  GraphHopper GmbH licenses this file to you under the Apache License, 
 *  Version 2.0 (the "License"); you may not use this file except in 
 *  compliance with the License. You may obtain a copy of the License at
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

import com.graphhopper.reader.PrinctonReader;
import com.graphhopper.routing.ch.PrepareContractionHierarchies;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.TestAlgoCollector.AlgoHelperEntry;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.ShortestWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.*;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.LocationIndexTree;
import com.graphhopper.util.StopWatch;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.zip.GZIPInputStream;

import static com.graphhopper.util.Parameters.Algorithms.*;
import static org.junit.Assert.assertTrue;

/**
 * Try algorithms, indices and graph storages with real data
 * <p>
 *
 * @author Peter Karich
 */
public class RoutingAlgorithmIT {
    public static List<AlgoHelperEntry> createAlgos(GraphHopperStorage ghStorage,
                                                    LocationIndex idx, boolean withCh,
                                                    final TraversalMode tMode, final Weighting weighting,
                                                    final EncodingManager manager) {
        List<AlgoHelperEntry> prepare = new ArrayList<AlgoHelperEntry>();
        prepare.add(new AlgoHelperEntry(ghStorage, ghStorage, new AlgorithmOptions(ASTAR, weighting, tMode), idx));
        // later: include dijkstraOneToMany
        prepare.add(new AlgoHelperEntry(ghStorage, ghStorage, new AlgorithmOptions(DIJKSTRA, weighting, tMode), idx));

        final AlgorithmOptions astarbiOpts = new AlgorithmOptions(ASTAR_BI, weighting, tMode);
        astarbiOpts.getHints().put(ASTAR_BI + ".approximation", "BeelineSimplification");
        final AlgorithmOptions dijkstrabiOpts = new AlgorithmOptions(DIJKSTRA_BI, weighting, tMode);
        prepare.add(new AlgoHelperEntry(ghStorage, ghStorage, astarbiOpts, idx));
        prepare.add(new AlgoHelperEntry(ghStorage, ghStorage, dijkstrabiOpts, idx));

        if (withCh) {
            GraphHopperStorage storageCopy = new GraphBuilder(manager).
                    set3D(ghStorage.getNodeAccess().is3D()).setCHGraph(weighting).
                    create();
            ghStorage.copyTo(storageCopy);
            storageCopy.freeze();
            final CHGraph graphCH = storageCopy.getGraph(CHGraph.class, weighting);
            final PrepareContractionHierarchies prepareCH = new PrepareContractionHierarchies(
                    new GHDirectory("", DAType.RAM_INT), storageCopy, graphCH, weighting, tMode);
            prepareCH.doWork();
            LocationIndex idxCH = new LocationIndexTree(storageCopy, new RAMDirectory()).prepareIndex();
            prepare.add(new AlgoHelperEntry(graphCH, storageCopy, dijkstrabiOpts, idxCH) {
                @Override
                public RoutingAlgorithm createAlgo(Graph qGraph) {
                    return prepareCH.createAlgo(qGraph, dijkstrabiOpts);
                }
            });

            prepare.add(new AlgoHelperEntry(graphCH, storageCopy, astarbiOpts, idxCH) {
                @Override
                public RoutingAlgorithm createAlgo(Graph qGraph) {
                    return prepareCH.createAlgo(qGraph, astarbiOpts);
                }
            });
        }
        return prepare;
    }

    @Test
    public void testPerformance() throws IOException {
        int N = 10;
        int noJvmWarming = N / 4;

        Random rand = new Random(0);
        EncodingManager eManager = new EncodingManager("car");
        FlagEncoder encoder = eManager.getEncoder("car");
        GraphHopperStorage graph = new GraphBuilder(eManager).create();

        String bigFile = "10000EWD.txt.gz";
        new PrinctonReader(graph).setStream(new GZIPInputStream(PrinctonReader.class.getResourceAsStream(bigFile))).read();
        Collection<AlgoHelperEntry> prepares = createAlgos(graph, null, false, TraversalMode.NODE_BASED,
                new ShortestWeighting(encoder), eManager);
        for (AlgoHelperEntry entry : prepares) {
            StopWatch sw = new StopWatch();
            for (int i = 0; i < N; i++) {
                int node1 = Math.abs(rand.nextInt(graph.getNodes()));
                int node2 = Math.abs(rand.nextInt(graph.getNodes()));
                RoutingAlgorithm d = entry.createAlgo(graph);
                if (i >= noJvmWarming)
                    sw.start();

                Path p = d.calcPath(node1, node2);
                // avoid jvm optimization => call p.distance
                if (i >= noJvmWarming && p.getDistance() > -1)
                    sw.stop();

                // System.out.println("#" + i + " " + name + ":" + sw.getSeconds() + " " + p.nodes());
            }

            float perRun = sw.stop().getSeconds() / ((float) (N - noJvmWarming));
            System.out.println("# " + getClass().getSimpleName() + " " + entry
                    + ":" + sw.stop().getSeconds() + ", per run:" + perRun);
            assertTrue("speed to low!? " + perRun + " per run", perRun < 0.08);
        }
    }
}
