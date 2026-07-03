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

import com.graphhopper.coll.primitive.IntArrayList
import com.graphhopper.apache.commons.collections.IntFloatBinaryHeap
import com.graphhopper.routing.util.TraversalMode
import com.graphhopper.routing.weighting.Weighting
import com.graphhopper.storage.Graph
import com.graphhopper.util.ArrayUtil
import com.graphhopper.util.EdgeIterator
import com.graphhopper.util.GHUtility
import com.graphhopper.util.Helper
import com.graphhopper.util.Parameters
import java.util.Arrays

/**
 * A simple dijkstra tuned to perform multiple one to many queries with the same source and different target nodes
 * more efficiently than [Dijkstra]. Old data structures are cached between requests and potentially reused and
 * the shortest path tree is stored in (large as the graph) arrays instead of hash maps.
 *
 * @author Peter Karich
 */
open class DijkstraOneToMany(graph: Graph, weighting: Weighting, tMode: TraversalMode) :
    AbstractRoutingAlgorithm(graph, weighting, tMode) {

    private val changedNodes = IntArrayListWithCap()

    @JvmField
    protected var weights: DoubleArray?
    private var parents: IntArray?
    private var edgeIds: IntArray?
    private var heap: IntFloatBinaryHeap?
    private var visitedNodes = 0
    private var doClear = true
    private var endNode = 0
    private var currNode = 0
    private var fromNode = 0
    private var to = 0
    private var weightLimit = Double.MAX_VALUE

    init {
        val parents = IntArray(graph.nodes)
        Arrays.fill(parents, EMPTY_PARENT)
        this.parents = parents

        val edgeIds = IntArray(graph.nodes)
        Arrays.fill(edgeIds, EdgeIterator.NO_EDGE)
        this.edgeIds = edgeIds

        val weights = DoubleArray(graph.nodes)
        Arrays.fill(weights, Double.MAX_VALUE)
        this.weights = weights

        heap = IntFloatBinaryHeap(1000)
    }

    override fun calcPath(from: Int, to: Int): Path {
        setupFinishTime()
        fromNode = from
        endNode = findEndNode(from, to)
        if (endNode < 0 || isWeightLimitExceeded()) {
            val path = createEmptyPath()
            path.setFromNode(fromNode)
            path.setEndNode(endNode)
            return path
        }

        val weights = this.weights!!
        val parents = this.parents!!
        val edgeIds = this.edgeIds!!
        val path = Path(graph)
        var node = endNode
        while (true) {
            val edge = edgeIds[node]
            if (!EdgeIterator.Edge.isValid(edge)) {
                break
            }
            val edgeState = graph.getEdgeIteratorState(edge, node)!!
            path.addDistance_mm(edgeState.distance_mm)
            // todo: we do not yet account for turn times here!
            path.addTime(weighting.calcEdgeMillis(edgeState, false))
            path.addEdge(edge)
            node = parents[node]
        }
        ArrayUtil.reverse(path.getEdges())
        path.setFromNode(fromNode)
        path.setEndNode(endNode)
        path.setFound(true)
        path.setWeight(weights[endNode])
        return path
    }

    /**
     * Call clear if you have a different start node and need to clear the cache.
     */
    fun clear(): DijkstraOneToMany {
        doClear = true
        return this
    }

    fun getWeight(endNode: Int): Double = weights!![endNode]

    fun findEndNode(from: Int, to: Int): Int {
        val weights = this.weights!!
        if (weights.size < 2)
            return NOT_FOUND

        val parents = this.parents!!
        val edgeIds = this.edgeIds!!
        val heap = this.heap!!

        this.to = to
        if (doClear) {
            doClear = false
            val vn = changedNodes.size()
            for (i in 0 until vn) {
                val n = changedNodes.get(i)
                weights[n] = Double.MAX_VALUE
                parents[n] = EMPTY_PARENT
                edgeIds[n] = EdgeIterator.NO_EDGE
            }

            heap.clear()

            // changedNodes.clear();
            changedNodes.elementsCount = 0

            currNode = from
            if (!traversalMode.isEdgeBased) {
                weights[currNode] = 0.0
                changedNodes.add(currNode)
            }
        } else {
            // Cached! Re-use existing data structures
            val parentNode = parents[to]
            if (parentNode != EMPTY_PARENT && weights[to] <= weights[currNode])
                return to

            if (heap.isEmpty() || isMaxVisitedNodesExceeded() || isTimeoutExceeded())
                return NOT_FOUND

            currNode = heap.poll()
        }

        visitedNodes = 0

        // we call 'finished' before heap.peekElement but this would add unnecessary overhead for this special case so we do it outside of the loop
        if (finished()) {
            // then we need a small workaround for special cases see #707
            if (heap.isEmpty())
                doClear = true
            return currNode
        }

        while (true) {
            visitedNodes++
            val iter = edgeExplorer.setBaseNode(currNode)
            while (iter.next()) {
                val adjNode = iter.adjNode
                val prevEdgeId = edgeIds[adjNode]
                if (!accept(iter, prevEdgeId))
                    continue

                val tmpWeight = GHUtility.calcWeightWithTurnWeight(weighting, iter, false, prevEdgeId) + weights[currNode]
                if (tmpWeight.isInfinite())
                    continue

                val w = weights[adjNode]
                if (w == Double.MAX_VALUE) {
                    parents[adjNode] = currNode
                    weights[adjNode] = tmpWeight
                    heap.insert(tmpWeight, adjNode)
                    changedNodes.add(adjNode)
                    edgeIds[adjNode] = iter.edge
                } else if (w > tmpWeight) {
                    parents[adjNode] = currNode
                    weights[adjNode] = tmpWeight
                    heap.update(tmpWeight, adjNode)
                    changedNodes.add(adjNode)
                    edgeIds[adjNode] = iter.edge
                }
            }

            if (heap.isEmpty() || isMaxVisitedNodesExceeded() || isWeightLimitExceeded() || isTimeoutExceeded())
                return NOT_FOUND

            // calling just peek and not poll is important if the next query is cached
            currNode = heap.peekElement()
            if (finished())
                return currNode

            heap.poll()
        }
    }

    private fun finished(): Boolean = currNode == to

    fun setWeightLimit(weightLimit: Double) {
        this.weightLimit = weightLimit
    }

    protected fun isWeightLimitExceeded(): Boolean = weights!![currNode] > weightLimit

    fun close() {
        weights = null
        parents = null
        edgeIds = null
        heap = null
    }

    override fun getVisitedNodes(): Int = visitedNodes

    override fun getName(): String = Parameters.Algorithms.DIJKSTRA_ONE_TO_MANY

    /**
     * List currently used memory in MB (approximately)
     */
    fun getMemoryUsageAsString(): String {
        val len = weights!!.size.toLong()
        return (((8L + 4L + 4L) * len
                + changedNodes.getCapacity() * 4L
                + heap!!.getCapacity() * (4L + 4L)) / Helper.MB).toString() + "MB"
    }

    private class IntArrayListWithCap : IntArrayList() {
        fun getCapacity(): Int = buffer.size
    }

    companion object {
        private const val EMPTY_PARENT = -1
        private const val NOT_FOUND = -1
    }
}
