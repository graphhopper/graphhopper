package com.graphhopper.routing.ev;

public class FerrySpeed {
    public static final String KEY = "ferry_speed";

    public static DecimalEncodedValue create() {
        return new DecimalEncodedValueImpl(KEY, 5, 2, false);
    }
}
