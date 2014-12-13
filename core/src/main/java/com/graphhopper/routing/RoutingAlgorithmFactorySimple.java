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

import com.graphhopper.routing.util.BeelineWeightApproximator;
import com.graphhopper.routing.util.WeightApproximator;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.DistanceCalcEarth;
import com.graphhopper.util.DistancePlaneProjection;

/**
 * A simple factory creating normal algorithms (RoutingAlgorithm) without preparation.
 * <p>
 * @author Peter Karich
 */
public class RoutingAlgorithmFactorySimple implements RoutingAlgorithmFactory
{
    @Override
    public RoutingAlgorithm createAlgo( Graph g, AlgorithmOptions opts )
    {
        AbstractRoutingAlgorithm algo;
        String algoStr = opts.getAlgorithm();
        if (AlgorithmOptions.DIJKSTRA_BI.equalsIgnoreCase(algoStr))
        {
            return new DijkstraBidirectionRef(g, opts.getFlagEncoder(), opts.getWeighting(), opts.getTraversalMode());
        } else if (AlgorithmOptions.DIJKSTRA.equalsIgnoreCase(algoStr))
        {
            return new Dijkstra(g, opts.getFlagEncoder(), opts.getWeighting(), opts.getTraversalMode());
        } else if (AlgorithmOptions.ASTAR_BI.equalsIgnoreCase(algoStr))
        {
            AStarBidirection aStarBi = new AStarBidirection(g, opts.getFlagEncoder(), opts.getWeighting(),
                                                            opts.getTraversalMode());
            String approximation = opts.getHints().get(AlgorithmOptions.ASTAR_BI + ".approximation",
                                                       "BeelineSimplification");
            if (approximation == "BeelineSimplification") {
                BeelineWeightApproximator approx = new BeelineWeightApproximator(aStarBi.nodeAccess, aStarBi.weighting);
                approx.setDistanceCalc(new DistancePlaneProjection());
                aStarBi.setApproximation(approx);
            }
            else if (approximation == "BeelineAccurate")
            {
                BeelineWeightApproximator approx = new BeelineWeightApproximator(aStarBi.nodeAccess, aStarBi.weighting);
                approx.setDistanceCalc(new DistanceCalcEarth());
                aStarBi.setApproximation(approx);
            } else
            {
                throw new IllegalArgumentException("Approximation " + approximation + " not found in " + getClass().getName());
            }

            return aStarBi;
        } else if (AlgorithmOptions.DIJKSTRA_ONE_TO_MANY.equalsIgnoreCase(algoStr))
        {
            return new DijkstraOneToMany(g, opts.getFlagEncoder(), opts.getWeighting(), opts.getTraversalMode());
        } else if (AlgorithmOptions.ASTAR.equalsIgnoreCase(algoStr))
        {
            AStar aStar = new AStar(g, opts.getFlagEncoder(), opts.getWeighting(),opts.getTraversalMode());
            String approximation = opts.getHints().get(AlgorithmOptions.ASTAR + ".approximation",
                                                       "BeelineSimplification");
            if (approximation == "BeelineSimplification") {
                BeelineWeightApproximator approx = new BeelineWeightApproximator(aStar.nodeAccess, aStar.weighting);
                approx.setDistanceCalc(new DistancePlaneProjection());
                aStar.setApproximation(approx);
            }
            else if (approximation == "BeelineAccurate")
            {
                BeelineWeightApproximator approx = new BeelineWeightApproximator(aStar.nodeAccess, aStar.weighting);
                approx.setDistanceCalc(new DistanceCalcEarth());
                aStar.setApproximation(approx);
            } else
            {
                throw new IllegalArgumentException("Approximation " + approximation + " not found in " + getClass().getName());
            }
            return aStar;
        } else
        {
            throw new IllegalArgumentException("Algorithm " + algoStr + " not found in " + getClass().getName());
        }

    }
}
