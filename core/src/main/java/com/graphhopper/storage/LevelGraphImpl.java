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
import com.graphhopper.storage.BaseGraph.AllEdgeIterator;
import com.graphhopper.storage.BaseGraph.EdgeIterable;
import com.graphhopper.storage.BaseGraph.SingleEdge;
import com.graphhopper.util.*;
import com.graphhopper.util.shapes.BBox;

/**
 * A Graph necessary for Contraction Hierarchies. This class enables the storage to hold the level
 * of a node and shortcut edges per edge.
 * <p/>
 * @author Peter Karich
 */
public class LevelGraphImpl implements LevelGraph, Storable<LevelGraph>
{
    private static final double WEIGHT_FACTOR = 1000f;
    // 2 bits for access, for now only 32bit => not Long.MAX
    private static final long MAX_WEIGHT_LONG = (Integer.MAX_VALUE >> 2) << 2;
    private static final double MAX_WEIGHT = (Integer.MAX_VALUE >> 2) / WEIGHT_FACTOR;
    private int I_SKIP_EDGE1;
    private int I_SKIP_EDGE2;
    private int I_LEVEL;
    // after the last edge only shortcuts are stored
    int lastEdgeIndex = -1;
    final long scDirMask = PrepareEncoder.getScDirMask();
    private final BaseGraph baseGraph;
    private final InternalGraphPropertyAccess propAccess;
//    private final DataAccess nodesCH;
//    private final int nodeCHEntryBytes;

    LevelGraphImpl( String name, Directory dir, BaseGraph baseGraph, InternalGraphPropertyAccess propAccess )
    {
        this.baseGraph = baseGraph;
        this.propAccess = propAccess;
    }

    @Override
    public boolean isShortcut( int edgeId )
    {
        assert lastEdgeIndex >= 0 : "level graph not yet freezed";
        return edgeId > lastEdgeIndex;
    }

    @Override
    public final void setLevel( int nodeIndex, int level )
    {
        if (nodeIndex >= baseGraph.getNodes())
            return;

        baseGraph.nodes.setInt((long) nodeIndex * baseGraph.nodeEntryBytes + I_LEVEL, level);
    }

    @Override
    public final int getLevel( int nodeIndex )
    {
        // automatically allocate new nodes only via creating edges or setting node properties
        if (nodeIndex >= baseGraph.getNodes())
            throw new IllegalStateException("node " + nodeIndex + " is invalid. Not in [0," + baseGraph.getNodes() + ")");

        return baseGraph.nodes.getInt((long) nodeIndex * baseGraph.nodeEntryBytes + I_LEVEL);
    }

    @Override
    public EdgeSkipIterState shortcut( int a, int b )
    {
        if (!baseGraph.isFreezed())
            baseGraph.freeze();

        return createEdge(a, b);
    }

    @Override
    public EdgeIteratorState edge( int a, int b, double distance, boolean bothDirections )
    {
        return createEdge(a, b).setDistance(distance).setFlags(baseGraph.encodingManager.flagsDefault(true, bothDirections));
    }

    @Override
    public EdgeSkipIterState edge( int a, int b )
    {
        return createEdge(a, b);
    }

    private EdgeSkipIterState createEdge( int a, int b )
    {
        baseGraph.ensureNodeIndex(Math.max(a, b));
        int edgeId = baseGraph.internalEdgeAdd(a, b);
        EdgeSkipIteratorImpl iter = new EdgeSkipIteratorImpl(baseGraph, propAccess, EdgeFilter.ALL_EDGES);
        iter.setBaseNode(a);
        iter.setEdgeId(edgeId);
        iter.next();
        iter.setSkippedEdges(EdgeIterator.NO_EDGE, EdgeIterator.NO_EDGE);
        return iter;
    }

    void ensureNodeIndex( int nodeIndex )
    {
        // nodesCH.ensureCapacity(((long) nodeIndex + 1) * nodeCHEntryBytes);
    }

    void ensureEdgeIndex( int edgeIndex )
    {
    }

