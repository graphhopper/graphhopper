package com.graphhopper.routing.ev;

import com.graphhopper.util.Helper;

/**
 * Defines general restrictions for the transport of hazardous materials.<br>
 * If not tagged it will be {@link #YES}
 */
public enum Hazmat {
    YES, NO;

    public static final String KEY = "hazmat";

    private final String name;

    Hazmat() {
        this.name = Helper.toLowerCase(name());
    }

    @Override
    public String toString() {
        return name;
    }
}
