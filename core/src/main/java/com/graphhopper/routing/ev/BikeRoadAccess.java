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

import com.graphhopper.util.Helper;

import java.util.Arrays;
import java.util.List;

public enum BikeRoadAccess {
    MISSING, YES, DESTINATION, DESIGNATED, USE_SIDEPATH, DISMOUNT, PRIVATE, NO;

    public static final String KEY = "bike_road_access";

    /**
     * The access restriction list returned from OSMRoadAccessParser.toOSMRestrictions(TransportationMode.Bike)
     * does not contain "vehicle" to still allow walking, via 'dismount' (#2981). But to allow
     * walking via dismount in case of vehicle=private we need bike_road_access == PRIVATE. This
     * also allows us to limit speed to 5km/h if foot_road_access == YES. See
     * <a href="https://www.openstreetmap.org/way/1058548816">this way</a>.
     */
    public static final List<String> RESTRICTIONS = Arrays.asList("bicycle", "vehicle", "access");

    public static EnumEncodedValue<BikeRoadAccess> create() {
        return new EnumEncodedValue<>(BikeRoadAccess.KEY, BikeRoadAccess.class);
    }

    @Override
    public String toString() {
        return Helper.toLowerCase(super.toString());
    }

    public static BikeRoadAccess find(String name) {
        if (name == null || name.isEmpty())
            return MISSING;
        if (name.equalsIgnoreCase("permit") || name.equalsIgnoreCase("customers"))
            return PRIVATE;
        try {
            return BikeRoadAccess.valueOf(Helper.toUpperCase(name));
        } catch (IllegalArgumentException ex) {
            return YES;
        }
    }
}
