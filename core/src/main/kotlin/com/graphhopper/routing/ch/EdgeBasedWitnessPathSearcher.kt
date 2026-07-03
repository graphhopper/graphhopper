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
package com.graphhopper.routing.ch

import com.graphhopper.coll.primitive.IntArrayList
import com.graphhopper.apache.commons.collections.IntFloatBinaryHeap
import com.graphhopper.util.GHUtility
import com.graphhopper.util.Helper.nf
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * Helper class used to perform local witness path searches for graph preparation in edge-based Contraction Hierarchies.
 * <p>
 * (source edge) -- s -- x -- t -- (target edge)
 * Let x be a node to be contracted (the 'center node') and s and t neighboring un-contracted nodes of x that are
 * directly connected with x (via a normal edge or a shortcut). This class is used to find out whether a path between a
 * given source edge incoming to s and a given target edge outgoing from t exists with a given maximum weight. The
 * weights of the source and target edges are not counted in, but the turn costs from the source edge to s->x and from
 * x->t to the target edge are. We also distinguish whether this path is a 'bridge-path' or not:
 * <p>
 * 1) The path only consists of one edge from s to x, an arbitrary number of loops at x, and one edge from x to t.
 * This is called a 'bridge-path' here.
 * 2) The path includes an edge from s to a node other than x or an edge from another node than x to t.
 * This is called a 'witness-path'. Note that a witness path can still include x! This is because if a witness includes
 * x we still do not need to include a shortcut because the path contains another (smaller) shortcut in this case.
 * <p>
 * To find the optimal path an edge-based unidirectional Dijkstra algorithm is used that takes into account turn-costs.
 * The search is initialized for a given source edge key and node to be contracted x. Subsequent searches for different
 * target edges will keep on building the shortest path tree from previous searches. For the performance of edge-based
 * CH graph preparation it is crucial to limit the local witness path searches as much as possible.
 *
 * @author easbar
 */
class EdgeBasedWitnessPathSearcher(private val prepareGraph: CHPreparationGraph) {

    private var outEdgeExplorer: PrepareGraphEdgeExplorer? = prepareGraph.createOutEdgeExplorer()
    private var origInEdgeExplorer: PrepareGraphOrigEdgeExplorer? = prepareGraph.createInOrigEdgeExplorer()

    private var sourceNode = 0
    private var centerNode = 0

    // various counters
    private var numPolls = 0
    private var numUpdates = 0

    // data structures used to build the shortest path tree
    // we allocate memory for all possible edge keys and keep track which ones have been discovered so far
    private var weights: DoubleArray? = null
    private var parents: IntArray? = null
    private var adjNodesAndIsPathToCenters: IntArray? = null
    private var changedEdgeKeys: IntArrayList? = null
    private var dijkstraHeap: IntFloatBinaryHeap? = null

    // statistics to analyze performance
    private var stats: Stats? = null

    init {
        initStorage(2 * prepareGraph.getOriginalEdges())
        initCollections()
    }

    /**
     * Deletes the shortest path tree that has been found so far and initializes a new witness path search for a given
     * node to be contracted and source edge key.
     *
     * @param sourceEdgeKey the key of the original edge incoming to s from which the search starts
     * @param sourceNode    the neighbor node from which the search starts (s)
     * @param centerNode    the node to be contracted (x)
     */
    fun initSearch(sourceEdgeKey: Int, sourceNode: Int, centerNode: Int, stats: Stats) {
        this.stats = stats
        stats.numTrees++
        this.sourceNode = sourceNode
        this.centerNode = centerNode

        // set start entry
        weights!![sourceEdgeKey] = 0.0
        parents!![sourceEdgeKey] = -1
        setAdjNodeAndPathToCenter(sourceEdgeKey, sourceNode, true)
        changedEdgeKeys!!.add(sourceEdgeKey)
        dijkstraHeap!!.insert(0.0, sourceEdgeKey)
    }

