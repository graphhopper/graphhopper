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
import com.graphhopper.routing.util.AllCHEdgesIterator;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.storage.BaseGraph.AllEdgeIterator;
import com.graphhopper.storage.BaseGraph.EdgeIterable;
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
    private int N_LEVEL;
    int N_CH_REF;
    // shortcut memory layout is synced with edges indices until E_FLAGS, then:
    private int S_SKIP_EDGE1, S_SKIP_EDGE2;

    int shortcutEntryBytes;
    private int shortcutCount = 0;
    final DataAccess shortcuts;
    // the nodesCH storage is limited via baseGraph.nodeCount too
    int nodeCHEntryBytes;
    final DataAccess nodesCH;
    final long scDirMask = PrepareEncoder.getScDirMask();
    private final BaseGraph baseGraph;
    private final EdgeAccess chEdgeAccess;

    CHGraphImpl( String name, Directory dir, final BaseGraph baseGraph )
    {
        this.baseGraph = baseGraph;
        this.nodesCH = dir.find("nodes_ch");
        this.shortcuts = dir.find("shortcuts");
        this.chEdgeAccess = new EdgeAccess(shortcuts, baseGraph.bitUtil)
        {
            @Override
            final EdgeIterable createSingleEdge( EdgeFilter edgeFilter )
            {
                return new CHEdgeIteratorImpl(baseGraph, this, edgeFilter);
            }

            @Override
            final int getEdgeRef( int nodeId )
            {
                return nodesCH.getInt((long) nodeId * nodeCHEntryBytes + N_CH_REF);
            }

            @Override
            final void setEdgeRef( int nodeId, int edgeId )
            {
                nodesCH.setInt((long) nodeId * nodeCHEntryBytes + N_CH_REF, edgeId);
            }

            @Override
            final int getEntryBytes()
            {
                return shortcutEntryBytes;
            }

            @Override
            final long toPointer( int shortcutId )
            {
                assert isInBounds(shortcutId) : "shortcutId " + shortcutId + " not in bounds [" + baseGraph.edgeCount + ", " + (baseGraph.edgeCount + shortcutCount) + ")";
                return (long) (shortcutId - baseGraph.edgeCount) * shortcutEntryBytes;
            }

            @Override
            final boolean isInBounds( int shortcutId )
            {
                int tmp = shortcutId - baseGraph.edgeCount;
                return tmp < shortcutCount && tmp >= 0;
            }

            @Override
            final long reverseFlags( long edgePointer, long flags )
            {
                boolean isShortcut = edgePointer >= toPointer(baseGraph.edgeCount);
                if (!isShortcut)
                    return baseGraph.edgeAccess.reverseFlags(edgePointer, flags);

                // we need a special swapping for level graph if it is a shortcut as we only store the weight and access flags then
                long dir = flags & scDirMask;
                if (dir == scDirMask || dir == 0)
                    return flags;

                // swap the last bits with this mask
                return flags ^ scDirMask;
            }

            @Override
            public String toString()
            {
                return "ch edge access";
            }
        };
    }

    @Override
    public boolean isShortcut( int edgeId )
    {
        assert baseGraph.isFrozen() : "level graph not yet frozen";
        return edgeId >= baseGraph.edgeCount;
    }

    @Override
    public final void setLevel( int nodeIndex, int level )
    {
        checkNodeId(nodeIndex);
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
        assert nodeId < baseGraph.getNodes() : "node " + nodeId + " is invalid. Not in [0," + baseGraph.getNodes() + ")";
    }

    @Override
    public CHEdgeIteratorState shortcut( int a, int b )
    {
        if (!baseGraph.isFrozen())
            throw new IllegalStateException("Cannot create shortcut if graph is not yet frozen");

        checkNodeId(a);
        checkNodeId(b);

        int scId = chEdgeAccess.internalEdgeAdd(nextShortcutId(), a, b);
        CHEdgeIteratorImpl iter = new CHEdgeIteratorImpl(baseGraph, chEdgeAccess, EdgeFilter.ALL_EDGES);
        boolean ret = iter.init(scId, b);
        assert ret;
        iter.setSkippedEdges(EdgeIterator.NO_EDGE, EdgeIterator.NO_EDGE);
        return iter;
    }

    protected int nextShortcutId()
    {
        int nextSC = shortcutCount;
        shortcutCount++;
        if (shortcutCount < 0)
            throw new IllegalStateException("too many shortcuts. new shortcut id would be negative. " + toString());

        shortcuts.ensureCapacity(((long) shortcutCount + 1) * shortcutEntryBytes);
        return nextSC + baseGraph.edgeCount;
    }

    @Override
    public EdgeIteratorState edge( int a, int b, double distance, boolean bothDirections )
    {
        return edge(a, b).setDistance(distance).setFlags(baseGraph.encodingManager.flagsDefault(true, bothDirections));
    }

    @Override
    public CHEdgeIteratorState edge( int a, int b )
    {
        // increase edge array not for shortcuts
        baseGraph.ensureNodeIndex(Math.max(a, b));
        int edgeId = baseGraph.edgeAccess.internalEdgeAdd(baseGraph.nextEdgeId(), a, b);
        CHEdgeIteratorImpl iter = new CHEdgeIteratorImpl(baseGraph, baseGraph.edgeAccess, EdgeFilter.ALL_EDGES);
        boolean ret = iter.init(edgeId, b);
        assert ret;
        return iter;
    }

    @Override
    public CHEdgeExplorer createEdgeExplorer()
    {
        return createEdgeExplorer(EdgeFilter.ALL_EDGES);
    }

    @Override
    public CHEdgeExplorer createEdgeExplorer( EdgeFilter filter )
    {
        return new CHEdgeIteratorImpl(baseGraph, chEdgeAccess, filter);
    }

    @Override
    public final CHEdgeIteratorState getEdgeIteratorState( int edgeId, int endNode )
    {
        if (isShortcut(edgeId))
        {
            if (!chEdgeAccess.isInBounds(edgeId))
                throw new IllegalStateException("shortcutId " + edgeId + " out of bounds");
        } else
        {
            if (!baseGraph.edgeAccess.isInBounds(edgeId))
                throw new IllegalStateException("edgeId " + edgeId + " out of bounds");
        }

        return (CHEdgeIteratorState) chEdgeAccess.getEdgeProps(edgeId, endNode);
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

    class CHEdgeIteratorImpl extends EdgeIterable implements CHEdgeExplorer, CHEdgeIterator
    {
        public CHEdgeIteratorImpl( BaseGraph baseGraph, EdgeAccess edgeAccess, EdgeFilter filter )
        {
            super(baseGraph, edgeAccess, filter);
        }

        @Override
        public final CHEdgeIterator setBaseNode( int baseNode )
        {
            assert baseGraph.isFrozen() : "Traversal CHGraph is only possible if BaseGraph is frozen";

            // always use ch edge access
            setEdgeId(chEdgeAccess.getEdgeRef(baseNode));
            _setBaseNode(baseNode);
            return this;
        }

        @Override
        public final void setSkippedEdges( int edge1, int edge2 )
        {
            assert isShortcut() : "cannot access skipped edges if not a shortcut";
            if (EdgeIterator.Edge.isValid(edge1) != EdgeIterator.Edge.isValid(edge2))
            {
                throw new IllegalStateException("Skipped edges of a shortcut needs "
                        + "to be both valid or invalid but they were not " + edge1 + ", " + edge2);
            }
            shortcuts.setInt(edgePointer + S_SKIP_EDGE1, edge1);
            shortcuts.setInt(edgePointer + S_SKIP_EDGE2, edge2);
        }

        @Override
        public final int getSkippedEdge1()
        {
            assert isShortcut() : "cannot access skipped edge if not a shortcut";
            return shortcuts.getInt(edgePointer + S_SKIP_EDGE1);
        }

        @Override
        public final int getSkippedEdge2()
        {
            assert isShortcut() : "cannot access skipped edge if not a shortcut";
            return shortcuts.getInt(edgePointer + S_SKIP_EDGE2);
        }

        @Override
        public final boolean isShortcut()
        {
            // assert baseGraph.isFrozen() : "chgraph not yet frozen";
            return edgeId >= baseGraph.edgeCount;
        }

        @Override
        public final CHEdgeIteratorState setWeight( double weight )
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
        protected final void selectEdgeAccess()
        {
            if (nextEdgeId < baseGraph.edgeCount)
                // iterate over edges
                edgeAccess = baseGraph.edgeAccess;
            else
                // ... or shortcuts
                edgeAccess = chEdgeAccess;
        }

        @Override
        public final String getName()
        {
            if (isShortcut())
                throw new IllegalStateException("Cannot call getName on shortcut " + getEdge());
            return super.getName();
        }

        @Override
        public final EdgeIteratorState setName( String name )
        {
            if (isShortcut())
                throw new IllegalStateException("Cannot call setName on shortcut " + getEdge());
            return super.setName(name);
        }

        @Override
        public final PointList fetchWayGeometry( int mode )
        {
            if (isShortcut())
                throw new IllegalStateException("Cannot call fetchWayGeometry on shortcut " + getEdge());
            return super.fetchWayGeometry(mode);
        }

        @Override
        public final EdgeIteratorState setWayGeometry( PointList list )
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
    public void disconnect( CHEdgeExplorer explorer, EdgeIteratorState edgeState )
    {
        // search edge with opposite direction but we need to know the previousEdge for the internalEdgeDisconnect so we cannot simply do:
        // EdgeIteratorState tmpIter = getEdgeProps(iter.getEdge(), iter.getBaseNode());
        CHEdgeIterator tmpIter = explorer.setBaseNode(edgeState.getAdjNode());
        int tmpPrevEdge = EdgeIterator.NO_EDGE;
        while (tmpIter.next())
        {
            if (tmpIter.isShortcut() && tmpIter.getEdge() == edgeState.getEdge())
            {
                // TODO this is ugly, move this somehow into the underlying iteration logic
                long edgePointer = tmpPrevEdge == EdgeIterator.NO_EDGE ? -1
                        : isShortcut(tmpPrevEdge) ? chEdgeAccess.toPointer(tmpPrevEdge) : baseGraph.edgeAccess.toPointer(tmpPrevEdge);
                chEdgeAccess.internalEdgeDisconnect(edgeState.getEdge(), edgePointer,
                        edgeState.getAdjNode(), edgeState.getBaseNode());
                break;
            }

            tmpPrevEdge = tmpIter.getEdge();
        }
    }

    @Override
    public AllCHEdgesIterator getAllEdges()
    {
        return new AllCHEdgesIteratorImpl(baseGraph);
    }

    class AllCHEdgesIteratorImpl extends AllEdgeIterator implements AllCHEdgesIterator
    {
        public AllCHEdgesIteratorImpl( BaseGraph baseGraph )
        {
            super(baseGraph);
        }

        @Override
        protected final boolean checkRange()
        {
            if (isShortcut())
                return edgeId < shortcutCount;

            if (super.checkRange())
                return true;

            // iterate over shortcuts
            edgeAccess = chEdgeAccess;
            edgeId = 0;
            edgePointer = (long) edgeId * shortcutEntryBytes;
            return edgeId < shortcutCount;
        }

        @Override
        public int getMaxId()
        {
            return super.getMaxId() + shortcutCount;
        }

        @Override
        public final void setSkippedEdges( int edge1, int edge2 )
        {
            baseGraph.edges.setInt(edgePointer + S_SKIP_EDGE1, edge1);
            baseGraph.edges.setInt(edgePointer + S_SKIP_EDGE2, edge2);
        }

        @Override
        public final int getSkippedEdge1()
        {
            return baseGraph.edges.getInt(edgePointer + S_SKIP_EDGE1);
        }

        @Override
        public final int getSkippedEdge2()
        {
            return baseGraph.edges.getInt(edgePointer + S_SKIP_EDGE2);
        }

        @Override
        public final boolean isShortcut()
        {
            assert baseGraph.isFrozen() : "level graph not yet frozen";
            return edgeAccess == chEdgeAccess;
        }

        @Override
        public final CHEdgeIteratorState setWeight( double weight )
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
        public int getEdge()
        {
            if (isShortcut())
                return baseGraph.edgeCount + edgeId;
            return super.getEdge();
        }
    }

    final void setWeight( CHEdgeIteratorState edge, double weight )
    {
        if (!edge.isShortcut())
            throw new IllegalStateException("setWeight is only available for shortcuts");
        if (weight < 0)
            throw new IllegalArgumentException("weight cannot be negative but was " + weight);

        long weightLong;
        if (weight > MAX_WEIGHT)
            weightLong = MAX_WEIGHT_LONG;
        else
            weightLong = ((long) (weight * WEIGHT_FACTOR)) << 2;

        long accessFlags = edge.getFlags() & scDirMask;
        edge.setFlags(weightLong | accessFlags);
    }

    final double getWeight( CHEdgeIteratorState edge )
    {
        if (!edge.isShortcut())
            throw new IllegalStateException("getWeight is only available for shortcuts");

        // Note: code duplication here but getWeight is very performance critical!
        // we need to avoid reverseFlags call for getFlags and no need for 64bit
        int flags32bit = chEdgeAccess.edges.getInt(((CHEdgeIteratorImpl) edge).edgePointer + chEdgeAccess.E_FLAGS);
        double weight = (flags32bit >>> 2) / WEIGHT_FACTOR;
        if (weight >= MAX_WEIGHT)
            return Double.POSITIVE_INFINITY;

        return weight;
    }

    protected int loadEdgesHeader()
    {
        shortcutCount = shortcuts.getHeader(0 * 4);
        shortcutEntryBytes = shortcuts.getHeader(1 * 4);
        return 3;
    }

    protected int setEdgesHeader()
    {
        shortcuts.setHeader(0 * 4, shortcutCount);
        shortcuts.setHeader(1 * 4, shortcutEntryBytes);
        return 3;
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
        shortcuts.copyTo(tmpG.shortcuts);

        tmpG.N_LEVEL = N_LEVEL;
        tmpG.N_CH_REF = N_CH_REF;
        tmpG.nodeCHEntryBytes = nodeCHEntryBytes;
        return g;
    }

    void initStorage()
    {
        EdgeAccess ea = baseGraph.edgeAccess;
        chEdgeAccess.init(ea.E_NODEA, ea.E_NODEB, ea.E_LINKA, ea.E_LINKB, ea.E_DIST, ea.E_FLAGS, false);
        // shortcuts
        S_SKIP_EDGE1 = ea.E_FLAGS + 4;
        S_SKIP_EDGE2 = S_SKIP_EDGE1 + 4;
        shortcutEntryBytes = S_SKIP_EDGE2 + 4;

        // node based data:
        N_LEVEL = 0;
        N_CH_REF = N_LEVEL + 4;
        nodeCHEntryBytes = N_CH_REF + 4;
    }

    void setSegmentSize( int bytes )
    {
        nodesCH.setSegmentSize(bytes);
        shortcuts.setSegmentSize(bytes);
    }

    @Override
    public CHGraph create( long bytes )
    {
        nodesCH.create(bytes);
        shortcuts.create(bytes);
        return this;
    }

    @Override
    public boolean loadExisting()
    {
        if (!nodesCH.loadExisting() || !shortcuts.loadExisting())
            return false;

        loadEdgesHeader();
        return true;
    }

    @Override
    public void flush()
    {
        nodesCH.flush();
        shortcuts.flush();
    }

    @Override
    public void close()
    {
        nodesCH.close();
        shortcuts.close();
    }

    @Override
    public boolean isClosed()
    {
        return nodesCH.isClosed();
    }

    @Override
    public long getCapacity()
    {
        return nodesCH.getCapacity() + shortcuts.getCapacity();
    }
}
