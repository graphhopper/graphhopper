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
 * @author Peter Karich
 */
open class DistanceCalcEarth : DistanceCalc {
    /**
     * Calculates distance of (from, to) in meter.
     *
     * http://en.wikipedia.org/wiki/Haversine_formula a = sin²(Δlat/2) +
     * cos(lat1).cos(lat2).sin²(Δlong/2) c = 2.atan2(√a, √(1−a)) d = R.c
     */
    override fun calcDist(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): Double {
        val normedDist = calcNormalizedDist(fromLat, fromLon, toLat, toLon)
        return R * 2 * Math.asin(Math.sqrt(normedDist))
    }

    /**
     * This implements a rather quick solution to calculate 3D distances on earth using euclidean
     * geometry mixed with Haversine formula used for the on earth distance. The haversine formula makes
     * not so much sense as it is only important for large distances where then the rather smallish
     * heights would becomes negligible.
     */
    override fun calcDist3D(fromLat: Double, fromLon: Double, fromEle: Double,
                            toLat: Double, toLon: Double, toEle: Double): Double {
        val eleDelta = if (hasElevationDiff(fromEle, toEle)) toEle - fromEle else 0.0
        val len = calcDist(fromLat, fromLon, toLat, toLon)
        return Math.sqrt(eleDelta * eleDelta + len * len)
    }

    override fun calcDenormalizedDist(normedDist: Double): Double {
        return R * 2 * Math.asin(Math.sqrt(normedDist))
    }

    /**
     * Returns the specified length in normalized meter.
     */
    override fun calcNormalizedDist(dist: Double): Double {
        val tmp = Math.sin(dist / 2 / R)
        return tmp * tmp
    }

    override fun calcNormalizedDist(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): Double {
        val sinDeltaLat = Math.sin(Math.toRadians(toLat - fromLat) / 2)
        val sinDeltaLon = Math.sin(Math.toRadians(toLon - fromLon) / 2)
        return sinDeltaLat * sinDeltaLat +
                sinDeltaLon * sinDeltaLon * Math.cos(Math.toRadians(fromLat)) * Math.cos(Math.toRadians(toLat))
    }

    /**
     * Circumference of the earth at different latitudes (breitengrad)
     */
    override fun calcCircumference(lat: Double): Double {
        return 2 * Math.PI * R * Math.cos(Math.toRadians(lat))
    }

    open fun isDateLineCrossOver(lon1: Double, lon2: Double): Boolean {
        return Math.abs(lon1 - lon2) > 180.0
    }

    override fun createBBox(lat: Double, lon: Double, radiusInMeter: Double): BBox {
        if (radiusInMeter <= 0)
            throw IllegalArgumentException("Distance must not be zero or negative! " + radiusInMeter + " lat,lon:" + lat + "," + lon)

        // length of a circle at specified lat / dist
        val dLon = 360 / (calcCircumference(lat) / radiusInMeter)

        // length of a circle is independent of the longitude
        val dLat = 360 / (C / radiusInMeter)

        // Now return bounding box in coordinates
        return BBox(lon - dLon, lon + dLon, lat - dLat, lat + dLat)
    }

