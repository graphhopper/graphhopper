package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.EdgeBytesAccess;
import com.graphhopper.routing.ev.EnumEncodedValue;
import com.graphhopper.routing.ev.HazmatWater;
import com.graphhopper.storage.BytesRef;


public class OSMHazmatWaterParser implements TagParser {

    private final EnumEncodedValue<HazmatWater> hazWaterEnc;

    public OSMHazmatWaterParser(EnumEncodedValue<HazmatWater> hazWaterEnc) {
        this.hazWaterEnc = hazWaterEnc;
    }

    @Override
    public void handleWayTags(int edgeId, EdgeBytesAccess edgeAccess, ReaderWay readerWay, BytesRef relationFlags) {
        if (readerWay.hasTag("hazmat:water", "no")) {
            hazWaterEnc.setEnum(false, edgeId, edgeAccess, HazmatWater.NO);
        } else if (readerWay.hasTag("hazmat:water", "permissive")) {
            hazWaterEnc.setEnum(false, edgeId, edgeAccess, HazmatWater.PERMISSIVE);
        }
    }

}
