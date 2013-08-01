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

/**
 * Same as full index but calculates distance to all edges too
 * <p/>
 * @author Peter Karich
 */
public class Location2IDFullWithEdgesIndex implements Location2IDIndex
{
    private DistanceCalc calc = new DistanceCalc();
    private Graph g;

    public Location2IDFullWithEdgesIndex( Graph g )
    {
        this.g = g;
    }

    @Override
    public boolean loadExisting()
    {
        return true;
    }

    @Override
    public Location2IDIndex setResolution( int resolution )
    {
        return this;
    }

    @Override
    public Location2IDIndex setApproximation( boolean approxDist )
    {
        if (approxDist)
        {
            calc = new DistancePlaneProjection();
        } else
        {
            calc = new DistanceCalc();
        }
        return this;
    }

    @Override
    public Location2IDIndex prepareIndex()
    {
        return this;
    }

    @Override
    public int findID( double lat, double lon )
    {
        return findClosest(lat, lon, EdgeFilter.ALL_EDGES).getClosestNode();
    }

    @Override
    public LocationIDResult findClosest( double queryLat, double queryLon, EdgeFilter filter )
    {
        int nodes = g.getNodes();
        LocationIDResult res = new LocationIDResult();
        double foundDist = Double.MAX_VALUE;
        AllEdgesIterator iter = g.getAllEdges();
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

                double fromLat = g.getLatitude(node);
                double fromLon = g.getLongitude(node);
                double fromDist = calc.calcDist(fromLat, fromLon, queryLat, queryLon);
                if (fromDist < 0)
                {
                    continue;
                }

                if (fromDist < foundDist)
                {
                    res.setClosestNode(node);
                    foundDist = fromDist;
                }

                // process the next stuff only for baseNode
                if (i > 0)
                {
                    continue;
                }
                int toNode = iter.getAdjNode();
                double toLat = g.getLatitude(toNode);
                double toLon = g.getLongitude(toNode);

                if (calc.validEdgeDistance(queryLat, queryLon,
                        fromLat, fromLon, toLat, toLon))
                {
                    double distEdge = calc.calcDenormalizedDist(calc.calcNormalizedEdgeDistance(queryLat, queryLon,
                            fromLat, fromLon, toLat, toLon));
                    if (distEdge < foundDist)
                    {
                        res.setClosestNode(node);
                        if (fromDist > calc.calcDist(toLat, toLon, queryLat, queryLon))
                        {
                            res.setClosestNode(toNode);
                        }
                        foundDist = distEdge;
                    }
                }
            }
        }
        return res;
    }

    @Override
    public Location2IDIndex create( long size )
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
