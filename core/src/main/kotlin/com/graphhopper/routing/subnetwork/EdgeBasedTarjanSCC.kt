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

package com.graphhopper.routing.subnetwork

import androidx.collection.CircularIntArray
import androidx.collection.MutableIntIntMap
import androidx.collection.MutableIntSet
import com.graphhopper.coll.GrowableBitSet
import com.graphhopper.coll.primitive.IntArrayList
import com.graphhopper.coll.primitive.IntContainer
import com.graphhopper.coll.primitive.LongArrayDeque
import com.graphhopper.routing.util.TraversalMode
import com.graphhopper.storage.Graph
import com.graphhopper.util.BitUtil
import com.graphhopper.util.EdgeIteratorState
import com.graphhopper.util.GHUtility.getEdgeFromEdgeKey
import kotlin.math.max
import kotlin.math.min

/**
 * Edge-based version of Tarjan's algorithm to find strongly connected components on a directed graph. Compared
 * to the more traditional node-based version that traverses the nodes of the graph this version works directly with
 * the edges. This way its possible to take into account possible turn restrictions.
 *
 * The algorithm is of course very similar to the node-based version and it might be possible to reuse some code between
 * the two, but especially the version with an explicit stack needs different 'state' information and loops required
 * some special treatment as well (this was written when base graph could still have loops!).
 *
 * @author easbar
 * @see TarjanSCC
 */
