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
package com.graphhopper.routing.util.tour;

import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.CoordinateProjection;
import com.graphhopper.util.shapes.GHPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class TourWayPointGenerator
{

    public static int INITIAL_TRIES = 40;
    public static int RESET_TRIES = 2;
    public static int MAX_TRIES = 50;

    private static final Logger logger = LoggerFactory.getLogger(TourWayPointGenerator.class);

    public static List<Integer> generateTour( GHPoint start, LocationIndex locationIndex, EdgeFilter edgeFilter, double distanceInKm)
    {
        return generateTour(start, locationIndex, edgeFilter, TourStrategyFactory.getStrategy(distanceInKm));
    }

    public static List<Integer> generateTour( GHPoint start, LocationIndex locationIndex, EdgeFilter edgeFilter, TourStrategy strategy )
    {
        List<Integer> tour = new ArrayList<Integer>(2 + strategy.numberOfGeneratedPoints());
        int startIndex = locationIndex.findClosest(start.lat, start.lon, edgeFilter).getClosestNode();
        tour.add(startIndex);

        GHPoint last = start;
        QueryResult result;
        for (int i = 0; i < strategy.numberOfGeneratedPoints(); i++)
        {
            result = generateValidPoint(last, strategy.getDistanceForIteration(i), strategy.getBearingForIteration(i), locationIndex, edgeFilter, INITIAL_TRIES);
            last = result.getSnappedPoint();
            tour.add(result.getClosestNode());
        }

        tour.add(startIndex);
        return tour;
    }

    private static QueryResult generateValidPoint( GHPoint from, double distanceInKm, double bearing, LocationIndex locationIndex, EdgeFilter edgeFilter, int triesAvailable )
    {
        int counter = 0;
        while (true)
        {
            GHPoint generatedPoint = CoordinateProjection.projectCoordinate(from.getLat(), from.getLon(), distanceInKm, bearing);
            QueryResult qr = locationIndex.findClosest(generatedPoint.getLat(), generatedPoint.getLon(), edgeFilter);
            if (qr.isValid())
            {
                return qr;
            }

            triesAvailable--;
            counter++;

            // The idea is that if we cannot find any valid points around a coordinate, we reduce the distance, because it could be that there are no points in the area
            if (triesAvailable <= 0)
            {
                triesAvailable = RESET_TRIES;
                distanceInKm = distanceInKm / 3;
                logger.debug("Cannot find anything, reducing the distance to: " + distanceInKm);
            }

            // Last try with 1km distance
            if (counter == MAX_TRIES)
            {
                distanceInKm = 1;
            }

            if (counter >= MAX_TRIES)
            {
                throw new IllegalStateException("Could not find a valid point after " + counter + " iterations, for the Point:" + from);
            }
        }
    }
}