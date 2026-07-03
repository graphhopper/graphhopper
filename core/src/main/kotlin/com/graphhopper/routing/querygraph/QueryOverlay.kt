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

package com.graphhopper.routing.querygraph

import androidx.collection.MutableIntLongMap
import androidx.collection.MutableIntObjectMap
import androidx.collection.MutableIntSet
import com.graphhopper.coll.primitive.DoubleArrayList
import com.graphhopper.coll.primitive.IntArrayList
import com.graphhopper.coll.primitive.IntDoubleHashMap
import com.graphhopper.coll.primitive.LongArrayList
import com.graphhopper.coll.GHIntObjectHashMap
import com.graphhopper.routing.weighting.Weighting
import com.graphhopper.storage.BaseGraph
import com.graphhopper.util.PointList
import kotlin.math.abs
import kotlin.math.min

/**
 * This class holds the data that is necessary to add additional nodes and edges to an existing graph, as it is needed
 * when we want to start/end a route at a location that is in between the actual nodes of the graph (virtual nodes+edges).
 */
internal class QueryOverlay(numVirtualNodes: Int, is3D: Boolean) {
    // stores the coordinates of the additional/virtual nodes
    val virtualNodes: PointList = PointList(numVirtualNodes, is3D)

    // stores the closest edge id for each virtual node
    val closestEdges: IntArrayList = IntArrayList(numVirtualNodes)

    // stores the virtual edges, for every virtual node there are four such edges: base-snap, snap-base, snap-adj, adj-snap.
    val virtualEdges: MutableList<VirtualEdgeIteratorState> = ArrayList(numVirtualNodes * 2)

    // stores the changes that need to be done to the real nodes
    val edgeChangesAtRealNodes: GHIntObjectHashMap<EdgeChanges> = GHIntObjectHashMap(numVirtualNodes * 3)

    val numVirtualEdges: Int
        get() = virtualEdges.size

    fun addVirtualEdge(virtualEdge: VirtualEdgeIteratorState) {
        virtualEdges.add(virtualEdge)
    }

    fun getVirtualEdge(edgeId: Int): VirtualEdgeIteratorState = virtualEdges[edgeId]

    class WeightsAndTimes(val weights: IntDoubleHashMap, val times: MutableIntLongMap)

