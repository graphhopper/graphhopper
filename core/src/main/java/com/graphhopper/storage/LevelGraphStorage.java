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
package com.graphhopper.storage;

import com.graphhopper.routing.ch.PrepareEncoder;
import com.graphhopper.routing.util.AllEdgesSkipIterator;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.util.*;

/**
 * A Graph necessary for shortcut algorithms like Contraction Hierarchies. This class enables the
 * storage to hold the level of a node and a shortcut edge per edge.
 * <p/>
 * @author Peter Karich
 * @see GraphBuilder
 */
public class LevelGraphStorage extends GraphHopperStorage implements LevelGraph
{
    private static final double WEIGHT_FACTOR = 1000f;
    // 2 bits for access, for now only 32bit => not Long.MAX
    private static final long MAX_WEIGHT_LONG = (Integer.MAX_VALUE >> 2) << 2;
    private static final double MAX_WEIGHT = (Integer.MAX_VALUE >> 2) / WEIGHT_FACTOR;
    private int I_SKIP_EDGE1;
    private int I_SKIP_EDGE2;
    private int I_LEVEL;
    // after the last edge only shortcuts are stored
    private int lastEdgeIndex = -1;
    private final long scDirMask = PrepareEncoder.getScDirMask();
    private final Graph baseGraph;

    public LevelGraphStorage( Directory dir, EncodingManager encodingManager, boolean enabled3D )
    {
        super(dir, encodingManager, enabled3D);
        baseGraph = new BaseGraph(this);
    }

    @Override
    public boolean isShortcut( int edgeId )
    {
        return edgeId > lastEdgeIndex;
    }

    @Override
    protected void initStorage()
    {
        super.initStorage();
        I_SKIP_EDGE1 = nextEdgeEntryIndex(4);
        I_SKIP_EDGE2 = nextEdgeEntryIndex(4);
        I_LEVEL = nextNodeEntryIndex(4);
        initNodeAndEdgeEntrySize();
    }

    @Override
    public final void setLevel( int nodeIndex, int level )
    {
        if (nodeIndex >= getNodes())
            return;

        nodes.setInt((long) nodeIndex * nodeEntryBytes + I_LEVEL, level);
    }

    @Override
    public final int getLevel( int nodeIndex )
    {
        // automatically allocate new nodes only via creating edges or setting node properties
        if (nodeIndex >= getNodes())
            throw new IllegalStateException("node " + nodeIndex + " is invalid. Not in [0," + getNodes() + ")");

        return nodes.getInt((long) nodeIndex * nodeEntryBytes + I_LEVEL);
    }

    @Override
    public EdgeSkipIterState shortcut( int a, int b )
    {
        return createEdge(a, b);
    }

    @Override
    public EdgeSkipIterState edge( int a, int b )
    {
        if (lastEdgeIndex + 1 < edgeCount)
            throw new IllegalStateException("Cannot create edge after first shortcut was created");

        lastEdgeIndex = edgeCount;
        return createEdge(a, b);
    }

    private EdgeSkipIterState createEdge( int a, int b )
    {
        ensureNodeIndex(Math.max(a, b));
        int edgeId = internalEdgeAdd(a, b);
        EdgeSkipIteratorImpl iter = new EdgeSkipIteratorImpl(EdgeFilter.ALL_EDGES);
        iter.setBaseNode(a);
        iter.setEdgeId(edgeId);
        iter.next();
        iter.setSkippedEdges(EdgeIterator.NO_EDGE, EdgeIterator.NO_EDGE);
        return iter;
    }

    @Override
    public EdgeSkipExplorer createEdgeExplorer()
    {
        return createEdgeExplorer(EdgeFilter.ALL_EDGES);
    }

    @Override
    public EdgeSkipExplorer createEdgeExplorer( EdgeFilter filter )
    {
        return new EdgeSkipIteratorImpl(filter);
    }

    @Override
    public LevelGraphStorage create( long nodeCount )
    {
        super.create(nodeCount);
        return this;
    }

    @Override
    public final EdgeSkipIterState getEdgeProps( int edgeId, int endNode )
    {
        return (EdgeSkipIterState) super.getEdgeProps(edgeId, endNode);
    }

    class EdgeSkipIteratorImpl extends EdgeIterable implements EdgeSkipExplorer, EdgeSkipIterator
    {
        public EdgeSkipIteratorImpl( EdgeFilter filter )
        {
            super(filter);
        }

