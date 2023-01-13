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

import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.core.util.EdgeIteratorState;

/**
 * An {@link EdgeFilter} that only accepts edges with finite weight (in either direction)
 */
public class FiniteWeightFilter implements EdgeFilter {
    private final Weighting weighting;

    public FiniteWeightFilter(Weighting weighting) {
        this.weighting = weighting;
    }

    @Override
    public final boolean accept(EdgeIteratorState edgeState) {
        return Double.isFinite(weighting.calcEdgeWeightWithAccess(edgeState, false)) ||
                Double.isFinite(weighting.calcEdgeWeightWithAccess(edgeState, true));
    }
}
