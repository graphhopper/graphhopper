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

import com.carrotsearch.hppc.IntArrayList
import com.carrotsearch.hppc.IntIndexedContainer
import com.graphhopper.storage.Graph
import com.graphhopper.storage.NodeAccess
import com.graphhopper.util.EdgeIterator
import com.graphhopper.util.EdgeIteratorState
import com.graphhopper.util.FetchMode
import com.graphhopper.util.PointList

/**
 * This class represents the result of a shortest path calculation. It also provides methods to extract further
 * information about the found path, like instructions etc.
 *
 * @author Peter Karich
 * @author Ottavio Campana
 * @author jan soe
 * @author easbar
 */
open class Path(@JvmField val graph: Graph) {
    private val nodeAccess: NodeAccess = graph.nodeAccess

    fun getGraph(): Graph = graph
    private var weight = Double.MAX_VALUE
    private var distance_mm: Long = 0
    private var time: Long = 0
    private var edgeIds = IntArrayList()
    private var fromNode = -1
    private var endNode = -1
    private var description: List<String>? = null
    private var found = false
    private var debugInfo = ""

    /**
     * @return the description of this route alternative to make it meaningful for the user e.g. it
     * displays one or two main roads of the route.
     */
    fun getDescription(): List<String> = description ?: emptyList()

    fun setDescription(description: List<String>?): Path {
        this.description = description
        return this
    }

    fun getEdges(): IntArrayList = edgeIds

    fun setEdges(edgeIds: IntArrayList) {
        this.edgeIds = edgeIds
    }

    fun addEdge(edge: Int) {
        edgeIds.add(edge)
    }

    fun getEdgeCount(): Int = edgeIds.size()

    fun getEndNode(): Int = endNode

    fun setEndNode(end: Int): Path {
        endNode = end
        return this
    }

    fun getFromNode(): Int {
        check(fromNode >= 0) { "fromNode < 0 should not happen" }
        return fromNode
    }

    /**
     * We need to remember fromNode explicitly as its not saved in one edgeId of edgeIds.
     */
    fun setFromNode(from: Int): Path {
        fromNode = from
        return this
    }

    fun isFound(): Boolean = found

    fun setFound(found: Boolean): Path {
        this.found = found
        return this
    }

    fun addDistance_mm(distance_mm: Long): Path {
        this.distance_mm += distance_mm
        return this
    }

    /**
     * @return distance in meter
     */
    fun getDistance(): Double = distance_mm / 1000.0

    fun getDistance_mm(): Long = distance_mm

    /**
     * @return time in millis
     */
    fun getTime(): Long = time

    fun setTime(time: Long): Path {
        this.time = time
        return this
    }

    fun addTime(time: Long): Path {
        this.time += time
        return this
    }

    /**
     * This weight will be updated during the algorithm. The initial value is maximum double.
     */
    fun getWeight(): Double = weight

    fun setWeight(w: Double): Path {
        this.weight = w
        return this
    }

    /**
     * Yields the final edge of the path
     */
    fun getFinalEdge(): EdgeIteratorState? =
        graph.getEdgeIteratorState(edgeIds.get(edgeIds.size() - 1), endNode)

    fun setDebugInfo(debugInfo: String) {
        this.debugInfo = debugInfo
    }

    fun getDebugInfo(): String = debugInfo

    /**
     * Iterates over all edges in this path sorted from start to end and calls the visitor callback
     * for every edge.
     *
     * @param visitor callback to handle every edge. The edge is decoupled from the iterator and can
     *                be stored.
     */
    fun forEveryEdge(visitor: EdgeVisitor) {
        var tmpNode = getFromNode()
        val len = edgeIds.size()
        var prevEdgeId = EdgeIterator.NO_EDGE
        for (i in 0 until len) {
            var edgeBase = graph.getEdgeIteratorState(edgeIds.get(i), tmpNode)
                ?: throw IllegalStateException(
                    "Edge " + edgeIds.get(i) + " was empty when requested with node " + tmpNode
                            + ", array index:" + i + ", edges:" + edgeIds.size()
                )

            tmpNode = edgeBase.baseNode
            // more efficient swap, currently not implemented for virtual edges: visitor.next(edgeBase.detach(true), i);
            edgeBase = graph.getEdgeIteratorState(edgeBase.edge, tmpNode)!!
            visitor.next(edgeBase, i, prevEdgeId)

            prevEdgeId = edgeBase.edge
        }
        visitor.finish()
    }

    /**
     * Returns the list of all edges.
     */
    fun calcEdges(): List<EdgeIteratorState> {
        val edges = ArrayList<EdgeIteratorState>(edgeIds.size())
        if (edgeIds.isEmpty)
            return edges

        forEveryEdge(object : EdgeVisitor {
            override fun next(edge: EdgeIteratorState, index: Int, prevEdgeId: Int) {
                edges.add(edge)
            }

            override fun finish() {
            }
        })
        return edges
    }

    /**
     * @return the uncached node indices of the tower nodes in this path.
     */
    fun calcNodes(): IntIndexedContainer {
        val nodes = IntArrayList(edgeIds.size() + 1)
        if (edgeIds.isEmpty) {
            if (isFound()) {
                nodes.add(endNode)
            }
            return nodes
        }

        val tmpNode = getFromNode()
        nodes.add(tmpNode)
        forEveryEdge(object : EdgeVisitor {
            override fun next(edge: EdgeIteratorState, index: Int, prevEdgeId: Int) {
                nodes.add(edge.adjNode)
            }

            override fun finish() {
            }
        })
        return nodes
    }

    /**
     * This method calculated a list of points for this path
     *
     * @return the geometry of this path
     */
    fun calcPoints(): PointList {
        val points = PointList(edgeIds.size() + 1, nodeAccess.is3D())
        if (edgeIds.isEmpty) {
            if (isFound()) {
                points.add(nodeAccess, endNode)
            }
            return points
        }

        val tmpNode = getFromNode()
        points.add(nodeAccess, tmpNode)
        forEveryEdge(object : EdgeVisitor {
            override fun next(edge: EdgeIteratorState, index: Int, prevEdgeId: Int) {
                val pl = edge.fetchWayGeometry(FetchMode.PILLAR_AND_ADJ)
                for (j in 0 until pl.size()) {
                    points.add(pl, j)
                }
            }

            override fun finish() {
            }
        })
        return points
    }

    override fun toString(): String =
        "found: $found, weight: $weight, time: $time, distance: $distance_mm, edges: ${edgeIds.size()}"

    /**
     * The callback used in forEveryEdge.
     */
    interface EdgeVisitor {
        fun next(edge: EdgeIteratorState, index: Int, prevEdgeId: Int)

        fun finish()
    }
}
