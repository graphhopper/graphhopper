/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
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

import com.graphhopper.routing.ch.NodeOrderingProvider;
import com.graphhopper.routing.ch.PrepareEncoder;
import com.graphhopper.routing.profiles.BooleanEncodedValue;
import com.graphhopper.routing.util.AllCHEdgesIterator;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.weighting.AbstractWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.BaseGraph.AllEdgeIterator;
import com.graphhopper.storage.BaseGraph.EdgeIterable;
import com.graphhopper.util.*;
import com.graphhopper.util.shapes.BBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

import static com.graphhopper.util.Helper.nf;

/**
 * A Graph implementation necessary for Contraction Hierarchies. This class enables the storage to
 * hold the level of a node and shortcut edges per edge.
 * <p>
 *
 * @author Peter Karich
 */
public class CHGraphImpl implements CHGraph, Storable<CHGraph> {
    private static final Logger LOGGER = LoggerFactory.getLogger(CHGraphImpl.class);
    private static final double WEIGHT_FACTOR = 1000f;
    // 2 bits for access, 29 bits for weight (See #1544 on how to improve this to 30 bits)
    private static final int MAX_WEIGHT_31 = (Integer.MAX_VALUE >> 2) << 2;
    private static final double MAX_WEIGHT = (Integer.MAX_VALUE >> 2) / WEIGHT_FACTOR;
    private static final double MIN_WEIGHT = 1 / WEIGHT_FACTOR;
    final DataAccess shortcuts;
    final DataAccess nodesCH;
    final int scDirMask = PrepareEncoder.getScDirMask();
    private final boolean edgeBased;
    private final BaseGraph baseGraph;
    private final CHEdgeAccess chEdgeAccess;
    private final Weighting weighting;
    int N_CH_REF;
    int shortcutEntryBytes;
    // the nodesCH storage is limited via baseGraph.nodeCount too
    int nodeCHEntryBytes;
    final int shortcutBytesForFlags = 4;
    private int N_LEVEL;
    // shortcut memory layout is synced with edges indices until E_FLAGS, then:
    private int S_SKIP_EDGE1, S_SKIP_EDGE2, S_ORIG_FIRST, S_ORIG_LAST;
    private int shortcutCount = 0;
    private boolean isReadyForContraction;

    CHGraphImpl(Weighting w, Directory dir, final BaseGraph baseGraph, boolean edgeBased) {
        if (w == null)
            throw new IllegalStateException("Weighting for CHGraph cannot be null");

        this.weighting = w;
        this.baseGraph = baseGraph;
        final String name = AbstractWeighting.weightingToFileName(w, edgeBased);
        this.edgeBased = edgeBased;
        this.nodesCH = dir.find("nodes_ch_" + name, DAType.getPreferredInt(dir.getDefaultType()));
        this.shortcuts = dir.find("shortcuts_" + name, DAType.getPreferredInt(dir.getDefaultType()));
        this.chEdgeAccess = new CHEdgeAccess(name);
    }

    public final Weighting getWeighting() {
        return weighting;
    }

    @Override
    public boolean isShortcut(int edgeId) {
        assert baseGraph.isFrozen() : "level graph not yet frozen";
        return edgeId >= baseGraph.edgeCount;
    }

    @Override
    public final void setLevel(int nodeIndex, int level) {
        checkNodeId(nodeIndex);
        nodesCH.setInt((long) nodeIndex * nodeCHEntryBytes + N_LEVEL, level);
    }

    @Override
    public final int getLevel(int nodeIndex) {
        checkNodeId(nodeIndex);
        return nodesCH.getInt((long) nodeIndex * nodeCHEntryBytes + N_LEVEL);
    }

    final void checkNodeId(int nodeId) {
        assert nodeId < baseGraph.getNodes() : "node " + nodeId + " is invalid. Not in [0," + baseGraph.getNodes() + ")";
    }

