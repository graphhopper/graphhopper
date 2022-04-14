package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.EncodedValue;
import com.graphhopper.routing.ev.EncodedValueLookup;
import com.graphhopper.routing.ev.EnumEncodedValue;
import com.graphhopper.routing.ev.Hazmat;
import com.graphhopper.storage.IntsRef;

import java.util.List;

public class OSMHazmatParser implements TagParser {

    private final EnumEncodedValue<Hazmat> hazEnc;

    public OSMHazmatParser() {
        this(new EnumEncodedValue<>(Hazmat.KEY, Hazmat.class));
    }

    public OSMHazmatParser(EnumEncodedValue<Hazmat> hazEnc) {
        this.hazEnc = hazEnc;
    }

    @Override
    public void createEncodedValues(EncodedValueLookup lookup, List<EncodedValue> registerNewEncodedValue) {
        registerNewEncodedValue.add(hazEnc);
    }

    @Override
    public IntsRef handleWayTags(IntsRef edgeFlags, ReaderWay readerWay, IntsRef relationFlags) {
        if (readerWay.hasTag("hazmat", "no")) {
            hazEnc.setEnum(false, edgeFlags, Hazmat.NO);
        }
        return edgeFlags;
    }
}
