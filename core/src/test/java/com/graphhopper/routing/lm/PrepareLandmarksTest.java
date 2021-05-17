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
package com.graphhopper.routing.lm;

import com.graphhopper.routing.AStar;
import com.graphhopper.routing.AlgorithmOptions;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.RoutingAlgorithm;
import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.Subnetwork;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.util.*;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Directory;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.RAMDirectory;
import com.graphhopper.storage.index.LocationIndexTree;
import com.graphhopper.storage.index.Snap;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.Helper;
import com.graphhopper.util.PMap;
import com.graphhopper.util.Parameters;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static com.graphhopper.util.GHUtility.updateDistancesFor;
import static com.graphhopper.util.Parameters.Algorithms.ASTAR;
import static com.graphhopper.util.Parameters.Algorithms.ASTAR_BI;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Peter Karich
 */
public class PrepareLandmarksTest {
    private GraphHopperStorage graph;
    private EncodingManager encodingManager;
    private FlagEncoder encoder;
    private TraversalMode tm;

    @BeforeEach
    public void setUp() {
        encoder = new CarFlagEncoder();
        tm = TraversalMode.NODE_BASED;
        encodingManager = new EncodingManager.Builder().add(encoder).add(Subnetwork.create("car")).build();
        GraphHopperStorage tmp = new GraphHopperStorage(new RAMDirectory(),
                encodingManager, false);
        tmp.create(1000);
        graph = tmp;
    }

    @Test
    public void testLandmarkStorageAndRouting() {
        // create graph with lat,lon 
        // 0  1  2  ...
        // 15 16 17 ...
        Random rand = new Random(0);
        int width = 15, height = 15;

        DecimalEncodedValue avSpeedEnc = encoder.getAverageSpeedEnc();
        BooleanEncodedValue accessEnc = encoder.getAccessEnc();
        for (int hIndex = 0; hIndex < height; hIndex++) {
            for (int wIndex = 0; wIndex < width; wIndex++) {
                int node = wIndex + hIndex * width;

                // do not connect first with last column!
                double speed = 20 + rand.nextDouble() * 30;
                if (wIndex + 1 < width)
                    graph.edge(node, node + 1).set(accessEnc, true, true).set(avSpeedEnc, speed);

                // avoid dead ends
                if (hIndex + 1 < height)
                    graph.edge(node, node + width).set(accessEnc, true, true).set(avSpeedEnc, speed);

                updateDistancesFor(graph, node, -hIndex / 50.0, wIndex / 50.0);
            }
        }
        Directory dir = new RAMDirectory();
        LocationIndexTree index = new LocationIndexTree(graph, dir);
        index.prepareIndex();

        int lm = 5, activeLM = 2;
        Weighting weighting = new FastestWeighting(encoder);
        LMConfig lmConfig = new LMConfig("car", weighting);
        LandmarkStorage store = new LandmarkStorage(graph, dir, lmConfig, lm);
        store.setMinimumNodes(2);
        store.createLandmarks();

        // landmarks should be the 4 corners of the grid:
        int[] intList = store.getLandmarks(1);
        Arrays.sort(intList);
        assertEquals("[0, 14, 70, 182, 224]", Arrays.toString(intList));
        // two landmarks: one for subnetwork 0 (all empty) and one for subnetwork 1
        assertEquals(2, store.getSubnetworksWithLandmarks());

        assertEquals(0, store.getFromWeight(0, 224));
        double factor = store.getFactor();
        assertEquals(4671, Math.round(store.getFromWeight(0, 47) * factor));
        assertEquals(3640, Math.round(store.getFromWeight(0, 52) * factor));

        long weight1_224 = store.getFromWeight(1, 224);
        assertEquals(5525, Math.round(weight1_224 * factor));
        long weight1_47 = store.getFromWeight(1, 47);
        assertEquals(921, Math.round(weight1_47 * factor));

        // grid is symmetric
        assertEquals(weight1_224, store.getToWeight(1, 224));
        assertEquals(weight1_47, store.getToWeight(1, 47));

        // prefer the landmarks before and behind the goal
        int[] activeLandmarkIndices = new int[activeLM];
        Arrays.fill(activeLandmarkIndices, -1);
        store.chooseActiveLandmarks(27, 47, activeLandmarkIndices, false);
        List<Integer> list = new ArrayList<>();
        for (int idx : activeLandmarkIndices) {
            list.add(store.getLandmarks(1)[idx]);
        }
        // TODO should better select 0 and 224?
        assertEquals(Arrays.asList(224, 70), list);

        PrepareLandmarks prepare = new PrepareLandmarks(new RAMDirectory(), graph, lmConfig, 4);
        prepare.setMinimumNodes(2);
        prepare.doWork();

        AStar expectedAlgo = new AStar(graph, weighting, tm);
        Path expectedPath = expectedAlgo.calcPath(41, 183);

        PMap hints = new PMap().putObject(Parameters.Landmark.ACTIVE_COUNT, 2);

        // landmarks with A*
        RoutingAlgorithm oneDirAlgoWithLandmarks = prepare.getRoutingAlgorithmFactory().createAlgo(graph, weighting,
                new AlgorithmOptions().setAlgorithm(ASTAR).setTraversalMode(tm).setHints(hints));

        Path path = oneDirAlgoWithLandmarks.calcPath(41, 183);

        assertEquals(expectedPath.getWeight(), path.getWeight(), .1);
        assertEquals(expectedPath.calcNodes(), path.calcNodes());
        assertEquals(expectedAlgo.getVisitedNodes() - 135, oneDirAlgoWithLandmarks.getVisitedNodes());

        // landmarks with bidir A*
        RoutingAlgorithm biDirAlgoWithLandmarks = prepare.getRoutingAlgorithmFactory().createAlgo(graph, weighting,
                new AlgorithmOptions().setAlgorithm(ASTAR_BI).setTraversalMode(tm).setHints(hints));
        path = biDirAlgoWithLandmarks.calcPath(41, 183);
        assertEquals(expectedPath.getWeight(), path.getWeight(), .1);
        assertEquals(expectedPath.calcNodes(), path.calcNodes());
        assertEquals(expectedAlgo.getVisitedNodes() - 162, biDirAlgoWithLandmarks.getVisitedNodes());

        // landmarks with A* and a QueryGraph. We expect slightly less optimal as two more cycles needs to be traversed
        // due to the two more virtual nodes but this should not harm in practise
        Snap fromSnap = index.findClosest(-0.0401, 0.2201, EdgeFilter.ALL_EDGES);
        Snap toSnap = index.findClosest(-0.2401, 0.0601, EdgeFilter.ALL_EDGES);
        QueryGraph qGraph = QueryGraph.create(graph, fromSnap, toSnap);
        RoutingAlgorithm qGraphOneDirAlgo = prepare.getRoutingAlgorithmFactory().createAlgo(qGraph, weighting,
                new AlgorithmOptions().setAlgorithm(ASTAR).setTraversalMode(tm).setHints(hints));
        path = qGraphOneDirAlgo.calcPath(fromSnap.getClosestNode(), toSnap.getClosestNode());

        expectedAlgo = new AStar(qGraph, weighting, tm);
        expectedPath = expectedAlgo.calcPath(fromSnap.getClosestNode(), toSnap.getClosestNode());
        assertEquals(expectedPath.getWeight(), path.getWeight(), .1);
        assertEquals(expectedPath.calcNodes(), path.calcNodes());
        assertEquals(expectedAlgo.getVisitedNodes() - 135, qGraphOneDirAlgo.getVisitedNodes());
    }

