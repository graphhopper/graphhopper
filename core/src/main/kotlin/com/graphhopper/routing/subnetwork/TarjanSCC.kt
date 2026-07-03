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
import com.graphhopper.coll.GrowableBitSet
import com.graphhopper.coll.primitive.IntArrayList
import com.graphhopper.coll.primitive.LongArrayDeque
import com.graphhopper.routing.util.EdgeFilter
import com.graphhopper.storage.Graph
import com.graphhopper.util.BitUtil
import kotlin.math.max
import kotlin.math.min

/**
 * Tarjan's algorithm to find strongly connected components of a directed graph. Two nodes belong to the same connected
 * component iff they are reachable from each other. Reachability from A to B is not necessarily equal to reachability
 * from B to A because the graph is directed.
 *
 * This class offers two ways to run the algorithm: Either using (function call) recursion [findComponentsRecursive]
 * or recursion using an explicit stack [findComponents]. The first one is easier to implement and understand
 * and the second one allows running the algorithm also on large graphs without having to deal with JVM stack size
 * limits.
 *
 * Tarjan's algorithm is explained for example here:
 * - http://en.wikipedia.org/wiki/Tarjan's_strongly_connected_components_algorithm
 * - http://www.timl.id.au/?p=327
 * - http://homepages.ecs.vuw.ac.nz/~djp/files/P05.pdf
 *
 * @author easbar
 */
