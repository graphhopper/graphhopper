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

import com.graphhopper.Repeat;
import com.graphhopper.RepeatRule;
import com.graphhopper.routing.Dijkstra;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.ShortestWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Directory;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.RAMDirectory;
import com.graphhopper.util.GHUtility;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.assertEquals;

public class LMApproximatorTest {

    @Rule
    public RepeatRule repeatRule = new RepeatRule();

    @Before
    public void init() {
    }

    @Test
    @Repeat(times = 5)
    public void randomGraph() {
        Directory dir = new RAMDirectory();
        CarFlagEncoder encoder = new CarFlagEncoder(5, 5, 1);
        EncodingManager encodingManager = EncodingManager.create(encoder);
        GraphHopperStorage graph = new GraphBuilder(encodingManager).setDir(dir).withTurnCosts(true).create();

        final long seed = System.nanoTime();
        System.out.println("random Graph seed: " + seed);
        Random rnd = new Random(seed);
        GHUtility.buildRandomGraph(graph, rnd, 100, 2.2, true, true, encoder.getAverageSpeedEnc(), 0.7, 0.8, 0.8);

        Weighting weighting = new ShortestWeighting(encoder);

        PrepareLandmarks lm = new PrepareLandmarks(dir, graph, weighting, 16, 8);
        lm.doWork();
        LandmarkStorage landmarkStorage = lm.getLandmarkStorage();
        LMApproximator lmApproximator = new LMApproximator(graph, weighting, graph.getNodes(), landmarkStorage, 8, landmarkStorage.getFactor(), false);

        int t = 0;
        lmApproximator.setTo(t);

        int nOverApproximatedWeights = 0;
        for (int v=0; v<graph.getNodes(); v++) {
            Dijkstra dijkstra = new Dijkstra(graph, weighting, TraversalMode.NODE_BASED);
            Path path = dijkstra.calcPath(v, t);
            if (path.isFound()) {
                double realRemainingWeight = path.getWeight();
                double approximatedRemainingWeight = lmApproximator.approximate(v);
                if (approximatedRemainingWeight > realRemainingWeight) {
                    System.out.printf("%f\t%f\n", approximatedRemainingWeight, realRemainingWeight);
                    nOverApproximatedWeights++;
                }
            }
        }

        assertEquals(0, nOverApproximatedWeights);
    }
}