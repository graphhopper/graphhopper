package com.graphhopper.routing.ev;

import com.graphhopper.util.Helper;

/**
 * For buses that are acting as public transport vehicle. See also Psv.
 */
public enum Bus {
    MISSING, YES, DESIGNATED, NO;
    public final static String KEY = "bus";

    public static EnumEncodedValue<Bus> create() {
        return new EnumEncodedValue<>(KEY, Bus.class);
    }

    public static Bus find(String name) {
        if (name == null)
            return MISSING;
        try {
            return Bus.valueOf(Helper.toUpperCase(name));
        } catch (IllegalArgumentException ex) {
            return MISSING;
        }
    }
}
