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
public class AStarBidirectionTest extends AbstractRoutingAlgorithmTester
{
    /**
     * Runs the same test with each of the supported traversal modes
     */
    @Parameters
    public static Collection<Object[]> configs()
    {
        return Arrays.asList(new Object[][]
        {
            {
                true
            }, 
            {
                false
            }
        });
    }

    private final boolean edgeBased;

    public AStarBidirectionTest( boolean eb )
    {
        this.edgeBased = eb;
    }

    @Override
    public AlgorithmPreparation prepareGraph( Graph g, final FlagEncoder encoder, final Weighting w )
    {
        return new NoOpAlgorithmPreparation()
        {
            @Override
            public RoutingAlgorithm createAlgo()
            {
                return new AStarBidirection(_graph, encoder, w, edgeBased);
            }
        }.setGraph(g);
    }
}
