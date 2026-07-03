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
package com.graphhopper.routing

import com.carrotsearch.hppc.IntObjectMap
import com.graphhopper.coll.GHIntObjectHashMap
import com.graphhopper.routing.util.TraversalMode
import com.graphhopper.routing.weighting.Weighting
import com.graphhopper.storage.Graph
import com.graphhopper.util.EdgeIteratorState
import com.graphhopper.util.GHUtility
import com.graphhopper.util.Parameters
import java.util.PriorityQueue

/**
 * Implements a single source shortest path algorithm
 * http://en.wikipedia.org/wiki/Dijkstra's_algorithm
 *
 * @author Peter Karich
 */
open class Dijkstra(graph: Graph, weighting: Weighting, tMode: TraversalMode) :
    AbstractRoutingAlgorithm(graph, weighting, tMode) {

    protected lateinit var fromMap: IntObjectMap<SPTEntry?>
    protected lateinit var fromHeap: PriorityQueue<SPTEntry>

    @JvmField
    protected var currEdge: SPTEntry? = null
    private var visitedNodes = 0
    private var to = -1

    init {
        val size = Math.min(Math.max(200, graph.nodes / 10), 2000)
        initCollections(size)
    }

    protected open fun initCollections(size: Int) {
        fromHeap = PriorityQueue(size)
        fromMap = GHIntObjectHashMap(size)
    }

    override fun calcPath(from: Int, to: Int): Path {
        checkAlreadyRun()
        setupFinishTime()
        this.to = to
        val startEntry = SPTEntry(from, 0.0)
        fromHeap.add(startEntry)
        if (!traversalMode.isEdgeBased)
            fromMap.put(from, currEdge)
        runAlgo()
        return extractPath()
    }

    protected open fun runAlgo() {
        while (!fromHeap.isEmpty()) {
            val curr = fromHeap.poll()
            currEdge = curr
            if (curr.isDeleted())
                continue
            visitedNodes++
            if (isMaxVisitedNodesExceeded() || finished() || isTimeoutExceeded())
                break

            val currNode = curr.adjNode
            val iter = edgeExplorer.setBaseNode(currNode)
            while (iter.next()) {
                if (!accept(iter, curr.edge))
                    continue

                val tmpWeight = GHUtility.calcWeightWithTurnWeight(weighting, iter, false, curr.edge) + curr.weight
                if (tmpWeight.isInfinite()) {
                    continue
                }
                val traversalId = traversalMode.createTraversalId(iter, false)

                var nEdge = fromMap.get(traversalId)
                if (nEdge == null) {
                    nEdge = SPTEntry(iter.edge, iter.adjNode, tmpWeight, curr)
                    fromMap.put(traversalId, nEdge)
                    fromHeap.add(nEdge)
                } else if (nEdge.weight > tmpWeight) {
                    nEdge.setDeleted()
                    nEdge = SPTEntry(iter.edge, iter.adjNode, tmpWeight, curr)
                    fromMap.put(traversalId, nEdge)
                    fromHeap.add(nEdge)
                } else
                    continue

                updateBestPath(iter, nEdge, traversalId)
            }
        }
    }

    protected open fun finished(): Boolean = currEdge!!.adjNode == to

    private fun extractPath(): Path {
        if (currEdge == null || !finished())
            return createEmptyPath()

        return PathExtractor.extractPath(graph, weighting, currEdge)
    }

    override fun getVisitedNodes(): Int = visitedNodes

    protected open fun updateBestPath(edgeState: EdgeIteratorState, bestSPTEntry: SPTEntry, traversalId: Int) {
    }

    override fun getName(): String = Parameters.Algorithms.DIJKSTRA
}
