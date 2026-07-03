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

import com.graphhopper.coll.GHIntObjectHashMap
import com.graphhopper.routing.querygraph.QueryGraph
import com.graphhopper.routing.util.EdgeFilter
import com.graphhopper.routing.util.TraversalMode
import com.graphhopper.routing.weighting.QueryGraphWeighting
import com.graphhopper.routing.weighting.Weighting
import com.graphhopper.storage.Graph
import com.graphhopper.storage.NodeAccess
import com.graphhopper.util.EdgeExplorer
import com.graphhopper.util.EdgeIterator
import com.graphhopper.util.EdgeIterator.Companion.ANY_EDGE
import com.graphhopper.util.EdgeIteratorState
import com.graphhopper.util.GHUtility
import java.util.PriorityQueue

/**
 * Common subclass for bidirectional algorithms.
 *
 * @author Peter Karich
 * @author easbar
 * @see AbstractBidirCHAlgo for bidirectional CH algorithms
 */
abstract class AbstractNonCHBidirAlgo(graph: Graph, weighting: Weighting, tMode: TraversalMode) :
    AbstractBidirAlgo(tMode), EdgeToEdgeRoutingAlgorithm {

    @JvmField
    protected val graph: Graph

    @JvmField
    protected val nodeAccess: NodeAccess

    @JvmField
    protected val weighting: Weighting

    @JvmField
    protected var edgeExplorer: EdgeExplorer

    @JvmField
    protected var additionalEdgeFilter: EdgeFilter? = null

    init {
        check(!(graph is QueryGraph && weighting !is QueryGraphWeighting)) { "Weighting must use QueryGraphWeighting" }
        this.weighting = weighting
        check(!(weighting.hasTurnCosts() && !tMode.isEdgeBased)) {
            "Weightings supporting turn costs cannot be used with node-based traversal mode"
        }
        this.graph = graph
        this.nodeAccess = graph.nodeAccess
        this.edgeExplorer = graph.createEdgeExplorer()
        val size = Math.min(Math.max(200, graph.nodes / 10), 150_000)
        initCollections(size)
    }

    /**
     * Creates a new entry of the shortest path tree (a [SPTEntry] or one of its subclasses) during a dijkstra
     * expansion.
     *
     * @param edge    the edge that is currently processed for the expansion
     * @param weight  the weight the shortest path three entry should carry
     * @param parent  the parent entry of in the shortest path tree
     * @param reverse true if we are currently looking at the backward search, false otherwise
     */
    protected abstract fun createEntry(edge: EdgeIteratorState, weight: Double, parent: SPTEntry?, reverse: Boolean): SPTEntry

    protected open fun createPathExtractor(graph: Graph, weighting: Weighting): DefaultBidirPathExtractor {
        return DefaultBidirPathExtractor(graph, weighting)
    }

    override fun postInitFrom() {
        if (fromOutEdge == ANY_EDGE) {
            fillEdgesFrom()
        } else {
            fillEdgesFromUsingFilter(EdgeFilter { edgeState -> edgeState.edge == fromOutEdge })
        }
    }

    override fun postInitTo() {
        if (toInEdge == ANY_EDGE) {
            fillEdgesTo()
        } else {
            fillEdgesToUsingFilter(EdgeFilter { edgeState -> edgeState.edge == toInEdge })
        }
    }

    /**
     * @param edgeFilter edge filter used to filter edges during [fillEdgesFrom]
     */
    protected fun fillEdgesFromUsingFilter(edgeFilter: EdgeFilter?) {
        additionalEdgeFilter = edgeFilter
        finishedFrom = !fillEdgesFrom()
        additionalEdgeFilter = null
    }

    /**
     * @see fillEdgesFromUsingFilter
     */
    protected fun fillEdgesToUsingFilter(edgeFilter: EdgeFilter?) {
        additionalEdgeFilter = edgeFilter
        finishedTo = !fillEdgesTo()
        additionalEdgeFilter = null
    }

    override fun fillEdgesFrom(): Boolean {
        while (true) {
            if (pqOpenSetFrom.isEmpty())
                return false
            val curr = pqOpenSetFrom.poll()
            currFrom = curr
            if (!curr.isDeleted())
                break
        }
        visitedCountFrom++
        if (fromEntryCanBeSkipped()) {
            return true
        }
        if (fwdSearchCanBeStopped()) {
            return false
        }
        bestWeightMapOther = bestWeightMapTo
        fillEdges(currFrom!!, pqOpenSetFrom, bestWeightMapFrom, false)
        return true
    }

    override fun fillEdgesTo(): Boolean {
        while (true) {
            if (pqOpenSetTo.isEmpty())
                return false
            val curr = pqOpenSetTo.poll()
            currTo = curr
            if (!curr.isDeleted())
                break
        }
        visitedCountTo++
        if (toEntryCanBeSkipped()) {
            return true
        }
        if (bwdSearchCanBeStopped()) {
            return false
        }
        bestWeightMapOther = bestWeightMapFrom
        fillEdges(currTo!!, pqOpenSetTo, bestWeightMapTo, true)
        return true
    }

    private fun fillEdges(currEdge: SPTEntry, prioQueue: PriorityQueue<SPTEntry>, bestWeightMap: GHIntObjectHashMap<SPTEntry>, reverse: Boolean) {
        val iter = edgeExplorer.setBaseNode(currEdge.adjNode)
        while (iter.next()) {
            if (!accept(iter, currEdge.edge))
                continue

            val weight = calcWeight(iter, currEdge, reverse)
            if (weight.isInfinite()) {
                continue
            }
            val traversalId = traversalMode.createTraversalId(iter, reverse)
            var entry = bestWeightMap.get(traversalId)
            if (entry == null) {
                entry = createEntry(iter, weight, currEdge, reverse)
                bestWeightMap.put(traversalId, entry)
                prioQueue.add(entry)
            } else if (entry.getWeightOfVisitedPath() > weight) {
                // flagging this entry, so it will be ignored when it is polled the next time
                entry.setDeleted()
                val isBestEntry = if (reverse) (entry === bestBwdEntry) else (entry === bestFwdEntry)
                entry = createEntry(iter, weight, currEdge, reverse)
                bestWeightMap.put(traversalId, entry)
                prioQueue.add(entry)
                // if this is the best entry we need to update the best reference as well
                if (isBestEntry)
                    if (reverse)
                        bestBwdEntry = entry
                    else
                        bestFwdEntry = entry
            } else
                continue

            if (updateBestPath) {
                // only needed for edge-based -> skip the calculation and use dummy value otherwise
                val edgeWeight = if (traversalMode.isEdgeBased) weighting.calcEdgeWeight(iter, reverse) else Double.POSITIVE_INFINITY
                // todo: performance - if bestWeightMapOther.get(traversalId) == null, updateBestPath will exit early and we might
                // have calculated the edgeWeight unnecessarily
                updateBestPath(edgeWeight, entry, EdgeIterator.NO_EDGE, traversalId, reverse)
            }
        }
    }

    protected open fun calcWeight(iter: EdgeIteratorState, currEdge: SPTEntry, reverse: Boolean): Double {
        // note that for node-based routing the weights will be wrong in case the weighting is returning non-zero
        // turn weights, see discussion in #1960
        return GHUtility.calcWeightWithTurnWeight(weighting, iter, reverse, currEdge.edge) + currEdge.getWeightOfVisitedPath()
    }

    override fun getInEdgeWeight(entry: SPTEntry): Double {
        return weighting.calcEdgeWeight(graph.getEdgeIteratorState(entry.edge, entry.adjNode)!!, false)
    }

    override fun extractPath(): Path {
        if (finished())
            return createPathExtractor(graph, weighting).extract(bestFwdEntry, bestBwdEntry, bestWeight)

        return createEmptyPath()
    }

    protected open fun accept(iter: EdgeIteratorState, prevOrNextEdgeId: Int): Boolean {
        // for edge-based traversal we leave it for calcTurnWeight to decide whether or not a u-turn is acceptable,
        // but for node-based traversal we exclude such a turn for performance reasons already here
        if (!traversalMode.isEdgeBased && iter.edge == prevOrNextEdgeId)
            return false

        return additionalEdgeFilter == null || additionalEdgeFilter!!.accept(iter)
    }

    protected open fun createEmptyPath(): Path = Path(graph)

    override fun toString(): String = getName() + "|" + weighting
}
