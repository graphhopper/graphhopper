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

package com.graphhopper;

import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.HintsMap;
import com.graphhopper.routing.weighting.TDWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.util.EdgeIteratorState;

import java.time.Instant;

class TimeDependentAccessWeighting implements TDWeighting {

    private final TimeDependentAccessRestriction timeDependentAccessRestriction;
    private final Weighting finalWeighting;

    public TimeDependentAccessWeighting(GraphHopper graphHopper, Weighting finalWeighting) {
        this.finalWeighting = finalWeighting;
        timeDependentAccessRestriction = new TimeDependentAccessRestriction(graphHopper.getGraphHopperStorage());
    }

    @Override
    public long calcTDMillis(EdgeIteratorState edge, boolean reverse, int prevOrNextEdgeId, long linkEnterTime) {
        return calcMillis(edge, reverse, prevOrNextEdgeId);
    }

    @Override
    public double getMinWeight(double distance) {
        return finalWeighting.getMinWeight(distance);
    }

    @Override
    public double calcWeight(EdgeIteratorState edgeState, boolean reverse, int prevOrNextEdgeId) {
        return finalWeighting.calcWeight(edgeState, reverse, prevOrNextEdgeId);
    }

    @Override
    public double calcTDWeight(EdgeIteratorState edgeState, boolean reverse, int prevOrNextEdgeId, long linkEnterTimeMilli) {
        if (timeDependentAccessRestriction.accessible(edgeState, Instant.ofEpochMilli(linkEnterTimeMilli))) {
            return finalWeighting.calcWeight(edgeState, reverse, prevOrNextEdgeId);
        } else {
            return Double.POSITIVE_INFINITY;
        }
    }

    @Override
    public long calcMillis(EdgeIteratorState edgeState, boolean reverse, int prevOrNextEdgeId) {
        return finalWeighting.calcMillis(edgeState, reverse, prevOrNextEdgeId);
    }

    @Override
    public FlagEncoder getFlagEncoder() {
        return finalWeighting.getFlagEncoder();
    }

    @Override
    public String getName() {
        return finalWeighting.getName();
    }

    @Override
    public boolean matches(HintsMap map) {
        return finalWeighting.matches(map);
    }
}
