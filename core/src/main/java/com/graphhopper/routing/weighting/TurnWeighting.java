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
import com.graphhopper.routing.util.TurnCostEncoder;
import com.graphhopper.storage.TurnCostExtension;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;

/**
 * Provides methods to retrieve turn costs for a specific turn.
 * <p>
 *
 * @author Karl Hübner
 * @author Peter Karich
 */
public class TurnWeighting implements Weighting {
    public static final int INFINITE_U_TURN_COSTS = -1;
    /**
     * Encoder, which decodes the turn flags
     */
    private final TurnCostEncoder turnCostEncoder;
    private final TurnCostExtension turnCostExt;
    private final Weighting superWeighting;
    private final double uTurnCosts;

    public TurnWeighting(Weighting superWeighting, TurnCostExtension turnCostExt) {
        this(superWeighting, turnCostExt, INFINITE_U_TURN_COSTS);
    }

    /**
     * @param superWeighting the weighting that is wrapped by this {@link TurnWeighting} and used to calculate the
     *                       edge weights for example
     * @param turnCostExt    the turn cost storage to be used
     * @param uTurnCosts     the cost of a u-turn in seconds, this value will be applied to all u-turn costs no matter
     *                       whether or not turnCostExt contains explicit values for these turns.
     */
    public TurnWeighting(Weighting superWeighting, TurnCostExtension turnCostExt, double uTurnCosts) {
        if (turnCostExt == null) {
            throw new RuntimeException("No storage set to calculate turn weight");
        }
        this.turnCostEncoder = superWeighting.getFlagEncoder();
        this.superWeighting = superWeighting;
        this.turnCostExt = turnCostExt;
        this.uTurnCosts = uTurnCosts < 0 ? Double.POSITIVE_INFINITY : uTurnCosts;
    }

    public double getUTurnCosts() {
        return uTurnCosts;
    }

    @Override
    public double getMinWeight(double distance) {
        return superWeighting.getMinWeight(distance);
    }

    @Override
    public double calcWeight(EdgeIteratorState edgeState, boolean reverse, int prevOrNextEdgeId) {
        double weight = superWeighting.calcWeight(edgeState, reverse, prevOrNextEdgeId);
        if (!EdgeIterator.Edge.isValid(prevOrNextEdgeId))
            return weight;

        final int origEdgeId = reverse ? edgeState.getOrigEdgeLast() : edgeState.getOrigEdgeFirst();
        double turnCosts = reverse
                ? calcTurnWeight(origEdgeId, edgeState.getBaseNode(), prevOrNextEdgeId)
                : calcTurnWeight(prevOrNextEdgeId, edgeState.getBaseNode(), origEdgeId);

        return weight + turnCosts;
    }

    @Override
    public long calcMillis(EdgeIteratorState edgeState, boolean reverse, int prevOrNextEdgeId) {
        long millis = superWeighting.calcMillis(edgeState, reverse, prevOrNextEdgeId);
        if (!EdgeIterator.Edge.isValid(prevOrNextEdgeId))
            return millis;

        // should we also separate weighting vs. time for turn? E.g. a fast but dangerous turn - is this common?
        // todo: why no first/last orig edge here as in calcWeight ?
        final int origEdgeId = edgeState.getEdge();
        long turnCostsInSeconds = (long) (reverse
                ? calcTurnWeight(origEdgeId, edgeState.getBaseNode(), prevOrNextEdgeId)
                : calcTurnWeight(prevOrNextEdgeId, edgeState.getBaseNode(), origEdgeId));

        return millis + 1000 * turnCostsInSeconds;
    }

    /**
     * This method calculates the turn weight separately.
     */
    public double calcTurnWeight(int edgeFrom, int nodeVia, int edgeTo) {
        if (!EdgeIterator.Edge.isValid(edgeFrom) || !EdgeIterator.Edge.isValid(edgeTo)) {
            return 0;
        }
        if (turnCostExt.isUTurn(edgeFrom, edgeTo)) {
            // note that the u-turn costs overwrite any turn costs set in TurnCostExtension
            // todo:
            // also this does not allow TurnCostEncoder to set the u-turn costs to zero explicitly, like FootFlagEncoder!
            // this problem is a bit hidden, because if you do not apply any turn restrictions (like FootFlagEncoder)
            // you never do a u-turn anyway.
            if (!turnCostExt.isUTurnAllowed(nodeVia)) {
                return Double.POSITIVE_INFINITY;
            }
            return uTurnCosts;
        }
        long turnFlags = turnCostExt.getTurnCostFlags(edgeFrom, nodeVia, edgeTo);
        if (turnCostEncoder.isTurnRestricted(turnFlags))
            return Double.POSITIVE_INFINITY;

        return turnCostEncoder.getTurnCost(turnFlags);
    }

    @Override
    public FlagEncoder getFlagEncoder() {
        return superWeighting.getFlagEncoder();
    }

    @Override
    public boolean matches(HintsMap weightingMap) {
        // TODO without 'turn' in comparison
        return superWeighting.matches(weightingMap);
    }

    @Override
    public String toString() {
        return "turn|" + superWeighting.toString();
    }

    @Override
    public String getName() {
        return "turn|" + superWeighting.getName();
    }
}
