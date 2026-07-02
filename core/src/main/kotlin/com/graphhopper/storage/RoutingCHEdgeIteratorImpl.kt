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

import com.graphhopper.routing.util.EdgeFilter
import com.graphhopper.routing.weighting.Weighting
import com.graphhopper.util.EdgeIterator
import com.graphhopper.util.EdgeIteratorState

class RoutingCHEdgeIteratorImpl(chStore: CHStorage, baseGraph: BaseGraph, weighting: Weighting, private val outgoing: Boolean, private val incoming: Boolean) :
    RoutingCHEdgeIteratorStateImpl(chStore, baseGraph, BaseGraph.EdgeIteratorImpl(baseGraph, EdgeFilter.ALL_EDGES), weighting),
    RoutingCHEdgeExplorer, RoutingCHEdgeIterator {

    private val baseIterator: BaseGraph.EdgeIteratorImpl = baseEdgeState as BaseGraph.EdgeIteratorImpl
    private var nextEdgeId = 0

    internal override fun edgeState(): EdgeIteratorState = baseIterator

    override fun setBaseNode(baseNode: Int): RoutingCHEdgeIterator {
        assert(baseGraph.isFrozen)
        baseIterator.setBaseNode(baseNode)
        val lastShortcut = store.getLastShortcut(store.toNodePointer(baseNode))
        edgeId = if (lastShortcut < 0) baseIterator.edgeId else baseGraph.edges + lastShortcut
        nextEdgeId = edgeId
        return this
    }

    override fun next(): Boolean {
        // we first traverse shortcuts (in decreasing order) and when we are done we use the base iterator to traverse
        // the base edges as well. shortcuts are filtered using shortcutFilter, but base edges are only filtered by
        // access/finite weight.
        while (nextEdgeId >= baseGraph.edges) {
            shortcutPointer = store.toShortcutPointer(nextEdgeId - baseGraph.edges)
            _baseNode = store.getNodeA(shortcutPointer)
            _adjNode = store.getNodeB(shortcutPointer)
            edgeId = nextEdgeId
            nextEdgeId--
            if (nextEdgeId < baseGraph.edges || store.getNodeA(store.toShortcutPointer(nextEdgeId - baseGraph.edges)) != _baseNode)
                nextEdgeId = baseIterator.edgeId
            // todo: note that it would be more efficient (but cost more memory) to separate in/out edges,
            //       especially for edge-based where we do not use bidirectional shortcuts
            // this is needed for edge-based CH, see #1525
            // background: we need to explicitly accept shortcut edges that are loops so they will be
            // found as 'incoming' edges no matter which directions we are looking at
            // todo: or maybe this is not really needed as edge-based shortcuts are not bidirectional anyway?
            if ((_baseNode == _adjNode && (store.getFwdAccess(shortcutPointer) || store.getBwdAccess(shortcutPointer))) ||
                (outgoing && store.getFwdAccess(shortcutPointer) || incoming && store.getBwdAccess(shortcutPointer)))
                return true
        }

        // similar to baseIterator.next(), but we apply our own filter and set edgeId
        while (EdgeIterator.Edge.isValid(baseIterator.nextEdgeId)) {
            baseIterator.goToNext()
            // we update edgeId even when iterating base edges. is it faster to do this also for base/adjNode?
            edgeId = baseIterator.edgeId
            if ((outgoing && finiteWeight(false)) || (incoming && finiteWeight(true)))
                return true
        }
        return false
    }

    override fun toString(): String = "$edge $baseNode-$adjNode"

    private fun finiteWeight(reverse: Boolean): Boolean = !getOrigEdgeWeight(reverse).isInfinite()

    companion object {
        @JvmStatic
        fun outEdges(chStore: CHStorage, baseGraph: BaseGraph, weighting: Weighting): RoutingCHEdgeIteratorImpl =
            RoutingCHEdgeIteratorImpl(chStore, baseGraph, weighting, true, false)

        @JvmStatic
        fun inEdges(chStore: CHStorage, baseGraph: BaseGraph, weighting: Weighting): RoutingCHEdgeIteratorImpl =
            RoutingCHEdgeIteratorImpl(chStore, baseGraph, weighting, false, true)
    }
}
