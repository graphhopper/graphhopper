package com.graphhopper.routing.ev;

import com.graphhopper.util.Helper;

/**
 * Stores temporary so-called conditional restrictions from access:conditional and other conditional
 * tags affecting cars and motor vehicles.
 */
public class CarTemporaryRestriction {

    public static final String KEY = "car_temporary_restriction";

    public static BooleanEncodedValue create() {
        return new SimpleBooleanEncodedValue(KEY, true);
    }

    @Override
    public String toString() {
        return Helper.toLowerCase(super.toString());
    }
}
