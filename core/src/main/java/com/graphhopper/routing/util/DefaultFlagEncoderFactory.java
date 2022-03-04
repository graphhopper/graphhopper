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
            return new RoadsFlagEncoder();

        if (name.equals(CAR))
            return new CarFlagEncoder(configuration);

        if (name.equals(CAR4WD))
            return new Car4WDFlagEncoder(configuration);

        if (name.equals(BIKE))
            return new BikeFlagEncoder(configuration);

        if (name.equals(BIKE2))
            return new Bike2WeightFlagEncoder(configuration);

        if (name.equals(RACINGBIKE))
            return new RacingBikeFlagEncoder(configuration);

        if (name.equals(MOUNTAINBIKE))
            return new MountainBikeFlagEncoder(configuration);

        if (name.equals(FOOT))
            return new FootFlagEncoder(configuration);

        if (name.equals(HIKE))
            return new HikeFlagEncoder(configuration);

        if (name.equals(MOTORCYCLE))
            return new MotorcycleFlagEncoder(configuration);

        if (name.equals(WHEELCHAIR))
            return new WheelchairFlagEncoder(configuration);

        throw new IllegalArgumentException("entry in encoder list not supported: " + name);
    }
}
