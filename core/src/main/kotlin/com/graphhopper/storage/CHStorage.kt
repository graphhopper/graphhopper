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

import com.graphhopper.routing.ch.NodeOrderingProvider
import com.graphhopper.routing.ch.PrepareEncoder
import com.graphhopper.util.Constants
import com.graphhopper.util.GHUtility
import com.graphhopper.util.Helper
import com.graphhopper.util.Helper.nf
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.Locale
import java.util.function.Consumer

/**
 * DataAccess-based storage for CH shortcuts. Stores shortcuts and CH levels sequentially using two DataAccess objects
 * and gives read/write access to the different shortcut and node fields.
 *
 * This can be seen as an extension to a base graph: We assign a CH level to each node and add additional edges to
 * the graph ('shortcuts'). The shortcuts need to be ordered in a certain way, but this is not enforced here.
 *
 * @see CHStorageBuilder to build a valid storage that can be used for routing
 */
class CHStorage(dir: Directory, name: String, edgeBased: Boolean) {

    // nodes (created before the shortcuts DataAccess, like in the original constructor)
    private val nodesCH: DataAccess = dir.create("nodes_ch_$name", dir.getDefaultType("nodes_ch_$name", true))
    private val N_LEVEL: Int
    private val N_LAST_SC: Int
    private var nodeCHEntryBytes: Int
    private var nodeCount = -1

    // shortcuts
    private val shortcuts: DataAccess = dir.create("shortcuts_$name", dir.getDefaultType("shortcuts_$name", true))
    private val S_NODEA: Int
    private val S_NODEB: Int
    private val S_WEIGHT: Int
    private val S_SKIP_EDGE1: Int
    private val S_SKIP_EDGE2: Int
    private val S_ORIG_KEY_FIRST: Int
    private val S_ORIG_KEY_LAST: Int
    private var shortcutEntryBytes: Int
    private var shortcutCount = 0

    private var edgeBased: Boolean = edgeBased

    // some shortcut weights are under the minimum storable weight, and we count them here
    var numShortcutsUnderMinWeight = 0
        private set

    // some shortcut weights are over the maximum storable weight, and we count them here
    var numShortcutsOverMaxWeight = 0
        private set

    // use this to report shortcuts with too large weights
    private var highWeightShortcutConsumer: Consumer<HighWeightShortcut>? = null

    var minValidWeight = Double.POSITIVE_INFINITY
        private set

    var maxValidWeight = Double.NEGATIVE_INFINITY
        private set

    init {
        // shortcuts are stored consecutively using this layout (the last two entries only exist for edge-based):
        // NODEA | NODEB | WEIGHT | SKIP_EDGE1 | SKIP_EDGE2 | S_ORIG_FIRST | S_ORIG_LAST
        S_NODEA = 0
        S_NODEB = S_NODEA + 4
        S_WEIGHT = S_NODEB + 4
        S_SKIP_EDGE1 = S_WEIGHT + 4
        S_SKIP_EDGE2 = S_SKIP_EDGE1 + 4
        S_ORIG_KEY_FIRST = S_SKIP_EDGE2 + (if (edgeBased) 4 else 0)
        S_ORIG_KEY_LAST = S_ORIG_KEY_FIRST + (if (edgeBased) 4 else 0)
        shortcutEntryBytes = S_ORIG_KEY_LAST + 4

        // nodes/levels are stored consecutively using this layout:
        // LEVEL | N_LAST_SC
        N_LEVEL = 0
        N_LAST_SC = N_LEVEL + 4
        nodeCHEntryBytes = N_LAST_SC + 4
    }

    fun setHighWeightShortcutConsumer(highWeightShortcutConsumer: Consumer<HighWeightShortcut>?) {
        this.highWeightShortcutConsumer = highWeightShortcutConsumer
    }

