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

import com.graphhopper.routing.ch.AStarCHEntry;
import com.graphhopper.routing.weighting.*;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.SPTEntry;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.Helper;

/**
 * @author easbar
 */
public class AStarBidirectionEdgeCHNoSOD extends AbstractBidirectionEdgeCHNoSOD {
    private final boolean useHeuristicForNodeOrder = false;
    private ConsistentWeightApproximator weightApprox;

    public AStarBidirectionEdgeCHNoSOD(Graph graph, TurnWeighting weighting) {
        super(graph, weighting);
        setApproximation(new BeelineWeightApproximator(nodeAccess, weighting).setDistanceCalc(Helper.DIST_PLANE));
    }

    @Override
    public void init(int from, double fromWeight, int to, double toWeight) {
        weightApprox.setFrom(from);
        weightApprox.setTo(to);
        super.init(from, fromWeight, to, toWeight);
    }

    @Override
    protected boolean fwdSearchCanBeStopped() {
        return getMinCurrFromPathWeight() > bestPath.getWeight();
    }

    @Override
    protected boolean bwdSearchCanBeStopped() {
        return getMinCurrToPathWeight() > bestPath.getWeight();
    }

    @Override
    protected AStarCHEntry createStartEntry(int node, double weight, boolean reverse) {
        final double heapWeight = getHeapWeight(node, reverse, weight);
        return new AStarCHEntry(node, heapWeight, weight);
    }

    @Override
    protected SPTEntry createEntry(EdgeIteratorState edge, int incEdge, double weight, SPTEntry parent, boolean reverse) {
        int neighborNode = edge.getAdjNode();
        double heapWeight = getHeapWeight(neighborNode, reverse, weight);
        AStarCHEntry entry = new AStarCHEntry(edge.getEdge(), incEdge, neighborNode, heapWeight, weight);
        entry.parent = parent;
        return entry;
    }

    @Override
    protected void updateEntry(SPTEntry entry, EdgeIteratorState edge, int edgeId, double weight, SPTEntry parent, boolean reverse) {
        entry.edge = edge.getEdge();
        ((AStarCHEntry) entry).incEdge = edgeId;
        entry.weight = getHeapWeight(edge.getAdjNode(), reverse, weight);
        ((AStarCHEntry) entry).weightOfVisitedPath = weight;
        entry.parent = parent;
    }

    public WeightApproximator getApproximation() {
        return weightApprox.getApproximation();
    }

    public AStarBidirectionEdgeCHNoSOD setApproximation(WeightApproximator weightApproximator) {
        weightApprox = new ConsistentWeightApproximator(weightApproximator);
        return this;
    }

    private double getHeapWeight(int node, boolean reverse, double weightOfVisitedPath) {
        if (useHeuristicForNodeOrder) {
            return weightOfVisitedPath + weightApprox.approximate(node, reverse);
        }
        return weightOfVisitedPath;
    }

    private double getMinCurrFromPathWeight() {
        if (useHeuristicForNodeOrder) {
            return currFrom.weight;
        }
        return currFrom.weight + weightApprox.approximate(currFrom.adjNode, false);
    }

    private double getMinCurrToPathWeight() {
        if (useHeuristicForNodeOrder) {
            return currTo.weight;
        }
        return currTo.weight + weightApprox.approximate(currTo.adjNode, true);
    }


    @Override
    public String getName() {
        return "astarbi|ch|edge_based|no_sod";
    }
}
