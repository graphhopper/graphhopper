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
package com.graphhopper.util.shapes

import com.graphhopper.util.DistanceCalc
import com.graphhopper.util.DistanceCalcEarth
import com.graphhopper.util.NumHelper
import com.graphhopper.util.PointList

/**
 * @author Peter Karich
 */
class Circle @JvmOverloads constructor(
    val lat: Double,
    val lon: Double,
    @JvmField val radiusInMeter: Double,
    private val calc: DistanceCalc = DistanceCalcEarth.DIST_EARTH
) : Shape {
    private val normedDist: Double = calc.calcNormalizedDist(radiusInMeter)
    private val bbox: BBox = calc.createBBox(lat, lon, radiusInMeter)

    override fun contains(lat: Double, lon: Double): Boolean = normDist(lat, lon) <= normedDist

    override val bounds: BBox
        get() = bbox

    private fun normDist(lat1: Double, lon1: Double): Double = calc.calcNormalizedDist(lat, lon, lat1, lon1)

    override fun intersects(pointList: PointList): Boolean {
        // similar code to LocationIndexTree.checkAdjacent
        val len = pointList.size()
        if (len == 0) throw IllegalArgumentException("PointList must not be empty")

        var tmpLat = pointList.getLat(0)
        var tmpLon = pointList.getLon(0)
        if (len == 1) return calc.calcNormalizedDist(lat, lon, tmpLat, tmpLon) <= normedDist

        for (pointIndex in 1 until len) {
            val wayLat = pointList.getLat(pointIndex)
            val wayLon = pointList.getLon(pointIndex)

            if (calc.validEdgeDistance(lat, lon, tmpLat, tmpLon, wayLat, wayLon)) {
                if (calc.calcNormalizedEdgeDistance(lat, lon, tmpLat, tmpLon, wayLat, wayLon) <= normedDist)
                    return true
            } else {
                if (calc.calcNormalizedDist(lat, lon, tmpLat, tmpLon) <= normedDist
                    || pointIndex + 1 == len && calc.calcNormalizedDist(lat, lon, wayLat, wayLon) <= normedDist
                )
                    return true
            }
            tmpLat = wayLat
            tmpLon = wayLon
        }
        return false
    }

    fun intersects(b: BBox): Boolean {
        // test top intersects
        if (lat > b.maxLat) {
            if (lon < b.minLon) return normDist(b.maxLat, b.minLon) <= normedDist
            if (lon > b.maxLon) return normDist(b.maxLat, b.maxLon) <= normedDist
            return b.maxLat - bbox.minLat > 0
        }

        // test bottom intersects
        if (lat < b.minLat) {
            if (lon < b.minLon) return normDist(b.minLat, b.minLon) <= normedDist
            if (lon > b.maxLon) return normDist(b.minLat, b.maxLon) <= normedDist
            return bbox.maxLat - b.minLat > 0
        }

        // test middle intersects
        if (lon < b.minLon) return bbox.maxLon - b.minLon > 0
        if (lon > b.maxLon) return b.maxLon - bbox.minLon > 0
        return true
    }

    fun contains(b: BBox): Boolean {
        if (bbox.contains(b)) {
            return contains(b.maxLat, b.minLon) && contains(b.minLat, b.minLon)
                    && contains(b.maxLat, b.maxLon) && contains(b.minLat, b.maxLon)
        }
        return false
    }

    fun contains(c: Circle): Boolean {
        val res = radiusInMeter - c.radiusInMeter
        if (res < 0) return false
        return calc.calcDist(lat, lon, c.lat, c.lon) <= res
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        // the hard cast (possible ClassCastException for a non-Circle argument) matches the
        // original Java behavior
        val b = other as Circle
        // equals within a very small range
        return NumHelper.equalsEps(lat, b.lat) && NumHelper.equalsEps(lon, b.lon)
                && NumHelper.equalsEps(radiusInMeter, b.radiusInMeter)
    }

    override fun hashCode(): Int {
        var hash = 3
        hash = 17 * hash + (lat.toBits() xor (lat.toBits() ushr 32)).toInt()
        hash = 17 * hash + (lon.toBits() xor (lon.toBits() ushr 32)).toInt()
        hash = 17 * hash + (radiusInMeter.toBits() xor (radiusInMeter.toBits() ushr 32)).toInt()
        return hash
    }

    override fun toString(): String = "$lat,$lon, radius:$radiusInMeter"
}
