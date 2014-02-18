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

import com.graphhopper.routing.RoutingAlgorithm;
import com.graphhopper.routing.RoutingAlgorithmFactory;
import com.graphhopper.storage.Graph;

/**
 * @author Peter Karich
 */
public abstract class NoOpAlgorithmPreparation extends AbstractAlgoPreparation<NoOpAlgorithmPreparation>
{
    public NoOpAlgorithmPreparation()
    {
    }

    /**
     * Creates a preparation wrapper for the specified algorithm. Possible values for algorithmStr:
     * astar (A* algorithm), astarbi (bidirectional A*) dijkstra (Dijkstra), dijkstrabi and
     * dijkstraNativebi (a bit faster bidirectional Dijkstra).
     */
    public static AlgorithmPreparation createAlgoPrepare( Graph g, final String algorithmStr,
            FlagEncoder encoder, Weighting weighting )
    {
        return p(new RoutingAlgorithmFactory(algorithmStr, false), encoder, weighting).setGraph(g);
    }

    private static AlgorithmPreparation p( final RoutingAlgorithmFactory factory,
            final FlagEncoder encoder, final Weighting weighting )
    {
        return new NoOpAlgorithmPreparation()
        {
            @Override
            public RoutingAlgorithm createAlgo()
            {
                try
                {
                    return factory.createAlgo(_graph, encoder, weighting);
                } catch (Exception ex)
                {
                    throw new RuntimeException(ex);
                }
            }

            @Override
            public String toString()
            {
                return createAlgo().getName() + ", " + encoder + ", " + weighting;
            }
        };
    }
}
