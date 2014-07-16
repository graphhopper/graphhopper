/*
 *  Licensed to GraphHopper and Peter Karich under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  GraphHopper licenses this file to you under the Apache License, 
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
package com.graphhopper.routing.util;

import com.graphhopper.storage.TurnCostStorage;
import com.graphhopper.util.EdgeIteratorState;

/**
 * Provides methods to retrieve turn costs for a specific turn.
 * <p>
 * @author Karl Hübner
 */
public class TurnWeighting implements Weighting
{
    /**
     * Encoder, which decodes the turn flags
     */
    private final TurnCostEncoder turnCostEncoder;
    private final TurnCostStorage turnCostStorage;
    private final Weighting superWeighting;

    /**
     * @param turnCostStorage the turn cost storage to be used
     */
    public TurnWeighting( Weighting superWeighting, TurnCostEncoder encoder, TurnCostStorage tcs )
    {
        this.turnCostEncoder = encoder;
        this.superWeighting = superWeighting;
        this.turnCostStorage = tcs;
        if (encoder == null)
            throw new IllegalArgumentException("No encoder set to calculate turn weight");
        if (tcs == null)
            throw new RuntimeException("No storage set to calculate turn weight");
    }

    @Override
    public double getMinWeight( double distance )
    {
        return superWeighting.getMinWeight(distance);
    }

    @Override
    public double calcWeight( EdgeIteratorState edgeState, boolean reverse, int prevOrNextEdgeId )
    {

        double weight = superWeighting.calcWeight(edgeState, reverse, prevOrNextEdgeId);
        // if prevOrNextEdgeId == -1 return weight
        if (reverse)
            return weight + calcTurnWeight(edgeState.getEdge(), edgeState.getBaseNode(), prevOrNextEdgeId);
        else
            return weight + calcTurnWeight(prevOrNextEdgeId, edgeState.getBaseNode(), edgeState.getEdge());
    }

    protected double calcTurnWeight( int edgeFrom, int nodeVia, int edgeTo )
    {
        long turnFlags = turnCostStorage.getTurnCostsFlags(nodeVia, edgeFrom, edgeTo);
        if (turnCostEncoder.isTurnRestricted(turnFlags))
            return Double.POSITIVE_INFINITY;

        return turnCostEncoder.getTurnCosts(turnFlags);
    }

    @Override
    public String toString()
    {
        return "TURN|" + superWeighting.toString();
    }
}
