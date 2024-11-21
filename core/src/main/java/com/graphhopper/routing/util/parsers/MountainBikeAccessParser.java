package com.graphhopper.routing.util.parsers;

import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.EncodedValueLookup;
import com.graphhopper.routing.ev.Roundabout;
import com.graphhopper.routing.ev.VehicleAccess;
import com.graphhopper.util.PMap;

public class MountainBikeAccessParser extends BikeCommonAccessParser {

    public MountainBikeAccessParser(EncodedValueLookup lookup, PMap properties) {
        this(lookup.getBooleanEncodedValue(VehicleAccess.key("mtb")),
                lookup.getBooleanEncodedValue(Roundabout.KEY));
        check(properties);
    }

    protected MountainBikeAccessParser(BooleanEncodedValue accessEnc, BooleanEncodedValue roundaboutEnc) {
        super(accessEnc, roundaboutEnc);
    }

}
