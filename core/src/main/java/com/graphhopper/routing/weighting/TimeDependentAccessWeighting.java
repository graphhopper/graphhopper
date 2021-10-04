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

import com.graphhopper.routing.util.TimeDependentAccessEdgeFilter;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.util.EdgeIteratorState;

/**
 * Calculates the fastest route with the specified vehicle (VehicleEncoder). Calculates the time-dependent weight
 * in seconds.
 * <p>
 *
 * @author Andrzej Oles
 */
public class TimeDependentAccessWeighting extends AbstractAdjustedWeighting {
    private TimeDependentAccessEdgeFilter edgeFilter;

    public TimeDependentAccessWeighting(Weighting weighting, GraphHopperStorage graph, FlagEncoder encoder) {
        super(weighting);
        this.edgeFilter = new TimeDependentAccessEdgeFilter(graph, encoder);
    }

    @Override
    public double calcEdgeWeight(EdgeIteratorState edge, boolean reverse, long linkEnterTime) {
        if (edgeFilter.accept(edge, linkEnterTime)) {
            return superWeighting.calcEdgeWeight(edge, reverse, linkEnterTime);
        } else {
            return Double.POSITIVE_INFINITY;
        }
    }

    @Override
    public String getName() {
        return superWeighting.getName();
    }

    @Override
    public String toString() {
        return "td_access" + "|" + superWeighting.toString();
    }

    @Override
    public boolean isTimeDependent() {
        return true;
    }
}
