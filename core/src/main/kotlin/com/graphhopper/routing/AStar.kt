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

import com.graphhopper.coll.GHIntObjectHashMap
import com.graphhopper.routing.util.TraversalMode
import com.graphhopper.routing.weighting.BeelineWeightApproximator
import com.graphhopper.routing.weighting.WeightApproximator
import com.graphhopper.routing.weighting.Weighting
import com.graphhopper.storage.Graph
import com.graphhopper.util.DistancePlaneProjection
import com.graphhopper.util.EdgeIterator
import com.graphhopper.util.EdgeIterator.Companion.ANY_EDGE
import com.graphhopper.util.EdgeIterator.Companion.NO_EDGE
import com.graphhopper.util.EdgeIteratorState
import com.graphhopper.util.GHUtility
import com.graphhopper.util.Parameters
import java.util.PriorityQueue

/**
 * This class implements the A* algorithm according to
 * http://en.wikipedia.org/wiki/A*_search_algorithm
 *
 * Different distance calculations can be used via setApproximation.
 *
 * @author Peter Karich
 */
open class AStar(graph: Graph, weighting: Weighting, tMode: TraversalMode) :
    AbstractRoutingAlgorithm(graph, weighting, tMode), EdgeToEdgeRoutingAlgorithm {

    private lateinit var fromMap: GHIntObjectHashMap<AStarEntry?>
    private lateinit var fromHeap: PriorityQueue<AStarEntry>
    private var currEdge: AStarEntry? = null
    private var visitedNodes = 0
    private var to = -1
    private lateinit var weightApprox: WeightApproximator
    private var fromOutEdge = 0
    private var toInEdge = 0

    init {
        val size = Math.min(Math.max(200, graph.nodes / 10), 2000)
        initCollections(size)
        val defaultApprox = BeelineWeightApproximator(nodeAccess, weighting)
        defaultApprox.setDistanceCalc(DistancePlaneProjection.DIST_PLANE)
        setApproximation(defaultApprox)
    }

    /**
     * @param approx defines how distance to goal Node is approximated
     */
    fun setApproximation(approx: WeightApproximator): AStar {
        weightApprox = approx
        return this
    }

    protected open fun initCollections(size: Int) {
        fromMap = GHIntObjectHashMap()
        fromHeap = PriorityQueue(size)
    }

    override fun calcPath(from: Int, to: Int): Path {
        return calcPath(from, to, EdgeIterator.ANY_EDGE, EdgeIterator.ANY_EDGE)
    }

    override fun calcPath(from: Int, to: Int, fromOutEdge: Int, toInEdge: Int): Path {
        if ((fromOutEdge != ANY_EDGE || toInEdge != ANY_EDGE) && !traversalMode.isEdgeBased) {
            throw IllegalArgumentException("Restricting the start/target edges is only possible for edge-based graph traversal")
        }
        this.fromOutEdge = fromOutEdge
        this.toInEdge = toInEdge
        checkAlreadyRun()
        setupFinishTime()
        this.to = to
        if (fromOutEdge == NO_EDGE || toInEdge == NO_EDGE)
            return extractPath()
        weightApprox.setTo(to)
        val weightToGoal = weightApprox.approximate(from)
        if (weightToGoal.isInfinite())
            return extractPath()
        val startEntry = AStarEntry(EdgeIterator.NO_EDGE, from, 0 + weightToGoal, 0.0)
        fromHeap.add(startEntry)
        if (!traversalMode.isEdgeBased)
            fromMap.put(from, currEdge)
        runAlgo()
        return extractPath()
    }

    private fun runAlgo() {
        var currWeightToGoal: Double
        var estimationFullWeight: Double
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
                if (!accept(iter, curr.edge) || (curr.edge == NO_EDGE && fromOutEdge != ANY_EDGE && iter.edge != fromOutEdge))
                    continue

                val tmpWeight = GHUtility.calcWeightWithTurnWeight(weighting, iter, false, curr.edge) + curr.weightOfVisitedPath
                if (tmpWeight.isInfinite()) {
                    continue
                }
                val traversalId = traversalMode.createTraversalId(iter, false)

                var ase = fromMap.get(traversalId)
                if (ase == null || ase.weightOfVisitedPath > tmpWeight) {
                    val neighborNode = iter.adjNode
                    currWeightToGoal = weightApprox.approximate(neighborNode)
                    if (currWeightToGoal.isInfinite())
                        continue
                    estimationFullWeight = tmpWeight + currWeightToGoal
                    if (ase == null) {
                        ase = AStarEntry(iter.edge, neighborNode, estimationFullWeight, tmpWeight, curr)
                        fromMap.put(traversalId, ase)
                    } else {
                        ase.setDeleted()
                        ase = AStarEntry(iter.edge, neighborNode, estimationFullWeight, tmpWeight, curr)
                        fromMap.put(traversalId, ase)
                    }
                    fromHeap.add(ase)
                    updateBestPath(iter, ase, traversalId)
                }
            }
        }
    }

    private fun finished(): Boolean {
        val curr = currEdge!!
        return curr.adjNode == to && (toInEdge == ANY_EDGE || curr.edge == toInEdge) && (fromOutEdge == ANY_EDGE || curr.edge != NO_EDGE)
    }

    protected open fun extractPath(): Path {
        if (currEdge == null || !finished())
            return createEmptyPath()

        return PathExtractor.extractPath(graph, weighting, currEdge)
            // the path extractor uses currEdge.weight to set the weight, but this is the one that includes the
            // A* approximation, not the weight of the visited path! this is still correct, because the approximation
            // at the to-node (the end of the route) must be zero. Still it seems clearer to set the weight explicitly.
            .setWeight(currEdge!!.getWeightOfVisitedPath())
    }

    override fun getVisitedNodes(): Int = visitedNodes

    protected open fun updateBestPath(edgeState: EdgeIteratorState, bestSPTEntry: SPTEntry, traversalId: Int) {
    }

    open class AStarEntry @JvmOverloads constructor(
        edgeId: Int,
        adjNode: Int,
        weightForHeap: Double,
        @JvmField var weightOfVisitedPath: Double,
        parent: SPTEntry? = null
    ) : SPTEntry(edgeId, adjNode, weightForHeap, parent) {

        final override fun getWeightOfVisitedPath(): Double = weightOfVisitedPath

        override fun getParent(): AStarEntry? = parent as AStarEntry?
    }

    override fun getName(): String = Parameters.Algorithms.ASTAR + "|" + weightApprox
}
