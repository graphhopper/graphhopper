package com.graphhopper.routing.ev;

public class HorseRating {
    public static final String KEY = "horse_rating";

    public static IntEncodedValue create() {
        return new SignedIntEncodedValue(KEY, 3, 0, false);
    }
}
