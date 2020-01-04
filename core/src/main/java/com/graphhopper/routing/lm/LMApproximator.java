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
    private final Weighting weighting;
    // store node ids
    private int[] activeLandmarks;
    // store weights as int
    private int[] activeFromIntWeights;
    private int[] activeToIntWeights;
    private double epsilon = 1;
    private int toTowerNode = -1;
    private double weightToTowerNode;

    // do activate landmark recalculation
    private boolean doALMRecalc = true;
    private final double factor;
    private final boolean reverse;
    private final int maxBaseNodes;
    private final Graph graph;
    private final WeightApproximator fallBackApproximation;
    private boolean fallback = false;

    public LMApproximator(Graph graph, Weighting weighting, int maxBaseNodes, LandmarkStorage lms, int activeCount,
                          double factor, boolean reverse) {
        this.reverse = reverse;
        this.lms = lms;
        this.factor = factor;
        if (activeCount > lms.getLandmarkCount())
            throw new IllegalArgumentException("Active landmarks " + activeCount
                    + " should be lower or equals to landmark count " + lms.getLandmarkCount());

        activeLandmarks = new int[activeCount];
        Arrays.fill(activeLandmarks, -1);
        activeFromIntWeights = new int[activeCount];
        activeToIntWeights = new int[activeCount];

        this.graph = graph;
        this.weighting = weighting;
        this.fallBackApproximation = new BeelineWeightApproximator(graph.getNodeAccess(), lms.getWeighting());
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
    public double approximate(final int queryNode) {
        if (!doALMRecalc && fallback || lms.isEmpty())
            return fallBackApproximation.approximate(queryNode);

        if (queryNode >= maxBaseNodes) {
            // handle virtual node
            return 0;
        }

        if (queryNode == toTowerNode)
            return 0;

        // select better active landmarks, LATER: use 'success' statistics about last active landmark
        // we have to update the priority queues and the maps if done in the middle of the search http://cstheory.stackexchange.com/q/36355/13229
        if (doALMRecalc) {
            doALMRecalc = false;
            boolean res = lms.initActiveLandmarks(queryNode, toTowerNode, activeLandmarks, activeFromIntWeights, activeToIntWeights, reverse);
            if (!res) {
                // note: fallback==true means forever true!
                fallback = true;
                return fallBackApproximation.approximate(queryNode);
            }
        }

        return (getRemainingWeightUnderestimationUpToTowerNode(queryNode) - weightToTowerNode) * epsilon;
    }

    private double getRemainingWeightUnderestimationUpToTowerNode(int v) {
        int maxWeightInt = -1;
        for (int activeLMIdx = 0; activeLMIdx < activeLandmarks.length; activeLMIdx++) {
            int landmarkIndex = activeLandmarks[activeLMIdx];

            // 1. assume route from a to b: a--->v--->b and a landmark LM.
            //    From this we get two inequality formulas where v is the start (or current node) and b is the 'to' node:
            //    LMv + vb >= LMb therefor vb >= LMb - LMv => 'getFromWeight'
            //    vb + bLM >= vLM therefor vb >= vLM - bLM => 'getToWeight'
            // 2. for the case a->v the sign is reverse as we need to know the vector av not va => if(reverse) "-weight"
            int fromWeightInt = activeFromIntWeights[activeLMIdx] - lms.getFromWeight(landmarkIndex, v);
            int toWeightInt = lms.getToWeight(landmarkIndex, v) - activeToIntWeights[activeLMIdx];
            if (reverse) {
                fromWeightInt = -fromWeightInt;
                toWeightInt = -toWeightInt;
            }

            int tmpMaxWeightInt = Math.max(fromWeightInt, toWeightInt);
            if (tmpMaxWeightInt > maxWeightInt)
                maxWeightInt = tmpMaxWeightInt;
        }
        // Round down, we need to be an underestimator.
        return (maxWeightInt - 1) * factor;
    }

    @Override
    public void setTo(int to) {
        this.fallBackApproximation.setTo(to);
        findClosestRealNode(to);
    }

    private void findClosestRealNode(int to) {
        Dijkstra dijkstra = new Dijkstra(graph, weighting, TraversalMode.NODE_BASED) {
            @Override
            protected boolean finished() {
                toTowerNode = currEdge.adjNode;
                weightToTowerNode = currEdge.weight;
                return currEdge.adjNode < maxBaseNodes;
            }

            // We only expect a very short search
            @Override
            protected void initCollections(int size) {
                super.initCollections(2);
            }
        };
        dijkstra.calcPath(to, -1);
    }

    @Override
    public WeightApproximator reverse() {
        return new LMApproximator(graph, weighting, maxBaseNodes, lms, activeLandmarks.length, factor, !reverse);
    }

    /**
     * This method forces a lazy recalculation of the active landmark set e.g. necessary after the 'to' node changed.
     */
    public void triggerActiveLandmarkRecalculation() {
        doALMRecalc = true;
    }

    @Override
    public String toString() {
        return "landmarks";
    }
}
