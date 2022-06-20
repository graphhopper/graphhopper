package com.graphhopper.routing.util;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.EncodedValueLookup;
import com.graphhopper.routing.ev.TurnCost;
import com.graphhopper.storage.IntsRef;

import static com.graphhopper.routing.util.EncodingManager.getKey;

public class RoadsTagParser extends VehicleTagParser {
    public static final double ROADS_MAX_SPEED = 254;

    public RoadsTagParser(EncodedValueLookup lookup) {
        this(
                lookup.getBooleanEncodedValue(getKey("roads", "access")),
                lookup.getDecimalEncodedValue(getKey("roads", "average_speed")),
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
