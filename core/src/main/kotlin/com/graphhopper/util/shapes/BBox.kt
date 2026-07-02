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
package com.graphhopper.util.shapes

import com.graphhopper.util.Helper
import com.graphhopper.util.NumHelper
import com.graphhopper.util.PointList
import org.locationtech.jts.algorithm.RectangleLineIntersector
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.Envelope

/**
 * A simple bounding box defined as follows: minLon, maxLon followed by minLat which is south(!) and
 * maxLat. Equally to EX_GeographicBoundingBox in the ISO 19115 standard see
 * http://osgeo-org.1560.n6.nabble.com/Boundingbox-issue-for-discussion-td3875533.html
 *
 * Nice German overview:
 * http://www.geoinf.uni-jena.de/fileadmin/Geoinformatik/Lehre/Diplomarbeiten/DA_Andres.pdf
 *
 * @author Peter Karich
 */
class BBox(
    // longitude (theta) = x, latitude (phi) = y, elevation = z
    @JvmField var minLon: Double,
    @JvmField var maxLon: Double,
    @JvmField var minLat: Double,
    @JvmField var maxLat: Double,
    @JvmField var minEle: Double,
    @JvmField var maxEle: Double,
    private val elevation: Boolean
) : Shape, Cloneable {

    constructor(coords: DoubleArray) : this(coords[0], coords[2], coords[1], coords[3])

    constructor(minLon: Double, maxLon: Double, minLat: Double, maxLat: Double) :
            this(minLon, maxLon, minLat, maxLat, Double.NaN, Double.NaN, false)

    constructor(minLon: Double, maxLon: Double, minLat: Double, maxLat: Double, minEle: Double, maxEle: Double) :
            this(minLon, maxLon, minLat, maxLat, minEle, maxEle, true)

    fun hasElevation(): Boolean = elevation

    fun update(lat: Double, lon: Double) {
        if (lat > maxLat) maxLat = lat
        if (lat < minLat) minLat = lat
        if (lon > maxLon) maxLon = lon
        if (lon < minLon) minLon = lon
    }

    fun update(lat: Double, lon: Double, elev: Double) {
        if (elevation) {
            if (elev > maxEle) maxEle = elev
            if (elev < minEle) minEle = elev
        } else {
            throw IllegalStateException("No BBox with elevation to update")
        }
        update(lat, lon)
    }

    /**
     * Calculates the intersecting BBox between this and the specified BBox
     *
     * @return the intersecting BBox or null if not intersecting
     */
    fun calculateIntersection(bBox: BBox): BBox? {
        if (!this.intersects(bBox)) return null

        return BBox(
            maxOf(this.minLon, bBox.minLon),
            minOf(this.maxLon, bBox.maxLon),
            maxOf(this.minLat, bBox.minLat),
            minOf(this.maxLat, bBox.maxLat)
        )
    }

    public override fun clone(): BBox = BBox(minLon, maxLon, minLat, maxLat, minEle, maxEle, elevation)

    override fun intersects(pointList: PointList): Boolean =
        intersects(RectangleLineIntersector(toEnvelope(this)), pointList)

    /**
     * This method calculates if this BBox intersects with the specified BBox
     */
    fun intersects(minLon: Double, maxLon: Double, minLat: Double, maxLat: Double): Boolean =
        this.minLon < maxLon && this.minLat < maxLat && minLon < this.maxLon && minLat < this.maxLat

    /**
     * This method calculates if this BBox intersects with the specified BBox
     */
    fun intersects(o: BBox): Boolean =
        this.minLon < o.maxLon && this.minLat < o.maxLat && o.minLon < this.maxLon && o.minLat < this.maxLat

    override fun contains(lat: Double, lon: Double): Boolean =
        lat <= maxLat && lat >= minLat && lon <= maxLon && lon >= minLon

    fun contains(b: BBox): Boolean =
        maxLat >= b.maxLat && minLat <= b.minLat && maxLon >= b.maxLon && minLon <= b.minLon

    override fun toString(): String {
        var str = "$minLon,$maxLon,$minLat,$maxLat"
        if (elevation) str += ",$minEle,$maxEle"
        return str
    }

    fun toLessPrecisionString(): String =
        "${minLon.toFloat()},${maxLon.toFloat()},${minLat.toFloat()},${maxLat.toFloat()}"

    override val bounds: BBox
        get() = this

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        // the hard cast (possible ClassCastException for a non-BBox argument) matches the
        // original Java behavior
        val b = other as BBox
        // equals within a very small range
        return NumHelper.equalsEps(minLat, b.minLat) && NumHelper.equalsEps(maxLat, b.maxLat)
                && NumHelper.equalsEps(minLon, b.minLon) && NumHelper.equalsEps(maxLon, b.maxLon)
    }

    override fun hashCode(): Int {
        var hash = 3
        hash = 17 * hash + (minLon.toBits() xor (minLon.toBits() ushr 32)).toInt()
        hash = 17 * hash + (maxLon.toBits() xor (maxLon.toBits() ushr 32)).toInt()
        hash = 17 * hash + (minLat.toBits() xor (minLat.toBits() ushr 32)).toInt()
        hash = 17 * hash + (maxLat.toBits() xor (maxLat.toBits() ushr 32)).toInt()
        return hash
    }

    fun isValid(): Boolean {
        // second longitude should be bigger than the first
        if (minLon >= maxLon) return false

        // second latitude should be smaller than the first
        if (minLat >= maxLat) return false

        if (elevation) {
            // equal elevation is okay
            if (minEle > maxEle) return false

            if (maxEle.compareTo(-Double.MAX_VALUE) == 0 || minEle.compareTo(Double.MAX_VALUE) == 0)
                return false
        }

        return maxLat.compareTo(-Double.MAX_VALUE) != 0
                && minLat.compareTo(Double.MAX_VALUE) != 0
                && maxLon.compareTo(-Double.MAX_VALUE) != 0
                && minLon.compareTo(Double.MAX_VALUE) != 0
    }

    /**
     * @return array containing this bounding box. Attention: GeoJson is lon,lat! If 3D is gets even
     * worse: lon,lat,ele
     */
    fun toGeoJson(): List<Double> {
        val list = ArrayList<Double>(4)
        list.add(Helper.round6(minLon))
        list.add(Helper.round6(minLat))
        // hmh
        if (elevation) list.add(Helper.round2(minEle))

        list.add(Helper.round6(maxLon))
        list.add(Helper.round6(maxLat))
        if (elevation) list.add(Helper.round2(maxEle))

        return list
    }

    companion object {
        /**
         * Prefills BBox with minimum values so that it can increase.
         */
        @JvmStatic
        fun createInverse(elevation: Boolean): BBox = if (elevation) {
            BBox(
                Double.MAX_VALUE, -Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE,
                Double.MAX_VALUE, -Double.MAX_VALUE, true
            )
        } else {
            BBox(
                Double.MAX_VALUE, -Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE,
                Double.NaN, Double.NaN, false
            )
        }

        @JvmStatic
        fun intersects(intersector: RectangleLineIntersector, pointList: PointList): Boolean {
            val len = pointList.size()
            if (len == 0) throw IllegalArgumentException("PointList must not be empty")

            var coords = Coordinate(pointList.getLon(0), pointList.getLat(0))
            if (len == 1) return intersector.intersects(coords, coords)

            for (pointIndex in 1 until len) {
                val nextCoords = Coordinate(pointList.getLon(pointIndex), pointList.getLat(pointIndex))
                if (intersector.intersects(coords, nextCoords)) return true
                coords = nextCoords
            }
            return false
        }

        @JvmStatic
        fun fromEnvelope(envelope: Envelope): BBox =
            BBox(envelope.minX, envelope.maxX, envelope.minY, envelope.maxY)

        @JvmStatic
        fun toEnvelope(bbox: BBox): Envelope = Envelope(bbox.minLon, bbox.maxLon, bbox.minLat, bbox.maxLat)

        /**
         * This method creates a BBox out of a string in format lat1,lon1,lat2,lon2
         */
        @JvmStatic
        fun parseTwoPoints(objectAsString: String): BBox {
            // dropLastWhile mirrors java's String.split which discards trailing empty strings
            val splittedObject = objectAsString.split(",").dropLastWhile { it.isEmpty() }

            if (splittedObject.size != 4)
                throw IllegalArgumentException("BBox should have 4 parts but was $objectAsString")

            val minLat = splittedObject[0].toDouble()
            val minLon = splittedObject[1].toDouble()

            val maxLat = splittedObject[2].toDouble()
            val maxLon = splittedObject[3].toDouble()
            return fromPoints(minLat, minLon, maxLat, maxLon)
        }

        @JvmStatic
        fun fromPoints(lat1: Double, lon1: Double, lat2: Double, lon2: Double): BBox {
            // explicit swaps (not minOf/maxOf) to keep the original NaN pass-through behavior
            var minLat = lat1
            var maxLat = lat2
            if (minLat > maxLat) {
                val tmp = minLat
                minLat = maxLat
                maxLat = tmp
            }
            var minLon = lon1
            var maxLon = lon2
            if (minLon > maxLon) {
                val tmp = minLon
                minLon = maxLon
                maxLon = tmp
            }
            return BBox(minLon, maxLon, minLat, maxLat)
        }

        /**
         * This method creates a BBox out of a string in format lon1,lon2,lat1,lat2
         */
        @JvmStatic
        fun parseBBoxString(objectAsString: String): BBox {
            // dropLastWhile mirrors java's String.split which discards trailing empty strings
            val splittedObject = objectAsString.split(",").dropLastWhile { it.isEmpty() }

            if (splittedObject.size != 4)
                throw IllegalArgumentException("BBox should have 4 parts but was $objectAsString")

            val minLon = splittedObject[0].toDouble()
            val maxLon = splittedObject[1].toDouble()

            val minLat = splittedObject[2].toDouble()
            val maxLat = splittedObject[3].toDouble()

            return BBox(minLon, maxLon, minLat, maxLat)
        }
    }
}
