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

import com.graphhopper.routing.ch.AStarCHEntry
import com.graphhopper.routing.weighting.BalancedWeightApproximator
import com.graphhopper.routing.weighting.BeelineWeightApproximator
import com.graphhopper.routing.weighting.WeightApproximator
import com.graphhopper.storage.RoutingCHGraph
import com.graphhopper.util.DistancePlaneProjection

/**
 * @author easbar
 */
open class AStarBidirectionEdgeCHNoSOD(graph: RoutingCHGraph) : AbstractBidirectionEdgeCHNoSOD(graph) {

    private val useHeuristicForNodeOrder = false
    private lateinit var weightApprox: BalancedWeightApproximator

    init {
        setApproximation(BeelineWeightApproximator(nodeAccess, graph.weighting).setDistanceCalc(DistancePlaneProjection.DIST_PLANE))
    }

    public override fun init(from: Int, fromWeight: Double, to: Int, toWeight: Double) {
        weightApprox.setFromTo(from, to)
        super.init(from, fromWeight, to, toWeight)
    }

    override fun fromEntryCanBeSkipped(): Boolean {
        return getMinCurrFromPathWeight() > bestWeight
    }

    override fun toEntryCanBeSkipped(): Boolean {
        return getMinCurrToPathWeight() > bestWeight
    }

    override fun fwdSearchCanBeStopped(): Boolean {
        return useHeuristicForNodeOrder && currFrom!!.weight > bestWeight
    }

    override fun bwdSearchCanBeStopped(): Boolean {
        return useHeuristicForNodeOrder && currTo!!.weight > bestWeight
    }

    override fun createStartEntry(node: Int, weight: Double, reverse: Boolean): AStarCHEntry {
        val heapWeight = getHeapWeight(node, reverse, weight)
        return AStarCHEntry(node, heapWeight, weight)
    }

    override fun createEntry(edge: Int, adjNode: Int, incEdge: Int, weight: Double, parent: SPTEntry?, reverse: Boolean): SPTEntry {
        val heapWeight = getHeapWeight(adjNode, reverse, weight)
        return AStarCHEntry(edge, incEdge, adjNode, heapWeight, weight, parent)
    }

    override fun updateEntry(entry: SPTEntry, edge: Int, adjNode: Int, incEdge: Int, weight: Double, parent: SPTEntry?, reverse: Boolean) {
        entry.edge = edge
        (entry as AStarCHEntry).incEdge = incEdge
        entry.weight = getHeapWeight(adjNode, reverse, weight)
        entry.weightOfVisitedPath = weight
        entry.parent = parent
    }

    fun getApproximation(): WeightApproximator = weightApprox.approximation

    fun setApproximation(weightApproximator: WeightApproximator): AStarBidirectionEdgeCHNoSOD {
        weightApprox = BalancedWeightApproximator(weightApproximator)
        return this
    }

    private fun getHeapWeight(node: Int, reverse: Boolean, weightOfVisitedPath: Double): Double {
        if (useHeuristicForNodeOrder) {
            return weightOfVisitedPath + weightApprox.approximate(node, reverse)
        }
        return weightOfVisitedPath
    }

    private fun getMinCurrFromPathWeight(): Double {
        if (useHeuristicForNodeOrder) {
            return currFrom!!.weight
        }
        return currFrom!!.weight + weightApprox.approximate(currFrom!!.adjNode, false)
    }

    private fun getMinCurrToPathWeight(): Double {
        if (useHeuristicForNodeOrder) {
            return currTo!!.weight
        }
        return currTo!!.weight + weightApprox.approximate(currTo!!.adjNode, true)
    }

    override fun getName(): String = "astarbi|ch|edge_based|no_sod"
}
