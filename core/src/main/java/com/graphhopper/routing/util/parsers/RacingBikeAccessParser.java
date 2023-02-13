package com.graphhopper.routing.util.parsers;

import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.EncodedValueLookup;
import com.graphhopper.routing.ev.Roundabout;
import com.graphhopper.routing.ev.VehicleAccess;
import com.graphhopper.util.PMap;

public class RacingBikeAccessParser extends BikeCommonAccessParser {

    public RacingBikeAccessParser(EncodedValueLookup lookup, PMap properties) {
        this(lookup.getBooleanEncodedValue(VehicleAccess.key(properties.getString("name", "racingbike"))));
        blockPrivate(properties.getBool("block_private", true));
        blockFords(properties.getBool("block_fords", false));
    }

    protected RacingBikeAccessParser(BooleanEncodedValue accessEnc) {
        super(accessEnc);

        barriers.add("kissing_gate");
        barriers.add("stile");
        barriers.add("turnstile");
    }
}
