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
package com.graphhopper.routing.util.parsers

import com.carrotsearch.hppc.BitSet
import com.carrotsearch.hppc.IntArrayList
import com.carrotsearch.hppc.IntHashSet
import com.carrotsearch.hppc.IntObjectMap
import com.carrotsearch.hppc.IntObjectScatterMap
import com.carrotsearch.hppc.IntScatterSet
import com.carrotsearch.hppc.IntSet
import com.carrotsearch.hppc.LongIntMap
import com.carrotsearch.hppc.LongIntScatterMap
import com.carrotsearch.hppc.procedures.IntProcedure
import com.carrotsearch.hppc.procedures.LongIntProcedure
import com.graphhopper.reader.osm.Pair
import com.graphhopper.routing.ev.BooleanEncodedValue
import com.graphhopper.storage.BaseGraph
import com.graphhopper.util.ArrayUtil
import com.graphhopper.util.BitUtil
import com.graphhopper.util.EdgeIteratorState
import com.graphhopper.util.EdgeIteratorState.Companion.REVERSE_STATE
import com.graphhopper.util.FetchMode
import com.graphhopper.util.GHUtility
import java.util.Arrays

/**
 * Used to add via-node and via-edge restrictions to a given graph. Via-edge restrictions are realized
 * by augmenting the graph with artificial edges. For proper handling of overlapping turn restrictions
 * (turn restrictions that share the same via-edges) and turn restrictions for different encoded values
 * it is important to add all restrictions with a single call.
 */
