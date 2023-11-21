package com.graphhopper.routing.ev;

import com.graphhopper.util.Helper;

public enum Motorcycle {
    MISSING, YES, DESIGNATED, DESTINATION, PRIVATE, NO;
    public final static String KEY = "motorcycle";

    public static EnumEncodedValue<Motorcycle> create() {
        return new EnumEncodedValue<>(KEY, Motorcycle.class);
    }

    public static Motorcycle find(String name) {
        if (name == null)
            return MISSING;
        try {
            if (name.equals("permissive")) return YES;
            return Motorcycle.valueOf(Helper.toUpperCase(name));
        } catch (IllegalArgumentException ex) {
            return MISSING;
        }
    }
}
