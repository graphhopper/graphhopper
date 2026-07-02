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

package com.graphhopper.storage

import com.graphhopper.routing.weighting.Weighting

class RoutingCHGraphImpl(
    override val baseGraph: BaseGraph,
    @get:JvmName("getCHStorage") val chStorage: CHStorage,
    override val weighting: Weighting
) : RoutingCHGraph {

    init {
        if (weighting.hasTurnCosts() && !chStorage.isEdgeBased)
            throw IllegalArgumentException("Weighting has turn costs, but CHStorage is node-based")
    }

    override val nodes: Int
        get() = baseGraph.nodes

    override val edges: Int
        get() = baseGraph.edges + chStorage.getShortcuts()

    override val shortcuts: Int
        get() = chStorage.getShortcuts()

    override fun createInEdgeExplorer(): RoutingCHEdgeExplorer =
        RoutingCHEdgeIteratorImpl.inEdges(chStorage, baseGraph, weighting)

    override fun createOutEdgeExplorer(): RoutingCHEdgeExplorer =
        RoutingCHEdgeIteratorImpl.outEdges(chStorage, baseGraph, weighting)

    override fun getEdgeIteratorState(chEdge: Int, adjNode: Int): RoutingCHEdgeIteratorState? {
        val edgeState =
            RoutingCHEdgeIteratorStateImpl(chStorage, baseGraph, BaseGraph.EdgeIteratorStateImpl(baseGraph), weighting)
        if (edgeState.init(chEdge, adjNode))
            return edgeState
        // if edgeId exists, but adjacent nodes do not match
        return null
    }

    override fun getLevel(node: Int): Int = chStorage.getLevel(chStorage.toNodePointer(node))

    override fun hasTurnCosts(): Boolean = weighting.hasTurnCosts()

    override val isEdgeBased: Boolean
        get() = chStorage.isEdgeBased

    override fun getTurnWeight(inEdge: Int, viaNode: Int, outEdge: Int): Double =
        weighting.calcTurnWeight(inEdge, viaNode, outEdge)

    override fun close() {
        if (!baseGraph.isClosed) baseGraph.close()
        chStorage.close()
    }

    companion object {
        @JvmStatic
        fun fromGraph(baseGraph: BaseGraph, chStorage: CHStorage, chConfig: CHConfig): RoutingCHGraph =
            RoutingCHGraphImpl(baseGraph, chStorage, chConfig.weighting)
    }
}