class RestrictionSetter(
    private val baseGraph: BaseGraph,
    private val turnRestrictionEncs: List<BooleanEncodedValue>
) {

    fun setRestrictions(restrictions: List<Restriction>, encBits: List<BitSet>) {
        if (restrictions.size != encBits.size)
            throw IllegalArgumentException("There must be as many encBits as restrictions. Got: " + encBits.size + " and " + restrictions.size)
        val internalRestrictions = restrictions.map { convertToInternal(it) }
        disableRedundantRestrictions(internalRestrictions, encBits)
        val artificialEdgeKeysByIncViaPairs: LongIntMap = LongIntScatterMap()
        val artificialEdgesByEdge: IntObjectMap<IntSet> = IntObjectScatterMap()
        for (i in internalRestrictions.indices) {
            if (encBits[i].cardinality() < 1) continue
            val restriction = internalRestrictions[i]
            if (restriction.edgeKeys.size() < 3)
                continue
            var incomingEdge = restriction.fromEdge
            for (j in 1 until restriction.edgeKeys.size() - 1) {
                val viaEdgeKey = restriction.edgeKeys.get(j)
                val key = BitUtil.LITTLE.toLong(incomingEdge, viaEdgeKey)
                var artificialEdgeKey: Int
                if (artificialEdgeKeysByIncViaPairs.containsKey(key)) {
                    artificialEdgeKey = artificialEdgeKeysByIncViaPairs.get(key)
                } else {
                    val viaEdge = GHUtility.getEdgeFromEdgeKey(viaEdgeKey)
                    val artificialEdgeState = baseGraph.copyEdge(viaEdge, true)
                    val artificialEdge = artificialEdgeState.edge
                    if (artificialEdgesByEdge.containsKey(viaEdge)) {
                        val artificialEdges = artificialEdgesByEdge.get(viaEdge)
                        artificialEdges.forEach(IntProcedure { a ->
                            for (turnRestrictionEnc in turnRestrictionEncs)
                                restrictTurnsBetweenEdges(turnRestrictionEnc, artificialEdgeState, a)
                        })
                        artificialEdges.add(artificialEdge)
                    } else {
                        val artificialEdges: IntSet = IntScatterSet()
                        artificialEdges.add(artificialEdge)
                        artificialEdgesByEdge.put(viaEdge, artificialEdges)
                    }
                    for (turnRestrictionEnc in turnRestrictionEncs)
                        restrictTurnsBetweenEdges(turnRestrictionEnc, artificialEdgeState, viaEdge)
                    artificialEdgeKey = artificialEdgeState.edgeKey
                    if (baseGraph.getEdgeIteratorStateForKey(viaEdgeKey).get(REVERSE_STATE))
                        artificialEdgeKey = GHUtility.reverseEdgeKey(artificialEdgeKey)
                    artificialEdgeKeysByIncViaPairs.put(key, artificialEdgeKey)
                }
                restriction.actualEdgeKeys.set(j, artificialEdgeKey)
                incomingEdge = GHUtility.getEdgeFromEdgeKey(artificialEdgeKey)
            }
        }
        artificialEdgeKeysByIncViaPairs.forEach(LongIntProcedure { incViaPair, artificialEdgeKey ->
            val incomingEdge = BitUtil.LITTLE.getIntLow(incViaPair)
            val viaEdgeKey = BitUtil.LITTLE.getIntHigh(incViaPair)
            val viaEdge = GHUtility.getEdgeFromEdgeKey(viaEdgeKey)
            val node = baseGraph.getEdgeIteratorStateForKey(viaEdgeKey).baseNode
            // we restrict turning onto the original edge and all artificial edges except the one we created for this in-edge
            // i.e. we force turning onto the artificial edge we created for this in-edge
            for (turnRestrictionEnc in turnRestrictionEncs)
                restrictTurn(turnRestrictionEnc, incomingEdge, node, viaEdge)
            val artificialEdges = artificialEdgesByEdge.get(viaEdge)
            artificialEdges.forEach(IntProcedure { a ->
                if (a != GHUtility.getEdgeFromEdgeKey(artificialEdgeKey))
                    for (turnRestrictionEnc in turnRestrictionEncs)
                        restrictTurn(turnRestrictionEnc, incomingEdge, node, a)
            })
        })
        for (i in internalRestrictions.indices) {
            if (encBits[i].cardinality() < 1) continue
            val restriction = internalRestrictions[i]
            if (restriction.edgeKeys.size() < 3) {
                val fromEdges = artificialEdgesByEdge.getOrDefault(restriction.fromEdge, IntScatterSet())
                fromEdges.add(restriction.fromEdge)
                val toEdges = artificialEdgesByEdge.getOrDefault(restriction.toEdge, IntScatterSet())
                toEdges.add(restriction.toEdge)
                for (j in turnRestrictionEncs.indices) {
                    val turnRestrictionEnc = turnRestrictionEncs[j]
                    if (encBits[i].get(j)) {
                        fromEdges.forEach(IntProcedure { from ->
                            toEdges.forEach(IntProcedure { to ->
                                restrictTurn(turnRestrictionEnc, from, restriction.viaNodes.get(0), to)
                            })
                        })
                    }
                }
            } else {
                val viaEdgeKey = restriction.actualEdgeKeys.get(restriction.actualEdgeKeys.size() - 2)
                val viaEdge = GHUtility.getEdgeFromEdgeKey(viaEdgeKey)
                val node = baseGraph.getEdgeIteratorStateForKey(viaEdgeKey).adjNode
                // For via-edge restrictions we deny turning from the from-edge onto the via-edge,
                // but allow turning onto the artificial edge(s) instead (see above). Then we deny
                // turning from the artificial edge onto the to-edge here.
                for (j in turnRestrictionEncs.indices) {
                    val turnRestrictionEnc = turnRestrictionEncs[j]
                    if (encBits[i].get(j)) {
                        restrictTurn(turnRestrictionEnc, viaEdge, node, restriction.toEdge)
                        // also restrict the turns to the artificial edges corresponding to the to-edge
                        artificialEdgesByEdge.getOrDefault(restriction.toEdge, EMPTY_SET).forEach(
                            IntProcedure { toEdge -> restrictTurn(turnRestrictionEnc, viaEdge, node, toEdge) }
                        )
                    }
                }
            }
        }
    }

    private fun disableRedundantRestrictions(restrictions: List<InternalRestriction>, encBits: List<BitSet>) {
        for (encIdx in turnRestrictionEncs.indices) {
            // first we disable all duplicates
            val uniqueRestrictions = HashSet<InternalRestriction>()
            for (i in restrictions.indices) {
                if (!encBits[i].get(encIdx))
                    continue
                if (!uniqueRestrictions.add(restrictions[i]))
                    encBits[i].clear(encIdx.toLong())
            }
            // build an index of restrictions to quickly find all restrictions containing a given edge key
            val restrictionsByEdgeKeys = IntObjectScatterMap<MutableList<InternalRestriction>>()
            for (i in restrictions.indices) {
                if (!encBits[i].get(encIdx))
                    continue
                val restriction = restrictions[i]
                for (edgeKey in restriction.edgeKeys) {
                    val idx = restrictionsByEdgeKeys.indexOf(edgeKey.value)
                    if (idx < 0) {
                        val list = ArrayList<InternalRestriction>()
                        list.add(restriction)
                        restrictionsByEdgeKeys.indexInsert(idx, edgeKey.value, list)
                    } else {
                        restrictionsByEdgeKeys.indexGet(idx).add(restriction)
                    }
                }
            }
            // Only keep restrictions that do not contain another restriction. For example, it would be unnecessary to restrict
            // 6-8-2 when 6-8 is restricted already
            for (i in restrictions.indices) {
                if (!encBits[i].get(encIdx))
                    continue
                if (containsAnotherRestriction(restrictions[i], restrictionsByEdgeKeys))
                    encBits[i].clear(encIdx.toLong())
            }
        }
    }

    private fun containsAnotherRestriction(restriction: InternalRestriction, restrictionsByEdgeKeys: IntObjectMap<MutableList<InternalRestriction>>): Boolean {
        for (edgeKey in restriction.edgeKeys) {
            val restrictionsWithThisEdgeKey = restrictionsByEdgeKeys.get(edgeKey.value)
            for (r in restrictionsWithThisEdgeKey) {
                if (r === restriction) continue
                if (r == restriction)
                    throw IllegalStateException("Equal restrictions should have already been filtered out here!")
                if (isSubsetOf(r.edgeKeys, restriction.edgeKeys))
                    return true
            }
        }
        return false
    }

    private fun restrictTurnsBetweenEdges(turnRestrictionEnc: BooleanEncodedValue, edgeState: EdgeIteratorState, otherEdge: Int) {
        restrictTurn(turnRestrictionEnc, otherEdge, edgeState.baseNode, edgeState.edge)
        restrictTurn(turnRestrictionEnc, edgeState.edge, edgeState.baseNode, otherEdge)
        restrictTurn(turnRestrictionEnc, otherEdge, edgeState.adjNode, edgeState.edge)
        restrictTurn(turnRestrictionEnc, edgeState.edge, edgeState.adjNode, otherEdge)
    }

    private fun convertToInternal(restriction: Restriction): InternalRestriction {
        val edges = restriction.edges
        if (edges.size() < 2)
            throw IllegalArgumentException("Invalid restriction, there must be at least two edges")
        else if (edges.size() == 2) {
            val fromKey = baseGraph.getEdgeIteratorState(edges.get(0), restriction.viaNode)!!.edgeKey
            val toKey = baseGraph.getEdgeIteratorState(edges.get(1), restriction.viaNode)!!.reverseEdgeKey
            return InternalRestriction(IntArrayList.from(restriction.viaNode), IntArrayList.from(fromKey, toKey))
        } else {
            val p = findNodesAndEdgeKeys(baseGraph, edges)
            p.first.remove(p.first.size() - 1)
            return InternalRestriction(p.first, p.second)
        }
    }

    private fun findNodesAndEdgeKeys(baseGraph: BaseGraph, edges: IntArrayList): Pair<IntArrayList, IntArrayList> {
        // we get a list of edges and need to find the directions of the edges and the connecting nodes
        val solutions = ArrayList<Pair<IntArrayList, IntArrayList>>()
        findEdgeChain(baseGraph, edges, 0, IntArrayList.from(), IntArrayList.from(), solutions)
        if (solutions.isEmpty()) {
            throw IllegalArgumentException("Disconnected edges: " + edges + " " + edgesToLocationString(baseGraph, edges))
        } else if (solutions.size > 1) {
            throw IllegalArgumentException("Ambiguous edge restriction: " + edges + " " + edgesToLocationString(baseGraph, edges))
        } else {
            return solutions[0]
        }
    }

    private fun findEdgeChain(baseGraph: BaseGraph, edges: IntArrayList, index: Int, nodes: IntArrayList, edgeKeys: IntArrayList, solutions: MutableList<Pair<IntArrayList, IntArrayList>>) {
        if (index == edges.size()) {
            solutions.add(Pair(IntArrayList(nodes), IntArrayList(edgeKeys)))
            return
        }
        val edgeState = baseGraph.getEdgeIteratorState(edges.get(index), Integer.MIN_VALUE)!!
        if (index == 0 || edgeState.baseNode == nodes.get(nodes.size() - 1)) {
            nodes.add(edgeState.adjNode)
            edgeKeys.add(edgeState.edgeKey)
            findEdgeChain(baseGraph, edges, index + 1, nodes, edgeKeys, solutions)
            nodes.elementsCount--
            edgeKeys.elementsCount--
        }
        if (index == 0 || edgeState.adjNode == nodes.get(nodes.size() - 1)) {
            nodes.add(edgeState.baseNode)
            edgeKeys.add(edgeState.reverseEdgeKey)
            findEdgeChain(baseGraph, edges, index + 1, nodes, edgeKeys, solutions)
            nodes.elementsCount--
            edgeKeys.elementsCount--
        }
    }

    private fun restrictTurn(turnRestrictionEnc: BooleanEncodedValue, fromEdge: Int, viaNode: Int, toEdge: Int) {
        if (fromEdge < 0 || toEdge < 0 || viaNode < 0)
            throw IllegalArgumentException("from/toEdge and viaNode must be >= 0")
        baseGraph.turnCostStorage!!.set(turnRestrictionEnc, fromEdge, viaNode, toEdge, true)
    }

    class Restriction internal constructor(
        @JvmField val edges: IntArrayList,
        internal val viaNode: Int
    ) {
        override fun toString(): String {
            return "edges: " + edges.toString() + ", viaNode: " + viaNode
        }
    }

    private class InternalRestriction(val viaNodes: IntArrayList, val edgeKeys: IntArrayList) {
        val actualEdgeKeys: IntArrayList = ArrayUtil.constant(edgeKeys.size(), -1)

        init {
            actualEdgeKeys.set(0, edgeKeys.get(0))
            actualEdgeKeys.set(edgeKeys.size() - 1, edgeKeys.get(edgeKeys.size() - 1))
        }

        val fromEdge: Int
            get() = GHUtility.getEdgeFromEdgeKey(edgeKeys.get(0))

        val toEdge: Int
            get() = GHUtility.getEdgeFromEdgeKey(edgeKeys.get(edgeKeys.size() - 1))

        override fun hashCode(): Int {
            return 31 * viaNodes.hashCode() + edgeKeys.hashCode()
        }

        override fun equals(other: Any?): Boolean {
            // this is actually needed, because we build a Set of InternalRestrictions to remove duplicates
            // no need to compare the actualEdgeKeys
            if (other !is InternalRestriction) return false
            return other.viaNodes == viaNodes && other.edgeKeys == edgeKeys
        }

        override fun toString(): String {
            val result = StringBuilder()
            for (i in 0 until viaNodes.size())
                result.append(GHUtility.getEdgeFromEdgeKey(edgeKeys.get(i))).append("-(").append(viaNodes.get(i)).append(")-")
            return result.toString() + GHUtility.getEdgeFromEdgeKey(edgeKeys.get(edgeKeys.size() - 1))
        }
    }

    companion object {
        private val EMPTY_SET: IntSet = IntHashSet.from()

        @JvmStatic
        fun createViaNodeRestriction(fromEdge: Int, viaNode: Int, toEdge: Int): Restriction {
            return Restriction(IntArrayList.from(fromEdge, toEdge), viaNode)
        }

        @JvmStatic
        fun createViaEdgeRestriction(edges: IntArrayList): Restriction {
            if (edges.size() < 3)
                throw IllegalArgumentException("Via-edge restrictions must have at least three edges, but got: " + edges.size())
            return Restriction(edges, -1)
        }

        @JvmStatic
        fun copyEncBits(encBits: BitSet): BitSet {
            return BitSet(Arrays.copyOf(encBits.bits, encBits.bits.size), encBits.wlen)
        }

        private fun isSubsetOf(candidate: IntArrayList, array: IntArrayList): Boolean {
            if (candidate.size() > array.size())
                return false
            for (i in 0..array.size() - candidate.size()) {
                var isSubset = true
                for (j in 0 until candidate.size()) {
                    if (candidate.get(j) != array.get(i + j)) {
                        isSubset = false
                        break
                    }
                }
                if (isSubset)
                    return true
            }
            return false
        }

        private fun edgesToLocationString(baseGraph: BaseGraph, edges: IntArrayList): String {
            return Arrays.stream(edges.buffer, 0, edges.size())
                .mapToObj { e -> baseGraph.getEdgeIteratorState(e, Integer.MIN_VALUE)!!.fetchWayGeometry(FetchMode.ALL) }
                .toList().toString()
        }
    }
}
