package com.graphhopper.routing.ev;

import com.graphhopper.util.Helper;

public enum Psv {
    MISSING, YES, DESIGNATED, NO;
    public final static String KEY = "psv";

    public static EnumEncodedValue<Psv> create() {
        return new EnumEncodedValue<>(KEY, Psv.class);
    }

    public static Psv find(String name) {
        if (name == null)
            return MISSING;
        try {
            if (name.equals("permissive")) return YES;
            return Psv.valueOf(Helper.toUpperCase(name));
        } catch (IllegalArgumentException ex) {
            return MISSING;
        }
    }
}
