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
import com.graphhopper.util.EdgeIterator.Companion.NO_EDGE
import com.graphhopper.util.EdgeIteratorState

open class RoutingCHEdgeIteratorStateImpl internal constructor(
    @JvmField internal val store: CHStorage,
    @JvmField internal val baseGraph: BaseGraph,
    @JvmField internal val baseEdgeState: BaseGraph.EdgeIteratorStateImpl,
    private val weighting: Weighting
) : RoutingCHEdgeIteratorState {

    @JvmField
    internal var edgeId = -1

    @JvmField
    internal var _baseNode = 0

    @JvmField
    internal var _adjNode = 0

    @JvmField
    internal var shortcutPointer = -1L

    internal fun init(edge: Int, expectedAdjNode: Int): Boolean {
        if (edge < 0 || edge >= baseGraph.edges + store.getShortcuts())
            throw IllegalArgumentException("edge must be in bounds: [0," + (baseGraph.edges + store.getShortcuts()) + "[")
        edgeId = edge
        if (isShortcut) {
            shortcutPointer = store.toShortcutPointer(edge - baseGraph.edges)
            _baseNode = store.getNodeA(shortcutPointer)
            _adjNode = store.getNodeB(shortcutPointer)

            if (expectedAdjNode == _adjNode || expectedAdjNode == Int.MIN_VALUE) {
                return true
            } else if (expectedAdjNode == _baseNode) {
                _baseNode = _adjNode
                _adjNode = expectedAdjNode
                return true
            }
            return false
        } else {
            return baseEdgeState.init(edge, expectedAdjNode)
        }
    }

    override val edge: Int
        // we maintain this even for base edges, maybe try if not maintaining it is faster
        get() = edgeId

    override val origEdge: Int
        get() = if (isShortcut) NO_EDGE else edgeState().edge

    override val origEdgeKeyFirst: Int
        get() {
            if (!isShortcut || !store.isEdgeBased)
                return edgeState().edgeKey
            return store.getOrigEdgeKeyFirst(shortcutPointer)
        }

    override val origEdgeKeyLast: Int
        get() {
            if (!isShortcut || !store.isEdgeBased)
                return edgeState().edgeKey
            return store.getOrigEdgeKeyLast(shortcutPointer)
        }

    override val baseNode: Int
        get() = if (isShortcut) _baseNode else edgeState().baseNode

    override val adjNode: Int
        get() = if (isShortcut) _adjNode else edgeState().adjNode

    override val isShortcut: Boolean
        get() = edgeId >= baseGraph.edges

    override val skippedEdge1: Int
        get() {
            checkShortcut(true, "getSkippedEdge1")
            return store.getSkippedEdge1(shortcutPointer)
        }

    override val skippedEdge2: Int
        get() {
            checkShortcut(true, "getSkippedEdge2")
            return store.getSkippedEdge2(shortcutPointer)
        }

    override fun getWeight(reverse: Boolean): Double {
        if (isShortcut) {
            return store.getWeight(shortcutPointer)
        } else {
            return getOrigEdgeWeight(reverse)
        }
    }

    internal fun getOrigEdgeWeight(reverse: Boolean): Double =
        weighting.calcEdgeWeight(getBaseGraphEdgeState(), reverse)

    private fun getBaseGraphEdgeState(): EdgeIteratorState {
        checkShortcut(false, "getBaseGraphEdgeState")
        return edgeState()
    }

    internal open fun edgeState(): EdgeIteratorState {
        // use this only via this getter method as it might have been overwritten
        return baseEdgeState
    }

    internal fun checkShortcut(shouldBeShortcut: Boolean, methodName: String) {
        if (isShortcut) {
            if (!shouldBeShortcut)
                throw IllegalStateException("Cannot call $methodName on shortcut $edge")
        } else if (shouldBeShortcut)
            throw IllegalStateException("Method $methodName only for shortcuts $edge")
    }
}
