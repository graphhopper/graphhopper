package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.EdgeIntAccess;
import com.graphhopper.routing.ev.EnumEncodedValue;
import com.graphhopper.routing.ev.Hov;
import com.graphhopper.storage.IntsRef;

public class OSMHovParser implements TagParser {
    EnumEncodedValue<Hov> hovEnc;

    public OSMHovParser(EnumEncodedValue<Hov> hovEnc) {
        this.hovEnc = hovEnc;
    }

    @Override
    public void handleWayTags(int edgeId, EdgeIntAccess edgeIntAccess, ReaderWay way, IntsRef relationFlags) {
        String surfaceTag = way.getTag("hov");
        Hov hov = Hov.find(surfaceTag);
        if (hov != Hov.MISSING)
            hovEnc.setEnum(false, edgeId, edgeIntAccess, hov);
    }
}
