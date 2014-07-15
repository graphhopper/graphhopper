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
package com.graphhopper.routing;

import com.graphhopper.routing.RoutingAlgorithm.TRAVERSAL_MODE;
import java.util.Arrays;
import java.util.Collection;

import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.graphhopper.routing.util.AlgorithmPreparation;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.NoOpAlgorithmPreparation;
import com.graphhopper.routing.util.Weighting;
import com.graphhopper.storage.Graph;

/**
 *
 * @author Peter Karich
 */
@RunWith(Parameterized.class)
public class DijkstraTest extends AbstractRoutingAlgorithmTester
{
    /**
     * Runs the same test with each of the supported traversal modes
     */
    @Parameters
    public static Collection<Object[]> configs()
    {
        return Arrays.asList(new Object[][]
        {
            { TRAVERSAL_MODE.NODE_BASED },
            { TRAVERSAL_MODE.EDGE_BASED_DIRECTION_SENSITIVE }
        });
    }

    private TRAVERSAL_MODE traversalMode;

    public DijkstraTest( TRAVERSAL_MODE traversalMode )
    {
        this.traversalMode = traversalMode;
    }

    @Override
    public AlgorithmPreparation prepareGraph( Graph defaultGraph, final FlagEncoder encoder, final Weighting weighting )
    {
        return new NoOpAlgorithmPreparation()
        {
            @Override
            public RoutingAlgorithm createAlgo()
            {
                Dijkstra dijkstra = new Dijkstra(_graph, encoder, weighting);
                dijkstra.setTraversalMode(traversalMode);
                return dijkstra;
            }
        }.setGraph(defaultGraph);
    }
}