    /**
     * Creates a new storage. Alternatively we could load an existing one using [loadExisting].
     * The number of nodes must be given here while the expected number of shortcuts can
     * be given to prevent some memory allocations, but is not a requirement. When in doubt rather use a small value
     * so the resulting files/byte arrays won't be unnecessarily large.
     * todo: we could also trim down the shortcuts DataAccess when we are done adding shortcuts
     */
    fun create(nodes: Int, expectedShortcuts: Int) {
        if (nodeCount >= 0)
            throw IllegalStateException("CHStorage can only be created once")
        if (nodes < 0)
            throw IllegalStateException("CHStorage must be created with a positive number of nodes")
        nodesCH.create(nodes.toLong() * nodeCHEntryBytes)
        nodeCount = nodes
        for (node in 0 until nodes)
            setLastShortcut(toNodePointer(node), -1)
        shortcuts.create(expectedShortcuts.toLong() * shortcutEntryBytes)
    }

    fun flush() {
        // nodes
        nodesCH.setHeader(0, Constants.VERSION_NODE_CH)
        nodesCH.setHeader(4, nodeCount)
        nodesCH.setHeader(8, nodeCHEntryBytes)
        nodesCH.flush()

        // shortcuts
        shortcuts.setHeader(0, Constants.VERSION_SHORTCUT)
        shortcuts.setHeader(4, shortcutCount)
        shortcuts.setHeader(8, shortcutEntryBytes)
        shortcuts.setHeader(12, numShortcutsUnderMinWeight)
        shortcuts.setHeader(16, numShortcutsOverMaxWeight)
        shortcuts.setHeader(20, if (edgeBased) 1 else 0)
        shortcuts.flush()
    }

    fun loadExisting(): Boolean {
        if (!nodesCH.loadExisting() || !shortcuts.loadExisting())
            return false

        // nodes
        val nodesCHVersion = nodesCH.getHeader(0)
        GHUtility.checkDAVersion(nodesCH.name, Constants.VERSION_NODE_CH, nodesCHVersion)
        nodeCount = nodesCH.getHeader(4)
        nodeCHEntryBytes = nodesCH.getHeader(8)

        // shortcuts
        val shortcutsVersion = shortcuts.getHeader(0)
        GHUtility.checkDAVersion(shortcuts.name, Constants.VERSION_SHORTCUT, shortcutsVersion)
        shortcutCount = shortcuts.getHeader(4)
        shortcutEntryBytes = shortcuts.getHeader(8)
        numShortcutsUnderMinWeight = shortcuts.getHeader(12)
        numShortcutsOverMaxWeight = shortcuts.getHeader(16)
        edgeBased = shortcuts.getHeader(20) == 1

        return true
    }

    fun close() {
        nodesCH.close()
        shortcuts.close()
    }

    /**
     * Adds a shortcut to the storage. Shortcuts are stored in the same order they are added. The underlying DataAccess
     * object grows automatically when adding more shortcuts.
     */
    fun shortcutNodeBased(nodeA: Int, nodeB: Int, accessFlags: Int, weight: Double, skip1: Int, skip2: Int): Int {
        if (edgeBased)
            throw IllegalArgumentException("Cannot add node-based shortcuts to edge-based CH")
        return shortcut(nodeA, nodeB, accessFlags, weight, skip1, skip2)
    }

    fun shortcutEdgeBased(nodeA: Int, nodeB: Int, accessFlags: Int, weight: Double, skip1: Int, skip2: Int, origKeyFirst: Int, origKeyLast: Int): Int {
        if (!edgeBased)
            throw IllegalArgumentException("Cannot add edge-based shortcuts to node-based CH")
        val shortcut = shortcut(nodeA, nodeB, accessFlags, weight, skip1, skip2)
        setOrigEdgeKeys(toShortcutPointer(shortcut), origKeyFirst, origKeyLast)
        return shortcut
    }

