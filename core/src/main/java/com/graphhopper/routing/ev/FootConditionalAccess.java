package com.graphhopper.routing.ev;

import com.graphhopper.util.Helper;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;

/**
 * Stores temporary so-called conditional restrictions from access:conditional and other conditional
 * tags affecting foot.
 */
public enum FootConditionalAccess {

    MISSING, YES, NO;

    public static final Collection<String> CONDITIONALS = new HashSet<>(Arrays.asList("access:conditional",
            "foot:conditional"));
    public static final String KEY = "foot_conditional_access";

    public static EnumEncodedValue<FootConditionalAccess> create() {
        return new EnumEncodedValue<>(KEY, FootConditionalAccess.class);
    }

    @Override
    public String toString() {
        return Helper.toLowerCase(super.toString());
    }
}
