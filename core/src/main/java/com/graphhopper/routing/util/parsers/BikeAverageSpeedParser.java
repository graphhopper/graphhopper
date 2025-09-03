package com.graphhopper.routing.util.parsers;

import com.graphhopper.routing.ev.*;

public class BikeAverageSpeedParser extends BikeCommonAverageSpeedParser {

    public BikeAverageSpeedParser(EncodedValueLookup lookup) {
        this(lookup.getDecimalEncodedValue(VehicleSpeed.key("bike")),
                lookup.getEnumEncodedValue(Smoothness.KEY, Smoothness.class),
                lookup.getDecimalEncodedValue(FerrySpeed.KEY));
    }

    public BikeAverageSpeedParser(DecimalEncodedValue speedEnc, EnumEncodedValue<Smoothness> smoothnessEnc, DecimalEncodedValue ferrySpeedEnc) {
        super(speedEnc, smoothnessEnc, ferrySpeedEnc);
    }
}
