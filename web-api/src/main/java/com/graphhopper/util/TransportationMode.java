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
package com.graphhopper.util;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Define disjunct ways of transportation that are used to create and populate our encoded values from a data source
 * like OpenStreetMap.
 *
 * @author Robin Boldt
 * @author Peter Karich
 */
public enum TransportationMode {
    @JsonProperty("other")
    OTHER(false),
    @JsonProperty("foot")
    FOOT(false),
    @JsonProperty("vehicle")
    VEHICLE(false),
    @JsonProperty("bike")
    BIKE(false),
    @JsonProperty("car")
    CAR(true),
    @JsonProperty("motorcycle")
    MOTORCYCLE(true),
    @JsonProperty("hgv")
    HGV(true),
    @JsonProperty("psv")
    PSV(true),
    @JsonProperty("bus")
    BUS(true);

    private final boolean motorVehicle;

    TransportationMode(boolean motorVehicle) {
        this.motorVehicle = motorVehicle;
    }

    public boolean isMotorVehicle() {
        return motorVehicle;
    }

    public static TransportationMode find(String name) {
        if (name == null || name.isEmpty())
            return OTHER;
        try {
            return TransportationMode.valueOf(Helper.toUpperCase(name));
        } catch (IllegalArgumentException ex) {
            return OTHER;
        }
    }
}