    override fun calcNormalizedEdgeDistance(r_lat_deg: Double, r_lon_deg: Double,
                                            a_lat_deg: Double, a_lon_deg: Double,
                                            b_lat_deg: Double, b_lon_deg: Double): Double {
        val shrinkFactor = calcShrinkFactor(a_lat_deg, b_lat_deg)

        val a_lat = a_lat_deg
        val a_lon = a_lon_deg * shrinkFactor

        val b_lat = b_lat_deg
        val b_lon = b_lon_deg * shrinkFactor

        val r_lat = r_lat_deg
        val r_lon = r_lon_deg * shrinkFactor

        val delta_lon = b_lon - a_lon
        val delta_lat = b_lat - a_lat

        if (delta_lat == 0.0)
        // special case: horizontal edge
            return calcNormalizedDist(a_lat_deg, r_lon_deg, r_lat_deg, r_lon_deg)

        if (delta_lon == 0.0)
        // special case: vertical edge
            return calcNormalizedDist(r_lat_deg, a_lon_deg, r_lat_deg, r_lon_deg)

        val norm = delta_lon * delta_lon + delta_lat * delta_lat
        val factor = ((r_lon - a_lon) * delta_lon + (r_lat - a_lat) * delta_lat) / norm

        // x,y is projection of r onto segment a-b
        val c_lon = a_lon + factor * delta_lon
        val c_lat = a_lat + factor * delta_lat
        return calcNormalizedDist(c_lat, c_lon / shrinkFactor, r_lat_deg, r_lon_deg)
    }

    override fun calcNormalizedEdgeDistance3D(r_lat_deg: Double, r_lon_deg: Double, r_ele_m: Double,
                                              a_lat_deg: Double, a_lon_deg: Double, a_ele_m: Double,
                                              b_lat_deg: Double, b_lon_deg: Double, b_ele_m: Double): Double {
        if (r_ele_m.isNaN() || a_ele_m.isNaN() || b_ele_m.isNaN())
            return calcNormalizedEdgeDistance(r_lat_deg, r_lon_deg, a_lat_deg, a_lon_deg, b_lat_deg, b_lon_deg)

        val shrinkFactor = calcShrinkFactor(a_lat_deg, b_lat_deg)

        val a_lat = a_lat_deg
        val a_lon = a_lon_deg * shrinkFactor
        val a_ele = a_ele_m / METERS_PER_DEGREE

        val b_lat = b_lat_deg
        val b_lon = b_lon_deg * shrinkFactor
        val b_ele = b_ele_m / METERS_PER_DEGREE

        val r_lat = r_lat_deg
        val r_lon = r_lon_deg * shrinkFactor
        val r_ele = r_ele_m / METERS_PER_DEGREE

        val delta_lon = b_lon - a_lon
        val delta_lat = b_lat - a_lat
        val delta_ele = b_ele - a_ele

        val norm = delta_lon * delta_lon + delta_lat * delta_lat + delta_ele * delta_ele
        var factor = ((r_lon - a_lon) * delta_lon + (r_lat - a_lat) * delta_lat + (r_ele - a_ele) * delta_ele) / norm
        if (factor.isNaN()) factor = 0.0

        // x,y,z is projection of r onto segment a-b
        val c_lon = a_lon + factor * delta_lon
        val c_lat = a_lat + factor * delta_lat
        val c_ele_m = (a_ele + factor * delta_ele) * METERS_PER_DEGREE
        return calcNormalizedDist(c_lat, c_lon / shrinkFactor, r_lat_deg, r_lon_deg) + calcNormalizedDist(r_ele_m - c_ele_m)
    }

    protected open fun calcShrinkFactor(a_lat_deg: Double, b_lat_deg: Double): Double {
        return Math.cos(Math.toRadians((a_lat_deg + b_lat_deg) / 2))
    }

    override fun calcCrossingPointToEdge(r_lat_deg: Double, r_lon_deg: Double,
                                         a_lat_deg: Double, a_lon_deg: Double,
                                         b_lat_deg: Double, b_lon_deg: Double): GHPoint {
        val shrinkFactor = calcShrinkFactor(a_lat_deg, b_lat_deg)
        val a_lat = a_lat_deg
        val a_lon = a_lon_deg * shrinkFactor

        val b_lat = b_lat_deg
        val b_lon = b_lon_deg * shrinkFactor

        val r_lat = r_lat_deg
        val r_lon = r_lon_deg * shrinkFactor

        val delta_lon = b_lon - a_lon
        val delta_lat = b_lat - a_lat

        if (delta_lat == 0.0)
        // special case: horizontal edge
            return GHPoint(a_lat_deg, r_lon_deg)

        if (delta_lon == 0.0)
        // special case: vertical edge
            return GHPoint(r_lat_deg, a_lon_deg)

        val norm = delta_lon * delta_lon + delta_lat * delta_lat
        val factor = ((r_lon - a_lon) * delta_lon + (r_lat - a_lat) * delta_lat) / norm

        // x,y is projection of r onto segment a-b
        val c_lon = a_lon + factor * delta_lon
        val c_lat = a_lat + factor * delta_lat
        return GHPoint(c_lat, c_lon / shrinkFactor)
    }

