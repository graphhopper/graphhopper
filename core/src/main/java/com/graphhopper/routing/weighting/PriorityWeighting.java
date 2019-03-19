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

import com.graphhopper.routing.profiles.DecimalEncodedValue;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.PriorityCode;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.PMap;

import static com.graphhopper.routing.util.PriorityCode.BEST;

/**
 * Special weighting for (motor)bike
 *
 * @author Peter Karich
 */
public class PriorityWeighting extends FastestWeighting {

    private final double minFactor;
    private final DecimalEncodedValue priorityEnc;

    public PriorityWeighting(FlagEncoder encoder, PMap pMap) {
        super(encoder, pMap);
        priorityEnc = encoder.getDecimalEncodedValue(EncodingManager.getKey(encoder, "priority"));
        double maxPriority = PriorityCode.getFactor(BEST.getValue());
        minFactor = 1 / (0.5 + maxPriority);
    }

    @Override
    public double getMinWeight(double distance) {
        return minFactor * super.getMinWeight(distance);
    }

    @Override
    public double calcWeight(EdgeIteratorState edgeState, boolean reverse, int prevOrNextEdgeId) {
        double weight = super.calcWeight(edgeState, reverse, prevOrNextEdgeId);
        if (Double.isInfinite(weight))
            return Double.POSITIVE_INFINITY;
        return weight / (0.5 + edgeState.get(priorityEnc));
    }
}
