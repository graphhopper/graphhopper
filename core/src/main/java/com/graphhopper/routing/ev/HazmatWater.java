package com.graphhopper.routing.ev;

import com.graphhopper.util.Helper;

/**
 * Defines general restrictions for the transport of goods through water protection areas.<br>
 * If not tagged it will be {@link #YES}
 */
public enum HazmatWater {
    YES, PERMISSIVE, NO;

    public static final String KEY = "hazmat_water";

    public static EnumEncodedValue<HazmatWater> create() {
        return new EnumEncodedValue<>(KEY, HazmatWater.class);
    }

    @Override
    public String toString() {
        return Helper.toLowerCase(super.toString());
    }
}
