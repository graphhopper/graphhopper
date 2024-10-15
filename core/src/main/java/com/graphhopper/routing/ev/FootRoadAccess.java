package com.graphhopper.routing.ev;

import com.graphhopper.util.Helper;

public enum FootRoadAccess {
    MISSING, YES, DESTINATION, DESIGNATED, USE_SIDEPATH, PRIVATE, NO;

    public static final String KEY = "foot_road_access";

    public static EnumEncodedValue<FootRoadAccess> create() {
        return new EnumEncodedValue<>(FootRoadAccess.KEY, FootRoadAccess.class);
    }

    @Override
    public String toString() {
        return Helper.toLowerCase(super.toString());
    }

    public static FootRoadAccess find(String name) {
        if (name == null || name.isEmpty())
            return MISSING;
        if (name.equalsIgnoreCase("permit") || name.equalsIgnoreCase("customers"))
            return PRIVATE;
        try {
            return FootRoadAccess.valueOf(Helper.toUpperCase(name));
        } catch (IllegalArgumentException ex) {
            return YES;
        }
    }
}
