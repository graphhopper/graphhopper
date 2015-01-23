/*
 *  Licensed to Peter Karich under one or more contributor license
 *  agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  Peter Karich licenses this file to you under the Apache License,
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
package com.graphhopper.storage;

import com.graphhopper.routing.util.AllEdgesIterator;
import com.graphhopper.routing.util.AllEdgesSkipIterator;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.EdgeSkipIterator;
import com.graphhopper.util.PointList;
import com.graphhopper.util.shapes.BBox;

/**
 * @author Peter Karich
 */
class BaseGraph implements Graph
{
    private final LevelGraph lg;

    BaseGraph( LevelGraph lg )
    {
        this.lg = lg;
    }

    @Override
    public Graph getBaseGraph()
    {
        return this;
    }

    @Override
    public int getNodes()
    {
        return lg.getNodes();
    }

    @Override
    public NodeAccess getNodeAccess()
    {
        return lg.getNodeAccess();
    }

    @Override
    public BBox getBounds()
    {
        return lg.getBounds();
    }

    @Override
    public EdgeIteratorState edge( int a, int b )
    {
        return lg.edge(a, b);
    }

    @Override
    public EdgeIteratorState edge( int a, int b, double distance, boolean bothDirections )
    {
        return lg.edge(a, b, distance, bothDirections);
    }

    @Override
    public EdgeIteratorState getEdgeProps( int edgeId, int adjNode )
    {
        if (lg.isShortcut(edgeId))
            throw new IllegalStateException("Do not fetch shortcuts from BaseGraph use the LevelGraph instead");

        return lg.getEdgeProps(edgeId, adjNode);
    }

    @Override
    public AllEdgesIterator getAllEdges()
    {
        final AllEdgesSkipIterator tmpIter = lg.getAllEdges();
        return new AllEdgesIterator()
        {
            @Override
            public int getCount()
            {
                return tmpIter.getCount();
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
            public EdgeIteratorState copyPropertiesTo( EdgeIteratorState edge )
            {
                return tmpIter.copyPropertiesTo(edge);
            }

            @Override
            public EdgeIteratorState detach( boolean reverse )
            {
                return tmpIter.detach(reverse);
            }
        };
    }

    @Override
    public EdgeExplorer createEdgeExplorer( final EdgeFilter filter )
    {
        if (filter == EdgeFilter.ALL_EDGES)
            return createEdgeExplorer();

        return lg.createEdgeExplorer(new EdgeFilter()
        {
            @Override
            public boolean accept( EdgeIteratorState edgeIterState )
            {
                if (((EdgeSkipIterator) edgeIterState).isShortcut())
                    return false;

                return filter.accept(edgeIterState);
            }
        });
    }

    private final static EdgeFilter NO_SHORTCUTS = new EdgeFilter()
    {
        @Override
        public boolean accept( EdgeIteratorState edgeIterState )
        {
            return !((EdgeSkipIterator) edgeIterState).isShortcut();
        }
    };

    @Override
    public EdgeExplorer createEdgeExplorer()
    {
        return lg.createEdgeExplorer(NO_SHORTCUTS);
    }

    @Override
    public Graph copyTo( Graph g )
    {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public GraphExtension getExtension()
    {
        return lg.getExtension();
    }
}
