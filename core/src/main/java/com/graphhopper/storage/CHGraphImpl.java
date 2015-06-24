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
 * A Graph implementation necessary for Contraction Hierarchies. This class enables the storage to
 * hold the level of a node and shortcut edges per edge.
 * <p/>
 * @author Peter Karich
 */
public class CHGraphImpl implements CHGraph, Storable<CHGraph>
{
    private static final double WEIGHT_FACTOR = 1000f;
    // 2 bits for access, for now only 32bit => not Long.MAX
    private static final long MAX_WEIGHT_LONG = (Integer.MAX_VALUE >> 2) << 2;
    private static final double MAX_WEIGHT = (Integer.MAX_VALUE >> 2) / WEIGHT_FACTOR;
    private int E_SKIP_EDGE1;
    private int E_SKIP_EDGE2;
    private int N_LEVEL;
    int N_CH_REF;
    // after the last edge only shortcuts are stored
    int lastEdgeIndex = -2;
    private int shortcutCount = 0;
    final long scDirMask = PrepareEncoder.getScDirMask();
    private final BaseGraph baseGraph;
    private final InternalGraphPropertyAccess chPropAccess;
    final DataAccess nodesCH;
    int nodeCHEntryBytes;
    private final BaseGraph.EdgeAccess chEdgeAccess;

    CHGraphImpl( String name, Directory dir, BaseGraph baseGraph, InternalGraphPropertyAccess propAccess )
    {
        this.baseGraph = baseGraph;
        this.chPropAccess = propAccess;
        this.nodesCH = dir.find("nodes_ch");
        this.chEdgeAccess = new BaseGraph.EdgeAccess(baseGraph.edges, propAccess, baseGraph);
    }

    @Override
    public boolean isShortcut( int edgeId )
    {
        assert lastEdgeIndex > -2 : "level graph not yet freezed";
        return edgeId > lastEdgeIndex;
    }

    @Override
    public final void setLevel( int nodeIndex, int level )
    {
        if (nodeIndex >= baseGraph.getNodes())
            return;

        nodesCH.setInt((long) nodeIndex * nodeCHEntryBytes + N_LEVEL, level);
    }

    @Override
    public final int getLevel( int nodeIndex )
    {
        checkNodeId(nodeIndex);
        return nodesCH.getInt((long) nodeIndex * nodeCHEntryBytes + N_LEVEL);
    }

    final void checkNodeId( int nodeId )
    {
        if (nodeId >= baseGraph.getNodes())
            throw new IllegalStateException("node " + nodeId + " is invalid. Not in [0," + baseGraph.getNodes() + ")");
    }

    @Override
    public EdgeSkipIterState shortcut( int a, int b )
    {
        if (!baseGraph.isFreezed())
            throw new IllegalStateException("Cannot create shortcut if graph is not yet freezed");

        checkNodeId(a);
        checkNodeId(b);
        shortcutCount++;

        return createEdge(chEdgeAccess, chPropAccess, a, b);
    }

    @Override
    public EdgeIteratorState edge( int a, int b, double distance, boolean bothDirections )
    {
        return edge(a, b).setDistance(distance).setFlags(baseGraph.encodingManager.flagsDefault(true, bothDirections));
    }

    @Override
    public EdgeSkipIterState edge( int a, int b )
    {
        // increase edge array not for shortcuts
        baseGraph.ensureNodeIndex(Math.max(a, b));
        return createEdge(baseGraph.edgeAccess, baseGraph.propAccess, a, b);
    }

    private EdgeSkipIterState createEdge( BaseGraph.EdgeAccess ea, InternalGraphPropertyAccess tmpPA, int a, int b )
    {
        int edgeId = ea.internalEdgeAdd(a, b);
        EdgeSkipIteratorImpl iter = new EdgeSkipIteratorImpl(baseGraph, tmpPA, EdgeFilter.ALL_EDGES);
        iter.setBaseNodeUnchecked(a);
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
        return new EdgeSkipIteratorImpl(baseGraph, chPropAccess, filter);
    }

