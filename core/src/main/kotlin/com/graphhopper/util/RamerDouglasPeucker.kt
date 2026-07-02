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

/**
 * Simplifies a list of 2D points which are not too far away.
 * http://en.wikipedia.org/wiki/Ramer%E2%80%93Douglas%E2%80%93Peucker_algorithm
 *
 * Calling simplify is thread safe.
 *
 * @author Peter Karich
 */
class RamerDouglasPeucker {
    private var normedMaxDist = 0.0
    private var elevationMaxDistance = 0.0
    private var maxDistance = 0.0
    private var calc: DistanceCalc = DistancePlaneProjection.DIST_PLANE
    private var approx = false

    init {
        setApproximation(true)
        // 1m
        setMaxDistance(1.0)
        // elevation ignored by default
        setElevationMaxDistance(Double.MAX_VALUE)
    }

    fun setApproximation(a: Boolean) {
        approx = a
        calc = if (approx)
            DistancePlaneProjection.DIST_PLANE
        else
            DistanceCalcEarth.DIST_EARTH
    }

    /**
     * maximum distance of discrepancy (from the normal way) in meter
     */
    fun setMaxDistance(dist: Double): RamerDouglasPeucker {
        this.normedMaxDist = calc.calcNormalizedDist(dist)
        this.maxDistance = dist
        return this
    }

    /**
     * maximum elevation distance of discrepancy (from the normal way) in meters
     */
    fun setElevationMaxDistance(dist: Double): RamerDouglasPeucker {
        this.elevationMaxDistance = dist
        return this
    }

    /**
     * Simplifies the `points`, from index 0 to size-1.
     *
     * It is a wrapper method for [RamerDouglasPeucker.simplify].
     *
     * @return The number removed points
     */
    fun simplify(points: PointList): Int = simplify(points, 0, points.size() - 1)

    fun simplify(points: PointList, fromIndex: Int, lastIndex: Int): Int =
        simplify(points, fromIndex, lastIndex, true)

    /**
     * Simplifies a part of the `points`. The `fromIndex` and `lastIndex`
     * are guaranteed to be kept.
     *
     * @param points    The PointList to simplify
     * @param fromIndex Start index to simplify, should be <= `lastIndex`
     * @param lastIndex Simplify up to this index
     * @param compress  Whether the `points` shall be compressed or not, if set to false no points
     *                  are actually removed, but instead their lat/lon/ele is only set to NaN
     * @return The number of removed points
     */
    fun simplify(points: PointList, fromIndex: Int, lastIndex: Int, compress: Boolean): Int {
        var removed = 0
        val size = lastIndex - fromIndex
        if (approx) {
            val delta = 500
            val segments = size / delta + 1
            var start = fromIndex
            for (i in 0 until segments) {
                // start of next is end of last segment, except for the last
                removed += subSimplify(points, start, Math.min(lastIndex, start + delta))
                start += delta
            }
        } else {
            removed = subSimplify(points, fromIndex, lastIndex)
        }

        if (removed > 0 && compress)
            removeNaN(points)

        return removed
    }

    // keep the points of fromIndex and lastIndex
    private fun subSimplify(points: PointList, fromIndex: Int, lastIndex: Int): Int {
        if (lastIndex - fromIndex < 2) {
            return 0
        }
        var indexWithMaxDist = -1
        var maxDist = -1.0
        val elevationFactor = maxDistance / elevationMaxDistance
        val firstLat = points.getLat(fromIndex)
        val firstLon = points.getLon(fromIndex)
        val firstEle = points.getEle(fromIndex)
        val lastLat = points.getLat(lastIndex)
        val lastLon = points.getLon(lastIndex)
        val lastEle = points.getEle(lastIndex)
        for (i in fromIndex + 1 until lastIndex) {
            val lat = points.getLat(i)
            if (java.lang.Double.isNaN(lat)) {
                continue
            }
            val lon = points.getLon(i)
            val ele = points.getEle(i)
            val dist = if (points.is3D && elevationMaxDistance < Double.MAX_VALUE && !java.lang.Double.isNaN(firstEle) && !java.lang.Double.isNaN(lastEle) && !java.lang.Double.isNaN(ele))
                calc.calcNormalizedEdgeDistance3D(
                    lat, lon, ele * elevationFactor,
                    firstLat, firstLon, firstEle * elevationFactor,
                    lastLat, lastLon, lastEle * elevationFactor)
            else
                calc.calcNormalizedEdgeDistance(lat, lon, firstLat, firstLon, lastLat, lastLon)
            if (maxDist < dist) {
                indexWithMaxDist = i
                maxDist = dist
            }
        }

        if (indexWithMaxDist < 0) {
            throw IllegalStateException("maximum not found in [$fromIndex,$lastIndex]")
        }

        var counter = 0
        if (maxDist < normedMaxDist) {
            for (i in fromIndex + 1 until lastIndex) {
                points.set(i, Double.NaN, Double.NaN, Double.NaN)
                counter++
            }
        } else {
            counter = subSimplify(points, fromIndex, indexWithMaxDist)
            counter += subSimplify(points, indexWithMaxDist, lastIndex)
        }
        return counter
    }

    companion object {
        /**
         * Fills all entries of the point list that are NaN with the subsequent values (and therefore shortens the list)
         */
        @JvmStatic
        @JvmName("removeNaN")
        internal fun removeNaN(pointList: PointList) {
            var curr = 0
            for (i in 0 until pointList.size()) {
                if (!java.lang.Double.isNaN(pointList.getLat(i))) {
                    pointList.set(curr, pointList.getLat(i), pointList.getLon(i), pointList.getEle(i))
                    curr++
                }
            }
            pointList.trimToSize(curr)
        }
    }
}
