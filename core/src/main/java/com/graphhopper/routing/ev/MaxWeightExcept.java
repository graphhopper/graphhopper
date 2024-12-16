package com.graphhopper.routing.ev;

import com.graphhopper.util.Helper;

/**
 * When the max_weight EncodedValue is not legally binding. E.g. if there is a sign that a delivery vehicle can access
 * the road (even if larger than maxweight tag) then DELIVERY of this enum will be set.
 */
public enum MaxWeightExcept {

    MISSING, DELIVERY, DESTINATION, FORESTRY;

    public static final String KEY = "max_weight_except";

    public static EnumEncodedValue<MaxWeightExcept> create() {
        return new EnumEncodedValue<>(MaxWeightExcept.KEY, MaxWeightExcept.class);
    }

    @Override
    public String toString() {
        return Helper.toLowerCase(super.toString());
    }

    public static MaxWeightExcept find(String name) {
        if (name == null || name.isEmpty())
            return MISSING;

        // "maxweight:conditional=none @ private" is rare and seems to be known from a few mappers only
        if (name.equalsIgnoreCase("permit") || name.equalsIgnoreCase("private"))
            return DELIVERY;

        try {
            return MaxWeightExcept.valueOf(Helper.toUpperCase(name));
        } catch (IllegalArgumentException ex) {
            return MISSING;
        }
    }
}
