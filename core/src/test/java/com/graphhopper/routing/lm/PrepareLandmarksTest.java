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

import com.graphhopper.routing.*;
import com.graphhopper.routing.profiles.BooleanEncodedValue;
import com.graphhopper.routing.profiles.DecimalEncodedValue;
import com.graphhopper.routing.util.*;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Directory;
import com.graphhopper.storage.GraphExtension;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.RAMDirectory;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.LocationIndexTree;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.Helper;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Peter Karich
 */
public class PrepareLandmarksTest
        /* extends AbstractRoutingAlgorithmTester */ {
    private GraphHopperStorage graph;
    private EncodingManager encodingManager;
    private FlagEncoder encoder;
    private TraversalMode tm;

    @Before
    public void setUp() {
        encoder = new CarFlagEncoder();
        tm = TraversalMode.NODE_BASED;
        encodingManager = EncodingManager.create(encoder);
        GraphHopperStorage tmp = new GraphHopperStorage(new RAMDirectory(),
                encodingManager, false, new GraphExtension.NoOpExtension());
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
                    graph.edge(node, node + 1).set(accessEnc, true).setReverse(accessEnc, true).set(avSpeedEnc, speed);

                // avoid dead ends
                if (hIndex + 1 < height)
                    graph.edge(node, node + width).set(accessEnc, true).setReverse(accessEnc, true).set(avSpeedEnc, speed);

                AbstractRoutingAlgorithmTester.updateDistancesFor(graph, node, -hIndex / 50.0, wIndex / 50.0);
            }
        }
        Directory dir = new RAMDirectory();
        LocationIndex index = new LocationIndexTree(graph, dir);
        index.prepareIndex();

        int lm = 5, activeLM = 2;
        Weighting weighting = new FastestWeighting(encoder);
        LandmarkStorage store = new LandmarkStorage(graph, dir, weighting, lm);
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
        int activeLandmarkIndices[] = new int[activeLM];
        int activeFroms[] = new int[activeLM];
        int activeTos[] = new int[activeLM];
        Arrays.fill(activeLandmarkIndices, -1);
        store.initActiveLandmarks(27, 47, activeLandmarkIndices, activeFroms, activeTos, false);
        List<Integer> list = new ArrayList<>();
        for (int idx : activeLandmarkIndices) {
            list.add(store.getLandmarks(1)[idx]);
        }
        // TODO should better select 0 and 224?
        assertEquals(Arrays.asList(224, 70), list);

        AlgorithmOptions opts = AlgorithmOptions.start().weighting(weighting).traversalMode(tm).
                build();

        PrepareLandmarks prepare = new PrepareLandmarks(new RAMDirectory(), graph, weighting, 4, 2);
        prepare.setMinimumNodes(2);
        prepare.doWork();

        AStar expectedAlgo = new AStar(graph, weighting, tm);
        Path expectedPath = expectedAlgo.calcPath(41, 183);

        // landmarks with A*
        RoutingAlgorithm oneDirAlgoWithLandmarks = prepare.getDecoratedAlgorithm(graph, new AStar(graph, weighting, tm), opts);
        Path path = oneDirAlgoWithLandmarks.calcPath(41, 183);

        assertEquals(expectedPath.getWeight(), path.getWeight(), .1);
        assertEquals(expectedPath.calcNodes(), path.calcNodes());
        assertEquals(expectedAlgo.getVisitedNodes(), oneDirAlgoWithLandmarks.getVisitedNodes() + 142);

        // landmarks with bidir A*
        opts.getHints().put("lm.recalc_count", 50);
        RoutingAlgorithm biDirAlgoWithLandmarks = prepare.getDecoratedAlgorithm(graph,
                new AStarBidirection(graph, weighting, tm), opts);
        path = biDirAlgoWithLandmarks.calcPath(41, 183);
        assertEquals(expectedPath.getWeight(), path.getWeight(), .1);
        assertEquals(expectedPath.calcNodes(), path.calcNodes());
        assertEquals(expectedAlgo.getVisitedNodes(), biDirAlgoWithLandmarks.getVisitedNodes() + 164);

        // landmarks with A* and a QueryGraph. We expect slightly less optimal as two more cycles needs to be traversed
        // due to the two more virtual nodes but this should not harm in practise
        QueryGraph qGraph = new QueryGraph(graph);
        QueryResult fromQR = index.findClosest(-0.0401, 0.2201, EdgeFilter.ALL_EDGES);
        QueryResult toQR = index.findClosest(-0.2401, 0.0601, EdgeFilter.ALL_EDGES);
        qGraph.lookup(fromQR, toQR);
        RoutingAlgorithm qGraphOneDirAlgo = prepare.getDecoratedAlgorithm(qGraph,
                new AStar(qGraph, weighting, tm), opts);
        path = qGraphOneDirAlgo.calcPath(fromQR.getClosestNode(), toQR.getClosestNode());

        expectedAlgo = new AStar(qGraph, weighting, tm);
        expectedPath = expectedAlgo.calcPath(fromQR.getClosestNode(), toQR.getClosestNode());
        assertEquals(expectedPath.getWeight(), path.getWeight(), .1);
        assertEquals(expectedPath.calcNodes(), path.calcNodes());
        assertEquals(expectedAlgo.getVisitedNodes(), qGraphOneDirAlgo.getVisitedNodes() + 133);
    }

    @Test
    public void testStoreAndLoad() {
        graph.edge(0, 1, 80_000, true);
        graph.edge(1, 2, 80_000, true);
        String fileStr = "./target/tmp-lm";
        Helper.removeDir(new File(fileStr));

        Directory dir = new RAMDirectory(fileStr, true).create();
        Weighting weighting = new FastestWeighting(encoder);
        PrepareLandmarks plm = new PrepareLandmarks(dir, graph, weighting, 2, 2);
        plm.setMinimumNodes(2);
        plm.doWork();

        double expectedFactor = plm.getLandmarkStorage().getFactor();
        assertTrue(plm.getLandmarkStorage().isInitialized());
        assertEquals(Arrays.toString(new int[]{
                2, 0
        }), Arrays.toString(plm.getLandmarkStorage().getLandmarks(1)));
        assertEquals(4791, Math.round(plm.getLandmarkStorage().getFromWeight(0, 1) * expectedFactor));

        dir = new RAMDirectory(fileStr, true);
        plm = new PrepareLandmarks(dir, graph, weighting, 2, 2);
        assertTrue(plm.loadExisting());
        assertEquals(expectedFactor, plm.getLandmarkStorage().getFactor(), 1e-6);
        assertEquals(Arrays.toString(new int[]{
                2, 0
        }), Arrays.toString(plm.getLandmarkStorage().getLandmarks(1)));
        assertEquals(4791, Math.round(plm.getLandmarkStorage().getFromWeight(0, 1) * expectedFactor));

        Helper.removeDir(new File(fileStr));
    }
}
