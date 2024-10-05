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
 * This enum defines the road access of an edge. Most edges are accessible from everyone and so the default value is
 * YES. But some have restrictions like "accessible only for customers" or when delivering. Unknown tags will get the
 * value OTHER. The NO value does not permit any access.
 */
public enum RoadAccess {
    YES, DESTINATION, CUSTOMERS, DELIVERY, FORESTRY, AGRICULTURAL, PRIVATE, NO;

    // TODO backward compatible rename to car_road_access
    public static final String KEY = "road_access";

    public static EnumEncodedValue<RoadAccess> create() {
        return new EnumEncodedValue<>(RoadAccess.KEY, RoadAccess.class);
    }

    @Override
    public String toString() {
        return Helper.toLowerCase(super.toString());
    }

    public static RoadAccess find(String name) {
        if (name == null)
            return YES;
        if (name.equalsIgnoreCase("permit"))
            return PRIVATE;
        try {
            // public and permissive will be converted into "yes"
            return RoadAccess.valueOf(Helper.toUpperCase(name));
        } catch (IllegalArgumentException ex) {
            return YES;
        }
    }

    public static RoadAccess countryHook(ReaderWay readerWay, RoadAccess roadAccess) {
        CountryRule countryRule = readerWay.getTag("country_rule", null);
        return countryRule == null ? roadAccess : countryRule.getAccess(readerWay, TransportationMode.CAR, roadAccess == null ? RoadAccess.YES : roadAccess);
    }
}
