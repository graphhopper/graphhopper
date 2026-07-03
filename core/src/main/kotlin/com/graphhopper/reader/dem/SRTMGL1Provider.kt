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

import com.graphhopper.util.Helper.close
import com.graphhopper.util.Helper.toLowerCase
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException

/**
 * SRTMGL1 contains elevation data for most of the world with 1 arc second (~30m) accuracy.
 * We use the mirror of OpenTopography, as the official SRTMGL1 download requires authentication.
 * http://opentopo.sdsc.edu/raster?opentopoID=OTSRTM.082015.4326.1
 *
 * TODO: In addition, NSF requires oral acknowledgment during all news media interviews.
 * TODO: This is weird, we have to check if really want to use OpenTopography then, see: http://www.opentopography.org/citations
 *
 * When using this data we have to acknowledge:
 * This material is based on data services provided by the OpenTopography Facility with support from the
 * National Science Foundation under NSF Award Numbers 1226353 &amp; 1225810
 * National Geospatial-Intelligence Agency (NGA) and the National Aeronautics and Space Administration (NASA), 2013,
 * SRTMGL1: NASA Shuttle Radar Topography Mission Global 1 arc second V003. [Version]. NASA EOSDIS Land Processes DAAC,
 * USGS Earth Resources Observation and Science (EROS) Center, Sioux Falls, South Dakota (https://lpdaac.usgs.gov),
 * accessed 11 29, 2017, at https://doi.org/10.5067/measures/srtm/srtmgl1.003
 *
 * Detailed information can be found here: https://lpdaac.usgs.gov/sites/default/files/public/measures/docs/NASA_SRTM_V3.pdf
 *
 * @author Robin Boldt
 */
open class SRTMGL1Provider @JvmOverloads constructor(cacheDir: String = "") : AbstractSRTMElevationProvider(
        "https://cloud.sdsc.edu/v1/AUTH_opentopography/Raster/SRTM_GL1/SRTM_GL1_srtm/",
        if (cacheDir.isEmpty()) "/tmp/srtmgl1" else cacheDir,
        "GraphHopper SRTMReader",
        -56,
        60,
        3601
) {
    private val latDegree = 1
    private val lonDegree = 1

    @Throws(IOException::class)
    override fun readFile(file: File): ByteArray {
        val stream = FileInputStream(file)
        val buff = BufferedInputStream(stream)
        val os = ByteArrayOutputStream()
        val buffer = ByteArray(0xFFFF)
        var len: Int
        while (buff.read(buffer).also { len = it } > 0) {
            os.write(buffer, 0, len)
        }
        os.flush()
        close(buff)
        return os.toByteArray()
    }

    @JvmName("getMinLatForTile")
    internal fun getMinLatForTile(lat: Double): Int {
        return (Math.floor((90 + lat) / latDegree) * latDegree).toInt() - 90
    }

    @JvmName("getMinLonForTile")
    internal fun getMinLonForTile(lon: Double): Int {
        return (Math.floor((180 + lon) / lonDegree) * lonDegree).toInt() - 180
    }

    override fun getFileName(lat: Double, lon: Double): String? {
        val lonInt = getMinLonForTile(lon)
        val latInt = getMinLatForTile(lat)
        return toLowerCase(getNorthString(latInt) + getPaddedLatString(latInt) + getEastString(lonInt) + getPaddedLonString(lonInt))
    }

    override fun getDownloadURL(lat: Double, lon: Double): String {
        val lonInt = getMinLonForTile(lon)
        val latInt = getMinLatForTile(lat)
        val north = getNorthString(latInt)
        var dir: String
        if (north == "N") {
            dir = "North/"
            if (lat >= 30)
                dir += "North_30_60/"
            else
                dir += "North_0_29/"
        } else {
            dir = "South/"
        }

        return dir + north + getPaddedLatString(latInt) + getEastString(lonInt) + getPaddedLonString(lonInt) + ".hgt"
    }

    private fun getNorthString(lat: Int): String {
        if (lat < 0) {
            return "S"
        }
        return "N"
    }

    private fun getEastString(lon: Int): String {
        if (lon < 0) {
            return "W"
        }
        return "E"
    }

    override fun toString(): String = "srtmgl1"

    companion object {
        @JvmStatic
        @Throws(IOException::class)
        fun main(args: Array<String>) {
            val provider = SRTMGL1Provider()
            // 338
            println(provider.getEle(49.949784, 11.57517))
            // 468
            println(provider.getEle(49.968668, 11.575127))
            // 467
            println(provider.getEle(49.968682, 11.574842))
            // 3110
            println(provider.getEle(-22.532854, -65.110474))
            // 120
            println(provider.getEle(38.065392, -87.099609))
            // 1617
            println(provider.getEle(40.0, -105.2277023))
            println(provider.getEle(39.99999999, -105.2277023))
            println(provider.getEle(39.9999999, -105.2277023))
            println(provider.getEle(39.999999, -105.2277023))
            // 1015
            println(provider.getEle(47.468668, 14.575127))
            // 1107
            println(provider.getEle(47.467753, 14.573911))
            // 1930
            println(provider.getEle(46.468835, 12.578777))
            // 844
            println(provider.getEle(48.469123, 9.576393))
        }
    }
}
