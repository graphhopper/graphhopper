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
 * Calculates the approximate distance of two points on earth. Very good results if delat_lon is
 * not too big (see DistanceCalcTest), e.g. the distance is small.
 *
 * http://en.wikipedia.org/wiki/Geographical_distance#Spherical_Earth_projected_to_a_plane
 *
 * http://stackoverflow.com/q/1006654
 *
 * http://en.wikipedia.org/wiki/Mercator_projection#Mathematics_of_the_Mercator_projection
 * http://gis.stackexchange.com/questions/4906/why-is-law-of-cosines-more-preferable-than-haversine-when-calculating-distance-b
 *
 * @author Peter Karich
 */
class DistancePlaneProjection : DistanceCalcEarth() {
    override fun calcDist(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): Double {
        val normedDist = calcNormalizedDist(fromLat, fromLon, toLat, toLon)
        return R * Math.sqrt(normedDist)
    }

    override fun calcDist3D(fromLat: Double, fromLon: Double, fromEle: Double,
                            toLat: Double, toLon: Double, toEle: Double): Double {
        val dEleNorm = if (hasElevationDiff(fromEle, toEle)) calcNormalizedDist(toEle - fromEle) else 0.0
        val normedDist = calcNormalizedDist(fromLat, fromLon, toLat, toLon)
        return R * Math.sqrt(normedDist + dEleNorm)
    }

    override fun calcDenormalizedDist(normedDist: Double): Double {
        return R * Math.sqrt(normedDist)
    }

    override fun calcNormalizedDist(dist: Double): Double {
        val tmp = dist / R
        return tmp * tmp
    }

    override fun calcNormalizedDist(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): Double {
        val dLat = Math.toRadians(toLat - fromLat)
        val dLon = Math.toRadians(toLon - fromLon)
        val left = Math.cos(Math.toRadians((fromLat + toLat) / 2)) * dLon
        return dLat * dLat + left * left
    }

    override fun toString(): String {
        return "PLANE_PROJ"
    }

    companion object {
        @JvmField
        val DIST_PLANE = DistancePlaneProjection()
    }
}
