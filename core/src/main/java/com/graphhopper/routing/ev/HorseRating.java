package com.graphhopper.routing.ev;

public class HorseRating {
    public static final String KEY = "horse_rating";

    public static IntEncodedValue create() {
        return new UnsignedIntEncodedValue(KEY, 3, false);
    }
}