    @Override
    public EdgeSkipExplorer createEdgeExplorer()
    {
        return createEdgeExplorer(EdgeFilter.ALL_EDGES);
    }

    @Override
    public EdgeSkipExplorer createEdgeExplorer( EdgeFilter filter )
    {
        return new EdgeSkipIteratorImpl(baseGraph, propAccess, filter);
    }

    @Override
    public final EdgeSkipIterState getEdgeProps( int edgeId, int endNode )
    {
        return (EdgeSkipIterState) baseGraph.getEdgeProps(propAccess, edgeId, endNode);
    }

    @Override
    public int getNodes()
    {
        return baseGraph.getNodes();
    }

    @Override
    public NodeAccess getNodeAccess()
    {
        return baseGraph.getNodeAccess();
    }

    @Override
    public BBox getBounds()
    {
        return baseGraph.getBounds();
    }

    public void freeze()
    {
        if (baseGraph.edgeCount == 0)
            throw new IllegalStateException("No edges added and graph should be freezed!?");

        lastEdgeIndex = baseGraph.edgeCount - 1;
    }

    String toDetailsString()
    {
        return "scIndex:" + lastEdgeIndex;
    }

    class EdgeSkipIteratorImpl extends EdgeIterable implements EdgeSkipExplorer, EdgeSkipIterator
    {

        public EdgeSkipIteratorImpl( BaseGraph baseGraph, InternalGraphPropertyAccess propAccess, EdgeFilter filter )
        {
            super(baseGraph, propAccess, filter);
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
            baseGraph.edges.setInt(edgePointer + I_SKIP_EDGE1, edge1);
            baseGraph.edges.setInt(edgePointer + I_SKIP_EDGE2, edge2);
        }

        @Override
        public final int getSkippedEdge1()
        {
            return baseGraph.edges.getInt(edgePointer + I_SKIP_EDGE1);
        }

        @Override
        public final int getSkippedEdge2()
        {
            return baseGraph.edges.getInt(edgePointer + I_SKIP_EDGE2);
        }

        @Override
        public final boolean isShortcut()
        {
            // TODO include later
            // assert lastEdgeIndex >= 0 : "level graph not yet freezed";
            if (lastEdgeIndex < 0)
                return false;

            return edgeId > lastEdgeIndex;
        }

        @Override
        public final EdgeSkipIterState setWeight( double weight )
        {
            LevelGraphImpl.this.setWeight(this, weight);
            return this;
        }

        @Override
        public final double getWeight()
        {
            return LevelGraphImpl.this.getWeight(this);
        }

        @Override
        public final EdgeIteratorState detach( boolean reverseArg )
        {
            if (edgeId == nextEdge)
                throw new IllegalStateException("call next before detaching");
            EdgeSkipIteratorImpl iter = new EdgeSkipIteratorImpl(baseGraph, propAccess, filter);
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
                baseGraph.internalEdgeDisconnect(edgeState.getEdge(), (long) tmpPrevEdge * baseGraph.edgeEntryBytes, edgeState.getAdjNode(), edgeState.getBaseNode());
                break;
            }

