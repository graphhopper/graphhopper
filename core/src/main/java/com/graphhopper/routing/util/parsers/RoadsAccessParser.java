package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.EncodedValueLookup;
import com.graphhopper.routing.ev.VehicleAccess;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.core.util.PMap;

public class RoadsAccessParser implements TagParser {
    private final BooleanEncodedValue accessEnc;

    public RoadsAccessParser(EncodedValueLookup lookup, PMap properties) {
        this(lookup.getBooleanEncodedValue(VehicleAccess.key(properties.getString("name", "roads"))));
    }

    public RoadsAccessParser(BooleanEncodedValue accessEnc) {
        this.accessEnc = accessEnc;
    }

    @Override
    public void handleWayTags(IntsRef edgeFlags, ReaderWay way, IntsRef relationFlags) {
        accessEnc.setBool(true, edgeFlags, true);
        accessEnc.setBool(false, edgeFlags, true);
    }

    @Override
    public String toString() {
        return accessEnc.getName();
    }
}
