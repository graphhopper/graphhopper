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

import com.carrotsearch.hppc.IntObjectHashMap
import com.carrotsearch.hppc.IntObjectMap
import com.graphhopper.routing.querygraph.QueryGraph.Companion.SNAP_ADJ
import com.graphhopper.routing.querygraph.QueryGraph.Companion.SNAP_BASE
import com.graphhopper.routing.weighting.Weighting
import com.graphhopper.storage.Graph
import com.graphhopper.storage.RoutingCHEdgeExplorer
import com.graphhopper.storage.RoutingCHEdgeIterator
import com.graphhopper.storage.RoutingCHEdgeIteratorState
import com.graphhopper.storage.RoutingCHGraph
import com.graphhopper.util.EdgeIterator.Companion.NO_EDGE

/**
 * This class is used to allow routing between virtual nodes (snapped coordinates that lie between the nodes of the
 * original graph) when using CH. To use it first create a [QueryGraph] just as if you were not using CH and then
 * create an instance of the present class on top of this.
 */
class QueryRoutingCHGraph(private val routingCHGraph: RoutingCHGraph, private val queryGraph: QueryGraph) : RoutingCHGraph {
    override val weighting: Weighting = routingCHGraph.weighting
    private val queryOverlay: QueryOverlay = queryGraph.queryOverlay
    private val queryGraphWeighting: Weighting = queryGraph.wrapWeighting(weighting)

    private val virtualOutEdgesAtRealNodes: IntObjectMap<List<RoutingCHEdgeIteratorState>> =
        buildVirtualEdgesAtRealNodes(routingCHGraph.createOutEdgeExplorer())
    private val virtualInEdgesAtRealNodes: IntObjectMap<List<RoutingCHEdgeIteratorState>> =
        buildVirtualEdgesAtRealNodes(routingCHGraph.createInEdgeExplorer())
    private val virtualEdgesAtVirtualNodes: MutableList<List<RoutingCHEdgeIteratorState>> =
        buildVirtualEdgesAtVirtualNodes()

    override val nodes: Int = queryGraph.nodes

    override val edges: Int
        get() = routingCHGraph.edges + queryOverlay.numVirtualEdges

    override val shortcuts: Int
        get() = routingCHGraph.shortcuts

    override fun createInEdgeExplorer(): RoutingCHEdgeExplorer =
        createEdgeExplorer(routingCHGraph.createInEdgeExplorer(), virtualInEdgesAtRealNodes)

    override fun createOutEdgeExplorer(): RoutingCHEdgeExplorer =
        createEdgeExplorer(routingCHGraph.createOutEdgeExplorer(), virtualOutEdgesAtRealNodes)

    private fun createEdgeExplorer(explorer: RoutingCHEdgeExplorer, virtualEdgesAtRealNodes: IntObjectMap<List<RoutingCHEdgeIteratorState>>): RoutingCHEdgeExplorer {
        val iterator = VirtualCHEdgeIterator()
        return object : RoutingCHEdgeExplorer {
            override fun setBaseNode(baseNode: Int): RoutingCHEdgeIterator {
                if (isVirtualNode(baseNode)) {
                    val virtualEdges = virtualEdgesAtVirtualNodes[baseNode - routingCHGraph.nodes]
                    iterator.reset(virtualEdges)
                    return iterator
                } else {
                    val virtualEdges = virtualEdgesAtRealNodes.get(baseNode)
                    return if (virtualEdges == null) {
                        explorer.setBaseNode(baseNode)
                    } else {
                        iterator.reset(virtualEdges)
                        iterator
                    }
                }
            }
        }
    }

    override fun getEdgeIteratorState(chEdge: Int, adjNode: Int): RoutingCHEdgeIteratorState? {
        if (!isVirtualEdge(chEdge))
            return routingCHGraph.getEdgeIteratorState(chEdge, adjNode)
        // todo: possible optimization - instead of building a new virtual edge object use the ones we already
        // built for virtualEdgesAtReal/VirtualNodes
        return buildVirtualCHEdgeState(getVirtualEdgeState(chEdge, adjNode))
    }

    override fun getLevel(node: Int): Int {
        if (isVirtualNode(node))
            return Int.MAX_VALUE
        return routingCHGraph.getLevel(node)
    }

