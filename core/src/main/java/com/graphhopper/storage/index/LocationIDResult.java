/*
 *  Licensed to GraphHopper and Peter Karich under one or more contributor license
 *  agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the
 *  License at
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

import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.shapes.CoordTrig;

/**
 * Result of Location2IDIndex lookup.
 * <p/>
 * <
 * pre> X=query coordinates S=snapped coordinates: "snapping" real coords to road N=tower or pillar
 * node T=closest tower node XS=distance
 * <p/>
 * X
 * |
 * T--S----N
 * <p/>
 * </pre>
 * <p/>
 * @author Peter Karich
 */
public class LocationIDResult
{
    private double queryDistance = Double.MAX_VALUE;
    private double basedDistance = 0;
    private double adjDistance = 0;
    private int wayIndex = -1;
    private int closestNode = -1;
    private EdgeIteratorState edgeState;
    private CoordTrig queryPoint;
    private CoordTrig snappedPoint;

    public LocationIDResult()
    {
    }

    public void setClosestNode( int node )
    {
        closestNode = node;
    }

    /**
     * @return the closest matching node. -1 if nothing found or call isValid before.
     */
    public int getClosestNode()
    {
        return closestNode;
    }

    public void setQueryDistance( double dist )
    {
        queryDistance = dist;
    }

    /**
     * @return the distance of the query to the snapped coordinates. In meter
     */
    public double getQueryDistance()
    {
        return queryDistance;
    }

    public void setBasedDistance( double dist )
    {
        basedDistance = dist;
    }

    /**
     * @return the distance from the base node to the snapped point. In meter
     */
    public double getBasedDistance()
    {
        return basedDistance;
    }

    public void setAdjDistance( double adjDistance )
    {
        this.adjDistance = adjDistance;
    }

    /**
     * @return the distance from the adjacent node to the snapped point. In meter
     */
    public double getAdjDistance()
    {
        return adjDistance;
    }

    /**
     * References to a tower node or the index of wayGeometry of the closest edge. If wayGeometry
     * has lengh L then the wayIndex 0 refers to the *base* node, 1 to L (inclusive) refer to the
     * wayGeometry indices (minus one) and L+1 to the *adjacent* node. Currently only supported if
     * returned from Location2NodesNtree.
     */
    public void setWayIndex( int wayIndex )
    {
        this.wayIndex = wayIndex;
    }

    /**
     * @return true if a close node was found
     */
    public boolean isValid()
    {
        return closestNode >= 0;
    }

    public void setClosestEdge( EdgeIteratorState detach )
    {
        edgeState = detach;
    }

    /**
     * @return the closest matching edge. Will be null if nothing found or call isValid before
     */
    public EdgeIteratorState getClosestEdge()
    {
        return edgeState;
    }

    public void setQueryPoint( CoordTrig queryPoint )
    {
        this.queryPoint = queryPoint;
    }

    public CoordTrig getQueryPoint()
    {
        return queryPoint;
    }

    public void setSnappedPoint( CoordTrig snappedPoint )
    {
        this.snappedPoint = snappedPoint;
    }

    /**
     * @return the position of the query point 'snapped' to a road segment or node. Can be null if
     * no result found.
     */
    public CoordTrig getSnappedPoint()
    {
        return snappedPoint;
    }

    @Override
    public String toString()
    {
        return closestNode + ", " + queryDistance + ", " + wayIndex;
    }
}
