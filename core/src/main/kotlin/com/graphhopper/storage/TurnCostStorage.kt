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

import com.carrotsearch.hppc.IntArrayList
import com.graphhopper.routing.ev.BooleanEncodedValue
import com.graphhopper.routing.ev.DecimalEncodedValue
import com.graphhopper.routing.ev.EdgeIntAccess
import com.graphhopper.routing.ev.IntsRefEdgeIntAccess
import com.graphhopper.util.Constants
import com.graphhopper.util.EdgeIterator
import com.graphhopper.util.GHUtility
import java.util.function.IntUnaryOperator

/**
 * A key/value store, where the unique keys are triples (fromEdge, viaNode, toEdge) and the values
 * are integers that can be used to store encoded values.
 *
 * @author Karl Hübner
 * @author Peter Karich
 * @author Michael Zilske
 */
class TurnCostStorage(private val baseGraph: BaseGraph, private val turnCosts: DataAccess) {

    private val edgeIntAccess: EdgeIntAccess = createEdgeIntAccess()

    var turnCostsCount = 0
        private set

    fun create(initBytes: Long): TurnCostStorage {
        turnCosts.create(initBytes)
        return this
    }

    fun flush() {
        turnCosts.setHeader(0, Constants.VERSION_TURN_COSTS)
        turnCosts.setHeader(4, BYTES_PER_ENTRY)
        turnCosts.setHeader(2 * 4, turnCostsCount)
        turnCosts.flush()
    }

    fun close() {
        turnCosts.close()
    }

    val capacity: Long
        get() = turnCosts.capacity

    fun loadExisting(): Boolean {
        if (!turnCosts.loadExisting())
            return false

        GHUtility.checkDAVersion(turnCosts.name, Constants.VERSION_TURN_COSTS, turnCosts.getHeader(0))
        if (turnCosts.getHeader(4) != BYTES_PER_ENTRY) {
            throw IllegalStateException("Number of bytes per turn cost entry does not match the current configuration: " + turnCosts.getHeader(0) + " vs. " + BYTES_PER_ENTRY)
        }
        turnCostsCount = turnCosts.getHeader(8)
        return true
    }

    fun set(bev: BooleanEncodedValue, fromEdge: Int, viaNode: Int, toEdge: Int, value: Boolean) {
        val index = findOrCreateTurnCostEntry(fromEdge, viaNode, toEdge)
        if (index < 0)
            throw IllegalStateException("Invalid index: $index at ($fromEdge, $viaNode, $toEdge)")
        bev.setBool(false, index, edgeIntAccess, value)
    }

    /**
     * Sets the turn cost at the viaNode when going from "fromEdge" to "toEdge"
     */
    fun set(turnCostEnc: DecimalEncodedValue, fromEdge: Int, viaNode: Int, toEdge: Int, cost: Double) {
        val index = findOrCreateTurnCostEntry(fromEdge, viaNode, toEdge)
        if (index < 0)
            throw IllegalStateException("Invalid index: $index at ($fromEdge, $viaNode, $toEdge)")
        turnCostEnc.setDecimal(false, index, edgeIntAccess, cost)
    }

    private fun findOrCreateTurnCostEntry(fromEdge: Int, viaNode: Int, toEdge: Int): Int {
        var index = findIndex(fromEdge, viaNode, toEdge)
        if (index < 0) {
            // create a new entry
            index = turnCostsCount
            ensureTurnCostIndex(index)
            val prevIndex = baseGraph.nodeAccess.getTurnCostIndex(viaNode)
            baseGraph.nodeAccess.setTurnCostIndex(viaNode, index)
            val pointer = toPointer(index)
            turnCosts.setInt(pointer + TC_FROM, fromEdge)
            turnCosts.setInt(pointer + TC_TO, toEdge)
            turnCosts.setInt(pointer + TC_NEXT, prevIndex)
            turnCostsCount++
        }
        return index
    }

    fun get(dev: DecimalEncodedValue, fromEdge: Int, viaNode: Int, toEdge: Int): Double {
        val index = findIndex(fromEdge, viaNode, toEdge)
        // todo: should we rather pass 0 to the encoded value so it can decide what this means?
        if (index < 0) return 0.0
        return dev.getDecimal(false, index, edgeIntAccess)
    }

