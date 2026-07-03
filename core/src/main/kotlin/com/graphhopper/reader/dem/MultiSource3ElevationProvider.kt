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
 * The MultiSource3ElevationProvider mixes different elevation providers to provide the best available elevation data
 * for the whole world.
 *
 * @author ratrun
 */
open class MultiSource3ElevationProvider(
    // Usually a high resolution provider in the SRTM area
    private val srtmProvider: TileBasedElevationProvider,
    // The fallback provider that provides elevation data globally
    private val globalProvider: TileBasedElevationProvider,
    // The provider that provides elevation data for Europe
    private val sonnyProvider: TileBasedElevationProvider
) : TileBasedElevationProvider("_ignored_") {

    constructor() : this(CGIARProvider(), GMTEDProvider(), SonnyProvider())

    constructor(cacheDir: String) : this(CGIARProvider(cacheDir), GMTEDProvider(cacheDir), SonnyProvider(cacheDir))

    override fun init(): ElevationProvider {
        srtmProvider.init()
        globalProvider.init()
        sonnyProvider.init()
        return this
    }

    override fun getEle(lat: Double, lon: Double): Double {
        try {
            return sonnyProvider.getEle(lat, lon)
        } catch (ex: Exception) {
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
    }

    /**
     * For the MultiSource3ElevationProvider you have to specify the base URL separated by a ';'.
     * The first for cgiar, the second for gmted, the third for sonny
     */
    override fun setBaseURL(baseUrl: String?): MultiSource3ElevationProvider {
        val urls = baseUrl!!.split(";".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        if (urls.size != 3) {
            throw IllegalArgumentException("The base url must consist of three urls separated by a ';'. The first for cgiar, the second for gmted")
        }
        srtmProvider.setBaseURL(urls[0])
        globalProvider.setBaseURL(urls[1])
        sonnyProvider.setBaseURL(urls[2])
        return this
    }

    override fun setDAType(daType: DAType): MultiSource3ElevationProvider {
        srtmProvider.setDAType(daType)
        globalProvider.setDAType(daType)
        sonnyProvider.setDAType(daType)
        return this
    }

    override fun setInterpolate(interpolate: Boolean): MultiSource3ElevationProvider {
        srtmProvider.setInterpolate(interpolate)
        globalProvider.setInterpolate(interpolate)
        sonnyProvider.setInterpolate(interpolate)
        return this
    }

    override fun canInterpolate(): Boolean {
        return srtmProvider.canInterpolate() && globalProvider.canInterpolate() && sonnyProvider.canInterpolate()
    }

    override fun release() {
        srtmProvider.release()
        globalProvider.release()
        sonnyProvider.release()
    }

    override fun setAutoRemoveTemporaryFiles(autoRemoveTemporary: Boolean): MultiSource3ElevationProvider {
        srtmProvider.setAutoRemoveTemporaryFiles(autoRemoveTemporary)
        globalProvider.setAutoRemoveTemporaryFiles(autoRemoveTemporary)
        sonnyProvider.setAutoRemoveTemporaryFiles(autoRemoveTemporary)
        return this
    }

    override fun toString(): String = "multi3"
}