    @Override
    public final EdgeSkipIterState getEdgeProps( int edgeId, int endNode )
    {
        return (EdgeSkipIterState) baseGraph.getEdgeProps(chPropAccess, edgeId, endNode);
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

    void _freeze()
    {
        lastEdgeIndex = baseGraph.edgeCount - 1;

        long maxCapacity = ((long) getNodes()) * nodeCHEntryBytes;
        nodesCH.ensureCapacity(maxCapacity);
        long baseCapacity = baseGraph.nodes.getCapacity();

        // copy normal edge refs into ch edge refs
        for (long pointer = N_CH_REF, basePointer = baseGraph.N_EDGE_REF;
                pointer < maxCapacity;
                pointer += nodeCHEntryBytes, basePointer += baseGraph.nodeEntryBytes)
        {
            if (basePointer >= baseCapacity)
                throw new IllegalStateException("Cannot copy edge refs into ch graph. "
                        + "pointer:" + pointer + ", cap:" + maxCapacity + ", basePtr:" + basePointer + ", baseCap:" + baseCapacity);

            nodesCH.setInt(pointer, baseGraph.nodes.getInt(basePointer));
        }
    }

    String toDetailsString()
    {
        return "shortcuts:" + shortcutCount + ", nodesCH: -(" + nodesCH.getCapacity() / Helper.MB + ")";
    }

    class EdgeSkipIteratorImpl extends EdgeIterable implements EdgeSkipExplorer, EdgeSkipIterator
    {
        public EdgeSkipIteratorImpl( BaseGraph baseGraph, InternalGraphPropertyAccess propAccess, EdgeFilter filter )
        {
            super(baseGraph, propAccess, filter);
        }

        private EdgeSkipIterator setBaseNodeUnchecked( int baseNode )
        {
            super.setBaseNode(baseNode);
            return this;
        }

        @Override
        public final EdgeSkipIterator setBaseNode( int baseNode )
        {
            if (!baseGraph.isFreezed())
                throw new IllegalStateException("Traversal CHGraph is only possible if BaseGraph is freezed");

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
            baseGraph.edges.setInt(edgePointer + E_SKIP_EDGE1, edge1);
            baseGraph.edges.setInt(edgePointer + E_SKIP_EDGE2, edge2);
        }

        @Override
        public final int getSkippedEdge1()
        {
            return baseGraph.edges.getInt(edgePointer + E_SKIP_EDGE1);
        }

        @Override
        public final int getSkippedEdge2()
        {
            return baseGraph.edges.getInt(edgePointer + E_SKIP_EDGE2);
        }

        @Override
        public final boolean isShortcut()
        {
            // TODO include later
            // assert lastEdgeIndex > -2 : "level graph not yet freezed";
            if (lastEdgeIndex < 0)
                return false;

            return edgeId > lastEdgeIndex;
        }

        @Override
        public final EdgeSkipIterState setWeight( double weight )
        {
            CHGraphImpl.this.setWeight(this, weight);
            return this;
        }

        @Override
        public final double getWeight()
        {
            return CHGraphImpl.this.getWeight(this);
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
                baseGraph.internalEdgeDisconnect(chPropAccess,
                        edgeState.getEdge(), (long) tmpPrevEdge * baseGraph.edgeEntryBytes,
                        edgeState.getAdjNode(), edgeState.getBaseNode());
                break;
            }

            tmpPrevEdge = tmpIter.getEdge();
        }
    }

    @Override
    public AllEdgesSkipIterator getAllEdges()
    {
        return new AllEdgeSkipIterator(baseGraph, chPropAccess);
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
            baseGraph.edges.setInt(edgePointer + E_SKIP_EDGE1, edge1);
            baseGraph.edges.setInt(edgePointer + E_SKIP_EDGE2, edge2);
        }

