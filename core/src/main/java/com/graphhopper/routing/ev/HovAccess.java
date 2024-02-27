package com.graphhopper.routing.ev;

public class HovAccess {
    public final static String KEY = "hov_access";

    public static BooleanEncodedValue create() {
        return new SimpleBooleanEncodedValue(KEY, true);
    }
}
