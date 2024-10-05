package com.graphhopper.routing.ev;

import com.graphhopper.util.Helper;

import java.util.Arrays;
import java.util.List;

public enum BikeRoadAccess {
    YES, DESTINATION, CUSTOMERS, DESIGNATED, USE_SIDEPATH, PRIVATE, DISMOUNT, NO;

    public static final String KEY = "bike_road_access";

    /**
     * The access restriction list returned from OSMRoadAccessParser.toOSMRestrictions(TransportationMode.Bike)
     * does not contain "vehicle" to still allow walking, via 'dismount' (#2981). But to allow
     * walking via dismount in case of vehicle=private we need bike_road_access == PRIVATE. This
     * also allows us to limit speed to 5km/h if foot_road_access == YES. See
     * <a href="https://www.openstreetmap.org/way/1058548816">this way</a>.
     */
    public static final List<String> RESTRICTIONS = Arrays.asList("bicycle", "vehicle", "access");

    public static EnumEncodedValue<BikeRoadAccess> create() {
        return new EnumEncodedValue<>(BikeRoadAccess.KEY, BikeRoadAccess.class);
    }

    @Override
    public String toString() {
        return Helper.toLowerCase(super.toString());
    }

    public static BikeRoadAccess find(String name) {
        if (name == null)
            return YES;
        if (name.equalsIgnoreCase("permit"))
            return PRIVATE;
        try {
            return BikeRoadAccess.valueOf(Helper.toUpperCase(name));
        } catch (IllegalArgumentException ex) {
            return YES;
        }
    }
}
