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

import com.graphhopper.routing.util.DirectedEdgeFilter
import com.graphhopper.storage.Graph
import com.graphhopper.storage.NodeAccess
import com.graphhopper.util.AngleCalc
import com.graphhopper.util.EdgeExplorer
import com.graphhopper.util.FetchMode
import com.graphhopper.util.NumHelper
import com.graphhopper.util.PointList
import com.graphhopper.util.shapes.GHPoint

/**
 * This class is used to determine the pairs of edges that go into/out of a node of the routing graph. Two such pairs
 * are determined: One pair for the case a given coordinate should be right of a vehicle driving into/out of the node and
 * one pair for the case where the coordinate is on the left.
 *
 * Example:
 *
 * .a  x  b
 * --- o ---
 *
 * If the location 'x' should be on the left side the incoming edge would be 'a' and the outgoing edge would be 'b'.
 * If the location 'x' should be on the right side the incoming edge would be 'b' and the outgoing edge would be 'a'.
 *
 * The returned edge IDs can have some special values: we use [com.graphhopper.util.EdgeIterator.NO_EDGE] to indicate it is
 * not possible to arrive or leave a location in a certain direction and [com.graphhopper.util.EdgeIterator.ANY_EDGE] if
 * there was no clear way to determine an edge id.
 *
 * There are a few special cases:
 * - if it is not possible to determine a clear result, such as for junctions with multiple adjacent edges
 * we return [DirectionResolverResult.unrestricted]
 * - if there is no way to reach or leave a location at all we return [DirectionResolverResult.impossible]
 * - for locations where the location can only possibly be on the left or right side (such as one-ways we return
 * [DirectionResolverResult.onlyLeft] or [DirectionResolverResult.onlyRight]
 */
class DirectionResolver(graph: Graph, private val isAccessible: DirectedEdgeFilter) {
    private val edgeExplorer: EdgeExplorer = graph.createEdgeExplorer()
    private val nodeAccess: NodeAccess = graph.nodeAccess

    /**
     * @param node     the node for which the incoming/outgoing edges should be determined
     * @param location the location next to the road relative to which the 'left' and 'right' side edges should be determined
     * @see DirectionResolver
     */
    fun resolveDirections(node: Int, location: GHPoint): DirectionResolverResult {
        val adjacentEdges = calcAdjEdges(node)
        if (adjacentEdges.numStandardEdges == 0) {
            return DirectionResolverResult.impossible()
        }
        if (!adjacentEdges.hasInEdges() || !adjacentEdges.hasOutEdges()) {
            return DirectionResolverResult.impossible()
        }
        if (adjacentEdges.nextPoints.isEmpty()) {
            return DirectionResolverResult.impossible()
        }
        if (adjacentEdges.numZeroDistanceEdges > 0) {
            // if we snap to a tower node that is adjacent to a barrier edge we apply no restrictions. this is the
            // easiest thing to do, but maybe we need a more sophisticated handling of this case in the future.
            return DirectionResolverResult.unrestricted()
        }
        val snappedPoint = Point(nodeAccess.getLat(node), nodeAccess.getLon(node))
        if (adjacentEdges.nextPoints.contains(snappedPoint)) {
            // this might happen if a pillar node of an adjacent edge has the same coordinates as the snapped point,
            // but this should be prevented by the map import already
            throw IllegalStateException("Pillar node of adjacent edge matches snapped point, this should not happen")
        }
        // we can classify the different cases by the number of different next points!
        if (adjacentEdges.nextPoints.size == 1) {
            val neighbor = adjacentEdges.nextPoints.iterator().next()
            val inEdges = adjacentEdges.getInEdges(neighbor)
            val outEdges = adjacentEdges.getOutEdges(neighbor)
            assert(inEdges.size > 0 && outEdges.size > 0) { "if there is only one next point there has to be an in edge and an out edge connected with it" }
            // if there are multiple edges going to the (single) next point we cannot return a reasonable result and
            // leave this point unrestricted
            if (inEdges.size > 1 || outEdges.size > 1) {
                return DirectionResolverResult.unrestricted()
            }
            // since there is only one next point we know this is the end of a dead end street so the right and left
            // side are treated equally and for both cases we use the only possible edge ids.
            return DirectionResolverResult.restricted(inEdges[0].edgeId, outEdges[0].edgeId, inEdges[0].edgeId, outEdges[0].edgeId)
        } else if (adjacentEdges.nextPoints.size == 2) {
            val iter = adjacentEdges.nextPoints.iterator()
            val p1 = iter.next()
            val p2 = iter.next()
            val in1 = adjacentEdges.getInEdges(p1)
            val in2 = adjacentEdges.getInEdges(p2)
            val out1 = adjacentEdges.getOutEdges(p1)
            val out2 = adjacentEdges.getOutEdges(p2)
            if (in1.size > 1 || in2.size > 1 || out1.size > 1 || out2.size > 1) {
                return DirectionResolverResult.unrestricted()
            }
            if (in1.size + in2.size == 0 || out1.size + out2.size == 0) {
                throw IllegalStateException("there has to be at least one in and one out edge when there are two next points")
            }
            if (in1.size + out1.size == 0 || in2.size + out2.size == 0) {
                throw IllegalStateException("there has to be at least one in or one out edge for each of the two next points")
            }
            val locationPoint = Point(location.lat, location.lon)
            return if (in1.isEmpty() || out2.isEmpty()) {
                resolveDirections(snappedPoint, locationPoint, in2[0], out1[0])
            } else if (in2.isEmpty() || out1.isEmpty()) {
                resolveDirections(snappedPoint, locationPoint, in1[0], out2[0])
            } else {
                resolveDirections(snappedPoint, locationPoint, in1[0], out2[0], in2[0].edgeId, out1[0].edgeId)
            }
        } else {
            // we snapped to a junction, in this case we do not apply restrictions
            // note: TOWER and PILLAR mostly occur when location is near the end of a dead end street or a sharp
            // curve, like switchbacks in the mountains of Andorra
            return DirectionResolverResult.unrestricted()
        }
    }

