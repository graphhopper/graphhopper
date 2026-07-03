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

import com.carrotsearch.hppc.IntArrayList
import com.graphhopper.apache.commons.collections.IntFloatBinaryHeap
import com.graphhopper.util.Helper

/**
 * Used to perform witness searches during node-based CH preparation. Witness searches at node B determine if there is a
 * path between two neighbor nodes A and C when we exclude B and check if this path is shorter than or equal to A-B-C.
 */
class NodeBasedWitnessPathSearcher(graph: CHPreparationGraph) {
    private val outEdgeExplorer: PrepareGraphEdgeExplorer = graph.createOutEdgeExplorer()
    private val weights = DoubleArray(graph.getNodes()) { Double.POSITIVE_INFINITY }
    private val changedNodes = IntArrayList()
    private val heap = IntFloatBinaryHeap(1000)
    private var ignoreNode = -1
    private var settledNodes = 0

    /**
     * Sets up a search for given start node and an ignored node. The shortest path tree will be re-used for different
     * target nodes until this method is called again.
     */
    fun init(startNode: Int, ignoreNode: Int) {
        reset()
        this.ignoreNode = ignoreNode
        weights[startNode] = 0.0
        changedNodes.add(startNode)
        heap.insert(0.0, startNode)
    }

    /**
     * Runs or continues a Dijkstra search starting at the startNode and ignoring the ignoreNode given in init().
     * If the shortest path is found we return its weight. However, this method also returns early if any path was
     * found for which the weight is below or equal to the given acceptedWeight, or the given maximum number of settled
     * nodes is exceeded. In these cases the returned weight can be larger than the actual weight of the shortest path.
     * In any case we get an upper bound for the real shortest path weight.
     *
     * @param targetNode      the target of the search. if this node is settled we return the weight of the shortest path
     * @param acceptedWeight  once we find a path with weight smaller than or equal to this we return the weight. the
     *                        returned weight might be larger than the weight of the real shortest path. if there is
     *                        no path with weight smaller than or equal to this we stop the search and return the best
     *                        path we found.
     * @param maxSettledNodes once the number of settled nodes exceeds this number we return the currently found best
     *                        weight path. in this case we might not have found a path at all.
     * @return the weight of the found path or [Double.POSITIVE_INFINITY] if no path was found
     */
    fun findUpperBound(targetNode: Int, acceptedWeight: Double, maxSettledNodes: Int): Double {
        // todo: for historic reasons we count the number of settled nodes for each call of this method
        //       *not* the total number of settled nodes since starting the search (which corresponds
        //       to the size of the settled part of the shortest path tree). it's probably worthwhile
        //       to change this in the future.
        while (!heap.isEmpty() && settledNodes < maxSettledNodes && heap.peekKey() <= acceptedWeight) {
            if (weights[targetNode] <= acceptedWeight)
                // we found *a* path to the target node (not necessarily the shortest), and the weight is acceptable, so we stop
                return weights[targetNode]
            val node = heap.poll()
            val iter = outEdgeExplorer.setBaseNode(node)
            while (iter.next()) {
                val adjNode = iter.getAdjNode()
                if (adjNode == ignoreNode)
                    continue
                val weight = weights[node] + iter.getWeight()
                if (weight.isInfinite())
                    continue
                val adjWeight = weights[adjNode]
                if (adjWeight == Double.POSITIVE_INFINITY) {
                    weights[adjNode] = weight
                    heap.insert(weight, adjNode)
                    changedNodes.add(adjNode)
                } else if (weight < adjWeight) {
                    weights[adjNode] = weight
                    heap.update(weight, adjNode)
                }
            }
            settledNodes++
            if (node == targetNode)
                // we have settled the target node, we now know the exact weight of the shortest path and return
                return weights[node]
        }

        return weights[targetNode]
    }

    fun getSettledNodes(): Int = settledNodes

    private fun reset() {
        for (c in changedNodes)
            weights[c.value] = Double.POSITIVE_INFINITY
        changedNodes.elementsCount = 0
        heap.clear()
        ignoreNode = -1
        settledNodes = 0
    }

    /**
     * @return currently used memory in MB (approximately)
     */
    fun getMemoryUsageAsString(): String {
        return ((8L * weights.size
                + changedNodes.buffer.size * 4L
                + heap.getMemoryUsage()
                ) / Helper.MB).toString() + "MB"
    }
}
