package com.graphhopper.routing.ev;

import com.graphhopper.util.Helper;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;

/**
 * Stores temporary so-called conditional restrictions from access:conditional and other conditional
 * tags affecting cars.
 */
public enum CarAccessConditional {

    MISSING, YES, NO;

    public static final Collection<String> CONDITIONALS = new HashSet<>(Arrays.asList("access:conditional",
            "vehicle:conditional", "motor_vehicle:conditional", "motorcar:conditional"));
    public static final String KEY = "car_access_conditional";

    public static EnumEncodedValue<CarAccessConditional> create() {
        return new EnumEncodedValue<>(KEY, CarAccessConditional.class);
    }

    @Override
    public String toString() {
        return Helper.toLowerCase(super.toString());
    }
}
