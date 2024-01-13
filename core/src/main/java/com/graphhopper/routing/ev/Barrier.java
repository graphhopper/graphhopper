package com.graphhopper.routing.ev;

import com.graphhopper.util.Helper;

public enum Barrier {
    MISSING,
    BOLLARD,
    KERB,
    STILE,
    LIFT_GATE,
    GATE,
    BLOCK,
    YES;
    // wall and fence is mostly ways https://taginfo.openstreetmap.org/keys/barrier#values

    public static final String KEY = "barrier";

    @Override
    public String toString() {
        return Helper.toLowerCase(super.toString());
    }

    public static Barrier find(String name) {
        if (name == null)
            return MISSING;
        try {
            return Barrier.valueOf(Helper.toUpperCase(name));
        } catch (IllegalArgumentException ex) {
            return MISSING;
        }
    }
}
