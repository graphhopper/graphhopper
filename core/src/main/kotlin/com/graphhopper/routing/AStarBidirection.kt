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

import com.graphhopper.routing.AStar.AStarEntry
import com.graphhopper.routing.util.TraversalMode
import com.graphhopper.routing.weighting.BalancedWeightApproximator
import com.graphhopper.routing.weighting.BeelineWeightApproximator
import com.graphhopper.routing.weighting.WeightApproximator
import com.graphhopper.routing.weighting.Weighting
import com.graphhopper.storage.Graph
import com.graphhopper.util.DistancePlaneProjection
import com.graphhopper.util.EdgeIterator
import com.graphhopper.util.EdgeIteratorState
import com.graphhopper.util.Parameters

/**
 * This class implements a bidirectional A* algorithm. It is interesting to note that a
 * bidirectional dijkstra is far more efficient than a single direction one. The same does not hold
 * for a bidirectional A* as the heuristic can not be as tight.
 *
 * See http://research.microsoft.com/apps/pubs/default.aspx?id=64511
 * http://i11www.iti.uni-karlsruhe.de/_media/teaching/sommer2012/routenplanung/vorlesung4.pdf
 * http://research.microsoft.com/pubs/64504/goldberg-sofsem07.pdf
 * http://www.cs.princeton.edu/courses/archive/spr06/cos423/Handouts/EPP%20shortest%20path%20algorithms.pdf
 *
 * and
 *
 * 1. Ikeda, T., Hsu, M.-Y., Imai, H., Nishimura, S., Shimoura, H., Hashimoto, T., Tenmoku, K., and
 * Mitoh, K. (1994). A fast algorithm for finding better routes by ai search techniques. In VNIS,
 * pages 291–296.
 *
 * 2. Whangbo, T. K. (2007). Efficient modified bidirectional a* algorithm for optimal route-
 * finding. In IEA/AIE, volume 4570, pages 344–353. Springer.
 *
 * or could we even use this three phase approach?
 * www.lix.polytechnique.fr/~giacomon/papers/bidirtimedep.pdf
 *
 * @author Peter Karich
 * @author jansoe
 */
open class AStarBidirection(graph: Graph, weighting: Weighting, tMode: TraversalMode) :
    AbstractNonCHBidirAlgo(graph, weighting, tMode) {

    private lateinit var weightApprox: BalancedWeightApproximator

    @JvmField
    protected var stoppingCriterionOffset = 0.0

    init {
        val defaultApprox = BeelineWeightApproximator(nodeAccess, weighting)
        defaultApprox.setDistanceCalc(DistancePlaneProjection.DIST_PLANE)
        setApproximation(defaultApprox)
    }

    override fun init(from: Int, fromWeight: Double, to: Int, toWeight: Double) {
        weightApprox.setFromTo(from, to)
        stoppingCriterionOffset = weightApprox.approximate(to, true) + weightApprox.slack
        super.init(from, fromWeight, to, toWeight)
    }

    override fun finished(): Boolean {
        if (finishedFrom || finishedTo)
            return true

        return currFrom!!.weight + currTo!!.weight >= bestWeight + stoppingCriterionOffset
    }

    override fun createStartEntry(node: Int, weight: Double, reverse: Boolean): SPTEntry {
        val heapWeight = weight + weightApprox.approximate(node, reverse)
        return AStarEntry(EdgeIterator.NO_EDGE, node, heapWeight, weight)
    }

    override fun createEntry(edge: EdgeIteratorState, weight: Double, parent: SPTEntry?, reverse: Boolean): SPTEntry {
        val neighborNode = edge.adjNode
        val heapWeight = weight + weightApprox.approximate(neighborNode, reverse)
        return AStarEntry(edge.edge, neighborNode, heapWeight, weight, parent)
    }

    override fun calcWeight(iter: EdgeIteratorState, currEdge: SPTEntry, reverse: Boolean): Double {
        // TODO performance: check if the node is already existent in the opposite direction
        // then we could avoid the approximation as we already know the exact complete path!
        return super.calcWeight(iter, currEdge, reverse)
    }

    fun getApproximation(): WeightApproximator = weightApprox.approximation

    fun setApproximation(approx: WeightApproximator): AStarBidirection {
        weightApprox = BalancedWeightApproximator(approx)
        return this
    }

    override fun setToDataStructures(other: AbstractBidirAlgo) {
        throw UnsupportedOperationException()
    }

    override fun getName(): String = Parameters.Algorithms.ASTAR_BI + "|" + weightApprox
}
