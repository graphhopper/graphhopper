package com.graphhopper.routing.util.parsers

import com.graphhopper.routing.ev.BikeNetwork
import com.graphhopper.routing.ev.DecimalEncodedValue
import com.graphhopper.routing.ev.EncodedValueLookup
import com.graphhopper.routing.ev.EnumEncodedValue
import com.graphhopper.routing.ev.RouteNetwork
import com.graphhopper.routing.ev.Smoothness
import com.graphhopper.routing.ev.VehicleSpeed

open class MountainBikeAverageSpeedParser protected constructor(
    speedEnc: DecimalEncodedValue,
    smoothnessEnc: EnumEncodedValue<Smoothness>,
    bikeRouteEnc: EnumEncodedValue<RouteNetwork>
) : BikeCommonAverageSpeedParser(speedEnc, smoothnessEnc, bikeRouteEnc) {

    constructor(lookup: EncodedValueLookup) : this(
            lookup.getDecimalEncodedValue(VehicleSpeed.key("mtb")),
            lookup.getEnumEncodedValue(Smoothness.KEY, Smoothness::class.java),
            lookup.getEnumEncodedValue(BikeNetwork.KEY, RouteNetwork::class.java))

    init {
        setTrackTypeSpeed("grade1", 18) // paved
        setTrackTypeSpeed("grade2", 16) // now unpaved ...
        setTrackTypeSpeed("grade3", 12)
        setTrackTypeSpeed("grade4", 8)
        setTrackTypeSpeed("grade5", PUSHING_SECTION_SPEED) // like sand

        // +4km/h on certain surfaces (max 16km/h) due to wide MTB tires
        setSurfaceSpeed("dirt", 14)
        setSurfaceSpeed("earth", 14)
        setSurfaceSpeed("ground", 14)
        setSurfaceSpeed("fine_gravel", 16)
        setSurfaceSpeed("gravel", 16)
        setSurfaceSpeed("pebblestone", 16)
        setSurfaceSpeed("compacted", 16)
        setSurfaceSpeed("grass", 12)
        setSurfaceSpeed("grass_paver", 12)
    }
}
