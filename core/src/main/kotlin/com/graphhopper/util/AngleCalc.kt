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
 * Calculates the angle of a turn, defined by three points. The fast atan2 method is from Jim Shima,
 * 1999, http://www.dspguru.com/dsp/tricks/fixed-point-atan2-with-self-normalization
 * and stands under public domain.
 *
 * @author Johannes Pelzer
 * @author Peter Karich
 */
class AngleCalc {
    /**
     * Return orientation of line relative to east.
     *
     * @param exact If false the atan gets calculated faster, but it might contain small errors
     * @return Orientation in interval -pi to +pi where 0 is east and the "bottom" arc is negative
     */
    @JvmOverloads
    fun calcOrientation(lat1: Double, lon1: Double, lat2: Double, lon2: Double, exact: Boolean = true): Double {
        val shrinkFactor = Math.cos(Math.toRadians((lat1 + lat2) / 2))
        return if (exact)
            Math.atan2(lat2 - lat1, shrinkFactor * (lon2 - lon1))
        else
            atan2(lat2 - lat1, shrinkFactor * (lon2 - lon1))
    }

    /**
     * convert north based clockwise azimuth (0, 360) into x-axis/east based angle (-Pi, Pi)
     */
    fun convertAzimuth2xaxisAngle(azimuth: Double): Double {
        if (java.lang.Double.compare(azimuth, 360.0) > 0 || java.lang.Double.compare(azimuth, 0.0) < 0) {
            throw IllegalArgumentException("Azimuth " + azimuth + " must be in (0, 360)")
        }
        var angleXY = PI_2 - azimuth / 180.0 * Math.PI
        if (angleXY < -Math.PI)
            angleXY += 2 * Math.PI
        if (angleXY > Math.PI)
            angleXY -= 2 * Math.PI
        return angleXY
    }

    /**
     * Change the representation of an orientation, so the difference to the given baseOrientation
     * will be smaller or equal to PI (180 degree). This is achieved by adding or subtracting a
     * 2*PI, so the direction of the orientation will not be changed
     */
    fun alignOrientation(baseOrientation: Double, orientation: Double): Double {
        val resultOrientation: Double
        if (baseOrientation >= 0) {
            if (orientation < -Math.PI + baseOrientation)
                resultOrientation = orientation + 2 * Math.PI
            else
                resultOrientation = orientation
        } else if (orientation > +Math.PI + baseOrientation)
            resultOrientation = orientation - 2 * Math.PI
        else
            resultOrientation = orientation
        return resultOrientation
    }

    /**
     * Calculate the azimuth in degree for a line given by two coordinates. Direction in 'degree'
     * where 0 is north, 90 is east, 180 is south and 270 is west.
     */
    fun calcAzimuth(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        var orientation = Math.PI / 2 - calcOrientation(lat1, lon1, lat2, lon2)
        if (orientation < 0)
            orientation += 2 * Math.PI

        return Math.toDegrees(Helper.round4(orientation)) % 360
    }

    fun azimuth2compassPoint(azimuth: Double): String {
        val cp: String
        val slice = 360.0 / 16
        if (azimuth < slice) {
            cp = "N"
        } else if (azimuth < slice * 3) {
            cp = "NE"
        } else if (azimuth < slice * 5) {
            cp = "E"
        } else if (azimuth < slice * 7) {
            cp = "SE"
        } else if (azimuth < slice * 9) {
            cp = "S"
        } else if (azimuth < slice * 11) {
            cp = "SW"
        } else if (azimuth < slice * 13) {
            cp = "W"
        } else if (azimuth < slice * 15) {
            cp = "NW"
        } else {
            cp = "N"
        }
        return cp
    }

    /**
     * @return true if the given vectors follow a clockwise order abc, bca or cab,
     * false if the order is counter-clockwise cba, acb or bac, e.g. this returns true:
     * a   b
     * | /
     * 0 - c
     */
    fun isClockwise(aX: Double, aY: Double, bX: Double, bY: Double, cX: Double, cY: Double): Boolean {
        // simply compare angles between a,b and b,c
        val angleDiff = (cX - aX) * (bY - aY) - (cY - aY) * (bX - aX)
        return angleDiff < 0
    }

    companion object {
        @JvmField
        val ANGLE_CALC = AngleCalc()

        private val PI_4 = Math.PI / 4.0
        private val PI_2 = Math.PI / 2.0
        private val PI3_4 = 3.0 * Math.PI / 4.0

        @JvmStatic
        @JvmName("atan2")
        internal fun atan2(y: Double, x: Double): Double {
            // kludge to prevent 0/0 condition
            val absY = Math.abs(y) + 1e-10
            val r: Double
            var angle: Double
            if (x < 0.0) {
                r = (x + absY) / (absY - x)
                angle = PI3_4
            } else {
                r = (x - absY) / (x + absY)
                angle = PI_4
            }

            angle += (0.1963 * r * r - 0.9817) * r
            if (y < 0.0)
            // negate if in quad III or IV
                return -angle
            return angle
        }
    }
}