    override fun validEdgeDistance(r_lat_deg: Double, r_lon_deg: Double,
                                   a_lat_deg: Double, a_lon_deg: Double,
                                   b_lat_deg: Double, b_lon_deg: Double): Boolean {
        val shrinkFactor = calcShrinkFactor(a_lat_deg, b_lat_deg)
        val a_lat = a_lat_deg
        val a_lon = a_lon_deg * shrinkFactor

        val b_lat = b_lat_deg
        val b_lon = b_lon_deg * shrinkFactor

        val r_lat = r_lat_deg
        val r_lon = r_lon_deg * shrinkFactor

        val ar_x = r_lon - a_lon
        val ar_y = r_lat - a_lat
        val ab_x = b_lon - a_lon
        val ab_y = b_lat - a_lat
        val ab_ar = ar_x * ab_x + ar_y * ab_y

        val rb_x = b_lon - r_lon
        val rb_y = b_lat - r_lat
        val ab_rb = rb_x * ab_x + rb_y * ab_y

        // calculate the exact degree alpha(ar, ab) and beta(rb,ab) if it is case 1 then both angles are <= 90°
        // double ab_ar_norm = Math.sqrt(ar_x * ar_x + ar_y * ar_y) * Math.sqrt(ab_x * ab_x + ab_y * ab_y);
        // double ab_rb_norm = Math.sqrt(rb_x * rb_x + rb_y * rb_y) * Math.sqrt(ab_x * ab_x + ab_y * ab_y);
        // return Math.acos(ab_ar / ab_ar_norm) <= Math.PI / 2 && Math.acos(ab_rb / ab_rb_norm) <= Math.PI / 2;
        return ab_ar > 0 && ab_rb > 0
    }

    override fun projectCoordinate(lat: Double, lon: Double,
                                   distanceInMeter: Double, headingClockwiseFromNorth: Double): GHPoint {
        val angularDistance = distanceInMeter / R

        val latInRadians = Math.toRadians(lat)
        val lonInRadians = Math.toRadians(lon)
        val headingInRadians = Math.toRadians(headingClockwiseFromNorth)

        // This formula is taken from: http://williams.best.vwh.net/avform.htm#LL (http://www.movable-type.co.uk/scripts/latlong.html -> https://github.com/chrisveness/geodesy MIT)
        // θ=heading,δ=distance,φ1=latInRadians
        // lat2 = asin( sin φ1 ⋅ cos δ + cos φ1 ⋅ sin δ ⋅ cos θ )
        // lon2 = λ1 + atan2( sin θ ⋅ sin δ ⋅ cos φ1, cos δ − sin φ1 ⋅ sin φ2 )
        var projectedLat = Math.asin(Math.sin(latInRadians) * Math.cos(angularDistance)
                + Math.cos(latInRadians) * Math.sin(angularDistance) * Math.cos(headingInRadians))
        var projectedLon = lonInRadians + Math.atan2(Math.sin(headingInRadians) * Math.sin(angularDistance) * Math.cos(latInRadians),
                Math.cos(angularDistance) - Math.sin(latInRadians) * Math.sin(projectedLat))

        projectedLon = (projectedLon + 3 * Math.PI) % (2 * Math.PI) - Math.PI // normalise to -180..+180°

        projectedLat = Math.toDegrees(projectedLat)
        projectedLon = Math.toDegrees(projectedLon)

        return GHPoint(projectedLat, projectedLon)
    }

