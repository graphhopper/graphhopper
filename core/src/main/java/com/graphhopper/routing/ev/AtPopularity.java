package com.graphhopper.routing.ev;

public class AtPopularity {
    public static final String KEY = "at_popularity";

    public static DecimalEncodedValue create() {
        // Range is 0.0-10.0
        return new DecimalEncodedValueImpl(KEY, 5, 0.0, 0.3226,
                false, false, false);
    }
}
