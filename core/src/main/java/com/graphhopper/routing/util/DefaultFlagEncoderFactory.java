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
 * This class creates FlagEncoders that are already included in the GraphHopper distribution.
 *
 * @author Peter Karich
 */
public class DefaultFlagEncoderFactory implements FlagEncoderFactory {
    @Override
    public FlagEncoder createFlagEncoder(String name, PMap configuration) {
        if (name.equals(ROADS))
            return FlagEncoders.createRoads(configuration);

        if (name.equals(CAR))
            return FlagEncoders.createCar(configuration);

        if (name.equals(CAR4WD))
            return FlagEncoders.createCar4wd(configuration);

        if (name.equals(BIKE))
            return FlagEncoders.createBike(configuration);

        if (name.equals(BIKE2))
            return FlagEncoders.createBike2(configuration);

        if (name.equals(RACINGBIKE))
            return FlagEncoders.createRacingBike(configuration);

        if (name.equals(MOUNTAINBIKE))
            return FlagEncoders.createMountainBike(configuration);

        if (name.equals(FOOT))
            return FlagEncoders.createFoot(configuration);

        if (name.equals(HIKE))
            return FlagEncoders.createHike(configuration);

        if (name.equals(MOTORCYCLE))
            return FlagEncoders.createMotorcycle(configuration);

        if (name.equals(WHEELCHAIR))
            return FlagEncoders.createWheelchair(configuration);

        throw new IllegalArgumentException("entry in encoder list not supported: " + name);
    }
}
