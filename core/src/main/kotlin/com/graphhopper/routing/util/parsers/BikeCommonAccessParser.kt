package com.graphhopper.routing.util.parsers

import com.graphhopper.reader.ReaderWay
import com.graphhopper.routing.ev.BooleanEncodedValue
import com.graphhopper.routing.ev.EdgeIntAccess
import com.graphhopper.routing.util.FerrySpeedCalculator
import com.graphhopper.routing.util.WayAccess
import com.graphhopper.routing.util.parsers.OSMTemporalAccessParser.hasPermissiveTemporalRestriction

abstract class BikeCommonAccessParser protected constructor(
    accessEnc: BooleanEncodedValue,
    private val roundaboutEnc: BooleanEncodedValue
) : AbstractAccessParser(accessEnc, RESTRICTIONS), TagParser {

    private val allowedHighways = HashSet<String>()

    init {
        restrictedValues.add("agricultural")
        restrictedValues.add("forestry")
        restrictedValues.add("delivery")

        barriers.add("fence")

        allowedHighways.addAll(listOf("living_street", "steps", "cycleway", "path", "footway", "platform",
                "pedestrian", "track", "service", "residential", "unclassified", "road", "bridleway",
                "motorway", "motorway_link", "trunk", "trunk_link",
                "primary", "primary_link", "secondary", "secondary_link", "tertiary", "tertiary_link"))
    }

    open fun getAccess(way: ReaderWay): WayAccess {
        val highwayValue = way.getTag("highway")
        if (highwayValue == null) {
            var access = WayAccess.CAN_SKIP

            if (FerrySpeedCalculator.isFerry(way)) {
                // if bike is NOT explicitly tagged allow bike but only if foot is not specified either
                val bikeTag = way.getTag("bicycle")
                if (bikeTag == null && !way.hasTag("foot") || allowedValues.contains(bikeTag) || "dismount" == bikeTag)
                    access = WayAccess.FERRY
            }

            // special case not for all acceptedRailways, only platform
            if (way.hasTag("railway", "platform"))
                access = WayAccess.WAY

            if (way.hasTag("man_made", "pier"))
                access = WayAccess.WAY

            if (!access.canSkip()) {
                if (way.hasTag(restrictionKeys, restrictedValues))
                    return WayAccess.CAN_SKIP
                return access
            }

            return WayAccess.CAN_SKIP
        }

        if (!allowedHighways.contains(highwayValue))
            return WayAccess.CAN_SKIP

        if (way.hasTag("bicycle", "dismount") // use the way for pushing
                || "cycleway" == highwayValue && !way.hasTag("bicycle", "no")) // cycleway gets bicycle=yes by default
            return WayAccess.WAY

        val firstIndex = way.getFirstIndex(restrictionKeys)
        if (firstIndex >= 0) {
            val firstValue = way.getTag(restrictionKeys[firstIndex], "")
            val restrict = firstValue.split(";").toTypedArray()
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

        // accept only if explicitly tagged for bike usage
        if ("motorway" == highwayValue || "motorway_link" == highwayValue)
            return WayAccess.CAN_SKIP

        if (way.hasTag("motorroad", "yes"))
            return WayAccess.CAN_SKIP

        return WayAccess.WAY
    }

    override fun handleWayTags(edgeId: Int, edgeIntAccess: EdgeIntAccess, way: ReaderWay) {
        val access = getAccess(way)
        if (access.canSkip())
            return

        // handle oneways. The value -1 means it is a oneway but for reverse direction of stored geometry.
        // The tagging oneway:bicycle=no or cycleway:right:oneway=no or cycleway:left:oneway=no lifts the generic oneway restriction of the way for bike.
        // cycleway:*:oneway describes the cycle facility, not the carriageway, so it does not by itself
        // make the road oneway for bikes — it only modifies the bike direction when the carriageway is oneway (handled below).
        val isOneway = way.hasTag("oneway", ONEWAYS) && !way.hasTag("oneway", "-1") && !way.hasTag("bicycle:backward", allowedValues)
                || way.hasTag("oneway", "-1") && !way.hasTag("bicycle:forward", allowedValues)
                || way.hasTag("oneway:bicycle", ONEWAYS)
                || way.hasTag("vehicle:backward", restrictedValues) && !way.hasTag("bicycle:forward", allowedValues)
                || way.hasTag("vehicle:forward", restrictedValues) && !way.hasTag("bicycle:backward", allowedValues)
                || way.hasTag("bicycle:forward", restrictedValues)
                || way.hasTag("bicycle:backward", restrictedValues)

        if ((isOneway || roundaboutEnc.getBool(false, edgeId, edgeIntAccess))
                && !way.hasTag("oneway:bicycle", "no")
                && !(way.hasTag("cycleway:both") && !way.hasTag("cycleway:both", "no"))
                && !way.hasTag("cycleway", OPP_LANES)
                && !way.hasTag("cycleway:left", OPP_LANES)
                && !way.hasTag("cycleway:right", OPP_LANES)
                && !way.hasTag("cycleway:left:oneway", "no")
                && !way.hasTag("cycleway:right:oneway", "no")) {
            val isBackward = way.hasTag("oneway", "-1")
                    || way.hasTag("oneway:bicycle", "-1")
                    || way.hasTag("cycleway:left:oneway", "-1")
                    || way.hasTag("cycleway:right:oneway", "-1")
                    || way.hasTag("vehicle:forward", restrictedValues)
                    || way.hasTag("bicycle:forward", restrictedValues)
            accessEnc.setBool(isBackward, edgeId, edgeIntAccess, true)

        } else {
            accessEnc.setBool(true, edgeId, edgeIntAccess, true)
            accessEnc.setBool(false, edgeId, edgeIntAccess, true)
        }

        if (way.hasTag("gh:barrier_edge")) {
            val nodeTags: List<Map<String, Any>> = way.getTag("node_tags", emptyList())
            handleBarrierEdge(edgeId, edgeIntAccess, nodeTags[0])
        }
    }

    companion object {
        private val OPP_LANES: Set<String> = HashSet(listOf("opposite", "opposite_lane", "opposite_track"))

        /**
         * The access restriction list returned from OSMRoadAccessParser.toOSMRestrictions(TransportationMode.Bike)
         * contains "vehicle". But here we want to allow walking via dismount.
         */
        private val RESTRICTIONS: List<String> = listOf("bicycle", "access")
    }
}