    private fun shortcut(nodeA: Int, nodeB: Int, accessFlags: Int, weight: Double, skip1: Int, skip2: Int): Int {
        if (shortcutCount == Int.MAX_VALUE)
            throw IllegalStateException("Maximum shortcut count exceeded: $shortcutCount")
        if (highWeightShortcutConsumer != null && weight >= MAX_WEIGHT)
            highWeightShortcutConsumer!!.accept(HighWeightShortcut(nodeA, nodeB, shortcutCount, weight, MAX_WEIGHT))
        if (weight < MAX_WEIGHT) {
            minValidWeight = Math.min(weight, minValidWeight)
            maxValidWeight = Math.max(weight, maxValidWeight)
        }
        val shortcutPointer = shortcutCount.toLong() * shortcutEntryBytes
        shortcutCount++
        shortcuts.ensureCapacity(shortcutCount.toLong() * shortcutEntryBytes)
        val weightInt = weightFromDouble(weight)
        setNodesAB(shortcutPointer, nodeA, nodeB, accessFlags)
        setWeightInt(shortcutPointer, weightInt)
        setSkippedEdges(shortcutPointer, skip1, skip2)
        return shortcutCount - 1
    }

    /**
     * The number of nodes of this storage.
     */
    fun getNodes(): Int = nodeCount

    /**
     * The number of shortcuts that were added to this storage
     */
    fun getShortcuts(): Int = shortcutCount

    /**
     * To use the node getters/setters you need to convert node IDs to a nodePointer first
     */
    fun toNodePointer(node: Int): Long {
        assert(node >= 0 && node < nodeCount) { "node not in bounds: [0, $nodeCount[" }
        return node.toLong() * nodeCHEntryBytes
    }

    /**
     * To use the shortcut getters/setters you need to convert shortcut IDs to an shortcutPointer first
     */
    fun toShortcutPointer(shortcut: Int): Long {
        assert(shortcut < shortcutCount) { "shortcut $shortcut not in bounds [0, $shortcutCount[" }
        return shortcut.toLong() * shortcutEntryBytes
    }

    val isEdgeBased: Boolean
        get() = edgeBased

    fun getLastShortcut(nodePointer: Long): Int = nodesCH.getInt(nodePointer + N_LAST_SC)

    fun setLastShortcut(nodePointer: Long, shortcut: Int) {
        nodesCH.setInt(nodePointer + N_LAST_SC, shortcut)
    }

    fun getLevel(nodePointer: Long): Int = nodesCH.getInt(nodePointer + N_LEVEL)

    fun setLevel(nodePointer: Long, level: Int) {
        nodesCH.setInt(nodePointer + N_LEVEL, level)
    }

    private fun setNodesAB(shortcutPointer: Long, nodeA: Int, nodeB: Int, accessFlags: Int) {
        shortcuts.setInt(shortcutPointer + S_NODEA, (nodeA shl 1) or (accessFlags and PrepareEncoder.getScFwdDir()))
        shortcuts.setInt(shortcutPointer + S_NODEB, (nodeB shl 1) or ((accessFlags and PrepareEncoder.getScBwdDir()) shr 1))
    }

    fun setWeight(shortcutPointer: Long, weight: Double) {
        setWeightInt(shortcutPointer, weightFromDouble(weight))
    }

    private fun setWeightInt(shortcutPointer: Long, weightInt: Int) {
        shortcuts.setInt(shortcutPointer + S_WEIGHT, weightInt)
    }

    fun setSkippedEdges(shortcutPointer: Long, edge1: Int, edge2: Int) {
        shortcuts.setInt(shortcutPointer + S_SKIP_EDGE1, edge1)
        shortcuts.setInt(shortcutPointer + S_SKIP_EDGE2, edge2)
    }

    fun setOrigEdgeKeys(shortcutPointer: Long, origKeyFirst: Int, origKeyLast: Int) {
        if (!edgeBased)
            throw IllegalArgumentException("Setting orig edge keys is only possible for edge-based CH")
        shortcuts.setInt(shortcutPointer + S_ORIG_KEY_FIRST, origKeyFirst)
        shortcuts.setInt(shortcutPointer + S_ORIG_KEY_LAST, origKeyLast)
    }

    fun getNodeA(shortcutPointer: Long): Int = shortcuts.getInt(shortcutPointer + S_NODEA) ushr 1

    fun getNodeB(shortcutPointer: Long): Int = shortcuts.getInt(shortcutPointer + S_NODEB) ushr 1

