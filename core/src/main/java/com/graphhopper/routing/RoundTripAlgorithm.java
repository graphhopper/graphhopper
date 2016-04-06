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

import com.graphhopper.routing.util.AvoidPathWeighting;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.util.Weighting;
import com.graphhopper.routing.util.tour.TourPointGenerator;
import com.graphhopper.storage.Graph;

import java.util.ArrayList;
import java.util.List;

/**
 * This class implements the round trip calculation via randomly generated points
 * <p/>
 * @author Robin Boldt
 */
public class RoundTripAlgorithm implements RoutingAlgorithm
{
    private final Graph g;
    private final AvoidPathWeighting avoidPathWeighting;
    private final FlagEncoder flagEncoder;
    private final TraversalMode traversalMode;
    private TourPointGenerator generator;
    private int numberOfVisitedNodes = 0;
    private double weightLimit = Double.MAX_VALUE;

    public RoundTripAlgorithm( Graph g, FlagEncoder flagEncoder, Weighting weighting, TraversalMode traversalMode )
    {
        this.g = g;
        this.flagEncoder = flagEncoder;
        if (!(weighting instanceof AvoidPathWeighting))
            this.avoidPathWeighting = new AvoidPathWeighting(weighting);
        else
            this.avoidPathWeighting = (AvoidPathWeighting) weighting;

        this.traversalMode = traversalMode;
    }

    public void setTourPointGenerator( TourPointGenerator generator )
    {
        this.generator = generator;
    }

    /**
     * This method calculates the round trip consisting of multiple paths.
     */
    List<Path> calcRoundTrip()
    {
        List<Integer> points = generator.calculatePoints();
        List<Path> pathList = new ArrayList<Path>(points.size() - 1);

        for (int i = 1; i < points.size(); i++)
        {
            RoutingAlgorithm routingAlgorithm = new DijkstraBidirectionRef(g, flagEncoder, avoidPathWeighting, traversalMode);
            routingAlgorithm.setWeightLimit(weightLimit);
            Path path = routingAlgorithm.calcPath(points.get(i - 1), points.get(i));
            pathList.add(path);
            numberOfVisitedNodes += routingAlgorithm.getVisitedNodes();

            // it is important to avoid previously visited nodes for future paths
            avoidPathWeighting.addPath(path);
        }

        return pathList;
    }

    @Override
    public Path calcPath( int from, int to )
    {
        throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    public List<Path> calcPaths( int fromUnused, int toUnused )
    {
        return calcRoundTrip();
    }

    @Override
    public void setWeightLimit( double weightLimit )
    {
        this.weightLimit = weightLimit;
    }

    @Override
    public String getName()
    {
        return AlgorithmOptions.ROUND_TRIP;
    }

    @Override
    public int getVisitedNodes()
    {
        return this.numberOfVisitedNodes;
    }

}
