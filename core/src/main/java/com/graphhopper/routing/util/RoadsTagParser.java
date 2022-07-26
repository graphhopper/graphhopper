package com.graphhopper.routing.util;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.*;
import com.graphhopper.storage.IntsRef;


public class RoadsTagParser extends VehicleTagParser {
    public static final double ROADS_MAX_SPEED = 254;

    public RoadsTagParser(EncodedValueLookup lookup) {
        this(
                lookup.getBooleanEncodedValue(VehicleAccess.key("roads")),
                lookup.getDecimalEncodedValue(VehicleSpeed.key("roads")),
                lookup.getDecimalEncodedValue(TurnCost.key("roads"))
        );
    }

    public RoadsTagParser(BooleanEncodedValue accessEnc, DecimalEncodedValue speedEnc, DecimalEncodedValue turnCostEnc) {
        super(accessEnc, speedEnc, "roads", null, turnCostEnc, TransportationMode.VEHICLE, speedEnc.getNextStorableValue(ROADS_MAX_SPEED));
    }

    @Override
    public IntsRef handleWayTags(IntsRef edgeFlags, ReaderWay way) {
        // let's make it high and let it be reduced in the custom model
        double speed = maxPossibleSpeed;
        accessEnc.setBool(true, edgeFlags, true);
        accessEnc.setBool(false, edgeFlags, true);
        setSpeed(false, edgeFlags, speed);
        if (avgSpeedEnc.isStoreTwoDirections())
            setSpeed(true, edgeFlags, speed);
        return edgeFlags;
    }

    @Override
    public WayAccess getAccess(ReaderWay way) {
        if (way.getTag("highway", "").isEmpty())
            return WayAccess.CAN_SKIP;
        return WayAccess.WAY;
    }

}
