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
package com.graphhopper.routing.util

import com.carrotsearch.hppc.IntArrayDeque
import com.carrotsearch.hppc.IntScatterSet
import com.carrotsearch.hppc.IntSet
import com.graphhopper.storage.Graph
import com.graphhopper.storage.NodeAccess
import com.graphhopper.util.DistancePlaneProjection.Companion.DIST_PLANE
import com.graphhopper.util.EdgeIteratorState
import com.graphhopper.util.GHUtility
import com.graphhopper.util.shapes.GHPoint
import java.util.function.BiConsumer
import java.util.function.ToDoubleFunction
import java.util.stream.IntStream
import java.util.stream.Stream

class RoadDensityCalculator(private val graph: Graph) {
    private val edgeExplorer = graph.createEdgeExplorer()
    private val visited: IntSet = IntScatterSet()
    private val deque: IntArrayDeque = IntArrayDeque(100)

    /**
     * @param radius         in meters
     * @param calcRoadFactor weighting function. use this to define how different kinds of roads shall contribute to the calculated road density
     * @return the road density in the vicinity of the given edge, i.e. the weighted road length divided by the squared radius
     */
    fun calcRoadDensity(edge: EdgeIteratorState, radius: Double, calcRoadFactor: ToDoubleFunction<EdgeIteratorState>): Double {
        visited.clear()
        deque.tail = 0
        deque.head = deque.tail
        var totalRoadWeight = 0.0
        val na = graph.nodeAccess
        val baseNode = edge.baseNode
        val adjNode = edge.adjNode
        val center = GHPoint(getLat(na, baseNode, adjNode), getLon(na, baseNode, adjNode))
        deque.addLast(baseNode)
        deque.addLast(adjNode)
        visited.add(baseNode)
        visited.add(adjNode)
        // we just do a BFS search and sum up all the road lengths
        val radiusNormalized = DIST_PLANE.calcNormalizedDist(radius)
        // for long tunnels or motorway sections where the distance between the exit points and the
        // center is larger than the radius it is important to continue the search even outside the radius
        val minPolls = (radius / 2).toInt()
        var polls = 0
        while (!deque.isEmpty) {
            val node = deque.removeFirst()
            polls++
            val distance = DIST_PLANE.calcNormalizedDist(center.lat, center.lon, na.getLat(node), na.getLon(node))
            if (polls > minPolls && distance > radiusNormalized)
                continue
            val iter = edgeExplorer.setBaseNode(node)
            while (iter.next()) {
                if (visited.contains(iter.adjNode))
                    continue
                visited.add(iter.adjNode)
                if (distance <= radiusNormalized)
                    totalRoadWeight += calcRoadFactor.applyAsDouble(iter)
                deque.addLast(iter.adjNode)
            }
        }
        return totalRoadWeight / radius / radius
    }

    companion object {
        /**
         * Loops over all edges of the graph and calls the given edgeHandler for each edge. This is done in parallel using
         * the given number of threads. For every call we can calculate the road density using the provided thread local
         * road density calculator.
         */
        @JvmStatic
        fun calcRoadDensities(graph: Graph, edgeHandler: BiConsumer<RoadDensityCalculator, EdgeIteratorState>, threads: Int) {
            val calculator = ThreadLocal.withInitial { RoadDensityCalculator(graph) }
            val roadDensityWorkers: Stream<Runnable> = IntStream.range(0, graph.edges)
                .mapToObj { i ->
                    Runnable {
                        val edge = graph.getEdgeIteratorState(i, Int.MIN_VALUE)!!
                        edgeHandler.accept(calculator.get(), edge)
                    }
                }
            GHUtility.runConcurrently(roadDensityWorkers, threads)
        }

        private fun getLat(na: NodeAccess, baseNode: Int, adjNode: Int): Double =
            (na.getLat(baseNode) + na.getLat(adjNode)) / 2

        private fun getLon(na: NodeAccess, baseNode: Int, adjNode: Int): Double =
            (na.getLon(baseNode) + na.getLon(adjNode)) / 2
    }
}
