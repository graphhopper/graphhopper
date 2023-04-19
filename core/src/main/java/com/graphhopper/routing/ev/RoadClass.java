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

/**
 * This enum defines the road class of an edge. It is heavily influenced from the highway tag in OSM that can be
 * primary, cycleway etc. All edges that do not fit get OTHER as value.
 */
public enum RoadClass {
    OTHER, MOTORWAY, TRUNK, PRIMARY, SECONDARY, TERTIARY, RESIDENTIAL, UNCLASSIFIED,
    SERVICE, ROAD, TRACK, BRIDLEWAY, STEPS, CYCLEWAY, PATH, LIVING_STREET, FOOTWAY, PEDESTRIAN, PLATFORM, CORRIDOR;

    public static final String KEY = "road_class";

    public static EnumEncodedValue<RoadClass> create() {
        return new EnumEncodedValue<>(RoadClass.KEY, RoadClass.class);
    }

    public static RoadClass find(String name) {
        if (name == null || name.isEmpty())
            return OTHER;
        try {
            return RoadClass.valueOf(Helper.toUpperCase(name));
        } catch (IllegalArgumentException ex) {
            return OTHER;
        }
    }
}