    @Test
    public void testStoreAndLoad() {
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(0, 1).setDistance(80_000));
        GHUtility.setSpeed(60, true, true, encoder, graph.edge(1, 2).setDistance(80_000));
        String fileStr = "./target/tmp-lm";
        Helper.removeDir(new File(fileStr));

        Directory dir = new RAMDirectory(fileStr, true).create();
        Weighting weighting = new FastestWeighting(encoder);
        LMConfig lmConfig = new LMConfig("car", weighting);
        PrepareLandmarks plm = new PrepareLandmarks(dir, graph, lmConfig, 2);
        plm.setMinimumNodes(2);
        plm.doWork();

        double expectedFactor = plm.getLandmarkStorage().getFactor();
        assertTrue(plm.getLandmarkStorage().isInitialized());
        assertEquals(Arrays.toString(new int[]{
                2, 0
        }), Arrays.toString(plm.getLandmarkStorage().getLandmarks(1)));
        assertEquals(4800, Math.round(plm.getLandmarkStorage().getFromWeight(0, 1) * expectedFactor));

        dir = new RAMDirectory(fileStr, true);
        plm = new PrepareLandmarks(dir, graph, lmConfig, 2);
        assertTrue(plm.loadExisting());
        assertEquals(expectedFactor, plm.getLandmarkStorage().getFactor(), 1e-6);
        assertEquals(Arrays.toString(new int[]{
                2, 0
        }), Arrays.toString(plm.getLandmarkStorage().getLandmarks(1)));
        assertEquals(4800, Math.round(plm.getLandmarkStorage().getFromWeight(0, 1) * expectedFactor));

        Helper.removeDir(new File(fileStr));
    }
}
