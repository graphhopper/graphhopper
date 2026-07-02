package com.graphhopper.routing.util.parsers

import com.graphhopper.reader.ReaderWay
import com.graphhopper.routing.ev.DecimalEncodedValue
import com.graphhopper.routing.ev.EncodedValueLookup
import com.graphhopper.routing.ev.VehiclePriority
import com.graphhopper.routing.util.PriorityCode
import com.graphhopper.routing.util.PriorityCode.BAD
import com.graphhopper.routing.util.PriorityCode.PREFER
import com.graphhopper.routing.util.PriorityCode.SLIGHT_PREFER
import com.graphhopper.routing.util.PriorityCode.VERY_NICE
import java.util.TreeMap

open class MountainBikePriorityParser protected constructor(priorityEnc: DecimalEncodedValue) :
        BikeCommonPriorityParser(priorityEnc) {

    constructor(lookup: EncodedValueLookup) : this(lookup.getDecimalEncodedValue(VehiclePriority.key("mtb")))

    init {
        avoidHighwayTags["primary"] = BAD
        avoidHighwayTags["primary_link"] = BAD

        preferHighwayTags.add("road")
        preferHighwayTags.add("track")
        preferHighwayTags.add("path")
        preferHighwayTags.add("service")
        preferHighwayTags.add("tertiary")
        preferHighwayTags.add("tertiary_link")
        preferHighwayTags.add("residential")
        preferHighwayTags.add("unclassified")
        preferHighwayTags.add("cycleway")

        setSpecificClassBicycle("mtb")
    }

    override fun collect(way: ReaderWay, bikeDesignated: Boolean, weightToPrioMap: TreeMap<Double, PriorityCode>) {
        super.collect(way, bikeDesignated, weightToPrioMap)

        val highway = way.getTag("highway")
        if ("track" == highway) {
            val trackType = way.getTag("tracktype")
            if ("grade1" == trackType || goodSurface.contains(way.getTag("surface", "")))
                weightToPrioMap[50.0] = SLIGHT_PREFER
            else if (trackType == null)
                weightToPrioMap[90.0] = PREFER
            else if (trackType.startsWith("grade"))
                weightToPrioMap[100.0] = VERY_NICE
        }
    }
}
