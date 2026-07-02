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
package com.graphhopper.storage.index

import com.graphhopper.util.DistanceCalc
import com.graphhopper.util.EdgeIteratorState
import com.graphhopper.util.FetchMode
import com.graphhopper.util.Helper
import com.graphhopper.util.shapes.GHPoint
import com.graphhopper.util.shapes.GHPoint3D
import kotlin.math.abs

/**
 * Result of LocationIndex lookup.
 * <pre> X=query coordinates S=snapped coordinates: "snapping" real coords to road N=tower or pillar
 * node T=closest tower node XS=distance
 * X
 * |
 * T--S----N
 * </pre>
 * <p>
 *
 * @author Peter Karich
 */
open class Snap(queryLat: Double, queryLon: Double) {
    val queryPoint: GHPoint = GHPoint(queryLat, queryLon)

    /**
     * The distance of the query to the snapped coordinates. In meter
     */
    var queryDistance: Double = Double.MAX_VALUE
    var wayIndex: Int = -1

    /**
     * The closest matching node. This is either a tower node of the base graph
     * or a virtual node (see also QueryGraph.create(BaseGraph, List)).
     * [INVALID_NODE] if nothing found, this should be avoided via a call of 'isValid'
     */
    var closestNode: Int = INVALID_NODE
    var closestEdge: EdgeIteratorState? = null
    private var _snappedPoint: GHPoint3D? = null

    /**
     * 0 if on edge. 1 if on pillar node and 2 if on tower node.
     */
    var snappedPosition: Position? = null

    /**
     * @return true if a closest node was found
     */
    val isValid: Boolean
        get() = closestNode >= 0

    /**
     * Calculates the position of the query point 'snapped' to a close road segment or node. Call
     * calcSnappedPoint before, if not, an IllegalStateException is thrown.
     */
    open fun getSnappedPoint(): GHPoint3D =
        _snappedPoint ?: throw IllegalStateException("Calculate snapped point before!")

    fun setSnappedPoint(point: GHPoint3D?) {
        this._snappedPoint = point
    }

    /**
     * Calculates the closest point on the edge from the query point. If too close to a tower or pillar node this method
     * might change the snappedPosition and wayIndex.
     */
    fun calcSnappedPoint(distCalc: DistanceCalc) {
        val closestEdge = this.closestEdge ?: throw IllegalStateException("No closest edge?")
        if (_snappedPoint != null)
            throw IllegalStateException("Calculate snapped point only once")

        val fullPL = closestEdge.fetchWayGeometry(FetchMode.ALL)
        val tmpLat = fullPL.getLat(wayIndex)
        val tmpLon = fullPL.getLon(wayIndex)
        val tmpEle = fullPL.getEle(wayIndex)
        if (snappedPosition != Position.EDGE) {
            _snappedPoint = GHPoint3D(tmpLat, tmpLon, tmpEle)
            return
        }

        val queryLat = queryPoint.lat
        val queryLon = queryPoint.lon
        val adjLat = fullPL.getLat(wayIndex + 1)
        val adjLon = fullPL.getLon(wayIndex + 1)
        if (distCalc.validEdgeDistance(queryLat, queryLon, tmpLat, tmpLon, adjLat, adjLon)) {
            val crossingPoint = distCalc.calcCrossingPointToEdge(queryLat, queryLon, tmpLat, tmpLon, adjLat, adjLon)
            val adjEle = fullPL.getEle(wayIndex + 1)

            // We want to prevent extra virtual nodes and very short virtual edges in case the snap/crossing point is
            // very close to a tower node. Since we delayed the calculation of the crossing point until here, we need
            // to correct the Snap.Position in these cases. Note that it is possible that the query point is very far
            // from the tower node, but the crossing point is still very close to it.
            if (considerEqual(crossingPoint.lat, crossingPoint.lon, tmpLat, tmpLon)) {
                snappedPosition = if (wayIndex == 0) Position.TOWER else Position.PILLAR
                _snappedPoint = GHPoint3D(tmpLat, tmpLon, tmpEle)
                closestNode = if (wayIndex == 0) closestEdge.baseNode else closestNode
            } else if (considerEqual(crossingPoint.lat, crossingPoint.lon, adjLat, adjLon)) {
                wayIndex++
                snappedPosition = if (wayIndex == fullPL.size() - 1) Position.TOWER else Position.PILLAR
                _snappedPoint = GHPoint3D(adjLat, adjLon, adjEle)
                closestNode = if (wayIndex == fullPL.size() - 1) closestEdge.adjNode else closestNode
            } else {
                val deltaLat = adjLat - tmpLat
                val deltaLon = adjLon - tmpLon
                val elevation: Double
                if (deltaLon == 0.0 && deltaLat == 0.0)
                    elevation = tmpEle
                else {
                    // We can calculate the fraction t directly from the crossing point, without
                    // calculating the distance to the previous point:
                    // calcCrossingPointToEdge computes the point on a straight line between A and B
                    // that is closest to the query point (the "crossing point C") in the shrunk space
                    // where x_lat' = x_lat and x_lon' = x_lon*s:
                    //  c_lat' = c_lat   = a_lat   + t*(b_lat   - a_lat)
                    //  c_lon' = c_lon*s = a_lon*s + t*(b_lon*s - a_lon*s)
                    // and returns (c_lat', c_lon'/s), so:
                    //  c_lon = a_lon + t*(b_lon - a_lon)
                    // => C lies also on a straight line between A and B in lat/lon coordinates
                    val t = if (abs(deltaLat) > abs(deltaLon))
                        (crossingPoint.lat - tmpLat) / deltaLat
                    else
                        (crossingPoint.lon - tmpLon) / deltaLon
                    elevation = tmpEle + t * (adjEle - tmpEle)
                }
                _snappedPoint = GHPoint3D(crossingPoint.lat, crossingPoint.lon, elevation)
            }
        } else {
            // outside of edge segment [wayIndex, wayIndex+1] should not happen for EDGE
            assert(false) { "incorrect pos: $snappedPosition for $_snappedPoint, $fullPL, $wayIndex" }
        }
    }

    override fun toString(): String {
        val closestEdge = this.closestEdge
        if (closestEdge != null)
            return snappedPosition.toString() + ", " + closestNode + " " + closestEdge.edge + ":" + closestEdge.baseNode + "-" + closestEdge.adjNode +
                    " snap: [" + Helper.round6(_snappedPoint!!.lat) + ", " + Helper.round6(_snappedPoint!!.lon) + "]," +
                    " query: [" + Helper.round6(queryPoint.lat) + "," + Helper.round6(queryPoint.lon) + "]"
        return "$closestNode, $queryPoint, $wayIndex"
    }

    /**
     * Whether the query point is projected onto a tower node, pillar node or somewhere within
     * the closest edge.
     * <p>
     * Due to precision differences it is hard to define when something is exactly 90° or "on-node"
     * like TOWER or PILLAR or if it is more "on-edge" (EDGE). The default mechanism is to prefer
     * "on-edge" even if it could be 90°. To prefer "on-node" you could use e.g. GHPoint.equals with
     * a default precision of 1e-6.
     * <p>
     *
     * @see DistanceCalc.validEdgeDistance
     */
    enum class Position {
        EDGE, TOWER, PILLAR
    }

    companion object {
        const val INVALID_NODE = -1

        @JvmStatic
        fun considerEqual(lat: Double, lon: Double, lat2: Double, lon2: Double): Boolean =
            abs(lat - lat2) < 1e-6 && abs(lon - lon2) < 1e-6
    }
}