        @Override
        public final EdgeSkipIterator setBaseNode( int baseNode )
        {
            super.setBaseNode(baseNode);
            return this;
        }

        @Override
        public final void setSkippedEdges( int edge1, int edge2 )
        {
            if (EdgeIterator.Edge.isValid(edge1) != EdgeIterator.Edge.isValid(edge2))
            {
                throw new IllegalStateException("Skipped edges of a shortcut needs "
                        + "to be both valid or invalid but they were not " + edge1 + ", " + edge2);
            }
            edges.setInt(edgePointer + I_SKIP_EDGE1, edge1);
            edges.setInt(edgePointer + I_SKIP_EDGE2, edge2);
        }

        @Override
        public final int getSkippedEdge1()
        {
            return edges.getInt(edgePointer + I_SKIP_EDGE1);
        }

        @Override
        public final int getSkippedEdge2()
        {
            return edges.getInt(edgePointer + I_SKIP_EDGE2);
        }

        @Override
        public final boolean isShortcut()
        {
            return edgeId > lastEdgeIndex;
        }

        @Override
        public final EdgeSkipIterState setWeight( double weight )
        {
            LevelGraphStorage.this.setWeight(this, weight);
            return this;
        }

        @Override
        public final double getWeight()
        {
            return LevelGraphStorage.this.getWeight(this);
        }

        @Override
        public final EdgeIteratorState detach( boolean reverseArg )
        {
            if (edgeId == nextEdge)
                throw new IllegalStateException("call next before detaching");
            EdgeSkipIteratorImpl iter = new EdgeSkipIteratorImpl(filter);
            iter.setBaseNode(baseNode);
            iter.setEdgeId(edgeId);
            iter.next();
            if (reverseArg)
            {
                iter.reverse = !this.reverse;
                iter.adjNode = baseNode;
                iter.baseNode = adjNode;
            }
            return iter;
        }

        @Override
        public final EdgeIteratorState copyPropertiesTo( EdgeIteratorState edge )
        {
            super.copyPropertiesTo(edge);

//            EdgeSkipIterator eSkip = (EdgeSkipIterator) edge;
//            setSkippedEdges(eSkip.getSkippedEdge1(), eSkip.getSkippedEdge2());
            return edge;
        }

        @Override
        public String getName()
        {
            if (isShortcut())
                throw new IllegalStateException("Cannot call getName on shortcut " + getEdge());
            return super.getName();
        }

        @Override
        public EdgeIteratorState setName( String name )
        {
            if (isShortcut())
                throw new IllegalStateException("Cannot call setName on shortcut " + getEdge());
            return super.setName(name);
        }

        @Override
        public PointList fetchWayGeometry( int mode )
        {
            if (isShortcut())
                throw new IllegalStateException("Cannot call fetchWayGeometry on shortcut " + getEdge());
            return super.fetchWayGeometry(mode);
        }

        @Override
        public EdgeIteratorState setWayGeometry( PointList list )
        {
            if (isShortcut())
                throw new IllegalStateException("Cannot call setWayGeometry on shortcut " + getEdge());
            return super.setWayGeometry(list);
        }
    }

    @Override
    long reverseFlags( long edgePointer, long flags )
    {
        boolean isShortcut = edgePointer > (long) lastEdgeIndex * edgeEntryBytes;
        if (!isShortcut)
            return super.reverseFlags(edgePointer, flags);

        // we need a special swapping for level graph if it is a shortcut as we only store the weight and access flags then
        long dir = flags & scDirMask;
        if (dir == scDirMask || dir == 0)
            return flags;

        // swap the last bits with this mask
        return flags ^ scDirMask;
    }

    /**
     * Disconnects the edges (higher->lower node) via the specified edgeState pointing from lower to
     * higher node.
     * <p/>
     * @param edgeState the edge from lower to higher
     */
    public void disconnect( EdgeSkipExplorer explorer, EdgeIteratorState edgeState )
    {
        // search edge with opposite direction but we need to know the previousEdge for the internalEdgeDisconnect so we cannot simply do:
        // EdgeIteratorState tmpIter = getEdgeProps(iter.getEdge(), iter.getBaseNode());
        EdgeSkipIterator tmpIter = explorer.setBaseNode(edgeState.getAdjNode());
        int tmpPrevEdge = EdgeIterator.NO_EDGE;
        while (tmpIter.next())
        {
            if (tmpIter.isShortcut() && tmpIter.getEdge() == edgeState.getEdge())
            {
                internalEdgeDisconnect(edgeState.getEdge(), (long) tmpPrevEdge * edgeEntryBytes, edgeState.getAdjNode(), edgeState.getBaseNode());
                break;
            }

            tmpPrevEdge = tmpIter.getEdge();
        }
    }

