package com.graphhopper.routing.ev;

import com.graphhopper.util.Helper;

public enum FootRoadAccess {
    YES, DESTINATION, CUSTOMERS, DESIGNATED, USE_SIDEPATH, PRIVATE, NO;

    public static final String KEY = "foot_road_access";

    public static EnumEncodedValue<FootRoadAccess> create() {
        return new EnumEncodedValue<>(FootRoadAccess.KEY, FootRoadAccess.class);
    }

    @Override
    public String toString() {
        return Helper.toLowerCase(super.toString());
    }

    public static FootRoadAccess find(String name) {
        if (name == null)
            return YES;
        if (name.equalsIgnoreCase("permit"))
            return PRIVATE;
        try {
            return FootRoadAccess.valueOf(Helper.toUpperCase(name));
        } catch (IllegalArgumentException ex) {
            return YES;
        }
    }
}
