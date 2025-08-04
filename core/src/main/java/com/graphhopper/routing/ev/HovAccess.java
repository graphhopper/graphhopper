package com.graphhopper.routing.ev;

/**
 * High-occupancy vehicle (carpool, diamond, transit, T2, or T3).
 * See <a href="https://wiki.openstreetmap.org/wiki/Key:hov">also here</a>.
 */
public class HovAccess {
    public final static String KEY = "hov_access";

    public static BooleanEncodedValue create() {
        return new SimpleBooleanEncodedValue(KEY, true);
    }
}