            tmpPrevEdge = tmpIter.getEdge();
        }
    }

    @Override
    public AllEdgesSkipIterator getAllEdges()
    {
        return new AllEdgeSkipIterator(baseGraph, propAccess);
    }

    class AllEdgeSkipIterator extends AllEdgeIterator implements AllEdgesSkipIterator
    {

        public AllEdgeSkipIterator( BaseGraph baseGraph, InternalGraphPropertyAccess propAccess )
        {
            super(baseGraph, propAccess);
        }

        @Override
        public final void setSkippedEdges( int edge1, int edge2 )
        {
            baseGraph.edges.setInt(edgePointer + I_SKIP_EDGE1, edge1);
            baseGraph.edges.setInt(edgePointer + I_SKIP_EDGE2, edge2);
        }

        @Override
        public final int getSkippedEdge1()
        {
            return baseGraph.edges.getInt(edgePointer + I_SKIP_EDGE1);
        }

        @Override
        public final int getSkippedEdge2()
        {
            return baseGraph.edges.getInt(edgePointer + I_SKIP_EDGE2);
        }

        @Override
        public final boolean isShortcut()
        {
            assert lastEdgeIndex >= 0 : "level graph not yet freezed";
            return edgePointer / baseGraph.edgeEntryBytes > lastEdgeIndex;
        }

        @Override
        public final EdgeSkipIterState setWeight( double weight )
        {
            LevelGraphImpl.this.setWeight(this, weight);
            return this;
        }

        @Override
        public final double getWeight()
        {
            return LevelGraphImpl.this.getWeight(this);
        }
    }

    protected SingleEdge createSingleEdge( int edge, int nodeId )
    {
        return new SingleLevelEdge(baseGraph, propAccess, edge, nodeId);
    }

    class SingleLevelEdge extends SingleEdge implements EdgeSkipIterState
    {

        public SingleLevelEdge( BaseGraph baseGraph, InternalGraphPropertyAccess propAccess, int edgeId, int nodeId )
        {
            super(baseGraph, propAccess, edgeId, nodeId);
        }

        @Override
        public final void setSkippedEdges( int edge1, int edge2 )
        {
            baseGraph.edges.setInt(edgePointer + I_SKIP_EDGE1, edge1);
            baseGraph.edges.setInt(edgePointer + I_SKIP_EDGE2, edge2);
        }

        @Override
        public final int getSkippedEdge1()
        {
            return baseGraph.edges.getInt(edgePointer + I_SKIP_EDGE1);
        }

        @Override
        public final int getSkippedEdge2()
        {
            return baseGraph.edges.getInt(edgePointer + I_SKIP_EDGE2);
        }

        @Override
        public final boolean isShortcut()
        {
            assert lastEdgeIndex >= 0 : "level graph not yet freezed";
            return edgeId > lastEdgeIndex;
        }

        @Override
        public final EdgeSkipIterState setWeight( double weight )
        {
            LevelGraphImpl.this.setWeight(this, weight);
            return this;
        }

        @Override
        public final double getWeight()
        {
            return LevelGraphImpl.this.getWeight(this);
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

    protected int loadEdgesHeader()
    {
        // TODO overwrites first call of baseGraph itself, currently does not matter
        int next = baseGraph.loadEdgesHeader();
        lastEdgeIndex = baseGraph.edges.getHeader(next * 4);
        return next + 1;
    }

    protected int setEdgesHeader()
    {
        // TODO overwrites first call of baseGraph itself, currently does not matter
        int next = baseGraph.setEdgesHeader();
        baseGraph.edges.setHeader(next * 4, lastEdgeIndex);
        return next + 1;
    }

    @Override
    public GraphExtension getExtension()
    {
        return baseGraph.getExtension();
    }

    @Override
    public Graph getBaseGraph()
    {
        return baseGraph;
    }

    @Override
    public Graph copyTo( Graph g )
    {
        // copying the shortcuts is currently done from base graph
        // but what about 'lastEdgeIndex'?
        return g;
    }

    void initStorage()
    {
        I_SKIP_EDGE1 = baseGraph.nextEdgeEntryIndex(4);
        I_SKIP_EDGE2 = baseGraph.nextEdgeEntryIndex(4);
        I_LEVEL = baseGraph.nextNodeEntryIndex(4);
        baseGraph.initNodeAndEdgeEntrySize();
    }

    void setSegmentSize( int bytes )
    {
        // nodesCH.setSegmentSize(bytes);
    }

    @Override
    public LevelGraph create( long bytes )
    {
        // nodesCH.create(bytes);
        return this;
    }

    @Override
    public boolean loadExisting()
    {
        loadEdgesHeader();
        return true;
    }

    @Override
    public void flush()
    {
        // nodesCH.flush();
    }

    @Override
    public void close()
    {
        // nodesCH.close();
    }

    @Override
    public boolean isClosed()
    {
        throw new IllegalStateException("not yet supported");
        // return nodesCH.isClosed();
    }

    @Override
    public long getCapacity()
    {
        return 0;
        // return nodesCH.getCapacity();
    }
}
