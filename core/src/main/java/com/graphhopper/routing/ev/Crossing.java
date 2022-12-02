package com.graphhopper.routing.ev;

import com.graphhopper.util.Helper;

public enum Crossing {
    MISSING,
    TRAFFIC_SIGNAL, // regulated with light signals
    RAILWAY,
    ZEBRA, // marked with a striped pattern, but no traffic signals
    MARKED, // with crosswalk, with or without traffic lights
    UNMARKED, // without markings or traffic lights
    UNCONTROLLED, // with crosswalk, without traffic lights
    NO; // no crossing is possible/legal
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
