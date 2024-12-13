package com.graphhopper.routing.util;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.EdgeIntAccess;
import com.graphhopper.routing.util.parsers.TagParser;
import com.graphhopper.storage.IntsRef;

import com.graphhopper.AtGlobals;

public class AtScenicValueCalculator implements TagParser {

    private final DecimalEncodedValue scenicValueEnc;

    public AtScenicValueCalculator(DecimalEncodedValue scenicValueEnc) {
        this.scenicValueEnc = scenicValueEnc;
    }

    @Override
    public void handleWayTags(int edgeId, EdgeIntAccess edgeIntAccess, ReaderWay way, IntsRef relationFlags) {
        long wayId = way.getId();
        Double scenicValue = AtGlobals.scenicValues.get(wayId);
        if (scenicValue != null) {
            // String name = way.getTag("name", null);
            // System.out.println(name + ":" + wayId + " scenicValue: " + scenicValue);
            scenicValueEnc.setDecimal(false, edgeId, edgeIntAccess, scenicValue);
        }
    }
}
