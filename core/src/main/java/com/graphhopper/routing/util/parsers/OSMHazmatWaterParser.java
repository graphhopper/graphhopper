package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.EncodedValue;
import com.graphhopper.routing.ev.EncodedValueLookup;
import com.graphhopper.routing.ev.EnumEncodedValue;
import com.graphhopper.routing.ev.HazmatWater;
import com.graphhopper.storage.IntsRef;

import java.util.List;


public class OSMHazmatWaterParser implements TagParser {

    private final EnumEncodedValue<HazmatWater> hazWaterEnc;

    public OSMHazmatWaterParser() {
        this(new EnumEncodedValue<>(HazmatWater.KEY, HazmatWater.class));
    }

    public OSMHazmatWaterParser(EnumEncodedValue<HazmatWater> hazWaterEnc) {
        this.hazWaterEnc = hazWaterEnc;
    }

    @Override
    public void createEncodedValues(EncodedValueLookup lookup, List<EncodedValue> registerNewEncodedValue) {
        registerNewEncodedValue.add(hazWaterEnc);
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
