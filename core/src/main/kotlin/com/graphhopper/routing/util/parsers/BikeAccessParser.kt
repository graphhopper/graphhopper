package com.graphhopper.routing.util.parsers

import com.graphhopper.routing.ev.BooleanEncodedValue
import com.graphhopper.routing.ev.EncodedValueLookup
import com.graphhopper.routing.ev.Roundabout
import com.graphhopper.routing.ev.VehicleAccess
import com.graphhopper.util.PMap

open class BikeAccessParser(accessEnc: BooleanEncodedValue, roundaboutEnc: BooleanEncodedValue) :
        BikeCommonAccessParser(accessEnc, roundaboutEnc) {

    constructor(lookup: EncodedValueLookup, properties: PMap) : this(
            lookup.getBooleanEncodedValue(VehicleAccess.key("bike")),
            lookup.getBooleanEncodedValue(Roundabout.KEY)) {
        blockPrivate(properties.getBool("block_private", true))
    }

    init {
        barriers.add("kissing_gate")
        barriers.add("stile")
        barriers.add("turnstile")
    }
}