    fun getFwdAccess(shortcutPointer: Long): Boolean = (shortcuts.getInt(shortcutPointer + S_NODEA) and 0x1) != 0

    fun getBwdAccess(shortcutPointer: Long): Boolean = (shortcuts.getInt(shortcutPointer + S_NODEB) and 0x1) != 0

    fun getWeight(shortcutPointer: Long): Double = weightToDouble(shortcuts.getInt(shortcutPointer + S_WEIGHT))

    fun getSkippedEdge1(shortcutPointer: Long): Int = shortcuts.getInt(shortcutPointer + S_SKIP_EDGE1)

    fun getSkippedEdge2(shortcutPointer: Long): Int = shortcuts.getInt(shortcutPointer + S_SKIP_EDGE2)

    fun getOrigEdgeKeyFirst(shortcutPointer: Long): Int {
        assert(edgeBased) { "orig edge keys are only available for edge-based CH" }
        return shortcuts.getInt(shortcutPointer + S_ORIG_KEY_FIRST)
    }

    fun getOrigEdgeKeyLast(shortcutPointer: Long): Int {
        assert(edgeBased) { "orig edge keys are only available for edge-based CH" }
        return shortcuts.getInt(shortcutPointer + S_ORIG_KEY_LAST)
    }

    fun getNodeOrderingProvider(): NodeOrderingProvider {
        val numNodes = getNodes()
        val nodeOrdering = IntArray(numNodes)
        // the node ordering is the inverse of the ch levels
        // if we really want to save some memory it could be still reasonable to not create the node ordering here,
        // but search nodesCH for a given level on demand.
        for (i in 0 until numNodes) {
            val level = getLevel(toNodePointer(i))
            nodeOrdering[level] = i
        }
        return NodeOrderingProvider.fromArray(*nodeOrdering)
    }

    fun debugPrint() {
        val printMax = 100
        println("nodesCH:")
        val formatNodes = "%12s | %12s | %12s \n"
        System.out.format(Locale.ROOT, formatNodes, "#", "N_LAST_SC", "N_LEVEL")
        for (i in 0 until Math.min(nodeCount, printMax)) {
            val ptr = toNodePointer(i)
            System.out.format(Locale.ROOT, formatNodes, i, getLastShortcut(ptr), getLevel(ptr))
        }
        if (nodeCount > printMax) {
            System.out.format(Locale.ROOT, " ... %d more nodes", nodeCount - printMax)
        }
        println("shortcuts:")
        val formatShortcutsBase = "%12s | %12s | %12s | %12s | %12s | %12s"
        val formatShortcutExt = " | %12s | %12s"
        var header = String.format(Locale.ROOT, formatShortcutsBase, "#", "E_NODEA", "E_NODEB", "S_WEIGHT", "S_SKIP_EDGE1", "S_SKIP_EDGE2")
        if (isEdgeBased) {
            header += String.format(Locale.ROOT, formatShortcutExt, "S_ORIG_FIRST", "S_ORIG_LAST")
        }
        println(header)
        for (i in 0 until Math.min(shortcutCount, printMax)) {
            val ptr = toShortcutPointer(i)
            var edgeString = String.format(Locale.ROOT, formatShortcutsBase,
                    i,
                    getNodeA(ptr),
                    getNodeB(ptr),
                    getWeight(ptr),
                    getSkippedEdge1(ptr),
                    getSkippedEdge2(ptr))
            if (edgeBased) {
                edgeString += String.format(Locale.ROOT, formatShortcutExt,
                        getOrigEdgeKeyFirst(ptr),
                        getOrigEdgeKeyLast(ptr))
            }
            println(edgeString)
        }
        if (shortcutCount > printMax) {
            System.out.printf(Locale.ROOT, " ... %d more shortcut edges\n", shortcutCount - printMax)
        }
    }

    val capacity: Long
        get() = nodesCH.capacity + shortcuts.capacity

