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
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.util.Weighting;
import com.graphhopper.storage.Graph;

/**
 * @author Peter Karich
 */
public class RoutingAlgorithmFactory
{
    private final String algoStr;
    private final boolean approx;
    private final TraversalMode traversalMode;

    /**
     * @param algo possible values are astar (A* algorithm), astarbi (bidirectional A*), dijkstra
     * (Dijkstra) or dijkstrabi.
     */
    public RoutingAlgorithmFactory( String algo, boolean approx, TraversalMode tMode )
    {
        this.algoStr = algo;
        this.approx = approx;
        this.traversalMode = tMode;
    }

    public RoutingAlgorithm createAlgo( Graph g, FlagEncoder encoder, Weighting weighting )
    {
        AbstractRoutingAlgorithm algo;
        if ("dijkstrabi".equalsIgnoreCase(algoStr))
        {
            algo = new DijkstraBidirectionRef(g, encoder, weighting, traversalMode);        
        } else if ("dijkstra".equalsIgnoreCase(algoStr))
        {
            algo = new Dijkstra(g, encoder, weighting, traversalMode);
        } else if ("astarbi".equalsIgnoreCase(algoStr))
        {
            algo = new AStarBidirection(g, encoder, weighting, traversalMode).setApproximation(approx);
        } else if ("dijkstraOneToMany".equalsIgnoreCase(algoStr))
        {
            algo = new DijkstraOneToManyRef(g, encoder, weighting, traversalMode);
        } else
        {
            algo = new AStar(g, encoder, weighting, traversalMode);
        }
        
        return algo;
    }
}
