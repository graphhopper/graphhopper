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
    /**
     * Encoder, which decodes the turn flags
     */
    private final TurnCostEncoder turnCostEncoder;
    private final TurnCostExtension turnCostExt;
    private final Weighting superWeighting;
    private double defaultUTurnCost = 40;

    /**
     * @param turnCostExt the turn cost storage to be used
     */
    public TurnWeighting(Weighting superWeighting, TurnCostExtension turnCostExt) {
        this.turnCostEncoder = (TurnCostEncoder) superWeighting.getFlagEncoder();
        this.superWeighting = superWeighting;
        this.turnCostExt = turnCostExt;

        if (turnCostExt == null)
            throw new RuntimeException("No storage set to calculate turn weight");
    }

    /**
     * Set the default cost for an u-turn in seconds. Default is 40s. Should be that high to avoid
     * 'tricking' other turn costs or restrictions.
     */
    public TurnWeighting setDefaultUTurnCost(double costInSeconds) {
        this.defaultUTurnCost = costInSeconds;
        return this;
    }

    @Override
    public double getMinWeight(double distance) {
        return superWeighting.getMinWeight(distance);
    }

    @Override
    public double calcWeight(EdgeIteratorState edgeState, boolean reverse, int prevOrNextEdgeId) {
        double weight = superWeighting.calcWeight(edgeState, reverse, prevOrNextEdgeId);
        if (prevOrNextEdgeId == EdgeIterator.NO_EDGE)
            return weight;

        final int origEdgeId = reverse ? edgeState.getOrigEdgeLast() : edgeState.getOrigEdgeFirst();
        double turnCosts = reverse ?
                calcTurnWeight(origEdgeId, edgeState.getBaseNode(), prevOrNextEdgeId) :
                calcTurnWeight(prevOrNextEdgeId, edgeState.getBaseNode(), origEdgeId);

        if (turnCosts == 0 && origEdgeId == prevOrNextEdgeId)
            return weight + defaultUTurnCost;

        return weight + turnCosts;
    }

    @Override
    public long calcMillis(EdgeIteratorState edgeState, boolean reverse, int prevOrNextEdgeId) {
        long millis = superWeighting.calcMillis(edgeState, reverse, prevOrNextEdgeId);
        if (prevOrNextEdgeId == EdgeIterator.NO_EDGE)
            return millis;

        // TODO for now assume turn costs are returned in milliseconds?
        // should we also separate weighting vs. time for turn? E.g. a fast but dangerous turn - is this common?
        long turnCostsInMillis;
        if (reverse)
            turnCostsInMillis = (long) calcTurnWeight(edgeState.getEdge(), edgeState.getBaseNode(), prevOrNextEdgeId);
        else
            turnCostsInMillis = (long) calcTurnWeight(prevOrNextEdgeId, edgeState.getBaseNode(), edgeState.getEdge());

        return millis + turnCostsInMillis;
    }

    /**
     * This method calculates the turn weight separately.
     */
    public double calcTurnWeight(int edgeFrom, int nodeVia, int edgeTo) {
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
