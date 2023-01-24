package com.graphhopper.routing.util.parsers;

import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.EncodedValueLookup;
import com.graphhopper.routing.ev.Roundabout;
import com.graphhopper.routing.ev.VehicleAccess;
import com.graphhopper.util.PMap;

public class MountainBikeAccessParser extends BikeCommonAccessParser {

    public MountainBikeAccessParser(EncodedValueLookup lookup, PMap properties) {
        this(lookup.getBooleanEncodedValue(VehicleAccess.key(properties.getString("name", "mtb"))),
                lookup.getBooleanEncodedValue(Roundabout.KEY));
        blockPrivate(properties.getBool("block_private", true));
        blockFords(properties.getBool("block_fords", false));
    }

    protected MountainBikeAccessParser(BooleanEncodedValue accessEnc, BooleanEncodedValue roundaboutEnc) {
        super(accessEnc, roundaboutEnc);
    }

    @Override
    boolean isSacScaleAllowed(String sacScale) {
        // other scales are too dangerous even for MTB, see http://wiki.openstreetmap.org/wiki/Key:sac_scale
        return "hiking".equals(sacScale) || "mountain_hiking".equals(sacScale)
                || "demanding_mountain_hiking".equals(sacScale) || "alpine_hiking".equals(sacScale);
    }
}