    @Override
    public AllEdgesSkipIterator getAllEdges()
    {
        return new AllEdgeSkipIterator();
    }

    class AllEdgeSkipIterator extends AllEdgeIterator implements AllEdgesSkipIterator
    {
        @Override
        public final void setSkippedEdges( int edge1, int edge2 )
        {
            edges.setInt(edgePointer + I_SKIP_EDGE1, edge1);
            edges.setInt(edgePointer + I_SKIP_EDGE2, edge2);
        }

        @Override
        public final int getSkippedEdge1()
        {
            return edges.getInt(edgePointer + I_SKIP_EDGE1);
        }

        @Override
        public final int getSkippedEdge2()
        {
            return edges.getInt(edgePointer + I_SKIP_EDGE2);
        }

        @Override
        public final boolean isShortcut()
        {
            return edgePointer / edgeEntryBytes > lastEdgeIndex;
        }

        @Override
        public final EdgeSkipIterState setWeight( double weight )
        {
            LevelGraphStorage.this.setWeight(this, weight);
            return this;
        }

        @Override
        public final double getWeight()
        {
            return LevelGraphStorage.this.getWeight(this);
        }
    }

    @Override
    protected SingleEdge createSingleEdge( int edge, int nodeId )
    {
        return new SingleLevelEdge(edge, nodeId);
    }

    class SingleLevelEdge extends SingleEdge implements EdgeSkipIterState
    {
        public SingleLevelEdge( int edge, int nodeId )
        {
            super(edge, nodeId);
        }

        @Override
        public final void setSkippedEdges( int edge1, int edge2 )
        {
            edges.setInt(edgePointer + I_SKIP_EDGE1, edge1);
            edges.setInt(edgePointer + I_SKIP_EDGE2, edge2);
        }

        @Override
        public final int getSkippedEdge1()
        {
            return edges.getInt(edgePointer + I_SKIP_EDGE1);
        }

        @Override
        public final int getSkippedEdge2()
        {
            return edges.getInt(edgePointer + I_SKIP_EDGE2);
        }

        @Override
        public final boolean isShortcut()
        {
            return edgeId > lastEdgeIndex;
        }

        @Override
        public final EdgeSkipIterState setWeight( double weight )
        {
            LevelGraphStorage.this.setWeight(this, weight);
            return this;
        }

        @Override
        public final double getWeight()
        {
            return LevelGraphStorage.this.getWeight(this);
        }
    }

    final void setWeight( EdgeSkipIterState edge, double weight )
    {
        if (!edge.isShortcut())
            throw new IllegalStateException("setWeight is only available for shortcuts");
        if (weight < 0)
            throw new IllegalArgumentException("weight cannot be negative! but was " + weight);

        long weightLong;
        if (weight > MAX_WEIGHT)
            weightLong = MAX_WEIGHT_LONG;
        else
            weightLong = ((long) (weight * WEIGHT_FACTOR)) << 2;

        long accessFlags = edge.getFlags() & PrepareEncoder.getScDirMask();
        edge.setFlags(weightLong | accessFlags);
    }

    final double getWeight( EdgeSkipIterState edge )
    {
        if (!edge.isShortcut())
            throw new IllegalStateException("getWeight is only available for shortcuts");

        double weight = (edge.getFlags() >>> 2) / WEIGHT_FACTOR;
        if (weight >= MAX_WEIGHT)
            return Double.POSITIVE_INFINITY;

        return weight;
    }

    @Override
    protected int loadEdgesHeader()
    {
        int next = super.loadEdgesHeader();
        lastEdgeIndex = edges.getHeader(next * 4);
        return next + 1;
    }

    @Override
    protected int setEdgesHeader()
    {
        int next = super.setEdgesHeader();
        edges.setHeader(next * 4, lastEdgeIndex);
        return next + 1;
    }

    @Override
    public Graph getBaseGraph()
    {
        return baseGraph;
    }
}
