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
import com.graphhopper.util.EdgeIteratorState;

/**
 * Calculates the shortest route - independent of a vehicle as the calculation is based on the
 * distance only.
 *
 * @author Peter Karich
 */
public class ShortestWeighting implements Weighting {

    final BooleanEncodedValue accessEnc;
    final DecimalEncodedValue speedEnc;
    final TurnCostProvider tcProvider;

    public ShortestWeighting(BooleanEncodedValue accessEnc, DecimalEncodedValue speedEnc) {
        this(accessEnc, speedEnc, TurnCostProvider.NO_TURN_COST_PROVIDER);
    }

    public ShortestWeighting(BooleanEncodedValue accessEnc, DecimalEncodedValue speedEnc, TurnCostProvider tcProvider) {
        this.accessEnc = accessEnc;
        this.speedEnc = speedEnc;
        this.tcProvider = tcProvider;
    }

    @Override
    public double getMinWeight(double distance) {
        return distance;
    }

    @Override
    public boolean edgeHasNoAccess(EdgeIteratorState edgeState, boolean reverse) {
        return reverse ? !edgeState.getReverse(accessEnc) : !edgeState.get(accessEnc);
    }

    @Override
    public double calcEdgeWeight(EdgeIteratorState edgeState, boolean reverse) {
        return edgeState.getDistance();
    }

    @Override
    public long calcEdgeMillis(EdgeIteratorState edgeState, boolean reverse) {
        if (edgeState.getBaseNode() == edgeState.getAdjNode()) reverse = false;
        double speed = reverse ? edgeState.getReverse(speedEnc) : edgeState.get(speedEnc);
        return Math.round(edgeState.getDistance() / speed * 3.6 * 1000);
    }

    @Override
    public double calcTurnWeight(int inEdge, int viaNode, int outEdge) {
        return tcProvider.calcTurnWeight(inEdge, viaNode, outEdge);
    }

    @Override
    public long calcTurnMillis(int inEdge, int viaNode, int outEdge) {
        return tcProvider.calcTurnMillis(inEdge, viaNode, outEdge);
    }

    @Override
    public boolean hasTurnCosts() {
        return tcProvider != TurnCostProvider.NO_TURN_COST_PROVIDER;
    }

    @Override
    public String getName() {
        return "shortest";
    }
}
