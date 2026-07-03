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
import com.graphhopper.coll.GHIntObjectHashMap
import com.graphhopper.routing.util.AllEdgesIterator
import com.graphhopper.routing.util.EdgeFilter
import com.graphhopper.routing.weighting.QueryGraphWeighting
import com.graphhopper.routing.weighting.Weighting
import com.graphhopper.storage.BaseGraph
import com.graphhopper.storage.ExtendedNodeAccess
import com.graphhopper.storage.Graph
import com.graphhopper.storage.NodeAccess
import com.graphhopper.storage.TurnCostStorage
import com.graphhopper.storage.index.Snap
import com.graphhopper.util.EdgeExplorer
import com.graphhopper.util.EdgeIterator
import com.graphhopper.util.EdgeIteratorState
import com.graphhopper.util.GHUtility
import com.graphhopper.util.shapes.BBox

/**
 * A class which is used to query the underlying graph with real GPS points. It does so by
 * introducing virtual nodes and edges. It is lightweight in order to be created every time a new
 * query comes in, which makes the behaviour thread safe.
 *
 * Calling any `create` method creates virtual edges between the tower nodes of the existing
 * graph and new virtual tower nodes. Every virtual node has two adjacent nodes and is connected
 * to each adjacent nodes via 2 virtual edges with opposite base node / adjacent node encoding.
 * However, the edge explorer returned by [createEdgeExplorer] only returns two
 * virtual edges per virtual node (the ones with correct base node).
 *
 * @author Peter Karich
 */
class QueryGraph private constructor(graph: BaseGraph, snaps: List<Snap>) : Graph {
    override val baseGraph: BaseGraph = graph
    private val baseNodes: Int = graph.nodes
    private val baseEdges: Int = graph.edges
    internal val queryOverlay: QueryOverlay = QueryOverlayBuilder.build(graph, snaps)
    override val nodeAccess: NodeAccess = ExtendedNodeAccess(graph.nodeAccess, queryOverlay.virtualNodes, baseNodes)
    override val turnCostStorage: TurnCostStorage? = baseGraph.turnCostStorage

    // Use LinkedHashSet for predictable iteration order.
    private val unfavoredEdges: MutableSet<VirtualEdgeIteratorState> = LinkedHashSet(5)
    private val virtualEdgesAtRealNodes: GHIntObjectHashMap<List<EdgeIteratorState>>
    private val virtualEdgesAtVirtualNodes: List<List<EdgeIteratorState>>

    init {
        // build data structures holding the virtual edges at all real/virtual nodes that are modified compared to the
        // mainGraph.
        val mainExplorer = baseGraph.createEdgeExplorer()
        virtualEdgesAtRealNodes = buildVirtualEdgesAtRealNodes(mainExplorer)
        virtualEdgesAtVirtualNodes = buildVirtualEdgesAtVirtualNodes()
    }

    companion object {
        internal const val BASE_SNAP = 0
        internal const val SNAP_BASE = 1
        internal const val SNAP_ADJ = 2
        internal const val ADJ_SNAP = 3

        @JvmStatic
        fun create(graph: BaseGraph, snap: Snap): QueryGraph = create(graph, listOf(snap))

        @JvmStatic
        fun create(graph: BaseGraph, fromSnap: Snap, toSnap: Snap): QueryGraph =
            create(graph.baseGraph, listOf(fromSnap, toSnap))

        @JvmStatic
        fun create(graph: BaseGraph, snaps: List<Snap>): QueryGraph = QueryGraph(graph, snaps)

        // find reverse edge via convention. see virtualEdges comment above
        internal fun getPosOfReverseEdge(edgeId: Int): Int =
            if (edgeId % 2 == 0) edgeId + 1 else edgeId - 1
    }

    fun isVirtualEdge(edgeId: Int): Boolean = edgeId >= baseEdges

    fun isVirtualNode(nodeId: Int): Boolean = nodeId >= baseNodes

    /**
     * Assigns the 'unfavored' flag to the given virtual edges (for both directions)
     */
    fun unfavorVirtualEdges(edgeIds: IntArrayList) {
        for (c in edgeIds) {
            val virtualEdgeId = c.value
            if (!isVirtualEdge(virtualEdgeId))
                return
            val edge = getVirtualEdge(getInternalVirtualEdgeId(virtualEdgeId))
            edge.setUnfavored(true)
            unfavoredEdges.add(edge)
            // we have to set the unfavored flag also for the virtual edge state that is used when we discover the same edge
            // from the adjacent node. note that the unfavored flag will be set for both 'directions' of the same edge state.
            val reverseEdge = getVirtualEdge(getPosOfReverseEdge(getInternalVirtualEdgeId(virtualEdgeId)))
            reverseEdge.setUnfavored(true)
            unfavoredEdges.add(reverseEdge)
        }
    }

    /**
     * Returns all virtual edges that have been unfavored via [unfavorVirtualEdges]
     */
    val unfavoredVirtualEdges: Set<EdgeIteratorState>
        // Need to create a new set to convert Set<VirtualEdgeIteratorState> to
        // Set<EdgeIteratorState>.
        get() = LinkedHashSet<EdgeIteratorState>(unfavoredEdges)

    /**
     * Removes the 'unfavored' status of all virtual edges.
     */
    fun clearUnfavoredStatus() {
        for (edge in unfavoredEdges) {
            edge.setUnfavored(false)
        }
        unfavoredEdges.clear()
    }

    override val nodes: Int
        get() = queryOverlay.virtualNodes.size() + baseNodes

    override val edges: Int
        get() = queryOverlay.numVirtualEdges / 2 + baseEdges

