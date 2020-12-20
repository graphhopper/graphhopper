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
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.weighting.Weighting;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.graphhopper.util.Parameters.Details.*;

/**
 * Generates a list of PathDetailsBuilder from a List of PathDetail names
 *
 * @author Robin Boldt
 */
public class PathDetailsBuilderFactory {
    
    private static final String PRIORITY_EV_SUFFIX = EncodingManager.getKey("", "priority");

    public List<PathDetailsBuilder> createPathDetailsBuilders(List<String> requestedPathDetails, EncodedValueLookup evl, Weighting weighting) {
        List<PathDetailsBuilder> builders = new ArrayList<>();
        
        Set<String> uniqueDetails = new HashSet<>();
        for (String pathDetail : requestedPathDetails) {
            if (!uniqueDetails.add(pathDetail)) {
                throw new IllegalArgumentException("Duplicate path detail requested: " + pathDetail);
            }
            builders.add(createPathDetailsBuilder(pathDetail, evl, weighting));
        }

        return builders;
    }
    
    private PathDetailsBuilder createPathDetailsBuilder(String pathDetail, EncodedValueLookup evl, Weighting weighting) {
        switch (pathDetail) {
        case AVERAGE_SPEED:
            return new AverageSpeedDetails(weighting);
        case DISTANCE:
            return new DistanceDetails();
        case EDGE_ID:
            return new EdgeIdDetails();
        case EDGE_KEY:
            return new EdgeKeyDetails();
        case STREET_NAME:
            return new StreetNameDetails();
        case TIME:
            return new TimeDetails(weighting);
        case WEIGHT:
            return new WeightDetails(weighting);
        case MaxAxleLoad.KEY:
        case MaxHeight.KEY:
        case MaxLength.KEY:
        case MaxSpeed.KEY:
        case MaxWeight.KEY:
        case MaxWidth.KEY:
            return createDecimalDetails(pathDetail, evl);
        case GetOffBike.KEY:
        case RoadClassLink.KEY:
        case Roundabout.KEY:
            return createBooleanDetails(pathDetail, evl);
        case HikeRating.KEY:
        case HorseRating.KEY:
        case MtbRating.KEY:
            return createIntDetails(pathDetail, evl);
        case BikeNetwork.KEY:
        case Country.KEY:
        case FootNetwork.KEY:
        case Hazmat.KEY:
        case HazmatTunnel.KEY:
        case HazmatWater.KEY:
        case RoadAccess.KEY:
        case RoadClass.KEY:
        case RoadEnvironment.KEY:
        case Surface.KEY:
        case Toll.KEY:
        case TrackType.KEY:
            return createEnumDetails(pathDetail, evl);
        default:
            if (pathDetail.endsWith(PRIORITY_EV_SUFFIX)) {
                return createDecimalDetails(pathDetail, evl);
            }
            throw new IllegalArgumentException("Unsupported path detail: " + pathDetail);
        }
    }
    
    private BooleanDetails createBooleanDetails(String key, EncodedValueLookup evl) {
        checkEncodedValue(key, evl);
        return new BooleanDetails(key, evl.getBooleanEncodedValue(key));
    }
    
    private DecimalDetails createDecimalDetails(String key, EncodedValueLookup evl) {
        checkEncodedValue(key, evl);
        return new DecimalDetails(key, evl.getDecimalEncodedValue(key));
    }
    
    private IntDetails createIntDetails(String key, EncodedValueLookup evl) {
        checkEncodedValue(key, evl);
        return new IntDetails(key, evl.getIntEncodedValue(key));
    }
    
    private EnumDetails<?> createEnumDetails(String key, EncodedValueLookup evl) {
        checkEncodedValue(key, evl);
        return new EnumDetails<>(key, evl.getEnumEncodedValue(key, Enum.class));
    }
    
    private static void checkEncodedValue(String key, EncodedValueLookup evl) {
        if (!evl.hasEncodedValue(key)) {
            throw new IllegalArgumentException("Missing encoded values for path detail: " + key);
        }
    }
}
