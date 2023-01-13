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

import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.BalancedWeightApproximator;
import com.graphhopper.routing.weighting.BeelineWeightApproximator;
import com.graphhopper.routing.weighting.WeightApproximator;
import com.graphhopper.storage.RoutingCHEdgeIteratorState;
import com.graphhopper.storage.RoutingCHGraph;
import com.graphhopper.core.util.DistancePlaneProjection;
import com.graphhopper.core.util.EdgeIterator;

/**
 * @see AStarBidirection
 */
public class AStarBidirectionCH extends AbstractBidirCHAlgo {
    private BalancedWeightApproximator weightApprox;

    public AStarBidirectionCH(RoutingCHGraph graph) {
        super(graph, TraversalMode.NODE_BASED);
        BeelineWeightApproximator defaultApprox = new BeelineWeightApproximator(nodeAccess, graph.getWeighting());
        defaultApprox.setDistanceCalc(DistancePlaneProjection.DIST_PLANE);
        setApproximation(defaultApprox);
    }

    @Override
    void init(int from, double fromWeight, int to, double toWeight) {
        weightApprox.setFromTo(from, to);
        super.init(from, fromWeight, to, toWeight);
    }

    @Override
    protected SPTEntry createStartEntry(int node, double weight, boolean reverse) {
        double heapWeight = weight + weightApprox.approximate(node, reverse);
        return new AStar.AStarEntry(EdgeIterator.NO_EDGE, node, heapWeight, weight);
    }

    @Override
    protected SPTEntry createEntry(int edge, int adjNode, int incEdge, double weight, SPTEntry parent, boolean reverse) {
        double heapWeight = weight + weightApprox.approximate(adjNode, reverse);
        return new AStar.AStarEntry(edge, adjNode, heapWeight, weight, parent);
    }

    @Override
    protected void updateEntry(SPTEntry entry, int edge, int adjNode, int incEdge, double weight, SPTEntry parent, boolean reverse) {
        entry.edge = edge;
        entry.weight = weight + weightApprox.approximate(adjNode, reverse);
        ((AStar.AStarEntry) entry).weightOfVisitedPath = weight;
        entry.parent = parent;
    }

    @Override
    protected double calcWeight(RoutingCHEdgeIteratorState iter, SPTEntry currEdge, boolean reverse) {
        // TODO performance: check if the node is already existent in the opposite direction
        // then we could avoid the approximation as we already know the exact complete path!
        return super.calcWeight(iter, currEdge, reverse);
    }

    public WeightApproximator getApproximation() {
        return weightApprox.getApproximation();
    }

    /**
     * @param approx if true it enables approximate distance calculation from lat,lon values
     */
    public AStarBidirectionCH setApproximation(WeightApproximator approx) {
        weightApprox = new BalancedWeightApproximator(approx);
        return this;
    }

    @Override
    public String getName() {
        return "astarbi|ch";
    }

}
