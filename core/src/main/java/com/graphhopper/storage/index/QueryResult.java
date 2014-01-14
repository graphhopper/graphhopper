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

import com.graphhopper.util.DistanceCalc;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.PointList;
import com.graphhopper.util.shapes.CoordTrig;
import com.graphhopper.util.shapes.GHPoint;

/**
 * Result of LocationIndex lookup.
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
public class QueryResult
{
    private double queryDistance = Double.MAX_VALUE;
    private int wayIndex = -1;
    private int closestNode = -1;
    private EdgeIteratorState closestEdge;
    private final GHPoint queryPoint;
    private GHPoint snappedPoint;
    private Position snappedPosition;

    public static enum Position
    {
        EDGE, TOWER, PILLAR
    }

    public QueryResult( double queryLat, double queryLon )
    {
        queryPoint = new GHPoint(queryLat, queryLon);
    }

    public void setClosestNode( int node )
    {
        closestNode = node;
    }

    /**
     * @return the closest matching node. -1 if nothing found, this should be avoided via a call of
     * 'isValid'
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

    public void setWayIndex( int wayIndex )
    {
        this.wayIndex = wayIndex;
    }

    /**
     * References to a tower node or the index of wayGeometry of the closest edge. If wayGeometry
     * has lengh L then the wayIndex 0 refers to the *base* node, 1 to L (inclusive) refer to the
     * wayGeometry indices (minus one) and L+1 to the *adjacent* node. Currently only intialized if
     * returned from Location2NodesNtree.
     */
    public int getWayIndex()
    {
        return wayIndex;
    }

    public void setSnappedPosition( Position pos )
    {
        this.snappedPosition = pos;
    }

    /**
     * @return 0 if on edge. 1 if on pillar node and 2 if on tower node.
     */
    public Position getSnappedPosition()
    {
        return snappedPosition;
    }

    /**
     * @return true if a close node was found
     */
    public boolean isValid()
    {
        // Location2IDQuadtree does not support edges
        return closestNode >= 0;
    }

    public void setClosestEdge( EdgeIteratorState detach )
    {
        closestEdge = detach;
    }

    /**
     * @return the closest matching edge. Will be null if nothing found or call isValid before
     */
    public EdgeIteratorState getClosestEdge()
    {
        return closestEdge;
    }

    public CoordTrig getQueryPoint()
    {
        return queryPoint;
    }

    /**
     * Calculates the position of the query point 'snapped' to a close road segment or node. Call
     * calcSnappedPoint before, if not, an IllegalStateException is thrown.
     */
    public GHPoint getSnappedPoint()
    {
        if (snappedPoint == null)
            throw new IllegalStateException("Calculate snapped point before!");
        return snappedPoint;
    }

    /**
     * Calculates the closet point on the edge from the query point.
     */
    public void calcSnappedPoint( DistanceCalc distCalc )
    {
        if (closestEdge == null)
            throw new IllegalStateException("No closest edge?");
        if (snappedPoint != null)
            throw new IllegalStateException("Calculate snapped point only once");

        PointList fullPL = getClosestEdge().fetchWayGeometry(3);
        double tmpLat = fullPL.getLatitude(wayIndex);
        double tmpLon = fullPL.getLongitude(wayIndex);
        if (snappedPosition != Position.EDGE)
        {
            snappedPoint = new GHPoint(tmpLat, tmpLon);
            return;
        }

        double queryLat = getQueryPoint().lat, queryLon = getQueryPoint().lon;
        double adjLat = fullPL.getLatitude(wayIndex + 1), adjLon = fullPL.getLongitude(wayIndex + 1);
        if (distCalc.validEdgeDistance(queryLat, queryLon, tmpLat, tmpLon, adjLat, adjLon))
            snappedPoint = distCalc.calcCrossingPointToEdge(queryLat, queryLon, tmpLat, tmpLon, adjLat, adjLon);
        else
            // outside of edge boundaries
            snappedPoint = new GHPoint(tmpLat, tmpLon);
    }

    @Override
    public String toString()
    {
        if (closestEdge != null)
            return closestEdge.getBaseNode() + "-" + closestEdge.getAdjNode() + "  " + snappedPoint;
        return closestNode + ", " + queryPoint + ", " + wayIndex;
    }
}