    /**
     * Runs a witness path search for a given target edge key. Results of previous searches (the shortest path tree) are
     * reused and the previous search is extended if necessary. Note that you need to call
     * [initSearch] before calling this method to initialize the search.
     *
     * @param targetNode     the neighbor node that should be reached by the path (t)
     * @param targetEdgeKey  the original edge key outgoing from t where the search ends
     * @param acceptedWeight Once we find a path with a weight smaller or equal to this we return the weight. The
     *                       returned weight might be larger than the weight of the real shortest path. If there is
     *                       no path with weight smaller than or equal to this we stop the search and return the weight
     *                       of the best path found so far.
     * @return the weight of the found path or [Double.POSITIVE_INFINITY] if no path was found
     */
    fun runSearch(targetNode: Int, targetEdgeKey: Int, acceptedWeight: Double, maxPolls: Int): Double {
        val stats = stats!!
        val weights = weights!!
        val parents = parents!!
        val changedEdgeKeys = changedEdgeKeys!!
        val dijkstraHeap = dijkstraHeap!!
        stats.numSearches++
        // first we check if we can already reach the target edge from the shortest path tree we discovered so far
        val inIter = origInEdgeExplorer!!.setBaseNode(targetNode)
        while (inIter.next()) {
            val edgeKey = GHUtility.reverseEdgeKey(inIter.getOrigEdgeKeyLast())
            if (weights[edgeKey] == Double.POSITIVE_INFINITY)
                continue
            val weight = weights[edgeKey] + calcTurnWeight(edgeKey, targetNode, targetEdgeKey)
            if (weight < acceptedWeight || (weight == acceptedWeight && (parents[edgeKey] < 0 || !isPathToCenter(parents[edgeKey]))))
                return weight
        }

        // run the search
        while (!dijkstraHeap.isEmpty() && numPolls < maxPolls &&
                // we *could* use dijkstraHeap.peekKey() instead, but since it is cast to float this might be smaller than
                // the actual weight in which case the search might continue and find a false witness path when there is
                // an adjacent zero weight edge *and* u-turn costs are zero. we could check this explicitly somewhere,,
                // but we just use the exact weight here instead. #2564
                weights[dijkstraHeap.peekElement()] < acceptedWeight
        ) {
            val currKey = dijkstraHeap.poll()
            numPolls++
            val currNode = getAdjNode(currKey)
            val iter = outEdgeExplorer!!.setBaseNode(currNode)
            var foundWeight = Double.POSITIVE_INFINITY
            while (iter.next()) {
                // in a few very special cases this is needed to prevent paths that start with a zero weight loop from
                // being recognized as witnesses when there are double zero weight loops at the source node
                if (currNode == sourceNode && iter.getAdjNode() == sourceNode && iter.getWeight() < MAX_ZERO_WEIGHT_LOOP)
                    continue
                val weight = weights[currKey] + calcTurnWeight(currKey, currNode, iter.getOrigEdgeKeyFirst()) + iter.getWeight()
                if (weight.isInfinite())
                    continue
                val key = iter.getOrigEdgeKeyLast()
                val isPathToCenter = isPathToCenter(currKey) && iter.getAdjNode() == centerNode
                if (weights[key] == Double.POSITIVE_INFINITY) {
                    weights[key] = weight
                    parents[key] = currKey
                    setAdjNodeAndPathToCenter(key, iter.getAdjNode(), isPathToCenter)
                    changedEdgeKeys.add(key)
                    dijkstraHeap.insert(weight, key)
                    if (iter.getAdjNode() == targetNode && (!isPathToCenter(currKey) || parents[currKey] < 0))
                        foundWeight = min(foundWeight, weight + calcTurnWeight(key, targetNode, targetEdgeKey))
                } else if (weight < weights[key]
                        // if weights are equal make sure we prefer witness paths over bridge paths
                        || (weight == weights[key] && !isPathToCenter(currKey))) {
                    numUpdates++
                    weights[key] = weight
                    parents[key] = currKey
                    setAdjNodeAndPathToCenter(key, iter.getAdjNode(), isPathToCenter)
                    dijkstraHeap.update(weight, key)
                    if (iter.getAdjNode() == targetNode && (!isPathToCenter(currKey) || parents[currKey] < 0))
                        foundWeight = min(foundWeight, weight + calcTurnWeight(key, targetNode, targetEdgeKey))
                }
            }
            if (foundWeight <= acceptedWeight)
                // note that we have to finish the iteration for the current node, otherwise we'll never check the
                // remaining edges again
                return foundWeight
        }
        if (numPolls == maxPolls)
            stats.numCapped++
        return Double.POSITIVE_INFINITY
    }

