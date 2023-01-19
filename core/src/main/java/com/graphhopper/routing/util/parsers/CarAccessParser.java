package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.util.TransportationMode;
import com.graphhopper.storage.IntsRef;

public class CarAccessParser extends GenericAccessParser implements TagParser {

    protected CarAccessParser(BooleanEncodedValue accessEnc, DecimalEncodedValue speedEnc, BooleanEncodedValue roundaboutEnc, TransportationMode transportationMode, double maxPossibleSpeed) {
        super(accessEnc, speedEnc, roundaboutEnc, transportationMode, maxPossibleSpeed);
    }

    @Override
    protected IntsRef handleWayTags(IntsRef edgeFlags, ReaderWay way) {
        return null;
    }
}
