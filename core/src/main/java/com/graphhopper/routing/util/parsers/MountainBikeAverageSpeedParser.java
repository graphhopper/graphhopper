package com.graphhopper.routing.util.parsers;

import com.graphhopper.routing.ev.*;

public class MountainBikeAverageSpeedParser extends BikeCommonAverageSpeedParser {

    public MountainBikeAverageSpeedParser(EncodedValueLookup lookup) {
        this(lookup.getDecimalEncodedValue(VehicleSpeed.key("mtb")),
                lookup.getEnumEncodedValue(Smoothness.KEY, Smoothness.class),
                lookup.getEnumEncodedValue(BikeNetwork.KEY, RouteNetwork.class));
    }

    protected MountainBikeAverageSpeedParser(DecimalEncodedValue speedEnc,
                                             EnumEncodedValue<Smoothness> smoothnessEnc,
                                             EnumEncodedValue<RouteNetwork> bikeRouteEnc) {
        super(speedEnc, smoothnessEnc, bikeRouteEnc);
        setTrackTypeSpeed("grade1", 20); // paved
        setTrackTypeSpeed("grade2", 16); // now unpaved ...
        setTrackTypeSpeed("grade3", 12);
        setTrackTypeSpeed("grade4", 8);
        setTrackTypeSpeed("grade5", PUSHING_SECTION_SPEED); // like sand

        // +4km/h on certain surfaces (max 16km/h) due to wide MTB tires
        setSurfaceSpeed("dirt", 14);
        setSurfaceSpeed("earth", 14);
        setSurfaceSpeed("ground", 14);
        setSurfaceSpeed("fine_gravel", 16);
        setSurfaceSpeed("gravel", 16);
        setSurfaceSpeed("pebblestone", 16);
        setSurfaceSpeed("compacted", 16);
        setSurfaceSpeed("grass", 12);
        setSurfaceSpeed("grass_paver", 12);

        // 20km/h on paved roads matches the flat speed of a 160W cyclist on an mtb
        setSurfaceSpeed("paved", 20);
        setSurfaceSpeed("asphalt", 20);
        setSurfaceSpeed("concrete", 20);
        setHighwaySpeed("trunk", 20);
        setHighwaySpeed("trunk_link", 20);
        setHighwaySpeed("primary", 20);
        setHighwaySpeed("primary_link", 20);
        setHighwaySpeed("secondary", 20);
        setHighwaySpeed("secondary_link", 20);
        setHighwaySpeed("tertiary", 20);
        setHighwaySpeed("tertiary_link", 20);
        setHighwaySpeed("cycleway", 20);
        setHighwaySpeed("residential", 20);
        setHighwaySpeed("unclassified", 20);
    }
}
