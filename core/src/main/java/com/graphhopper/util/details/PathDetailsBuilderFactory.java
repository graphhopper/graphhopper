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
package com.graphhopper.util.details;

import com.graphhopper.routing.profiles.*;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.weighting.Weighting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.graphhopper.util.Parameters.DETAILS.*;

/**
 * Generates a list of PathDetailsBuilder from a List of PathDetail names
 *
 * @author Robin Boldt
 */
public class PathDetailsBuilderFactory {

    public List<PathDetailsBuilder> createPathDetailsBuilders(List<String> requestedPathDetails, FlagEncoder encoder, Weighting weighting) {
        List<PathDetailsBuilder> builders = new ArrayList<>();

        if (requestedPathDetails.contains(AVERAGE_SPEED))
            builders.add(new DecimalDetails(AVERAGE_SPEED, encoder.getAverageSpeedEnc(), false));

        if (requestedPathDetails.contains(STREET_NAME))
            builders.add(new StreetNameDetails());

        if (requestedPathDetails.contains(EDGE_ID))
            builders.add(new EdgeIdDetails());

        if (requestedPathDetails.contains(TIME))
            builders.add(new TimeDetails(weighting));

        if (requestedPathDetails.contains(DISTANCE))
            builders.add(new DistanceDetails());

        if (requestedPathDetails.contains(CarMaxSpeed.KEY) && encoder.hasEncodedValue(CarMaxSpeed.KEY))
            builders.add(new DecimalDetails(CarMaxSpeed.KEY, encoder.getDecimalEncodedValue(CarMaxSpeed.KEY), true));

        for (String key : Arrays.asList(RoadClass.KEY, RoadEnvironment.KEY, Surface.KEY, RoadAccess.KEY, Toll.KEY)) {
            if (requestedPathDetails.contains(key) && encoder.hasEncodedValue(key))
                builders.add(new ObjectDetails(key, encoder.getObjectEncodedValue(key)));
        }

        if (requestedPathDetails.size() != builders.size()) {
            throw new IllegalArgumentException("You requested the details " + requestedPathDetails + " but we could only find " + builders);
        }

        return builders;
    }
}
