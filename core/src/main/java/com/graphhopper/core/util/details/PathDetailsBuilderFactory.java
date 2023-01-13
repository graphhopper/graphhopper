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
package com.graphhopper.core.util.details;

import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;

import java.util.ArrayList;
import java.util.List;

import static com.graphhopper.util.Parameters.Details.*;

/**
 * Generates a list of PathDetailsBuilder from a List of PathDetail names
 *
 * @author Robin Boldt
 */
public class PathDetailsBuilderFactory {

    public List<PathDetailsBuilder> createPathDetailsBuilders(List<String> requestedPathDetails, EncodedValueLookup evl, Weighting weighting, Graph graph) {
        List<PathDetailsBuilder> builders = new ArrayList<>();

        if (requestedPathDetails.contains(STREET_NAME))
            builders.add(new KVStringDetails(STREET_NAME));
        if (requestedPathDetails.contains(STREET_REF))
            builders.add(new KVStringDetails(STREET_REF));
        if (requestedPathDetails.contains(STREET_DESTINATION))
            builders.add(new KVStringDetails(STREET_DESTINATION));

        if (requestedPathDetails.contains(AVERAGE_SPEED))
            builders.add(new AverageSpeedDetails(weighting));

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

        if (requestedPathDetails.contains(INTERSECTION))
            builders.add(new IntersectionDetails(graph, weighting));

        for (String pathDetail : requestedPathDetails) {
            if (!evl.hasEncodedValue(pathDetail)) continue; // path details like "time" won't be found

            EncodedValue ev = evl.getEncodedValue(pathDetail, EncodedValue.class);
            if (ev instanceof DecimalEncodedValue)
                builders.add(new DecimalDetails(pathDetail, (DecimalEncodedValue) ev));
            else if (ev instanceof BooleanEncodedValue)
                builders.add(new BooleanDetails(pathDetail, (BooleanEncodedValue) ev));
            else if (ev instanceof EnumEncodedValue)
                builders.add(new EnumDetails<>(pathDetail, (EnumEncodedValue) ev));
            else if (ev instanceof StringEncodedValue)
                builders.add(new StringDetails(pathDetail, (StringEncodedValue) ev));
            else if (ev instanceof IntEncodedValue)
                builders.add(new IntDetails(pathDetail, (IntEncodedValue) ev));
            else throw new IllegalArgumentException("unknown EncodedValue class " + ev.getClass().getName());
        }

        if (requestedPathDetails.size() != builders.size()) {
            throw new IllegalArgumentException("You requested the details " + requestedPathDetails + " but we could only find " + builders);
        }

        return builders;
    }
}
