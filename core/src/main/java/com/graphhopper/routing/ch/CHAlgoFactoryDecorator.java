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
package com.graphhopper.routing.ch;

import com.graphhopper.routing.RoutingAlgorithmFactory;
import com.graphhopper.routing.RoutingAlgorithmFactoryDecorator;
import com.graphhopper.routing.util.HintsMap;
import com.graphhopper.routing.util.Weighting;

import java.util.ArrayList;
import java.util.List;

/**
 * This class implements the CH decorator for the routing algorithm factory.
 * @author Peter Karich
 */
public class CHAlgoFactoryDecorator implements RoutingAlgorithmFactoryDecorator
{
    private final List<PrepareContractionHierarchies> preparations = new ArrayList<PrepareContractionHierarchies>();
    private final List<Weighting> weightings = new ArrayList<Weighting>();

    /**
     * Decouple weightings from PrepareContractionHierarchies as we need weightings for the
     * graphstorage and the graphstorage for the preparation.
     */
    public CHAlgoFactoryDecorator addWeighting( Weighting weighting )
    {
        weightings.add(weighting);
        return this;
    }

    public CHAlgoFactoryDecorator add( PrepareContractionHierarchies pch )
    {
        preparations.add(pch);
        int lastIndex = preparations.size() - 1;
        if (lastIndex >= weightings.size())
            throw new IllegalStateException("Cannot access weighting for PrepareContractionHierarchies with " + pch.getWeighting()
                    + ". Call add(Weighting) before");

        if (preparations.get(lastIndex).getWeighting() != weightings.get(lastIndex))
            throw new IllegalArgumentException("Weighting of PrepareContractionHierarchies " + preparations.get(lastIndex).getWeighting()
                    + " needs to be identical to previously added " + weightings.get(lastIndex));
        return this;
    }

    public boolean hasWeightings()
    {
        return !weightings.isEmpty();
    }

    public boolean hasPreparations()
    {
        return !preparations.isEmpty();
    }

    public int size()
    {
        return preparations.size();
    }

    public List<Weighting> getWeightings()
    {
        return weightings;
    }

    public List<PrepareContractionHierarchies> getPreparations()
    {
        return preparations;
    }

    @Override
    public RoutingAlgorithmFactory decorate( RoutingAlgorithmFactory defaultAlgoFactory, HintsMap map )
    {
        boolean forceFlexMode = map.getBool("routing.flexibleMode.force", false);
        if (forceFlexMode)
            return defaultAlgoFactory;

        if (preparations.isEmpty())
            throw new IllegalStateException("No preparations added to this decorator");

        for (PrepareContractionHierarchies p : preparations)
        {
            if (p.getWeighting().matches(map))
                return p;
        }

        throw new IllegalStateException("Cannot find RoutingAlgorithFactory for weighting map " + map);
    }
}
