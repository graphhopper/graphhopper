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
import org.apache.xmlgraphics.image.codec.tiff.TIFFDecodeParam
import org.apache.xmlgraphics.image.codec.tiff.TIFFImageDecoder
import org.apache.xmlgraphics.image.codec.util.SeekableStream
import java.awt.image.Raster
import java.io.File
import java.io.FileInputStream

/**
 * Elevation data from Global Multi-resolution Terrain Elevation Data 2010 (GMTED2010).
 * The data provides 7.5 arc seconds resolution (~250 m) global elevation data. The data is available between latitudes
 * of 84°N and 70°S. The data is available as .tiff and the we are using the mean elevation per cell (other options are
 * median, min, max, etc.).
 *
 * More information can be found here: https://topotools.cr.usgs.gov/gmted_viewer/
 *
 * When using the data we have to acknowledge the source: "Data available from the U.S. Geological Survey.",
 * more information can be found here: https://lta.cr.usgs.gov/citation
 *
 * The gdalinfo of one GeoTiff is:
 * Driver: GTiff/GeoTIFF
 * Files: 50N000E_20101117_gmted_mea075.tif
 * Size is 14400, 9600
 * Coordinate System is:
 * GEOGCS["WGS 84",
 * DATUM["WGS_1984",
 * SPHEROID["WGS 84",6378137,298.257223563,
 * AUTHORITY["EPSG","7030"]],
 * AUTHORITY["EPSG","6326"]],
 * PRIMEM["Greenwich",0],
 * UNIT["degree",0.0174532925199433],
 * AUTHORITY["EPSG","4326"]]
 * Origin = (-0.000138888888889,69.999861111111116)
 * Pixel Size = (0.002083333333333,-0.002083333333333)
 * Metadata:
 * AREA_OR_POINT=Area
 * Image Structure Metadata:
 * INTERLEAVE=BAND
 * Corner Coordinates:
 * Upper Left  (  -0.0001389,  69.9998611) (  0d 0' 0.50"W, 69d59'59.50"N)
 * Lower Left  (  -0.0001389,  49.9998611) (  0d 0' 0.50"W, 49d59'59.50"N)
 * Upper Right (  29.9998611,  69.9998611) ( 29d59'59.50"E, 69d59'59.50"N)
 * Lower Right (  29.9998611,  49.9998611) ( 29d59'59.50"E, 49d59'59.50"N)
 * Center      (  14.9998611,  59.9998611) ( 14d59'59.50"E, 59d59'59.50"N)
 * Band 1 Block=14400x1 Type=Int16, ColorInterp=Gray
 * Min=-209.000 Max=2437.000
 * Minimum=-209.000, Maximum=2437.000, Mean=149.447, StdDev=239.767
 * NoData Value=-32768
 * Metadata:
 * STATISTICS_EXCLUDEDVALUES=-32768
 * STATISTICS_MAXIMUM=2437
 * STATISTICS_MEAN=149.44718774595
 * STATISTICS_MINIMUM=-209
 * STATISTICS_STDDEV=239.767158482
 *
 * @author Robin Boldt
 */
open class GMTEDProvider @JvmOverloads constructor(cacheDir: String = "") : AbstractTiffElevationProvider(
        // for alternatives see #346
        "https://edcintl.cr.usgs.gov/downloads/sciweb1/shared/topo/downloads/GMTED/Global_tiles_GMTED/075darcsec/mea/",
        if (cacheDir.isEmpty()) "/tmp/gmted" else cacheDir,
        14400, 9600,
        20, 30
) {
    private val FILE_NAME_END = "_20101117_gmted_mea075"

    override fun readFile(file: File, tifName: String): Raster {
        var ss: SeekableStream? = null
        try {
            val stream = FileInputStream(file)
            ss = SeekableStream.wrapInputStream(stream, true)
            val imageDecoder = TIFFImageDecoder(ss, TIFFDecodeParam())
            return imageDecoder.decodeAsRaster()
        } catch (e: Exception) {
            throw RuntimeException("Can't decode " + file.name, e)
        } finally {
            if (ss != null)
                close(ss)
        }
    }

    override fun getMinLatForTile(lat: Double): Int {
        return (Math.floor((90 + lat) / LAT_DEGREE) * LAT_DEGREE).toInt() - 90
    }

    override fun getMinLonForTile(lon: Double): Int {
        return (Math.floor((180 + lon) / LON_DEGREE) * LON_DEGREE).toInt() - 180
    }

    private fun getLonString(lonInt: Int): String {
        val lon = Math.abs(lonInt)
        var lonString = if (lon < 100) "0" else ""
        if (lon < 10)
            lonString += "0"
        lonString += lon
        return lonString
    }

    private fun getLatString(latInt: Int): String {
        val lat = Math.abs(latInt)
        var latString = if (lat < 10) "0" else ""
        latString += lat
        return latString
    }

    override fun isOutsideSupportedArea(lat: Double, lon: Double): Boolean {
        return lat > 84 || lat < -70
    }

    override fun getFileName(lat: Double, lon: Double): String {
        val lonInt = getMinLonForTile(lon)
        val latInt = getMinLatForTile(lat)
        return toLowerCase(getLatString(latInt) + getNorthString(latInt) + getLonString(lonInt) + getEastString(lonInt) + FILE_NAME_END)
    }

    override fun getDownloadURL(lat: Double, lon: Double): String {
        val lonInt = getMinLonForTile(lon)
        val latInt = getMinLatForTile(lat)
        val east = getEastString(lonInt)
        val lonString = getLonString(lonInt)
        return baseUrl + "/" + east + lonString + "/" + getLatString(latInt) + getNorthString(latInt) + lonString + east + FILE_NAME_END + ".tif"
    }

    override fun getFileNameOfLocalFile(lat: Double, lon: Double): String {
        return getFileName(lat, lon) + ".tif"
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

    override fun toString(): String = "gmted"

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val provider = GMTEDProvider()

            println(provider.getEle(46.0, -20.0))

            // 337.0 (339)
            println(provider.getEle(49.949784, 11.57517))
            // 453.0 (438)
            println(provider.getEle(49.968668, 11.575127))
            // 447.0 (432)
            println(provider.getEle(49.968682, 11.574842))

            // 3131 (3169)
            println(provider.getEle(-22.532854, -65.110474))

            // 123 (124)
            println(provider.getEle(38.065392, -87.099609))

            // 1615 (1615)
            println(provider.getEle(40.0, -105.2277023))
            // (1618)
            println(provider.getEle(39.99999999, -105.2277023))
            println(provider.getEle(39.9999999, -105.2277023))
            // 1617 (1618)
            println(provider.getEle(39.999999, -105.2277023))

            // 1046 (1070)
            println(provider.getEle(47.468668, 14.575127))
            // 1113 (1115)
            println(provider.getEle(47.467753, 14.573911))

            // 1946 (1990)
            println(provider.getEle(46.468835, 12.578777))

            // 845 (841)
            println(provider.getEle(48.469123, 9.576393))

            // 1113 vs new: (1115)
            provider.setInterpolate(true)
            println(provider.getEle(47.467753, 14.573911))

            // 0
            println(provider.getEle(29.840644, -42.890625))
        }
    }
}
