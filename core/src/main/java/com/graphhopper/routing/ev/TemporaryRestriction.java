package com.graphhopper.routing.ev;

import com.graphhopper.util.Helper;

public class TemporaryRestriction {

    public static final String KEY = "temporary_restriction";

    public static BooleanEncodedValue create() {
        return new SimpleBooleanEncodedValue(KEY, true);
    }

    @Override
    public String toString() {
        return Helper.toLowerCase(super.toString());
    }
}
