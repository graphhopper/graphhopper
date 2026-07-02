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
import com.graphhopper.routing.ev.Roundabout
import com.graphhopper.routing.ev.VehicleAccess
import com.graphhopper.routing.util.FerrySpeedCalculator
import com.graphhopper.routing.util.TransportationMode
import com.graphhopper.routing.util.WayAccess
import com.graphhopper.routing.util.parsers.OSMTemporalAccessParser.Companion.hasPermissiveTemporalRestriction
import com.graphhopper.util.PMap

open class CarAccessParser(
    accessEnc: BooleanEncodedValue,
    @JvmField protected val roundaboutEnc: BooleanEncodedValue,
    properties: PMap,
    restrictionsKeys: List<String>
) : AbstractAccessParser(accessEnc, restrictionsKeys), TagParser {

    @JvmField
    protected val trackTypeValues: MutableSet<String?> = HashSet()

    @JvmField
    protected val highwayValues: MutableSet<String> = HashSet()

    constructor(lookup: EncodedValueLookup, properties: PMap) : this(
            lookup.getBooleanEncodedValue(VehicleAccess.key("car")),
            lookup.getBooleanEncodedValue(Roundabout.KEY),
            properties,
            OSMRoadAccessParser.toOSMRestrictions(TransportationMode.CAR)
    )

    init {
        restrictedValues.add("agricultural")
        restrictedValues.add("forestry")
        restrictedValues.add("delivery")

        blockPrivate(properties.getBool("block_private", true))

        barriers.add("kissing_gate")
        barriers.add("fence")
        barriers.add("bollard")
        barriers.add("stile")
        barriers.add("turnstile")
        barriers.add("cycle_barrier")
        barriers.add("motorcycle_barrier")
        barriers.add("block")
        barriers.add("bus_trap")
        barriers.add("sump_buster")
        barriers.add("jersey_barrier")

        highwayValues.addAll(listOf("motorway", "motorway_link", "trunk", "trunk_link",
                "primary", "primary_link", "secondary", "secondary_link", "tertiary", "tertiary_link",
                "unclassified", "residential", "living_street", "service", "road", "track", "pedestrian"))

        trackTypeValues.addAll(listOf("grade1", "grade2", "grade3", null))
    }

    open fun getAccess(way: ReaderWay): WayAccess {
        // TODO: Ferries have conditionals, like opening hours or are closed during some time in the year
        val highwayValue = way.getTag("highway")
        val firstIndex = way.getFirstIndex(restrictionKeys)
        val firstValue = if (firstIndex < 0) "" else way.getTag(restrictionKeys[firstIndex], "")
        if (highwayValue == null) {
            if (FerrySpeedCalculator.isFerry(way)) {
                if (allowedValues.contains(firstValue) ||
                        // implied default is allowed only if foot and bicycle is not specified:
                        firstValue.isEmpty() && !way.hasTag("foot") && !way.hasTag("bicycle") ||
                        // if hgv is allowed then smaller trucks and cars are allowed too
                        way.hasTag("hgv", "yes"))
                    return WayAccess.FERRY
                if (restrictedValues.contains(firstValue))
                    return WayAccess.CAN_SKIP
            }
            return WayAccess.CAN_SKIP
        }

        if ("pedestrian" == highwayValue
                && !allowedValues.contains(firstValue)
                && !hasPermissiveTemporalRestriction(way, restrictionKeys.size - 1, restrictionKeys, allowedValues)) {
            // allow pedestrian if explicitly tagged
            return WayAccess.CAN_SKIP
        }

        if ("service" == highwayValue && "emergency_access" == way.getTag("service"))
            return WayAccess.CAN_SKIP

        if ("track" == highwayValue && !trackTypeValues.contains(way.getTag("tracktype")))
            return WayAccess.CAN_SKIP

        if (!highwayValues.contains(highwayValue))
            return WayAccess.CAN_SKIP

        // this is a very rare tagging which we should/could remove (the status key itself is described as "vague")
        if (way.hasTag("impassable", "yes") || way.hasTag("status", "impassable"))
            return WayAccess.CAN_SKIP

        // multiple restrictions needs special handling
        if (firstIndex >= 0) {
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

        return WayAccess.WAY
    }

    override fun handleWayTags(edgeId: Int, edgeIntAccess: EdgeIntAccess, way: ReaderWay) {
        val access = getAccess(way)
        if (access.canSkip())
            return

        val isRoundabout = roundaboutEnc.getBool(false, edgeId, edgeIntAccess)
        if (isOneway(way) || isRoundabout) {
            if (isForwardOneway(way))
                accessEnc.setBool(false, edgeId, edgeIntAccess, true)
            if (isBackwardOneway(way))
                accessEnc.setBool(true, edgeId, edgeIntAccess, true)
        } else {
            accessEnc.setBool(false, edgeId, edgeIntAccess, true)
            accessEnc.setBool(true, edgeId, edgeIntAccess, true)
        }

        if (way.hasTag("gh:barrier_edge")) {
            val nodeTags: List<Map<String, Any>> = way.getTag("node_tags", emptyList())
            handleBarrierEdge(edgeId, edgeIntAccess, nodeTags[0])
        }
    }

    /**
     * make sure that isOneway is called before
     */
    protected open fun isBackwardOneway(way: ReaderWay): Boolean {
        return way.hasTag("oneway", "-1")
                || way.hasTag("vehicle:forward", restrictedValues)
                || way.hasTag("motor_vehicle:forward", restrictedValues)
    }

    /**
     * make sure that isOneway is called before
     */
    protected open fun isForwardOneway(way: ReaderWay): Boolean {
        return !way.hasTag("oneway", "-1")
                && !way.hasTag("vehicle:forward", restrictedValues)
                && !way.hasTag("motor_vehicle:forward", restrictedValues)
    }

    protected open fun isOneway(way: ReaderWay): Boolean {
        return way.hasTag("oneway", ONEWAYS)
                || way.hasTag("vehicle:backward", restrictedValues)
                || way.hasTag("vehicle:forward", restrictedValues)
                || way.hasTag("motor_vehicle:backward", restrictedValues)
                || way.hasTag("motor_vehicle:forward", restrictedValues)
    }
}
