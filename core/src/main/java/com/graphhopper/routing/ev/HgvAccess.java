package com.graphhopper.routing.ev;

public class HgvAccess {
    public final static String KEY = "hgv_access";

    public static BooleanEncodedValue create() {
        return new SimpleBooleanEncodedValue(KEY, true);
    }

}
