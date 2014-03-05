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

import com.graphhopper.util.EdgeIteratorState;

/**
 * Calculates the shortest route - independent of a vehicle as the calculation is based on the
 * distance only.
 * <p/>
 * @author Peter Karich
 * @author Karl Hübner
 */
public class ShortestWeighting extends AbstractTurnWeighting implements Weighting
{
    public ShortestWeighting( TurnCostEncoder turnCostEncoder )
    {
        super(turnCostEncoder);
    }

    @Override
    public double getMinWeight( double currDistToGoal )
    {
        return currDistToGoal;
    }

    @Override
    public double calcWeight( EdgeIteratorState edge, boolean reverse )
    {
        return edge.getDistance();
    }

    @Override
    public String toString()
    {
        return "SHORTEST";
    }

    @Override
    protected double calcTurnWeight( int edgeFrom, int nodeVia, int edgeTo )
    {
        int turnFlags = turnCostStorage.getTurnCosts(edgeFrom, nodeVia, edgeTo);
        if (isEnabledTurnRestrictions() && turnCostEncoder.isTurnRestricted(turnFlags))
        {
            //we only consider turn restrictions in shortest calculation
            return Double.MAX_VALUE;
        }
        return 0;
    }

    @Override
    public boolean isEnabledTurnCosts()
    {
        return false;
    }
}
