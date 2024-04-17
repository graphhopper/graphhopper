package com.graphhopper.routing.ev;

import com.graphhopper.util.Helper;

public enum HighwayFeature {
    MISSING, // no information
    TRAFFIC_SIGNALS, // Lights that control the traffic
    STOP, // A Stop sign
    CROSSING; // A.k.a. crosswalk. Pedestrians can cross a street here; e.g., zebra crossing
    public static final String KEY = "highway_feature";

    public static EnumEncodedValue<HighwayFeature> create() {
        return new EnumEncodedValue<>(HighwayFeature.KEY, HighwayFeature.class);
    }

    @Override
    public String toString() {
        return Helper.toLowerCase(super.toString());
    }

    public static HighwayFeature find(String name) {
        if (name == null)
            return MISSING;
        try {
            return HighwayFeature.valueOf(Helper.toUpperCase(name));
        } catch (IllegalArgumentException ex) {
            return MISSING;
        }
    }
}
