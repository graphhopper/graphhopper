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

import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.querygraph.VirtualEdgeIterator;
import com.graphhopper.routing.querygraph.VirtualEdgeIteratorState;
import com.graphhopper.storage.TurnCostStorage;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;

public class SpeedWeighting implements Weighting {
    private final DecimalEncodedValue speedEnc;
    private final TurnCostProvider turnCostProvider;

    public SpeedWeighting(DecimalEncodedValue speedEnc) {
        this(speedEnc, TurnCostProvider.NO_TURN_COST_PROVIDER);
    }

    public SpeedWeighting(DecimalEncodedValue speedEnc, DecimalEncodedValue turnCostEnc, TurnCostStorage turnCostStorage, double uTurnCosts) {
        if (turnCostStorage == null || turnCostEnc == null)
            throw new IllegalArgumentException("This SpeedWeighting constructor expects turnCostEnc and turnCostStorage to be != null");
        if (uTurnCosts < 0) throw new IllegalArgumentException("u-turn costs must be positive");
        this.speedEnc = speedEnc;
        this.turnCostProvider = new TurnCostProvider() {
            @Override
            public double calcTurnWeight(int inEdge, int viaNode, int outEdge) {
                if (!EdgeIterator.Edge.isValid(inEdge) || !EdgeIterator.Edge.isValid(outEdge))
                    return 0;
                if (inEdge == outEdge)
                    return Math.max(turnCostStorage.get(turnCostEnc, inEdge, viaNode, outEdge), uTurnCosts);
                else
                    return turnCostStorage.get(turnCostEnc, inEdge, viaNode, outEdge);
            }

            @Override
            public long calcTurnMillis(int inEdge, int viaNode, int outEdge) {
                return (long) (1000 * calcTurnWeight(inEdge, viaNode, outEdge));
            }
        };
    }

    public SpeedWeighting(DecimalEncodedValue speedEnc, TurnCostProvider turnCostProvider) {
        this.speedEnc = speedEnc;
        this.turnCostProvider = turnCostProvider;
    }

    @Override
    public double calcMinWeightPerKm() {
        return Math.round(10 * 1000.0 / speedEnc.getMaxStorableDecimal());
    }

    @Override
    public double calcEdgeWeight(EdgeIteratorState edgeState, boolean reverse) {
//        if (edgeState.isVirtual())
//            throw new IllegalStateException("Edge state must not be virtual: " + edgeState.getClass().getName() + ". You need to use graph.wrapWeighting");
        double speed = reverse ? edgeState.getReverse(speedEnc) : edgeState.get(speedEnc);
        if (speed == 0) return Double.POSITIVE_INFINITY;
        return roundDouble(10 * edgeState.getDistance() / speed);
    }

    @Override
    public long calcEdgeMillis(EdgeIteratorState edgeState, boolean reverse) {
        double speed = reverse ? edgeState.getReverse(speedEnc) : edgeState.get(speedEnc);
        if (speed == 0) return Long.MAX_VALUE;
        return (long) (1000 * edgeState.getDistance() / speed);
    }

    @Override
    public double calcTurnWeight(int inEdge, int viaNode, int outEdge) {
        double turnWeight = turnCostProvider.calcTurnWeight(inEdge, viaNode, outEdge);
        return roundDouble(10 * turnWeight);
    }

    public static double roundDouble(double d) {
        if (Double.isInfinite(d)) return Double.POSITIVE_INFINITY;
        if (d % 1 != 0 && d < 0.5)
            return 1;
        return Math.round(d);
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
    public String toString() {
        return getName();
    }

    @Override
    public String getName() {
        return "speed";
    }
}
