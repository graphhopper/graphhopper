package com.graphhopper.routing.util.parsers;

import com.graphhopper.routing.ev.*;

public class BikeAverageSpeedParser extends BikeCommonAverageSpeedParser {

    public BikeAverageSpeedParser(EncodedValueLookup lookup) {
        this(lookup.getDecimalEncodedValue(VehicleSpeed.key("bike")),
                lookup.getEnumEncodedValue(Smoothness.KEY, Smoothness.class),
                lookup.getDecimalEncodedValue(FerrySpeed.KEY),
                lookup.getEnumEncodedValue(BikeNetwork.KEY, RouteNetwork.class));
    }

    public BikeAverageSpeedParser(DecimalEncodedValue speedEnc,
                                  EnumEncodedValue<Smoothness> smoothnessEnc,
                                  DecimalEncodedValue ferrySpeedEnc,
                                  EnumEncodedValue<RouteNetwork> bikeRouteEnc) {
        super(speedEnc, smoothnessEnc, ferrySpeedEnc, bikeRouteEnc);
    }
}
