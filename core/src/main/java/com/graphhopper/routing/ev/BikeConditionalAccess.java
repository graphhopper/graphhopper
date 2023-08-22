package com.graphhopper.routing.ev;

import com.graphhopper.util.Helper;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;

/**
 * Stores temporary so-called conditional restrictions from access:conditional and other conditional
 * tags affecting bikes.
 */
public enum BikeConditionalAccess {

    MISSING, YES, NO;

    public static final Collection<String> CONDITIONALS = new HashSet<>(Arrays.asList("access:conditional",
            "vehicle:conditional", "bicycle:conditional"));
    public static final String KEY = "bike_conditional_access";

    public static EnumEncodedValue<BikeConditionalAccess> create() {
        return new EnumEncodedValue<>(KEY, BikeConditionalAccess.class);
    }

    @Override
    public String toString() {
        return Helper.toLowerCase(super.toString());
    }
}
