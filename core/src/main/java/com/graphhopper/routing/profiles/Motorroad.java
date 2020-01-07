package com.graphhopper.routing.profiles;

public final class Motorroad {
    public static final String KEY = "motorroad";

    public static BooleanEncodedValue create() {
        return new SimpleBooleanEncodedValue(KEY, false);
    }
    
    private Motorroad() {
        // utility class
    }
}
