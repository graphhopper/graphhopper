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

import com.graphhopper.routing.Path;
import com.graphhopper.routing.RoutingAlgorithm;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.DistanceCalc;
import com.graphhopper.util.DistanceCalcEarth;
import com.graphhopper.util.PointList;
import com.graphhopper.util.shapes.GHPoint;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Peter Karich
 */
public class TestAlgoCollector
{
    private final String name;
    private final DistanceCalc distCalc = new DistanceCalcEarth();
    public List<String> errors = new ArrayList<String>();

    public TestAlgoCollector( String name )
    {
        this.name = name;
    }

    public TestAlgoCollector assertDistance( RoutingAlgorithm algo,
            QueryResult from, QueryResult to, double distance, int pointCount )
    {
        Path path = algo.calcPath(from, to);
        if (!path.isFound())
        {
            errors.add(algo + " returns no path! expected distance: " + distance
                    + ", expected locations: " + pointCount + ". from:" + from + ", to:" + to);
            return this;
        }

        PointList pointList = path.calcPoints();
        double tmpDist = pointList.calcDistance(distCalc);
        if (Math.abs(path.getDistance() - tmpDist) > 5)
        {
            errors.add(algo + " path.getDistance was  " + path.getDistance()
                    + "\t pointList.calcDistance was " + tmpDist + "\t (expected points " + pointCount
                    + ", expected distance " + distance + ") from:" + from + ", to:" + to);
        }

        if (Math.abs(path.getDistance() - distance) > 4)
        {
            errors.add(algo + " returns path not matching the expected distance of " + distance
                    + "\t Returned was " + path.getDistance() + "\t (expected points " + pointCount
                    + ", was " + pointList.getSize() + ") from:" + from + ", to:" + to);
        }

        // There are real world instances where A-B-C is identical to A-C (in meter precision).
        if (Math.abs(pointList.getSize() - pointCount) > 4)
        {
            errors.add(algo + " returns path not matching the expected points of " + pointCount
                    + "\t Returned was " + pointList.getSize() + "\t (expected distance " + distance
                    + ", was " + path.getDistance() + ") from:" + from + ", to:" + to);
        }
        return this;
    }

    void queryIndex( Graph g, LocationIndex idx, double lat, double lon, double expectedDist )
    {
        QueryResult res = idx.findClosest(lat, lon, EdgeFilter.ALL_EDGES);
        if (!res.isValid())
        {
            errors.add("node not found for " + lat + "," + lon);
            return;
        }

        GHPoint found = res.getSnappedPoint();
        double dist = distCalc.calcDist(lat, lon, found.lat, found.lon);
        if (Math.abs(dist - expectedDist) > .1)
        {
            errors.add("queried lat,lon=" + (float) lat + "," + (float) lon
                    + " (found: " + (float) found.lat + "," + (float) found.lon + ")"
                    + "\n   expected distance:" + expectedDist + ", but was:" + dist);
        }
    }

    @Override
    public String toString()
    {
        String str = "";
        str += "FOUND " + errors.size() + " ERRORS.\n";
        for (String s : errors)
        {
            str += s + ".\n";
        }
        return str;
    }

    void printSummary()
    {
        if (errors.size() > 0)
        {
            System.out.println("\n-------------------------------\n");
            System.out.println(toString());
        } else
        {
            System.out.println("SUCCESS for " + name + "!");
        }
    }
}