class TarjanSCC private constructor(
    private val graph: Graph,
    private val edgeFilter: EdgeFilter,
    private val excludeSingleNodeComponents: Boolean
) {
    private val explorer = graph.createEdgeExplorer(edgeFilter)
    private val bitUtil = BitUtil.LITTLE
    private val nodeIndex = IntArray(graph.nodes) { -1 }
    private val nodeLowLink = IntArray(graph.nodes) { -1 }
    private val nodeOnStack = GrowableBitSet(graph.nodes.toLong())
    private val tarjanStack = CircularIntArray()
    private val dfsStack = LongArrayDeque()
    private val components = ConnectedComponents(if (excludeSingleNodeComponents) -1 else graph.nodes)

    private var currIndex = 0
    private var v = 0
    private var w = 0
    private lateinit var dfsState: State

    init {
        if (!nodeOnStack.javaClass.name.contains("GrowableBitSet"))
            throw IllegalStateException("Was meant to be a growable bit set")
    }

    companion object {
        /**
         * Runs Tarjan's algorithm using an explicit stack.
         *
         * @param excludeSingleNodeComponents if set to true components that only contain a single node will not be
         * returned when calling [findComponents] or [findComponentsRecursive],
         * which can be useful to save some memory.
         */
        @JvmStatic
        fun findComponents(graph: Graph, edgeFilter: EdgeFilter, excludeSingleNodeComponents: Boolean): ConnectedComponents =
            TarjanSCC(graph, edgeFilter, excludeSingleNodeComponents).findComponents()

        /**
         * Runs Tarjan's algorithm in a recursive way. Doing it like this requires a large stack size for large graphs,
         * which can be set like `-Xss1024M`. Usually the version using an explicit stack ([findComponents]) should be
         * preferred. However, this recursive implementation is easier to understand.
         *
         * @see findComponents
         */
        @JvmStatic
        fun findComponentsRecursive(graph: Graph, edgeFilter: EdgeFilter, excludeSingleNodeComponents: Boolean): ConnectedComponents =
            TarjanSCC(graph, edgeFilter, excludeSingleNodeComponents).findComponentsRecursive()
    }

    private enum class State {
        UPDATE,
        HANDLE_NEIGHBOR,
        FIND_COMPONENT,
        BUILD_COMPONENT
    }

    private fun findComponentsRecursive(): ConnectedComponents {
        for (node in 0 until graph.nodes) {
            if (nodeIndex[node] == -1) {
                findComponentForNode(node)
            }
        }
        return components
    }

    private fun findComponentForNode(v: Int) {
        setupNextNode(v)
        // we have to create a new explorer on each iteration because of the nested edge iterations
        val explorer = graph.createEdgeExplorer(edgeFilter)
        val iter = explorer.setBaseNode(v)
        while (iter.next()) {
            val w = iter.adjNode
            if (nodeIndex[w] == -1) {
                findComponentForNode(w)
                nodeLowLink[v] = min(nodeLowLink[v], nodeLowLink[w])
            } else if (nodeOnStack.get(w.toLong()))
                nodeLowLink[v] = min(nodeLowLink[v], nodeIndex[w])
        }
        buildComponent(v)
    }

    private fun setupNextNode(v: Int) {
        nodeIndex[v] = currIndex
        nodeLowLink[v] = currIndex
        currIndex++
        tarjanStack.addLast(v)
        nodeOnStack.set(v.toLong())
    }

    private fun buildComponent(v: Int) {
        if (nodeLowLink[v] == nodeIndex[v]) {
            if (tarjanStack.last == v) {
                tarjanStack.popLast()
                nodeOnStack.clear(v.toLong())
                components.numComponents++
                components.numNodes++
                if (!excludeSingleNodeComponents)
                    components.singleNodeComponents.set(v.toLong())
            } else {
                val component = IntArrayList()
                while (true) {
                    val w = tarjanStack.popLast()
                    component.add(w)
                    nodeOnStack.clear(w.toLong())
                    if (w == v)
                        break
                }
                component.trimToSize()
                assert(component.size() > 1)
                components.numComponents++
                components.numNodes += component.size()
                components.components.add(component)
                if (component.size() > components.biggestComponent.size())
                    components.biggestComponent = component
            }
        }
    }

    private fun findComponents(): ConnectedComponents {
        for (node in 0 until graph.nodes) {
            if (nodeIndex[node] != -1)
                continue

            pushFindComponentForNode(node)
            while (hasNext()) {
                pop()
                when (dfsState) {
                    State.BUILD_COMPONENT ->
                        buildComponent(v)
                    State.UPDATE ->
                        nodeLowLink[v] = min(nodeLowLink[v], nodeLowLink[w])
                    State.HANDLE_NEIGHBOR -> {
                        if (nodeIndex[w] != -1 && nodeOnStack.get(w.toLong()))
                            nodeLowLink[v] = min(nodeLowLink[v], nodeIndex[w])
                        if (nodeIndex[w] == -1) {
                            // we are pushing updateLowLinks first so it will run *after* findComponent finishes
                            pushUpdateLowLinks(v, w)
                            pushFindComponentForNode(w)
                        }
                    }
                    State.FIND_COMPONENT -> {
                        setupNextNode(v)
                        // we push buildComponent first so it will run *after* we finished traversing the edges
                        pushBuildComponent(v)
                        val iter = explorer.setBaseNode(v)
                        while (iter.next()) {
                            pushHandleNeighbor(v, iter.adjNode)
                        }
                    }
                }
            }
        }
        return components
    }

    private fun hasNext(): Boolean = !dfsStack.isEmpty

    private fun pop() {
        val l = dfsStack.removeLast()
        // We are maintaining a stack of longs to hold three kinds of information: two node indices (v&w) and the kind
        // of code ('state') we want to execute for a given stack item. The following code combined with the pushXYZ
        // methods does the fwd/bwd conversion between this information and a single long value.
        val low = bitUtil.getIntLow(l)
        val high = bitUtil.getIntHigh(l)
        if (low > 0 && high > 0) {
            dfsState = State.HANDLE_NEIGHBOR
            v = low - 1
            w = high - 1
        } else if (low > 0 && high < 0) {
            dfsState = State.UPDATE
            v = low - 1
            w = -high - 1
        } else if (low == 0) {
            dfsState = State.BUILD_COMPONENT
            v = high - 1
            w = -1
        } else {
            dfsState = State.FIND_COMPONENT
            v = low - 1
            w = -1
        }
    }

    private fun pushHandleNeighbor(v: Int, w: Int) {
        assert(v >= 0 && v < Int.MAX_VALUE)
        assert(w >= 0 && w < Int.MAX_VALUE)
        dfsStack.addLast(bitUtil.toLong(v + 1, w + 1))
    }

    private fun pushUpdateLowLinks(v: Int, w: Int) {
        assert(v >= 0 && v < Int.MAX_VALUE)
        assert(w >= 0 && w < Int.MAX_VALUE)
        dfsStack.addLast(bitUtil.toLong(v + 1, -(w + 1)))
    }

    private fun pushBuildComponent(v: Int) {
        assert(v >= 0 && v < Int.MAX_VALUE)
        dfsStack.addLast(bitUtil.toLong(0, v + 1))
    }

    private fun pushFindComponentForNode(v: Int) {
        assert(v >= 0 && v < Int.MAX_VALUE)
        dfsStack.addLast(bitUtil.toLong(v + 1, 0))
    }

    class ConnectedComponents internal constructor(nodes: Int) {
        /**
         * A list of arrays each containing the nodes of a strongly connected component. Components with only a single
         * node are not included here, but need to be obtained using [singleNodeComponents].
         */
        val components: MutableList<IntArrayList> = ArrayList()

        /**
         * The set of nodes that form their own (single-node) component. If [TarjanSCC.excludeSingleNodeComponents]
         * is enabled this set will be empty.
         */
        val singleNodeComponents: GrowableBitSet = GrowableBitSet(max(nodes, 0).toLong())

        /**
         * A reference to the biggest component contained in [components] or an empty list if there are
         * either no components or the biggest component has only a single node (and hence [components] is
         * empty).
         */
        var biggestComponent: IntArrayList = IntArrayList()
            internal set

        internal var numComponents = 0
        internal var numNodes = 0

        init {
            if (!singleNodeComponents.javaClass.name.contains("GrowableBitSet"))
                throw IllegalStateException("Was meant to be a growable bit set")
        }

        /**
         * The total number of strongly connected components. This always includes single-node components.
         */
        val totalComponents: Int
            get() = numComponents

        val nodes: Int
            get() = numNodes
    }
}
