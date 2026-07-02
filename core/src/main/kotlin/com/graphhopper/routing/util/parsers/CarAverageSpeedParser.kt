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
package com.graphhopper.routing.util.parsers

import com.graphhopper.reader.ReaderWay
import com.graphhopper.routing.ev.DecimalEncodedValue
import com.graphhopper.routing.ev.EdgeIntAccess
import com.graphhopper.routing.ev.EncodedValueLookup
import com.graphhopper.routing.ev.MaxSpeed
import com.graphhopper.routing.ev.VehicleSpeed
import com.graphhopper.routing.util.FerrySpeedCalculator
import com.graphhopper.util.Helper
import kotlin.math.max

open class CarAverageSpeedParser(speedEnc: DecimalEncodedValue) : AbstractAverageSpeedParser(speedEnc), TagParser {

    @JvmField
    protected val trackTypeSpeedMap: MutableMap<String?, Int> = HashMap()

    @JvmField
    protected val badSurfaceSpeedMap: MutableSet<String> = HashSet()

    // This value determines the maximal possible on roads with bad surfaces
    private val badSurfaceSpeed: Int

    /**
     * A map which associates string to speed. Get some impression:
     * http://www.itoworld.com/map/124#fullscreen
     * http://wiki.openstreetmap.org/wiki/OSM_tags_for_routing/Maxspeed
     */
    @JvmField
    protected val defaultSpeedMap: MutableMap<String, Int> = HashMap()

    constructor(lookup: EncodedValueLookup) : this(lookup.getDecimalEncodedValue(VehicleSpeed.key("car")))

    init {
        badSurfaceSpeedMap.add("cobblestone")
        badSurfaceSpeedMap.add("unhewn_cobblestone")
        badSurfaceSpeedMap.add("sett")
        badSurfaceSpeedMap.add("grass_paver")
        badSurfaceSpeedMap.add("gravel")
        badSurfaceSpeedMap.add("fine_gravel")
        badSurfaceSpeedMap.add("pebblestone")
        badSurfaceSpeedMap.add("sand")
        badSurfaceSpeedMap.add("paving_stones")
        badSurfaceSpeedMap.add("dirt")
        badSurfaceSpeedMap.add("earth")
        badSurfaceSpeedMap.add("ground")
        badSurfaceSpeedMap.add("wood")
        badSurfaceSpeedMap.add("grass")
        badSurfaceSpeedMap.add("unpaved")
        badSurfaceSpeedMap.add("compacted")

        // autobahn
        defaultSpeedMap["motorway"] = 100
        defaultSpeedMap["motorway_link"] = 70
        // bundesstraße
        defaultSpeedMap["trunk"] = 70
        defaultSpeedMap["trunk_link"] = 65
        // linking bigger town
        defaultSpeedMap["primary"] = 65
        defaultSpeedMap["primary_link"] = 60
        // linking towns + villages
        defaultSpeedMap["secondary"] = 60
        defaultSpeedMap["secondary_link"] = 50
        // streets without middle line separation
        defaultSpeedMap["tertiary"] = 50
        defaultSpeedMap["tertiary_link"] = 40
        defaultSpeedMap["unclassified"] = 30
        defaultSpeedMap["residential"] = 30
        defaultSpeedMap["living_street"] = 6
        defaultSpeedMap["pedestrian"] = 6
        defaultSpeedMap["service"] = 20
        // unknown road
        defaultSpeedMap["road"] = 20
        // forestry stuff
        defaultSpeedMap["track"] = 15

        trackTypeSpeedMap["grade1"] = 20 // paved
        trackTypeSpeedMap["grade2"] = 15 // now unpaved - gravel mixed with ...
        trackTypeSpeedMap["grade3"] = 10 // ... hard and soft materials
        trackTypeSpeedMap[null] = defaultSpeedMap["track"]!!

        // limit speed on bad surfaces to 30 km/h
        badSurfaceSpeed = 30
    }

    protected open fun getSpeed(way: ReaderWay): Double {
        val highwayValue = way.getTag("highway", "")
        // even inaccessible edges get a speed assigned
        var speed = defaultSpeedMap[highwayValue] ?: 10

        if (highwayValue == "track") {
            val tt = way.getTag("tracktype")
            if (!Helper.isEmpty(tt)) {
                val tInt = trackTypeSpeedMap[tt]
                if (tInt != null)
                    speed = tInt
            }
        }

        return speed.toDouble()
    }

    override fun handleWayTags(edgeId: Int, edgeIntAccess: EdgeIntAccess, way: ReaderWay) {
        if (FerrySpeedCalculator.isFerry(way))
            return

        // get assumed speed from highway type
        var speed = getSpeed(way)
        speed = applyBadSurfaceSpeed(way, speed)

        setSpeed(false, edgeId, edgeIntAccess, applyMaxSpeed(way, speed, false))
        setSpeed(true, edgeId, edgeIntAccess, applyMaxSpeed(way, speed, true))
    }

    /**
     * @param way   needed to retrieve tags
     * @param speed speed guessed e.g. from the road type or other tags
     * @return The assumed speed.
     */
    protected open fun applyMaxSpeed(way: ReaderWay, speed: Double, bwd: Boolean): Double {
        val maxSpeed = OSMMaxSpeedParser.parseMaxSpeed(way, bwd)
        return if (maxSpeed != MaxSpeed.MAXSPEED_MISSING) max(1.0, maxSpeed * 0.9) else speed
    }

    /**
     * @param way   needed to retrieve tags
     * @param speed speed guessed e.g. from the road type or other tags
     * @return The assumed speed
     */
    protected open fun applyBadSurfaceSpeed(way: ReaderWay, speed: Double): Double {
        // limit speed if bad surface
        if (badSurfaceSpeed > 0 && speed > badSurfaceSpeed) {
            var surface = way.getTag("surface", "")
            val colonIndex = surface.indexOf(":")
            if (colonIndex != -1)
                surface = surface.substring(0, colonIndex)
            if (badSurfaceSpeedMap.contains(surface))
                return badSurfaceSpeed.toDouble()
        }
        return speed
    }
}
