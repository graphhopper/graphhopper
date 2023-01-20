package com.graphhopper.routing.util.parsers;

import com.graphhopper.routing.ev.*;
import com.graphhopper.util.PMap;

public class BikeAccessParser extends BikeCommonAccessParser {

    public BikeAccessParser(EncodedValueLookup lookup, PMap properties) {
        this(lookup.getBooleanEncodedValue(properties.getString("name", VehicleAccess.key("bike"))),
                lookup.getBooleanEncodedValue(Roundabout.KEY));
        blockPrivate(properties.getBool("block_private", true));
        blockFords(properties.getBool("block_fords", false));
    }

    public BikeAccessParser(BooleanEncodedValue accessEnc, BooleanEncodedValue roundaboutEnc) {
        super(accessEnc, roundaboutEnc);
        barriers.add("kissing_gate");
        barriers.add("stile");
        barriers.add("turnstile");
    }
}
