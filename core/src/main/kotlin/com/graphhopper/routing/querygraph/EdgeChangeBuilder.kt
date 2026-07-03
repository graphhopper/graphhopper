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

import com.carrotsearch.hppc.IntArrayList
import com.carrotsearch.hppc.IntObjectMap
import com.carrotsearch.hppc.procedures.IntProcedure
import com.graphhopper.coll.GHIntHashSet
import com.graphhopper.routing.querygraph.QueryGraph.Companion.ADJ_SNAP
import com.graphhopper.routing.querygraph.QueryGraph.Companion.BASE_SNAP
import com.graphhopper.routing.querygraph.QueryGraph.Companion.SNAP_ADJ
import com.graphhopper.routing.querygraph.QueryGraph.Companion.SNAP_BASE

/**
 * Helper class for [QueryOverlayBuilder]
 *
 * @see build
 */
internal class EdgeChangeBuilder private constructor(
    private val closestEdges: IntArrayList,
    private val virtualEdges: List<VirtualEdgeIteratorState>,
    private val firstVirtualNodeId: Int,
    edgeChangesAtRealNodes: IntObjectMap<QueryOverlay.EdgeChanges>
) {
    private val edgeChangesAtRealNodes: IntObjectMap<QueryOverlay.EdgeChanges>

    init {
        if (!edgeChangesAtRealNodes.isEmpty) {
            throw IllegalArgumentException("real node modifications need to be empty")
        }
        this.edgeChangesAtRealNodes = edgeChangesAtRealNodes
    }

    companion object {
        /**
         * Builds a mapping between real node ids and the set of changes for their adjacent edges.
         *
         * @param edgeChangesAtRealNodes output parameter, you need to pass an empty & modifiable map and the results will
         *                               be added to it
         */
        @JvmStatic
        fun build(closestEdges: IntArrayList, virtualEdges: List<VirtualEdgeIteratorState>, firstVirtualNodeId: Int, edgeChangesAtRealNodes: IntObjectMap<QueryOverlay.EdgeChanges>) {
            EdgeChangeBuilder(closestEdges, virtualEdges, firstVirtualNodeId, edgeChangesAtRealNodes).build()
        }
    }

    private fun build() {
        val towerNodesToChange = GHIntHashSet(getNumVirtualNodes())

        // 1. for every real node adjacent to a virtual one we collect the virtual edges, also build a set of
        //    these adjacent real nodes so we can use them in the next step
        for (i in 0 until getNumVirtualNodes()) {
            // base node
            val baseRevEdge = getVirtualEdge(i * 4 + SNAP_BASE)
            var towerNode = baseRevEdge.adjNode
            if (!isVirtualNode(towerNode)) {
                towerNodesToChange.add(towerNode)
                addVirtualEdges(true, towerNode, i)
            }

            // adj node
            val adjEdge = getVirtualEdge(i * 4 + SNAP_ADJ)
            towerNode = adjEdge.adjNode
            if (!isVirtualNode(towerNode)) {
                towerNodesToChange.add(towerNode)
                addVirtualEdges(false, towerNode, i)
            }
        }

        // 2. build the list of removed edges for all real nodes adjacent to virtual ones
        towerNodesToChange.forEach(IntProcedure { value ->
            addRemovedEdges(value)
        })
    }

    /**
     * Adds the virtual edges adjacent to the real tower nodes
     */
    private fun addVirtualEdges(base: Boolean, node: Int, virtNode: Int) {
        var edgeChanges = edgeChangesAtRealNodes.get(node)
        if (edgeChanges == null) {
            edgeChanges = QueryOverlay.EdgeChanges(2, 2)
            edgeChangesAtRealNodes.put(node, edgeChanges)
        }
        val edge = if (base)
            getVirtualEdge(virtNode * 4 + BASE_SNAP)
        else
            getVirtualEdge(virtNode * 4 + ADJ_SNAP)
        edgeChanges.additionalEdges.add(edge)
    }

    /**
     * Adds the ids of the removed edges at the real tower nodes. We need to do this such that we cannot 'skip'
     * virtual nodes by just using the original edges and also to prevent u-turns at the real nodes adjacent to the
     * virtual ones.
     */
    private fun addRemovedEdges(towerNode: Int) {
        if (isVirtualNode(towerNode))
            throw IllegalStateException("Node should not be virtual:$towerNode, $edgeChangesAtRealNodes")

        val edgeChanges = edgeChangesAtRealNodes.get(towerNode)
        val existingEdges = edgeChanges.additionalEdges
        val removedEdges = edgeChanges.removedEdges
        for (existingEdge in existingEdges) {
            removedEdges.add(getClosestEdge(existingEdge.adjNode))
        }
    }

    private fun isVirtualNode(nodeId: Int): Boolean = nodeId >= firstVirtualNodeId

    private fun getNumVirtualNodes(): Int = closestEdges.size()

    private fun getClosestEdge(node: Int): Int = closestEdges.get(node - firstVirtualNodeId)

    private fun getVirtualEdge(virtualEdgeId: Int): VirtualEdgeIteratorState = virtualEdges[virtualEdgeId]
}
