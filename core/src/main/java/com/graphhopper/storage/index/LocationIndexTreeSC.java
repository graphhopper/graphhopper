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
import com.graphhopper.routing.util.AllEdgesSkipIterator;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.storage.Directory;
import com.graphhopper.storage.LevelGraph;
import com.graphhopper.util.*;

/**
 * The LevelGraph has some edges disconnected (to be more efficient), but this happens before the
 * index is created! So we need to take care of this and also ignore the introduced shortcuts e.g.
 * for calculating closest edges.
 * <p/>
 * TODO avoid some of the tricks if we move a disconnected edge to the end of the edge-list (instead
 * of just disconnecting them). And then while accessing them break iteration if we encounter the
 * first of those disconnected edges (this should have the same speed). Therefor we also need to
 * change the EdgeFilter interface and add a stop(EdgeIterator) method or similar.
 * <p/>
 * @author Peter Karich
 */
public class LocationIndexTreeSC extends LocationIndexTree
{
    private final static EdgeFilter NO_SHORTCUT = new EdgeFilter()
    {
        @Override
        public boolean accept( EdgeIteratorState edgeIterState )
        {
            return !((EdgeSkipIterator) edgeIterState).isShortcut();
        }
    };
    private final LevelGraph lg;

    public LocationIndexTreeSC( LevelGraph g, Directory dir )
    {
        super(g, dir);
        lg = g;
    }

    @Override
    protected int pickBestNode( int nodeA, int nodeB )
    {
        // return lower level nodes as those nodes are always connected to higher ones
        // (high level nodes are potentially disconnected from lower ones in order to improve performance on Android)
        if (lg.getLevel(nodeA) < lg.getLevel(nodeB))
            return nodeA;
        return nodeB;
    }

    @Override
    protected AllEdgesIterator getAllEdges()
    {
        final AllEdgesSkipIterator tmpIter = lg.getAllEdges();
        return new AllEdgesIterator()
        {
            @Override
            public int getMaxId()
            {
                return tmpIter.getMaxId();
            }

            @Override
            public boolean next()
            {
                while (tmpIter.next())
                {
                    if (!tmpIter.isShortcut())
                    {
                        return true;
                    }
                }
                return false;
            }

            @Override
            public int getEdge()
            {
                return tmpIter.getEdge();
            }

            @Override
            public int getBaseNode()
            {
                return tmpIter.getBaseNode();
            }

            @Override
            public int getAdjNode()
            {
                return tmpIter.getAdjNode();
            }

            @Override
            public PointList fetchWayGeometry( int type )
            {
                return tmpIter.fetchWayGeometry(type);
            }

            @Override
            public EdgeIteratorState setWayGeometry( PointList list )
            {
                return tmpIter.setWayGeometry(list);
            }

            @Override
            public double getDistance()
            {
                return tmpIter.getDistance();
            }

            @Override
            public EdgeIteratorState setDistance( double dist )
            {
                return tmpIter.setDistance(dist);
            }

            @Override
            public long getFlags()
            {
                return tmpIter.getFlags();
            }

            @Override
            public EdgeIteratorState setFlags( long flags )
            {
                return tmpIter.setFlags(flags);
            }

            @Override
            public String getName()
            {
                return tmpIter.getName();
            }

            @Override
            public EdgeIteratorState setName( String name )
            {
                return tmpIter.setName(name);
            }

            @Override
            public int getAdditionalField()
            {
                return tmpIter.getAdditionalField();
            }

            @Override
            public EdgeIteratorState setAdditionalField( int value )
            {
                return tmpIter.setAdditionalField(value);
            }

            @Override
            public void copyProperties( EdgeIteratorState edge )
            {
                tmpIter.copyProperties(edge);
            }

            @Override
            public EdgeIteratorState detach(boolean reverse)
            {
                return tmpIter.detach(reverse);
            }
        };
    }

    @Override
    protected EdgeFilter getEdgeFilter()
    {
        return NO_SHORTCUT;
    }
}
