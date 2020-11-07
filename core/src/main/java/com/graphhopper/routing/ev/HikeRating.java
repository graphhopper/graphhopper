package com.graphhopper.routing.ev;

public class HikeRating {
    public static final String KEY = "hike_rating";

    public static EnumEncodedValue<Rating> create() {
        return new EnumEncodedValue<>(KEY, Rating.class);
    }
}
