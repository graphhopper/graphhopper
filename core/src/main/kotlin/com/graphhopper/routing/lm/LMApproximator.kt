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
package com.graphhopper.routing.lm

import com.graphhopper.routing.Dijkstra
import com.graphhopper.routing.querygraph.QueryGraph
import com.graphhopper.routing.util.TraversalMode
import com.graphhopper.routing.weighting.BeelineWeightApproximator
import com.graphhopper.routing.weighting.QueryGraphWeighting
import com.graphhopper.routing.weighting.WeightApproximator
import com.graphhopper.routing.weighting.Weighting
import com.graphhopper.storage.Graph
import java.util.Arrays

/**
 * This class is a weight approximation based on precalculated landmarks.
 *
 * @param lmWeighting the weighting used for the LM preparation, but wrapped for the given graph.
 *                    Essentially graph.wrapWeighting(lms.getWeighting()), and just lms.getWeighting()
 *                    unless graph is the QueryGraph
 * @param routingWeighting the weighting used for the current path calculation, not necessarily
 *                         the same we used for the LM preparation. All edge weights must be larger
 *                         or equal compared to those used for the preparation.
 *
 * @author Peter Karich
 */
class LMApproximator(
    // the weighting used for the LM preparation
    private val graph: Graph,
    private val lmWeighting: Weighting,
    // the weighting used for the current path calculation
    private val routingWeighting: Weighting,
    private val lms: LandmarkStorage,
    activeCount: Int,
    private val reverse: Boolean
) : WeightApproximator {

    private val activeLandmarkIndices: IntArray
    private val weightsFromActiveLandmarksToT: IntArray
    private val weightsFromTToActiveLandmarks: IntArray
    private var epsilon = 1.0
    private var towerNodeNextToT = -1
    private var weightFromTToTowerNode = 0.0
    private var recalculateActiveLandmarks = true
    private val factor: Double
    private val maxBaseNodes: Int
    private val fallBackApproximation: WeightApproximator
    private val beelineApproximation: WeightApproximator
    private var fallback = false

    init {
        if (graph is QueryGraph && (lmWeighting !is QueryGraphWeighting || routingWeighting !is QueryGraphWeighting))
            throw IllegalStateException("Weighting must use QueryGraphWeighting")
        this.factor = lms.getFactor()
        if (activeCount > lms.getLandmarkCount())
            throw IllegalArgumentException("Active landmarks " + activeCount
                    + " should be lower or equals to landmark count " + lms.getLandmarkCount())

        activeLandmarkIndices = IntArray(activeCount)
        Arrays.fill(activeLandmarkIndices, -1)
        weightsFromActiveLandmarksToT = IntArray(activeCount)
        weightsFromTToActiveLandmarks = IntArray(activeCount)

        this.fallBackApproximation = BeelineWeightApproximator(graph.nodeAccess, routingWeighting)
        this.beelineApproximation = BeelineWeightApproximator(graph.nodeAccess, routingWeighting)
        this.maxBaseNodes = lms.getBaseNodes()
    }

    /**
     * Increase approximation with higher epsilon
     */
    fun setEpsilon(epsilon: Double): LMApproximator {
        this.epsilon = epsilon
        return this
    }

    override fun approximate(currentNode: Int): Double {
        val v = currentNode
        if (!recalculateActiveLandmarks && fallback || lms.isEmpty())
            return fallBackApproximation.approximate(v)

        if (v >= maxBaseNodes) {
            // handle virtual node
            return 0.0
        }

        if (v == towerNodeNextToT)
            return 0.0

        // select better active landmarks, LATER: use 'success' statistics about last active landmark
        // we have to update the priority queues and the maps if done in the middle of the search http://cstheory.stackexchange.com/q/36355/13229
        if (recalculateActiveLandmarks) {
            recalculateActiveLandmarks = false
            if (lms.chooseActiveLandmarks(v, towerNodeNextToT, activeLandmarkIndices, reverse)) {
                for (i in activeLandmarkIndices.indices) {
                    weightsFromActiveLandmarksToT[i] = lms.getFromWeight(activeLandmarkIndices[i], towerNodeNextToT)
                    weightsFromTToActiveLandmarks[i] = lms.getToWeight(activeLandmarkIndices[i], towerNodeNextToT)
                }
            } else {
                // note: fallback==true means forever true!
                fallback = true
                return fallBackApproximation.approximate(v)
            }
        }
        val lmApproximation = Math.max(0.0, (getRemainingWeightUnderestimationUpToTowerNode(v) - weightFromTToTowerNode) * epsilon)
        // Since both the LM and the beeline approximations underestimate the real remaining weight the larger one is
        // more accurate. For example when the speed is reduced for all roads the beeline approximation adjusts automatically
        // to the reduced global maximum speed, while the LM approximation becomes worse.
        return Math.max(lmApproximation, beelineApproximation.approximate(v))
    }

    private fun getRemainingWeightUnderestimationUpToTowerNode(v: Int): Double {
        var maxWeightInt = 0
        for (i in activeLandmarkIndices.indices) {
            val resultInt = approximateForLandmark(i, v)
            maxWeightInt = Math.max(maxWeightInt, resultInt)
        }
        // Round down, we need to be an underestimator.
        return (maxWeightInt - 1) * factor
    }

    private fun approximateForLandmark(i: Int, v: Int): Int {
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

        var rhs1Int = lms.getToWeight(activeLandmarkIndices[i], v) - weightsFromTToActiveLandmarks[i]
        var rhs2Int = weightsFromActiveLandmarksToT[i] - lms.getFromWeight(activeLandmarkIndices[i], v)

        if (reverse) {
            rhs1Int *= -1
            rhs2Int *= -1
        }
        return Math.max(rhs1Int, rhs2Int)
    }

    override fun setTo(to: Int) {
        this.fallBackApproximation.setTo(to)
        this.beelineApproximation.setTo(to)
        findClosestRealNode(to)
    }

    private fun findClosestRealNode(t: Int) {
        val dijkstra = object : Dijkstra(graph, lmWeighting, TraversalMode.NODE_BASED) {
            override fun finished(): Boolean {
                val curr = currEdge!!
                towerNodeNextToT = curr.adjNode
                weightFromTToTowerNode = curr.weight
                return curr.adjNode < maxBaseNodes
            }

            // We only expect a very short search
            override fun initCollections(size: Int) {
                super.initCollections(2)
            }
        }
        dijkstra.calcPath(t, -1)
    }

    override fun reverse(): WeightApproximator {
        return LMApproximator(graph, lmWeighting, routingWeighting, lms, activeLandmarkIndices.size, !reverse)
    }

    override val slack: Double
        get() = lms.getFactor()

    override fun toString(): String = "landmarks"
}
