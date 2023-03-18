package com.graphhopper.routing.ev;

import com.graphhopper.util.Helper;

public enum Crossing {
    MISSING, // no information
    RAILWAY_BARRIER, // railway crossing with barrier
    RAILWAY, // railway crossing with road
    TRAFFIC_SIGNALS, // with light signals
    UNCONTROLLED, // with crosswalk, without traffic lights
    MARKED, // with crosswalk, with or without traffic lights
    UNMARKED, // without markings or traffic lights
    NO; // crossing is impossible or illegal
    public static final String KEY = "crossing";

    @Override
    public String toString() {
        return Helper.toLowerCase(name());
    }

    public static Crossing find(String name) {
        if (name == null)
            return MISSING;
        try {
            return Crossing.valueOf(Helper.toUpperCase(name));
        } catch (IllegalArgumentException ex) {
            return MISSING;
        }
    }
}
