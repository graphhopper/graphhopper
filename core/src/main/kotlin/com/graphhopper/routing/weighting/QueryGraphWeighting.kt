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

package com.graphhopper.routing.weighting

import com.graphhopper.coll.primitive.IntArrayList
import androidx.collection.MutableIntLongMap
import com.graphhopper.coll.primitive.IntDoubleHashMap
import com.graphhopper.routing.querygraph.QueryGraph
import com.graphhopper.routing.querygraph.VirtualEdgeIterator
import com.graphhopper.routing.querygraph.VirtualEdgeIteratorState
import com.graphhopper.storage.BaseGraph
import com.graphhopper.util.EdgeIterator
import com.graphhopper.util.EdgeIteratorState

/**
 * Whenever a [QueryGraph] is used for shortest path calculations including turn costs we need to wrap the
 * [Weighting] we want to use with this class. Otherwise turn costs at virtual nodes and/or including virtual
 * edges will not be calculated correctly.
 */
class QueryGraphWeighting(
    private val graph: BaseGraph,
    private val weighting: Weighting,
    private val closestEdges: IntArrayList,
    private val virtualWeightsByEdgeKey: IntDoubleHashMap,
    private val virtualTimesByEdgeKey: MutableIntLongMap
) : Weighting {
    private val firstVirtualNodeId: Int = graph.nodes
    private val firstVirtualEdgeId: Int = graph.edges

    override fun calcMinWeightPerDistance(): Double {
        return weighting.calcMinWeightPerDistance()
    }

    override fun calcEdgeWeight(edgeState: EdgeIteratorState, reverse: Boolean): Double {
        if (isVirtualEdge(edgeState.edge) && !edgeState.get(EdgeIteratorState.UNFAVORED_EDGE)) {
            return if (edgeState is VirtualEdgeIteratorState)
                virtualWeightsByEdgeKey.get(if (reverse) edgeState.reverseEdgeKey else edgeState.edgeKey)
            else if (edgeState is VirtualEdgeIterator)
                virtualWeightsByEdgeKey.get(if (reverse) edgeState.reverseEdgeKey else edgeState.edgeKey)
            else
                throw IllegalStateException("Unexpected virtual edge state: $edgeState")
        }
        return weighting.calcEdgeWeight(edgeState, reverse)
    }

    override fun calcTurnWeight(inEdge: Int, viaNode: Int, outEdge: Int): Double {
        if (!EdgeIterator.Edge.isValid(inEdge) || !EdgeIterator.Edge.isValid(outEdge)) {
            return 0.0
        }
        if (isVirtualNode(viaNode)) {
            return if (isUTurn(inEdge, outEdge)) {
                // do not allow u-turns at virtual nodes, otherwise the route depends on whether or not there are
                // virtual via nodes, see #1672. note since we are turning between virtual edges here we need to compare
                // the *virtual* edge ids (the orig edge would always be the same for all virtual edges at a virtual
                // node), see #1593
                Double.POSITIVE_INFINITY
            } else {
                0.0
            }
        }
        return getMinWeightAndOriginalEdges(inEdge, viaNode, outEdge).minTurnWeight
    }

    private fun getMinWeightAndOriginalEdges(inEdge: Int, viaNode: Int, outEdge: Int): Result {
        // to calculate the actual turn costs or detect u-turns we need to look at the original edge of each virtual
        // edge, see #1593
        val result = Result()
        if (isVirtualEdge(inEdge) && isVirtualEdge(outEdge)) {
            val innerExplorer = graph.createEdgeExplorer()
            graph.forEdgeAndCopiesOfEdge(graph.createEdgeExplorer(), viaNode, getOriginalEdge(inEdge)) { p ->
                graph.forEdgeAndCopiesOfEdge(innerExplorer, viaNode, getOriginalEdge(outEdge)) { q ->
                    val w = weighting.calcTurnWeight(p, viaNode, q)
                    if (w < result.minTurnWeight) {
                        result.origInEdge = p
                        result.origOutEdge = q
                        result.minTurnWeight = w
                    }
                }
            }
        } else if (isVirtualEdge(inEdge)) {
            graph.forEdgeAndCopiesOfEdge(graph.createEdgeExplorer(), viaNode, getOriginalEdge(inEdge)) { e ->
                val w = weighting.calcTurnWeight(e, viaNode, outEdge)
                if (w < result.minTurnWeight) {
                    result.origInEdge = e
                    result.origOutEdge = outEdge
                    result.minTurnWeight = w
                }
            }
        } else if (isVirtualEdge(outEdge)) {
            graph.forEdgeAndCopiesOfEdge(graph.createEdgeExplorer(), viaNode, getOriginalEdge(outEdge)) { e ->
                val w = weighting.calcTurnWeight(inEdge, viaNode, e)
                if (w < result.minTurnWeight) {
                    result.origInEdge = inEdge
                    result.origOutEdge = e
                    result.minTurnWeight = w
                }
            }
        } else {
            result.origInEdge = inEdge
            result.origOutEdge = outEdge
            result.minTurnWeight = weighting.calcTurnWeight(inEdge, viaNode, outEdge)
        }
        return result
    }

    private fun isUTurn(inEdge: Int, outEdge: Int): Boolean {
        return inEdge == outEdge
    }

    override fun calcEdgeMillis(edgeState: EdgeIteratorState, reverse: Boolean): Long {
        if (isVirtualEdge(edgeState.edge) && !edgeState.get(EdgeIteratorState.UNFAVORED_EDGE)) {
            return if (edgeState is VirtualEdgeIteratorState)
                virtualTimesByEdgeKey.getOrDefault(if (reverse) edgeState.reverseEdgeKey else edgeState.edgeKey, 0L)
            else if (edgeState is VirtualEdgeIterator)
                virtualTimesByEdgeKey.getOrDefault(if (reverse) edgeState.reverseEdgeKey else edgeState.edgeKey, 0L)
            else
                throw IllegalStateException("Unexpected virtual edge state: $edgeState")
        }
        return weighting.calcEdgeMillis(edgeState, reverse)
    }

    override fun calcTurnMillis(inEdge: Int, viaNode: Int, outEdge: Int): Long {
        return if (isVirtualNode(viaNode))
        // see calcTurnWeight
            0
        else {
            // we want the turn time given by the actual weighting for the edges with minimum weight
            // (the same ones that would be selected when routing)
            val result = getMinWeightAndOriginalEdges(inEdge, viaNode, outEdge)
            weighting.calcTurnMillis(result.origInEdge, viaNode, result.origOutEdge)
        }
    }

    override fun hasTurnCosts(): Boolean {
        return weighting.hasTurnCosts()
    }

    override val name: String
        get() = weighting.name

    override fun toString(): String {
        return name
    }

    private fun getOriginalEdge(edge: Int): Int {
        return closestEdges.get((edge - firstVirtualEdgeId) / 2)
    }

    private fun isVirtualNode(node: Int): Boolean {
        return node >= firstVirtualNodeId
    }

    private fun isVirtualEdge(edge: Int): Boolean {
        return edge >= firstVirtualEdgeId
    }

    private class Result {
        @JvmField
        var origInEdge = -1
        @JvmField
        var origOutEdge = -1
        @JvmField
        var minTurnWeight = Double.POSITIVE_INFINITY
    }
}
