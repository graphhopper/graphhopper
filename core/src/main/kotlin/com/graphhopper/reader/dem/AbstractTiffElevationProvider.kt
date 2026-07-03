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

import com.graphhopper.util.Downloader
import java.awt.image.Raster
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import javax.net.ssl.SSLException

/**
 * Provides basic methods that are usually used in an ElevationProvider that reads tiff files.
 *
 * @author Robin Boldt
 */
abstract class AbstractTiffElevationProvider(
    baseUrl: String,
    cacheDir: String,
    private val width: Int,
    private val height: Int,
    latDegree: Int,
    lonDegree: Int
) : TileBasedElevationProvider(cacheDir) {

    private val cacheData = HashMap<String, HeightTile>()

    internal val precision = 1e7

    // Degrees of latitude covered by this tile
    @JvmField
    internal val LAT_DEGREE = latDegree

    // Degrees of longitude covered by this tile
    @JvmField
    internal val LON_DEGREE = lonDegree

    init {
        this.baseUrl = baseUrl
        this.downloader = Downloader().setTimeout(10000)
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

    /**
     * Return true if the coordinates are outside of the supported area
     */
    protected abstract fun isOutsideSupportedArea(lat: Double, lon: Double): Boolean

    /**
     * The smallest lat that is still in the HeightTile
     */
    protected abstract fun getMinLatForTile(lat: Double): Int

    /**
     * The smallest lon that is still in the HeightTile
     */
    protected abstract fun getMinLonForTile(lon: Double): Int

    /**
     * Specify the name of the file after downloading
     */
    protected abstract fun getFileNameOfLocalFile(lat: Double, lon: Double): String

    /**
     * Return the local file name without file ending, has to be lower case, because DataAccess only supports lower case names.
     */
    protected abstract fun getFileName(lat: Double, lon: Double): String

    /**
     * Returns the complete URL to download the file
     */
    protected abstract fun getDownloadURL(lat: Double, lon: Double): String

    override fun getEle(lat: Double, lon: Double): Double {
        // Return fast, if there is no data available
        if (isOutsideSupportedArea(lat, lon))
            return 0.0

        @Suppress("NAME_SHADOWING") val lat = (lat * precision).toInt() / precision
        @Suppress("NAME_SHADOWING") val lon = (lon * precision).toInt() / precision
        val name = getFileName(lat, lon)
        var demProvider = cacheData[name]
        if (demProvider == null) {
            if (!cacheDir!!.exists())
                cacheDir!!.mkdirs()

            val minLat = getMinLatForTile(lat)
            val minLon = getMinLonForTile(lon)
            // less restrictive against boundary checking
            demProvider = HeightTile(minLat, minLon, width, height, LON_DEGREE * precision, LON_DEGREE, LAT_DEGREE)
            demProvider.setInterpolate(interpolate)

            cacheData[name] = demProvider
            val heights = getDirectory().create("$name.gh")
            demProvider.setHeights(heights)
            var loadExisting = false
            try {
                loadExisting = heights.loadExisting()
            } catch (ex: Exception) {
                logger.warn("cannot load " + name + ", error: " + ex.message)
            }

            if (!loadExisting) {
                val zipFile = File(cacheDir, File(getFileNameOfLocalFile(lat, lon)).name)
                if (!zipFile.exists())
                    try {
                        val zippedURL = getDownloadURL(lat, lon)
                        downloadToFile(zipFile, zippedURL)
                    } catch (ex: SSLException) {
                        throw IllegalStateException("SSL problem with elevation provider " + javaClass.simpleName, ex)
                    } catch (ex: IOException) {
                        demProvider.setSeaLevel(true)
                        // use small size on disc and in-memory
                        heights.create(10).flush()
                        return 0.0
                    }

                // short == 2 bytes
                heights.create(2L * width * height)

                val raster = readFile(zipFile, "$name.tif")
                fillDataAccessWithElevationData(raster, heights, width)

            } // loadExisting
        }

        if (demProvider.isSeaLevel())
            return 0.0

        return demProvider.getHeight(lat, lon)
    }

    protected abstract fun readFile(file: File, tifName: String): Raster

    /**
     * Download a file at the provided url and save it as the given downloadFile if the downloadFile does not exist.
     */
    @Throws(IOException::class)
    private fun downloadToFile(downloadFile: File, url: String) {
        if (!downloadFile.exists()) {
            val max = 3
            for (trial in 0 until max) {
                try {
                    downloader!!.downloadFile(url, downloadFile.absolutePath)
                    return
                } catch (ex: SocketTimeoutException) {
                    if (trial >= max - 1)
                        throw RuntimeException(ex)
                    try {
                        Thread.sleep(sleep)
                    } catch (ignored: InterruptedException) {
                    }
                }
            }
        }
    }

    private fun fillDataAccessWithElevationData(raster: Raster, heights: com.graphhopper.storage.DataAccess, dataAccessWidth: Int) {
        val height = raster.height
        val width = raster.width
        var x = 0
        var y = 0
        try {
            y = 0
            while (y < height) {
                x = 0
                while (x < width) {
                    var value = raster.getPixel(x, y, null as IntArray?)[0].toShort()
                    if (value < -1000 || value > 12000)
                        value = Short.MIN_VALUE

                    heights.setShort(2 * (y.toLong() * dataAccessWidth + x), value)
                    x++
                }
                y++
            }
            heights.flush()
        } catch (ex: Exception) {
            throw RuntimeException("Problem at x:$x, y:$y", ex)
        }
    }
}
