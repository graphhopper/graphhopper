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

import com.graphhopper.util.DistanceCalc;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.PointList;
import com.graphhopper.util.shapes.CoordTrig;
import com.graphhopper.util.shapes.GHPoint;

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
    private int wayIndex = -1;
    private int closestNode = -1;
    private EdgeIteratorState closestEdge;
    private EdgeIteratorState baseEdge;
    private EdgeIteratorState adjEdge;
    private final GHPoint queryPoint;
    private GHPoint snappedPoint;

    public LocationIDResult( double queryLat, double queryLon )
    {
        queryPoint = new GHPoint(queryLat, queryLon);
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

    public int getWayIndex()
    {
        return wayIndex;
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

    /**
     * @return the closest edge but the snapped point get the new adjacent node.
     */
    public EdgeIteratorState getBaseEdge()
    {
        checkSnappedPoint();
        return baseEdge;
    }

    /**
     * @return the closest edge but the snapped point gets the new base node.
     */
    public EdgeIteratorState getAdjEdge()
    {
        checkSnappedPoint();
        return adjEdge;
    }

    public CoordTrig getQueryPoint()
    {
        return queryPoint;
    }

    /**
     * Calculates the position of the query point 'snapped' to a close road segment or node. Can be
     * null if no result found. Call calcSnappedPoint before.
     */
    public GHPoint getSnappedPoint()
    {
        checkSnappedPoint();
        return snappedPoint;
    }

    private void checkSnappedPoint()
    {
        if (snappedPoint == null)
            throw new IllegalStateException("Call calcSnappedPoint before");
    }

    public void calcSnappedPoint( DistanceCalc distCalc )
    {
        if (closestEdge == null || wayIndex < 0)
            throw new IllegalStateException("State is invalid. Set closestEdge AND wayIndex!");

        PointList pl = getClosestEdge().getWayGeometry(3);
        int size = pl.getSize();
        int index = getWayIndex();
        double tmpLat = pl.getLatitude(index);
        double tmpLon = pl.getLongitude(index);
        boolean newPoint = false;
        if (index + 1 < size)
        {
            double queryLat = getQueryPoint().lat, queryLon = getQueryPoint().lon;
            double adjLat = pl.getLatitude(index + 1), adjLon = pl.getLongitude(index + 1);
            if (distCalc.validEdgeDistance(queryLat, queryLon, tmpLat, tmpLon, adjLat, adjLon))
            {
                snappedPoint = distCalc.calcCrossingPointToEdge(queryLat, queryLon, tmpLat, tmpLon, adjLat, adjLon);
                newPoint = true;
            } else
                // outside of edge boundaries
                snappedPoint = new GHPoint(tmpLat, tmpLon);
        } else
            // snapped point is on adjacent node
            snappedPoint = new GHPoint(tmpLat, tmpLon);

        // build the two parts of the closest edge
        PointList basePoints = new PointList(index);
        PointList adjPoints = new PointList(index);

        adjPoints.add(snappedPoint.lat, snappedPoint.lon);
        for (int i = 0; i < pl.getSize(); i++)
        {
            if (i < wayIndex || newPoint && i == wayIndex)
                basePoints.add(pl.getLatitude(i), pl.getLongitude(i));

            if (i > wayIndex)
                adjPoints.add(pl.getLatitude(i), pl.getLongitude(i));
        }
        basePoints.add(snappedPoint.lat, snappedPoint.lon);

        double baseDistance = basePoints.calcDistance(distCalc);
        double adjDistance = adjPoints.calcDistance(distCalc);
        baseEdge = new InMemEdgeIState(baseDistance, closestEdge.getFlags(), closestEdge.getName(), basePoints);
        adjEdge = new InMemEdgeIState(adjDistance, closestEdge.getFlags(), closestEdge.getName(), adjPoints);
    }

    /**
     * Create edge decoupled from graph where distance and nodes are kept in memory.
     */
    private static class InMemEdgeIState implements EdgeIteratorState
    {
        private final PointList pointList;
        private double distance;
        private int flags;
        private String name;

        /**
         * @param distance
         * @param flags
         * @param name
         * @param pointList all points including the base and adjacent to avoid back reference to
         * graph but also to avoid special handling for snapped point which does not exist in the
         * graph.
         */
        public InMemEdgeIState( double distance, int flags, String name, PointList pointList )
        {
            this.distance = distance;
            this.flags = flags;
            this.name = name;
            this.pointList = pointList;
        }

        @Override
        public int getEdge()
        {
            throw new UnsupportedOperationException("Not supported for in-memory edge.");
        }

        @Override
        public int getBaseNode()
        {
            throw new UnsupportedOperationException("Not supported for in-memory edge.");
        }

        @Override
        public int getAdjNode()
        {
            throw new UnsupportedOperationException("Not supported for in-memory edge.");
        }

        @Override
        public PointList getWayGeometry( int mode )
        {
            if (mode != 3)
                throw new UnsupportedOperationException("Not yet implemented.");
            return pointList;
        }

        @Override
        public void setWayGeometry( PointList list )
        {
            throw new UnsupportedOperationException("Not supported for in-memory edge. Set when creating it.");
        }

        @Override
        public double getDistance()
        {
            return distance;
        }

        @Override
        public void setDistance( double dist )
        {
            this.distance = dist;
        }

        @Override
        public int getFlags()
        {
            return flags;
        }

        @Override
        public void setFlags( int flags )
        {
            this.flags = flags;
        }

        @Override
        public String getName()
        {
            return name;
        }

        @Override
        public void setName( String name )
        {
            this.name = name;
        }
    }

    @Override
    public String toString()
    {
        return queryPoint + ", " + closestNode + ", " + snappedPoint + ", " + wayIndex;
    }
}
