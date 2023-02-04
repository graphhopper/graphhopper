package com.graphhopper.routing.util.parsers;

import com.graphhopper.routing.ev.*;
import com.graphhopper.core.util.PMap;

public class BikeAverageSpeedParser extends BikeCommonAverageSpeedParser {

    public BikeAverageSpeedParser(EncodedValueLookup lookup, PMap properties) {
        this(lookup.getDecimalEncodedValue(VehicleSpeed.key(properties.getString("name", "bike"))),
                lookup.getEnumEncodedValue(Smoothness.KEY, Smoothness.class));
    }

    public BikeAverageSpeedParser(DecimalEncodedValue speedEnc, EnumEncodedValue<Smoothness> smoothnessEnc) {
        super(speedEnc, smoothnessEnc);
        addPushingSection("path");
    }
}
