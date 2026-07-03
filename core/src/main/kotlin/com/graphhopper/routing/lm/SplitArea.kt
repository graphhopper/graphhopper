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

package com.graphhopper.routing.lm

import com.graphhopper.routing.util.AreaIndex
import com.graphhopper.util.JsonFeature
import org.locationtech.jts.geom.Polygon
import org.locationtech.jts.geom.util.PolygonExtracter

class SplitArea(override val borders: List<Polygon>) : AreaIndex.Area {

    companion object {
        @JvmStatic
        fun fromJsonFeature(j: JsonFeature): SplitArea {
            val borders = ArrayList<Polygon>()
            for (i in 0 until j.geometry.numGeometries) {
                val geometry = j.geometry.getGeometryN(i)
                if (geometry is Polygon)
                    PolygonExtracter.getPolygons(geometry, borders)
                else
                    throw IllegalArgumentException("GeoJson features used to create split areas must be of type 'Polygon', but was: " + geometry.javaClass.simpleName)
            }
            return SplitArea(borders)
        }
    }
}
