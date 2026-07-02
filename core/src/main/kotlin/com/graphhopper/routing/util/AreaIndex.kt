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
package com.graphhopper.routing.util

import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.Envelope
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.Point
import org.locationtech.jts.geom.Polygon
import org.locationtech.jts.geom.prep.PreparedGeometry
import org.locationtech.jts.geom.prep.PreparedGeometryFactory
import org.locationtech.jts.index.strtree.STRtree

open class AreaIndex<T : AreaIndex.Area>(areas: List<T>) {

    interface Area {
        val borders: List<Polygon>
    }

    private val gf: GeometryFactory = GeometryFactory()
    private val index: STRtree = STRtree()

    init {
        val pgf = PreparedGeometryFactory()
        for (area in areas) {
            for (border in area.borders) {
                val indexedCustomArea = IndexedCustomArea(area, pgf.create(border))
                index.insert(border.envelopeInternal, indexedCustomArea)
            }
        }
        index.build()
    }

    open fun query(lat: Double, lon: Double): List<T> {
        val searchEnv = Envelope(lon, lon, lat, lat)
        @Suppress("UNCHECKED_CAST")
        val result = index.query(searchEnv) as List<IndexedCustomArea<T>>
        val point = gf.createPoint(Coordinate(lon, lat))
        return result
            .filter { it.intersects(point) }
            .map { it.area }
    }

    private class IndexedCustomArea<T : Area>(
        val area: T,
        val preparedGeometry: PreparedGeometry
    ) {
        fun intersects(point: Point): Boolean = preparedGeometry.intersects(point)
    }
}