    @Override
    public CHEdgeIteratorState shortcut(int a, int b) {
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

    @Override
    public int shortcut(int a, int b, int accessFlags, double weight, double distance, int skippedEdge1, int skippedEdge2) {
        if (!baseGraph.isFrozen())
            throw new IllegalStateException("Cannot create shortcut if graph is not yet frozen");

        checkNodeId(a);
        checkNodeId(b);

        int scId = chEdgeAccess.internalEdgeAdd(nextShortcutId(), a, b);
        // do not create CHEdgeIteratorImpl object
        long edgePointer = chEdgeAccess.toPointer(scId);
        chEdgeAccess.setAccessAndWeight(edgePointer, accessFlags & scDirMask, weight);
        chEdgeAccess.setDist(edgePointer, distance);
        chEdgeAccess.setSkippedEdges(edgePointer, skippedEdge1, skippedEdge2);
        return scId;
    }

    @Override
    public int shortcutEdgeBased(int a, int b, int accessFlags, double weight, double distance, int skippedEdge1, int skippedEdge2, int origFirst, int origLast) {
        assert edgeBased : "Edge-based shortcuts should only be added when CHGraph is edge-based";
        int scId = shortcut(a, b, accessFlags, weight, distance, skippedEdge1, skippedEdge2);
        chEdgeAccess.setFirstAndLastOrigEdges(chEdgeAccess.toPointer(scId), origFirst, origLast);
        return scId;
    }

    protected int nextShortcutId() {
        int nextSC = shortcutCount;
        shortcutCount++;
        if (shortcutCount < 0)
            throw new IllegalStateException("too many shortcuts. new shortcut id would be negative. " + toString());

        shortcuts.ensureCapacity(((long) shortcutCount + 1) * shortcutEntryBytes);
        return nextSC + baseGraph.edgeCount;
    }

    @Override
    public EdgeIteratorState edge(int a, int b, double distance, boolean bothDirections) {
        return edge(a, b).setDistance(distance).setFlags(baseGraph.encodingManager.flagsDefault(true, bothDirections));
    }

    @Override
    public CHEdgeIteratorState edge(int a, int b) {
        // increase edge array not for shortcuts
        baseGraph.ensureNodeIndex(Math.max(a, b));
        int edgeId = baseGraph.edgeAccess.internalEdgeAdd(baseGraph.nextEdgeId(), a, b);
        CHEdgeIteratorImpl iter = new CHEdgeIteratorImpl(baseGraph, baseGraph.edgeAccess, EdgeFilter.ALL_EDGES);
        boolean ret = iter.init(edgeId, b);
        assert ret;
        return iter;
    }

    @Override
    public CHEdgeExplorer createEdgeExplorer() {
        return createEdgeExplorer(EdgeFilter.ALL_EDGES);
    }

    @Override
    public CHEdgeExplorer createEdgeExplorer(EdgeFilter filter) {
        return new CHEdgeIteratorImpl(baseGraph, chEdgeAccess, filter);
    }

    @Override
    public EdgeExplorer createOriginalEdgeExplorer() {
        return createOriginalEdgeExplorer(EdgeFilter.ALL_EDGES);
    }

    @Override
    public EdgeExplorer createOriginalEdgeExplorer(EdgeFilter filter) {
        return baseGraph.createEdgeExplorer(filter);
    }

    @Override
    public final CHEdgeIteratorState getEdgeIteratorState(int edgeId, int endNode) {
        if (isShortcut(edgeId)) {
            if (!chEdgeAccess.isInBounds(edgeId))
                throw new IllegalStateException("shortcutId " + edgeId + " out of bounds");
        } else if (!baseGraph.edgeAccess.isInBounds(edgeId))
            throw new IllegalStateException("edgeId " + edgeId + " out of bounds");

        return (CHEdgeIteratorState) chEdgeAccess.getEdgeProps(edgeId, endNode);
    }

    @Override
    public int getNodes() {
        return baseGraph.getNodes();
    }

    @Override
    public int getEdges() {
        return getAllEdges().length();
    }

    @Override
    public int getOriginalEdges() {
        return baseGraph.getEdges();
    }

    @Override
    public NodeAccess getNodeAccess() {
        return baseGraph.getNodeAccess();
    }

    @Override
    public BBox getBounds() {
        return baseGraph.getBounds();
    }

    @Override
    public boolean isReadyForContraction() {
        return isReadyForContraction;
    }

    @Override
    public int getOtherNode(int edge, int node) {
        EdgeAccess edgeAccess = isShortcut(edge) ? chEdgeAccess : baseGraph.edgeAccess;
        long edgePointer = edgeAccess.toPointer(edge);
        return edgeAccess.getOtherNode(node, edgePointer);
    }

    void _prepareForContraction() {
        if (isReadyForContraction) {
            return;
        }
        long maxCapacity = ((long) getNodes()) * nodeCHEntryBytes;
        nodesCH.ensureCapacity(maxCapacity);
        long baseCapacity = baseGraph.nodes.getCapacity();

        // copy normal edge refs into ch edge refs
        for (long pointer = N_CH_REF, basePointer = baseGraph.N_EDGE_REF;
             pointer < maxCapacity;
             pointer += nodeCHEntryBytes, basePointer += baseGraph.nodeEntryBytes) {
            if (basePointer >= baseCapacity)
                throw new IllegalStateException("Cannot copy edge refs into ch graph. "
                        + "pointer:" + pointer + ", cap:" + maxCapacity + ", basePtr:" + basePointer + ", baseCap:" + baseCapacity);

            nodesCH.setInt(pointer, baseGraph.nodes.getInt(basePointer));
        }
        isReadyForContraction = true;
    }

    String toDetailsString() {
        return toString() + ", shortcuts:" + nf(shortcutCount) + ", nodesCH:(" + nodesCH.getCapacity() / Helper.MB + "MB)";
    }

    @Override
    public void disconnect(CHEdgeExplorer explorer, EdgeIteratorState edgeState) {
        // search edge with opposite direction but we need to know the previousEdge for the internalEdgeDisconnect so we cannot simply do:
        // EdgeIteratorState tmpIter = getEdgeIteratorState(iter.getEdge(), iter.getBaseNode());
        CHEdgeIterator tmpIter = explorer.setBaseNode(edgeState.getAdjNode());
        int tmpPrevEdge = EdgeIterator.NO_EDGE;
        while (tmpIter.next()) {
            // note that we do not disconnect original edges, because we are re-using the base graph for different profiles,
            // even though this is not optimal from a speed performance point of view.
            if (tmpIter.isShortcut() && tmpIter.getEdge() == edgeState.getEdge()) {
                // TODO this is ugly, move this somehow into the underlying iteration logic
                long edgePointer = tmpPrevEdge == EdgeIterator.NO_EDGE ? -1
                        : isShortcut(tmpPrevEdge) ? chEdgeAccess.toPointer(tmpPrevEdge) : baseGraph.edgeAccess.toPointer(tmpPrevEdge);
                chEdgeAccess.internalEdgeDisconnect(edgeState.getEdge(), edgePointer, edgeState.getAdjNode());
                break;
            }

            tmpPrevEdge = tmpIter.getEdge();
        }
    }

    @Override
    public AllCHEdgesIterator getAllEdges() {
        return new AllCHEdgesIteratorImpl(baseGraph);
    }

    void loadNodesHeader() {
        isReadyForContraction = nodesCH.getHeader(0 * 4) == 1;
    }

    void setNodesHeader() {
        nodesCH.setHeader(0 * 4, isReadyForContraction ? 1 : 0);
    }

    protected int loadEdgesHeader() {
        shortcutCount = shortcuts.getHeader(0 * 4);
        shortcutEntryBytes = shortcuts.getHeader(1 * 4);
        return 3;
    }

    protected int setEdgesHeader() {
        shortcuts.setHeader(0 * 4, shortcutCount);
        shortcuts.setHeader(1 * 4, shortcutEntryBytes);
        return 3;
    }

    @Override
    public GraphExtension getExtension() {
        return baseGraph.getExtension();
    }

    @Override
    public Graph getBaseGraph() {
        return baseGraph;
    }

    @Override
    public Graph copyTo(Graph g) {
        CHGraphImpl tmpG = ((CHGraphImpl) g);

        nodesCH.copyTo(tmpG.nodesCH);
        shortcuts.copyTo(tmpG.shortcuts);

        tmpG.N_LEVEL = N_LEVEL;
        tmpG.N_CH_REF = N_CH_REF;
        tmpG.nodeCHEntryBytes = nodeCHEntryBytes;
        return g;
    }

    void initStorage() {
        EdgeAccess ea = baseGraph.edgeAccess;
        chEdgeAccess.init(ea.E_NODEA, ea.E_NODEB, ea.E_LINKA, ea.E_LINKB, ea.E_DIST, ea.E_FLAGS);
        // shortcuts
        S_SKIP_EDGE1 = ea.E_FLAGS + 4;
        S_SKIP_EDGE2 = S_SKIP_EDGE1 + 4;
        if (edgeBased) {
            S_ORIG_FIRST = S_SKIP_EDGE2 + 4;
            S_ORIG_LAST = S_ORIG_FIRST + 4;
            shortcutEntryBytes = S_ORIG_LAST + 4;
        } else {
            shortcutEntryBytes = S_SKIP_EDGE2 + 4;
        }

        // node based data:
        N_LEVEL = 0;
        N_CH_REF = N_LEVEL + 4;
        nodeCHEntryBytes = N_CH_REF + 4;
    }

    void setSegmentSize(int bytes) {
        nodesCH.setSegmentSize(bytes);
        shortcuts.setSegmentSize(bytes);
    }

    @Override
    public CHGraph create(long bytes) {
        nodesCH.create(bytes);
        shortcuts.create(bytes);
        return this;
    }

    @Override
    public boolean loadExisting() {
        if (!nodesCH.loadExisting() || !shortcuts.loadExisting())
            return false;

        loadNodesHeader();
        loadEdgesHeader();
        return true;
    }

    @Override
    public void flush() {
        nodesCH.flush();
        shortcuts.flush();
    }

    @Override
    public void close() {
        nodesCH.close();
        shortcuts.close();
    }

    @Override
    public boolean isClosed() {
        return nodesCH.isClosed();
    }

    @Override
    public long getCapacity() {
        return nodesCH.getCapacity() + shortcuts.getCapacity();
    }

    @Override
    public String toString() {
        return "CHGraph|" + getWeighting().toString();
    }

    public void debugPrint() {
        final int printMax = 100;
        System.out.println("nodesCH:");
        String formatNodes = "%12s | %12s | %12s \n";
        System.out.format(Locale.ROOT, formatNodes, "#", "N_CH_REF", "N_LEVEL");
        for (int i = 0; i < Math.min(baseGraph.getNodes(), printMax); ++i) {
            System.out.format(Locale.ROOT, formatNodes, i, chEdgeAccess.getEdgeRef(i), getLevel(i));
        }
        if (baseGraph.getNodes() > printMax) {
            System.out.format(Locale.ROOT, " ... %d more nodes", baseGraph.getNodes() - printMax);
        }
        System.out.println("shortcuts:");
        String formatShortcutsBase = "%12s | %12s | %12s | %12s | %12s | %12s | %12s | %12s | %12s";
        String formatShortcutExt = " | %12s | %12s";
        String header = String.format(Locale.ROOT, formatShortcutsBase, "#", "E_NODEA", "E_NODEB", "E_LINKA", "E_LINKB", "E_DIST", "E_FLAGS", "S_SKIP_EDGE1", "S_SKIP_EDGE2");
        if (edgeBased) {
            header += String.format(Locale.ROOT, formatShortcutExt, "S_ORIG_FIRST", "S_ORIG_LAST");
        }
        System.out.println(header);
        for (int i = baseGraph.edgeCount; i < baseGraph.edgeCount + Math.min(shortcutCount, printMax); ++i) {
            long edgePointer = chEdgeAccess.toPointer(i);
            String edgeString = String.format(Locale.ROOT, formatShortcutsBase,
                    i,
                    chEdgeAccess.getNodeA(edgePointer),
                    chEdgeAccess.getNodeB(edgePointer),
                    chEdgeAccess.getLinkA(edgePointer),
                    chEdgeAccess.getLinkB(edgePointer),
                    chEdgeAccess.getDist(edgePointer),
                    chEdgeAccess.getShortcutFlags(edgePointer),
                    shortcuts.getInt(edgePointer + S_SKIP_EDGE1),
                    shortcuts.getInt(edgePointer + S_SKIP_EDGE2));
            if (edgeBased) {
                edgeString += String.format(Locale.ROOT, formatShortcutExt,
                        shortcuts.getInt(edgePointer + S_ORIG_FIRST),
                        shortcuts.getInt(edgePointer + S_ORIG_LAST));
            }
            System.out.println(edgeString);
        }
        if (shortcutCount > printMax) {
            System.out.printf(Locale.ROOT, " ... %d more shortcut edges\n", shortcutCount - printMax);
        }
    }

    public NodeOrderingProvider getNodeOrderingProvider() {
        int numNodes = getNodes();
        final int[] nodeOrdering = new int[numNodes];
        // the node ordering is the inverse of the ch levels
        // if we really want to save some memory it could be still reasonable to not create the node ordering here,
        // but search nodesCH for a given level on demand.
        for (int i = 0; i < numNodes; ++i) {
            int level = getLevel(i);
            nodeOrdering[level] = i;
        }
        return new NodeOrderingProvider() {
            @Override
            public int getNodeIdForLevel(int level) {
                return nodeOrdering[level];
            }

            @Override
            public int getNumNodes() {
                return nodeOrdering.length;
            }
        };
    }

    class CHEdgeIteratorImpl extends EdgeIterable implements CHEdgeExplorer, CHEdgeIterator {
        public CHEdgeIteratorImpl(BaseGraph baseGraph, EdgeAccess edgeAccess, EdgeFilter filter) {
            super(baseGraph, edgeAccess, filter);
        }

        @Override
        public final IntsRef getFlags() {
            checkShortcut(false, "getFlags");
            return super.getFlags();
        }

        int getShortcutFlags() {
            if (!freshFlags) {
                chFlags = chEdgeAccess.getShortcutFlags(edgePointer);
                freshFlags = true;
            }
            return chFlags;
        }

        @Override
        public final CHEdgeIterator setBaseNode(int baseNode) {
            assert baseGraph.isFrozen() : "Traversal CHGraph is only possible if BaseGraph is frozen";

            // always use ch edge access
            setEdgeId(chEdgeAccess.getEdgeRef(baseNode));
            _setBaseNode(baseNode);
            return this;
        }

        @Override
        public final CHEdgeIteratorState setSkippedEdges(int edge1, int edge2) {
            checkShortcut(true, "setSkippedEdges");
            chEdgeAccess.setSkippedEdges(edgePointer, edge1, edge2);
            return this;
        }

        @Override
        public final int getSkippedEdge1() {
            checkShortcut(true, "getSkippedEdge1");
            return shortcuts.getInt(edgePointer + S_SKIP_EDGE1);
        }

        @Override
        public final int getSkippedEdge2() {
            checkShortcut(true, "getSkippedEdge2");
            return shortcuts.getInt(edgePointer + S_SKIP_EDGE2);
        }

        @Override
        public CHEdgeIteratorState setFirstAndLastOrigEdges(int firstOrigEdge, int lastOrigEdge) {
            checkShortcutAndEdgeBased("setFirstAndLastOrigEdges");
            chEdgeAccess.setFirstAndLastOrigEdges(edgePointer, firstOrigEdge, lastOrigEdge);
            return this;
        }

        @Override
        public int getOrigEdgeFirst() {
            if (!isShortcut()) {
                return getEdge();
            }
            checkShortcutAndEdgeBased("getOrigEdgeFirst");
            return shortcuts.getInt(edgePointer + S_ORIG_FIRST);
        }

        @Override
        public int getOrigEdgeLast() {
            if (!isShortcut()) {
                return getEdge();
            }
            checkShortcutAndEdgeBased("getOrigEdgeLast");
            return shortcuts.getInt(edgePointer + S_ORIG_LAST);
        }

        @Override
        public final boolean isShortcut() {
            // assert baseGraph.isFrozen() : "chgraph not yet frozen";
            return edgeId >= baseGraph.edgeCount;
        }

        @Override
        public boolean get(BooleanEncodedValue property) {
            // TODO assert equality of "access boolean encoded value" that is specifically created for CHGraph to make it possible we can use other BooleanEncodedValue objects for CH too!
            if (isShortcut())
                return (getShortcutFlags() & (reverse ? PrepareEncoder.getScBwdDir() : PrepareEncoder.getScFwdDir())) != 0;

            return property.getBool(reverse, getFlags());
        }

        @Override
        public boolean getReverse(BooleanEncodedValue property) {
            if (isShortcut())
                return (getShortcutFlags() & (reverse ? PrepareEncoder.getScFwdDir() : PrepareEncoder.getScBwdDir())) != 0;

            return property.getBool(!reverse, getFlags());
        }

        @Override
        public final CHEdgeIteratorState setWeight(double weight) {
            checkShortcut(true, "setWeight");
            chEdgeAccess.setShortcutWeight(edgePointer, weight);
            return this;
        }

        @Override
        public void setFlagsAndWeight(int flags, double weight) {
            checkShortcut(true, "setFlagsAndWeight");
            chEdgeAccess.setAccessAndWeight(edgePointer, flags, weight);
            chFlags = flags;
            freshFlags = true;
        }

        @Override
        public final double getWeight() {
            checkShortcut(true, "getWeight");
            return chEdgeAccess.getShortcutWeight(edgePointer);
        }

        @Override
        protected final void selectEdgeAccess() {
            // iterate over edges or shortcuts
            edgeAccess = nextEdgeId < baseGraph.edgeCount ? baseGraph.edgeAccess : chEdgeAccess;
        }

        public void checkShortcut(boolean shouldBeShortcut, String methodName) {
            if (isShortcut()) {
                if (!shouldBeShortcut)
                    throw new IllegalStateException("Cannot call " + methodName + " on shortcut " + getEdge());
            } else if (shouldBeShortcut)
                throw new IllegalStateException("Method " + methodName + " only for shortcuts " + getEdge());
        }

        private void checkShortcutAndEdgeBased(String method) {
            checkShortcut(true, method);
            if (!edgeBased) {
                throw new IllegalStateException("Method " + method + " only allowed when CH graph is configured for edge based traversal");
            }
        }

        @Override
        public final String getName() {
            checkShortcut(false, "getName");
            return super.getName();
        }

        @Override
        public final EdgeIteratorState setName(String name) {
            checkShortcut(false, "setName");
            return super.setName(name);
        }

        @Override
        public final PointList fetchWayGeometry(int mode) {
            checkShortcut(false, "fetchWayGeometry");
            return super.fetchWayGeometry(mode);
        }

        @Override
        public final EdgeIteratorState setWayGeometry(PointList list) {
            checkShortcut(false, "setWayGeometry");
            return super.setWayGeometry(list);
        }

        @Override
        public int getMergeStatus(int flags) {
            return PrepareEncoder.getScMergeStatus(getShortcutFlags(), flags);
        }
    }

    class AllCHEdgesIteratorImpl extends AllEdgeIterator implements AllCHEdgesIterator {
        public AllCHEdgesIteratorImpl(BaseGraph baseGraph) {
            super(baseGraph);
        }

        @Override
        protected final boolean checkRange() {
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

        int getShortcutFlags() {
            if (!freshFlags) {
                chFlags = chEdgeAccess.getShortcutFlags(edgePointer);
                freshFlags = true;
            }
            return chFlags;
        }

        @Override
        public int getEdge() {
            if (isShortcut())
                return baseGraph.edgeCount + edgeId;
            return super.getEdge();
        }

        @Override
        public boolean get(BooleanEncodedValue property) {
            // TODO assert equality of "access boolean encoded value" that is specifically created for CHGraph!
            if (isShortcut())
                return (getShortcutFlags() & (reverse ? PrepareEncoder.getScBwdDir() : PrepareEncoder.getScFwdDir())) != 0;

            return property.getBool(reverse, getFlags());
        }

        @Override
        public boolean getReverse(BooleanEncodedValue property) {
            if (isShortcut())
                return (getShortcutFlags() & (reverse ? PrepareEncoder.getScFwdDir() : PrepareEncoder.getScBwdDir())) != 0;

            return property.getBool(!reverse, getFlags());
        }

        @Override
        public final IntsRef getFlags() {
            if (isShortcut())
                throw new IllegalStateException("Shortcut should not need to return raw flags!");
            return super.getFlags();
        }

        @Override
        public int length() {
            return super.length() + shortcutCount;
        }

        @Override
        public final CHEdgeIteratorState setSkippedEdges(int edge1, int edge2) {
            checkShortcut(true, "setSkippedEdges");
            chEdgeAccess.setSkippedEdges(edgePointer, edge1, edge2);
            return this;
        }

        @Override
        public final int getSkippedEdge1() {
            checkShortcut(true, "getSkippedEdge1");
            return shortcuts.getInt(edgePointer + S_SKIP_EDGE1);
        }

        @Override
        public final int getSkippedEdge2() {
            checkShortcut(true, "getSkippedEdge2");
            return shortcuts.getInt(edgePointer + S_SKIP_EDGE2);
        }

        @Override
        public CHEdgeIteratorState setFirstAndLastOrigEdges(int firstOrigEdge, int lastOrigEdge) {
            checkShortcutAndEdgeBased("setFirstAndLastOrigEdges");
            shortcuts.setInt(edgePointer + S_ORIG_FIRST, firstOrigEdge);
            shortcuts.setInt(edgePointer + S_ORIG_LAST, lastOrigEdge);
            return this;
        }

        @Override
        public int getOrigEdgeFirst() {
            checkShortcutAndEdgeBased("getOrigEdgeFirst");
            return shortcuts.getInt(edgePointer + S_ORIG_FIRST);
        }

        @Override
        public int getOrigEdgeLast() {
            checkShortcutAndEdgeBased("getOrigEdgeLast");
            return shortcuts.getInt(edgePointer + S_ORIG_LAST);
        }

        @Override
        public final boolean isShortcut() {
            assert baseGraph.isFrozen() : "level graph not yet frozen";
            return edgeAccess == chEdgeAccess;
        }

        @Override
        public final CHEdgeIteratorState setWeight(double weight) {
            checkShortcut(true, "setWeight");
            chEdgeAccess.setShortcutWeight(edgePointer, weight);
            return this;
        }

        @Override
        public void setFlagsAndWeight(int flags, double weight) {
            checkShortcut(true, "setFlagsAndWeight");
            chEdgeAccess.setAccessAndWeight(edgePointer, flags, weight);
            chFlags = flags;
            freshFlags = true;
        }

        @Override
        public final double getWeight() {
            checkShortcut(true, "getWeight");
            return chEdgeAccess.getShortcutWeight(edgePointer);
        }

        @Override
        public int getMergeStatus(int flags) {
            return PrepareEncoder.getScMergeStatus(getShortcutFlags(), flags);
        }

        void checkShortcut(boolean shouldBeShortcut, String methodName) {
            if (isShortcut()) {
                if (!shouldBeShortcut)
                    throw new IllegalStateException("Cannot call " + methodName + " on shortcut " + getEdge());
            } else if (shouldBeShortcut)
                throw new IllegalStateException("Method " + methodName + " only for shortcuts " + getEdge());
        }

        private void checkShortcutAndEdgeBased(String method) {
            checkShortcut(true, method);
            if (!edgeBased) {
                throw new IllegalStateException("Method " + method + " not supported when turn costs are disabled");
            }
        }
    }

    private class CHEdgeAccess extends EdgeAccess {
        private final String name;

        public CHEdgeAccess(String name) {
            super(shortcuts);
            this.name = name;
        }

        @Override
        final EdgeIterable createSingleEdge(EdgeFilter edgeFilter) {
            return new CHEdgeIteratorImpl(baseGraph, this, edgeFilter);
        }

        @Override
        final int getEdgeRef(int nodeId) {
            return nodesCH.getInt((long) nodeId * nodeCHEntryBytes + N_CH_REF);
        }

        @Override
        final void setEdgeRef(int nodeId, int edgeId) {
            nodesCH.setInt((long) nodeId * nodeCHEntryBytes + N_CH_REF, edgeId);
        }

        @Override
        final int getEntryBytes() {
            return shortcutEntryBytes;
        }

        void setShortcutFlags(long edgePointer, int flags) {
            edges.setInt(edgePointer + E_FLAGS, flags);
        }

        int getShortcutFlags(long edgePointer) {
            return edges.getInt(edgePointer + E_FLAGS);
        }

        void setShortcutWeight(long edgePointer, double weight) {
            int accessFlags = getShortcutFlags(edgePointer) & scDirMask;
            setAccessAndWeight(edgePointer, accessFlags, weight);
        }

        void setAccessAndWeight(long edgePointer, int accessFlags, double weight) {
            int weightFlags = weightToWeightFlags(edgePointer, weight);
            setShortcutFlags(edgePointer, weightFlags | accessFlags);
        }

        int weightToWeightFlags(long edgePointer, double weight) {
            if (weight < 0)
                throw new IllegalArgumentException("weight cannot be negative but was " + weight);

            int weightInt;

            if (weight < MIN_WEIGHT) {
                NodeAccess nodeAccess = getNodeAccess();
                // todo: how to get edge id
                int edgeId = -1;
                LOGGER.warn("Setting weights smaller than " + MIN_WEIGHT + " is not allowed in CHGraphImpl#setWeight. " +
                        "You passed: " + weight + " for the edge " + edgeId +
                        " nodeA " + nodeAccess.getLat(getNodeA(edgePointer)) + "," + nodeAccess.getLon(getNodeA(edgePointer)) +
                        " nodeB " + nodeAccess.getLat(getNodeB(edgePointer)) + "," + nodeAccess.getLon(getNodeB(edgePointer)));
                weight = MIN_WEIGHT;
            }
            if (weight > MAX_WEIGHT)
                weightInt = MAX_WEIGHT_31;
            else
                weightInt = ((int) Math.round(weight * WEIGHT_FACTOR)) << 2;
            return weightInt;
        }

        double getShortcutWeight(long edgePointer) {
            // no need for reverseFlags call (shortcut has identical weight if both dies) and also no need for 64bit
            long flags32bit = getShortcutFlags(edgePointer);
            double weight = (flags32bit >>> 2) / WEIGHT_FACTOR;
            if (weight >= MAX_WEIGHT)
                return Double.POSITIVE_INFINITY;

            return weight;
        }

        void setSkippedEdges(long edgePointer, int edge1, int edge2) {
            if (EdgeIterator.Edge.isValid(edge1) != EdgeIterator.Edge.isValid(edge2)) {
                throw new IllegalStateException("Skipped edges of a shortcut needs "
                        + "to be both valid or invalid but they were not " + edge1 + ", " + edge2);
            }
            shortcuts.setInt(edgePointer + S_SKIP_EDGE1, edge1);
            shortcuts.setInt(edgePointer + S_SKIP_EDGE2, edge2);
        }

        public void setFirstAndLastOrigEdges(long edgePointer, int origFirst, int origLast) {
            shortcuts.setInt(edgePointer + S_ORIG_FIRST, origFirst);
            shortcuts.setInt(edgePointer + S_ORIG_LAST, origLast);
        }

        @Override
        final long toPointer(int shortcutId) {
            assert isInBounds(shortcutId) : "shortcutId " + shortcutId + " not in bounds [" + baseGraph.edgeCount + ", " + (baseGraph.edgeCount + shortcutCount) + ")";
            return (long) (shortcutId - baseGraph.edgeCount) * shortcutEntryBytes;
        }

        @Override
        final boolean isInBounds(int shortcutId) {
            int tmp = shortcutId - baseGraph.edgeCount;
            return tmp < shortcutCount && tmp >= 0;
        }

        @Override
        public String toString() {
            return "ch edge access " + name;
        }
    }
}
