package com.graphhopper.routing.util;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.storage.IntsRef;

public class RoadsFlagEncoder extends AbstractFlagEncoder {

    public RoadsFlagEncoder() {
        super("roads", 7, 2, true, 3);
        maxPossibleSpeed = avgSpeedEnc.getNextStorableValue(254);
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
    public EncodingManager.Access getAccess(ReaderWay way) {
        if (way.getTag("highway", "").isEmpty())
            return EncodingManager.Access.CAN_SKIP;
        return EncodingManager.Access.WAY;
    }

    @Override
    public TransportationMode getTransportationMode() {
        return TransportationMode.VEHICLE;
    }

}
