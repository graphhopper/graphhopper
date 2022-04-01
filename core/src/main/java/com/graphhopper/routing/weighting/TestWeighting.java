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
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.util.EdgeIteratorState;

public class TestWeighting implements Weighting {
    private final BooleanEncodedValue accessEnc;
    private final TurnCostProvider turnCostProvider;
    private final double speed = 60 / 3.6;

    public TestWeighting(BooleanEncodedValue accessEnc, TurnCostProvider turnCostProvider) {
        this.accessEnc = accessEnc;
        this.turnCostProvider = turnCostProvider;
    }

    @Override
    public double getMinWeight(double distance) {
        return 1000 * distance / speed;
    }

    @Override
    public double calcEdgeWeight(EdgeIteratorState edgeState, boolean reverse) {
        boolean access = reverse ? edgeState.getReverse(accessEnc) : edgeState.get(accessEnc);
        if (!access)
            return Double.POSITIVE_INFINITY;
        return calcEdgeMillis(edgeState, reverse);
    }

    @Override
    public long calcEdgeMillis(EdgeIteratorState edgeState, boolean reverse) {
        return 1000 * Math.round(edgeState.getDistance() / speed);
    }

    @Override
    public double calcTurnWeight(int inEdge, int viaNode, int outEdge) {
        return turnCostProvider.calcTurnWeight(inEdge, viaNode, outEdge);
    }

    @Override
    public long calcTurnMillis(int inEdge, int viaNode, int outEdge) {
        return turnCostProvider.calcTurnMillis(inEdge, viaNode, outEdge);
    }

    @Override
    public boolean hasTurnCosts() {
        return turnCostProvider != TurnCostProvider.NO_TURN_COST_PROVIDER;
    }

    @Override
    public FlagEncoder getFlagEncoder() {
        throw new UnsupportedOperationException("This method should be removed");
    }

    @Override
    public String getName() {
        throw new UnsupportedOperationException("This method should be removed");
    }
}
