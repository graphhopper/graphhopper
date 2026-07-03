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
package com.graphhopper.reader.dem

import com.graphhopper.util.Helper.round2
import com.graphhopper.util.PointList

/**
 * Elevation interpolator calculates elevation for the given lat/lon coordinates
 * based on lat/lon/ele coordinates of the given points.
 *
 * In case of two points, elevation is calculated using linear interpolation
 * (see [calculateElevationBasedOnTwoPoints]).
 *
 * In case of three points, elevation is calculated using planar interpolation
 * (see [calculateElevationBasedOnThreePoints]).
 *
 * In case of more than three points, elevation is calculated using the
 * interpolation method described in the
 * [following post](http://math.stackexchange.com/a/1930758/140512)
 * (see [calculateElevationBasedOnPointList]).
 *
 * @author Alexey Valikov
 */
class ElevationInterpolator {

    fun calculateElevationBasedOnTwoPoints(lat: Double, lon: Double, lat0: Double,
                                           lon0: Double, ele0: Double, lat1: Double, lon1: Double, ele1: Double): Double {
        val dlat0 = lat0 - lat
        val dlon0 = lon0 - lon
        val dlat1 = lat1 - lat
        val dlon1 = lon1 - lon
        val l0 = Math.sqrt(dlon0 * dlon0 + dlat0 * dlat0)
        val l1 = Math.sqrt(dlon1 * dlon1 + dlat1 * dlat1)
        val l = l0 + l1
        if (l < EPSILON) {
            // If points are too close to each other, return elevation of the
            // point which is closer;
            return if (l0 <= l1) ele0 else ele1
        } else {
            // Otherwise do linear interpolation
            return round2(ele0 + (ele1 - ele0) * l0 / l)
        }
    }

    fun calculateElevationBasedOnThreePoints(lat: Double, lon: Double, lat0: Double,
                                             lon0: Double, ele0: Double, lat1: Double, lon1: Double, ele1: Double, lat2: Double,
                                             lon2: Double, ele2: Double): Double {

        val dlat10 = lat1 - lat0
        val dlon10 = lon1 - lon0
        val dele10 = ele1 - ele0
        val dlat20 = lat2 - lat0
        val dlon20 = lon2 - lon0
        val dele20 = ele2 - ele0

        val a = dlon10 * dele20 - dele10 * dlon20
        val b = dele10 * dlat20 - dlat10 * dele20
        val c = dlat10 * dlon20 - dlon10 * dlat20

        if (Math.abs(c) < EPSILON) {
            val dlat21 = lat2 - lat1
            val dlon21 = lon2 - lon1
            val dele21 = ele2 - ele1

            val l10 = dlat10 * dlat10 + dlon10 * dlon10 + dele10 * dele10
            val l20 = dlat20 * dlat20 + dlon20 * dlon20 + dele20 * dele20
            val l21 = dlat21 * dlat21 + dlon21 * dlon21 + dele21 * dele21

            if (l21 > l10 && l21 > l20) {
                return calculateElevationBasedOnTwoPoints(lat, lon, lat1, lon1, ele1, lat2, lon2,
                        ele2)
            } else if (l20 > l10 && l20 > l21) {
                return calculateElevationBasedOnTwoPoints(lat, lon, lat0, lon0, ele0, lat2, lon2,
                        ele2)
            } else {
                return calculateElevationBasedOnTwoPoints(lat, lon, lat0, lon0, ele0, lat1, lon1,
                        ele1)
            }

        } else {
            val d = a * lat0 + b * lon0 + c * ele0
            val ele = (d - a * lat - b * lon) / c
            return round2(ele)
        }
    }

    fun calculateElevationBasedOnPointList(lat: Double, lon: Double, pointList: PointList): Double {
        // See http://math.stackexchange.com/a/1930758/140512 for the
        // explanation
        val size = pointList.size()
        if (size == 0) {
            throw IllegalArgumentException("At least one point is required in the pointList.")
        } else if (size == 1) {
            return pointList.getEle(0)
        } else if (size == 2) {
            return calculateElevationBasedOnTwoPoints(lat, lon, pointList.getLat(0),
                    pointList.getLon(0), pointList.getEle(0), pointList.getLat(1),
                    pointList.getLon(1), pointList.getEle(1))
        } else if (size == 3) {
            return calculateElevationBasedOnThreePoints(lat, lon, pointList.getLat(0),
                    pointList.getLon(0), pointList.getEle(0), pointList.getLat(1),
                    pointList.getLon(1), pointList.getEle(1), pointList.getLat(2),
                    pointList.getLon(2), pointList.getEle(2))
        } else {
            val vs = DoubleArray(size)
            val eles = DoubleArray(size)
            var v = 0.0
            for (index in 0 until size) {
                val lati = pointList.getLat(index)
                val loni = pointList.getLon(index)
                val dlati = lati - lat
                val dloni = loni - lon
                val l2 = (dlati * dlati + dloni * dloni)
                eles[index] = pointList.getEle(index)
                if (l2 < EPSILON2) {
                    return eles[index]
                }
                vs[index] = 1 / l2
                v += vs[index]
            }

            var ele = 0.0
            for (index in 0 until size) {
                ele += eles[index] * vs[index] / v
            }
            return round2(ele)
        }
    }

    companion object {
        const val EPSILON = 0.00001
        const val EPSILON2 = EPSILON * EPSILON
    }
}
