package com.graphhopper.routing.util.parsers

import com.graphhopper.routing.ev.DecimalEncodedValue
import com.graphhopper.routing.ev.EncodedValueLookup
import com.graphhopper.routing.ev.VehiclePriority
import com.graphhopper.routing.util.PriorityCode.BAD

open class BikePriorityParser(priorityEnc: DecimalEncodedValue) : BikeCommonPriorityParser(priorityEnc) {

    constructor(lookup: EncodedValueLookup) : this(lookup.getDecimalEncodedValue(VehiclePriority.key("bike")))

    init {
        addPushingSection("path")

        avoidHighwayTags["primary"] = BAD
        avoidHighwayTags["primary_link"] = BAD

        preferHighwayTags.add("service")
        preferHighwayTags.add("residential")
        preferHighwayTags.add("unclassified")
        preferHighwayTags.add("cycleway")

        setSpecificClassBicycle("touring")
    }
}
