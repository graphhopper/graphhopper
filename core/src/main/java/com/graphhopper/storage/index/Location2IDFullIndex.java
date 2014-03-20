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
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.util.DistanceCalc;
import com.graphhopper.util.DistanceCalcEarth;
import com.graphhopper.util.DistancePlaneProjection;
import com.graphhopper.util.shapes.Circle;

/**
 * Very slow O(n) LocationIndex but no RAM/disc required.
 * <p/>
 * @author Peter Karich
 */
public class Location2IDFullIndex implements LocationIndex
{
    private DistanceCalc calc = new DistancePlaneProjection();
    private final Graph graph;
    private final NodeAccess nodeAccess;

    public Location2IDFullIndex( Graph g )
    {
        this.graph = g;
        this.nodeAccess = g.getNodeAccess();
    }

    @Override
    public boolean loadExisting()
    {
        return true;
    }

    @Override
    public LocationIndex setApproximation( boolean approxDist )
    {
        if (approxDist)
            calc = new DistancePlaneProjection();
        else
            calc = new DistanceCalcEarth();
        
        return this;
    }

    @Override
    public LocationIndex setResolution( int resolution )
    {
        return this;
    }

    @Override
    public LocationIndex prepareIndex()
    {
        return this;
    }

    @Override
    public QueryResult findClosest( double queryLat, double queryLon, EdgeFilter edgeFilter )
    {
        QueryResult res = new QueryResult(queryLat, queryLon);
        Circle circle = null;
        AllEdgesIterator iter = graph.getAllEdges();
        while (iter.next())
        {
            if (!edgeFilter.accept(iter))
                continue;

            for (int node, i = 0; i < 2; i++)
            {
                if (i == 0)
                {
                    node = iter.getBaseNode();
                } else
                {
                    node = iter.getAdjNode();
                }
                double tmpLat = nodeAccess.getLatitude(node);
                double tmpLon = nodeAccess.getLongitude(node);
                double dist = calc.calcDist(tmpLat, tmpLon, queryLat, queryLon);
                if (circle == null || dist < calc.calcDist(circle.getLat(), circle.getLon(), queryLat, queryLon))
                {
                    res.setClosestEdge(iter.detach(false));
                    res.setClosestNode(node);
                    res.setQueryDistance(dist);
                    if (dist <= 0)
                        break;

                    circle = new Circle(tmpLat, tmpLon, dist, calc);
                }
            }
        }
        return res;
    }

    @Override
    public int findID( double lat, double lon )
    {
        return findClosest(lat, lon, EdgeFilter.ALL_EDGES).getClosestNode();
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
