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

import com.graphhopper.storage.DAType

/**
 * The MultiSourceElevationProvider mixes different elevation providers to provide the best available elevation data
 * for a certain area.
 *
 * @author Robin Boldt
 */
open class MultiSourceElevationProvider(
    // Usually a high resolution provider in the SRTM area
    private val srtmProvider: TileBasedElevationProvider,
    // The fallback provider that provides elevation data globally
    private val globalProvider: TileBasedElevationProvider
) : TileBasedElevationProvider("_ignored_") {

    constructor() : this(CGIARProvider(), GMTEDProvider())

    constructor(cacheDir: String) : this(CGIARProvider(cacheDir), GMTEDProvider(cacheDir))

    override fun init(): ElevationProvider {
        srtmProvider.init()
        globalProvider.init()
        return this
    }

    override fun getEle(lat: Double, lon: Double): Double {
        // Sometimes the cgiar data north of 59.999 equals 0
        if (lat < 59.999 && lat > -56) {
            var ele = srtmProvider.getEle(lat, lon)
            if (java.lang.Double.isNaN(ele)) {
                // If the SRTM data is not available, use the global provider
                ele = globalProvider.getEle(lat, lon)
            }
            return ele
        }
        return globalProvider.getEle(lat, lon)
    }

    /**
     * For the MultiSourceElevationProvider you have to specify the base URL separated by a ';'.
     * The first for cgiar, the second for gmted.
     */
    override fun setBaseURL(baseUrl: String?): MultiSourceElevationProvider {
        val urls = baseUrl!!.split(";".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        if (urls.size != 2) {
            throw IllegalArgumentException("The base url must consist of two urls separated by a ';'. The first for cgiar, the second for gmted")
        }
        srtmProvider.setBaseURL(urls[0])
        globalProvider.setBaseURL(urls[1])
        return this
    }

    override fun setDAType(daType: DAType): MultiSourceElevationProvider {
        srtmProvider.setDAType(daType)
        globalProvider.setDAType(daType)
        return this
    }

    override fun setInterpolate(interpolate: Boolean): MultiSourceElevationProvider {
        srtmProvider.setInterpolate(interpolate)
        globalProvider.setInterpolate(interpolate)
        return this
    }

    override fun canInterpolate(): Boolean {
        return srtmProvider.canInterpolate() && globalProvider.canInterpolate()
    }

    override fun release() {
        srtmProvider.release()
        globalProvider.release()
    }

    override fun setAutoRemoveTemporaryFiles(autoRemoveTemporary: Boolean): MultiSourceElevationProvider {
        srtmProvider.setAutoRemoveTemporaryFiles(autoRemoveTemporary)
        globalProvider.setAutoRemoveTemporaryFiles(autoRemoveTemporary)
        return this
    }

    override fun toString(): String = "multi"
}
