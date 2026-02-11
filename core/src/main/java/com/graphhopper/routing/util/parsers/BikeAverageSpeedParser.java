package com.graphhopper.routing.util.parsers;

import com.graphhopper.routing.ev.*;

public class BikeAverageSpeedParser extends BikeCommonAverageSpeedParser {

    public BikeAverageSpeedParser(EncodedValueLookup lookup) {
        this(lookup.getDecimalEncodedValue(VehicleSpeed.key("bike")),
                lookup.getEnumEncodedValue(Smoothness.KEY, Smoothness.class),
                lookup.getEnumEncodedValue(BikeNetwork.KEY, RouteNetwork.class));
    }

    public BikeAverageSpeedParser(DecimalEncodedValue speedEnc,
                                  EnumEncodedValue<Smoothness> smoothnessEnc,
                                  EnumEncodedValue<RouteNetwork> bikeRouteEnc) {
        super(speedEnc, smoothnessEnc, bikeRouteEnc);
    }
}
