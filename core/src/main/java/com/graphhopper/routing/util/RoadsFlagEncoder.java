package com.graphhopper.routing.util;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.DecimalEncodedValueImpl;
import com.graphhopper.routing.ev.EncodedValue;
import com.graphhopper.storage.IntsRef;

import java.util.List;

public class RoadsFlagEncoder extends AbstractFlagEncoder {

    final boolean speedTwoDirections = true;

    public RoadsFlagEncoder() {
        super(7, 2, 3);
        avgSpeedEnc = new DecimalEncodedValueImpl(EncodingManager.getKey(getName(), "average_speed"), speedBits, speedFactor, speedTwoDirections);
        maxPossibleSpeed = avgSpeedEnc.getNextStorableValue(254);
    }

    @Override
    public void createEncodedValues(List<EncodedValue> registerNewEncodedValue) {
        super.createEncodedValues(registerNewEncodedValue);
        registerNewEncodedValue.add(avgSpeedEnc);
    }

    @Override
    public IntsRef handleWayTags(IntsRef edgeFlags, ReaderWay way) {
        // let's make it high and let it be reduced in the custom model
        double speed = maxPossibleSpeed;
        accessEnc.setBool(true, edgeFlags, true);
        accessEnc.setBool(false, edgeFlags, true);
        setSpeed(false, edgeFlags, speed);
        if (speedTwoDirections)
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

    @Override
    public String getName() {
        return "roads";
    }
}
