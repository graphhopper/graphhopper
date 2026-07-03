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

import com.graphhopper.util.Helper
import org.apache.xmlgraphics.image.codec.tiff.TIFFDecodeParam
import org.apache.xmlgraphics.image.codec.tiff.TIFFImageDecoder
import org.apache.xmlgraphics.image.codec.util.SeekableStream
import java.awt.image.Raster
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipInputStream

/**
 * Elevation data from CGIAR project http://srtm.csi.cgiar.org/ 'PROCESSED SRTM DATA VERSION 4.1'.
 * Every file covers a region of 5x5 degree. License granted for all people using GraphHopper:
 * http://graphhopper.com/public/license/CGIAR.txt
 *
 * Every zip contains readme.txt with the necessary information e.g.:
 * 1. All GeoTiffs with 6000 x 6000 pixels.
 *
 * @author NopMap
 * @author Peter Karich
 */
open class CGIARProvider @JvmOverloads constructor(cacheDir: String = "") : AbstractTiffElevationProvider(
        // Alternative URLs for the CGIAR data can be found in #346
        "https://srtm.csi.cgiar.org/wp-content/uploads/files/srtm_5x5/TIFF/",
        if (cacheDir.isEmpty()) "/tmp/cgiar" else cacheDir,
        6000, 6000,
        5, 5
) {

    override fun readFile(file: File, tifName: String): Raster {
        var ss: SeekableStream? = null
        try {
            val stream = FileInputStream(file)
            val zis = ZipInputStream(stream)
            // find tif file in zip
            var entry = zis.nextEntry
            while (entry != null && entry.name != tifName) {
                entry = zis.nextEntry
            }

            ss = SeekableStream.wrapInputStream(zis, true)
            val imageDecoder = TIFFImageDecoder(ss, TIFFDecodeParam())
            return imageDecoder.decodeAsRaster()
        } catch (e: Exception) {
            throw RuntimeException("Can't decode $tifName", e)
        } finally {
            if (ss != null)
                Helper.close(ss)
        }
    }

    @JvmName("down")
    internal fun down(value: Double): Int {
        // floor to nearest multiple of LAT_DEGREE
        return Math.floor(value / LAT_DEGREE).toInt() * LAT_DEGREE
    }

    override fun isOutsideSupportedArea(lat: Double, lon: Double): Boolean {
        return lat >= 60 || lat <= -56
    }

    override fun getFileName(lat: Double, lon: Double): String {
        val minLat = down(lat)
        val minLon = down(lon)
        val lonInt = 1 + (minLon + 180) / LAT_DEGREE
        val latInt = (60 - minLat) / LAT_DEGREE

        // replace String.format as it seems to be slow
        // String.format("srtm_%02d_%02d", lonInt, latInt);
        var str = "srtm_"
        str += if (lonInt < 10) "0" else ""
        str += lonInt
        str += if (latInt < 10) "_0" else "_"
        str += latInt

        return str
    }

    override fun getMinLatForTile(lat: Double): Int = down(lat)

    override fun getMinLonForTile(lon: Double): Int = down(lon)

    override fun getDownloadURL(lat: Double, lon: Double): String {
        return baseUrl + "/" + getFileName(lat, lon) + ".zip"
    }

    override fun getFileNameOfLocalFile(lat: Double, lon: Double): String {
        return getDownloadURL(lat, lon)
    }

    override fun toString(): String = "cgiar"

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val provider = CGIARProvider()

            println(provider.getEle(46.0, -20.0))

            // 337.0
            println(provider.getEle(49.949784, 11.57517))
            // 466.0
            println(provider.getEle(49.968668, 11.575127))
            // 455.0
            println(provider.getEle(49.968682, 11.574842))

            // 3134
            println(provider.getEle(-22.532854, -65.110474))

            // 120
            println(provider.getEle(38.065392, -87.099609))

            // 1615
            println(provider.getEle(40.0, -105.2277023))
            println(provider.getEle(39.99999999, -105.2277023))
            println(provider.getEle(39.9999999, -105.2277023))
            // 1616
            println(provider.getEle(39.999999, -105.2277023))

            // 0
            println(provider.getEle(29.840644, -42.890625))

            // 841
            println(provider.getEle(48.469123, 9.576393))
        }
    }
}
