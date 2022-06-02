package com.graphhopper.routing.util.parsers;

import java.util.Collections;
import java.util.List;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.EnumEncodedValue;
import com.graphhopper.routing.ev.HazmatWater;
import com.graphhopper.storage.IntsRef;


public class OSMHazmatWaterParser implements TagParser {

    private final EnumEncodedValue<HazmatWater> hazWaterEnc;

    public OSMHazmatWaterParser(EnumEncodedValue<HazmatWater> hazWaterEnc) {
        this.hazWaterEnc = hazWaterEnc;
    }
    
    @Override
    public List<String> getProvidedEncodedValues() {
        return Collections.singletonList(hazWaterEnc.getName());
    }

    @Override
    public IntsRef handleWayTags(IntsRef edgeFlags, ReaderWay readerWay, IntsRef relationFlags) {
        if (readerWay.hasTag("hazmat:water", "no")) {
            hazWaterEnc.setEnum(false, edgeFlags, HazmatWater.NO);
        } else if (readerWay.hasTag("hazmat:water", "permissive")) {
            hazWaterEnc.setEnum(false, edgeFlags, HazmatWater.PERMISSIVE);
        }
        return edgeFlags;
    }

}
