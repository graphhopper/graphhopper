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
package com.graphhopper.util

import com.graphhopper.util.shapes.BBox
import com.graphhopper.util.shapes.GHPoint

/**
 * Calculates the distance of two points or one point and an edge in euclidean space.
 *
 * @author Peter Karich
 */
class DistanceCalcEuclidean : DistanceCalcEarth() {
    override fun calcDist(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): Double {
        return Math.sqrt(calcNormalizedDist(fromLat, fromLon, toLat, toLon))
    }

    override fun calcDist3D(fromLat: Double, fromLon: Double, fromEle: Double,
                            toLat: Double, toLon: Double, toEle: Double): Double {
        return Math.sqrt(calcNormalizedDist(fromLat, fromLon, toLat, toLon) + calcNormalizedDist(toEle - fromEle))
    }

    override fun calcDenormalizedDist(normedDist: Double): Double {
        return Math.sqrt(normedDist)
    }

    /**
     * Returns the specified length in normalized meter.
     */
    override fun calcNormalizedDist(dist: Double): Double {
        return dist * dist
    }

    override fun calcShrinkFactor(a_lat_deg: Double, b_lat_deg: Double): Double {
        return 1.0
    }

    /**
     * Calculates in normalized meter
     */
    override fun calcNormalizedDist(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): Double {
        val dX = fromLon - toLon
        val dY = fromLat - toLat
        return dX * dX + dY * dY
    }

    override fun toString(): String {
        return "2D"
    }

    override fun calcCircumference(lat: Double): Double {
        throw UnsupportedOperationException("Not supported for the 2D Euclidean space")
    }

    override fun isDateLineCrossOver(lon1: Double, lon2: Double): Boolean {
        throw UnsupportedOperationException("Not supported for the 2D Euclidean space")
    }

    override fun createBBox(lat: Double, lon: Double, radiusInMeter: Double): BBox {
        throw UnsupportedOperationException("Not supported for the 2D Euclidean space")
    }

    override fun projectCoordinate(lat: Double, lon: Double,
                                   distanceInMeter: Double, headingClockwiseFromNorth: Double): GHPoint {
        throw UnsupportedOperationException("Not supported for the 2D Euclidean space")
    }

    override fun intermediatePoint(f: Double, lat1: Double, lon1: Double, lat2: Double, lon2: Double): GHPoint {
        val delatLat = lat2 - lat1
        val deltaLon = lon2 - lon1
        val midLat = lat1 + delatLat * f
        val midLon = lon1 + deltaLon * f
        return GHPoint(midLat, midLon)
    }

    override fun isCrossBoundary(lon1: Double, lon2: Double): Boolean {
        throw UnsupportedOperationException("Not supported for the 2D Euclidean space")
    }

    override fun calcNormalizedEdgeDistance(r_lat_deg: Double, r_lon_deg: Double,
                                            a_lat_deg: Double, a_lon_deg: Double,
                                            b_lat_deg: Double, b_lon_deg: Double): Double {
        return calcNormalizedEdgeDistance3D(
            r_lat_deg, r_lon_deg, 0.0,
            a_lat_deg, a_lon_deg, 0.0,
            b_lat_deg, b_lon_deg, 0.0
        )
    }

    override fun calcNormalizedEdgeDistance3D(r_lat_deg: Double, r_lon_deg: Double, r_ele_m: Double,
                                              a_lat_deg: Double, a_lon_deg: Double, a_ele_m: Double,
                                              b_lat_deg: Double, b_lon_deg: Double, b_ele_m: Double): Double {
        val dx = b_lon_deg - a_lon_deg
        val dy = b_lat_deg - a_lat_deg
        val dz = b_ele_m - a_ele_m

        val norm = dx * dx + dy * dy + dz * dz
        var factor = ((r_lon_deg - a_lon_deg) * dx + (r_lat_deg - a_lat_deg) * dy + (r_ele_m - a_ele_m) * dz) / norm
        if (factor.isNaN()) factor = 0.0

        // x,y,z is projection of r onto segment a-b
        val cx = a_lon_deg + factor * dx
        val cy = a_lat_deg + factor * dy
        val cz = a_ele_m + factor * dz

        val rdx = cx - r_lon_deg
        val rdy = cy - r_lat_deg
        val rdz = cz - r_ele_m

        return rdx * rdx + rdy * rdy + rdz * rdz
    }
}
