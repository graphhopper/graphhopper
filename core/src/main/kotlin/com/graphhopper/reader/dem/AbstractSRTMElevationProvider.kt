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
import com.graphhopper.storage.DataAccess
import com.graphhopper.util.Downloader
import com.graphhopper.util.Helper
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * Common functionality used when working with SRTM hgt data.
 *
 * @author Robin Boldt
 */
abstract class AbstractSRTMElevationProvider(
    baseUrl: String,
    cacheDir: String,
    downloaderName: String,
    private val minLatProvider: Int,
    private val maxLatProvider: Int,
    private val defaultWidth: Int
) : TileBasedElevationProvider(cacheDir) {

    private val widthByteIndex = 0
    private val degree = 1

    // use a map as an array is not quite useful if we want to hold only parts of the world
    private val cacheData = GHIntObjectHashMap<HeightTile>()
    private val precision = 1e7
    private val invPrecision = 1 / precision

    init {
        this.baseUrl = baseUrl
        downloader = Downloader().setTimeout(10000)
    }

    // use int key instead of string for lower memory usage
    internal fun calcIntKey(lat: Double, lon: Double): Int {
        // we could use LinearKeyAlgo but this is simpler as we only need integer precision:
        return (down(lat) + 90) * 1000 + down(lon) + 180
    }

    override fun release() {
        cacheData.clear()
        val dir = this.dir
        if (dir != null) {
            // for memory mapped type we remove temporary files
            if (autoRemoveTemporary)
                dir.clear()
            else
                dir.close()
        }
    }

    internal fun down(value: Double): Int {
        val intVal = value.toInt()
        if (value >= 0 || intVal - value < invPrecision)
            return intVal
        return intVal - 1
    }

    override fun getEle(lat: Double, lon: Double): Double {
        // Return fast, if there is no data available
        // See https://www2.jpl.nasa.gov/srtm/faq.html
        if (lat >= maxLatProvider || lat <= minLatProvider)
            return 0.0

        @Suppress("NAME_SHADOWING") val lat = (lat * precision).toInt() / precision
        @Suppress("NAME_SHADOWING") val lon = (lon * precision).toInt() / precision
        val intKey = calcIntKey(lat, lon)
        var demProvider: HeightTile? = cacheData.get(intKey)
        if (demProvider == null) {
            if (!cacheDir!!.exists())
                cacheDir!!.mkdirs()

            val minLat = down(lat)
            val minLon = down(lon)

            val fileName = getFileName(lat, lon)
            if (fileName == null || (Helper.isEmpty(baseUrl) && !File(fileName).exists()))
                return 0.0

            val heights = getDirectory().create("dem$intKey")
            var loadExisting = false
            try {
                loadExisting = heights.loadExisting()
            } catch (ex: Exception) {
                logger.warn("cannot load dem" + intKey + ", error:" + ex.message)
            }

            if (!loadExisting) {
                try {
                    updateHeightsFromFile(lat, lon, heights)
                } catch (ex: FileNotFoundException) {
                    demProvider = HeightTile(minLat, minLon, defaultWidth, defaultWidth, precision, degree, degree)
                    cacheData.put(intKey, demProvider)
                    demProvider.setHeights(heights)
                    demProvider.setSeaLevel(true)
                    // use small size on disc and in-memory
                    heights.create(10)
                            .flush()
                    return 0.0
                }
            }

            var width = (Math.sqrt(heights.getHeader(widthByteIndex).toDouble()) + 0.5).toInt()
            if (width == 0)
                width = defaultWidth

            demProvider = HeightTile(minLat, minLon, width, width, precision, degree, degree)
            cacheData.put(intKey, demProvider)
            demProvider.setInterpolate(interpolate)
            demProvider.setHeights(heights)
        }

        if (demProvider.isSeaLevel())
            return 0.0

        return demProvider.getHeight(lat, lon)
    }

    @Throws(FileNotFoundException::class)
    private fun updateHeightsFromFile(lat: Double, lon: Double, heights: DataAccess) {
        try {
            var zippedURL = baseUrl + getDownloadURL(lat, lon)
            val zipFile = File(cacheDir, File(zippedURL).name)
            if (!zipFile.exists()) downloadToFile(zipFile, zippedURL)
            val bytes = readFile(zipFile)
            heights.create(bytes.size.toLong())
            var bytePos = 0
            while (bytePos < bytes.size) {
                var value = toShort(bytes, bytePos)
                if (value < -1000 || value > 12000)
                    value = Short.MIN_VALUE

                heights.setShort(bytePos.toLong(), value)
                bytePos += 2
            }
            heights.setHeader(widthByteIndex, bytes.size / 2)
            heights.flush()

        } catch (ex: FileNotFoundException) {
            logger.warn("File not found " + heights + " for the coordinates " + lat + "," + lon)
            throw ex
        } catch (ex: Exception) {
            throw RuntimeException("There was an issue with " + heights + " looking up the coordinates " + lat + "," + lon, ex)
        }
    }

    // we need big endianess to read the SRTM files
    internal fun toShort(b: ByteArray, offset: Int): Short {
        return ((b[offset].toInt() and 0xFF shl 8) or (b[offset + 1].toInt() and 0xFF)).toShort()
    }

    @Throws(InterruptedException::class, IOException::class)
    private fun downloadToFile(file: File, zippedURLIn: String) {
        var zippedURL = zippedURLIn
        for (i in 0 until 3) {
            try {
                downloader!!.downloadFile(zippedURL, file.absolutePath)
                break
            } catch (ex: SocketTimeoutException) {
                // just try again after a little nap
                Thread.sleep(2000)
            } catch (ex: FileNotFoundException) {
                if (zippedURL.contains(".hgt.zip")) {
                    zippedURL = zippedURL.replace(".hgt.zip", "hgt.zip")
                } else {
                    throw ex
                }
            }
        }
    }

    protected fun getPaddedLonString(lonInt: Int): String {
        val lon = Math.abs(lonInt)
        var lonString = if (lon < 100) "0" else ""
        if (lon < 10)
            lonString += "0"
        lonString += lon
        return lonString
    }

    protected fun getPaddedLatString(latInt: Int): String {
        val lat = Math.abs(latInt)
        var latString = if (lat < 10) "0" else ""
        latString += lat
        return latString
    }

    @Throws(IOException::class)
    protected abstract fun readFile(file: File): ByteArray

    /**
     * Return the local file name without file ending, has to be lower case, because DataAccess only supports lower case names.
     */
    protected abstract fun getFileName(lat: Double, lon: Double): String?

    /**
     * Returns the complete URL to download the file
     */
    protected abstract fun getDownloadURL(lat: Double, lon: Double): String
}
