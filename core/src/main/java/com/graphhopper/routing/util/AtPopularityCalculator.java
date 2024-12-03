package com.graphhopper.routing.util;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.EdgeIntAccess;
import com.graphhopper.routing.util.parsers.TagParser;
import com.graphhopper.storage.IntsRef;

import com.graphhopper.AtGlobals;

public class AtPopularityCalculator implements TagParser {

    private final DecimalEncodedValue popularityEnc;

    public AtPopularityCalculator(DecimalEncodedValue popularityEnc) {
        this.popularityEnc = popularityEnc;
    }

    @Override
    public void handleWayTags(int edgeId, EdgeIntAccess edgeIntAccess, ReaderWay way, IntsRef relationFlags) {
        long wayId = way.getId();
        Double popularity = AtGlobals.popularities.get(wayId);
        if (popularity != null) {
            // String name = way.getTag("name", null);
            // System.out.println(name + ":" + wayId + " popularity: " + popularity);
            popularityEnc.setDecimal(false, edgeId, edgeIntAccess, popularity);
        }
    }
}
