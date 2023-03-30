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
            return VehicleEncodedValues.roads(configuration);

        if (name.equals(CAR))
            return VehicleEncodedValues.car(configuration);

        if (name.equals("car4wd"))
            throw new IllegalArgumentException("Instead of car4wd use the roads vehicle and a custom_model, see custom_models/car4wd.json");

        if (name.equals(BIKE))
            return VehicleEncodedValues.bike(configuration);

        if (name.equals("bike2"))
            throw new IllegalArgumentException("Instead of bike2 use the bike vehicle and a custom model, see custom_models/bike.json and #2668");

        if (name.equals(RACINGBIKE))
            return VehicleEncodedValues.racingbike(configuration);

        if (name.equals(MOUNTAINBIKE))
            return VehicleEncodedValues.mountainbike(configuration);

        if (name.equals(FOOT))
            return VehicleEncodedValues.foot(configuration);

        if (name.equals("hike"))
            throw new IllegalArgumentException("Instead of hike use the foot vehicle and a custom model, see custom_models/hike.json and #2759");

        if (name.equals("motorcycle"))
            throw new IllegalArgumentException("Instead of motorcycle use the car vehicle and a custom model, see custom_models/motorcycle.json and #2781");

        if (name.equals(WHEELCHAIR))
            return VehicleEncodedValues.wheelchair(configuration);

        throw new IllegalArgumentException("entry in vehicle list not supported: " + name);
    }
}
