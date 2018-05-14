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
package com.graphhopper.routing.ch;

import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.HintsMap;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.util.CHEdgeIteratorState;
import com.graphhopper.util.EdgeIteratorState;

/**
 * Used in CH preparation and therefore assumed that all edges are of type CHEdgeIteratorState
 * <p>
 *
 * @author Peter Karich
 * @see PrepareContractionHierarchies
 */
public class PreparationWeighting implements Weighting {
    private final Weighting userWeighting;

    public PreparationWeighting(Weighting userWeighting) {
        this.userWeighting = userWeighting;
    }

    @Override
    public final double getMinWeight(double distance) {
        return userWeighting.getMinWeight(distance);
    }

    @Override
    public double calcWeight(EdgeIteratorState edgeState, boolean reverse, int prevOrNextEdgeId) {
        CHEdgeIteratorState tmp = (CHEdgeIteratorState) edgeState;
        if (tmp.isShortcut())
            // if a shortcut is in both directions the weight is identical => no need for 'reverse'
            return tmp.getWeight();

        return userWeighting.calcWeight(edgeState, reverse, prevOrNextEdgeId);
    }

    @Override
    public long calcMillis(EdgeIteratorState edgeState, boolean reverse, int prevOrNextEdgeId) {
        return userWeighting.calcMillis(edgeState, reverse, prevOrNextEdgeId);
    }

    @Override
    public FlagEncoder getFlagEncoder() {
        return userWeighting.getFlagEncoder();
    }

    @Override
    public boolean matches(HintsMap map) {
        return getName().equals(map.getWeighting()) && userWeighting.getFlagEncoder().toString().equals(map.getVehicle());
    }

    @Override
    public String getName() {
        return "prepare|" + userWeighting.getName();
    }

    @Override
    public String toString() {
        return getName();
    }
}
