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
import com.graphhopper.routing.ev.BikeRoadAccess
import com.graphhopper.routing.ev.Country
import com.graphhopper.routing.ev.EdgeIntAccess
import com.graphhopper.routing.ev.EnumEncodedValue
import com.graphhopper.routing.ev.FootRoadAccess
import com.graphhopper.routing.ev.RoadAccess
import com.graphhopper.routing.ev.RoadClass
import com.graphhopper.routing.util.TransportationMode
import com.graphhopper.storage.IntsRef
import java.util.function.Function

class OSMRoadAccessParser<T : Enum<*>>(
    protected val accessEnc: EnumEncodedValue<T>,
    private val restrictions: List<String>,
    private val roadAccessDefaultHandler: RoadAccessDefaultHandler<T?>,
    private val valueFinder: Function<String, T?>
) : TagParser {

    override fun handleWayTags(edgeId: Int, edgeIntAccess: EdgeIntAccess, way: ReaderWay, relationFlags: IntsRef?) {
        var accessValue: T? = null

        val nodeTags: List<Map<String, Any>> = way.getTag("node_tags", emptyList())
        // a barrier edge has the restriction in both nodes and the tags are the same
        if (way.hasTag("gh:barrier_edge"))
            for (restriction in restrictions) {
                val value = nodeTags[0][restriction]
                accessValue = getRoadAccess(value as String?, accessValue)
                if (accessValue != null) break
            }

        // Resolve the base access value in this priority order:
        //   1. explicit way tag (most specific)
        //   2. country-specific default (e.g. France allows bikes on pedestrian streets)
        //   3. universal OSM-wiki implied default (e.g. pedestrian implies motor_vehicle=no)
        // Steps 2 and 3 only fire if the previous step found nothing.
        for (restriction in restrictions) {
            accessValue = getRoadAccess(way.getTag(restriction), accessValue)
            if (accessValue != null) break
        }
        if (accessValue == null) {
            val country = way.getTag("country", Country.MISSING)
            accessValue = roadAccessDefaultHandler.getAccess(way, country)
        }
        if (accessValue == null) {
            val highwayValue = way.getTag("highway")
            val impliedDefaults: Map<String, String> = if (highwayValue == null)
                emptyMap()
            else
                ModeAccessParser.HIGHWAY_TYPE_DEFAULTS.getOrDefault(highwayValue, emptyMap())
            for (restriction in restrictions) {
                val implied = impliedDefaults[restriction]
                if (implied != null) {
                    accessValue = getRoadAccess(implied, accessValue)
                    if (accessValue != null) break
                }
            }
        }

        // Also check conditional tags. "Least restrictive wins": a conditional value can only
        // relax a base, not tighten an implicit default — e.g. motor_vehicle=no plus
        // motor_vehicle:conditional=delivery @ (time) qualifies as DELIVERY, but a conditional
        // alone (no base) must not turn an otherwise-accessible road into a restricted one.
        for (restriction in restrictions) {
            val conditionalValue = getConditionalRoadAccess(way.getTag("$restriction:conditional"))
            if (conditionalValue != null) {
                if (accessValue != null && conditionalValue.ordinal < accessValue.ordinal)
                    accessValue = conditionalValue
                break
            }
        }

        if (accessValue != null)
            accessEnc.setEnum(false, edgeId, edgeIntAccess, accessValue)
    }

    private fun getConditionalRoadAccess(tagValue: String?): T? {
        if (tagValue == null) return null
        val strs = tagValue.split("@").dropLastWhile { it.isEmpty() }
        if (strs.size == 2 && OSMTemporalAccessParser.hasTemporalSpec(strs[1])) {
            return valueFinder.apply(strs[0].trim())
        }
        return null
    }

    private fun getRoadAccess(tagValue: String?, accessValue: T?): T? {
        var result = accessValue
        if (tagValue != null) {
            val complex = tagValue.split(";").dropLastWhile { it.isEmpty() }
            for (simple in complex) {
                val tmpAccessValue = valueFinder.apply(simple) ?: continue
                if (result == null || tmpAccessValue.ordinal < result.ordinal) {
                    result = tmpAccessValue
                }
            }
        }
        return result
    }

    fun interface RoadAccessDefaultHandler<T> {
        fun getAccess(readerWay: ReaderWay, country: Country): T
    }

    companion object {
        @JvmStatic
        fun getRoadClass(readerWay: ReaderWay): RoadClass {
            val hw = readerWay.getTag("highway", "")
            return RoadClass.find(if (hw.endsWith("_link")) hw.substring(0, hw.length - 5) else hw)
        }

        @JvmField
        val CAR_HANDLER: RoadAccessDefaultHandler<RoadAccess?> = RoadAccessDefaultHandler { readerWay, country ->
            val roadClass = getRoadClass(readerWay)
            when (country) {
                Country.AUT -> when (roadClass) {
                    RoadClass.LIVING_STREET -> RoadAccess.DESTINATION
                    RoadClass.TRACK -> RoadAccess.FORESTRY
                    RoadClass.PATH, RoadClass.BRIDLEWAY, RoadClass.CYCLEWAY, RoadClass.FOOTWAY, RoadClass.PEDESTRIAN -> RoadAccess.NO
                    else -> RoadAccess.YES
                }
                Country.DEU -> when (roadClass) {
                    RoadClass.TRACK -> RoadAccess.DESTINATION
                    RoadClass.PATH, RoadClass.BRIDLEWAY, RoadClass.CYCLEWAY, RoadClass.FOOTWAY, RoadClass.PEDESTRIAN -> RoadAccess.NO
                    else -> RoadAccess.YES
                }
                Country.HUN -> {
                    if (roadClass == RoadClass.LIVING_STREET) RoadAccess.DESTINATION
                    else RoadAccess.YES
                }
                else -> null
            }
        }

        // Based on https://wiki.openstreetmap.org/wiki/OSM_tags_for_routing/Access_restrictions
        // The motorroad tag is handled in FootAccessParser and BikeCommonAccessParser via always skipping.
        // See https://wiki.openstreetmap.org/wiki/Tag:motorroad%3Dyes
        @JvmField
        val FOOT_HANDLER: RoadAccessDefaultHandler<FootRoadAccess?> = RoadAccessDefaultHandler { readerWay, country ->
            val roadClass = getRoadClass(readerWay)
            when (country) {
                Country.AUT, Country.CHE, Country.HRV, Country.SVK, Country.FRA ->
                    if (roadClass == RoadClass.TRUNK || roadClass == RoadClass.BRIDLEWAY) FootRoadAccess.NO
                    else null
                Country.BEL ->
                    if (roadClass == RoadClass.TRUNK /* foot=no implied for highway=trunk without motorroad=yes? */ || roadClass == RoadClass.BUSWAY) FootRoadAccess.NO
                    else if (roadClass == RoadClass.CYCLEWAY) FootRoadAccess.YES
                    else null
                Country.BLR, Country.RUS, Country.DEU, Country.ESP, Country.UKR ->
                    if (roadClass == RoadClass.BRIDLEWAY) FootRoadAccess.NO
                    else null
                Country.BRA ->
                    if (roadClass == RoadClass.BUSWAY) FootRoadAccess.NO
                    else null
                Country.CHN ->
                    if (roadClass == RoadClass.CYCLEWAY) FootRoadAccess.YES
                    else if (roadClass == RoadClass.BRIDLEWAY) FootRoadAccess.NO
                    else null
                Country.DNK ->
                    if (roadClass == RoadClass.TRUNK || roadClass == RoadClass.BRIDLEWAY) FootRoadAccess.NO
                    else if (roadClass == RoadClass.CYCLEWAY) FootRoadAccess.YES
                    else null
                Country.FIN ->
                    if (roadClass == RoadClass.BRIDLEWAY) FootRoadAccess.NO
                    else if (roadClass == RoadClass.CYCLEWAY) FootRoadAccess.YES
                    else null
                Country.GBR, Country.GRC, Country.ISL, Country.PHL, Country.THA, Country.USA, Country.NOR ->
                    if (roadClass == RoadClass.CYCLEWAY) FootRoadAccess.YES
                    else null
                Country.HUN ->
                    if (roadClass == RoadClass.TRUNK || roadClass == RoadClass.BRIDLEWAY) FootRoadAccess.NO
                    else if (roadClass == RoadClass.CYCLEWAY) FootRoadAccess.YES
                    else null
                Country.NLD ->
                    if (roadClass == RoadClass.BUSWAY || roadClass == RoadClass.BRIDLEWAY) FootRoadAccess.NO
                    else if (roadClass == RoadClass.CYCLEWAY) FootRoadAccess.YES
                    else null
                Country.OMN ->
                    if (roadClass == RoadClass.CYCLEWAY) FootRoadAccess.DESIGNATED
                    else null
                Country.SWE ->
                    if (roadClass == RoadClass.BUSWAY) FootRoadAccess.NO
                    else if (roadClass == RoadClass.CYCLEWAY) FootRoadAccess.YES
                    else null
                else -> null
            }
        }

        @JvmField
        val BIKE_HANDLER: RoadAccessDefaultHandler<BikeRoadAccess?> = RoadAccessDefaultHandler { readerWay, country ->
            // Unfortunately in practise bicycle=no is not distinguishable from bicycle=dismount
            // and so we use the implicit 'dismount' for footway and pedestrian for all countries,
            // except countries with an explicit 'yes'.
            val roadClass = getRoadClass(readerWay)
            when (country) {
                Country.AUT, Country.HRV, Country.HUN, Country.CHE, Country.DNK, Country.SVK ->
                    if (roadClass == RoadClass.TRUNK || roadClass == RoadClass.BRIDLEWAY) BikeRoadAccess.NO
                    else null
                Country.BEL ->
                    if (roadClass == RoadClass.TRUNK /* bicycle=no implied for highway=trunk without motorroad=yes? */
                        || roadClass == RoadClass.BUSWAY || roadClass == RoadClass.BRIDLEWAY) BikeRoadAccess.NO
                    else if (roadClass == RoadClass.PEDESTRIAN) BikeRoadAccess.YES
                    else null
                Country.BLR ->
                    if (roadClass == RoadClass.BRIDLEWAY) BikeRoadAccess.NO
                    else if (roadClass == RoadClass.PEDESTRIAN) BikeRoadAccess.DESIGNATED
                    else if (roadClass == RoadClass.FOOTWAY) BikeRoadAccess.YES
                    else null
                Country.BRA ->
                    if (roadClass == RoadClass.BUSWAY) BikeRoadAccess.NO
                    else null
                Country.CHN ->
                    if (roadClass == RoadClass.BRIDLEWAY) BikeRoadAccess.NO
                    else if (roadClass == RoadClass.PEDESTRIAN) BikeRoadAccess.YES
                    else null
                Country.DEU, Country.TUR, Country.RUS, Country.UKR ->
                    if (roadClass == RoadClass.BRIDLEWAY) BikeRoadAccess.NO
                    else null
                Country.ESP ->
                    if (roadClass == RoadClass.BRIDLEWAY) BikeRoadAccess.NO
                    else if (roadClass == RoadClass.PEDESTRIAN) BikeRoadAccess.YES
                    else null
                Country.FIN ->
                    if (roadClass == RoadClass.BRIDLEWAY) BikeRoadAccess.NO
                    else if (roadClass == RoadClass.PEDESTRIAN) BikeRoadAccess.YES
                    else null
                Country.FRA ->
                    if (roadClass == RoadClass.TRUNK || roadClass == RoadClass.BRIDLEWAY) BikeRoadAccess.NO
                    else if (roadClass == RoadClass.PEDESTRIAN) BikeRoadAccess.YES
                    else null
                Country.ISL, Country.NOR ->
                    if (roadClass == RoadClass.PEDESTRIAN || roadClass == RoadClass.FOOTWAY) BikeRoadAccess.YES
                    else null
                Country.ITA, Country.PHL, Country.THA, Country.USA, Country.SWE ->
                    if (roadClass == RoadClass.PEDESTRIAN) BikeRoadAccess.YES
                    else null
                Country.NLD ->
                    if (roadClass == RoadClass.BUSWAY || roadClass == RoadClass.BRIDLEWAY) BikeRoadAccess.NO
                    else null
                Country.OMN ->
                    if (roadClass == RoadClass.MOTORWAY) BikeRoadAccess.YES
                    else null
                else -> null
            }
        }

        @JvmStatic
        fun toOSMRestrictions(mode: TransportationMode): List<String> {
            return when (mode) {
                TransportationMode.FOOT -> listOf("foot", "access")
                TransportationMode.VEHICLE -> listOf("vehicle", "access")
                TransportationMode.BIKE -> listOf("bicycle", "vehicle", "access")
                TransportationMode.CAR -> listOf("motorcar", "motor_vehicle", "vehicle", "access")
                TransportationMode.MOTORCYCLE -> listOf("motorcycle", "motor_vehicle", "vehicle", "access")
                TransportationMode.HGV -> listOf("hgv", "motor_vehicle", "vehicle", "access")
                TransportationMode.PSV -> listOf("psv", "motor_vehicle", "vehicle", "access")
                TransportationMode.BUS -> listOf("bus", "psv", "motor_vehicle", "vehicle", "access")
                TransportationMode.HOV -> listOf("hov", "motor_vehicle", "vehicle", "access")
                else -> throw IllegalArgumentException("Cannot convert TransportationMode $mode to list of restrictions")
            }
        }

        @JvmStatic
        fun forCar(roadAccessEnc: EnumEncodedValue<RoadAccess>): OSMRoadAccessParser<RoadAccess> {
            return OSMRoadAccessParser(roadAccessEnc, toOSMRestrictions(TransportationMode.CAR), CAR_HANDLER) { RoadAccess.find(it) }
        }

        @JvmStatic
        fun forBike(roadAccessEnc: EnumEncodedValue<BikeRoadAccess>): OSMRoadAccessParser<BikeRoadAccess> {
            return OSMRoadAccessParser(roadAccessEnc, toOSMRestrictions(TransportationMode.BIKE), BIKE_HANDLER) { BikeRoadAccess.find(it) }
        }

        @JvmStatic
        fun forFoot(roadAccessEnc: EnumEncodedValue<FootRoadAccess>): OSMRoadAccessParser<FootRoadAccess> {
            return OSMRoadAccessParser(roadAccessEnc, toOSMRestrictions(TransportationMode.FOOT), FOOT_HANDLER) { FootRoadAccess.find(it) }
        }
    }
}
