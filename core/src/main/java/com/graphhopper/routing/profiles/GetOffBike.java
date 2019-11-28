package com.graphhopper.routing.profiles;

public class GetOffBike {
    public static final String KEY = "off_bike";

    public static BooleanEncodedValue create() {
        return new SimpleBooleanEncodedValue(KEY, false);
    }
}
