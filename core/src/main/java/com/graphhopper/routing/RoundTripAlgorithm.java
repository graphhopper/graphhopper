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

import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.UniquePathWeighting;
import com.graphhopper.routing.util.Weighting;
import com.graphhopper.routing.util.tour.TourWayPointGenerator;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.util.shapes.GHPoint;

import java.util.ArrayList;
import java.util.List;

/**
 * This class implements the round trip calculation via randomly generated points
 * <p>
 * @author Robin Boldt
 */
public class RoundTripAlgorithm implements RoutingAlgorithm
{
    private final Weighting weighting;
    private LocationIndex locationIndex = null;
    private EdgeFilter edgeFilter = null;
    private GHPoint from = null;
    private final double distanceInKm;
    private Graph g;
    private AlgorithmOptions opts;
    private int numberOfVisitedNodes = 0;
    private double weightLimit;

    public RoundTripAlgorithm( Weighting weighting, double distanceInKm, Graph g, AlgorithmOptions opts )
    {
        this.distanceInKm = distanceInKm;
        this.g = g;
        this.opts = opts;
        if (!(weighting instanceof UniquePathWeighting))
            weighting = new UniquePathWeighting(weighting);
        this.weighting = weighting;
    }

    public void prepare( LocationIndex locationIndex, EdgeFilter edgeFilter, GHPoint from )
    {
        this.locationIndex = locationIndex;
        this.edgeFilter = edgeFilter;
        this.from = from;
    }

    private RoutingAlgorithm getRoutingAlgorithm()
    {
        return new DijkstraBidirectionRef(g, opts.getFlagEncoder(), this.weighting, opts.getTraversalMode());
    }

    public List<Path> calcRoundTrips()
    {
        List<Integer> points = TourWayPointGenerator.generateTour(from, locationIndex, edgeFilter, this.distanceInKm);
        List<Path> pathList = new ArrayList<Path>(points.size() - 1);
        RoutingAlgorithm routingAlgorithm;

        for (int i = 1; i < points.size(); i++)
        {
            routingAlgorithm = this.getRoutingAlgorithm();
            routingAlgorithm.setWeightLimit(weightLimit);
            Path path = routingAlgorithm.calcPath(points.get(i - 1), points.get(i));
            pathList.add(path);
            this.numberOfVisitedNodes += routingAlgorithm.getVisitedNodes();
            if (weighting instanceof UniquePathWeighting)
                ((UniquePathWeighting) weighting).addPath(path);
        }

        return pathList;
    }

    @Override
    public Path calcPath( int notNeeded, int notNeededAsWell )
    {
        throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    public List<Path> calcPaths( int notNeeded, int notNeededAsWell )
    {
        if (locationIndex == null)
        {
            throw new IllegalStateException("You have to call prepare before calculating Paths");
        }
        return calcRoundTrips();
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
