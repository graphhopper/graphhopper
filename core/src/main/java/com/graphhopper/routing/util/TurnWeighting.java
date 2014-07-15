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
     * Storage, which contains the turn flags
     */
    protected TurnCostStorage turnCostStorage;

    /**
     * Encoder, which decodes the turn flags
     */
    protected TurnCostEncoder turnCostEncoder;
    protected Weighting superWeighting;

    public TurnWeighting( Weighting superWeighting, TurnCostEncoder encoder )
    {
        this.turnCostEncoder = encoder;
        if (turnCostEncoder == null)
            throw new AssertionError("No encoder set to calculate turn weight");
    }

    /**
     * Initializes the weighting by injecting the turn cost storage
     * <p>
     * @param turnCostStorage the turn cost storage to be used
     */
    public void initTurnWeighting( TurnCostStorage turnCostStorage )
    {
        this.turnCostStorage = turnCostStorage;
    }

    @Override
    public double getMinWeight( double distance )
    {
        return superWeighting.getMinWeight(distance);
    }

    @Override
    public double calcWeight( EdgeIteratorState edgeState, boolean reverse, int prevOrNextEdgeId )
    {
        if (turnCostStorage == null)
            throw new RuntimeException("No storage set to calculate turn weight");

        // TODO
        // if (edgeFrom==edgeTo){
        // // prevent U turn in A* bidirectional EDGE_BASED
        // return Double.MAX_VALUE;
        // }
        double weight = superWeighting.calcWeight(edgeState, reverse, prevOrNextEdgeId);
        if (reverse)
            return weight * calcTurnWeight(edgeState.getEdge(), edgeState.getBaseNode(), prevOrNextEdgeId);
        else
            return weight * calcTurnWeight(prevOrNextEdgeId, edgeState.getBaseNode(), edgeState.getEdge());
    }

    protected double calcTurnWeight( int edgeTo, int nodeVia, int edgeFrom )
    {
        int turnFlags = turnCostStorage.getTurnCostsFlags(nodeVia, edgeFrom, edgeTo);
        if (turnCostEncoder.isTurnRestricted(turnFlags))
            return Double.MAX_VALUE;

        return turnCostEncoder.getTurnCosts(turnFlags);
    }

    @Override
    public String toString()
    {
        return "TURN|" + superWeighting.toString();
    }
}
