package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.EdgeIntAccess;
import com.graphhopper.routing.ev.EncodedValueLookup;
import com.graphhopper.routing.ev.VehicleSpeed;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.PMap;

public class RoadsAverageSpeedParser implements TagParser {
    private final DecimalEncodedValue avgSpeedEnc;
    private final double maxPossibleSpeed;

    public RoadsAverageSpeedParser(EncodedValueLookup lookup, PMap properties) {
        this(lookup.getDecimalEncodedValue(VehicleSpeed.key("roads")));
    }

    public RoadsAverageSpeedParser(DecimalEncodedValue avgSpeedEnc) {
        this.avgSpeedEnc = avgSpeedEnc;
        this.maxPossibleSpeed = this.avgSpeedEnc.getMaxStorableDecimal();
    }

    @Override
    public void handleWayTags(int edgeId, EdgeIntAccess edgeIntAccess, ReaderWay way, IntsRef relationFlags) {
        // let's make it high and let it be reduced in the custom model
        avgSpeedEnc.setDecimal(false, edgeId, edgeIntAccess, maxPossibleSpeed);
        if (avgSpeedEnc.isStoreTwoDirections())
            avgSpeedEnc.setDecimal(true, edgeId, edgeIntAccess, maxPossibleSpeed);
    }
}
