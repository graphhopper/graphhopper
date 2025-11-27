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
import com.graphhopper.storage.GraphHopperStorage.SingleEdge;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.EdgeSkipIterator;
import com.graphhopper.util.Helper;
import com.graphhopper.util.PointList;
import com.graphhopper.util.shapes.BBox;

///////
import com.graphhopper.coll.GHBitSet;
import com.graphhopper.coll.GHBitSetImpl;
import com.graphhopper.coll.SparseIntIntArray;

import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.search.NameIndex;
import com.graphhopper.util.*;
import static com.graphhopper.util.Helper.nf;

import java.io.UnsupportedEncodingException;

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

    protected final int nextEdgeEntryIndex( int sizeInBytes )
    {
        int tmp = edgeEntryIndex;
        edgeEntryIndex += sizeInBytes;
        return tmp;
    }

    protected final int nextNodeEntryIndex( int sizeInBytes )
    {
        int tmp = nodeEntryIndex;
        nodeEntryIndex += sizeInBytes;
        return tmp;
    }

    protected final void initNodeAndEdgeEntrySize()
    {
        nodeEntryBytes = nodeEntryIndex;
        edgeEntryBytes = edgeEntryIndex;
    }

    protected final long getLinkPosInEdgeArea( int nodeThis, int nodeOther, long edgePointer )
    {
        return nodeThis <= nodeOther ? edgePointer + E_LINKA : edgePointer + E_LINKB;
    }

    public String getDebugInfo( int node, int area )
    {
        String str = "--- node " + node + " ---";
        int min = Math.max(0, node - area / 2);
        int max = Math.min(nodeCount, node + area / 2);
        long nodePointer = (long) node * nodeEntryBytes;
        for (int i = min; i < max; i++)
        {
            str += "\n" + i + ": ";
            for (int j = 0; j < nodeEntryBytes; j += 4)
            {
                if (j > 0)
                {
                    str += ",\t";
                }
                str += nodes.getInt(nodePointer + j);
            }
        }
        int edge = nodes.getInt(nodePointer);
        str += "\n--- edges " + edge + " ---";
        int otherNode;
        for (int i = 0; i < 1000; i++)
        {
            str += "\n";
            if (edge == EdgeIterator.NO_EDGE)
                break;

            str += edge + ": ";
            long edgePointer = (long) edge * edgeEntryBytes;
            for (int j = 0; j < edgeEntryBytes; j += 4)
            {
                if (j > 0)
                {
                    str += ",\t";
                }
                str += edges.getInt(edgePointer + j);
            }

            otherNode = getOtherNode(node, edgePointer);
            long lastLink = getLinkPosInEdgeArea(node, otherNode, edgePointer);
            edge = edges.getInt(lastLink);
        }
        return str;
    }

    protected SingleEdge createSingleEdge( int edgeId, int nodeId )
    {
        return new SingleEdge(edgeId, nodeId);
    }

    protected void initStorage()
    {
        edgeEntryIndex = 0;
        nodeEntryIndex = 0;
        E_NODEA = nextEdgeEntryIndex(4);
        E_NODEB = nextEdgeEntryIndex(4);
        E_LINKA = nextEdgeEntryIndex(4);
        E_LINKB = nextEdgeEntryIndex(4);
        E_DIST = nextEdgeEntryIndex(4);
        this.flagsSizeIsLong = encodingManager.getBytesForFlags() == 8;
        E_FLAGS = nextEdgeEntryIndex(encodingManager.getBytesForFlags());
        E_GEO = nextEdgeEntryIndex(4);
        E_NAME = nextEdgeEntryIndex(4);
        if (extStorage.isRequireEdgeField())
            E_ADDITIONAL = nextEdgeEntryIndex(4);
        else
            E_ADDITIONAL = -1;

        N_EDGE_REF = nextNodeEntryIndex(4);
        N_LAT = nextNodeEntryIndex(4);
        N_LON = nextNodeEntryIndex(4);
        if (nodeAccess.is3D())
            N_ELE = nextNodeEntryIndex(4);
        else
            N_ELE = -1;

        if (extStorage.isRequireNodeField())
            N_ADDITIONAL = nextNodeEntryIndex(4);
        else
            N_ADDITIONAL = -1;

        initNodeAndEdgeEntrySize();
        initialized = true;
    }

    protected int loadNodesHeader()
    {
        int hash = nodes.getHeader(0);
        if (hash != stringHashCode(getClass().getName()))
            throw new IllegalStateException("Cannot load the graph when using instance of "
                    + getClass().getName() + " and location: " + dir);

        nodeEntryBytes = nodes.getHeader(1 * 4);
        nodeCount = nodes.getHeader(2 * 4);
        bounds.minLon = Helper.intToDegree(nodes.getHeader(3 * 4));
        bounds.maxLon = Helper.intToDegree(nodes.getHeader(4 * 4));
        bounds.minLat = Helper.intToDegree(nodes.getHeader(5 * 4));
        bounds.maxLat = Helper.intToDegree(nodes.getHeader(6 * 4));

        if (bounds.hasElevation())
        {
            bounds.minEle = Helper.intToEle(nodes.getHeader(7 * 4));
            bounds.maxEle = Helper.intToEle(nodes.getHeader(8 * 4));
        }

        return 7;
    }

    protected int setNodesHeader()
    {
        nodes.setHeader(0, stringHashCode(getClass().getName()));
        nodes.setHeader(1 * 4, nodeEntryBytes);
        nodes.setHeader(2 * 4, nodeCount);
        nodes.setHeader(3 * 4, Helper.degreeToInt(bounds.minLon));
        nodes.setHeader(4 * 4, Helper.degreeToInt(bounds.maxLon));
        nodes.setHeader(5 * 4, Helper.degreeToInt(bounds.minLat));
        nodes.setHeader(6 * 4, Helper.degreeToInt(bounds.maxLat));
        if (bounds.hasElevation())
        {
            nodes.setHeader(7 * 4, Helper.eleToInt(bounds.minEle));
            nodes.setHeader(8 * 4, Helper.eleToInt(bounds.maxEle));
        }

        return 7;
    }

    protected int loadEdgesHeader()
    {
        edgeEntryBytes = edges.getHeader(0 * 4);
        edgeCount = edges.getHeader(1 * 4);
        return 4;
    }

    protected int setEdgesHeader()
    {
        edges.setHeader(0, edgeEntryBytes);
        edges.setHeader(1 * 4, edgeCount);
        edges.setHeader(2 * 4, encodingManager.hashCode());
        edges.setHeader(3 * 4, extStorage.hashCode());
        return 4;
    }

    protected int loadWayGeometryHeader()
    {
        maxGeoRef = wayGeometry.getHeader(0);
        return 1;
    }

    protected int setWayGeometryHeader()
    {
        wayGeometry.setHeader(0, maxGeoRef);
        return 1;
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
