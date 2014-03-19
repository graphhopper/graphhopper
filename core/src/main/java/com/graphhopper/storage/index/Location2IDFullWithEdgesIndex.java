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
package com.graphhopper.storage.index;

import com.graphhopper.routing.util.AllEdgesIterator;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.DistancePlaneProjection;
import com.graphhopper.util.DistanceCalc;
import com.graphhopper.util.DistanceCalcEarth;

/**
 * Same as full index but calculates distance to all edges too
 * <p/>
 * @author Peter Karich
 */
public class Location2IDFullWithEdgesIndex implements LocationIndex
{
    private DistanceCalc calc = new DistanceCalcEarth();
    private Graph graph;

    public Location2IDFullWithEdgesIndex( Graph g )
    {
        this.graph = g;
    }

    @Override
    public boolean loadExisting()
    {
        return true;
    }

    @Override
    public LocationIndex setResolution( int resolution )
    {
        return this;
    }

    @Override
    public LocationIndex setApproximation( boolean approxDist )
    {
        if (approxDist)
        {
            calc = new DistancePlaneProjection();
        } else
        {
            calc = new DistanceCalcEarth();
        }
        return this;
    }

    @Override
    public LocationIndex prepareIndex()
    {
        return this;
    }

    @Override
    public int findID( double lat, double lon )
    {
        return findClosest(lat, lon, EdgeFilter.ALL_EDGES).getClosestNode();
    }

    @Override
    public QueryResult findClosest( double queryLat, double queryLon, EdgeFilter filter )
    {
        QueryResult res = new QueryResult(queryLat, queryLon);
        double foundDist = Double.MAX_VALUE;
        AllEdgesIterator iter = graph.getAllEdges();
        while (iter.next())
        {
            if (!filter.accept(iter))
            {
                continue;
            }
            for (int i = 0, node; i < 2; i++)
            {
                if (i == 0)
                {
                    node = iter.getBaseNode();
                } else
                {
                    node = iter.getAdjNode();
                }

                double fromLat = graph.getLatitude(node);
                double fromLon = graph.getLongitude(node);
                double fromDist = calc.calcDist(fromLat, fromLon, queryLat, queryLon);
                if (fromDist < 0)
                    continue;

                if (fromDist < foundDist)
                {
                    res.setQueryDistance(fromDist);
                    res.setClosestEdge(iter.detach(false));
                    res.setClosestNode(node);
                    foundDist = fromDist;
                }

                // process the next stuff only for baseNode
                if (i > 0)
                    continue;

                int toNode = iter.getAdjNode();
                double toLat = graph.getLatitude(toNode);
                double toLon = graph.getLongitude(toNode);

                if (calc.validEdgeDistance(queryLat, queryLon,
                        fromLat, fromLon, toLat, toLon))
                {
                    double distEdge = calc.calcDenormalizedDist(calc.calcNormalizedEdgeDistance(queryLat, queryLon,
                            fromLat, fromLon, toLat, toLon));
                    if (distEdge < foundDist)
                    {
                        res.setQueryDistance(distEdge);
                        res.setClosestNode(node);
                        res.setClosestEdge(iter);
                        if (fromDist > calc.calcDist(toLat, toLon, queryLat, queryLon))
                            res.setClosestNode(toNode);

                        foundDist = distEdge;
                    }
                }
            }
        }
        return res;
    }

    @Override
    public LocationIndex create( long size )
    {
        return this;
    }

    @Override
    public void flush()
    {
    }

    @Override
    public void close()
    {
    }

    @Override
    public long getCapacity()
    {
        return 0;
    }

    @Override
    public void setSegmentSize( int bytes )
    {
    }
}