class EdgeBasedTarjanSCC private constructor(
    private val graph: Graph,
    private val edgeTransitionFilter: EdgeTransitionFilter,
    private val consumer: SCCConsumer
) {
    private val explorer = graph.createEdgeExplorer()
    private val bitUtil = BitUtil.LITTLE
    private val tarjanStack = CircularIntArray()
    private val dfsStackPQ = LongArrayDeque()
    private val dfsStackAdj = CircularIntArray()
    private lateinit var edgeKeyIndex: TarjanIntIntMap
    private lateinit var edgeKeyLowLink: TarjanIntIntMap
    private lateinit var edgeKeyOnStack: TarjanIntSet

    private var currIndex = 0
    private var p = 0
    private var q = 0
    private var adj = 0
    private lateinit var dfsState: State

    companion object {
        /**
         * Runs Tarjan's algorithm using an explicit stack.
         *
         * @param edgeTransitionFilter        Only edge transitions accepted by this filter will be considered when we explore the graph.
         *                                    If a turn is not accepted the corresponding path will be ignored (edges that are only connected
         *                                    by a path with such a turn will not be considered to belong to the same component)
         * @param excludeSingleEdgeComponents if set to true components that only contain a single edge will not be
         *                                    returned when calling [findComponents] or [findComponentsRecursive],
         *                                    which can be useful to save some memory.
         */
        @JvmStatic
        fun findComponents(graph: Graph, edgeTransitionFilter: EdgeTransitionFilter, excludeSingleEdgeComponents: Boolean): ConnectedComponents {
            val c = MaterializingConsumer(2 * graph.edges, excludeSingleEdgeComponents)
            EdgeBasedTarjanSCC(graph, edgeTransitionFilter, c).findComponents()
            return c.components
        }

        /**
         * Like [findComponents], but the search only starts at the
         * given edges. This does not mean the search cannot expand to other edges, but this can be controlled by the
         * edgeTransitionFilter. This method does not return single edge components (the excludeSingleEdgeComponents option is
         * set to true).
         */
        @JvmStatic
        fun findComponentsForStartEdges(graph: Graph, edgeTransitionFilter: EdgeTransitionFilter, edges: IntContainer): ConnectedComponents {
            val c = MaterializingConsumer(2 * edges.size(), true)
            EdgeBasedTarjanSCC(graph, edgeTransitionFilter, c).findComponentsForStartEdges(edges)
            return c.components
        }

        /**
         * Runs Tarjan's algorithm in a recursive way. Doing it like this requires a large stack size for large graphs,
         * which can be set like `-Xss1024M`. Usually the version using an explicit stack ([findComponents]) should be
         * preferred. However, this recursive implementation is easier to understand.
         *
         * @see findComponents
         */
        @JvmStatic
        fun findComponentsRecursive(graph: Graph, edgeTransitionFilter: EdgeTransitionFilter, excludeSingleEdgeComponents: Boolean): ConnectedComponents {
            val c = MaterializingConsumer(2 * graph.edges, excludeSingleEdgeComponents)
            EdgeBasedTarjanSCC(graph, edgeTransitionFilter, c).findComponentsRecursive()
            return c.components
        }

        /**
         * Streaming variant of [findComponents]: SCCs are delivered to
         * the supplied [SCCConsumer] as they are discovered, and nothing is retained inside Tarjan beyond what
         * the algorithm itself needs. Use this when the caller wants to process (or discard) each component on the fly
         * — most notably to avoid materializing the giant main component.
         */
        @JvmStatic
        fun findComponentsStreaming(graph: Graph, edgeTransitionFilter: EdgeTransitionFilter, consumer: SCCConsumer) {
            EdgeBasedTarjanSCC(graph, edgeTransitionFilter, consumer).findComponents()
        }

        @JvmStatic
        fun createEdgeKey(edgeState: EdgeIteratorState, reverse: Boolean): Int =
            TraversalMode.EDGE_BASED.createTraversalId(edgeState, reverse)
    }

    private fun initForEntireGraph() {
        val edges = graph.edges
        edgeKeyIndex = TarjanArrayIntIntMap(2 * edges)
        edgeKeyLowLink = TarjanArrayIntIntMap(2 * edges)
        edgeKeyOnStack = TarjanArrayIntSet(2 * edges)
    }

    private fun initForStartEdges(edges: Int) {
        edgeKeyIndex = TarjanHashIntIntMap(2 * edges)
        edgeKeyLowLink = TarjanHashIntIntMap(2 * edges)
        edgeKeyOnStack = TarjanHashIntSet(2 * edges)
    }

    private enum class State {
        UPDATE,
        HANDLE_NEIGHBOR,
        FIND_COMPONENT,
        BUILD_COMPONENT
    }

    private fun findComponentsRecursive() {
        initForEntireGraph()
        val iter = graph.allEdges
        while (iter.next()) {
            val edgeKeyFwd = createEdgeKey(iter, false)
            if (!edgeKeyIndex.has(edgeKeyFwd))
                findComponentForEdgeKey(edgeKeyFwd, iter.adjNode)
            val edgeKeyBwd = createEdgeKey(iter, true)
            if (!edgeKeyIndex.has(edgeKeyBwd))
                findComponentForEdgeKey(edgeKeyBwd, iter.adjNode)
        }
    }

    private fun findComponentForEdgeKey(p: Int, adjNode: Int) {
        setupNextEdgeKey(p)
        // we have to create a new explorer on each iteration because of the nested edge iterations
        val edge = getEdgeFromEdgeKey(p)
        val explorer = graph.createEdgeExplorer()
        val iter = explorer.setBaseNode(adjNode)
        while (iter.next()) {
            if (!edgeTransitionFilter.accept(edge, iter))
                continue
            val q = createEdgeKey(iter, false)
            handleNeighbor(p, q, iter.adjNode)
        }
        buildComponent(p)
    }

    private fun setupNextEdgeKey(p: Int) {
        edgeKeyIndex.set(p, currIndex)
        edgeKeyLowLink.set(p, currIndex)
        currIndex++
        tarjanStack.addLast(p)
        edgeKeyOnStack.add(p)
    }

    private fun handleNeighbor(p: Int, q: Int, adj: Int) {
        if (!edgeKeyIndex.has(q)) {
            findComponentForEdgeKey(q, adj)
            edgeKeyLowLink.minTo(p, edgeKeyLowLink.get(q))
        } else if (edgeKeyOnStack.contains(q))
            edgeKeyLowLink.minTo(p, edgeKeyIndex.get(q))
    }

    private fun buildComponent(p: Int) {
        if (edgeKeyLowLink.get(p) == edgeKeyIndex.get(p)) {
            if (tarjanStack.last == p) {
                tarjanStack.popLast()
                edgeKeyOnStack.remove(p)
                consumer.singleEdgeComponent(p)
            } else {
                consumer.beginComponent()
                while (true) {
                    val q = tarjanStack.popLast()
                    edgeKeyOnStack.remove(q)
                    consumer.edgeKey(q)
                    if (q == p)
                        break
                }
                consumer.endComponent()
            }
        }
    }

    private fun findComponents() {
        initForEntireGraph()
        val iter = graph.allEdges
        while (iter.next()) {
            findComponentsForEdgeState(iter)
        }
    }

    private fun findComponentsForStartEdges(startEdges: IntContainer) {
        initForStartEdges(startEdges.size())
        for (edge in startEdges) {
            // todo: using getEdgeIteratorState here is not efficient
            val edgeState = graph.getEdgeIteratorState(edge.value, Int.MIN_VALUE)!!
            findComponentsForEdgeState(edgeState)
        }
    }

    private fun findComponentsForEdgeState(edge: EdgeIteratorState) {
        val edgeKeyFwd = createEdgeKey(edge, false)
        if (!edgeKeyIndex.has(edgeKeyFwd))
            pushFindComponentForEdgeKey(edgeKeyFwd, edge.adjNode)
        startSearch()
        // We need to start the search for both edge keys of this edge, but its important to check if the second
        // has already been found by the first search. So we cannot simply push them both and start the search once.
        val edgeKeyBwd = createEdgeKey(edge, true)
        if (!edgeKeyIndex.has(edgeKeyBwd))
            pushFindComponentForEdgeKey(edgeKeyBwd, edge.adjNode)
        startSearch()
    }

    private fun startSearch() {
        while (hasNext()) {
            pop()
            when (dfsState) {
                State.BUILD_COMPONENT ->
                    buildComponent(p)
                State.UPDATE ->
                    edgeKeyLowLink.minTo(p, edgeKeyLowLink.get(q))
                State.HANDLE_NEIGHBOR -> {
                    if (edgeKeyIndex.has(q) && edgeKeyOnStack.contains(q))
                        edgeKeyLowLink.minTo(p, edgeKeyIndex.get(q))
                    if (!edgeKeyIndex.has(q)) {
                        // we are pushing updateLowLinks first so it will run *after* findComponent finishes
                        pushUpdateLowLinks(p, q)
                        pushFindComponentForEdgeKey(q, adj)
                    }
                }
                State.FIND_COMPONENT -> {
                    setupNextEdgeKey(p)
                    // we push buildComponent first so it will run *after* we finished traversing the edges
                    pushBuildComponent(p)
                    val edge = getEdgeFromEdgeKey(p)
                    val it = explorer.setBaseNode(adj)
                    while (it.next()) {
                        if (!edgeTransitionFilter.accept(edge, it))
                            continue
                        val q = createEdgeKey(it, false)
                        pushHandleNeighbor(p, q, it.adjNode)
                    }
                }
            }
        }
    }

    private fun hasNext(): Boolean = !dfsStackPQ.isEmpty

    private fun pop() {
        val l = dfsStackPQ.removeLast()
        val a = dfsStackAdj.popLast()
        // We are maintaining two stacks to hold four kinds of information: two edge keys (p&q), the adj node and the
        // kind of code ('state') we want to execute for a given stack item. The following code combined with the pushXYZ
        // methods does the fwd/bwd conversion between this information and the values on our stack(s).
        val low = bitUtil.getIntLow(l)
        val high = bitUtil.getIntHigh(l)
        if (a == -1) {
            dfsState = State.UPDATE
            p = low
            q = high
            adj = -1
        } else if (a == -2 && high == -2) {
            dfsState = State.BUILD_COMPONENT
            p = low
            q = -1
            adj = -1
        } else if (high == -1) {
            dfsState = State.FIND_COMPONENT
            p = low
            q = -1
            adj = a
        } else {
            assert(low >= 0 && high >= 0 && a >= 0)
            dfsState = State.HANDLE_NEIGHBOR
            p = low
            q = high
            adj = a
        }
    }

    private fun pushUpdateLowLinks(p: Int, q: Int) {
        assert(p >= 0 && q >= 0)
        dfsStackPQ.addLast(bitUtil.toLong(p, q))
        dfsStackAdj.addLast(-1)
    }

    private fun pushBuildComponent(p: Int) {
        assert(p >= 0)
        dfsStackPQ.addLast(bitUtil.toLong(p, -2))
        dfsStackAdj.addLast(-2)
    }

    private fun pushFindComponentForEdgeKey(p: Int, adj: Int) {
        assert(p >= 0 && adj >= 0)
        dfsStackPQ.addLast(bitUtil.toLong(p, -1))
        dfsStackAdj.addLast(adj)
    }

    private fun pushHandleNeighbor(p: Int, q: Int, adj: Int) {
        assert(p >= 0 && q >= 0 && adj >= 0)
        dfsStackPQ.addLast(bitUtil.toLong(p, q))
        dfsStackAdj.addLast(adj)
    }

    class ConnectedComponents internal constructor(edgeKeys: Int) {
        /**
         * A list of arrays each containing the edge keys of a strongly connected component. Components with only a single
         * edge key are not included here, but need to be obtained using [singleEdgeComponents].
         * The edge key is either 2*edgeId (if the edge direction corresponds to the storage order) or 2*edgeId+1 (for
         * the opposite direction). Use [com.graphhopper.util.GHUtility.getEdgeFromEdgeKey] to convert edge keys back to
         * edge IDs.
         */
        val components: MutableList<IntArrayList> = ArrayList()

        /**
         * The set of edge-keys that form their own (single-edge key) component. If the excludeSingleEdgeComponents
         * option is enabled this set will be empty.
         */
        val singleEdgeComponents: GrowableBitSet = GrowableBitSet(max(edgeKeys, 0).toLong())

        /**
         * A reference to the biggest component contained in [components] or an empty list if there are
         * either no components or the biggest component has only a single edge (and hence [components] is
         * empty).
         */
        var biggestComponent: IntArrayList = IntArrayList()
            internal set

        internal var numComponents = 0
        internal var numEdgeKeys = 0

        init {
            if (!singleEdgeComponents.javaClass.name.contains("GrowableBitSet"))
                throw IllegalStateException("Was meant to be a growable bit set")
        }

        /**
         * The total number of strongly connected components. This always includes single-edge components.
         */
        val totalComponents: Int
            get() = numComponents

        val edgeKeys: Int
            get() = numEdgeKeys
    }

    private interface TarjanIntIntMap {
        fun set(key: Int, value: Int)

        fun minTo(key: Int, min: Int)

        fun has(key: Int): Boolean

        fun get(key: Int): Int
    }

    private class TarjanArrayIntIntMap(elements: Int) : TarjanIntIntMap {
        private val arr = IntArray(elements) { -1 }

        override fun set(key: Int, value: Int) {
            arr[key] = value
        }

        override fun minTo(key: Int, min: Int) {
            arr[key] = min(arr[key], min)
        }

        override fun has(key: Int): Boolean = arr[key] != -1

        override fun get(key: Int): Int = arr[key]
    }

    private class TarjanHashIntIntMap(keys: Int) : TarjanIntIntMap {
        private val map = MutableIntIntMap(keys)

        override fun set(key: Int, value: Int) {
            map.put(key, value)
        }

        override fun minTo(key: Int, min: Int) {
            // todo: optimize with map.indexOf(key) etc
            map.put(key, min(map.getOrDefault(key, -1), min))
        }

        override fun has(key: Int): Boolean = map.containsKey(key)

        override fun get(key: Int): Int = map.getOrDefault(key, -1)
    }

    private interface TarjanIntSet {
        fun add(key: Int)

        fun contains(key: Int): Boolean

        fun remove(key: Int)
    }

    private class TarjanArrayIntSet(keys: Int) : TarjanIntSet {
        private val set = GrowableBitSet(keys.toLong())

        init {
            if (!set.javaClass.name.contains("GrowableBitSet"))
                throw IllegalStateException("Was meant to be a growable bit set")
        }

        override fun add(key: Int) {
            set.set(key.toLong())
        }

        override fun contains(key: Int): Boolean = set.get(key.toLong())

        override fun remove(key: Int) {
            set.clear(key.toLong())
        }
    }

    private class TarjanHashIntSet(keys: Int) : TarjanIntSet {
        private val set = MutableIntSet(keys)

        override fun add(key: Int) {
            set.add(key)
        }

        override fun contains(key: Int): Boolean = set.contains(key)

        override fun remove(key: Int) {
            set.remove(key)
        }
    }

    /**
     * Streaming sink for SCCs discovered by [findComponentsStreaming].
     * Multi-edge components are delivered as a sequence of [beginComponent], one or more
     * [edgeKey] calls, and a closing [endComponent]. Single-edge components arrive as a
     * single [singleEdgeComponent] call. The order of edge keys within a multi-edge component is
     * unspecified; the order of components is unspecified.
     */
    interface SCCConsumer {
        fun beginComponent() {}

        fun edgeKey(edgeKey: Int)

        fun endComponent() {}

        fun singleEdgeComponent(edgeKey: Int) {}
    }

    /**
     * Internal consumer that reproduces the legacy materialized [ConnectedComponents] output used by the
     * non-streaming public static methods.
     */
    private class MaterializingConsumer(edgeKeyCapacity: Int, val excludeSingleEdgeComponents: Boolean) : SCCConsumer {
        val components = ConnectedComponents(if (excludeSingleEdgeComponents) -1 else edgeKeyCapacity)
        var current: IntArrayList? = null

        override fun beginComponent() {
            current = IntArrayList()
        }

        override fun edgeKey(edgeKey: Int) {
            current!!.add(edgeKey)
        }

        override fun endComponent() {
            val current = this.current!!
            current.trimToSize()
            assert(current.size() > 1)
            components.numComponents++
            components.numEdgeKeys += current.size()
            components.components.add(current)
            if (current.size() > components.biggestComponent.size())
                components.biggestComponent = current
            this.current = null
        }

        override fun singleEdgeComponent(edgeKey: Int) {
            components.numComponents++
            components.numEdgeKeys++
            if (!excludeSingleEdgeComponents)
                components.singleEdgeComponents.set(edgeKey.toLong())
        }
    }

    fun interface EdgeTransitionFilter {
        /**
         * @return true if edgeState is allowed *and* turning from prevEdge onto edgeState is allowed, false otherwise
         */
        fun accept(prevEdge: Int, edgeState: EdgeIteratorState): Boolean
    }
}