    override fun getTurnWeight(inEdge: Int, viaNode: Int, outEdge: Int): Double {
        if (!routingCHGraph.hasTurnCosts())
        // this is important as node-based algorithms might pass in ch edge ids here
            return 0.0
        return queryGraphWeighting.calcTurnWeight(inEdge, viaNode, outEdge)
    }

    override val baseGraph: Graph
        get() = queryGraph

    override fun hasTurnCosts(): Boolean = routingCHGraph.hasTurnCosts()

    override val isEdgeBased: Boolean
        get() = routingCHGraph.isEdgeBased

    override fun close() {
        routingCHGraph.close()
        virtualEdgesAtVirtualNodes.clear()
        virtualInEdgesAtRealNodes.clear()
        virtualOutEdgesAtRealNodes.clear()
    }

    private fun getVirtualEdgeState(virtualEdgeId: Int, adjNode: Int): VirtualEdgeIteratorState {
        assert(isVirtualEdge(virtualEdgeId))
        var internalVirtualEdgeId = getInternalVirtualEdgeId(virtualEdgeId)
        var virtualEdge = queryOverlay.getVirtualEdge(internalVirtualEdgeId)
        if (virtualEdge.adjNode == adjNode || adjNode == Int.MIN_VALUE)
            return virtualEdge

        internalVirtualEdgeId = QueryGraph.getPosOfReverseEdge(internalVirtualEdgeId)
        virtualEdge = queryOverlay.getVirtualEdge(internalVirtualEdgeId)
        if (virtualEdge.adjNode != adjNode)
            throw IllegalArgumentException("The virtual edge with ID $virtualEdgeId does not touch node $adjNode")

        return virtualEdge
    }

    private fun buildVirtualEdgesAtRealNodes(explorer: RoutingCHEdgeExplorer): IntObjectMap<List<RoutingCHEdgeIteratorState>> {
        val virtualEdgesAtRealNodes: IntObjectMap<List<RoutingCHEdgeIteratorState>> =
            IntObjectHashMap(queryOverlay.edgeChangesAtRealNodes.size())
        // hppc forEach(procedure) order: empty key (0) first, then slots ascending
        queryOverlay.edgeChangesAtRealNodes.forEach { node, edgeChanges ->
            val virtualEdges: MutableList<RoutingCHEdgeIteratorState> = ArrayList()
            for (v in edgeChanges.additionalEdges) {
                assert(v.baseNode == node)
                var edge = v.edge
                if (queryGraph.isVirtualEdge(edge)) {
                    edge = shiftVirtualEdgeIDForCH(edge)
                }
                virtualEdges.add(buildVirtualCHEdgeState(v, edge))
            }
            val iter = explorer.setBaseNode(node)
            while (iter.next()) {
                // shortcuts cannot be in the removed edge set because this was determined on the (base) query graph
                if (iter.isShortcut) {
                    virtualEdges.add(VirtualCHEdgeIteratorState(iter.edge, NO_EDGE,
                        iter.baseNode, iter.adjNode, iter.origEdgeKeyFirst, iter.origEdgeKeyLast,
                        iter.skippedEdge1, iter.skippedEdge2, iter.getWeight(false), iter.getWeight(true)))
                } else if (!edgeChanges.removedEdges.contains(iter.origEdge)) {
                    virtualEdges.add(VirtualCHEdgeIteratorState(iter.edge, iter.origEdge,
                        iter.baseNode, iter.adjNode, iter.origEdgeKeyFirst, iter.origEdgeKeyLast,
                        NO_EDGE, NO_EDGE, iter.getWeight(false), iter.getWeight(true)))
                }
            }
            virtualEdgesAtRealNodes.put(node, virtualEdges)
        }
        return virtualEdgesAtRealNodes
    }

    private fun buildVirtualEdgesAtVirtualNodes(): MutableList<List<RoutingCHEdgeIteratorState>> {
        val virtualNodes = queryOverlay.virtualNodes.size()
        val virtualEdgesAtVirtualNodes: MutableList<List<RoutingCHEdgeIteratorState>> = ArrayList(virtualNodes)
        for (i in 0 until virtualNodes) {
            val virtualEdges = listOf<RoutingCHEdgeIteratorState>(
                buildVirtualCHEdgeState(queryOverlay.virtualEdges[i * 4 + SNAP_BASE]),
                buildVirtualCHEdgeState(queryOverlay.virtualEdges[i * 4 + SNAP_ADJ])
            )
            virtualEdgesAtVirtualNodes.add(virtualEdges)
        }
        return virtualEdgesAtVirtualNodes
    }

