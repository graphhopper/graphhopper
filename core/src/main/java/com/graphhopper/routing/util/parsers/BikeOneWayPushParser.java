package com.graphhopper.routing.util.parsers;

import com.graphhopper.routing.ev.*;
import com.graphhopper.util.PMap;

public class BikeOneWayPushParser extends BikeCommonOneWayPushParser {

    public BikeOneWayPushParser(EncodedValueLookup lookup, PMap properties) {
        this(
                lookup.getBooleanEncodedValue(VehicleAccess.key(properties.getString("name", "bike"))),
                lookup.getDecimalEncodedValue(VehicleSpeed.key(properties.getString("name", "bike")))
        );
    }

    public BikeOneWayPushParser(BooleanEncodedValue accessEnc,
                                DecimalEncodedValue speedEnc) {
        super(accessEnc, speedEnc);

        addPushingSection("path");

    }
}
