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

import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.Weighting;
import com.graphhopper.storage.Graph;

/**
 * @author Peter Karich
 */
public class RoutingAlgorithmFactory
{
    private String algoStr;
    private boolean approx;

    /**
     * @param algo possible values are astar (A* algorithm), astarbi (bidirectional A*) dijkstra
     * (Dijkstra), dijkstrabi and dijkstraNativebi (a bit faster bidirectional Dijkstra).
     */
    public RoutingAlgorithmFactory( String algo, boolean approx )
    {
        this.algoStr = algo;
        this.approx = approx;
    }

    public RoutingAlgorithm createAlgo( Graph g, FlagEncoder encoder, Weighting weighting )
    {
        if ("dijkstrabi".equalsIgnoreCase(algoStr))
        {
            return new DijkstraBidirectionRef(g, encoder, weighting);
        } else if ("dijkstraNativebi".equalsIgnoreCase(algoStr))
        {
            return new DijkstraBidirection(g, encoder, weighting);
        } else if ("dijkstra".equalsIgnoreCase(algoStr))
        {
            return new Dijkstra(g, encoder, weighting);
        } else if ("astarbi".equalsIgnoreCase(algoStr))
        {
            return new AStarBidirection(g, encoder, weighting).setApproximation(approx);
        } else if ("dijkstraOneToMany".equalsIgnoreCase(algoStr))
        {
            return new DijkstraOneToMany(g, encoder, weighting);
        } else
        {
            return new AStar(g, encoder, weighting);
        }
    }
}
