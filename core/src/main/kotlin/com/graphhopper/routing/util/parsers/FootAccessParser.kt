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
import com.graphhopper.routing.ev.BooleanEncodedValue
import com.graphhopper.routing.ev.EdgeIntAccess
import com.graphhopper.routing.ev.EncodedValueLookup
import com.graphhopper.routing.ev.RouteNetwork
import com.graphhopper.routing.ev.RouteNetwork.INTERNATIONAL
import com.graphhopper.routing.ev.RouteNetwork.LOCAL
import com.graphhopper.routing.ev.RouteNetwork.NATIONAL
import com.graphhopper.routing.ev.RouteNetwork.REGIONAL
import com.graphhopper.routing.ev.VehicleAccess
import com.graphhopper.routing.util.FerrySpeedCalculator
import com.graphhopper.routing.util.PriorityCode.UNCHANGED
import com.graphhopper.routing.util.TransportationMode
import com.graphhopper.routing.util.WayAccess
import com.graphhopper.routing.util.parsers.OSMTemporalAccessParser.hasPermissiveTemporalRestriction
import com.graphhopper.util.PMap

open class FootAccessParser protected constructor(accessEnc: BooleanEncodedValue) :
        AbstractAccessParser(accessEnc, OSMRoadAccessParser.toOSMRestrictions(TransportationMode.FOOT)), TagParser {

    internal val allowedHighwayTags: MutableSet<String> = HashSet()

    @JvmField
    protected var sidewalkValues: HashSet<String> = HashSet(5)

    @JvmField
    protected var routeMap: MutableMap<RouteNetwork, Int> = HashMap()

    constructor(lookup: EncodedValueLookup, properties: PMap) : this(lookup.getBooleanEncodedValue(VehicleAccess.key("foot"))) {
        blockPrivate(properties.getBool("block_private", true))
    }

    init {
        sidewalkValues.add("yes")
        sidewalkValues.add("both")
        sidewalkValues.add("left")
        sidewalkValues.add("right")

        barriers.add("fence")

        allowedHighwayTags.add("footway")
        allowedHighwayTags.add("path")
        allowedHighwayTags.add("steps")
        allowedHighwayTags.add("pedestrian")
        allowedHighwayTags.add("living_street")
        allowedHighwayTags.add("track")
        allowedHighwayTags.add("residential")
        allowedHighwayTags.add("service")
        allowedHighwayTags.add("platform")
        allowedHighwayTags.add("trunk")
        allowedHighwayTags.add("trunk_link")
        allowedHighwayTags.add("primary")
        allowedHighwayTags.add("primary_link")
        allowedHighwayTags.add("secondary")
        allowedHighwayTags.add("secondary_link")
        allowedHighwayTags.add("tertiary")
        allowedHighwayTags.add("tertiary_link")
        allowedHighwayTags.add("cycleway")
        allowedHighwayTags.add("unclassified")
        allowedHighwayTags.add("road")
        allowedHighwayTags.add("bridleway")

        routeMap[INTERNATIONAL] = UNCHANGED.value
        routeMap[NATIONAL] = UNCHANGED.value
        routeMap[REGIONAL] = UNCHANGED.value
        routeMap[LOCAL] = UNCHANGED.value
    }

    /**
     * Some ways are okay but not separate for pedestrians.
     */
    open fun getAccess(way: ReaderWay): WayAccess {
        val highwayValue = way.getTag("highway")
        if (highwayValue == null) {
            var acceptPotentially = WayAccess.CAN_SKIP

            if (FerrySpeedCalculator.isFerry(way)) {
                val footTag = way.getTag("foot")
                if (footTag == null || allowedValues.contains(footTag))
                    acceptPotentially = WayAccess.FERRY
            }

            // special case not for all acceptedRailways, only platform
            if (way.hasTag("railway", "platform"))
                acceptPotentially = WayAccess.WAY

            if (way.hasTag("man_made", "pier"))
                acceptPotentially = WayAccess.WAY

            if (!acceptPotentially.canSkip()) {
                if (way.hasTag(restrictionKeys, restrictedValues))
                    return WayAccess.CAN_SKIP
                return acceptPotentially
            }

            return WayAccess.CAN_SKIP
        }

        // via_ferrata is too dangerous, see #1326
        if ("via_ferrata" == highwayValue)
            return WayAccess.CAN_SKIP

        val firstIndex = way.getFirstIndex(restrictionKeys)
        if (firstIndex >= 0) {
            val firstValue = way.getTag(restrictionKeys[firstIndex], "")
            val restrict = firstValue.split(";")
            // if any of the values allows access then return early (regardless of the order)
            for (value in restrict) {
                if (allowedValues.contains(value))
                    return WayAccess.WAY
            }
            for (value in restrict) {
                if (restrictedValues.contains(value) && !hasPermissiveTemporalRestriction(way, firstIndex, restrictionKeys, allowedValues))
                    return WayAccess.CAN_SKIP
            }
        }

        if (way.hasTag("sidewalk", sidewalkValues))
            return WayAccess.WAY

        if (!allowedHighwayTags.contains(highwayValue))
            return WayAccess.CAN_SKIP

        if (way.hasTag("motorroad", "yes"))
            return WayAccess.CAN_SKIP

        return WayAccess.WAY
    }

    override fun handleWayTags(edgeId: Int, edgeIntAccess: EdgeIntAccess, way: ReaderWay) {
        val access = getAccess(way)
        if (access.canSkip())
            return

        if (way.hasTag("oneway:foot", ONEWAYS) || way.hasTag("foot:backward") || way.hasTag("foot:forward")
                || way.hasTag("oneway", ONEWAYS) && (way.hasTag("highway", "steps") /* <- outdated mapping style */ || access.isFerry())) {
            val reverse = way.hasTag("oneway:foot", "-1") || way.hasTag("foot:backward", "yes") || way.hasTag("foot:forward", "no")
            accessEnc.setBool(reverse, edgeId, edgeIntAccess, true)
        } else {
            accessEnc.setBool(false, edgeId, edgeIntAccess, true)
            accessEnc.setBool(true, edgeId, edgeIntAccess, true)
        }

        if (way.hasTag("gh:barrier_edge")) {
            val nodeTags: List<Map<String, Any>> = way.getTag("node_tags", emptyList())
            handleBarrierEdge(edgeId, edgeIntAccess, nodeTags[0])
        }
    }
}
