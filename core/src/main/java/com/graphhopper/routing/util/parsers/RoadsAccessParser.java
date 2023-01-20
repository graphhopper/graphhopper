package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderNode;
import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.EncodedValueLookup;
import com.graphhopper.routing.ev.VehicleAccess;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.PMap;

import java.util.Map;

import static java.util.Collections.emptyMap;

public class RoadsAccessParser implements TagParser {
    private final BooleanEncodedValue accessEnc;

    public RoadsAccessParser(EncodedValueLookup lookup, PMap properties) {
        this(lookup.getBooleanEncodedValue(properties.getString("name", VehicleAccess.key("roads"))));
    }

    public RoadsAccessParser(BooleanEncodedValue accessEnc) {
        this.accessEnc = accessEnc;
    }

    @Override
    public IntsRef handleWayTags(IntsRef edgeFlags, ReaderWay way, IntsRef relationFlags) {
        accessEnc.setBool(true, edgeFlags, true);
        accessEnc.setBool(false, edgeFlags, true);
        return edgeFlags;
    }
}
