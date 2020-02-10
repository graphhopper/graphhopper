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

import com.conveyal.osmlib.OSM;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.HintsMap;
import com.graphhopper.routing.weighting.TDWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.timezone.core.TimeZones;
import com.graphhopper.util.EdgeIteratorState;

import java.time.Instant;

public class TimeDependentAccessWeighting implements TDWeighting {

    private final TimeDependentAccessRestriction timeDependentAccessRestriction;
    private final Weighting finalWeighting;

    public TimeDependentAccessWeighting(OSM osm, GraphHopper graphHopper, TimeZones timeZones, Weighting finalWeighting) {
        this.finalWeighting = finalWeighting;
        timeDependentAccessRestriction = new TimeDependentAccessRestriction(graphHopper.getGraphHopperStorage(), osm, timeZones);
    }

    @Override
    public long calcTDMillis(EdgeIteratorState edge, boolean reverse, int prevOrNextEdgeId, long linkEnterTime) {
        return calcEdgeMillis(edge, reverse);
    }

    @Override
    public double getMinWeight(double distance) {
        return finalWeighting.getMinWeight(distance);
    }

    @Override
    public double calcEdgeWeight(EdgeIteratorState edgeState, boolean reverse) {
        return finalWeighting.calcEdgeWeight(edgeState, reverse);
    }

    @Override
    public double calcTDWeight(EdgeIteratorState edgeState, boolean reverse, int prevOrNextEdgeId, long linkEnterTimeMilli) {
        if (timeDependentAccessRestriction.accessible(edgeState, Instant.ofEpochMilli(linkEnterTimeMilli)).orElse(true)) {
            return finalWeighting.calcEdgeWeight(edgeState, reverse);
        } else {
            return Double.POSITIVE_INFINITY;
        }
    }

    @Override
    public long calcEdgeMillis(EdgeIteratorState edgeState, boolean reverse) {
        return finalWeighting.calcEdgeMillis(edgeState, reverse);
    }

    @Override
    public double calcTurnWeight(int inEdge, int viaNode, int outEdge) {
        return finalWeighting.calcTurnWeight(inEdge, viaNode, outEdge);
    }

    @Override
    public double calcTDTurnWeight(int inEdge, int viaNode, int outEdge, long turnTimeMilli) {
        if (timeDependentAccessRestriction.canTurn(inEdge, viaNode, outEdge, Instant.ofEpochMilli(turnTimeMilli)).orElse(true)) {
            return finalWeighting.calcTurnWeight(inEdge, viaNode, outEdge);
        } else {
            return Double.POSITIVE_INFINITY;
        }
    }

    @Override
    public long calcTurnMillis(int inEdge, int viaNode, int outEdge) {
        return finalWeighting.calcTurnMillis(inEdge, viaNode, outEdge);
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
