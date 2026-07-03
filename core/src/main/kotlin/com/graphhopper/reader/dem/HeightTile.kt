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

import com.graphhopper.storage.DataAccess
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import java.io.IOException
import javax.imageio.ImageIO

/**
 * One rectangle of height data from Shuttle Radar Topography Mission.
 *
 * @author Peter Karich
 */
open class HeightTile(
    private val minLat: Int,
    private val minLon: Int,
    private val width: Int,
    private val height: Int,
    precision: Double,
    private val horizontalDegree: Int,
    private val verticalDegree: Int
) {
    private val lowerBound: Double = -1 / precision
    private val lonHigherBound: Double = horizontalDegree + 1 / precision
    private val latHigherBound: Double = verticalDegree + 1 / precision
    private var heights: DataAccess? = null
    private var interpolate: Boolean = false

    private val MIN_ELEVATION_METERS = -12_000.0
    private val MAX_ELEVATION_METERS = 9_000.0

    fun setInterpolate(interpolate: Boolean): HeightTile {
        this.interpolate = interpolate
        return this
    }

    fun isSeaLevel(): Boolean = heights!!.getHeader(0) == 1

    fun setSeaLevel(b: Boolean): HeightTile {
        heights!!.setHeader(0, if (b) 1 else 0)
        return this
    }

    @JvmName("setHeights")
    internal fun setHeights(da: DataAccess) {
        this.heights = da
    }

    private fun getHeightSample(x: Int, y: Int): Short {
        // always keep in mind factor 2 because of short value
        return heights!!.getShort(2L * (y.toLong() * width + x))
    }

    private fun isValidElevation(elevation: Double): Boolean {
        return elevation > MIN_ELEVATION_METERS && elevation < MAX_ELEVATION_METERS
    }

    private fun linearInterpolate(a: Double, b: Double, f: Double): Double {
        // interpolate between a and b but if either are invalid, return the other
        return if (!isValidElevation(a)) b else if (!isValidElevation(b)) a else (a + (b - a) * f)
    }

    fun getHeight(lat: Double, lon: Double): Double {
        val deltaLat = lat - minLat
        val deltaLon = lon - minLon
        if (deltaLat > latHigherBound || deltaLat < lowerBound)
            throw IllegalStateException("latitude not in boundary of this file:" + lat + "," + lon + ", this:" + this.toString())
        if (deltaLon > lonHigherBound || deltaLon < lowerBound)
            throw IllegalStateException("longitude not in boundary of this file:" + lat + "," + lon + ", this:" + this.toString())

        val elevation: Double
        if (interpolate) {
            val x = (width - 1) * deltaLon / horizontalDegree
            val y = (height - 1) * (1 - deltaLat / verticalDegree)
            val left = x.toInt()
            val top = y.toInt()
            val right = left + 1
            val bottom = top + 1

            val w00 = getHeightSample(left, top).toDouble()
            val w01 = getHeightSample(left, bottom).toDouble()
            val w10 = getHeightSample(right, top).toDouble()
            val w11 = getHeightSample(right, bottom).toDouble()

            val topEle = linearInterpolate(w00, w10, x - left)
            val bottomEle = linearInterpolate(w01, w11, x - left)
            elevation = linearInterpolate(topEle, bottomEle, y - top)
        } else {
            // first row in the file is the northernmost one
            // http://gis.stackexchange.com/a/43756/9006
            var x = (width / horizontalDegree * deltaLon).toInt()
            // different fallback methods for lat and lon as we have different rounding (lon -> positive, lat -> negative)
            if (x >= width)
                x = width - 1
            var y = height - 1 - (height / verticalDegree * deltaLat).toInt()
            if (y < 0)
                y = 0

            elevation = getHeightSample(x, y).toDouble()
        }
        return if (isValidElevation(elevation)) elevation else Double.NaN
    }

    @Throws(IOException::class)
    fun toImage(imageFile: String) {
        ImageIO.write(makeARGB(), "PNG", File(imageFile))
    }

    protected fun makeARGB(): BufferedImage {
        val argbImage = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g = argbImage.graphics
        val len = (width * height).toLong()
        var i = 0
        while (i < len) {
            val lonSimilar = i % width
            // no need for width - y as coordinate system for Graphics is already this way
            val latSimilar = i / height
            var green = Math.abs(heights!!.getShort((i * 2).toLong()).toInt())
            if (green == 0) {
                g.color = Color(255, 0, 0, 255)
            } else {
                var red = 0
                while (green > 255) {
                    green = green / 10
                    red += 50
                }
                if (red > 255)
                    red = 255
                g.color = Color(red, green, 122, 255)
            }
            g.drawLine(lonSimilar, latSimilar, lonSimilar, latSimilar)
            i++
        }
        g.dispose()
        return argbImage
    }

    fun getImageFromArray(pixels: IntArray, width: Int, height: Int): BufferedImage {
        val tmpImage = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB_PRE)
        tmpImage.setRGB(0, 0, width, height, pixels, 0, width)
        return tmpImage
    }

    override fun toString(): String = "$minLat,$minLon"
}