    fun get(bev: BooleanEncodedValue, fromEdge: Int, viaNode: Int, toEdge: Int): Boolean {
        val index = findIndex(fromEdge, viaNode, toEdge)
        // todo: should we rather pass 0 to the encoded value so it can decide what this means?
        if (index < 0) return false
        return bev.getBool(false, index, edgeIntAccess)
    }

    private fun createEdgeIntAccess(): EdgeIntAccess {
        return object : EdgeIntAccess {
            override fun getInt(edgeId: Int, index: Int): Int =
                turnCosts.getInt(toPointer(edgeId) + TC_FLAGS)

            override fun setInt(edgeId: Int, index: Int, value: Int) {
                turnCosts.setInt(toPointer(edgeId) + TC_FLAGS, value)
            }
        }
    }

    private fun ensureTurnCostIndex(index: Int) {
        turnCosts.ensureCapacity(toPointer(index + 1))
    }

    private fun findIndex(fromEdge: Int, viaNode: Int, toEdge: Int): Int {
        if (!EdgeIterator.Edge.isValid(fromEdge) || !EdgeIterator.Edge.isValid(toEdge))
            throw IllegalArgumentException("from and to edge cannot be NO_EDGE")
        if (viaNode < 0)
            throw IllegalArgumentException("via node cannot be negative")

        val maxEntries = 1000
        var index = baseGraph.nodeAccess.getTurnCostIndex(viaNode)
        for (i in 0 until maxEntries) {
            if (index == NO_TURN_ENTRY) return -1
            val pointer = toPointer(index)
            if (fromEdge == turnCosts.getInt(pointer + TC_FROM) && toEdge == turnCosts.getInt(pointer + TC_TO))
                return index
            index = turnCosts.getInt(pointer + TC_NEXT)
        }
        throw IllegalStateException("Turn cost list for node: $viaNode is longer than expected, max: $maxEntries")
    }

    fun sortEdges(getNewEdgeForOldEdge: IntUnaryOperator) {
        for (i in 0 until turnCostsCount) {
            val pointer = toPointer(i)
            turnCosts.setInt(pointer + TC_FROM, getNewEdgeForOldEdge.applyAsInt(turnCosts.getInt(pointer + TC_FROM)))
            turnCosts.setInt(pointer + TC_TO, getNewEdgeForOldEdge.applyAsInt(turnCosts.getInt(pointer + TC_TO)))
        }
    }

    private fun toPointer(index: Int): Long = index.toLong() * BYTES_PER_ENTRY

    fun getTurnCostsCount(node: Int): Int {
        var index = baseGraph.nodeAccess.getTurnCostIndex(node)
        var count = 0
        while (index != NO_TURN_ENTRY) {
            val pointer = toPointer(index)
            index = turnCosts.getInt(pointer + TC_NEXT)
            count++
        }
        return count
    }

    val isClosed: Boolean
        get() = turnCosts.isClosed

    override fun toString(): String = "turn_cost"

    // TODO: Maybe some of the stuff above could now be re-implemented in a simpler way with some of the stuff below.
    // For now, I just wanted to iterate over all entries.

    /**
     * Returns an iterator over all entries.
     *
     * @return an iterator over all entries.
     */
    fun getAllTurnCosts(): Iterator = Itr()

