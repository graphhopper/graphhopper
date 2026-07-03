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

package com.graphhopper.routing.ch

import com.graphhopper.coll.primitive.IntArrayList
import com.graphhopper.coll.primitive.IntScatterSet
import com.carrotsearch.hppc.sorting.IndirectComparator
import com.carrotsearch.hppc.sorting.IndirectSort
import com.graphhopper.routing.weighting.Weighting
import com.graphhopper.storage.Graph
import com.graphhopper.util.ArrayUtil
import com.graphhopper.util.GHUtility
import kotlin.math.max

/**
 * Graph data structure used for CH preparation. It allows caching weights, and edges that are not needed anymore
 * (those adjacent to contracted nodes) can be removed (see [disconnect]).
 *
 * @author easbar
 */
class CHPreparationGraph private constructor(
    private val nodes: Int,
    private val edges: Int,
    private val edgeBased: Boolean,
    private val turnCostFunction: TurnCostFunction
) {
    // each edge/shortcut between nodes a/b is represented as a single object and we maintain two linked lists of such
    // objects for every node (one for outgoing edges and one for incoming edges).
    private var prepareEdgesOut: Array<PrepareEdge?>? = arrayOfNulls(nodes)
    private var prepareEdgesIn: Array<PrepareEdge?>? = arrayOfNulls(nodes)

    // todo: it should be possible to store the 'skipped node' for each shortcut instead of storing the shortcut for
    //       each prepare edge. but this is a bit tricky for edge-based, because of our bidir shortcuts for node-based,
    //       and because basegraph has multi-edges. the advantage of storing the skipped node is that we could just write
    //       it to one of the skipped edges fields temporarily, so we would not need this array and save memory during
    //       the preparation.
    private var shortcutsByPrepareEdges: IntArrayList? = IntArrayList()

    // todo: maybe we can get rid of this
    private var degrees: IntArray? = IntArray(nodes)
    private var neighborSet: IntScatterSet? = IntScatterSet()
    private var origGraph: OrigGraph? = null
    private var origGraphBuilder: OrigGraph.Builder? = if (edgeBased) OrigGraph.Builder() else null
    private var nextShortcutId: Int = edges
    private var ready = false

    companion object {
        @JvmStatic
        fun nodeBased(nodes: Int, edges: Int): CHPreparationGraph =
            CHPreparationGraph(nodes, edges, false, TurnCostFunction { _, _, _ -> 0.0 })

        @JvmStatic
        fun edgeBased(nodes: Int, edges: Int, turnCostFunction: TurnCostFunction): CHPreparationGraph =
            CHPreparationGraph(nodes, edges, true, turnCostFunction)

        @JvmStatic
        fun buildFromGraph(prepareGraph: CHPreparationGraph, graph: Graph, weighting: Weighting) {
            if (graph.nodes != prepareGraph.getNodes())
                throw IllegalArgumentException("Cannot initialize from given graph. The number of nodes does not match: " +
                        graph.nodes + " vs. " + prepareGraph.getNodes())
            if (graph.edges != prepareGraph.getOriginalEdges())
                throw IllegalArgumentException("Cannot initialize from given graph. The number of edges does not match: " +
                        graph.edges + " vs. " + prepareGraph.getOriginalEdges())
            val iter = graph.allEdges
            while (iter.next()) {
                val weightFwd = weighting.calcEdgeWeight(iter, false)
                val weightBwd = weighting.calcEdgeWeight(iter, true)
                prepareGraph.addEdge(iter.baseNode, iter.adjNode, iter.edge, weightFwd, weightBwd)
            }
            prepareGraph.prepareForContraction()
        }

        @JvmStatic
        fun buildTurnCostFunctionFromTurnCostStorage(graph: Graph, weighting: Weighting): TurnCostFunction {
            // At some point we used an optimized version where we copied the turn costs to sorted arrays
            // temporarily. This seemed to be around 25% faster according to measurements on the Bavaria
            // map, but for bigger maps the improvement is less, around 10% for planet. See also #2084
            return TurnCostFunction(weighting::calcTurnWeight)
        }

        private fun sortAndTrim(arr: IntArrayList, sortOrder: IntArray) {
            arr.buffer = applySortOrder(sortOrder, arr.buffer)
            arr.elementsCount = arr.buffer.size
        }

        private fun applySortOrder(sortOrder: IntArray, arr: IntArray): IntArray {
            if (sortOrder.size > arr.size) {
                throw IllegalArgumentException("sort order must not be shorter than array")
            }
            val result = IntArray(sortOrder.size)
            for (i in result.indices) {
                result[i] = arr[sortOrder[i]]
            }
            return result
        }
    }

    fun getNodes(): Int = nodes

    fun getOriginalEdges(): Int = edges

    fun getDegree(node: Int): Int = degrees!![node]

    fun addEdge(from: Int, to: Int, edge: Int, weightFwd: Double, weightBwd: Double) {
        checkNotReady()
        if (from == to)
            throw IllegalArgumentException("Loop edges are no longer supported since #2862")
        val fwd = weightFwd.isFinite()
        val bwd = weightBwd.isFinite()
        if (!fwd && !bwd)
            return
        val prepareEdge = PrepareBaseEdge(edge, from, to, weightFwd.toFloat(), weightBwd.toFloat())
        if (fwd) {
            addOutEdge(from, prepareEdge)
            addInEdge(to, prepareEdge)
        }
        if (bwd && from != to) {
            addOutEdge(to, prepareEdge)
            addInEdge(from, prepareEdge)
        }
        if (edgeBased)
            origGraphBuilder!!.addEdge(from, to, edge, fwd, bwd)
    }

    fun addShortcut(from: Int, to: Int, origEdgeKeyFirst: Int, origEdgeKeyLast: Int, skipped1: Int,
                    skipped2: Int, weight: Double, origEdgeCount: Int): Int {
        checkReady()
        val prepareEdge: PrepareEdge = if (edgeBased)
            EdgeBasedPrepareShortcut(nextShortcutId, from, to, origEdgeKeyFirst, origEdgeKeyLast, weight, skipped1, skipped2, origEdgeCount)
        else
            PrepareShortcut(nextShortcutId, from, to, weight, skipped1, skipped2, origEdgeCount)
        addOutEdge(from, prepareEdge)
        if (from != to)
            addInEdge(to, prepareEdge)
        return nextShortcutId++
    }

    fun prepareForContraction() {
        checkNotReady()
        origGraph = if (edgeBased) origGraphBuilder!!.build() else null
        origGraphBuilder = null
        ready = true
    }

    fun setShortcutForPrepareEdge(prepareEdge: Int, shortcut: Int) {
        val index = prepareEdge - edges
        val shortcutsByPrepareEdges = shortcutsByPrepareEdges!!
        if (index >= shortcutsByPrepareEdges.size())
            shortcutsByPrepareEdges.resize(index + 1)
        shortcutsByPrepareEdges.set(index, shortcut)
    }

    fun getShortcutForPrepareEdge(prepareEdge: Int): Int {
        if (prepareEdge < edges)
            return prepareEdge
        val index = prepareEdge - edges
        return shortcutsByPrepareEdges!!.get(index)
    }

    fun createOutEdgeExplorer(): PrepareGraphEdgeExplorer {
        checkReady()
        return PrepareGraphEdgeExplorerImpl(prepareEdgesOut!!, false)
    }

    fun createInEdgeExplorer(): PrepareGraphEdgeExplorer {
        checkReady()
        return PrepareGraphEdgeExplorerImpl(prepareEdgesIn!!, true)
    }

    fun createOutOrigEdgeExplorer(): PrepareGraphOrigEdgeExplorer {
        checkReady()
        if (!edgeBased)
            throw IllegalStateException("orig out explorer is not available for node-based graph")
        return origGraph!!.createOutOrigEdgeExplorer()
    }

    fun createInOrigEdgeExplorer(): PrepareGraphOrigEdgeExplorer {
        checkReady()
        if (!edgeBased)
            throw IllegalStateException("orig in explorer is not available for node-based graph")
        return origGraph!!.createInOrigEdgeExplorer()
    }

    fun getTurnWeight(inEdgeKey: Int, viaNode: Int, outEdgeKey: Int): Double =
        turnCostFunction.getTurnWeight(GHUtility.getEdgeFromEdgeKey(inEdgeKey), viaNode, GHUtility.getEdgeFromEdgeKey(outEdgeKey))

    fun disconnect(node: Int): IntScatterSet {
        checkReady()
        val prepareEdgesOut = prepareEdgesOut!!
        val prepareEdgesIn = prepareEdgesIn!!
        // we use this neighbor set to guarantee a deterministic order of the returned
        // node ids
        val neighborSet = neighborSet!!
        neighborSet.clear()
        var currOut = prepareEdgesOut[node]
        while (currOut != null) {
            var adjNode = currOut.getNodeB()
            if (adjNode == node)
                adjNode = currOut.getNodeA()
            if (adjNode == node) {
                // this is a loop
                currOut = currOut.getNextOut(node)
                continue
            }
            removeInEdge(adjNode, currOut)
            neighborSet.add(adjNode)
            currOut = currOut.getNextOut(node)
        }
        var currIn = prepareEdgesIn[node]
        while (currIn != null) {
            var adjNode = currIn.getNodeB()
            if (adjNode == node)
                adjNode = currIn.getNodeA()
            if (adjNode == node) {
                // this is a loop
                currIn = currIn.getNextIn(node)
                continue
            }
            removeOutEdge(adjNode, currIn)
            neighborSet.add(adjNode)
            currIn = currIn.getNextIn(node)
        }
        prepareEdgesOut[node] = null
        prepareEdgesIn[node] = null
        degrees!![node] = 0
        return neighborSet
    }

    private fun removeOutEdge(node: Int, prepareEdge: PrepareEdge) {
        val prepareEdgesOut = prepareEdgesOut!!
        var prevOut: PrepareEdge? = null
        var currOut = prepareEdgesOut[node]
        while (currOut != null) {
            if (currOut === prepareEdge) {
                if (prevOut == null) {
                    prepareEdgesOut[node] = currOut.getNextOut(node)
                } else {
                    prevOut.setNextOut(node, currOut.getNextOut(node))
                }
                degrees!![node]--
            } else {
                prevOut = currOut
            }
            currOut = currOut.getNextOut(node)
        }
    }

    private fun removeInEdge(node: Int, prepareEdge: PrepareEdge) {
        val prepareEdgesIn = prepareEdgesIn!!
        var prevIn: PrepareEdge? = null
        var currIn = prepareEdgesIn[node]
        while (currIn != null) {
            if (currIn === prepareEdge) {
                if (prevIn == null) {
                    prepareEdgesIn[node] = currIn.getNextIn(node)
                } else {
                    prevIn.setNextIn(node, currIn.getNextIn(node))
                }
                degrees!![node]--
            } else {
                prevIn = currIn
            }
            currIn = currIn.getNextIn(node)
        }
    }

    fun close() {
        checkReady()
        prepareEdgesOut = null
        prepareEdgesIn = null
        shortcutsByPrepareEdges = null
        degrees = null
        neighborSet = null
        if (edgeBased)
            origGraph = null
    }

    private fun addOutEdge(node: Int, prepareEdge: PrepareEdge) {
        prepareEdge.setNextOut(node, prepareEdgesOut!![node])
        prepareEdgesOut!![node] = prepareEdge
        degrees!![node]++
    }

    private fun addInEdge(node: Int, prepareEdge: PrepareEdge) {
        prepareEdge.setNextIn(node, prepareEdgesIn!![node])
        prepareEdgesIn!![node] = prepareEdge
        degrees!![node]++
    }

    private fun checkReady() {
        if (!ready)
            throw IllegalStateException("You need to call prepareForContraction() before calling this method")
    }

    private fun checkNotReady() {
        if (ready)
            throw IllegalStateException("You cannot call this method after calling prepareForContraction()")
    }

    fun interface TurnCostFunction {
        fun getTurnWeight(inEdge: Int, viaNode: Int, outEdge: Int): Double
    }

    private class PrepareGraphEdgeExplorerImpl(
        private val prepareEdges: Array<PrepareEdge?>,
        private val reverse: Boolean
    ) : PrepareGraphEdgeExplorer, PrepareGraphEdgeIterator {
        private var node = -1
        private var currEdge: PrepareEdge? = null
        private var nextEdge: PrepareEdge? = null

        override fun setBaseNode(node: Int): PrepareGraphEdgeIterator {
            this.node = node
            currEdge = null
            nextEdge = prepareEdges[node]
            return this
        }

        override fun next(): Boolean {
            currEdge = nextEdge
            val currEdge = currEdge ?: return false
            nextEdge = if (reverse) currEdge.getNextIn(node) else currEdge.getNextOut(node)
            return true
        }

        override fun getBaseNode(): Int = node

        override fun getAdjNode(): Int =
            if (nodeAisBase()) currEdge!!.getNodeB() else currEdge!!.getNodeA()

        override fun getPrepareEdge(): Int = currEdge!!.getPrepareEdge()

        override fun isShortcut(): Boolean = currEdge!!.isShortcut()

        override fun getOrigEdgeKeyFirst(): Int =
            if (nodeAisBase()) currEdge!!.getOrigEdgeKeyFirstAB() else currEdge!!.getOrigEdgeKeyFirstBA()

        override fun getOrigEdgeKeyLast(): Int =
            if (nodeAisBase()) currEdge!!.getOrigEdgeKeyLastAB() else currEdge!!.getOrigEdgeKeyLastBA()

        override fun getSkipped1(): Int = currEdge!!.getSkipped1()

        override fun getSkipped2(): Int = currEdge!!.getSkipped2()

        override fun getWeight(): Double {
            return if (nodeAisBase()) {
                if (reverse) currEdge!!.getWeightBA() else currEdge!!.getWeightAB()
            } else {
                if (reverse) currEdge!!.getWeightAB() else currEdge!!.getWeightBA()
            }
        }

        override fun getOrigEdgeCount(): Int = currEdge!!.getOrigEdgeCount()

        override fun setSkippedEdges(skipped1: Int, skipped2: Int) {
            currEdge!!.setSkipped1(skipped1)
            currEdge!!.setSkipped2(skipped2)
        }

        override fun setWeight(weight: Double) {
            assert(weight.isFinite())
            currEdge!!.setWeight(weight)
        }

        override fun setOrigEdgeCount(origEdgeCount: Int) {
            currEdge!!.setOrigEdgeCount(origEdgeCount)
        }

        override fun toString(): String = currEdge?.toString() ?: "not_started"

        private fun nodeAisBase(): Boolean {
            // in some cases we need to determine which direction of the (bidirectional) edge we want
            return currEdge!!.getNodeA() == node
        }
    }

    internal interface PrepareEdge {
        fun isShortcut(): Boolean

        fun getPrepareEdge(): Int

        fun getNodeA(): Int

        fun getNodeB(): Int

        fun getWeightAB(): Double

        fun getWeightBA(): Double

        fun getOrigEdgeKeyFirstAB(): Int

        fun getOrigEdgeKeyFirstBA(): Int

        fun getOrigEdgeKeyLastAB(): Int

        fun getOrigEdgeKeyLastBA(): Int

        fun getSkipped1(): Int

        fun getSkipped2(): Int

        fun getOrigEdgeCount(): Int

        fun setSkipped1(skipped1: Int)

        fun setSkipped2(skipped2: Int)

        fun setWeight(weight: Double)

        fun setOrigEdgeCount(origEdgeCount: Int)

        fun getNextOut(base: Int): PrepareEdge?

        fun setNextOut(base: Int, prepareEdge: PrepareEdge?)

        fun getNextIn(base: Int): PrepareEdge?

        fun setNextIn(base: Int, prepareEdge: PrepareEdge?)
    }

    internal class PrepareBaseEdge(
        private val prepareEdge: Int,
        private val nodeA: Int,
        private val nodeB: Int,
        private val weightAB: Float,
        private val weightBA: Float
    ) : PrepareEdge {
        private var nextOutA: PrepareEdge? = null
        private var nextOutB: PrepareEdge? = null
        private var nextInA: PrepareEdge? = null
        private var nextInB: PrepareEdge? = null

        override fun isShortcut(): Boolean = false

        override fun getPrepareEdge(): Int = prepareEdge

        override fun getNodeA(): Int = nodeA

        override fun getNodeB(): Int = nodeB

        override fun getWeightAB(): Double = weightAB.toDouble()

        override fun getWeightBA(): Double = weightBA.toDouble()

        override fun getOrigEdgeKeyFirstAB(): Int = GHUtility.createEdgeKey(prepareEdge, false)

        override fun getOrigEdgeKeyFirstBA(): Int = GHUtility.createEdgeKey(prepareEdge, true)

        override fun getOrigEdgeKeyLastAB(): Int = getOrigEdgeKeyFirstAB()

        override fun getOrigEdgeKeyLastBA(): Int = getOrigEdgeKeyFirstBA()

        override fun getSkipped1(): Int = throw UnsupportedOperationException()

        override fun getSkipped2(): Int = throw UnsupportedOperationException()

        override fun getOrigEdgeCount(): Int = 1

        override fun setSkipped1(skipped1: Int) {
            throw UnsupportedOperationException()
        }

        override fun setSkipped2(skipped2: Int) {
            throw UnsupportedOperationException()
        }

        override fun setWeight(weight: Double) {
            throw UnsupportedOperationException()
        }

        override fun setOrigEdgeCount(origEdgeCount: Int) {
            throw UnsupportedOperationException()
        }

        override fun getNextOut(base: Int): PrepareEdge? =
            if (base == nodeA)
                nextOutA
            else if (base == nodeB)
                nextOutB
            else
                throw IllegalStateException("Cannot get next out edge as the given base $base is not adjacent to the current edge")

        override fun setNextOut(base: Int, prepareEdge: PrepareEdge?) {
            if (base == nodeA)
                nextOutA = prepareEdge
            else if (base == nodeB)
                nextOutB = prepareEdge
            else
                throw IllegalStateException("Cannot set next out edge as the given base $base is not adjacent to the current edge")
        }

        override fun getNextIn(base: Int): PrepareEdge? =
            if (base == nodeA)
                nextInA
            else if (base == nodeB)
                nextInB
            else
                throw IllegalStateException("Cannot get next in edge as the given base $base is not adjacent to the current edge")

        override fun setNextIn(base: Int, prepareEdge: PrepareEdge?) {
            if (base == nodeA)
                nextInA = prepareEdge
            else if (base == nodeB)
                nextInB = prepareEdge
            else
                throw IllegalStateException("Cannot set next in edge as the given base $base is not adjacent to the current edge")
        }

        override fun toString(): String = "$nodeA-$nodeB ($prepareEdge) $weightAB $weightBA"
    }

    private open class PrepareShortcut(
        private val prepareEdge: Int,
        private val from: Int,
        private val to: Int,
        private var weight: Double,
        private var skipped1: Int,
        private var skipped2: Int,
        private var origEdgeCount: Int
    ) : PrepareEdge {
        private var nextOut: PrepareEdge? = null
        private var nextIn: PrepareEdge? = null

        init {
            assert(weight.isFinite())
        }

        override fun isShortcut(): Boolean = true

        override fun getPrepareEdge(): Int = prepareEdge

        override fun getNodeA(): Int = from

        override fun getNodeB(): Int = to

        override fun getWeightAB(): Double = weight

        override fun getWeightBA(): Double = weight

        override fun getOrigEdgeKeyFirstAB(): Int =
            throw IllegalStateException("Not supported for node-based shortcuts")

        override fun getOrigEdgeKeyFirstBA(): Int =
            throw IllegalStateException("Not supported for node-based shortcuts")

        override fun getOrigEdgeKeyLastAB(): Int =
            throw IllegalStateException("Not supported for node-based shortcuts")

        override fun getOrigEdgeKeyLastBA(): Int =
            throw IllegalStateException("Not supported for node-based shortcuts")

        override fun getSkipped1(): Int = skipped1

        override fun getSkipped2(): Int = skipped2

        override fun getOrigEdgeCount(): Int = origEdgeCount

        override fun setSkipped1(skipped1: Int) {
            this.skipped1 = skipped1
        }

        override fun setSkipped2(skipped2: Int) {
            this.skipped2 = skipped2
        }

        override fun setWeight(weight: Double) {
            this.weight = weight
        }

        override fun setOrigEdgeCount(origEdgeCount: Int) {
            this.origEdgeCount = origEdgeCount
        }

        override fun getNextOut(base: Int): PrepareEdge? = nextOut

        override fun setNextOut(base: Int, prepareEdge: PrepareEdge?) {
            this.nextOut = prepareEdge
        }

        override fun getNextIn(base: Int): PrepareEdge? = nextIn

        override fun setNextIn(base: Int, prepareEdge: PrepareEdge?) {
            this.nextIn = prepareEdge
        }

        override fun toString(): String = "$from-$to $weight"
    }

    private class EdgeBasedPrepareShortcut(
        prepareEdge: Int, from: Int, to: Int,
        // we use this subclass to save some memory for node-based where these are not needed
        private val origEdgeKeyFirst: Int,
        private val origEdgeKeyLast: Int,
        weight: Double, skipped1: Int, skipped2: Int, origEdgeCount: Int
    ) : PrepareShortcut(prepareEdge, from, to, weight, skipped1, skipped2, origEdgeCount) {

        override fun getOrigEdgeKeyFirstAB(): Int = origEdgeKeyFirst

        override fun getOrigEdgeKeyFirstBA(): Int = origEdgeKeyFirst

        override fun getOrigEdgeKeyLastAB(): Int = origEdgeKeyLast

        override fun getOrigEdgeKeyLastBA(): Int = origEdgeKeyLast

        override fun toString(): String =
            "" + getNodeA() + "-" + getNodeB() + " (" + origEdgeKeyFirst + ", " + origEdgeKeyLast + ") " + getWeightAB()
    }

    /**
     * This helper graph can be used to quickly obtain the edge-keys of the edges of a node. It is only used for
     * edge-based CH. In principle, we could use base graph for this, but it turned out it is faster to use this
     * graph (because it does not need to read all the edge flags to determine the access flags).
     */
    internal class OrigGraph private constructor(
        // for each node we store the index at which the edges for this node begin in the below edge list
        val firstEdgesByNode: IntArrayList,
        // we store a list of 'edges' in the format: adjNode|fwdAccess|edgeKey|bwdAccess, we use two ints for each edge
        val adjNodesAndFwdFlags: IntArrayList,
        val keysAndBwdFlags: IntArrayList
    ) {
        fun createOutOrigEdgeExplorer(): PrepareGraphOrigEdgeExplorer =
            OrigEdgeIteratorImpl(this, false)

        fun createInOrigEdgeExplorer(): PrepareGraphOrigEdgeExplorer =
            OrigEdgeIteratorImpl(this, true)

        internal class Builder {
            private val fromNodes = IntArrayList()
            private val toNodesAndFwdFlags = IntArrayList()
            private val keysAndBwdFlags = IntArrayList()
            private var maxFrom = -1
            private var maxTo = -1

            fun addEdge(from: Int, to: Int, edge: Int, fwd: Boolean, bwd: Boolean) {
                fromNodes.add(from)
                toNodesAndFwdFlags.add(getIntWithFlag(to, fwd))
                keysAndBwdFlags.add(getIntWithFlag(GHUtility.createEdgeKey(edge, false), bwd))
                maxFrom = max(maxFrom, from)
                maxTo = max(maxTo, to)

                fromNodes.add(to)
                toNodesAndFwdFlags.add(getIntWithFlag(from, bwd))
                keysAndBwdFlags.add(getIntWithFlag(GHUtility.createEdgeKey(edge, true), fwd))
                maxFrom = max(maxFrom, to)
                maxTo = max(maxTo, from)
            }

            fun build(): OrigGraph {
                val sortOrder = IndirectSort.mergesort(0, fromNodes.elementsCount, IndirectComparator.AscendingIntComparator(fromNodes.buffer))
                sortAndTrim(fromNodes, sortOrder)
                sortAndTrim(toNodesAndFwdFlags, sortOrder)
                sortAndTrim(keysAndBwdFlags, sortOrder)
                return OrigGraph(buildFirstEdgesByNode(), toNodesAndFwdFlags, keysAndBwdFlags)
            }

            private fun buildFirstEdgesByNode(): IntArrayList {
                // it is assumed the edges have been sorted already
                val numFroms = maxFrom + 1
                val numEdges = fromNodes.size()

                val firstEdgesByNode = ArrayUtil.zero(numFroms + 1)
                if (numFroms == 0) {
                    firstEdgesByNode.set(0, numEdges)
                    return firstEdgesByNode
                }
                var edgeIndex = 0
                for (from in 0 until numFroms) {
                    while (edgeIndex < numEdges && fromNodes.get(edgeIndex) < from) {
                        edgeIndex++
                    }
                    firstEdgesByNode.set(from, edgeIndex)
                }
                firstEdgesByNode.set(numFroms, numEdges)
                return firstEdgesByNode
            }

            companion object {
                private fun getIntWithFlag(value: Int, access: Boolean): Int {
                    // we use only 31 bits for the val and store an access flag along with the same int
                    // this allows for a maximum of 1073mio edges (and 2147mio nodes) in base graph
                    // which is still enough for planet-wide OSM, but if we exceed this limit we need to
                    // move the access bits somewhere else or store the edge instead of the val as we
                    // did before #2567 (only here)
                    if (value < 0)
                        throw IllegalArgumentException("Maximum node or edge key exceeded: " + value + ", max: " + Integer.MAX_VALUE)
                    var result = value shl 1
                    if (access)
                        result++
                    return result
                }
            }
        }
    }

    private class OrigEdgeIteratorImpl(
        private val graph: OrigGraph,
        private val reverse: Boolean
    ) : PrepareGraphOrigEdgeExplorer, PrepareGraphOrigEdgeIterator {
        private var node = 0
        private var endEdge = 0
        private var index = 0

        override fun setBaseNode(node: Int): PrepareGraphOrigEdgeIterator {
            this.node = node
            index = graph.firstEdgesByNode.get(node) - 1
            endEdge = graph.firstEdgesByNode.get(node + 1)
            return this
        }

        override fun next(): Boolean {
            while (true) {
                index++
                if (index >= endEdge)
                    return false
                if (hasAccess())
                    return true
            }
        }

        override fun getBaseNode(): Int = node

        override fun getAdjNode(): Int = graph.adjNodesAndFwdFlags.get(index) ushr 1

        override fun getOrigEdgeKeyFirst(): Int = graph.keysAndBwdFlags.get(index) ushr 1

        override fun getOrigEdgeKeyLast(): Int = getOrigEdgeKeyFirst()

        private fun hasAccess(): Boolean {
            val e = if (reverse)
                graph.keysAndBwdFlags.get(index)
            else
                graph.adjNodesAndFwdFlags.get(index)
            return (e and 0b01) == 0b01
        }

        override fun toString(): String =
            "" + getBaseNode() + "-" + getAdjNode() + "(" + getOrigEdgeKeyFirst() + ")"
    }
}
