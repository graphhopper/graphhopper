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
package com.graphhopper.routing.weighting;

import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.MotorcycleFlagEncoder;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.PMap;

/**
 * This Class uses bendiness parameter to prefer curvy routes.
 */
public class CurvatureWeighting extends PriorityWeighting {
    private final double minFactor;

    public CurvatureWeighting(FlagEncoder flagEncoder, PMap pMap) {
        super(flagEncoder, pMap);

        double minBendiness = 1; // see correctErrors
        double maxPriority = 1; // BEST / BEST
        minFactor = minBendiness / Math.log(flagEncoder.getMaxSpeed()) / (0.5 + maxPriority);
    }

    @Override
    public double getMinWeight(double distance) {
        return minFactor * distance;
    }

    @Override
    public double calcWeight(EdgeIteratorState edge, boolean reverse, int prevOrNextEdgeId) {
        double priority = flagEncoder.getDouble(edge.getFlags(), KEY);
        double bendiness = flagEncoder.getDouble(edge.getFlags(), MotorcycleFlagEncoder.CURVATURE_KEY);
        double speed = getRoadSpeed(edge, reverse);
        double roadDistance = edge.getDistance();

        // We use the log of the speed to decrease the impact of the speed, therefore we don't use the highway
        double regularWeight = roadDistance / Math.log(speed);

        return (bendiness * regularWeight) / (0.5 + priority);
    }

    protected double getRoadSpeed(EdgeIteratorState edge, boolean reverse) {
        return reverse ? flagEncoder.getReverseSpeed(edge.getFlags()) : flagEncoder.getSpeed(edge.getFlags());
    }

    @Override
    public String getName() {
        return "curvature";
    }
}
