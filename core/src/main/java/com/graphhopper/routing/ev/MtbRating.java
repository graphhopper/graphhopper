package com.graphhopper.routing.ev;

public class MtbRating {
    public static final String KEY = "mtb_rating";

    public static IntEncodedValue create() {
        return new UnsignedIntEncodedValue(KEY, 3, false);
    }
}
