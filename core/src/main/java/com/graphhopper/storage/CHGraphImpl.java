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
import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.EnumEncodedValue;
import com.graphhopper.routing.ev.IntEncodedValue;
import com.graphhopper.routing.ev.StringEncodedValue;
import com.graphhopper.routing.util.AllCHEdgesIterator;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.storage.BaseGraph.AllEdgeIterator;
import com.graphhopper.storage.BaseGraph.EdgeIteratorImpl;
import com.graphhopper.util.*;
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
    private final CHConfig chConfig;
    private final BaseGraph baseGraph;
    // CH node memory layout, there are as many entries has baseGraph.nodeCount
    private int N_LEVEL, N_CH_REF;
    private int nodeCHEntryBytes;
    // shortcut memory layout
    private int E_NODEA, E_NODEB, S_WEIGHT, S_SKIP_EDGE1, S_SKIP_EDGE2, S_ORIG_FIRST, S_ORIG_LAST;
    private int shortcutEntryBytes;
    private int shortcutCount = 0;
    private boolean isReadyForContraction;

    CHGraphImpl(CHConfig chConfig, Directory dir, final BaseGraph baseGraph, int segmentSize) {
        if (chConfig.getWeighting() == null)
            throw new IllegalStateException("Weighting for CHGraph cannot be null");
        this.chConfig = chConfig;
        this.baseGraph = baseGraph;
        final String name = chConfig.getName();
        this.nodesCH = dir.find("nodes_ch_" + name, DAType.getPreferredInt(dir.getDefaultType()));
        this.shortcuts = dir.find("shortcuts_" + name, DAType.getPreferredInt(dir.getDefaultType()));
        if (segmentSize >= 0) {
            nodesCH.setSegmentSize(segmentSize);
            shortcuts.setSegmentSize(segmentSize);
        }
    }

    @Override
    public CHConfig getCHConfig() {
        return chConfig;
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
    public int shortcut(int a, int b, int accessFlags, double weight, int skippedEdge1, int skippedEdge2) {
        if (!baseGraph.isFrozen())
            throw new IllegalStateException("Cannot create shortcut if graph is not yet frozen");
        checkNodeId(a);
        checkNodeId(b);
        // shortcuts must be inserted ordered by increasing level of node a
        if (getLevel(a) >= baseGraph.getNodes() || getLevel(a) < 0)
            throw new IllegalArgumentException("Invalid level for node " + a + ": " + getLevel(a) + ". Node a must" +
                    " be assigned a valid level before we add shortcuts a->b or a<-b");
        if (a != b && getLevel(a) == getLevel(b))
            throw new IllegalArgumentException("Different nodes must not have the same level, got levels " + getLevel(a)
                    + " and " + getLevel(b) + " for nodes " + a + " and " + b);
        if (a != b && getLevel(a) > getLevel(b))
            throw new IllegalArgumentException("The level of nodeA must be smaller than the level of nodeB, but got: " +
                    getLevel(a) + " and " + getLevel(b) + ". When inserting shortcut: " + a + "-" + b);
        if (shortcutCount > 0) {
            int prevNodeA = getNodeA(toPointer(shortcutCount + baseGraph.edgeCount - 1));
            int prevLevelA = getLevel(prevNodeA);
            if (getLevel(a) < prevLevelA) {
                throw new IllegalArgumentException("Invalid level for node " + a + ": " + getLevel(a) + ". The level " +
                        "must be equal to or larger than the lower level node of the previous shortcut (node: " + prevNodeA +
                        ", level: " + prevLevelA + ")");
            }
        }
        // we do not register the edge at node b which should be the higher level node (so no need to 'see' the lower
        // level node a)
        int shortcutId = nextShortcutId();
        writeShortcut(shortcutId, a, b);
        // we keep track of the last shortcut for each node (-1 if there are no shortcuts)
        setEdgeRef(a, shortcutId);
        long edgePointer = toPointer(shortcutId);
        setAccessAndWeight(edgePointer, accessFlags & scDirMask, weight);
        setSkippedEdges(edgePointer, skippedEdge1, skippedEdge2);
        return shortcutId;
    }

    void setShortcutFlags(long edgePointer, int flags) {
        shortcuts.setInt(edgePointer + S_WEIGHT, flags);
    }

    int getShortcutFlags(long edgePointer) {
        return shortcuts.getInt(edgePointer + S_WEIGHT);
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
            NodeAccess nodeAccess = baseGraph.getNodeAccess();
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
        if (!chConfig.isEdgeBased()) {
            throw new IllegalStateException("Edge-based shortcuts should only be added when CHGraph is edge-based");
        }
        shortcuts.setInt(edgePointer + S_ORIG_FIRST, origFirst);
        shortcuts.setInt(edgePointer + S_ORIG_LAST, origLast);
    }

    private long toPointer(int shortcutId) {
        assert isInBounds(shortcutId) : "shortcutId " + shortcutId + " not in bounds [" + baseGraph.edgeCount + ", " + (baseGraph.edgeCount + shortcutCount) + ")";
        return (long) (shortcutId - baseGraph.edgeCount) * shortcutEntryBytes;
    }

    private boolean isInBounds(int shortcutId) {
        int tmp = shortcutId - baseGraph.edgeCount;
        return tmp < shortcutCount && tmp >= 0;
    }

    @Override
    public int shortcutEdgeBased(int a, int b, int accessFlags, double weight, int skippedEdge1, int skippedEdge2, int origFirst, int origLast) {
        if (!chConfig.isEdgeBased()) {
            throw new IllegalStateException("Edge-based shortcuts should only be added when CHGraph is edge-based");
        }
        int scId = shortcut(a, b, accessFlags, weight, skippedEdge1, skippedEdge2);
        setFirstAndLastOrigEdges(toPointer(scId), origFirst, origLast);
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
    public CHEdgeExplorer createEdgeExplorer() {
        return createEdgeExplorer(EdgeFilter.ALL_EDGES);
    }

    @Override
    public CHEdgeExplorer createEdgeExplorer(EdgeFilter filter) {
        return new CHEdgeIteratorImpl(baseGraph, filter);
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
            if (!isInBounds(edgeId))
                throw new IllegalStateException("shortcutId " + edgeId + " out of bounds");
        } else if (!baseGraph.isInBounds(edgeId))
            throw new IllegalStateException("edgeId " + edgeId + " out of bounds");
        CHEdgeIteratorStateImpl edge = new CHEdgeIteratorStateImpl(new BaseGraph.EdgeIteratorStateImpl(baseGraph));
        if (edge.init(edgeId, endNode))
            return edge;
        // if edgeId exists but adjacent nodes do not match
        return null;
    }

    @Override
    public int getNodes() {
        return baseGraph.getNodes();
    }

    @Override
    public int getEdges() {
        return baseGraph.getEdges() + shortcutCount;
    }

    @Override
    public int getOriginalEdges() {
        return baseGraph.getEdges();
    }

    @Override
    public boolean isReadyForContraction() {
        return isReadyForContraction;
    }

    @Override
    public int getOtherNode(int edge, int node) {
        if (isShortcut(edge)) {
            long edgePointer = toPointer(edge);
            int nodeA = getNodeA(edgePointer);
            return node == nodeA ? getNodeB(edgePointer) : nodeA;
        } else {
            return baseGraph.getOtherNode(edge, node);
        }
    }

    @Override
    public boolean isAdjacentToNode(int edge, int node) {
        if (isShortcut(edge)) {
            long edgePointer = toPointer(edge);
            return getNodeA(edgePointer) == node || getNodeB(edgePointer) == node;
        } else {
            return baseGraph.isAdjacentToNode(edge, node);
        }
    }

    void _prepareForContraction() {
        if (isReadyForContraction)
            return;
        long maxCapacity = ((long) getNodes()) * nodeCHEntryBytes;
        nodesCH.ensureCapacity(maxCapacity);
        // copy normal edge refs into ch edge refs
        for (int node = 0; node < getNodes(); node++)
            setEdgeRef(node, baseGraph.getEdgeRef(node));
        isReadyForContraction = true;
    }

    /**
     * Writes plain edge information to the edges index
     */
    private long writeShortcut(int edgeId, int nodeA, int nodeB) {
        if (!EdgeIterator.Edge.isValid(edgeId))
            throw new IllegalStateException("Cannot write edge with illegal ID:" + edgeId + "; nodeA:" + nodeA + ", nodeB:" + nodeB);

        long edgePointer = toPointer(edgeId);
        shortcuts.setInt(edgePointer + E_NODEA, nodeA);
        shortcuts.setInt(edgePointer + E_NODEB, nodeB);
        return edgePointer;
    }

    String toDetailsString() {
        return toString() +
                ", shortcuts:" + nf(shortcutCount) + " (" + nf(shortcuts.getCapacity() / Helper.MB) + "MB)" +
                ", nodesCH:" + nf(getNodes()) + " (" + nf(nodesCH.getCapacity() / Helper.MB) + "MB)";
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

    int setEdgesHeader() {
        shortcuts.setHeader(0 * 4, shortcutCount);
        shortcuts.setHeader(1 * 4, shortcutEntryBytes);
        return 3;
    }

    @Override
    public Graph getBaseGraph() {
        return baseGraph;
    }

    void initStorage() {
        // shortcuts
        E_NODEA = 0;
        E_NODEB = E_NODEA + 4;
        S_WEIGHT = E_NODEB + 4;
        S_SKIP_EDGE1 = S_WEIGHT + +4;
        S_SKIP_EDGE2 = S_SKIP_EDGE1 + 4;
        if (chConfig.isEdgeBased()) {
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
        setNodesHeader();
        setEdgesHeader();
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
        return "CHGraph|" + chConfig.getName() + "|" + chConfig.getTraversalMode();
    }

    public void debugPrint() {
        final int printMax = 100;
        System.out.println("nodesCH:");
        String formatNodes = "%12s | %12s | %12s \n";
        System.out.format(Locale.ROOT, formatNodes, "#", "N_CH_REF", "N_LEVEL");
        for (int i = 0; i < Math.min(baseGraph.getNodes(), printMax); ++i) {
            System.out.format(Locale.ROOT, formatNodes, i, getEdgeRef(i), getLevel(i));
        }
        if (baseGraph.getNodes() > printMax) {
            System.out.format(Locale.ROOT, " ... %d more nodes", baseGraph.getNodes() - printMax);
        }
        System.out.println("shortcuts:");
        String formatShortcutsBase = "%12s | %12s | %12s | %12s | %12s | %12s";
        String formatShortcutExt = " | %12s | %12s";
        String header = String.format(Locale.ROOT, formatShortcutsBase, "#", "E_NODEA", "E_NODEB", "S_WEIGHT", "S_SKIP_EDGE1", "S_SKIP_EDGE2");
        if (chConfig.isEdgeBased()) {
            header += String.format(Locale.ROOT, formatShortcutExt, "S_ORIG_FIRST", "S_ORIG_LAST");
        }
        System.out.println(header);
        for (int i = baseGraph.edgeCount; i < baseGraph.edgeCount + Math.min(shortcutCount, printMax); ++i) {
            long edgePointer = toPointer(i);
            String edgeString = String.format(Locale.ROOT, formatShortcutsBase,
                    i,
                    getNodeA(edgePointer),
                    getNodeB(edgePointer),
                    getShortcutFlags(edgePointer),
                    shortcuts.getInt(edgePointer + S_SKIP_EDGE1),
                    shortcuts.getInt(edgePointer + S_SKIP_EDGE2));
            if (chConfig.isEdgeBased()) {
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

    private int getNodeA(long edgePointer) {
        return shortcuts.getInt(edgePointer + E_NODEA);
    }

    private int getNodeB(long edgePointer) {
        return shortcuts.getInt(edgePointer + E_NODEB);
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
        return NodeOrderingProvider.fromArray(nodeOrdering);
    }

    class CHEdgeIteratorImpl extends CHEdgeIteratorStateImpl implements CHEdgeExplorer, CHEdgeIterator {
        private final EdgeIteratorImpl baseIterator;
        private int nextEdgeId;

        public CHEdgeIteratorImpl(BaseGraph baseGraph, EdgeFilter filter) {
            super(new EdgeIteratorImpl(baseGraph, filter));
            this.baseIterator = (EdgeIteratorImpl) super.edgeIterable;
        }

        public final CHEdgeIterator setBaseNode(int baseNode) {
            assert baseIterator.baseGraph.isFrozen() : "Traversing CHGraph is only possible if BaseGraph is frozen";

            baseIterator.nextEdgeId = baseIterator.edgeId = baseGraph.getEdgeRef(baseNode);
            baseIterator.baseNode = baseNode;

            nextEdgeId = edgeId = CHGraphImpl.this.getEdgeRef(baseNode);
            return this;
        }

        @Override
        public boolean next() {
            // todo: note that it would be more efficient to separate in/out edges, especially for edge-based where we
            //       do not use bidirectional shortcuts
            while (true) {
                if (!EdgeIterator.Edge.isValid(nextEdgeId) || nextEdgeId < baseGraph.edgeCount)
                    break;
                edgeId = nextEdgeId;
                edgePointer = toPointer(edgeId);
                baseNode = getNodeA(edgePointer);
                adjNode = getNodeB(edgePointer);
                nextEdgeId = edgeId - 1;
                if (nextEdgeId < baseGraph.edgeCount || getNodeA(toPointer(nextEdgeId)) != baseNode)
                    nextEdgeId = edgeIterable.edgeId;
                reverse = false;
                freshFlags = false;
                if (baseIterator.filter.accept(this))
                    return true;
            }

            while (true) {
                if (!EdgeIterator.Edge.isValid(baseIterator.nextEdgeId))
                    return false;
                baseIterator.goToNext();
                // we update edgeId even when iterating base edges
                edgeId = baseIterator.edgeId;
                if (baseIterator.filter.accept(this))
                    return true;
            }
        }

        @Override
        public String toString() {
            return getEdge() + " " + getBaseNode() + "-" + getAdjNode();
        }

    }

    class AllCHEdgesIteratorImpl extends CHEdgeIteratorStateImpl implements AllCHEdgesIterator {
        private final AllEdgeIterator allEdgeIterator;

        public AllCHEdgesIteratorImpl(BaseGraph baseGraph) {
            super(new AllEdgeIterator(baseGraph));
            this.allEdgeIterator = (AllEdgeIterator) super.edgeIterable;
        }

        @Override
        public boolean next() {
            edgeId++;
            if (edgeId < baseGraph.edgeCount) {
                allEdgeIterator.next();
                return true;
            } else if (edgeId < baseGraph.edgeCount + shortcutCount) {
                edgePointer = toPointer(edgeId);
                baseNode = getNodeA(edgePointer);
                adjNode = getNodeB(edgePointer);
                freshFlags = false;
                reverse = false;
                return true;
            } else {
                return false;
            }
        }

        @Override
        public EdgeIteratorState detach(boolean reverseArg) {
            return allEdgeIterator.detach(reverseArg);
        }

        @Override
        public int getEdge() {
            return edgeId;
        }

        @Override
        public int length() {
            return baseGraph.edgeCount + shortcutCount;
        }

        @Override
        public final boolean isShortcut() {
            return edgeId >= baseGraph.edgeCount;
        }
    }

    private int getEdgeRef(int nodeId) {
        return nodesCH.getInt((long) nodeId * nodeCHEntryBytes + N_CH_REF);
    }

    private void setEdgeRef(int nodeId, int edgeId) {
        nodesCH.setInt((long) nodeId * nodeCHEntryBytes + N_CH_REF, edgeId);
    }

    private class CHEdgeIteratorStateImpl implements CHEdgeIteratorState {
        final BaseGraph.EdgeIteratorStateImpl edgeIterable;
        long edgePointer = -1;
        int baseNode;
        int adjNode;
        boolean reverse = false;
        boolean freshFlags;
        int edgeId = -1;
        private int chFlags;

        private CHEdgeIteratorStateImpl(BaseGraph.EdgeIteratorStateImpl edgeIterable) {
            this.edgeIterable = edgeIterable;
        }

        boolean init(int edgeId, int expectedAdjNode) {
            if (edgeId < baseGraph.edgeCount) {
                boolean b = edgeIterable.init(edgeId, expectedAdjNode);
                this.edgeId = edgeIterable.edgeId;
                return b;
            } else {
                if (!EdgeIterator.Edge.isValid(edgeId))
                    throw new IllegalArgumentException("fetching the edge requires a valid edgeId but was " + edgeId);
                this.edgeId = edgeId;
                edgePointer = toPointer(edgeId);
                baseNode = getNodeA(edgePointer);
                adjNode = getNodeB(edgePointer);
                freshFlags = false;

                if (expectedAdjNode == adjNode || expectedAdjNode == Integer.MIN_VALUE) {
                    reverse = false;
                    return true;
                } else if (expectedAdjNode == baseNode) {
                    reverse = true;
                    baseNode = adjNode;
                    adjNode = expectedAdjNode;
                    return true;
                }
                return false;
            }
        }

        @Override
        public int getBaseNode() {
            return edgeId < baseGraph.edgeCount ? edgeIterable.getBaseNode() : baseNode;
        }

        @Override
        public int getAdjNode() {
            return edgeId < baseGraph.edgeCount ? edgeIterable.getAdjNode() : adjNode;
        }

        @Override
        public int getEdge() {
            return edgeId < baseGraph.edgeCount ? edgeIterable.getEdge() : edgeId;
        }

        @Override
        public int getEdgeKey() {
            checkShortcut(false, "getEdgeKey");
            return edgeIterable.getEdgeKey();
        }

        @Override
        public EdgeIteratorState setFlags(IntsRef edgeFlags) {
            checkShortcut(false, "getFlags");
            return edgeIterable.setFlags(edgeFlags);
        }

        @Override
        public final IntsRef getFlags() {
            checkShortcut(false, "getFlags");
            return edgeIterable.getFlags();
        }

        @Override
        public EdgeIteratorState copyPropertiesFrom(EdgeIteratorState e) {
            checkShortcut(false, "copyPropertiesFrom");
            return edgeIterable.copyPropertiesFrom(e);
        }

        @Override
        public double getDistance() {
            checkShortcut(false, "getDistance");
            return edgeIterable.getDistance();
        }

        @Override
        public EdgeIteratorState setDistance(double dist) {
            checkShortcut(false, "setDistance");
            return edgeIterable.setDistance(dist);
        }

        @Override
        public final CHEdgeIteratorState setSkippedEdges(int edge1, int edge2) {
            checkShortcut(true, "setSkippedEdges");
            CHGraphImpl.this.setSkippedEdges(edgePointer, edge1, edge2);
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
        public int getOrigEdgeFirst() {
            if (!isShortcut() || !chConfig.isEdgeBased()) {
                return getEdge();
            }
            return shortcuts.getInt(edgePointer + S_ORIG_FIRST);
        }

        @Override
        public int getOrigEdgeLast() {
            if (!isShortcut() || !chConfig.isEdgeBased()) {
                return getEdge();
            }
            return shortcuts.getInt(edgePointer + S_ORIG_LAST);
        }

        @Override
        public boolean isShortcut() {
            return edgeId >= baseGraph.edgeCount;
        }

        @Override
        public boolean getFwdAccess() {
            checkShortcut(true, "getFwdAccess");
            return (getShortcutFlags() & (reverse ? PrepareEncoder.getScBwdDir() : PrepareEncoder.getScFwdDir())) != 0;
        }

        @Override
        public boolean getBwdAccess() {
            checkShortcut(true, "getBwdAccess");
            return (getShortcutFlags() & (reverse ? PrepareEncoder.getScFwdDir() : PrepareEncoder.getScBwdDir())) != 0;
        }

        @Override
        public boolean get(BooleanEncodedValue property) {
            // TODO assert equality of "access boolean encoded value" that is specifically created for CHGraph to make it possible we can use other BooleanEncodedValue objects for CH too!
            if (isShortcut())
                return getFwdAccess();

            return property.getBool(edgeIterable.reverse, getFlags());
        }

        @Override
        public boolean getReverse(BooleanEncodedValue property) {
            if (isShortcut())
                return getBwdAccess();

            return property.getBool(!edgeIterable.reverse, getFlags());
        }

        @Override
        public final CHEdgeIteratorState setWeight(double weight) {
            checkShortcut(true, "setWeight");
            CHGraphImpl.this.setShortcutWeight(edgePointer, weight);
            return this;
        }

        @Override
        public void setFlagsAndWeight(int flags, double weight) {
            checkShortcut(true, "setFlagsAndWeight");
            CHGraphImpl.this.setAccessAndWeight(edgePointer, flags, weight);
            chFlags = flags;
            freshFlags = true;
        }

        @Override
        public final double getWeight() {
            checkShortcut(true, "getWeight");
            return CHGraphImpl.this.getShortcutWeight(edgePointer);
        }

        void checkShortcut(boolean shouldBeShortcut, String methodName) {
            if (isShortcut()) {
                if (!shouldBeShortcut)
                    throw new IllegalStateException("Cannot call " + methodName + " on shortcut " + getEdge());
            } else if (shouldBeShortcut)
                throw new IllegalStateException("Method " + methodName + " only for shortcuts " + getEdge());
        }

        @Override
        public final String getName() {
            checkShortcut(false, "getName");
            return edgeIterable.getName();
        }

        @Override
        public final EdgeIteratorState setName(String name) {
            checkShortcut(false, "setName");
            return edgeIterable.setName(name);
        }

        @Override
        public final PointList fetchWayGeometry(FetchMode mode) {
            checkShortcut(false, "fetchWayGeometry");
            return edgeIterable.fetchWayGeometry(mode);
        }

        @Override
        public final EdgeIteratorState setWayGeometry(PointList list) {
            checkShortcut(false, "setWayGeometry");
            return edgeIterable.setWayGeometry(list);
        }

        @Override
        public EdgeIteratorState set(BooleanEncodedValue property, boolean value) {
            checkShortcut(false, "set(BooleanEncodedValue, boolean)");
            return edgeIterable.set(property, value);
        }

        @Override
        public EdgeIteratorState setReverse(BooleanEncodedValue property, boolean value) {
            checkShortcut(false, "setReverse(BooleanEncodedValue, boolean)");
            return edgeIterable.setReverse(property, value);
        }

        @Override
        public EdgeIteratorState set(BooleanEncodedValue property, boolean fwd, boolean bwd) {
            checkShortcut(false, "set(BooleanEncodedValue, boolean, boolean)");
            return edgeIterable.set(property, fwd, bwd);
        }

        @Override
        public int get(IntEncodedValue property) {
            checkShortcut(false, "get(IntEncodedValue)");
            return edgeIterable.get(property);
        }

        @Override
        public EdgeIteratorState set(IntEncodedValue property, int value) {
            checkShortcut(false, "set(IntEncodedValue, int)");
            return edgeIterable.set(property, value);
        }

        @Override
        public int getReverse(IntEncodedValue property) {
            checkShortcut(false, "getReverse(IntEncodedValue)");
            return edgeIterable.getReverse(property);
        }

        @Override
        public EdgeIteratorState setReverse(IntEncodedValue property, int value) {
            checkShortcut(false, "setReverse(IntEncodedValue, int)");
            return edgeIterable.setReverse(property, value);
        }

        @Override
        public EdgeIteratorState set(IntEncodedValue property, int fwd, int bwd) {
            checkShortcut(false, "set(IntEncodedValue, int, int)");
            return edgeIterable.set(property, fwd, bwd);
        }

        @Override
        public double get(DecimalEncodedValue property) {
            checkShortcut(false, "get(DecimalEncodedValue)");
            return edgeIterable.get(property);
        }

        @Override
        public EdgeIteratorState set(DecimalEncodedValue property, double value) {
            checkShortcut(false, "set(DecimalEncodedValue, double)");
            return edgeIterable.set(property, value);
        }

        @Override
        public double getReverse(DecimalEncodedValue property) {
            checkShortcut(false, "getReverse(DecimalEncodedValue)");
            return edgeIterable.getReverse(property);
        }

        @Override
        public EdgeIteratorState setReverse(DecimalEncodedValue property, double value) {
            checkShortcut(false, "setReverse(DecimalEncodedValue, double)");
            return edgeIterable.setReverse(property, value);
        }

        @Override
        public EdgeIteratorState set(DecimalEncodedValue property, double fwd, double bwd) {
            checkShortcut(false, "set(DecimalEncodedValue, double, double)");
            return edgeIterable.set(property, fwd, bwd);
        }

        @Override
        public <T extends Enum<?>> T get(EnumEncodedValue<T> property) {
            checkShortcut(false, "get(EnumEncodedValue<T>)");
            return edgeIterable.get(property);
        }

        @Override
        public <T extends Enum<?>> EdgeIteratorState set(EnumEncodedValue<T> property, T value) {
            checkShortcut(false, "set(EnumEncodedValue<T>, T)");
            return edgeIterable.set(property, value);
        }

        @Override
        public <T extends Enum<?>> T getReverse(EnumEncodedValue<T> property) {
            checkShortcut(false, "getReverse(EnumEncodedValue<T>)");
            return edgeIterable.getReverse(property);
        }

        @Override
        public <T extends Enum<?>> EdgeIteratorState setReverse(EnumEncodedValue<T> property, T value) {
            checkShortcut(false, "setReverse(EnumEncodedValue<T>, T)");
            return edgeIterable.setReverse(property, value);
        }

        @Override
        public <T extends Enum<?>> EdgeIteratorState set(EnumEncodedValue<T> property, T fwd, T bwd) {
            checkShortcut(false, "set(EnumEncodedValue<T>, T, T)");
            return edgeIterable.set(property, fwd, bwd);
        }
        
        @Override
        public String get(StringEncodedValue property) {
            checkShortcut(false, "get(StringEncodedValue)");
            return edgeIterable.get(property);
        }
        
        @Override
        public EdgeIteratorState set(StringEncodedValue property, String value) {
            checkShortcut(false, "set(StringEncodedValue, String)");
            return edgeIterable.set(property, value);
        }
        
        @Override
        public String getReverse(StringEncodedValue property) {
            checkShortcut(false, "getReverse(StringEncodedValue)");
            return edgeIterable.getReverse(property);
        }
        
        @Override
        public EdgeIteratorState setReverse(StringEncodedValue property, String value) {
            checkShortcut(false, "setReverse(StringEncodedValue, String)");
            return edgeIterable.setReverse(property, value);
        }
        
        @Override
        public EdgeIteratorState set(StringEncodedValue property, String fwd, String bwd) {
            checkShortcut(false, "set(StringEncodedValue, String, String)");
            return edgeIterable.set(property, fwd, bwd);
        }

        @Override
        public EdgeIteratorState detach(boolean reverseArg) {
            checkShortcut(false, "detach(boolean)");
            return edgeIterable.detach(reverseArg);
        }

        int getShortcutFlags() {
            if (!freshFlags) {
                chFlags = CHGraphImpl.this.getShortcutFlags(edgePointer);
                freshFlags = true;
            }
            return chFlags;
        }
    }
}
