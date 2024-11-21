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
package com.graphhopper.routing.ev;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.util.TransportationMode;
import com.graphhopper.routing.util.countryrules.CountryRule;
import com.graphhopper.util.Helper;

/**
 * This enum defines the car road access of an edge. The default is the MISSING value. Some edges
 * have an explicit YES and some have restrictions like when delivering.
 * The NO value does not permit any access.
 */
public enum CarRoadAccess {
    MISSING, YES, DESTINATION, DESIGNATED, FORESTRY, AGRICULTURAL, PRIVATE, NO;

    public static final String KEY = "car_road_access";

    public static EnumEncodedValue<CarRoadAccess> create() {
        return new EnumEncodedValue<>(CarRoadAccess.KEY, CarRoadAccess.class);
    }

    @Override
    public String toString() {
        return Helper.toLowerCase(super.toString());
    }

    public static CarRoadAccess find(String name) {
        if (name == null || name.isEmpty())
            return MISSING;
        if (name.equalsIgnoreCase("permit")
                || name.equalsIgnoreCase("residents")
                || name.equalsIgnoreCase("customers")
                || name.equalsIgnoreCase("delivery"))
            return PRIVATE;
        try {
            // public and permissive will be converted into "yes"
            return CarRoadAccess.valueOf(Helper.toUpperCase(name));
        } catch (IllegalArgumentException ex) {
            return YES;
        }
    }

    public static CarRoadAccess countryHook(ReaderWay readerWay, CarRoadAccess roadAccess) {
        CountryRule countryRule = readerWay.getTag("country_rule", null);
        return countryRule == null ? roadAccess : countryRule.getAccess(readerWay, TransportationMode.CAR, roadAccess == null ? CarRoadAccess.YES : roadAccess);
    }
}
