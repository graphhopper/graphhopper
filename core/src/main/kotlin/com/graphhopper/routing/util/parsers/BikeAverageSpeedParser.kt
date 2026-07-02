package com.graphhopper.routing.util.parsers

import com.graphhopper.routing.ev.BikeNetwork
import com.graphhopper.routing.ev.DecimalEncodedValue
import com.graphhopper.routing.ev.EncodedValueLookup
import com.graphhopper.routing.ev.EnumEncodedValue
import com.graphhopper.routing.ev.RouteNetwork
import com.graphhopper.routing.ev.Smoothness
import com.graphhopper.routing.ev.VehicleSpeed

open class BikeAverageSpeedParser(
    speedEnc: DecimalEncodedValue,
    smoothnessEnc: EnumEncodedValue<Smoothness>,
    bikeRouteEnc: EnumEncodedValue<RouteNetwork>
) : BikeCommonAverageSpeedParser(speedEnc, smoothnessEnc, bikeRouteEnc) {

    constructor(lookup: EncodedValueLookup) : this(
            lookup.getDecimalEncodedValue(VehicleSpeed.key("bike")),
            lookup.getEnumEncodedValue(Smoothness.KEY, Smoothness::class.java),
            lookup.getEnumEncodedValue(BikeNetwork.KEY, RouteNetwork::class.java))
}
