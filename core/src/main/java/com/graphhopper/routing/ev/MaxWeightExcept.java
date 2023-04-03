package com.graphhopper.routing.ev;

import java.util.Locale;

/**
 * When the max_weight EncodedValue is not legally binding. E.g. if there is a sign that a delivery vehicle can access
 * the road (even if larger than maxweight tag) then DELIVERY of this enum will be set.
 */
public enum MaxWeightExcept {

    NONE, DELIVERY, DESTINATION, FORESTRY;

    public static final String KEY = "max_weight_except";

    public static EnumEncodedValue<MaxWeightExcept> create() {
        return new EnumEncodedValue<>(MaxWeightExcept.KEY, MaxWeightExcept.class);
    }

    @Override
    public String toString() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static MaxWeightExcept find(String name) {
        if (name == null || name.isEmpty())
            return NONE;

        for (MaxWeightExcept mwe : values()) {
            if (mwe.name().equalsIgnoreCase(name))
                return mwe;
        }

        return NONE;
    }
}
