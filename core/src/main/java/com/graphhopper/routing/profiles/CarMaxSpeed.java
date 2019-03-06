package com.graphhopper.routing.profiles;

/**
 * This EncodedValue stores speed values and 0 stands for the default i.e. no maxspeed sign (does not imply no speed limit).
 */
public class CarMaxSpeed {
    public static final String KEY = "car_max_speed";

    /**
     * speed value used for "none" speed limit on German Autobahn
     */
    public static final double UNLIMITED_SIGN_SPEED = 140;

    /**
     * speed value used for road sections without known speed limit.
     */
    public static final double UNSET_SPEED = 0;

    public static DecimalEncodedValue create() {
        return new FactorizedDecimalEncodedValue(KEY, 5, 5, true);
    }
}
