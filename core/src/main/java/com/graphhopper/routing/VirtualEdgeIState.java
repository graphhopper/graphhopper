/*
 * Copyright 2015 Peter Karich.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.graphhopper.routing;

import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.EdgeSkipIterState;
import com.graphhopper.util.PointList;

/**
 * Creates an edge state decoupled from a graph where nodes, pointList, etc are kept in memory.
 */
class VirtualEdgeIState implements EdgeIteratorState, EdgeSkipIterState {
    private final PointList pointList;
    private final int edgeId;
    private double distance;
    private long flags;
    private String name;
    private final int baseNode;
    private final int adjNode;

    public VirtualEdgeIState( int edgeId, int baseNode, int adjNode, double distance, long flags, String name, PointList pointList )
    {
        this.edgeId = edgeId;
        this.baseNode = baseNode;
        this.adjNode = adjNode;
        this.distance = distance;
        this.flags = flags;
        this.name = name;
        this.pointList = pointList;
    }

    @Override
    public int getEdge()
    {
        return edgeId;
    }

    @Override
    public int getBaseNode()
    {
        return baseNode;
    }

    @Override
    public int getAdjNode()
    {
        return adjNode;
    }

    @Override
    public PointList fetchWayGeometry( int mode )
    {
        if (pointList.getSize() == 0)
            return PointList.EMPTY;
        // due to API we need to create a new instance per call!
        if (mode == 3)
            return pointList.clone(false);
        else if (mode == 1)
            return pointList.copy(0, pointList.getSize() - 1);
        else if (mode == 2)
            return pointList.copy(1, pointList.getSize());
        else if (mode == 0)
        {
            if (pointList.getSize() == 1)
                return PointList.EMPTY;
            return pointList.copy(1, pointList.getSize() - 1);
        }
        throw new UnsupportedOperationException("Illegal mode:" + mode);
    }

    @Override
    public EdgeIteratorState setWayGeometry( PointList list )
    {
        throw new UnsupportedOperationException("Not supported for virtual edge. Set when creating it.");
    }

    @Override
    public double getDistance()
    {
        return distance;
    }

    @Override
    public EdgeIteratorState setDistance( double dist )
    {
        this.distance = dist;
        return this;
    }

    @Override
    public long getFlags()
    {
        return flags;
    }

    @Override
    public EdgeIteratorState setFlags( long flags )
    {
        this.flags = flags;
        return this;
    }

    @Override
    public String getName()
    {
        return name;
    }

    @Override
    public EdgeIteratorState setName( String name )
    {
        this.name = name;
        return this;
    }

    @Override
    public String toString()
    {
        return baseNode + "->" + adjNode;
    }

    @Override
    public boolean isShortcut()
    {
        return false;
    }

    @Override
    public int getAdditionalField()
    {
        throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    public int getSkippedEdge1()
    {
        throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    public int getSkippedEdge2()
    {
        throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    public void setSkippedEdges( int edge1, int edge2 )
    {
        throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    public EdgeIteratorState detach( boolean reverse )
    {
        throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    public EdgeIteratorState setAdditionalField( int value )
    {
        throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    public EdgeIteratorState copyPropertiesTo( EdgeIteratorState edge )
    {
        throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    public EdgeSkipIterState setWeight( double weight )
    {
        throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    public double getWeight()
    {
        throw new UnsupportedOperationException("Not supported.");
    }
    
}
