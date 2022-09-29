package com.graphhopper.routing.ev;

public class Curvature {
    public static final String KEY = "curvature";

    public static DecimalEncodedValue create() {
        // for now save a bit: ignore all too low values and set them to the minimum value instead
        return new DecimalEncodedValueImpl(KEY, 4, 0.25, 0.05,
                false, false, false);
    }
}
