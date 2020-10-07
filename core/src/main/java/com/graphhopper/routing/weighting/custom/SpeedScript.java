package com.graphhopper.routing.weighting.custom;

import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.EncodedValueLookup;
import com.graphhopper.routing.util.CustomModel;
import com.graphhopper.util.EdgeIteratorState;

public abstract class SpeedScript implements EdgeToValueEntry {

    public SpeedScript() {
    }

    public static SpeedScript create(double maxSpeed, CustomModel customModel, DecimalEncodedValue avgSpeedEnc, EncodedValueLookup lookup) {
        return new SpeedScript() {
            @Override
            public double getValue(EdgeIteratorState edge, boolean reverse) {
                double speed = reverse ? edge.getReverse(avgSpeedEnc) : edge.get(avgSpeedEnc);
                if (Double.isInfinite(speed) || Double.isNaN(speed) || speed < 0)
                    throw new IllegalStateException("Invalid estimated speed " + speed);
                return speed;
            }
        };
    }

    public double getMaxSpeed() {
        return 0;
    }
}
