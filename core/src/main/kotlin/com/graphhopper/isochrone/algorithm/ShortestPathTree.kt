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
package com.graphhopper.isochrone.algorithm

import com.carrotsearch.hppc.IntObjectHashMap
import com.graphhopper.coll.GHIntObjectHashMap
import com.graphhopper.routing.AbstractRoutingAlgorithm
import com.graphhopper.routing.Path
import com.graphhopper.routing.util.TraversalMode
import com.graphhopper.routing.weighting.Weighting
import com.graphhopper.storage.Graph
import com.graphhopper.util.GHUtility
import java.util.Comparator.comparingDouble
import java.util.Comparator.comparingLong
import java.util.PriorityQueue
import java.util.function.Consumer

/**
 * Computes a shortest path tree by a given weighting. Terminates when all shortest paths up to
 * a given travel time, distance, or weight have been explored.
 *
 * IMPLEMENTATION NOTE:
 * util.PriorityQueue doesn't support efficient removes. We work around this by giving the labels
 * a deleted flag, not remove()ing them, and popping deleted elements off both queues.
 * Note to self/others: If you think this optimization is not needed, please test it with a scenario
 * where updates actually occur a lot, such as using finite, non-zero u-turn costs.
 *
 * @author Peter Karich
 * @author Michael Zilske
 */
class ShortestPathTree(
    g: Graph,
    weighting: Weighting,
    private val reverseFlow: Boolean,
    traversalMode: TraversalMode
) : AbstractRoutingAlgorithm(g, weighting, traversalMode) {

    private enum class ExploreType { TIME, DISTANCE, WEIGHT }

    class IsoLabel internal constructor(
        @JvmField var node: Int,
        @JvmField var edge: Int,
        @JvmField var weight: Double,
        @JvmField var time: Long,
        @JvmField var distance: Double,
        @JvmField var parent: IsoLabel?
    ) {
        @JvmField
        var deleted = false

        override fun toString(): String {
            return "IsoLabel{" +
                    "node=" + node +
                    ", edge=" + edge +
                    ", weight=" + weight +
                    ", time=" + time +
                    ", distance=" + distance +
                    '}'
        }
    }

    private val fromMap: IntObjectHashMap<IsoLabel> = GHIntObjectHashMap(1000)
    private val queueByWeighting: PriorityQueue<IsoLabel> = // a.k.a. the Dijkstra queue
        PriorityQueue(1000, comparingDouble { l: IsoLabel -> l.weight })
    private var queueByZ: PriorityQueue<IsoLabel> = PriorityQueue(1000) // so we know when we are finished
    private var visitedNodes = 0
    private var limit = -1.0
    private var exploreType = ExploreType.TIME

    override fun calcPath(from: Int, to: Int): Path {
        throw IllegalStateException("call search instead")
    }

    /**
     * Time limit in milliseconds
     */
    fun setTimeLimit(limit: Double) {
        exploreType = ExploreType.TIME
        this.limit = limit
        this.queueByZ = PriorityQueue(1000, comparingLong { l: IsoLabel -> l.time })
    }

    /**
     * Distance limit in meter
     */
    fun setDistanceLimit(limit: Double) {
        exploreType = ExploreType.DISTANCE
        this.limit = limit
        this.queueByZ = PriorityQueue(1000, comparingDouble { l: IsoLabel -> l.distance })
    }

    fun setWeightLimit(limit: Double) {
        exploreType = ExploreType.WEIGHT
        this.limit = limit
        this.queueByZ = PriorityQueue(1000, comparingDouble { l: IsoLabel -> l.weight })
    }

    fun search(from: Int, consumer: Consumer<IsoLabel>) {
        checkAlreadyRun()
        var currentLabel = IsoLabel(from, -1, 0.0, 0, 0.0, null)
        queueByWeighting.add(currentLabel)
        queueByZ.add(currentLabel)
        if (traversalMode == TraversalMode.NODE_BASED) {
            fromMap.put(from, currentLabel)
        }
        while (!finished()) {
            currentLabel = queueByWeighting.poll()
            if (currentLabel.deleted)
                continue
            if (getExploreValue(currentLabel) <= limit) {
                consumer.accept(currentLabel)
            }
            currentLabel.deleted = true
            visitedNodes++

            val iter = edgeExplorer.setBaseNode(currentLabel.node)
            while (iter.next()) {
                if (!accept(iter, currentLabel.edge)) {
                    continue
                }

                val nextWeight = GHUtility.calcWeightWithTurnWeight(weighting, iter, reverseFlow, currentLabel.edge) + currentLabel.weight
                if (nextWeight.isInfinite())
                    continue

                val nextDistance = iter.distance + currentLabel.distance
                val nextTime = GHUtility.calcMillisWithTurnMillis(weighting, iter, reverseFlow, currentLabel.edge) + currentLabel.time
                val nextTraversalId = traversalMode.createTraversalId(iter, reverseFlow)
                var nextLabel = fromMap.get(nextTraversalId)
                if (nextLabel == null) {
                    nextLabel = IsoLabel(iter.adjNode, iter.edge, nextWeight, nextTime, nextDistance, currentLabel)
                    fromMap.put(nextTraversalId, nextLabel)
                    queueByWeighting.add(nextLabel)
                    queueByZ.add(nextLabel)
                } else if (nextLabel.weight > nextWeight) {
                    nextLabel.deleted = true
                    nextLabel = IsoLabel(iter.adjNode, iter.edge, nextWeight, nextTime, nextDistance, currentLabel)
                    fromMap.put(nextTraversalId, nextLabel)
                    queueByWeighting.add(nextLabel)
                    queueByZ.add(nextLabel)
                }
            }
        }
    }

    fun getIsochroneEdges(): Collection<IsoLabel> {
        // assert alreadyRun
        return getIsochroneEdges(limit)
    }

    fun getIsochroneEdges(z: Double): ArrayList<IsoLabel> {
        val result = ArrayList<IsoLabel>()
        for (cursor in fromMap.values()) {
            val label = cursor.value
            val parent = label.parent
            if (parent != null &&
                ((getExploreValue(label) > z) xor (getExploreValue(parent) > z))) {
                result.add(label)
            }
        }
        return result
    }

    private fun getExploreValue(label: IsoLabel): Double {
        if (exploreType == ExploreType.TIME)
            return label.time.toDouble()
        if (exploreType == ExploreType.WEIGHT)
            return label.weight
        return label.distance
    }

    protected fun finished(): Boolean {
        while (queueByZ.peek() != null && queueByZ.peek().deleted)
            queueByZ.poll()
        if (queueByZ.peek() == null)
            return true
        return getExploreValue(queueByZ.peek()) >= limit
    }

    override fun getName(): String = "reachability"

    override fun getVisitedNodes(): Int = visitedNodes
}
