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

import com.graphhopper.routing.Path;
import com.graphhopper.util.EdgeIteratorState;
import gnu.trove.set.TIntSet;
import gnu.trove.set.hash.TIntHashSet;

/**
 * Rates already used Paths worse.
 *
 * @author RobinBoldt
 */
public class UniquePathWeighting extends AbstractAdjustedWeighting
{
    // Contains the EdgeIds of the already visisted Edges
    protected final TIntSet visitedEdges = new TIntHashSet();

    public static int ALREADY_VISISTED_EDGES_PENALTY = 5;

    public UniquePathWeighting( Weighting superWeighting )
    {
        super(superWeighting);
    }

    public void addPath( Path path )
    {
        for (EdgeIteratorState edge : path.calcEdges())
        {
            visitedEdges.add(edge.getEdge());
        }
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

        if (visitedEdges.contains(edgeState.getEdge()))
        {
            weight = weight * ALREADY_VISISTED_EDGES_PENALTY;
        }

        return weight;
    }

    @Override
    public String getName()
    {
        return "unique_path";
    }
}