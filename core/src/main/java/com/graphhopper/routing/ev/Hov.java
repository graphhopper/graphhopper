package com.graphhopper.routing.ev;

import com.graphhopper.util.Helper;

public enum Hov {
    MISSING, YES, DESIGNATED, NO;

    public static final String KEY = "hov";

    public static EnumEncodedValue<Hov> create() {
        return new EnumEncodedValue<>(Hov.KEY, Hov.class);
    }

    @Override
    public String toString() {
        return Helper.toLowerCase(super.toString());
    }

    public static Hov find(String name) {
        if (name == null)
            return MISSING;
        try {
            return Hov.valueOf(Helper.toUpperCase(name));
        } catch (IllegalArgumentException ex) {
            return MISSING;
        }
    }
}
