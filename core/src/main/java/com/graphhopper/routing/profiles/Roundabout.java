package com.graphhopper.routing.profiles;

public class Roundabout {
    public static final String KEY = "roundabout";

    public static BooleanEncodedValue create() {
        return new SimpleBooleanEncodedValue(KEY, false);
    }
}
