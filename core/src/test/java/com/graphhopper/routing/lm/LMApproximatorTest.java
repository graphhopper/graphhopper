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

import com.graphhopper.routing.Dijkstra;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.DecimalEncodedValueImpl;
import com.graphhopper.routing.ev.Subnetwork;
import com.graphhopper.routing.ev.TurnCost;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.*;
import com.graphhopper.storage.*;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.RandomGraph;
import org.junit.jupiter.api.RepeatedTest;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class LMApproximatorTest {

    @RepeatedTest(value = 10)
    public void randomGraph() {
        final long seed = System.nanoTime();
        run(seed);
    }

    private void run(long seed) {
        Directory dir = new GHDirectory("", DAType.RAM);
        DecimalEncodedValue speedEnc = new DecimalEncodedValueImpl("speed", 5, 5, true);
        DecimalEncodedValue turnCostEnc = TurnCost.create("car", 1);
        EncodingManager encodingManager = new EncodingManager.Builder().add(speedEnc).addTurnCostEncodedValue(turnCostEnc).add(Subnetwork.create("car")).build();
        BaseGraph graph = new BaseGraph.Builder(encodingManager).setDir(dir).withTurnCosts(true).create();

        RandomGraph.start().seed(seed).nodes(50).curviness(0.1).speedZero(0.1).fill(graph, speedEnc);

        Weighting weighting = new SpeedWeighting(speedEnc);

        PrepareLandmarks lm = new PrepareLandmarks(dir, graph, encodingManager, new LMConfig("car", weighting), 16);
        lm.setMaximumWeight(10000);
        lm.doWork();
        LandmarkStorage landmarkStorage = lm.getLandmarkStorage();

        for (int t = 0; t < graph.getNodes(); t++) {
            LMApproximator lmApproximator = new LMApproximator(graph, weighting, weighting, graph.getNodes(), landmarkStorage, 8, landmarkStorage.getFactor(), false);
            WeightApproximator reverseLmApproximator = lmApproximator.reverse();
            BeelineWeightApproximator beelineApproximator = new BeelineWeightApproximator(graph.getNodeAccess(), weighting);
            WeightApproximator reverseBeelineApproximator = beelineApproximator.reverse();
            PerfectApproximator perfectApproximator = new PerfectApproximator(graph, weighting, TraversalMode.NODE_BASED, false);
            PerfectApproximator reversePerfectApproximator = new PerfectApproximator(graph, weighting, TraversalMode.NODE_BASED, true);
            BalancedWeightApproximator balancedWeightApproximator = new BalancedWeightApproximator(new LMApproximator(graph, weighting, weighting, graph.getNodes(), landmarkStorage, 8, landmarkStorage.getFactor(), false));

            lmApproximator.setTo(t);
            beelineApproximator.setTo(t);
            reverseLmApproximator.setTo(t);
            reverseBeelineApproximator.setTo(t);
            perfectApproximator.setTo(t);
            reversePerfectApproximator.setTo(t);
            balancedWeightApproximator.setFromTo(0, t);
            int nOverApproximatedWeights = 0;
            int nInconsistentWeights = 0;
            for (int v = 0; v < graph.getNodes(); v++) {
                Dijkstra dijkstra = new Dijkstra(graph, weighting, TraversalMode.NODE_BASED);
                Path path = dijkstra.calcPath(v, t);
                if (path.isFound()) {
                    // Give the beelineApproximator some slack, because the map distance of an edge
                    // can be _smaller_ than its Euklidean distance, due to rounding.
                    double slack = path.getEdgeCount() * (1 / 1000.0);
                    double realRemainingWeight = path.getWeight();
                    double approximatedRemainingWeight = lmApproximator.approximate(v);
                    if (approximatedRemainingWeight - slack > realRemainingWeight) {
                        System.out.printf("LM: %f\treal: %f\n", approximatedRemainingWeight, realRemainingWeight);
                        nOverApproximatedWeights++;
                    }
                    double beelineApproximatedRemainingWeight = beelineApproximator.approximate(v);
                    if (beelineApproximatedRemainingWeight - slack > realRemainingWeight) {
                        System.out.printf("beeline: %f\treal: %f\n", beelineApproximatedRemainingWeight, realRemainingWeight);
                        nOverApproximatedWeights++;
                    }
                    double approximate = perfectApproximator.approximate(v);
                    if (approximate > realRemainingWeight) {
                        System.out.printf("perfect: %f\treal: %f\n", approximate, realRemainingWeight);
                        nOverApproximatedWeights++;
                    }

                    // Triangle inequality for approximator. This is what makes it 'consistent'.
                    // That's a requirement for normal A*-implementations, because if it is violated,
                    // the heap-weight of settled nodes can decrease, and that would mean our
                    // stopping criterion is not sufficient.
                    EdgeIterator neighbors = graph.createEdgeExplorer(edge -> edge.get(speedEnc) > 0).setBaseNode(v);
                    while (neighbors.next()) {
                        int w = neighbors.getAdjNode();
                        double vw = weighting.calcEdgeWeight(neighbors, false);
                        double vwApprox = lmApproximator.approximate(v) - lmApproximator.approximate(w);
                        if (vwApprox - lm.getLandmarkStorage().getFactor() > vw) {
                            System.out.printf("%f\t%f\n", vwApprox - lm.getLandmarkStorage().getFactor(), vw);
                            nInconsistentWeights++;
                        }
                    }

                    neighbors = graph.createEdgeExplorer(edge -> edge.get(speedEnc) > 0).setBaseNode(v);
                    while (neighbors.next()) {
                        int w = neighbors.getAdjNode();
                        double vw = weighting.calcEdgeWeight(neighbors, false);
                        double vwApprox = balancedWeightApproximator.approximate(v, false) - balancedWeightApproximator.approximate(w, false);
                        if (vwApprox - lm.getLandmarkStorage().getFactor() > vw) {
                            System.out.printf("%f\t%f\n", vwApprox - lm.getLandmarkStorage().getFactor(), vw);
                            nInconsistentWeights++;
                        }
                    }
                }
                Dijkstra reverseDijkstra = new Dijkstra(graph, weighting, TraversalMode.NODE_BASED);
                Path reversePath = reverseDijkstra.calcPath(t, v);
                if (reversePath.isFound()) {
                    // Give the beelineApproximator some slack, because the map distance of an edge
                    // can be _smaller_ than its Euklidean distance, due to rounding.
                    double slack = reversePath.getEdgeCount() * (1 / 1000.0);
                    double realRemainingWeight = reversePath.getWeight();
                    double approximatedRemainingWeight = reverseLmApproximator.approximate(v);
                    if (approximatedRemainingWeight - slack > realRemainingWeight) {
                        System.out.printf("LM: %f\treal: %f\n", approximatedRemainingWeight, realRemainingWeight);
                        nOverApproximatedWeights++;
                    }
                    double beelineApproximatedRemainingWeight = reverseBeelineApproximator.approximate(v);
                    if (beelineApproximatedRemainingWeight - slack > realRemainingWeight) {
                        System.out.printf("beeline: %f\treal: %f\n", beelineApproximatedRemainingWeight, realRemainingWeight);
                        nOverApproximatedWeights++;
                    }
                    double approximate = reversePerfectApproximator.approximate(v);
                    if (approximate > realRemainingWeight) {
                        System.out.printf("perfect: %f\treal: %f\n", approximate, realRemainingWeight);
                        nOverApproximatedWeights++;
                    }
                }

            }

            assertEquals(0, nOverApproximatedWeights, "too many over approximated weights, seed: " + seed);
            assertEquals(0, nInconsistentWeights, "too many inconsistent weights, seed: " + seed);
        }
    }

}
