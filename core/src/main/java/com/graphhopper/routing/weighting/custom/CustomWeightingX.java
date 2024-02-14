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
package com.graphhopper.routing.weighting.custom;

import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.weighting.AbstractWeighting;
import com.graphhopper.routing.weighting.TurnCostProvider;
import com.graphhopper.util.EdgeIteratorState;

public final class CustomWeightingX extends AbstractWeighting {
    public static final String NAME = "customx";
    private final CustomWeighting.EdgeToDoubleMapping edgeToPriorityMapping;
    private final CustomWeighting.EdgeToDoubleMapping edgeToSpeedMapping;

    public CustomWeightingX(BooleanEncodedValue baseAccessEnc, DecimalEncodedValue baseSpeedEnc, TurnCostProvider turnCostProvider, CustomWeighting.Parameters parameters) {
        super(baseAccessEnc, baseSpeedEnc, turnCostProvider);
        edgeToPriorityMapping = parameters.getEdgeToPriorityMapping();
        edgeToSpeedMapping = parameters.getEdgeToSpeedMapping();
    }

    @Override
    public double calcMinWeightPerDistance() {
        return 1;
    }

    @Override
    public double calcEdgeWeight(EdgeIteratorState edgeState, boolean reverse) {
        double priority = edgeToPriorityMapping.get(edgeState, reverse);
        if (priority == 0)
            return Double.POSITIVE_INFINITY;
        else if (priority < 1)
            throw new IllegalArgumentException("priority must be >= 1 (or 0 for infinite weight), use 1 only for the **highest** 'priority' roads");
        return priority * edgeState.getDistance();
    }

    @Override
    public long calcEdgeMillis(EdgeIteratorState edgeState, boolean reverse) {
        double speed = edgeToSpeedMapping.get(edgeState, reverse);
        if (speed == 0) return Long.MAX_VALUE;
        return Math.round(1000 * edgeState.getDistance() / (speed / 3.6));
    }

    @Override
    public String getName() {
        return NAME;
    }

}
