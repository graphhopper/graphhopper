package com.graphhopper.routing.profiles.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.profiles.BooleanEncodedValue;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.IntsRef;

public class RoundaboutParser extends AbstractTagParser {
    private final BooleanEncodedValue roundaboutEnc;

    public RoundaboutParser() {
        super(EncodingManager.ROUNDABOUT);
        this.roundaboutEnc = new BooleanEncodedValue(EncodingManager.ROUNDABOUT, false);
    }

    public BooleanEncodedValue getEnc() {
        return roundaboutEnc;
    }

    @Override
    public IntsRef handleWayTags(IntsRef edgeFlags, ReaderWay way, long allowed, long relationFlags) {
        boolean isRoundabout = way.hasTag("junction", "roundabout") || way.hasTag("junction", "circular");
        if (isRoundabout)
            roundaboutEnc.setBool(false, edgeFlags, true);
        return edgeFlags;
    }
}
