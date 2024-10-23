package com.graphhopper.routing.util.parsers;

import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.EncodedValueLookup;
import com.graphhopper.routing.ev.Roundabout;
import com.graphhopper.routing.ev.VehicleAccess;
import com.graphhopper.util.PMap;

public class RacingBikeAccessParser extends BikeCommonAccessParser {

    public RacingBikeAccessParser(EncodedValueLookup lookup, PMap properties) {
        this(lookup.getBooleanEncodedValue(VehicleAccess.key("racingbike")),
                lookup.getBooleanEncodedValue(Roundabout.KEY));
        check(properties);
    }

    protected RacingBikeAccessParser(BooleanEncodedValue accessEnc, BooleanEncodedValue roundaboutEnc) {
        super(accessEnc, roundaboutEnc);

        barriers.add("kissing_gate");
        barriers.add("stile");
        barriers.add("turnstile");
    }
}
