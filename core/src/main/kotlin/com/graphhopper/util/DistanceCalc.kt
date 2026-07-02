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
 * Calculates the distance of two points or one point and an edge on earth via haversine formula.
 * Allows subclasses to implement less or more precise calculations.
 *
 * See http://en.wikipedia.org/wiki/Haversine_formula
 *
 * @author Peter Karich
 */
interface DistanceCalc {
    fun createBBox(lat: Double, lon: Double, radiusInMeter: Double): BBox

    fun calcCircumference(lat: Double): Double

    /**
     * Calculates distance of (from, to) in meter.
     */
    fun calcDist(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): Double

    /**
     * Calculates 3d distance of (from, to) in meter.
     */
    fun calcDist3D(fromLat: Double, fromLon: Double, fromEle: Double, toLat: Double, toLon: Double, toEle: Double): Double

    /**
     * Returns the specified length in normalized meter.
     */
    fun calcNormalizedDist(dist: Double): Double

    /**
     * Inverse to calcNormalizedDist. Returned the length in meter.
     */
    fun calcDenormalizedDist(normedDist: Double): Double

    /**
     * Calculates in normalized meter
     */
    fun calcNormalizedDist(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): Double

    /**
     * This method decides for case 1: if we should use distance(r to edge) where r=(lat,lon) or
     * case 2: min(distance(r to a), distance(r to b)) where edge=(a to b). Note that due to
     * rounding errors it cannot properly detect if it is case 1 or 90°.
     * <pre>
     * case 1 (including ):
     *   r
     *  .
     * a-------b
     * </pre>
     * <pre>
     * case 2:
     * r
     *  .
     *    a-------b
     * </pre>
     *
     * @return true for case 1 which is "on edge" or the special case of 90° to the edge
     */
    fun validEdgeDistance(r_lat_deg: Double, r_lon_deg: Double,
                          a_lat_deg: Double, a_lon_deg: Double,
                          b_lat_deg: Double, b_lon_deg: Double): Boolean

    /**
     * This method calculates the distance from r to edge (a, b) where the crossing point is c
     *
     * @return the distance in normalized meter
     */
    fun calcNormalizedEdgeDistance(r_lat_deg: Double, r_lon_deg: Double,
                                   a_lat_deg: Double, a_lon_deg: Double,
                                   b_lat_deg: Double, b_lon_deg: Double): Double

    /**
     * This method calculates the distance from r to edge (a, b) where the crossing point is c including elevation
     *
     * @return the distance in normalized meter
     */
    fun calcNormalizedEdgeDistance3D(r_lat_deg: Double, r_lon_deg: Double, r_ele_m: Double,
                                     a_lat_deg: Double, a_lon_deg: Double, a_ele_m: Double,
                                     b_lat_deg: Double, b_lon_deg: Double, b_ele_m: Double): Double

    /**
     * @return the crossing point c of the vertical line from r to line (a, b)
     */
    fun calcCrossingPointToEdge(r_lat_deg: Double, r_lon_deg: Double,
                                a_lat_deg: Double, a_lon_deg: Double,
                                b_lat_deg: Double, b_lon_deg: Double): GHPoint

    /**
     * This methods creates a point (lat, lon in degrees) in a certain distance and direction from the specified
     * point (lat, lon in degrees). The heading is measured clockwise from north in degrees. The distance is passed in meter.
     */
    fun projectCoordinate(lat: Double, lon: Double,
                          distanceInMeter: Double, headingClockwiseFromNorth: Double): GHPoint

    /**
     * This methods creates a point (lat, lon in degrees) a fraction of the distance along the path from (lat1, lon1)
     * to (lat2, lon2).
     */
    fun intermediatePoint(f: Double, lat1: Double, lon1: Double, lat2: Double, lon2: Double): GHPoint

    /*
     * Simple heuristic to detect if the specified two points are crossing the boundary +-180°. See
     * #667
     */
    fun isCrossBoundary(lon1: Double, lon2: Double): Boolean

    fun calcDistance(pointList: PointList): Double
}
