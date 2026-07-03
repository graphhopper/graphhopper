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

package com.graphhopper.reader.osm

import com.graphhopper.coll.primitive.IntArrayList
import com.graphhopper.coll.primitive.LongArrayList
import com.graphhopper.coll.primitive.IntCursor
import com.graphhopper.storage.BaseGraph
import java.util.function.LongFunction

class WayToEdgeConverter(
    private val baseGraph: BaseGraph,
    private val edgesByWay: LongFunction<Iterator<IntCursor>>
) {

    /**
     * Finds the edge IDs associated with the given OSM ways that are adjacent to the given via-node.
     * For each way there can be multiple edge IDs and there should be exactly one that is adjacent to the via-node
     * for each way. Otherwise we throw [OSMRestrictionException]
     */
    @Throws(OSMRestrictionException::class)
    fun convertForViaNode(fromWays: LongArrayList, viaNode: Int, toWays: LongArrayList): NodeResult {
        if (fromWays.isEmpty || toWays.isEmpty)
            throw IllegalArgumentException("There must be at least one from- and to-way")
        if (fromWays.size() > 1 && toWays.size() > 1)
            throw IllegalArgumentException("There can only be multiple from- or to-ways, but not both")
        val result = NodeResult(fromWays.size(), toWays.size())
        for (fromWay in fromWays)
            edgesByWay.apply(fromWay.value).forEachRemaining { e ->
                if (baseGraph.isAdjacentToNode(e.value, viaNode))
                    result.fromEdges.add(e.value)
            }
        if (result.fromEdges.size() < fromWays.size())
            throw OSMRestrictionException("has from-member ways that aren't adjacent to the via-member node")
        else if (result.fromEdges.size() > fromWays.size())
            throw OSMRestrictionException("has from-member ways that aren't split at the via-member node")

        for (toWay in toWays)
            edgesByWay.apply(toWay.value).forEachRemaining { e ->
                if (baseGraph.isAdjacentToNode(e.value, viaNode))
                    result.toEdges.add(e.value)
            }
        if (result.toEdges.size() < toWays.size())
            throw OSMRestrictionException("has to-member ways that aren't adjacent to the via-member node")
        else if (result.toEdges.size() > toWays.size())
            throw OSMRestrictionException("has to-member ways that aren't split at the via-member node")
        return result
    }

    class NodeResult(numFrom: Int, numTo: Int) {
        val fromEdges: IntArrayList = IntArrayList(numFrom)
        val toEdges: IntArrayList = IntArrayList(numTo)
    }

    /**
     * Finds the edge IDs associated with the given OSM ways that are adjacent to each other. For example for given
     * from-, via- and to-ways there can be multiple edges associated with each (because each way can be split into
     * multiple edges). We then need to find the from-edge that is connected with one of the via-edges which in turn
     * must be connected with one of the to-edges. We use DFS/backtracking to do this.
     * There can also be *multiple* via-ways, but the concept is the same.
     * Note that there can also be multiple from- or to-*ways*, but only one of each of them should be considered at a
     * time. In contrast to the via-ways there are only multiple from/to-ways, because of restrictions like no_entry or
     * no_exit where there can be multiple from- or to-members. So we need to find one edge-chain for each pair of from-
     * and to-ways.
     * Besides the edge IDs we also return the node IDs that connect the edges, so we can add turn restrictions at these
     * nodes later.
     */
    @Throws(OSMRestrictionException::class)
    fun convertForViaWays(fromWays: LongArrayList, viaWays: LongArrayList, toWays: LongArrayList): EdgeResult {
        if (fromWays.isEmpty || toWays.isEmpty || viaWays.isEmpty)
            throw IllegalArgumentException("There must be at least one from-, via- and to-way")
        if (fromWays.size() > 1 && toWays.size() > 1)
            throw IllegalArgumentException("There can only be multiple from- or to-ways, but not both")
        val solutions = ArrayList<IntArrayList>()
        for (fromWay in fromWays)
            for (toWay in toWays)
                findEdgeChain(fromWay.value, viaWays, toWay.value, solutions)
        if (solutions.size < fromWays.size() * toWays.size())
            throw OSMRestrictionException("has disconnected member ways")
        else if (solutions.size > fromWays.size() * toWays.size())
            throw OSMRestrictionException("has member ways that do not form a unique path")
        return buildResult(solutions, fromWays, viaWays, toWays)
    }

    private fun findEdgeChain(fromWay: Long, viaWays: LongArrayList, toWay: Long, solutions: MutableList<IntArrayList>) {
        // For each edge chain there must be one edge associated with the from-way, at least one for each via-way and one
        // associated with the to-way. We use DFS with backtracking to find all edge chains that connect an edge
        // associated with the from-way with one associated with the to-way.
        val viaEdgesForViaWays = IntArrayList(viaWays.size())
        for (c in viaWays) {
            val iterator = edgesByWay.apply(c.value)
            viaEdgesForViaWays.add(iterator.next().value)
            iterator.forEachRemaining { i -> viaEdgesForViaWays.add(i.value) }
        }
        val toEdges = listFromIterator(edgesByWay.apply(toWay))

        // the search starts at *every* from edge
        edgesByWay.apply(fromWay).forEachRemaining { from ->
            val edge = baseGraph.getEdgeIteratorState(from.value, Int.MIN_VALUE)!!
            explore(viaEdgesForViaWays, toEdges, edge.baseNode, 0, IntArrayList.from(edge.edge, edge.baseNode), solutions)
            explore(viaEdgesForViaWays, toEdges, edge.adjNode, 0, IntArrayList.from(edge.edge, edge.adjNode), solutions)
        }
    }

    private fun explore(viaEdgesForViaWays: IntArrayList, toEdges: IntArrayList, node: Int, viaCount: Int, curr: IntArrayList, solutions: MutableList<IntArrayList>) {
        if (viaCount == viaEdgesForViaWays.size()) {
            for (to in toEdges) {
                if (baseGraph.isAdjacentToNode(to.value, node)) {
                    val solution = IntArrayList(curr)
                    solution.add(to.value)
                    solutions.add(solution)
                }
            }
            return
        }
        for (i in 0 until viaEdgesForViaWays.size()) {
            val viaEdge = viaEdgesForViaWays.get(i)
            if (viaEdge < 0) continue
            if (baseGraph.isAdjacentToNode(viaEdge, node)) {
                val otherNode = baseGraph.getOtherNode(viaEdge, node)
                curr.add(viaEdge, otherNode)
                // every via edge must only be used once, but instead of removing it we just set it to -1
                viaEdgesForViaWays.set(i, -1)
                explore(viaEdgesForViaWays, toEdges, otherNode, viaCount + 1, curr, solutions)
                // backtrack
                curr.elementsCount -= 2
                viaEdgesForViaWays.set(i, viaEdge)
            }
        }
    }

    class EdgeResult(numFrom: Int, numVia: Int, numTo: Int) {
        val fromEdges: IntArrayList = IntArrayList(numFrom)
        val viaEdges: IntArrayList = IntArrayList(numVia)
        val toEdges: IntArrayList = IntArrayList(numTo)

        /**
         * All the intermediate nodes, i.e. for an edge chain like this:
         * <pre>
         *   a   b   c   d
         * 0---1---2---3---4
         * </pre>
         * where 'a' is the from-edge and 'd' is the to-edge this will be [1,2,3]
         */
        val nodes: IntArrayList = IntArrayList(numVia + 1)
    }

    companion object {
        private fun buildResult(edgeChains: List<IntArrayList>, fromWays: LongArrayList, viaWays: LongArrayList, toWays: LongArrayList): EdgeResult {
            val result = EdgeResult(fromWays.size(), viaWays.size(), toWays.size())
            // we get multiple edge chains, but they are expected to be identical except for their first or last members
            val firstChain = edgeChains[0]
            result.fromEdges.add(firstChain.get(0))
            var i = 1
            while (i < firstChain.size() - 3) {
                result.nodes.add(firstChain.get(i))
                result.viaEdges.add(firstChain.get(i + 1))
                i += 2
            }
            result.nodes.add(firstChain.get(firstChain.size() - 2))
            result.toEdges.add(firstChain.get(firstChain.size() - 1))
            // We keep the first/last elements of all chains in case there are multiple from/to ways
            val otherChains = edgeChains.subList(1, edgeChains.size)
            if (fromWays.size() > 1) {
                if (otherChains.any { chain -> chain.get(chain.size() - 1) != firstChain.get(firstChain.size() - 1) })
                    throw IllegalArgumentException("edge chains were supposed to be the same except for their first elements, but got: $edgeChains - for: $fromWays, $viaWays, $toWays")
                otherChains.forEach { chain -> result.fromEdges.add(chain.get(0)) }
            } else if (toWays.size() > 1) {
                if (otherChains.any { chain -> chain.get(0) != firstChain.get(0) })
                    throw IllegalArgumentException("edge chains were supposed to be the same except for their last elements, but got: $edgeChains - for: $fromWays, $viaWays, $toWays")
                otherChains.forEach { chain -> result.toEdges.add(chain.get(chain.size() - 1)) }
            } else if (!otherChains.isEmpty())
                throw IllegalStateException("If there are multiple chains there must be either multiple from- or to-ways.")
            return result
        }

        private fun listFromIterator(iterator: Iterator<IntCursor>): IntArrayList {
            val result = IntArrayList()
            iterator.forEachRemaining { c -> result.add(c.value) }
            return result
        }
    }
}