    override val bounds: BBox
        get() = baseGraph.bounds

    override fun getEdgeIteratorState(edgeId: Int, adjNode: Int): EdgeIteratorState? {
        if (!isVirtualEdge(edgeId))
            return baseGraph.getEdgeIteratorState(edgeId, adjNode)

        var internalEdgeId = getInternalVirtualEdgeId(edgeId)
        val eis = getVirtualEdge(internalEdgeId)
        if (eis.adjNode == adjNode || adjNode == Int.MIN_VALUE)
            return eis
        internalEdgeId = getPosOfReverseEdge(internalEdgeId)

        val eis2 = getVirtualEdge(internalEdgeId)
        if (eis2.adjNode == adjNode)
            return eis2
        throw IllegalStateException("Edge " + edgeId + " not found with adjNode:" + adjNode
                + ". found edges were:" + eis + ", " + eis2)
    }

    override fun getEdgeIteratorStateForKey(edgeKey: Int): EdgeIteratorState {
        val edge = GHUtility.getEdgeFromEdgeKey(edgeKey)
        if (!isVirtualEdge(edge))
            return baseGraph.getEdgeIteratorStateForKey(edgeKey)
        return getVirtualEdge(edgeKey - 2 * baseEdges)
    }

    private fun getVirtualEdge(edgeId: Int): VirtualEdgeIteratorState = queryOverlay.getVirtualEdge(edgeId)

    private fun getInternalVirtualEdgeId(origEdgeId: Int): Int = 2 * (origEdgeId - baseEdges)

    override fun createEdgeExplorer(filter: EdgeFilter): EdgeExplorer {
        // re-use these objects between setBaseNode calls to prevent GC
        val mainExplorer = baseGraph.createEdgeExplorer(filter)
        val virtualEdgeIterator = VirtualEdgeIterator(filter, null)
        return object : EdgeExplorer {
            override fun setBaseNode(baseNode: Int): EdgeIterator {
                if (isVirtualNode(baseNode)) {
                    val virtualEdges = virtualEdgesAtVirtualNodes[baseNode - baseNodes]
                    return virtualEdgeIterator.reset(virtualEdges)
                } else {
                    val virtualEdges = virtualEdgesAtRealNodes.get(baseNode)
                    return if (virtualEdges == null) {
                        mainExplorer.setBaseNode(baseNode)
                    } else {
                        virtualEdgeIterator.reset(virtualEdges)
                    }
                }
            }
        }
    }

    private fun buildVirtualEdgesAtRealNodes(mainExplorer: EdgeExplorer): GHIntObjectHashMap<List<EdgeIteratorState>> {
        val virtualEdgesAtRealNodes: GHIntObjectHashMap<List<EdgeIteratorState>> =
            GHIntObjectHashMap(queryOverlay.edgeChangesAtRealNodes.size())
        // hppc forEach(procedure) order: empty key (0) first, then slots ascending
        queryOverlay.edgeChangesAtRealNodes.forEach { node, edgeChanges ->
            val virtualEdges: MutableList<EdgeIteratorState> = ArrayList(edgeChanges.additionalEdges)
            val mainIter = mainExplorer.setBaseNode(node)
            while (mainIter.next()) {
                if (!edgeChanges.removedEdges.contains(mainIter.edge)) {
                    virtualEdges.add(mainIter.detach(false))
                }
            }
            virtualEdgesAtRealNodes.put(node, virtualEdges)
        }
        return virtualEdgesAtRealNodes
    }

    private fun buildVirtualEdgesAtVirtualNodes(): List<List<EdgeIteratorState>> {
        val virtualEdgesAtVirtualNodes: MutableList<List<EdgeIteratorState>> = ArrayList()
        for (i in 0 until queryOverlay.virtualNodes.size()) {
            val virtualEdges = listOf<EdgeIteratorState>(
                queryOverlay.getVirtualEdge(i * 4 + SNAP_BASE),
                queryOverlay.getVirtualEdge(i * 4 + SNAP_ADJ)
            )
            virtualEdgesAtVirtualNodes.add(virtualEdges)
        }
        return virtualEdgesAtVirtualNodes
    }

    override val allEdges: AllEdgesIterator
        get() = throw UnsupportedOperationException("Not supported yet.")

    override fun edge(a: Int, b: Int): EdgeIteratorState = throw exc()

    override fun wrapWeighting(weighting: Weighting): Weighting {
        if (weighting is QueryGraphWeighting)
            return weighting
        val result = QueryOverlay.calcAdjustedVirtualWeightsAndTimes(queryOverlay, baseGraph, weighting)
        return QueryGraphWeighting(baseGraph, weighting, queryOverlay.closestEdges, result.weights, result.times)
    }

    override fun getOtherNode(edge: Int, node: Int): Int {
        if (isVirtualEdge(edge)) {
            return getEdgeIteratorState(edge, node)!!.baseNode
        }
        return baseGraph.getOtherNode(edge, node)
    }

    override fun isAdjacentToNode(edge: Int, node: Int): Boolean {
        if (isVirtualEdge(edge)) {
            val virtualEdge = getEdgeIteratorState(edge, node)!!
            return virtualEdge.baseNode == node || virtualEdge.adjNode == node
        }
        return baseGraph.isAdjacentToNode(edge, node)
    }

    @JvmName("getVirtualEdges")
    internal fun getVirtualEdges(): List<VirtualEdgeIteratorState> = queryOverlay.virtualEdges

    private fun exc(): UnsupportedOperationException =
        UnsupportedOperationException("QueryGraph cannot be modified.")
}
