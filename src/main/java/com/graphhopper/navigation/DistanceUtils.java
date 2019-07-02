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
        IN_HIGHER_DISTANCE_SINGULAR("in_km_singular", "in_mi_singular"),
        IN_HIGHER_DISTANCE_PLURAL("in_km", "in_mi"),
        IN_LOWER_DISTANCE_PLURAL("in_m", "in_ft"),
        FOR_HIGHER_DISTANCE_PLURAL("for_km", "for_mi");

        public String metric;
        public String imperial;

        UnitTranslationKey(String metric, String imperial) {
            this.metric = metric;
            this.imperial = imperial;
        }
    }
}
