package com.graphhopper.routing.util.parsers;

import com.graphhopper.routing.ev.*;
import com.graphhopper.util.PMap;

public class BikeAverageSpeedParser extends BikeCommonAverageSpeedParser {

    public BikeAverageSpeedParser(EncodedValueLookup lookup, PMap properties) {
        this(lookup.getDecimalEncodedValue(properties.getString("name", VehicleSpeed.key("bike"))),
                lookup.getEnumEncodedValue(Smoothness.KEY, Smoothness.class));
    }

    public BikeAverageSpeedParser(DecimalEncodedValue speedEnc, EnumEncodedValue<Smoothness> smoothnessEnc) {
        super(speedEnc, smoothnessEnc);
        addPushingSection("path");
    }
}
