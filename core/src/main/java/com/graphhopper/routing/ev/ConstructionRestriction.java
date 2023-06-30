package com.graphhopper.routing.ev;

import com.graphhopper.util.Helper;

public class ConstructionRestriction {

    public static final String KEY = "construction_restriction";

    public static BooleanEncodedValue create() {
        return new SimpleBooleanEncodedValue(KEY, true);
    }

    @Override
    public String toString() {
        return Helper.toLowerCase(super.toString());
    }
}
