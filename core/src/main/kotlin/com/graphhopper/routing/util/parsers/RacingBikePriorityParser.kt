package com.graphhopper.routing.util.parsers

import com.graphhopper.reader.ReaderWay
import com.graphhopper.routing.ev.DecimalEncodedValue
import com.graphhopper.routing.ev.EncodedValueLookup
import com.graphhopper.routing.ev.VehiclePriority
import com.graphhopper.routing.util.PriorityCode
import com.graphhopper.routing.util.PriorityCode.AVOID
import com.graphhopper.routing.util.PriorityCode.AVOID_MORE
import com.graphhopper.routing.util.PriorityCode.BAD
import com.graphhopper.routing.util.PriorityCode.SLIGHT_AVOID
import com.graphhopper.routing.util.PriorityCode.UNCHANGED
import com.graphhopper.routing.util.parsers.AbstractAccessParser.Companion.INTENDED
import java.util.TreeMap

open class RacingBikePriorityParser protected constructor(priorityEnc: DecimalEncodedValue) :
        BikeCommonPriorityParser(priorityEnc) {

    constructor(lookup: EncodedValueLookup) : this(lookup.getDecimalEncodedValue(VehiclePriority.key("racingbike")))

    init {
        addPushingSection("path")

        preferHighwayTags.add("road")
        preferHighwayTags.add("secondary")
        preferHighwayTags.add("secondary_link")
        preferHighwayTags.add("tertiary")
        preferHighwayTags.add("tertiary_link")

        avoidHighwayTags["motorway"] = BAD
        avoidHighwayTags["motorway_link"] = BAD
        avoidHighwayTags["trunk"] = BAD
        avoidHighwayTags["trunk_link"] = BAD

        setSpecificClassBicycle("roadcycling")

        avoidSpeedLimit = Double.POSITIVE_INFINITY
    }

    override fun collect(way: ReaderWay, bikeDesignated: Boolean, weightToPrioMap: TreeMap<Double, PriorityCode>) {
        super.collect(way, bikeDesignated, weightToPrioMap)

        val highway = way.getTag("highway")
        if (way.hasTag("foot", INTENDED)) {
            weightToPrioMap[100.0] = AVOID
        } else if ("service" == highway || "residential" == highway || "unclassified" == highway) {
            weightToPrioMap[40.0] = SLIGHT_AVOID
        } else if ("track" == highway) {
            val trackType = way.getTag("tracktype")
            if ("grade1" == trackType || goodSurface.contains(way.getTag("surface", "")))
                weightToPrioMap[110.0] = UNCHANGED
            else if (trackType == null || trackType.startsWith("grade"))
                weightToPrioMap[110.0] = AVOID_MORE
        }
    }
}
