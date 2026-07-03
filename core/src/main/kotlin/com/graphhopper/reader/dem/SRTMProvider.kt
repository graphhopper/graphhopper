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

import com.graphhopper.coll.GHIntObjectHashMap
import com.graphhopper.util.Helper
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStreamReader
import java.util.zip.ZipInputStream

/**
 * Elevation data from NASA (SRTM).
 *
 * Important information about SRTM: the coordinates of the lower-left corner of tile N40W118 are 40
 * degrees north latitude and 118 degrees west longitude. To be more exact, these coordinates refer
 * to the geometric center of the lower left sample, which in the case of SRTM3 data will be about
 * 90 meters in extent.
 *
 * @author Peter Karich
 */
open class SRTMProvider @JvmOverloads constructor(cacheDir: String = "") : AbstractSRTMElevationProvider(
        "https://srtm.kurviger.de/SRTM3/",
        if (cacheDir.isEmpty()) "/tmp/srtm" else cacheDir,
        "GraphHopper SRTMReader",
        -56,
        60,
        1201
) {
    private val areas = GHIntObjectHashMap<String>()

    /**
     * The URLs are a bit ugly and so we need to find out which area name a certain lat,lon
     * coordinate has.
     */
    override fun init(): ElevationProvider {
        super.init()
        try {
            val strs = arrayOf("Africa", "Australia", "Eurasia", "Islands", "North_America", "South_America")
            for (str in strs) {
                val stream = javaClass.getResourceAsStream(str + "_names.txt")
                for (line in Helper.readFile(InputStreamReader(stream, Helper.UTF_CS))) {
                    var lat = Integer.parseInt(line.substring(1, 3))
                    if (line.substring(0, 1)[0] == 'S')
                        lat = -lat

                    var lon = Integer.parseInt(line.substring(4, 7))
                    if (line.substring(3, 4)[0] == 'W')
                        lon = -lon

                    val intKey = calcIntKey(lat.toDouble(), lon.toDouble())
                    val key = areas.put(intKey, str)
                    if (key != null)
                        throw IllegalStateException("do not overwrite existing! key " + intKey + " " + key + " vs. " + str)
                }
            }
            return this
        } catch (ex: Exception) {
            throw IllegalStateException("Cannot load area names from classpath", ex)
        }
    }

    override fun toString(): String = "srtm"

    @Throws(IOException::class)
    override fun readFile(file: File): ByteArray {
        val stream = FileInputStream(file)
        val buff = BufferedInputStream(stream, 8 * 1024)
        ZipInputStream(buff).use { zis ->
            val entry = zis.nextEntry
                    ?: throw RuntimeException("No entry found in zip file $file")
            val bufferSize = Math.max(entry.size, (64 * 1024).toLong()).toInt()
            val os = ByteArrayOutputStream(bufferSize)
            zis.transferTo(os)
            return os.toByteArray()
        }
    }

    override fun getFileName(lat: Double, lon: Double): String? {
        val intKey = calcIntKey(lat, lon)
        var str: String = areas.get(intKey) ?: return null

        val minLat = Math.abs(down(lat))
        val minLon = Math.abs(down(lon))
        str += "/"
        if (lat >= 0)
            str += "N"
        else
            str += "S"

        if (minLat < 10)
            str += "0"
        str += minLat

        if (lon >= 0)
            str += "E"
        else
            str += "W"

        if (minLon < 10)
            str += "0"
        if (minLon < 100)
            str += "0"
        str += minLon
        return str
    }

    override fun getDownloadURL(lat: Double, lon: Double): String {
        return getFileName(lat, lon) + ".hgt.zip"
    }

    companion object {
        @JvmStatic
        @Throws(IOException::class)
        fun main(args: Array<String>) {
            val provider = SRTMProvider()
            // 337
            println(provider.getEle(49.949784, 11.57517))
            // 466
            println(provider.getEle(49.968668, 11.575127))
            // 466
            println(provider.getEle(49.968682, 11.574842))
            // 3100
            println(provider.getEle(-22.532854, -65.110474))
            // 122
            println(provider.getEle(38.065392, -87.099609))
            // 1617
            println(provider.getEle(40.0, -105.2277023))
            println(provider.getEle(39.99999999, -105.2277023))
            println(provider.getEle(39.9999999, -105.2277023))
            println(provider.getEle(39.999999, -105.2277023))
            // 1046
            println(provider.getEle(47.468668, 14.575127))
            // 1113
            println(provider.getEle(47.467753, 14.573911))
            // 1946
            println(provider.getEle(46.468835, 12.578777))
            // 845
            println(provider.getEle(48.469123, 9.576393))
        }
    }
}
