package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.EnumEncodedValue;
import com.graphhopper.routing.ev.Hgv;
import com.graphhopper.routing.ev.IntAccess;
import com.graphhopper.storage.IntsRef;

public class OSMHgvParser implements TagParser {
    EnumEncodedValue<Hgv> hgvEnc;

    public OSMHgvParser(EnumEncodedValue<Hgv> hgvEnc) {
        this.hgvEnc = hgvEnc;
    }

    @Override
    public void handleWayTags(int edgeId, IntAccess intAccess, ReaderWay way, IntsRef relationFlags) {
        hgvEnc.setEnum(false, edgeId, intAccess, Hgv.find(way.getTag("hgv")));
    }
}
