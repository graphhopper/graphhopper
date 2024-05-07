package com.graphhopper.routing.ev;

public class BusAccess {
    public final static String KEY = "bus_access";

    public static BooleanEncodedValue create() {
        return new SimpleBooleanEncodedValue(KEY, true);
    }

}
