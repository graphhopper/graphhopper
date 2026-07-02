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
package com.graphhopper.routing.ev

import com.graphhopper.util.Helper

/**
 * This enum defines the road surface of an edge like unpaved or asphalt. If not tagged the value will be MISSING, which
 * the default and best surface value (as surface is currently often not tagged). All unknown surface tags will get
 * OTHER (the worst surface).
 */
enum class Surface {
    // Order is important to make ordinal roughly comparable
    MISSING,
    PAVED, ASPHALT, CONCRETE, PAVING_STONES, COBBLESTONE,
    UNPAVED, COMPACTED, FINE_GRAVEL, GRAVEL, GROUND, DIRT, GRASS, SAND, WOOD,
    OTHER;

    override fun toString(): String = Helper.toLowerCase(super.toString())

    companion object {
        const val KEY = "surface"

        private val SURFACE_MAP: MutableMap<String, Surface> = HashMap()

        init {
            for (surface in entries) {
                if (surface == MISSING || surface == OTHER)
                    continue
                SURFACE_MAP[surface.toString()] = surface
            }
            SURFACE_MAP["metal"] = PAVED
            SURFACE_MAP["sett"] = COBBLESTONE
            SURFACE_MAP["unhewn_cobblestone"] = COBBLESTONE
            SURFACE_MAP["earth"] = DIRT
            SURFACE_MAP["pebblestone"] = GRAVEL
            SURFACE_MAP["grass_paver"] = GRASS
        }

        @JvmStatic
        fun create(): EnumEncodedValue<Surface> = EnumEncodedValue(KEY, Surface::class.java)

        @JvmStatic
        fun find(name: String?): Surface {
            if (Helper.isEmpty(name))
                return MISSING

            var surfaceName = name!!
            val colonIndex = surfaceName.indexOf(":")
            if (colonIndex != -1) {
                surfaceName = surfaceName.substring(0, colonIndex)
            }

            return SURFACE_MAP.getOrDefault(surfaceName, OTHER)
        }
    }
}