    override fun intermediatePoint(f: Double, lat1: Double, lon1: Double, lat2: Double, lon2: Double): GHPoint {
        val lat1radians = Math.toRadians(lat1)
        val lon1radians = Math.toRadians(lon1)
        val lat2radians = Math.toRadians(lat2)
        val lon2radians = Math.toRadians(lon2)

        // This formula is taken from: (http://www.movable-type.co.uk/scripts/latlong.html -> https://github.com/chrisveness/geodesy MIT)

        val deltaLat = lat2radians - lat1radians
        val deltaLon = lon2radians - lon1radians
        val cosLat1 = Math.cos(lat1radians)
        val cosLat2 = Math.cos(lat2radians)
        val sinHalfDeltaLat = Math.sin(deltaLat / 2)
        val sinHalfDeltaLon = Math.sin(deltaLon / 2)

        val a = sinHalfDeltaLat * sinHalfDeltaLat + cosLat1 * cosLat2 * sinHalfDeltaLon * sinHalfDeltaLon
        val angularDistance = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        val sinDistance = Math.sin(angularDistance)

        if (angularDistance == 0.0) return GHPoint(lat1, lon1)

        val A = Math.sin((1 - f) * angularDistance) / sinDistance
        val B = Math.sin(f * angularDistance) / sinDistance

        val x = A * cosLat1 * Math.cos(lon1radians) + B * cosLat2 * Math.cos(lon2radians)
        val y = A * cosLat1 * Math.sin(lon1radians) + B * cosLat2 * Math.sin(lon2radians)
        val z = A * Math.sin(lat1radians) + B * Math.sin(lat2radians)

        val midLat = Math.toDegrees(Math.atan2(z, Math.sqrt(x * x + y * y)))
        val midLon = Math.toDegrees(Math.atan2(y, x))

        return GHPoint(midLat, midLon)
    }

    override fun calcDistance(pointList: PointList): Double {
        return internCalcDistance(pointList, pointList.is3D())
    }

    private fun internCalcDistance(pointList: PointList, is3d: Boolean): Double {
        var prevLat = Double.NaN
        var prevLon = Double.NaN
        var prevEle = Double.NaN
        var dist = 0.0
        for (i in 0 until pointList.size()) {
            if (i > 0) {
                if (is3d)
                    dist += calcDist3D(prevLat, prevLon, prevEle, pointList.getLat(i), pointList.getLon(i), pointList.getEle(i))
                else
                    dist += calcDist(prevLat, prevLon, pointList.getLat(i), pointList.getLon(i))
            }

            prevLat = pointList.getLat(i)
            prevLon = pointList.getLon(i)
            if (pointList.is3D())
                prevEle = pointList.getEle(i)
        }
        return dist
    }

    override fun isCrossBoundary(lon1: Double, lon2: Double): Boolean {
        return Math.abs(lon1 - lon2) > 300
    }

    protected fun hasElevationDiff(a: Double, b: Double): Boolean {
        return a != b && !a.isNaN() && !b.isNaN()
    }

    override fun toString(): String {
        return "EXACT"
    }

    companion object {
        /**
         * mean radius of the earth
         */
        const val R = 6371000.0 // m

        /**
         * Radius of the earth at equator
         */
        const val R_EQ = 6378137.0 // m

        /**
         * Circumference of the earth
         */
        const val C = 2 * kotlin.math.PI * R
        const val KM_MILE = 1.609344
        const val METERS_PER_DEGREE = C / 360.0

        @JvmField
        val DIST_EARTH = DistanceCalcEarth()

        @JvmStatic
        fun calcDistance(pointList: PointList, is3d: Boolean): Double {
            return DIST_EARTH.internCalcDistance(pointList, is3d)
        }
    }
}
