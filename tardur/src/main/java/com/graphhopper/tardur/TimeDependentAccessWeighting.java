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

package com.graphhopper.tardur;

import com.graphhopper.GraphHopper;
import com.graphhopper.routing.profiles.IntEncodedValue;
import com.graphhopper.routing.profiles.TurnCost;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.HintsMap;
import com.graphhopper.routing.weighting.TDWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.timezone.core.TimeZones;
import com.graphhopper.util.EdgeIteratorState;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.TimeZone;

public class TimeDependentAccessWeighting implements TDWeighting {

    private final TimeDependentRestrictionsDAO timeDependentRestrictionsDAO;
    private final TimeZones timeZones;
    private final Weighting finalWeighting;
    private final IntEncodedValue tagPointer;
    private final GraphHopperStorage ghStorage;

    public TimeDependentAccessWeighting(GraphHopper graphHopper, TimeZones timeZones, Weighting finalWeighting) {
        this.timeZones = timeZones;
        this.finalWeighting = finalWeighting;
        ghStorage = graphHopper.getGraphHopperStorage();
        timeDependentRestrictionsDAO = new TimeDependentRestrictionsDAO(ghStorage, timeZones);
        tagPointer = graphHopper.getEncodingManager().getIntEncodedValue("turnrestrictiontagpointer");
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
        if (timeDependentRestrictionsDAO.accessible(edgeState, Instant.ofEpochMilli(linkEnterTimeMilli)).orElse(true)) {
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
        if (canTurn(inEdge, viaNode, outEdge, Instant.ofEpochMilli(turnTimeMilli)).orElse(true)) {
            return finalWeighting.calcTurnWeight(inEdge, viaNode, outEdge);
        } else {
            return Double.POSITIVE_INFINITY;
        }
    }

    private Optional<Boolean> canTurn(int inEdge, int viaNode, int outEdge, Instant at) {
        EdgeIteratorState inEdgeCursor = ghStorage.getEdgeIteratorState(inEdge, viaNode);
        IntsRef flags = ghStorage.getTurnCostStorage().readFlags(TurnCost.createFlags(), inEdge, viaNode, outEdge);
        int p = tagPointer.getInt(false, flags);
        if (p != 0) {
            Map<String, String> tags = ghStorage.getTagStore().getAll(p);
            TimeZone timeZone = timeZones.getTimeZone(ghStorage.getNodeAccess().getLat(inEdgeCursor.getBaseNode()), ghStorage.getNodeAccess().getLon(inEdgeCursor.getBaseNode()));
            return timeDependentRestrictionsDAO.accessible(tags, at.atZone(timeZone.toZoneId()));
        }
        return Optional.empty();
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
