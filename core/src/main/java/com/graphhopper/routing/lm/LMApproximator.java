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

import com.graphhopper.coll.GHIntObjectHashMap;
import com.graphhopper.routing.QueryGraph;
import com.graphhopper.routing.weighting.BeelineWeightApproximator;
import com.graphhopper.routing.weighting.WeightApproximator;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.EdgeIteratorState;

import java.util.Arrays;

/**
 * This class is a weight approximation based on precalculated landmarks.
 *
 * @author Peter Karich
 */
public class LMApproximator implements WeightApproximator {
    private static class VirtEntry {
        private int node;
        private int weight;

        @Override
        public String toString() {
            return node + ", " + weight;
        }
    }

    private final LandmarkStorage lms;
    // store node ids
    private int[] activeLandmarks;
    // store weights as int
    private int[] activeFromIntWeights;
    private int[] activeToIntWeights;
    private double epsilon = 1;
    private int to = -1;
    // do activate landmark recalculation
    private boolean doALMRecalc = true;
    private final double factor;
    private final boolean reverse;
    private final int maxBaseNodes;
    private final Graph graph;
    private final WeightApproximator fallBackApproximation;
    private boolean fallback = false;
    private final GHIntObjectHashMap<VirtEntry> virtNodeMap;

    public LMApproximator(Graph graph, int maxBaseNodes, LandmarkStorage lms, int activeCount,
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
        this.fallBackApproximation = new BeelineWeightApproximator(graph.getNodeAccess(), lms.getWeighting());
        this.maxBaseNodes = maxBaseNodes;
        int idxVirtNode = maxBaseNodes;
        virtNodeMap = new GHIntObjectHashMap(graph.getNodes() - idxVirtNode, 0.5f);
        // virtual nodes handling: calculate the minimum weight for the virtual nodes, i.e. pick the correct neighbouring node
        if (graph instanceof QueryGraph) {
            QueryGraph qGraph = (QueryGraph) graph;
            // there are at least two virtual nodes (start & destination)
            for (; idxVirtNode < qGraph.getNodes(); idxVirtNode++) {
                // we need the real underlying edge as neighboring nodes could be virtual too
                EdgeIteratorState edge = qGraph.getOriginalEdgeFromVirtNode(idxVirtNode);

                int weight = lms.calcWeight(edge, reverse);
                int reverseWeight = lms.calcWeight(edge, !reverse);
                VirtEntry virtEntry = new VirtEntry();
                if (weight < Integer.MAX_VALUE && (reverseWeight >= Integer.MAX_VALUE || weight < reverseWeight)) {
                    virtEntry.weight = weight;
                    virtEntry.node = reverse ? edge.getBaseNode() : edge.getAdjNode();
                } else {
                    virtEntry.weight = reverseWeight;
                    if (reverseWeight >= Integer.MAX_VALUE)
                        throw new IllegalStateException("At least one direction of edge (" + edge + ") should be accessible but wasn't!");

                    virtEntry.node = reverse ? edge.getAdjNode() : edge.getBaseNode();
                }

                virtNodeMap.put(idxVirtNode, virtEntry);
            }
        }
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

        int node = queryNode;
        int virtEdgeWeightInt = 0;
        if (queryNode >= maxBaseNodes) {
            // handle virtual node
            VirtEntry virtEntry = virtNodeMap.get(queryNode);
            node = virtEntry.node;
            virtEdgeWeightInt = virtEntry.weight;
        }

        if (node == to)
            return 0;

        // select better active landmarks, LATER: use 'success' statistics about last active landmark
        // we have to update the priority queues and the maps if done in the middle of the search http://cstheory.stackexchange.com/q/36355/13229
        if (doALMRecalc) {
            doALMRecalc = false;
            boolean res = lms.initActiveLandmarks(node, to, activeLandmarks, activeFromIntWeights, activeToIntWeights, reverse);
            if (!res) {
                // note: fallback==true means forever true!
                fallback = true;
                return fallBackApproximation.approximate(queryNode);
            }
        }

        int maxWeightInt = getMaxWeight(node, virtEdgeWeightInt, activeLandmarks, activeFromIntWeights, activeToIntWeights);
        if (maxWeightInt < 0) {
            // allow negative weight for now until we have more precise approximation (including query graph)
            return 0;
//                throw new IllegalStateException("Maximum approximation weight cannot be negative. "
//                        + "max weight:" + maxWeightInt
//                        + "queryNode:" + queryNode + ", node:" + node + ", reverse:" + reverse);
        }

        return maxWeightInt * factor * epsilon;
    }

    int getMaxWeight(int node, int virtEdgeWeightInt, int[] activeLandmarks, int[] activeFromIntWeights, int[] activeToIntWeights) {
        int maxWeightInt = -1;
        for (int activeLMIdx = 0; activeLMIdx < activeLandmarks.length; activeLMIdx++) {
            int landmarkIndex = activeLandmarks[activeLMIdx];

            // 1. assume route from a to b: a--->v--->b and a landmark LM.
            //    From this we get two inequality formulas where v is the start (or current node) and b is the 'to' node:
            //    LMv + vb >= LMb therefor vb >= LMb - LMv => 'getFromWeight'
            //    vb + bLM >= vLM therefor vb >= vLM - bLM => 'getToWeight'
            // 2. for the case a->v the sign is reverse as we need to know the vector av not va => if(reverse) "-weight"
            // 3. as weight is the full edge weight for now (and not the precise weight to the virt node) we can only add it to the subtrahend
            //    to avoid overestimating (keep the result strictly lower)
            int fromWeightInt = activeFromIntWeights[activeLMIdx] - (lms.getFromWeight(landmarkIndex, node) + virtEdgeWeightInt);
            int toWeightInt = lms.getToWeight(landmarkIndex, node) - activeToIntWeights[activeLMIdx];
            if (reverse) {
                fromWeightInt = -fromWeightInt;
                // we need virtEntryWeight for the minuend
                toWeightInt = -toWeightInt - virtEdgeWeightInt;
            } else {
                toWeightInt -= virtEdgeWeightInt;
            }

            int tmpMaxWeightInt = Math.max(fromWeightInt, toWeightInt);
//                if (tmpMaxWeightInt < 0)
//                {
//                    int lm = lms.getLandmarks()[landmarkIndex];
//                    throw new IllegalStateException("At least one weight should be positive but wasn't. "
//                            + "activeFromWeight:" + activeFromIntWeights[activeLMIdx] + ", lms.getFromWeight:" + lms.getFromWeight(landmarkIndex, node)
//                            + "lms.getToWeight:" + lms.getToWeight(landmarkIndex, node) + ", activeToWeight:" + activeToIntWeights[activeLMIdx]
//                            + ", virtEdgeWeight:" + virtEdgeWeightInt
//                            + ", lm:" + lm + " (" + getCoord(lm) + ")"
//                            + ", queryNode:" + queryNode + " , node:" + node + " (" + getCoord(node) + "), reverse:" + reverse);
//                }
            if (tmpMaxWeightInt > maxWeightInt)
                maxWeightInt = tmpMaxWeightInt;
        }
        return maxWeightInt;
    }

    final int getNode(int node) {
        return node >= maxBaseNodes ? virtNodeMap.get(node).node : node;
    }

    @Override
    public void setTo(int to) {
        this.to = getNode(to);
    }

    @Override
    public WeightApproximator reverse() {
        return new LMApproximator(graph, maxBaseNodes, lms, activeLandmarks.length, factor, !reverse);
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
