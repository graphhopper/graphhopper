package com.graphhopper.routing.ev;

public class HorseRating {
    public static final String KEY = "horse_rating";

    public static EnumEncodedValue<Rating> create() {
        return new EnumEncodedValue<>(KEY, Rating.class);
    }
}