    companion object {
        @JvmStatic
        fun calcAdjustedVirtualWeightsAndTimes(queryOverlay: QueryOverlay, baseGraph: BaseGraph, weighting: Weighting): WeightsAndTimes =
            calcAdjustedVirtualWeightsAndTimes(queryOverlay.virtualEdges, baseGraph, weighting)

        @JvmStatic
        fun calcAdjustedVirtualWeightsAndTimes(virtualEdges: List<VirtualEdgeIteratorState>, baseGraph: BaseGraph, weighting: Weighting): WeightsAndTimes {
            val weights = IntDoubleHashMap(virtualEdges.size)
            val times = MutableIntLongMap(virtualEdges.size)

            val virtualEdgesByOriginalKey: MutableIntObjectMap<MutableList<VirtualEdgeIteratorState>> = MutableIntObjectMap()
            val edgesSet: MutableIntSet = MutableIntSet()
            for (v in virtualEdges) {
                var edges = virtualEdgesByOriginalKey.get(v.originalEdgeKey)
                if (edges == null) {
                    edges = ArrayList()
                    virtualEdgesByOriginalKey.put(v.originalEdgeKey, edges)
                }
                // remove duplicates
                if (edges.isEmpty() || edgesSet.add(v.edgeKey))
                    edges.add(v)
            }

            virtualEdgesByOriginalKey.forEach { key, value ->
                val virtualWeights = DoubleArrayList(value.size)
                val virtualTimes = LongArrayList(value.size)
                var hasInfiniteVirtualEdge = false
                for (v in value) {
                    val w = weighting.calcEdgeWeight(v, false)
                    if (w.isInfinite())
                        hasInfiniteVirtualEdge = true
                    else if (w < 0 || w % 1 != 0.0)
                        throw IllegalArgumentException("weight must be non-negative whole number, got: $w")
                    virtualWeights.add(w)

                    val t = weighting.calcEdgeMillis(v, false)
                    virtualTimes.add(t)
                }
                val originalEdge = baseGraph.getEdgeIteratorStateForKey(key)
                val originalWeight = weighting.calcEdgeWeight(originalEdge, false)
                val originalTime = weighting.calcEdgeMillis(originalEdge, false)

                if (originalWeight.isInfinite() || hasInfiniteVirtualEdge) {
                    // we don't adjust anything
                    for (i in 0 until value.size) {
                        weights.put(value[i].edgeKey, virtualWeights.get(i))
                        times.put(value[i].edgeKey, virtualTimes.get(i))
                    }
                    return@forEach
                } else if (originalWeight < 0 || originalWeight % 1 != 0.0)
                    throw IllegalArgumentException("weight must be non-negative whole number, got: $originalWeight")

                // casting to long is safe since we checked weights are whole numbers
                val virtualWeightsLong = LongArrayList(virtualWeights.size())
                for (vw in virtualWeights) virtualWeightsLong.add(vw.value.toLong())

                // We do not adjust the weights if the difference is more than rounding errors.
                // For example, when we snap onto an edge only partially covered by an avoided area,
                // only one of the virtual edges might intersect the area. In this case we do not want to
                // penalize the virtual edges that are outside the area. This means that the sum of the
                // virtual edges' weights does not equal the weight of the original edge.
                adjustValues(virtualWeightsLong, originalWeight.toLong(), 1)
                adjustValues(virtualTimes, originalTime, 20)
                for (i in 0 until value.size) {
                    weights.put(value[i].edgeKey, virtualWeightsLong.get(i).toDouble())
                    times.put(value[i].edgeKey, virtualTimes.get(i))
                }
            }
            return WeightsAndTimes(weights, times)
        }

        /**
         * Adjusts values so they sum to target, changing each by at most maxPerElement.
         * The first element is kept >= 1 to avoid zero-weight virtual edges at tower nodes.
         * Zero-weight virtual edges at tower node introduce unique path ambiguity.
         * If the target is unreachable within these constraints, values are left untouched.
         */
        @JvmStatic
        fun adjustValues(values: LongArrayList, target: Long, maxPerElement: Long) {
            if (values.isEmpty) return
            if (target < 0) throw IllegalArgumentException("target cannot be negative: $target")
            if (maxPerElement < 0)
                throw IllegalArgumentException("maxPerElement cannot be negative: $maxPerElement")
            // If the target is zero we do nothing, because we would have to set all values zero, but we want to keep the zeroth >= 1 -> not our problem
            if (target == 0L) return
            var minTarget = 0L
            var maxTarget = 0L
            var diff = target
            for (i in 0 until values.size()) {
                diff -= values.get(i)
                val floor = if (i == 0) 1L else 0L
                minTarget += kotlin.math.max(floor, values.get(i) - maxPerElement)
                maxTarget += values.get(i) + maxPerElement
            }
            if (diff == 0L) return
            // Check if the target is reachable given maxPerElement, no element must be negative, and the first must be at least one.
            // If not, we leave the array untouched since we only want to account for small numerical errors.
            if (target < minTarget || target > maxTarget) return
            val sign = if (diff > 0) 1 else -1
            for (i in 0 until values.size()) {
                var adjustment = sign * min(abs(diff), maxPerElement)
                // The first element must stay > 0: a zero-weight first virtual edge (leaving the
                // tower node) introduces unique path ambiguity.
                val floor = if (i == 0) 1L else 0L
                if (values.get(i) + adjustment < floor) adjustment = floor - values.get(i)
                values.set(i, values.get(i) + adjustment)
                diff -= adjustment
            }
        }
    }

    class EdgeChanges(expectedNumAdditionalEdges: Int, expectedNumRemovedEdges: Int) {
        val additionalEdges: MutableList<VirtualEdgeIteratorState> = ArrayList(expectedNumAdditionalEdges)
        val removedEdges: IntArrayList = IntArrayList(expectedNumRemovedEdges)
    }
}
