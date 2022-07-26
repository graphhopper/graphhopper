package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.EnumEncodedValue;
import com.graphhopper.routing.ev.Hazmat;
import com.graphhopper.storage.IntsRef;

public class OSMHazmatParser implements TagParser {

    private final EnumEncodedValue<Hazmat> hazEnc;

    public OSMHazmatParser(EnumEncodedValue<Hazmat> hazEnc) {
        this.hazEnc = hazEnc;
    }

    @Override
    public IntsRef handleWayTags(IntsRef edgeFlags, ReaderWay readerWay, IntsRef relationFlags) {
        if (readerWay.hasTag("hazmat", "no")) {
            hazEnc.setEnum(false, edgeFlags, Hazmat.NO);
        }
        return edgeFlags;
    }
}
