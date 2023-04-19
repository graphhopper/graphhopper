package com.graphhopper.routing.ev;

import com.graphhopper.util.Helper;

/**
 * Defines general restrictions for the transport of hazardous materials.<br>
 * If not tagged it will be {@link #YES}
 */
public enum Hazmat {
    YES, NO;

    public static final String KEY = "hazmat";

    public static EnumEncodedValue<Hazmat> create() {
        return new EnumEncodedValue<>(KEY, Hazmat.class);
    }
}
