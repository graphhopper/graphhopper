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

import com.graphhopper.reader.ReaderNode

/**
 * @author Peter Karich
 */
interface ElevationProvider {

    fun init(): ElevationProvider

    /**
     * @return returns the height in meters or Double.NaN if invalid
     */
    fun getEle(lat: Double, lon: Double): Double

    /**
     * @param node Node to read
     * @return returns the height in meters or Double.NaN if invalid
     */
    fun getEle(node: ReaderNode): Double = getEle(node.lat, node.lon)

    /**
     * Returns true if bilinear interpolation is enabled.
     */
    fun canInterpolate(): Boolean

    /**
     * Release resources.
     */
    fun release()

    companion object {
        @JvmField
        val NOOP: ElevationProvider = object : ElevationProvider {
            override fun init(): ElevationProvider = this

            override fun getEle(lat: Double, lon: Double): Double = Double.NaN

            override fun release() {
            }

            override fun canInterpolate(): Boolean = false
        }
    }
}
