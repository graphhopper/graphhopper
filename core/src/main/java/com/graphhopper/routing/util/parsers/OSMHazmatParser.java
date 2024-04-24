package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.EdgeBytesAccess;
import com.graphhopper.routing.ev.EnumEncodedValue;
import com.graphhopper.routing.ev.Hazmat;
import com.graphhopper.storage.BytesRef;

public class OSMHazmatParser implements TagParser {

    private final EnumEncodedValue<Hazmat> hazEnc;

    public OSMHazmatParser(EnumEncodedValue<Hazmat> hazEnc) {
        this.hazEnc = hazEnc;
    }

    @Override
    public void handleWayTags(int edgeId, EdgeBytesAccess edgeAccess, ReaderWay readerWay, BytesRef relationFlags) {
        if (readerWay.hasTag("hazmat", "no"))
            hazEnc.setEnum(false, edgeId, edgeAccess, Hazmat.NO);
    }
}
