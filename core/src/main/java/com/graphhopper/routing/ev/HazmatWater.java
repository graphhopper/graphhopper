package com.graphhopper.routing.ev;

import com.graphhopper.util.Helper;

/**
 * Defines general restrictions for the transport of goods through water protection areas.<br>
 * If not tagged it will be {@link #YES}
 */
public enum HazmatWater {
    YES, PERMISSIVE, NO;

    public static final String KEY = "hazmat_water";

    private final String name;

    HazmatWater() {
        this.name = Helper.toLowerCase(name());
    }

    @Override
    public String toString() {
        return name;
    }
}
