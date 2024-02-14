package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.EdgeIntAccess;
import com.graphhopper.routing.ev.EncodedValueLookup;
import com.graphhopper.routing.ev.VehicleAccess;
import com.graphhopper.storage.IntsRef;

/**
 * Access parser (boolean) for the 'roads' vehicle. Not to be confused with OSMRoadAccessParser that fills road_access
 * enum (for car).
 */
public class RoadsAccessParser implements TagParser {
    private final BooleanEncodedValue accessEnc;

    public RoadsAccessParser(EncodedValueLookup lookup) {
        this(lookup.getBooleanEncodedValue(VehicleAccess.key("roads")));
    }

    public RoadsAccessParser(BooleanEncodedValue accessEnc) {
        this.accessEnc = accessEnc;
    }

    @Override
    public void handleWayTags(int edgeId, EdgeIntAccess edgeIntAccess, ReaderWay way, IntsRef relationFlags) {
        accessEnc.setBool(true, edgeId, edgeIntAccess, true);
        accessEnc.setBool(false, edgeId, edgeIntAccess, true);
    }

    @Override
    public String toString() {
        return accessEnc.getName();
    }
}
