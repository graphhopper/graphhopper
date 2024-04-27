package com.graphhopper.routing.ev;

/**
 * Average elevation. Will be negated in reverse direction.
 */
public class AverageSlope {
    public static final String KEY = "average_slope";

    public static DecimalEncodedValue create() {
        return new DecimalEncodedValueImpl(KEY, 5, 0, 1, true, false, false, true);
    }
}