        @Override
        public final int getSkippedEdge1()
        {
            return baseGraph.edges.getInt(edgePointer + E_SKIP_EDGE1);
        }

        @Override
        public final int getSkippedEdge2()
        {
            return baseGraph.edges.getInt(edgePointer + E_SKIP_EDGE2);
        }

        @Override
        public final boolean isShortcut()
        {
            assert lastEdgeIndex > -2 : "level graph not yet freezed";
            return edgePointer / baseGraph.edgeEntryBytes > lastEdgeIndex;
        }

        @Override
        public final EdgeSkipIterState setWeight( double weight )
        {
            CHGraphImpl.this.setWeight(this, weight);
            return this;
        }

        @Override
        public final double getWeight()
        {
            return CHGraphImpl.this.getWeight(this);
        }
    }

    protected SingleEdge createSingleEdge( int edge, int nodeId )
    {
        return new SingleLevelEdge(baseGraph, chPropAccess, edge, nodeId);
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
            baseGraph.edges.setInt(edgePointer + E_SKIP_EDGE1, edge1);
            baseGraph.edges.setInt(edgePointer + E_SKIP_EDGE2, edge2);
        }

        @Override
        public final int getSkippedEdge1()
        {
            return baseGraph.edges.getInt(edgePointer + E_SKIP_EDGE1);
        }

        @Override
        public final int getSkippedEdge2()
        {
            return baseGraph.edges.getInt(edgePointer + E_SKIP_EDGE2);
        }

        @Override
        public final boolean isShortcut()
        {
            assert lastEdgeIndex > -2 : "level graph not yet freezed";
            return edgeId > lastEdgeIndex;
        }

        @Override
        public final EdgeSkipIterState setWeight( double weight )
        {
            CHGraphImpl.this.setWeight(this, weight);
            return this;
        }

        @Override
        public final double getWeight()
        {
            return CHGraphImpl.this.getWeight(this);
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
        next++;
        shortcutCount = baseGraph.edges.getHeader(next * 4);
        next++;
        return next;
    }

    protected int setEdgesHeader()
    {
        // TODO overwrites first call of baseGraph itself, currently does not matter
        int next = baseGraph.setEdgesHeader();
        baseGraph.edges.setHeader(next * 4, lastEdgeIndex);
        next++;
        baseGraph.edges.setHeader(next * 4, shortcutCount);
        next++;
        return next;
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
        CHGraphImpl tmpG = ((CHGraphImpl) g);

        nodesCH.copyTo(tmpG.nodesCH);
        // TODO move in loadNodesHeader method similar to base graph
        tmpG.N_LEVEL = N_LEVEL;
        tmpG.N_CH_REF = N_CH_REF;
        tmpG.nodeCHEntryBytes = nodeCHEntryBytes;
        return g;
    }

    void initStorage()
    {
        E_SKIP_EDGE1 = baseGraph.nextEdgeEntryIndex(4);
        E_SKIP_EDGE2 = baseGraph.nextEdgeEntryIndex(4);
        N_LEVEL = 0;
        N_CH_REF = 4;
        nodeCHEntryBytes = 8;
        // still necessary to update the width of the edge column due to e_skip1+2
        baseGraph.initNodeAndEdgeEntrySize();
    }

    void setSegmentSize( int bytes )
    {
        nodesCH.setSegmentSize(bytes);
    }

    @Override
    public CHGraph create( long bytes )
    {
        nodesCH.create(bytes);
        return this;
    }

    @Override
    public boolean loadExisting()
    {
        loadEdgesHeader();
        return nodesCH.loadExisting();
    }

    @Override
    public void flush()
    {
        nodesCH.flush();
    }

    @Override
    public void close()
    {
        nodesCH.close();
    }

    @Override
    public boolean isClosed()
    {
        return nodesCH.isClosed();
    }

    @Override
    public long getCapacity()
    {
        return nodesCH.getCapacity();
    }
}
