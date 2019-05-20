package com.graphhopper.navigation;


public class DistanceUtils {

    static float meterToFeet = 3.28084f;
    static float meterToMiles = 0.00062137f;
    static float meterToKilometer = 0.001f;

    enum Unit {
        METRIC,
        IMPERIAL
    }

    enum UnitTranslationKey {
        IN_HIGHER_DISTANCE_SINGULAR("in_km_singular", ""),
        IN_HIGHER_DISTANCE_PLURAL("in_km", ""),
        IN_LOWER_DISTANCE_PLURAL("in_m", ""),
        FOR_HIGHER_DISTANCE_PLURAL("for_km", "");

        public String metric;
        public String imperial;

        UnitTranslationKey(String metric, String imperial) {
            this.metric = metric;
            this.imperial = imperial;
        }
    }
}
