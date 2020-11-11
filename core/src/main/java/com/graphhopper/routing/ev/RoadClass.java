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

/**
 * This enum defines the road class of an edge. It is heavily influenced from the highway tag in OSM that can be
 * primary, cycleway etc. All edges that do not fit get OTHER as value.
 */
public enum RoadClass {
    OTHER("other"), MOTORWAY("motorway"),
    TRUNK("trunk"), PRIMARY("primary"), SECONDARY("secondary"),
    TERTIARY("tertiary"), RESIDENTIAL("residential"), UNCLASSIFIED("unclassified"),
    SERVICE("service"), ROAD("road"), TRACK("track"),
    BRIDLEWAY("bridleway"), STEPS("steps"), CYCLEWAY("cycleway"),
    PATH("path"), LIVING_STREET("living_street"), FOOTWAY("footway"),
    PEDESTRIAN("pedestrian"), PLATFORM("platform"), CORRIDOR("corridor");

    public static final String KEY = "road_class";

    private final String name;

    RoadClass(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }

    public static RoadClass find(String name) {
        if (name == null || name.isEmpty())
            return OTHER;

        for (RoadClass roadClass : values()) {
            if (roadClass.name().equalsIgnoreCase(name)) {
                return roadClass;
            }
        }

        return OTHER;
    }
}