    private fun resolveDirections(snappedPoint: Point, queryPoint: Point, inEdge: Edge, outEdge: Edge): DirectionResolverResult {
        val rightLane = isOnRightLane(queryPoint, snappedPoint, inEdge.nextPoint, outEdge.nextPoint)
        return if (rightLane) {
            DirectionResolverResult.onlyRight(inEdge.edgeId, outEdge.edgeId)
        } else {
            DirectionResolverResult.onlyLeft(inEdge.edgeId, outEdge.edgeId)
        }
    }

    private fun resolveDirections(snappedPoint: Point, queryPoint: Point, inEdge: Edge, outEdge: Edge, altInEdge: Int, altOutEdge: Int): DirectionResolverResult {
        val inPoint = inEdge.nextPoint
        val outPoint = outEdge.nextPoint
        val rightLane = isOnRightLane(queryPoint, snappedPoint, inPoint, outPoint)
        return if (rightLane) {
            DirectionResolverResult.restricted(inEdge.edgeId, outEdge.edgeId, altInEdge, altOutEdge)
        } else {
            DirectionResolverResult.restricted(altInEdge, altOutEdge, inEdge.edgeId, outEdge.edgeId)
        }
    }

    private fun isOnRightLane(queryPoint: Point, snappedPoint: Point, inPoint: Point, outPoint: Point): Boolean {
        val qX = diffLon(snappedPoint, queryPoint)
        val qY = diffLat(snappedPoint, queryPoint)
        val iX = diffLon(snappedPoint, inPoint)
        val iY = diffLat(snappedPoint, inPoint)
        val oX = diffLon(snappedPoint, outPoint)
        val oY = diffLat(snappedPoint, outPoint)
        return !AngleCalc.ANGLE_CALC.isClockwise(iX, iY, oX, oY, qX, qY)
    }

    private fun diffLon(p: Point, q: Point): Double = q.lon - p.lon

    private fun diffLat(p: Point, q: Point): Double = q.lat - p.lat

