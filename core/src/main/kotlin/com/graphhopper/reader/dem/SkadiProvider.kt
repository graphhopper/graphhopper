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
import java.util.zip.GZIPInputStream

/**
 * Skadi contains elevation data for the entire world with 1 arc second (~30m) accuracy in SRTM format stitched
 * together from many sources (https://github.com/tilezen/joerd/blob/master/docs/data-sources.md).
 *
 * We use the hosted AWS Open Data mirror (https://registry.opendata.aws/terrain-tiles/) by default but you can
 * change to any mirror by updating the base URL.
 *
 * See https://github.com/tilezen/joerd/blob/master/docs/attribution.md for required attribution of any project
 * using this data.
 *
 * Detailed information can be found here: https://github.com/tilezen/joerd
 */
open class SkadiProvider @JvmOverloads constructor(cacheDir: String = "") : AbstractSRTMElevationProvider(
        "https://elevation-tiles-prod.s3.amazonaws.com/skadi/",
        if (cacheDir.isEmpty()) "/tmp/srtm" else cacheDir,
        "GraphHopper SRTMReader",
        -90,
        90,
        3601
) {

    @Throws(IOException::class)
    override fun readFile(file: File): ByteArray {
        val stream = FileInputStream(file)
        val gzis = GZIPInputStream(stream, 8 * 1024)
        val buff = BufferedInputStream(gzis, 16 * 1024)
        val os = ByteArrayOutputStream(64 * 1024)
        buff.transferTo(os)
        close(buff)
        return os.toByteArray()
    }

    private fun getLatString(lat: Double): String {
        val minLat = Math.floor(lat).toInt()
        return (if (minLat < 0) "S" else "N") + getPaddedLatString(minLat)
    }

    private fun getLonString(lon: Double): String {
        val minLon = Math.floor(lon).toInt()
        return (if (minLon < 0) "W" else "E") + getPaddedLonString(minLon)
    }

    override fun getFileName(lat: Double, lon: Double): String? {
        val latStr = getLatString(lat)
        val lonStr = getLonString(lon)
        return toLowerCase(latStr + lonStr)
    }

    override fun getDownloadURL(lat: Double, lon: Double): String {
        val latStr = getLatString(lat)
        val lonStr = getLonString(lon)

        return "$latStr/$latStr$lonStr.hgt.gz"
    }

    override fun toString(): String = "skadi"

    companion object {
        @JvmStatic
        @Throws(IOException::class)
        fun main(args: Array<String>) {
            val provider = SkadiProvider()
            // 338
            println(provider.getEle(49.949784, 11.57517))
            // 468
            println(provider.getEle(49.968668, 11.575127))
            // 467
            println(provider.getEle(49.968682, 11.574842))
            // 3110
            println(provider.getEle(-22.532854, -65.110474))
            // 115
            println(provider.getEle(38.065392, -87.099609))
            // 1612
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
