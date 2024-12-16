package com.graphhopper.routing.ev;

public class MaxSpeedEstimated {
    public static final String KEY = "max_speed_estimated";

    public static BooleanEncodedValue create() {
        return new SimpleBooleanEncodedValue(KEY);
    }
}
