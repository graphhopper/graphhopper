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
package com.graphhopper.routing

import com.graphhopper.routing.util.TraversalMode
import com.graphhopper.routing.weighting.BalancedWeightApproximator
import com.graphhopper.routing.weighting.BeelineWeightApproximator
import com.graphhopper.routing.weighting.WeightApproximator
import com.graphhopper.storage.RoutingCHEdgeIteratorState
import com.graphhopper.storage.RoutingCHGraph
import com.graphhopper.util.DistancePlaneProjection
import com.graphhopper.util.EdgeIterator

/**
 * @see AStarBidirection
 */
open class AStarBidirectionCH(graph: RoutingCHGraph) : AbstractBidirCHAlgo(graph, TraversalMode.NODE_BASED) {

    private lateinit var weightApprox: BalancedWeightApproximator

    init {
        val defaultApprox = BeelineWeightApproximator(nodeAccess, graph.weighting)
        defaultApprox.setDistanceCalc(DistancePlaneProjection.DIST_PLANE)
        setApproximation(defaultApprox)
    }

    override fun init(from: Int, fromWeight: Double, to: Int, toWeight: Double) {
        weightApprox.setFromTo(from, to)
        super.init(from, fromWeight, to, toWeight)
    }

    override fun createStartEntry(node: Int, weight: Double, reverse: Boolean): SPTEntry {
        val heapWeight = weight + weightApprox.approximate(node, reverse)
        return AStar.AStarEntry(EdgeIterator.NO_EDGE, node, heapWeight, weight)
    }

    override fun createEntry(edge: Int, adjNode: Int, incEdge: Int, weight: Double, parent: SPTEntry?, reverse: Boolean): SPTEntry {
        val heapWeight = weight + weightApprox.approximate(adjNode, reverse)
        return AStar.AStarEntry(edge, adjNode, heapWeight, weight, parent)
    }

    override fun updateEntry(entry: SPTEntry, edge: Int, adjNode: Int, incEdge: Int, weight: Double, parent: SPTEntry?, reverse: Boolean) {
        entry.edge = edge
        entry.weight = weight + weightApprox.approximate(adjNode, reverse)
        (entry as AStar.AStarEntry).weightOfVisitedPath = weight
        entry.parent = parent
    }

    override fun calcWeight(iter: RoutingCHEdgeIteratorState, currEdge: SPTEntry, reverse: Boolean): Double {
        // TODO performance: check if the node is already existent in the opposite direction
        // then we could avoid the approximation as we already know the exact complete path!
        return super.calcWeight(iter, currEdge, reverse)
    }

    fun getApproximation(): WeightApproximator = weightApprox.approximation

    /**
     * @param approx if true it enables approximate distance calculation from lat,lon values
     */
    fun setApproximation(approx: WeightApproximator): AStarBidirectionCH {
        weightApprox = BalancedWeightApproximator(approx)
        return this
    }

    override fun getName(): String = "astarbi|ch"
}
