package com.graphhopper.routing.ev;

public class Curvature {
    public static final String KEY = "curvature";

    public static DecimalEncodedValue create() {
        return new DecimalEncodedValueImpl(KEY, 4, 24 * 0.025 /* == 0.6000000001 */, 0.025,
                false, false, false);
    }
}
