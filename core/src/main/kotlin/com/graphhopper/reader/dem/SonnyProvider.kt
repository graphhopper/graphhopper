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
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException

/**
 * Sonny's LiDAR Digital Terrain Models contains elevation data for Europe with 1 arc second (~30m) accuracy.
 * The description is available at https://sonny.4lima.de/. Unfortunately the data is provided on a Google Drive
 * https://drive.google.com/drive/folders/0BxphPoRgwhnoWkRoTFhMbTM3RDA?resourcekey=0-wRe5bWl96pwvQ9tAfI9cQg
 * Therefore, the data is not available via a direct URL and you have to download it manually. After downloading,
 * the data has to be unzipped and placed in the cache directory. The cache directory is expected to contain DTM
 * data files with the naming convention like "N49E011.hgt" for the area around 49°N and 11°E.
 *
 * Please note that the data cannot be used for public hosting or redistribution due to the terms of use of the data. See
 * https://github.com/graphhopper/graphhopper/issues/2823
 *
 * @author ratrun
 */
open class SonnyProvider @JvmOverloads constructor(cacheDir: String = "") : AbstractSRTMElevationProvider(
        "https://drive.google.com/drive/folders/0BxphPoRgwhnoWkRoTFhMbTM3RDA?resourcekey=0-wRe5bWl96pwvQ9tAfI9cQg/", // This base URL cannot be used, as the data is not available via a direct URL
        if (cacheDir.isEmpty()) "/tmp/sonny" else cacheDir,
        "GraphHopper SonnyReader",
        -56,
        90,
        3601
) {

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

    override fun getFileName(lat: Double, lon: Double): String? {
        var str = ""

        val minLat = Math.abs(down(lat))
        val minLon = Math.abs(down(lon))

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
        return getFileName(lat, lon) + ".hgt"
    }

    override fun toString(): String = "sonny"

    companion object {
        @JvmStatic
        @Throws(IOException::class)
        fun main(args: Array<String>) {
            val provider = SonnyProvider()
            // 338
            println(provider.getEle(49.949784, 11.57517))
            // 462
            println(provider.getEle(49.968668, 11.575127))
            // 462
            println(provider.getEle(49.968682, 11.574842))
            // 982
            println(provider.getEle(47.468668, 14.575127))
            // 1094
            println(provider.getEle(47.467753, 14.573911))
            // 1925
            println(provider.getEle(46.468835, 12.578777))
            // 834
            println(provider.getEle(48.469123, 9.576393))
            // Out of area
            try {
                println(provider.getEle(37.5969196, 23.0706507))
            } catch (e: Exception) {
                println("Error: Out of area! " + e.message)
            }
        }
    }
}
