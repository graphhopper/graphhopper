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

import com.graphhopper.routing.ev.EncodedValueLookup;
import com.graphhopper.util.PMap;

import static com.graphhopper.routing.util.VehicleEncodedValuesFactory.*;

public class DefaultVehicleTagParserFactory implements VehicleTagParserFactory {
    public VehicleTagParsers createParsers(EncodedValueLookup lookup, String name, PMap configuration) {
        if (name.equals(ROADS))
            return VehicleTagParsers.roads(lookup, configuration);
        if (name.equals(CAR))
            return VehicleTagParsers.car(lookup, configuration);
        if (name.equals(BIKE))
            return VehicleTagParsers.bike(lookup, configuration);
        if (name.equals(RACINGBIKE))
            return VehicleTagParsers.racingbike(lookup, configuration);
        if (name.equals(MOUNTAINBIKE))
            return VehicleTagParsers.mtb(lookup, configuration);
        if (name.equals(FOOT))
            return VehicleTagParsers.foot(lookup, configuration);
        if (name.equals(MOTORCYCLE))
            return VehicleTagParsers.motorcycle(lookup, configuration);
        if (name.equals(WHEELCHAIR))
            return VehicleTagParsers.wheelchair(lookup, configuration);

        throw new IllegalArgumentException("Unknown name for vehicle tag parsers: " + name);
    }
}