    private fun buildVirtualCHEdgeState(virtualEdgeState: VirtualEdgeIteratorState): VirtualCHEdgeIteratorState {
        val virtualCHEdge = shiftVirtualEdgeIDForCH(virtualEdgeState.edge)
        return buildVirtualCHEdgeState(virtualEdgeState, virtualCHEdge)
    }

    private fun buildVirtualCHEdgeState(edgeState: VirtualEdgeIteratorState, edgeID: Int): VirtualCHEdgeIteratorState {
        val fwdWeight = queryGraphWeighting.calcEdgeWeight(edgeState, false)
        val bwdWeight = queryGraphWeighting.calcEdgeWeight(edgeState, true)
        return VirtualCHEdgeIteratorState(edgeID, edgeState.edge, edgeState.baseNode, edgeState.adjNode,
            edgeState.edgeKey, edgeState.edgeKey, NO_EDGE, NO_EDGE, fwdWeight, bwdWeight)
    }

    private fun shiftVirtualEdgeIDForCH(edge: Int): Int =
        edge + routingCHGraph.edges - routingCHGraph.baseGraph.edges

    private fun getInternalVirtualEdgeId(edge: Int): Int = 2 * (edge - routingCHGraph.edges)

    private fun isVirtualNode(node: Int): Boolean = node >= routingCHGraph.nodes

    private fun isVirtualEdge(edge: Int): Boolean = edge >= routingCHGraph.edges

    private class VirtualCHEdgeIteratorState(
        override val edge: Int,
        override val origEdge: Int,
        override val baseNode: Int,
        override val adjNode: Int,
        override val origEdgeKeyFirst: Int,
        override val origEdgeKeyLast: Int,
        override val skippedEdge1: Int,
        override val skippedEdge2: Int,
        private val weightFwd: Double,
        private val weightBwd: Double
    ) : RoutingCHEdgeIteratorState {

        override val isShortcut: Boolean
            get() = origEdge == NO_EDGE

        override fun getWeight(reverse: Boolean): Double = if (reverse) weightBwd else weightFwd

        override fun toString(): String =
            "virtual: $edge: $baseNode->$adjNode, orig: $origEdge, weightFwd: $weightFwd, weightBwd: $weightBwd"
    }

    private class VirtualCHEdgeIterator : RoutingCHEdgeIterator {
        private var edges: List<RoutingCHEdgeIteratorState>? = null
        private var current = -1

        override fun next(): Boolean {
            current++
            return current < edges!!.size
        }

        fun reset(edges: List<RoutingCHEdgeIteratorState>) {
            this.edges = edges
            current = -1
        }

        override val edge: Int
            get() = getCurrent().edge

        override val origEdge: Int
            get() = getCurrent().origEdge

        override val origEdgeKeyFirst: Int
            get() = getCurrent().origEdgeKeyFirst

        override val origEdgeKeyLast: Int
            get() = getCurrent().origEdgeKeyLast

        override val baseNode: Int
            get() = getCurrent().baseNode

        override val adjNode: Int
            get() = getCurrent().adjNode

        override val isShortcut: Boolean
            get() = getCurrent().isShortcut

        override val skippedEdge1: Int
            get() {
                if (!isShortcut)
                    throw IllegalStateException("Skipped edges are only available for shortcuts")
                return getCurrent().skippedEdge1
            }

        override val skippedEdge2: Int
            get() {
                if (!isShortcut)
                    throw IllegalStateException("Skipped edges are only available for shortcuts")
                return getCurrent().skippedEdge2
            }

        override fun getWeight(reverse: Boolean): Double = getCurrent().getWeight(reverse)

        override fun toString(): String {
            if (current < 0)
                return "not started"
            return edges!![current].toString() + ", current: " + (current + 1) + "/" + edges!!.size
        }

        private fun getCurrent(): RoutingCHEdgeIteratorState = edges!![current]
    }
}
