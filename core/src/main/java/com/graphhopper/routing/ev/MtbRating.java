package com.graphhopper.routing.ev;

public class MtbRating {
    public static final String KEY = "mtb_rating";

    public static EnumEncodedValue<Rating> create() {
        return new EnumEncodedValue<>(KEY, Rating.class);
    }
}
