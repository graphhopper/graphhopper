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
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;

import java.util.Arrays;

/**
 * This class is a weight approximation based on precalculated landmarks.
 *
 * @author Peter Karich
 */
public class LMApproximator implements WeightApproximator {
    private static class VirtEntry {
        private int towerNode;
        private double weight;

        @Override
        public String toString() {
            return towerNode + ", " + weight;
        }
    }

    private final LandmarkStorage lms;
    // store node ids
    private int[] activeLandmarkIndices;
    private double[] activeFromWeights;
    private double[] activeToWeights;
    private double epsilon = 1;
    // virtual nodes have no explicit weight associated, so we calculate everything to the next tower node
    private int toTowerNode = -1;
    // do activate landmark recalculation
    private boolean doALMRecalc = true;
    private final boolean reverse;
    private final int maxBaseNodes;
    private final Graph graph;
    private final WeightApproximator fallBackApproximation;
    private boolean fallback = false;
    private final GHIntObjectHashMap<VirtEntry> virtNodeMap;

    public LMApproximator(Graph graph, int maxBaseNodes, LandmarkStorage lms, int activeCount, boolean reverse) {
        this.reverse = reverse;
        this.lms = lms;
        if (activeCount > lms.getLandmarkCount())
            throw new IllegalArgumentException("Active landmarks " + activeCount
                    + " should be lower or equals to landmark count " + lms.getLandmarkCount());

        activeLandmarkIndices = new int[activeCount];
        Arrays.fill(activeLandmarkIndices, -1);
        activeFromWeights = new double[activeCount];
        activeToWeights = new double[activeCount];

        this.graph = graph;
        this.fallBackApproximation = new BeelineWeightApproximator(graph.getNodeAccess(), lms.getWeighting());
        this.maxBaseNodes = maxBaseNodes;
        int idxVirtNode = maxBaseNodes;
        Weighting w = lms.getWeighting();
        virtNodeMap = new GHIntObjectHashMap<>(graph.getNodes() - idxVirtNode, 0.5f);
        // virtual nodes handling: calculate the minimum weight for the virtual nodes, i.e. pick the correct neighbouring node
        if (graph instanceof QueryGraph) {
            QueryGraph qGraph = (QueryGraph) graph;
            // there are at least two virtual nodes (start & destination)
            for (; idxVirtNode < qGraph.getNodes(); idxVirtNode++) {
                // we need the real underlying edge as neighboring nodes could be virtual too
                EdgeIteratorState edge = qGraph.getOriginalEdgeFromVirtNode(idxVirtNode);

                double weight = w.calcWeight(edge, reverse, EdgeIterator.NO_EDGE);
                double reverseWeight = w.calcWeight(edge, !reverse, EdgeIterator.NO_EDGE);
                VirtEntry virtEntry = new VirtEntry();
                if (weight < Double.MAX_VALUE && (reverseWeight >= Double.MAX_VALUE || weight < reverseWeight)) {
                    virtEntry.weight = weight;
                    virtEntry.towerNode = reverse ? edge.getBaseNode() : edge.getAdjNode();
                } else {
                    virtEntry.weight = reverseWeight;
                    if (reverseWeight >= Integer.MAX_VALUE)
                        throw new IllegalStateException("At least one direction of edge (" + edge + ") should be accessible but wasn't!");

                    virtEntry.towerNode = reverse ? edge.getAdjNode() : edge.getBaseNode();
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

        int towerNode = queryNode;
        double virtEdgeWeight = 0;
        if (queryNode >= maxBaseNodes) {
            // handle virtual node
            VirtEntry virtEntry = virtNodeMap.get(queryNode);
            towerNode = virtEntry.towerNode;
            virtEdgeWeight = virtEntry.weight;
        }

        if (towerNode == toTowerNode)
            return 0;

        // select better active landmarks, LATER: use 'success' statistics about last active landmark
        // we have to update the priority queues and the maps if done in the middle of the search http://cstheory.stackexchange.com/q/36355/13229
        if (doALMRecalc) {
            doALMRecalc = false;
            boolean res = lms.initActiveLandmarks(towerNode, toTowerNode, activeLandmarkIndices, activeFromWeights, activeToWeights, reverse);
            if (!res) {
                // note: fallback==true means forever true!
                fallback = true;
                return fallBackApproximation.approximate(queryNode);
            }
        }

        double maxWeight = getMaxWeight(towerNode, virtEdgeWeight, activeLandmarkIndices, activeFromWeights, activeToWeights);
        if (maxWeight < 0 || Double.isInfinite(maxWeight)) {
            // ignore negative weight for now until we have more precise approximation (including query graph)
            // TODO NOW try if fallback improves speed for larger areas
            return 0;//fallBackApproximation.approximate(queryNode);
//                throw new IllegalStateException("Maximum approximation weight cannot be negative. "
//                        + "max weight:" + maxWeightInt
//                        + "queryNode:" + queryNode + ", node:" + node + ", reverse:" + reverse);
        }

        return maxWeight * epsilon;
    }

    double getMaxWeight(int node, double virtEdgeWeight, int[] activeLandmarksIndices, double[] activeFromWeights, double[] activeToWeights) {
        double maxWeight = -1;
        for (int activeLMIdx = 0; activeLMIdx < activeLandmarksIndices.length; activeLMIdx++) {
            int landmarkIndex = activeLandmarksIndices[activeLMIdx];

            // 1. assume route from a to b: a--->v--->b and a landmark LM.
            //    From this we get two inequality formulas where v is the start (or current node) and b is the 'to' node:
            //    LMv + vb >= LMb therefor vb >= LMb - LMv => 'weights from landmark => fromWeight'
            //    vb + bLM >= vLM therefor vb >= vLM - bLM => 'weights to   landmark => toWeight'
            // 2. for the case a->v the sign is reverse as we need to know the vector av not va => if(reverse) "-weight"
            // 3. as weight is the full edge weight for now (and not the precise weight to the virt node) we can only add it to the subtrahend
            //    to avoid overestimating (keep the result strictly lower)
            double fromWeight = activeFromWeights[activeLMIdx] - (lms.getFromWeight(landmarkIndex, node) + virtEdgeWeight);
            double toWeight = lms.getToWeight(landmarkIndex, node) - activeToWeights[activeLMIdx];
            if (reverse) {
                fromWeight = -fromWeight;
                // we need virtEntryWeight for the minuend
                toWeight = -toWeight - virtEdgeWeight;
            } else {
                toWeight -= virtEdgeWeight;
            }

            double tmpMaxWeight;
            if (Double.isInfinite(fromWeight)) {
                if (Double.isInfinite(toWeight))
                    continue;
                tmpMaxWeight = toWeight;
            } else if (Double.isInfinite(toWeight)) {
                tmpMaxWeight = fromWeight;
            } else {
                tmpMaxWeight = Math.max(fromWeight, toWeight);
            }

//            if (tmpMaxWeight < 0) {
//                // int lm = lms.getLandmarks(0)[landmarkIndex];
//                throw new IllegalStateException("At least one weight should be positive but wasn't. "
//                        + "activeFromWeight:" + activeFromWeights[activeLMIdx] + ", lms.getFromWeight:" + lms.getFromWeight(landmarkIndex, node)
//                        + ", lms.getToWeight:" + lms.getToWeight(landmarkIndex, node) + ", activeToWeight:" + activeToWeights[activeLMIdx]
//                        + ", virtEdgeWeight:" + virtEdgeWeight
//                        // + ", lm:" + lm + " (" + getCoord(lm) + ")" + ", queryNode:" + queryNode
//                        + " , node:" + node + " (" + (node) + "), reverse:" + reverse);
//            }
            if (tmpMaxWeight > maxWeight)
                maxWeight = tmpMaxWeight;
        }
        return maxWeight;
    }

    @Override
    public void setTo(int to) {
        fallBackApproximation.setTo(to);
        toTowerNode = to >= maxBaseNodes ? virtNodeMap.get(to).towerNode : to;
    }

    @Override
    public WeightApproximator reverse() {
        return new LMApproximator(graph, maxBaseNodes, lms, activeLandmarkIndices.length, !reverse);
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
