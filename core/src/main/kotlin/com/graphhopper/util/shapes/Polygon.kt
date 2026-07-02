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

import com.graphhopper.util.PointList
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.Envelope
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.impl.PackedCoordinateSequence
import org.locationtech.jts.geom.prep.PreparedGeometry
import org.locationtech.jts.geom.prep.PreparedPolygon

/**
 * This class represents a polygon that is defined by a set of points.
 * Every point i is connected to point i-1 and i+1.
 *
 * @author Robin Boldt
 */
class Polygon(prepPolygon: PreparedPolygon) : Shape {
    @JvmField
    val prepPolygon: PreparedGeometry = prepPolygon

    @JvmField
    val rectangle: Boolean = prepPolygon.geometry.isRectangle

    @JvmField
    val envelope: Envelope = prepPolygon.geometry.envelopeInternal

    @JvmField
    val bbox: BBox = BBox.fromEnvelope(envelope)

    constructor(lats: DoubleArray, lons: DoubleArray) : this(toPreparedPolygon(lats, lons))

    override fun intersects(pointList: PointList): Boolean =
        prepPolygon.intersects(pointList.getCachedLineString(false))

    /**
     * Does the point in polygon check.
     *
     * @param lat Latitude of the point to be checked
     * @param lon Longitude of the point to be checked
     * @return true if point is inside polygon
     */
    override fun contains(lat: Double, lon: Double): Boolean =
        prepPolygon.contains(factory.createPoint(Coordinate(lon, lat)))

    override val bounds: BBox
        get() = bbox

    val minLat: Double
        get() = envelope.minY

    val minLon: Double
        get() = envelope.minX

    val maxLat: Double
        get() = envelope.maxY

    val maxLon: Double
        get() = envelope.maxX

    fun isRectangle(): Boolean = rectangle

    override fun toString(): String =
        "polygon (${prepPolygon.geometry.numPoints} points,${prepPolygon.geometry.numGeometries} geometries)"

    companion object {
        private val factory = GeometryFactory()

        @JvmStatic
        fun create(polygon: org.locationtech.jts.geom.Polygon): Polygon = Polygon(PreparedPolygon(polygon))

        private fun toPreparedPolygon(lats: DoubleArray, lons: DoubleArray): PreparedPolygon {
            if (lats.size != lons.size)
                throw IllegalArgumentException("Points must be of equal length but was ${lats.size} vs. ${lons.size}")

            if (lats.isEmpty())
                throw IllegalArgumentException("Points must not be empty")

            val coordinates = arrayOfNulls<Coordinate>(lats.size + 1)
            for (i in lats.indices) {
                coordinates[i] = Coordinate(lons[i], lats[i])
            }
            coordinates[lats.size] = coordinates[0]
            return PreparedPolygon(factory.createPolygon(PackedCoordinateSequence.Double(coordinates, 2)))
        }
    }
}
