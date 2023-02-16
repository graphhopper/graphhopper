package com.graphhopper.routing.ev;

/**
 * This boolean is always true except if it is the reverse direction of a oneway.
 *
 * TODO NOW: we could define it as "bike_reverse_oneway" but this might be uglier to use in our parsers and custom model?
 */
public class BikeOneway {
    public static final String KEY = "bike_oneway";

    public static BooleanEncodedValue create() {
        return new SimpleBooleanEncodedValue(KEY, true);
    }
}
