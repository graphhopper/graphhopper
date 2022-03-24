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
 * Specifies the settings for cycletouring/trekking
 *
 * @author ratrun
 * @author Peter Karich
 */
public class BikeFlagEncoder extends BikeCommonFlagEncoder {
    public BikeFlagEncoder() {
        this("bike");
    }

    public BikeFlagEncoder(String name) {
        this(name, 4, 2, 0, false);
    }

    public BikeFlagEncoder(PMap properties) {
        this(properties.getString("name", "bike"),
                properties.getInt("speed_bits", 4),
                properties.getInt("speed_factor", 2),
                properties.getBool("turn_costs", false) ? 1 : 0,
                properties.getBool("speed_two_directions", false));

        blockPrivate(properties.getBool("block_private", true));
        blockFords(properties.getBool("block_fords", false));
    }

    public BikeFlagEncoder(int speedBits, double speedFactor, int maxTurnCosts, boolean speedTwoDirections) {
        this("bike", speedBits, speedFactor, maxTurnCosts, speedTwoDirections);
    }

    public BikeFlagEncoder(String name, int speedBits, double speedFactor, int maxTurnCosts, boolean speedTwoDirections) {
        super(name, speedBits, speedFactor, maxTurnCosts, speedTwoDirections);
        addPushingSection("path");
        addPushingSection("footway");
        addPushingSection("pedestrian");
        addPushingSection("steps");
        addPushingSection("platform");

        avoidHighwayTags.add("trunk");
        avoidHighwayTags.add("trunk_link");
        avoidHighwayTags.add("primary");
        avoidHighwayTags.add("primary_link");
        avoidHighwayTags.add("secondary");
        avoidHighwayTags.add("secondary_link");

        // preferHighwayTags.add("road");
        preferHighwayTags.add("service");
        preferHighwayTags.add("tertiary");
        preferHighwayTags.add("tertiary_link");
        preferHighwayTags.add("residential");
        preferHighwayTags.add("unclassified");

        setSmoothnessSpeedFactor(com.graphhopper.routing.ev.Smoothness.EXCELLENT, 1.1d);
        setSmoothnessSpeedFactor(com.graphhopper.routing.ev.Smoothness.GOOD, 1.0d);
        setSmoothnessSpeedFactor(com.graphhopper.routing.ev.Smoothness.INTERMEDIATE, 0.9d);
        setSmoothnessSpeedFactor(com.graphhopper.routing.ev.Smoothness.BAD, 0.7d);
        setSmoothnessSpeedFactor(com.graphhopper.routing.ev.Smoothness.VERY_BAD, 0.6d);
        setSmoothnessSpeedFactor(com.graphhopper.routing.ev.Smoothness.HORRIBLE, 0.5d);
        setSmoothnessSpeedFactor(com.graphhopper.routing.ev.Smoothness.VERY_HORRIBLE, 0.4d);
        // SmoothnessSpeed <= smoothnessFactorPushingSectionThreshold gets mapped to speed PUSHING_SECTION_SPEED
        setSmoothnessSpeedFactor(com.graphhopper.routing.ev.Smoothness.IMPASSABLE, smoothnessFactorPushingSectionThreshold);

        barriers.add("kissing_gate");
        barriers.add("stile");
        barriers.add("turnstile");

        setSpecificClassBicycle("touring");
    }
}
