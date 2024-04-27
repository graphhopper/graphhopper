package com.graphhopper.routing.ev;

/**
 * Maximum elevation change in m/100m.
 */
public class MaxSlope {
    public static final String KEY = "max_slope";

    public static DecimalEncodedValue create() {
        return new DecimalEncodedValueImpl(KEY, 5, 0, 1, true, false, false, true);
    }
}
