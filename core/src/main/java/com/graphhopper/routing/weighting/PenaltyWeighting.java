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

import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.PenaltyCode;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.PMap;

import static com.graphhopper.routing.util.PenaltyCode.EXCLUDE;

/**
 * Special weighting for (motor)bike, flipped version of PriorityWeighting
 *
 * @author Hazel Court
 */
public class PenaltyWeighting extends FastestWeighting {

    private final double minFactor;
    private final double maxPenalty;
    private final DecimalEncodedValue penaltyEnc;

    public PenaltyWeighting(FlagEncoder encoder, PMap pMap, TurnCostProvider turnCostProvider) {
        super(encoder, pMap, turnCostProvider);
        penaltyEnc = encoder.getDecimalEncodedValue(EncodingManager.getKey(encoder, "penalty"));
        minFactor = 1;
        maxPenalty = PenaltyCode.getFactor(EXCLUDE.getValue());
    }

    @Override
    public double getMinWeight(double distance) {
        return minFactor * super.getMinWeight(distance);
    }

    @Override
    public double calcEdgeWeight(EdgeIteratorState edgeState, boolean reverse) {
        double weight = super.calcEdgeWeight(edgeState, reverse);
        if (Double.isInfinite(weight))
            return Double.POSITIVE_INFINITY;
        double penalty = reverse ? edgeState.getReverse(penaltyEnc) : edgeState.get(penaltyEnc);
        if (penalty > maxPenalty)
            throw new IllegalArgumentException("penalty cannot be bigger than " + maxPenalty + " but was " + penalty);

        return weight * penalty;
    }
}
