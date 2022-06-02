package com.graphhopper.routing.util.parsers;

import java.util.Collections;
import java.util.List;

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
    public List<String> getProvidedEncodedValues() {
        return Collections.singletonList(hazEnc.getName());
    }

    @Override
    public IntsRef handleWayTags(IntsRef edgeFlags, ReaderWay readerWay, IntsRef relationFlags) {
        if (readerWay.hasTag("hazmat", "no")) {
            hazEnc.setEnum(false, edgeFlags, Hazmat.NO);
        }
        return edgeFlags;
    }
}
