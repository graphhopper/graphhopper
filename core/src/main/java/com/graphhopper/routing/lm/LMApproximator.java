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
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.BeelineWeightApproximator;
import com.graphhopper.routing.weighting.WeightApproximator;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;

import java.util.Arrays;

/**
 * This class is a weight approximation based on precalculated landmarks.
 *
 * @author Peter Karich
 */
public class LMApproximator implements WeightApproximator {

    private final LandmarkStorage lms;
    // the weighting used for the LM preparation
    private final Weighting lmWeighting;
    // the weighting used for the current path calculation
    private final Weighting routingWeighting;
    private final int[] activeLandmarkIndices;
    private final int[] weightsFromActiveLandmarksToT;
    private final int[] weightsFromTToActiveLandmarks;
    private double epsilon = 1;
    private int towerNodeNextToT = -1;
    private double weightFromTToTowerNode;
    private boolean recalculateActiveLandmarks = true;
    private final double factor;
    private final boolean reverse;
    private final int maxBaseNodes;
    private final Graph graph;
    private final WeightApproximator fallBackApproximation;
    private final WeightApproximator beelineApproximation;
    private boolean fallback = false;

    /**
     * @param weighting the weighting used for the current path calculation, not necessarily the same that we used for the LM preparation.
     *                  All edge weights must be larger or equal compared to those used for the preparation.
     */
    public static LMApproximator forLandmarks(Graph g, Weighting weighting, LandmarkStorage lms, int activeLM) {
        return new LMApproximator(g, lms.getWeighting(), weighting, lms.getBaseNodes(), lms, activeLM, lms.getFactor(), false);
    }

    public LMApproximator(Graph graph, Weighting lmWeighting, Weighting routingWeighting, int maxBaseNodes, LandmarkStorage lms, int activeCount,
                          double factor, boolean reverse) {
        this.reverse = reverse;
        this.lms = lms;
        this.factor = factor;
        if (activeCount > lms.getLandmarkCount())
            throw new IllegalArgumentException("Active landmarks " + activeCount
                    + " should be lower or equals to landmark count " + lms.getLandmarkCount());

        activeLandmarkIndices = new int[activeCount];
        Arrays.fill(activeLandmarkIndices, -1);
        weightsFromActiveLandmarksToT = new int[activeCount];
        weightsFromTToActiveLandmarks = new int[activeCount];

        this.graph = graph;
        this.lmWeighting = lmWeighting;
        this.routingWeighting = routingWeighting;
        this.fallBackApproximation = new BeelineWeightApproximator(graph.getNodeAccess(), lmWeighting);
        this.beelineApproximation = new BeelineWeightApproximator(graph.getNodeAccess(), routingWeighting);
        this.maxBaseNodes = maxBaseNodes;
    }

    /**
     * Increase approximation with higher epsilon
     */
    public LMApproximator setEpsilon(double epsilon) {
        this.epsilon = epsilon;
        return this;
    }

    @Override
    public double approximate(final int v) {
        if (!recalculateActiveLandmarks && fallback || lms.isEmpty())
            return fallBackApproximation.approximate(v);

        if (v >= maxBaseNodes) {
            // handle virtual node
            return 0;
        }

        if (v == towerNodeNextToT)
            return 0;

        // select better active landmarks, LATER: use 'success' statistics about last active landmark
        // we have to update the priority queues and the maps if done in the middle of the search http://cstheory.stackexchange.com/q/36355/13229
        if (recalculateActiveLandmarks) {
            recalculateActiveLandmarks = false;
            if (lms.chooseActiveLandmarks(v, towerNodeNextToT, activeLandmarkIndices, reverse)) {
                for (int i = 0; i < activeLandmarkIndices.length; i++) {
                    weightsFromActiveLandmarksToT[i] = lms.getFromWeight(activeLandmarkIndices[i], towerNodeNextToT);
                    weightsFromTToActiveLandmarks[i] = lms.getToWeight(activeLandmarkIndices[i], towerNodeNextToT);
                }
            } else {
                // note: fallback==true means forever true!
                fallback = true;
                return fallBackApproximation.approximate(v);
            }
        }
        double lmApproximation = Math.max(0.0, (getRemainingWeightUnderestimationUpToTowerNode(v) - weightFromTToTowerNode) * epsilon);
        // Since both the LM and the beeline approximations underestimate the real remaining weight the larger one is
        // more accurate. For example when the speed is reduced for all roads the beeline approximation adjusts automatically
        // to the reduced global maximum speed, while the LM approximation becomes worse.
        return Math.max(lmApproximation, beelineApproximation.approximate(v));
    }

