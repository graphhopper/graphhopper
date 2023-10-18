/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.navigation;

public class DistanceUtils {

    static float meterToFeet = 3.28084f;
    static float meterToMiles = 0.00062137f;
    static float meterToKilometer = 0.001f;

    public enum Unit {
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
