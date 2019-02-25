package com.graphhopper.routing.profiles;

/**
 * This EncodedValue stores speed values and 0 stands for the default i.e. no maxspeed sign (does not imply no speed limit).
 * <p>
 * TODO NOW this is a braking change to the previous version where dataFlagEncoder.getSpeed returned -1 for these cases.
 * The IntDetails still return this.
 */
public class CarMaxSpeed {
    public static final String KEY = "car_max_speed";

    // speed value used for "none" speed limit on German Autobahn
    public static final double UNLIMITED_SIGN = 140;

    public static DecimalEncodedValue create() {
        return new FactorizedDecimalEncodedValue(KEY, 5, 5, true);
    }
}