    private double getRemainingWeightUnderestimationUpToTowerNode(int v) {
        int maxWeightInt = 0;
        for (int i = 0; i < activeLandmarkIndices.length; i++) {
            int resultInt = approximateForLandmark(i, v);
            maxWeightInt = Math.max(maxWeightInt, resultInt);
        }
        // Round down, we need to be an underestimator.
        return (maxWeightInt - 1) * factor;
    }

    private int approximateForLandmark(int i, int v) {
        // ---> means shortest path, d means length of shortest path
        // but remember that d(v,t) != d(t,v)
        //
        // Suppose we are at v, want to go to t, and are looking at a landmark LM,
        // preferably behind t.
        //
        //   ---> t -->
        // v ---------> LM
        //
        // We know distances from everywhere to LM. From the triangle inequality for shortest-path distances we get:
        //  I)  d(v,t) + d(t,LM) >= d(v,LM), so d(v,t) >= d(v,LM) - d(t,LM)
        //
        // Now suppose LM is behind us:
        //
        //    ---> v -->
        // LM ---------> t
        //
        // We also know distances from LM to everywhere, so we get:
        //  II) d(LM,v) + d(v,t) >= d(LM,t), so d(v,t) >= d(LM,t) - d(LM,v)
        //
        // Both equations hold in the general case, so we just pick the tighter approximation.
        // (The other one will probably be negative.)
        //
        // Note that when routing backwards we want to approximate d(t,v), not d(v,t).
        // When we flip all the arrows in the two figures, we get
        //  III)  d(t,v)  + d(LM,t) >= d(LM,v), so d(t,v) >= d(LM,v) - d(LM,t)
        //   IV)  d(v,LM) + d(t,v)  >= d(t,LM), so d(t,v) >= d(t,LM) - d(v,LM)
        //
        // ...and we can get the right-hand sides of III) and IV) by multiplying those of II) and I) by -1.

        int rhs1Int = lms.getToWeight(activeLandmarkIndices[i], v) - weightsFromTToActiveLandmarks[i];
        int rhs2Int = weightsFromActiveLandmarksToT[i] - lms.getFromWeight(activeLandmarkIndices[i], v);

        if (reverse) {
            rhs1Int *= -1;
            rhs2Int *= -1;
        }
        return Math.max(rhs1Int, rhs2Int);
    }

    @Override
    public void setTo(int t) {
        this.fallBackApproximation.setTo(t);
        this.beelineApproximation.setTo(t);
        findClosestRealNode(t);
    }

    private void findClosestRealNode(int t) {
        Dijkstra dijkstra = new Dijkstra(graph, lmWeighting, TraversalMode.NODE_BASED) {
            @Override
            protected boolean finished() {
                towerNodeNextToT = currEdge.adjNode;
                weightFromTToTowerNode = currEdge.weight;
                return currEdge.adjNode < maxBaseNodes;
            }

            // We only expect a very short search
            @Override
            protected void initCollections(int size) {
                super.initCollections(2);
            }
        };
        dijkstra.calcPath(t, -1);
    }

    @Override
    public WeightApproximator reverse() {
        return new LMApproximator(graph, lmWeighting, routingWeighting, maxBaseNodes, lms, activeLandmarkIndices.length, factor, !reverse);
    }

    @Override
    public double getSlack() {
        return lms.getFactor();
    }

    @Override
    public String toString() {
        return "landmarks";
    }
}
