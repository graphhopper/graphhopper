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

import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.weighting.Weighting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.graphhopper.routing.util.EncodingManager.getKey;
import static com.graphhopper.util.Parameters.Details.*;

/**
 * Generates a list of PathDetailsBuilder from a List of PathDetail names
 *
 * @author Robin Boldt
 */
public class PathDetailsBuilderFactory {

    public List<PathDetailsBuilder> createPathDetailsBuilders(List<String> requestedPathDetails, EncodedValueLookup evl, Weighting weighting) {
        List<PathDetailsBuilder> builders = new ArrayList<>();

        if (requestedPathDetails.contains(AVERAGE_SPEED))
            builders.add(new AverageSpeedDetails(weighting));

        if (requestedPathDetails.contains(STREET_NAME))
            builders.add(new StreetNameDetails());

        if (requestedPathDetails.contains(EDGE_ID))
            builders.add(new EdgeIdDetails());

        if (requestedPathDetails.contains(EDGE_KEY))
            builders.add(new EdgeKeyDetails());

        if (requestedPathDetails.contains(TIME))
            builders.add(new TimeDetails(weighting));

        if (requestedPathDetails.contains(WEIGHT))
            builders.add(new WeightDetails(weighting));

        if (requestedPathDetails.contains(DISTANCE))
            builders.add(new DistanceDetails());

        for (String checkSuffix : requestedPathDetails) {
            if (checkSuffix.endsWith(getKey("", "priority")) && evl.hasEncodedValue(checkSuffix))
                builders.add(new DecimalDetails(checkSuffix, evl.getDecimalEncodedValue(checkSuffix)));
        }

        for (String key : Arrays.asList(MaxSpeed.KEY, MaxWidth.KEY, MaxHeight.KEY, MaxWeight.KEY,
                MaxAxleLoad.KEY, MaxLength.KEY)) {
            if (requestedPathDetails.contains(key) && evl.hasEncodedValue(key))
                builders.add(new DecimalDetails(key, evl.getDecimalEncodedValue(key)));
        }

        for (String key : Arrays.asList(Roundabout.KEY, RoadClassLink.KEY, GetOffBike.KEY, "car_access", "bike_access")) {
            if (requestedPathDetails.contains(key) && evl.hasEncodedValue(key))
                builders.add(new BooleanDetails(key, evl.getBooleanEncodedValue(key)));
        }

        for (String key : Arrays.asList(RoadClass.KEY, RoadEnvironment.KEY, Surface.KEY, Smoothness.KEY, RoadAccess.KEY,
                BikeNetwork.KEY, FootNetwork.KEY, Toll.KEY, TrackType.KEY, Hazmat.KEY, HazmatTunnel.KEY,
                HazmatWater.KEY, Country.KEY)) {
            if (requestedPathDetails.contains(key) && evl.hasEncodedValue(key))
                builders.add(new EnumDetails<>(key, evl.getEnumEncodedValue(key, Enum.class)));
        }

        for (String key : Arrays.asList(MtbRating.KEY, HikeRating.KEY, HorseRating.KEY, Lanes.KEY)) {
            if (requestedPathDetails.contains(key) && evl.hasEncodedValue(key))
                builders.add(new IntDetails(key, evl.getIntEncodedValue(key)));
        }

        if (requestedPathDetails.size() != builders.size()) {
            throw new IllegalArgumentException("You requested the details " + requestedPathDetails + " but we could only find " + builders);
        }

        return builders;
    }
}
