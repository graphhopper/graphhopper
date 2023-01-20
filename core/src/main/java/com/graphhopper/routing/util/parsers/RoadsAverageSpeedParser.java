package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.EncodedValueLookup;
import com.graphhopper.routing.ev.VehicleSpeed;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.PMap;

import static com.graphhopper.routing.util.RoadsTagParser.ROADS_MAX_SPEED;

public class RoadsAverageSpeedParser implements TagParser {
    private final DecimalEncodedValue avgSpeedEnc;
    private final double maxPossibleSpeed;

    public RoadsAverageSpeedParser(EncodedValueLookup lookup, PMap properties) {
        this(lookup.getDecimalEncodedValue(properties.getString("name", "")));
    }

    public RoadsAverageSpeedParser(DecimalEncodedValue avgSpeedEnc) {
        this.avgSpeedEnc = avgSpeedEnc;
        this.maxPossibleSpeed = this.avgSpeedEnc.getNextStorableValue(ROADS_MAX_SPEED);
    }

    @Override
    public IntsRef handleWayTags(IntsRef edgeFlags, ReaderWay way, IntsRef relationFlags) {
        // let's make it high and let it be reduced in the custom model
        avgSpeedEnc.setDecimal(false, edgeFlags, maxPossibleSpeed);
        if (avgSpeedEnc.isStoreTwoDirections())
            avgSpeedEnc.setDecimal(true, edgeFlags, maxPossibleSpeed);
        return edgeFlags;
    }
}
