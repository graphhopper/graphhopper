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
package com.graphhopper.routing.util;

import java.util.Arrays;
import java.util.List;

/**
 * Define different types of transportation that are used to create and populate our encoded values from a data source
 * like OpenStreetMap.
 *
 * @author Robin Boldt
 * @author Peter Karich
 */
public interface TransportationMode {
    TransportationMode
            OTHER = new TraMoImpl("other", java.util.Collections.singletonList("access"), false),
            FOOT = new TraMoImpl("foot", Arrays.asList("foot", "access"), false),
            VEHICLE = new TraMoImpl("vehicle", Arrays.asList("vehicle", "access"), false)
            // if we assume that TM_CONST.getRestrictions().contains(TM_CONST.getName()) is true then we need to name things like in OSM e.g. bicycle instead bike or motorcar instead car
            ,
            BICYCLE = new TraMoImpl("bicycle", Arrays.asList("bicycle", "vehicle", "access"), false),
            MOTOR_VEHICLE = new TraMoImpl("motor_vehicle", Arrays.asList("motor_vehicle", "vehicle", "access"), true),
            MOTORCAR = new TraMoImpl("motorcar", Arrays.asList("motorcar", "motor_vehicle", "vehicle", "access"), true),
            MOTORCYCLE = new TraMoImpl("motorcycle", Arrays.asList("motorcycle", "motor_vehicle", "vehicle", "access"), true);

    List<String> getRestrictions();

    String getName();

    boolean isMotorVehicle();
}
