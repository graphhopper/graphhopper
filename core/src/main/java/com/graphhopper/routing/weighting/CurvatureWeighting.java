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

import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.EnumEncodedValue;
import com.graphhopper.routing.ev.RoadAccess;
import com.graphhopper.routing.util.PriorityCode;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.PMap;

import static com.graphhopper.routing.util.PriorityCode.BEST;

/**
 * This Class uses bendiness parameter to prefer curvy routes.
 */
public class CurvatureWeighting extends PriorityWeighting {
    private final double minFactor;
    private final DecimalEncodedValue curvatureEnc;

    public CurvatureWeighting(BooleanEncodedValue accessEnc, DecimalEncodedValue speedEnc, DecimalEncodedValue priorityEnc,
                              DecimalEncodedValue curvatureEnc, EnumEncodedValue<RoadAccess> roadAccessEnc, PMap pMap, TurnCostProvider turnCostProvider) {
        super(accessEnc, speedEnc, priorityEnc, roadAccessEnc, pMap, turnCostProvider);
        this.curvatureEnc = curvatureEnc;
        double minBendiness = 1; // see correctErrors
        double maxSpeed = speedEnc.getMaxOrMaxStorableDecimal();
        minFactor = minBendiness / Math.log(maxSpeed) / PriorityCode.getValue(BEST.getValue());
    }

    @Override
    public double getMinWeight(double distance) {
        return minFactor * distance;
    }

    @Override
    public double calcEdgeWeight(EdgeIteratorState edgeState, boolean reverse) {
        double priority = edgeState.get(priorityEnc);
        double bendiness = edgeState.get(curvatureEnc);
        double speed = getRoadSpeed(edgeState, reverse);
        double roadDistance = edgeState.getDistance();

        // We use the log of the speed to decrease the impact of the speed, therefore we don't use the highway
        double regularWeight = roadDistance / Math.log(speed);
        return (bendiness * regularWeight) / priority;
    }

    protected double getRoadSpeed(EdgeIteratorState edge, boolean reverse) {
        return reverse ? edge.getReverse(speedEnc) : edge.get(speedEnc);
    }

    @Override
    public String getName() {
        return "curvature";
    }
}