    fun finishSearch() {
        val stats = stats!!
        // update stats using values of last search
        stats.numPolls += numPolls
        stats.maxPolls = max(stats.maxPolls, numPolls.toLong())
        stats.numExplored += changedEdgeKeys!!.size()
        stats.maxExplored = max(stats.maxExplored, changedEdgeKeys!!.size().toLong())
        stats.numUpdates += numUpdates
        stats.maxUpdates = max(stats.maxUpdates, numUpdates.toLong())
        reset()
    }

    private fun setAdjNodeAndPathToCenter(key: Int, adjNode: Int, isPathToCenter: Boolean) {
        adjNodesAndIsPathToCenters!![key] = (adjNode shl 1) + (if (isPathToCenter) 1 else 0)
    }

    private fun getAdjNode(key: Int): Int = adjNodesAndIsPathToCenters!![key] shr 1

    private fun isPathToCenter(key: Int): Boolean =
        (adjNodesAndIsPathToCenters!![key] and 0b01) == 0b01

    fun close() {
        prepareGraph.close()
        outEdgeExplorer = null
        origInEdgeExplorer = null
        weights = null
        parents = null
        adjNodesAndIsPathToCenters = null
        changedEdgeKeys!!.release()
        dijkstraHeap = null
    }

    private fun initStorage(numEntries: Int) {
        weights = DoubleArray(numEntries) { Double.POSITIVE_INFINITY }
        parents = IntArray(numEntries) { NO_NODE }
        // need bit shift, see getAdjNode(int)
        adjNodesAndIsPathToCenters = IntArray(numEntries) { NO_NODE shl 1 }
    }

    private fun initCollections() {
        changedEdgeKeys = IntArrayList(1000)
        dijkstraHeap = IntFloatBinaryHeap(1000)
    }

    private fun reset() {
        numPolls = 0
        numUpdates = 0
        resetShortestPathTree()
    }

    private fun resetShortestPathTree() {
        val changedEdgeKeys = changedEdgeKeys!!
        for (i in 0 until changedEdgeKeys.size())
            resetEntry(changedEdgeKeys.get(i))
        changedEdgeKeys.elementsCount = 0
        dijkstraHeap!!.clear()
    }

    private fun resetEntry(key: Int) {
        weights!![key] = Double.POSITIVE_INFINITY
        parents!![key] = NO_NODE
        setAdjNodeAndPathToCenter(key, NO_NODE, false)
    }

    private fun calcTurnWeight(inEdgeKey: Int, viaNode: Int, outEdgeKey: Int): Double =
        prepareGraph.getTurnWeight(inEdgeKey, viaNode, outEdgeKey)

    class Stats {
        @JvmField
        var numTrees = 0L
        @JvmField
        var numSearches = 0L
        @JvmField
        var numPolls = 0L
        @JvmField
        var maxPolls = 0L
        @JvmField
        var numExplored = 0L
        @JvmField
        var maxExplored = 0L
        @JvmField
        var numUpdates = 0L
        @JvmField
        var maxUpdates = 0L
        @JvmField
        var numCapped = 0L

        override fun toString(): String {
            return String.format(Locale.ROOT,
                    "trees: %12s, searches: %15s, capped: %12s (%5.2f%%), polled: avg %s max %6d, explored: avg %s max %6d, updated: avg %s max %6d",
                    nf(numTrees),
                    nf(numSearches),
                    nf(numCapped),
                    100 * numCapped.toDouble() / numSearches,
                    quotient(numPolls, numTrees),
                    maxPolls,
                    quotient(numExplored, numTrees),
                    maxExplored,
                    quotient(numUpdates, numTrees),
                    maxUpdates
            )
        }

        private fun quotient(a: Long, b: Long): String =
            if (b == 0L) "NaN" else String.format(Locale.ROOT, "%5.1f", a / b.toDouble())
    }

    companion object {
        private const val NO_NODE = -1
        private const val MAX_ZERO_WEIGHT_LOOP = 1.0e-3
    }
}
