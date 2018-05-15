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
import com.graphhopper.routing.util.HintsMap;
import com.graphhopper.util.EdgeIteratorState;

/**
 * The AdjustedWeighting wraps another Weighting.
 *
 * @author Robin Boldt
 */
public abstract class AbstractAdjustedWeighting implements Weighting {
    protected final Weighting superWeighting;

    public AbstractAdjustedWeighting(Weighting superWeighting) {
        if (superWeighting == null)
            throw new IllegalArgumentException("No super weighting set");
        this.superWeighting = superWeighting;
    }

    @Override
    public long calcMillis(EdgeIteratorState edgeState, boolean reverse, int prevOrNextEdgeId) {
        return superWeighting.calcMillis(edgeState, reverse, prevOrNextEdgeId);
    }

    /**
     * Returns the flagEncoder of the superWeighting. Usually we do not have a FlagEncoder here.
     */
    @Override
    public FlagEncoder getFlagEncoder() {
        return superWeighting.getFlagEncoder();
    }

    @Override
    public boolean matches(HintsMap reqMap) {
        return getName().equals(reqMap.getWeighting())
                && superWeighting.getFlagEncoder().toString().equals(reqMap.getVehicle());
    }

    @Override
    public String toString() {
        return getName() + "|" + superWeighting.toString();
    }
}
