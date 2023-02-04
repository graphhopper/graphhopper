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
import com.graphhopper.core.util.PMap;

import static com.graphhopper.routing.util.PriorityCode.BEST;

/**
 * Special weighting for (motor)bike
 *
 * @author Peter Karich
 */
public class PriorityWeighting extends FastestWeighting {

    private final double minFactor;
    private final double maxPrio;
    protected final DecimalEncodedValue priorityEnc;

    public PriorityWeighting(BooleanEncodedValue accessEnc, DecimalEncodedValue speedEnc, DecimalEncodedValue priorityEnc,
                             EnumEncodedValue<RoadAccess> roadAccessEnc, PMap pMap, TurnCostProvider turnCostProvider) {
        super(accessEnc, speedEnc, roadAccessEnc, pMap, turnCostProvider);
        this.priorityEnc = priorityEnc;
        minFactor = 1 / PriorityCode.getValue(BEST.getValue());
        maxPrio = PriorityCode.getFactor(BEST.getValue());
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
        double priority = edgeState.get(priorityEnc);
        if (priority > maxPrio)
            throw new IllegalArgumentException("priority cannot be bigger than " + maxPrio + " but was " + priority);
        return weight / priority;
    }
}