    fun getMB(): Int =
        ((shortcutEntryBytes * shortcutCount.toLong() + nodeCHEntryBytes * nodeCount.toLong()) / 1024 / 1024).toInt()

    fun toDetailsString(): String =
        "shortcuts:" + nf(shortcutCount.toLong()) + " (" + nf(shortcuts.capacity / Helper.MB) + "MB)" +
                ", nodesCH:" + nf(nodeCount.toLong()) + " (" + nf(nodesCH.capacity / Helper.MB) + "MB)"

    val isClosed: Boolean
        get() {
            assert(nodesCH.isClosed == shortcuts.isClosed)
            return nodesCH.isClosed
        }

    private fun weightFromDouble(weight: Double): Int {
        if (weight.isInfinite() || weight.isNaN()) throw IllegalArgumentException("weight should not be: $weight")
        if (weight < 0)
            throw IllegalArgumentException("weight cannot be negative but was $weight")
        if (weight % 1.0 != 0.0)
            throw IllegalArgumentException("weight must be an exact multiple of 1")
        if (weight >= MAX_WEIGHT) {
            numShortcutsOverMaxWeight++
            return MAX_STORED_INTEGER_WEIGHT.toInt() // negative
        } else
            return weight.toLong().toInt()
    }

    private fun weightToDouble(intWeight: Int): Double {
        // If the value is too large (> Integer.MAX_VALUE) the `int` is negative. Converted to `long` the JVM fills the
        // high bits with 1's which we remove via "& 0xFFFFFFFFL" to get the unsigned value. (The L is necessary or prepend 8 zeros.)
        val weightLong = intWeight.toLong() and 0xFFFFFFFFL
        if (weightLong == MAX_STORED_INTEGER_WEIGHT)
            // todo: maybe rather just cap to MAX_WEIGHT?
            return Double.POSITIVE_INFINITY
        if (weightLong >= MAX_WEIGHT)
            throw IllegalArgumentException("too large shortcut weight: $weightLong, limit: $MAX_WEIGHT")
        return weightLong.toDouble()
    }

    class HighWeightShortcut(
        @JvmField internal val nodeA: Int,
        @JvmField internal val nodeB: Int,
        @JvmField internal val shortcut: Int,
        @JvmField internal val weight: Double,
        @JvmField internal val maxWeight: Double
    )

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(CHStorage::class.java)

        // the maximum integer value we can store
        private const val MAX_STORED_INTEGER_WEIGHT: Long = Int.MAX_VALUE.toLong() shl 1

        // the maximum double weight we can store. if this is exceeded the shortcut will gain infinite weight, potentially yielding connection-not-found errors
        private const val MAX_WEIGHT: Double = MAX_STORED_INTEGER_WEIGHT.toDouble()

        @JvmStatic
        fun fromGraph(baseGraph: BaseGraph, chConfig: CHConfig): CHStorage {
            val name = chConfig.name
            val edgeBased = chConfig.isEdgeBased
            if (!baseGraph.isFrozen)
                throw IllegalStateException("graph must be frozen before we can create ch graphs")
            val store = CHStorage(baseGraph.directory, name, edgeBased)
            store.setHighWeightShortcutConsumer { s ->
                // we just log these to find potential routing errors
                val nodeAccess = baseGraph.nodeAccess
                LOGGER.warn("Setting weights larger than " + s.maxWeight + " results in infinite-weight shortcuts. " +
                        "You passed: " + s.weight + " for the shortcut " +
                        " nodeA (" + nodeAccess.getLat(s.nodeA) + "," + nodeAccess.getLon(s.nodeA) + ")" +
                        " nodeB (" + nodeAccess.getLat(s.nodeB) + "," + nodeAccess.getLon(s.nodeB) + ")")
            }
            // we use a rather small value here. this might result in more allocations later, but they should
            // not matter that much. if we expect a too large value the shortcuts DataAccess will end up
            // larger than needed, because we do not do something like trimToSize in the end.
            val expectedShortcuts = 0.3 * baseGraph.edges
            store.create(baseGraph.nodes, expectedShortcuts.toInt())
            return store
        }
    }
}
