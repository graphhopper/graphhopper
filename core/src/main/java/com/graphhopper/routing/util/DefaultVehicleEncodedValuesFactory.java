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

import com.graphhopper.util.PMap;

/**
 * This class creates vehicle encoded values that are already included in the GraphHopper distribution.
 *
 * @author Peter Karich
 */
public class DefaultVehicleEncodedValuesFactory implements VehicleEncodedValuesFactory {
    @Override
    public VehicleEncodedValues createVehicleEncodedValues(String name, PMap configuration) {
        if (name.equals(ROADS))
            return VehicleEncodedValues.roads();

        if (name.equals(CAR))
            return VehicleEncodedValues.car(configuration);

        if (name.equals(CAR4WD))
            return VehicleEncodedValues.car4wd(configuration);

        if (name.equals(BIKE))
            return VehicleEncodedValues.bike(configuration);

        if (name.equals(BIKE2))
            return VehicleEncodedValues.bike2(configuration);

        if (name.equals(RACINGBIKE))
            return VehicleEncodedValues.racingbike(configuration);

        if (name.equals(MOUNTAINBIKE))
            return VehicleEncodedValues.mountainbike(configuration);

        if (name.equals(FOOT))
            return VehicleEncodedValues.foot(configuration);

        if (name.equals(HIKE))
            return VehicleEncodedValues.hike(configuration);

        if (name.equals(MOTORCYCLE))
            return VehicleEncodedValues.motorcycle(configuration);

        if (name.equals(WHEELCHAIR))
            return VehicleEncodedValues.wheelchair(configuration);

        throw new IllegalArgumentException("entry in vehicle list not supported: " + name);
    }
}