    private fun calcAdjEdges(node: Int): AdjacentEdges {
        val adjacentEdges = AdjacentEdges()
        val iter = edgeExplorer.setBaseNode(node)
        while (iter.next()) {
            val isIn = isAccessible.accept(iter, true)
            val isOut = isAccessible.accept(iter, false)
            if (!isIn && !isOut)
                continue
            // we are interested in the coordinates of the next point on this edge, it could be the adj tower node
            // but also a pillar node
            val geometry = iter.fetchWayGeometry(FetchMode.ALL)
            var nextPointLat = geometry.getLat(1)
            var nextPointLon = geometry.getLon(1)

            var isZeroDistanceEdge = false
            if (PointList.equalsEps(nextPointLat, geometry.getLat(0)) &&
                PointList.equalsEps(nextPointLon, geometry.getLon(0))
            ) {
                if (geometry.size() > 2) {
                    // todo: special treatment in case the coordinates of the first pillar node equal those of the base tower
                    // node, see #1694
                    nextPointLat = geometry.getLat(2)
                    nextPointLon = geometry.getLon(2)
                } else if (geometry.size() == 2) {
                    // an edge where base and adj node share the same coordinates. this is the case for barrier edges that
                    // we create artificially
                    isZeroDistanceEdge = true
                } else {
                    throw IllegalStateException("Geometry has less than two points")
                }
            }
            val nextPoint = Point(nextPointLat, nextPointLon)
            val edge = Edge(iter.edge, iter.adjNode, nextPoint)
            adjacentEdges.addEdge(edge, isIn, isOut)

            if (isZeroDistanceEdge)
                adjacentEdges.numZeroDistanceEdges++
            else
                adjacentEdges.numStandardEdges++
        }
        return adjacentEdges
    }

    private class AdjacentEdges {
        private val inEdgesByNextPoint = HashMap<Point, MutableList<Edge>>(2)
        private val outEdgesByNextPoint = HashMap<Point, MutableList<Edge>>(2)
        val nextPoints: MutableSet<Point> = HashSet(2)
        var numStandardEdges = 0
        var numZeroDistanceEdges = 0

        fun addEdge(edge: Edge, isIn: Boolean, isOut: Boolean) {
            if (isIn) {
                addInEdge(edge)
            }
            if (isOut) {
                addOutEdge(edge)
            }
            addNextPoint(edge)
        }

        fun getInEdges(p: Point): List<Edge> = inEdgesByNextPoint[p] ?: emptyList()

        fun getOutEdges(p: Point): List<Edge> = outEdgesByNextPoint[p] ?: emptyList()

        fun hasInEdges(): Boolean = !inEdgesByNextPoint.isEmpty()

        fun hasOutEdges(): Boolean = !outEdgesByNextPoint.isEmpty()

        private fun addOutEdge(edge: Edge) {
            addEdge(outEdgesByNextPoint, edge)
        }

        private fun addInEdge(edge: Edge) {
            addEdge(inEdgesByNextPoint, edge)
        }

        private fun addNextPoint(edge: Edge) {
            nextPoints.add(edge.nextPoint)
        }

        companion object {
            private fun addEdge(edgesByNextPoint: MutableMap<Point, MutableList<Edge>>, edge: Edge) {
                val edges = edgesByNextPoint[edge.nextPoint]
                if (edges == null) {
                    val list = ArrayList<Edge>(2)
                    list.add(edge)
                    edgesByNextPoint[edge.nextPoint] = list
                } else {
                    edges.add(edge)
                }
            }
        }
    }

    private class Point(val lat: Double, val lon: Double) {

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || javaClass != other.javaClass) return false
            val o = other as Point
            return NumHelper.equalsEps(lat, o.lat) && NumHelper.equalsEps(lon, o.lon)
        }

        override fun hashCode(): Int {
            // it does not matter, because we only use maps with very few elements. not using GHPoint because of it's
            // broken hashCode implementation (#2445) and there is no good reason need to depend on it either
            return 0
        }

        override fun toString(): String = "$lat, $lon"
    }

    /**
     * @param nextPoint the next point of this edge, not necessarily the point corresponding to adjNode, but often
     * this is the next pillar (!) node.
     */
    private class Edge(val edgeId: Int, val adjNode: Int, val nextPoint: Point)
}