    fun sortNodes() {
        val tcFroms = IntArrayList()
        val tcTos = IntArrayList()
        val tcFlags = IntArrayList()
        val tcNexts = IntArrayList()
        for (i in 0 until turnCostsCount) {
            val pointer = toPointer(i)
            tcFroms.add(turnCosts.getInt(pointer + TC_FROM))
            tcTos.add(turnCosts.getInt(pointer + TC_TO))
            tcFlags.add(turnCosts.getInt(pointer + TC_FLAGS))
            tcNexts.add(turnCosts.getInt(pointer + TC_NEXT))
        }
        val turnCostsCountBefore = turnCostsCount.toLong()
        turnCostsCount = 0
        for (node in 0 until baseGraph.nodes) {
            var firstForNode = true
            var turnCostIndex = baseGraph.nodeAccess.getTurnCostIndex(node)
            while (turnCostIndex != NO_TURN_ENTRY) {
                if (firstForNode) {
                    baseGraph.nodeAccess.setTurnCostIndex(node, turnCostsCount)
                } else {
                    val prevPointer = toPointer(turnCostsCount - 1)
                    turnCosts.setInt(prevPointer + TC_NEXT, turnCostsCount)
                }
                val pointer = toPointer(turnCostsCount)
                turnCosts.setInt(pointer + TC_FROM, tcFroms.get(turnCostIndex))
                turnCosts.setInt(pointer + TC_TO, tcTos.get(turnCostIndex))
                turnCosts.setInt(pointer + TC_FLAGS, tcFlags.get(turnCostIndex))
                turnCosts.setInt(pointer + TC_NEXT, NO_TURN_ENTRY)
                turnCostsCount++
                firstForNode = false
                turnCostIndex = tcNexts.get(turnCostIndex)
            }
        }
        if (turnCostsCountBefore != turnCostsCount.toLong())
            throw IllegalStateException("Turn cost count changed unexpectedly: $turnCostsCountBefore -> $turnCostsCount")
    }

    interface Iterator {
        val fromEdge: Int

        val viaNode: Int

        val toEdge: Int

        fun get(booleanEncodedValue: BooleanEncodedValue): Boolean

        fun getCost(encodedValue: DecimalEncodedValue): Double

        fun next(): Boolean
    }

    private inner class Itr : Iterator {
        private var _viaNode = -1
        private var turnCostIndex = -1
        private val intsRef = IntsRef(1)
        private val edgeIntAccess: EdgeIntAccess = IntsRefEdgeIntAccess(intsRef)

        private fun turnCostPtr(): Long = toPointer(turnCostIndex)

        override val fromEdge: Int
            get() = turnCosts.getInt(turnCostPtr() + TC_FROM)

        override val viaNode: Int
            get() = _viaNode

        override val toEdge: Int
            get() = turnCosts.getInt(turnCostPtr() + TC_TO)

        override fun get(booleanEncodedValue: BooleanEncodedValue): Boolean {
            intsRef.ints[0] = turnCosts.getInt(turnCostPtr() + TC_FLAGS)
            return booleanEncodedValue.getBool(false, -1, edgeIntAccess)
        }

        override fun getCost(encodedValue: DecimalEncodedValue): Double {
            intsRef.ints[0] = turnCosts.getInt(turnCostPtr() + TC_FLAGS)
            return encodedValue.getDecimal(false, -1, edgeIntAccess)
        }

        override fun next(): Boolean {
            val gotNextTci = nextTci()
            if (!gotNextTci) {
                turnCostIndex = NO_TURN_ENTRY
                var gotNextNode = true
                while (turnCostIndex == NO_TURN_ENTRY) {
                    gotNextNode = nextNode()
                    if (!gotNextNode) break
                }
                if (!gotNextNode) {
                    return false
                }
            }
            return true
        }

        private fun nextNode(): Boolean {
            _viaNode++
            if (_viaNode >= baseGraph.nodes) {
                return false
            }
            turnCostIndex = baseGraph.nodeAccess.getTurnCostIndex(_viaNode)
            return true
        }

        private fun nextTci(): Boolean {
            if (turnCostIndex == NO_TURN_ENTRY) {
                return false
            }
            turnCostIndex = turnCosts.getInt(turnCostPtr() + TC_NEXT)
            if (turnCostIndex == NO_TURN_ENTRY) {
                return false
            }
            return true
        }
    }

    companion object {
        internal const val NO_TURN_ENTRY = -1

        // we store each turn cost entry in the format |from_edge|to_edge|flags|next|. each entry has 4 bytes -> 16 bytes total
        private const val TC_FROM = 0
        private const val TC_TO = 4
        private const val TC_FLAGS = 8
        private const val TC_NEXT = 12
        private const val BYTES_PER_ENTRY = 16
    }
}
