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

import com.carrotsearch.hppc.IntObjectMap
import com.graphhopper.routing.ch.NodeBasedCHBidirPathExtractor
import com.graphhopper.routing.util.TraversalMode
import com.graphhopper.storage.CHEdgeFilter
import com.graphhopper.storage.NodeAccess
import com.graphhopper.storage.RoutingCHEdgeExplorer
import com.graphhopper.storage.RoutingCHEdgeIteratorState
import com.graphhopper.storage.RoutingCHGraph
import com.graphhopper.util.EdgeIterator.Companion.ANY_EDGE
import com.graphhopper.util.GHUtility
import java.util.PriorityQueue
import java.util.function.Supplier

/**
 * Common subclass for bidirectional CH algorithms.
 *
 * @author Peter Karich
 * @author easbar
 * @see AbstractNonCHBidirAlgo for non-CH bidirectional algorithms
 */
abstract class AbstractBidirCHAlgo(graph: RoutingCHGraph, tMode: TraversalMode) :
    AbstractBidirAlgo(tMode), EdgeToEdgeRoutingAlgorithm {

    @JvmField
    protected val graph: RoutingCHGraph

    @JvmField
    protected val nodeAccess: NodeAccess

    @JvmField
    protected var inEdgeExplorer: RoutingCHEdgeExplorer

    @JvmField
    protected var outEdgeExplorer: RoutingCHEdgeExplorer

    @JvmField
    protected var levelEdgeFilter: CHEdgeFilter?

    private var pathExtractorSupplier: Supplier<BidirPathExtractor>

    init {
        this.graph = graph
        check(!(graph.hasTurnCosts() && !tMode.isEdgeBased)) {
            "Weightings supporting turn costs cannot be used with node-based traversal mode"
        }
        this.nodeAccess = graph.baseGraph.nodeAccess
        outEdgeExplorer = graph.createOutEdgeExplorer()
        inEdgeExplorer = graph.createInEdgeExplorer()
        levelEdgeFilter = CHLevelEdgeFilter(graph)
        pathExtractorSupplier = Supplier { NodeBasedCHBidirPathExtractor(graph) }
        val size = Math.min(Math.max(200, graph.nodes / 10), 150_000)
        initCollections(size)
    }

    override fun initCollections(size: Int) {
        super.initCollections(Math.min(size, 2000))
    }

    /**
     * Creates a new entry of the shortest path tree (a [SPTEntry] or one of its subclasses) during a dijkstra
     * expansion.
     *
     * @param edge    the id of the edge that is currently processed for the expansion
     * @param adjNode the adjacent node of the edge
     * @param incEdge the id of the edge that is incoming to the node the edge is pointed at. usually this is the same as
     *                edge, but for edge-based CH and in case edge corresponds to a shortcut incEdge is the original edge
     *                that is incoming to the node
     * @param weight  the weight the shortest path three entry should carry
     * @param parent  the parent entry of in the shortest path tree
     * @param reverse true if we are currently looking at the backward search, false otherwise
     */
    protected abstract fun createEntry(edge: Int, adjNode: Int, incEdge: Int, weight: Double, parent: SPTEntry?, reverse: Boolean): SPTEntry

    override fun postInitFrom() {
        if (fromOutEdge == ANY_EDGE) {
            fillEdgesFromUsingFilter(levelEdgeFilter)
        } else {
            // need to use a local reference here, because levelEdgeFilter is modified when calling fillEdgesFromUsingFilter
            val tmpFilter = levelEdgeFilter
            fillEdgesFromUsingFilter(CHEdgeFilter { edgeState ->
                (tmpFilter == null || tmpFilter.accept(edgeState)) && GHUtility.getEdgeFromEdgeKey(edgeState.origEdgeKeyFirst) == fromOutEdge
            })
        }
    }

    override fun postInitTo() {
        if (toInEdge == ANY_EDGE) {
            fillEdgesToUsingFilter(levelEdgeFilter)
        } else {
            val tmpFilter = levelEdgeFilter
            fillEdgesToUsingFilter(CHEdgeFilter { edgeState ->
                (tmpFilter == null || tmpFilter.accept(edgeState)) && GHUtility.getEdgeFromEdgeKey(edgeState.origEdgeKeyLast) == toInEdge
            })
        }
    }

    /**
     * @param edgeFilter edge filter used to fill edges. the [levelEdgeFilter] reference will be set to
     *                   edgeFilter by this method, so make sure edgeFilter does not use it directly.
     */
    protected fun fillEdgesFromUsingFilter(edgeFilter: CHEdgeFilter?) {
        // we temporarily ignore the additionalEdgeFilter
        val tmpFilter = levelEdgeFilter
        levelEdgeFilter = edgeFilter
        finishedFrom = !fillEdgesFrom()
        levelEdgeFilter = tmpFilter
    }

    /**
     * @see fillEdgesFromUsingFilter
     */
    protected fun fillEdgesToUsingFilter(edgeFilter: CHEdgeFilter?) {
        // we temporarily ignore the additionalEdgeFilter
        val tmpFilter = levelEdgeFilter
        levelEdgeFilter = edgeFilter
        finishedTo = !fillEdgesTo()
        levelEdgeFilter = tmpFilter
    }

    public override fun finished(): Boolean {
        // we need to finish BOTH searches for CH!
        if (finishedFrom && finishedTo)
            return true

        // changed also the final finish condition for CH
        return currFrom!!.weight >= bestWeight && currTo!!.weight >= bestWeight
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
        fillEdges(currFrom!!, pqOpenSetFrom, bestWeightMapFrom, outEdgeExplorer, false)
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
        fillEdges(currTo!!, pqOpenSetTo, bestWeightMapTo, inEdgeExplorer, true)
        return true
    }

    private fun fillEdges(
        currEdge: SPTEntry, prioQueue: PriorityQueue<SPTEntry>,
        bestWeightMap: IntObjectMap<SPTEntry>, explorer: RoutingCHEdgeExplorer, reverse: Boolean
    ) {
        val iter = explorer.setBaseNode(currEdge.adjNode)
        while (iter.next()) {
            if (!accept(iter, currEdge, reverse))
                continue

            val weight = calcWeight(iter, currEdge, reverse)
            if (weight.isInfinite()) {
                continue
            }
            val origEdgeId = GHUtility.getEdgeFromEdgeKey(if (reverse) iter.origEdgeKeyFirst else iter.origEdgeKeyLast)
            val traversalId = traversalMode.createTraversalId(iter, reverse)
            var entry = bestWeightMap.get(traversalId)
            if (entry == null) {
                entry = createEntry(iter.edge, iter.adjNode, origEdgeId, weight, currEdge, reverse)
                bestWeightMap.put(traversalId, entry)
                prioQueue.add(entry)
            } else if (entry.getWeightOfVisitedPath() > weight) {
                // flagging this entry, so it will be ignored when it is polled the next time
                // this is faster than removing the entry from the queue and adding again, but for CH it does not really
                // make a difference overall.
                entry.setDeleted()
                val isBestEntry = if (reverse) (entry === bestBwdEntry) else (entry === bestFwdEntry)
                entry = createEntry(iter.edge, iter.adjNode, origEdgeId, weight, currEdge, reverse)
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
                // use dummy value for edge weight as it is used for neither node- nor edge-based CH
                updateBestPath(Double.POSITIVE_INFINITY, entry, origEdgeId, traversalId, reverse)
            }
        }
    }

    protected open fun calcWeight(edgeState: RoutingCHEdgeIteratorState, reverse: Boolean, prevOrNextEdgeId: Int): Double {
        val edgeWeight = edgeState.getWeight(reverse)
        val origEdgeId = GHUtility.getEdgeFromEdgeKey(if (reverse) edgeState.origEdgeKeyLast else edgeState.origEdgeKeyFirst)
        val turnCosts = if (reverse)
            graph.getTurnWeight(origEdgeId, edgeState.baseNode, prevOrNextEdgeId)
        else
            graph.getTurnWeight(prevOrNextEdgeId, edgeState.baseNode, origEdgeId)
        return edgeWeight + turnCosts
    }

    protected open fun updateEntry(entry: SPTEntry, edge: Int, adjNode: Int, incEdge: Int, weight: Double, parent: SPTEntry?, reverse: Boolean) {
        entry.edge = edge
        entry.weight = weight
        entry.parent = parent
    }

    protected open fun accept(edge: RoutingCHEdgeIteratorState, currEdge: SPTEntry, reverse: Boolean): Boolean {
        // for edge-based traversal we leave it for calcTurnWeight to decide whether or not a u-turn is acceptable,
        // but for node-based traversal we exclude such a turn for performance reasons already here
        if (!traversalMode.isEdgeBased && edge.edge == getIncomingEdge(currEdge))
            return false

        return levelEdgeFilter == null || levelEdgeFilter!!.accept(edge)
    }

    protected open fun calcWeight(iter: RoutingCHEdgeIteratorState, currEdge: SPTEntry, reverse: Boolean): Double {
        return calcWeight(iter, reverse, getIncomingEdge(currEdge)) + currEdge.getWeightOfVisitedPath()
    }

    override fun getInEdgeWeight(entry: SPTEntry): Double {
        throw UnsupportedOperationException()
    }

    override fun extractPath(): Path {
        if (finished())
            return createPathExtractor().extract(bestFwdEntry, bestBwdEntry, bestWeight)

        return createEmptyPath()
    }

    fun setPathExtractorSupplier(pathExtractorSupplier: Supplier<BidirPathExtractor>) {
        this.pathExtractorSupplier = pathExtractorSupplier
    }

    internal fun createPathExtractor(): BidirPathExtractor = pathExtractorSupplier.get()

    protected open fun createEmptyPath(): Path = Path(graph.baseGraph)

    override fun toString(): String = getName() + "|" + graph.weighting

    private class CHLevelEdgeFilter(private val graph: RoutingCHGraph) : CHEdgeFilter {
        private val maxNodes: Int = graph.baseGraph.baseGraph.nodes

        override fun accept(edgeState: RoutingCHEdgeIteratorState): Boolean {
            val base = edgeState.baseNode
            val adj = edgeState.adjNode
            // always accept virtual edges, see #288
            if (base >= maxNodes || adj >= maxNodes)
                return true

            return graph.getLevel(base) <= graph.getLevel(adj)
        }
    }
}
