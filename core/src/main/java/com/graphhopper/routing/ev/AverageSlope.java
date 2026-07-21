package com.graphhopper.routing.ev;

/**
 * Average elevation. Will be negated in reverse direction.
 */
public class AverageSlope {
    public static final String KEY = "average_slope";

    public static DecimalEncodedValue create() {
        return new DecimalEncodedValueImpl(KEY, 6, 0, 0.5,
                true, false, false);
    }
}
