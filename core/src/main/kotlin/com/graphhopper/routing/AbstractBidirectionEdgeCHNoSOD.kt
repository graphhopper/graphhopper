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

import com.graphhopper.routing.ch.CHEntry
import com.graphhopper.routing.ch.EdgeBasedCHBidirPathExtractor
import com.graphhopper.routing.util.TraversalMode
import com.graphhopper.storage.CHEdgeFilter
import com.graphhopper.storage.RoutingCHEdgeIteratorState
import com.graphhopper.storage.RoutingCHGraph
import com.graphhopper.util.EdgeExplorer
import com.graphhopper.util.EdgeIterator.Companion.ANY_EDGE
import com.graphhopper.util.GHUtility
import java.util.function.Supplier

/**
 * @author easbar
 */
abstract class AbstractBidirectionEdgeCHNoSOD(graph: RoutingCHGraph) :
    AbstractBidirCHAlgo(graph, TraversalMode.EDGE_BASED) {

    private val innerExplorer: EdgeExplorer

    init {
        require(graph.isEdgeBased) { "Edge-based CH algorithms only work with edge-based CH graphs" }
        // the inner explorer will run on the base-(or base-query-)graph edges only.
        // we need an extra edge explorer, because it is called inside a loop that already iterates over edges
        // note that we do not need to filter edges with the inner explorer, because inaccessible edges won't be added
        // to bestWeightMapOther in the first place
        innerExplorer = graph.baseGraph.createEdgeExplorer()
        setPathExtractorSupplier(Supplier { EdgeBasedCHBidirPathExtractor(graph) })
    }

    override fun postInitFrom() {
        // We use the levelEdgeFilter to filter out edges leading or coming from lower rank nodes.
        // For the first step though we need all edges, so we need to ignore this filter.
        if (fromOutEdge == ANY_EDGE) {
            fillEdgesFromUsingFilter(CHEdgeFilter.ALL_EDGES)
        } else {
            fillEdgesFromUsingFilter(CHEdgeFilter { edgeState ->
                GHUtility.getEdgeFromEdgeKey(edgeState.origEdgeKeyFirst) == fromOutEdge
            })
        }
    }

    override fun postInitTo() {
        if (toInEdge == ANY_EDGE) {
            fillEdgesToUsingFilter(CHEdgeFilter.ALL_EDGES)
        } else {
            fillEdgesToUsingFilter(CHEdgeFilter { edgeState ->
                GHUtility.getEdgeFromEdgeKey(edgeState.origEdgeKeyLast) == toInEdge
            })
        }
    }

    override fun updateBestPath(edgeWeight: Double, entry: SPTEntry, origEdgeId: Int, traversalId: Int, reverse: Boolean) {
        assert(edgeWeight.isInfinite()) { "edge-based CH does not use pre-calculated edge weight" }
        // special case where the fwd/bwd search runs directly into the opposite node, for example if the highest level
        // node of the shortest path matches the source or target. in this case one of the searches does not contribute
        // anything to the shortest path.
        val oppositeNode = if (reverse) from else to
        val oppositeEdge = if (reverse) fromOutEdge else toInEdge
        val oppositeEdgeRestricted = if (reverse) (fromOutEdge != ANY_EDGE) else (toInEdge != ANY_EDGE)
        if (entry.adjNode == oppositeNode && (!oppositeEdgeRestricted || origEdgeId == oppositeEdge)) {
            if (entry.getWeightOfVisitedPath() < bestWeight) {
                bestFwdEntry = if (reverse) CHEntry(oppositeNode, 0.0) else entry
                bestBwdEntry = if (reverse) entry else CHEntry(oppositeNode, 0.0)
                bestWeight = entry.getWeightOfVisitedPath()
                return
            }
        }

        // todo: for a-star it should be possible to skip bridge node check at the beginning of the search as long as
        // the minimum source-target distance lies above total sum of fwd+bwd path candidates.
        val iter = innerExplorer.setBaseNode(entry.adjNode)
        while (iter.next()) {
            val edgeId = iter.edge
            val key = traversalMode.createTraversalId(iter, reverse)
            val entryOther = bestWeightMapOther!!.get(key) ?: continue

            val turnCostsAtBridgeNode = if (reverse)
                graph.getTurnWeight(edgeId, iter.baseNode, origEdgeId)
            else
                graph.getTurnWeight(origEdgeId, iter.baseNode, edgeId)

            val newWeight = entry.getWeightOfVisitedPath() + entryOther.getWeightOfVisitedPath() + turnCostsAtBridgeNode
            if (newWeight < bestWeight) {
                bestFwdEntry = if (reverse) entryOther else entry
                bestBwdEntry = if (reverse) entry else entryOther
                assert(bestFwdEntry!!.adjNode == bestBwdEntry!!.adjNode)
                bestWeight = newWeight
            }
        }
    }

    override fun getIncomingEdge(entry: SPTEntry): Int = (entry as CHEntry).incEdge

    override fun accept(edge: RoutingCHEdgeIteratorState, currEdge: SPTEntry, reverse: Boolean): Boolean {
        return levelEdgeFilter == null || levelEdgeFilter!!.accept(edge)
    }
}
