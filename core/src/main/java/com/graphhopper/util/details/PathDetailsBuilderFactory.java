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

import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.util.Parameters;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates a list of PathDetailsBuilder from a List of PathDetail names
 *
 * @author Robin Boldt
 */
public class PathDetailsBuilderFactory {

    private final List<String> requestedPathDetails;
    private final FlagEncoder encoder;

    public PathDetailsBuilderFactory(List<String> requestedPathDetails, FlagEncoder encoder) {
        this.requestedPathDetails = requestedPathDetails;
        this.encoder = encoder;
    }

    public List<PathDetailsBuilder> createPathDetailsBuilders() {
        List<PathDetailsBuilder> builders = new ArrayList<>();

        if (requestedPathDetails.contains(Parameters.DETAILS.AVERAGE_SPEED))
            builders.add(new AverageSpeedDetails(encoder));

        if (requestedPathDetails.size() != builders.size()) {
            throw new IllegalArgumentException("You requested the details " + requestedPathDetails + " but we couldn only find " + builders);
        }

        return builders;
    }
}
