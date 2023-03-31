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
import com.graphhopper.util.Helper;

import java.util.Locale;
import java.util.function.Consumer;

import static com.graphhopper.util.Helper.nf;

/**
 * DataAccess-based storage for CH shortcuts. Stores shortcuts and CH levels sequentially using two DataAccess objects
 * and gives read/write access to the different shortcut and node fields.
 * <p>
 * This can be seen as an extension to a base graph: We assign a CH level to each nodes and add additional edges to
 * the graph ('shortcuts'). The shortcuts need to be ordered in a certain way, but this is not enforced here.
 *
 * @see CHStorageBuilder to build a valid storage that can be used for routing
 */
public class CHStorage {
    // we store double weights as integers (rounded to three decimal digits)
    private static final double WEIGHT_FACTOR = 1000;
    // the maximum integer value we can store
    private static final long MAX_STORED_INTEGER_WEIGHT = ((long) Integer.MAX_VALUE) << 1;
    // the maximum double weight we can store. if this is exceeded the shortcut will gain infinite weight, potentially yielding connection-not-found errors
    private static final double MAX_WEIGHT = MAX_STORED_INTEGER_WEIGHT / WEIGHT_FACTOR;
    private static final double MIN_WEIGHT = 1 / WEIGHT_FACTOR;

    // shortcuts
    private final DataAccess shortcuts;
    private final int S_NODEA, S_NODEB, S_WEIGHT, S_SKIP_EDGE1, S_SKIP_EDGE2, S_ORIG_FIRST, S_ORIG_LAST;
    private int shortcutEntryBytes;
    private int shortcutCount = 0;

    // nodes
    private final DataAccess nodesCH;
    private final int N_LEVEL, N_LAST_SC;
    private int nodeCHEntryBytes;
    private int nodeCount = -1;

    private boolean edgeBased;
    // some shortcuts exceed the maximum storable weight and we count them here
    private int numShortcutsExceedingWeight;

    // use this to report shortcuts with too small weights
    private Consumer<LowWeightShortcut> lowShortcutWeightConsumer;

// ORS-GH MOD START add member variable and constructor
    private boolean isTypeCore;
    private int coreNodeCount = -1;
    private int S_TIME;

    public CHStorage(Directory dir, String name, int segmentSize, boolean edgeBased) {
        this(dir, name, segmentSize, edgeBased, "ch");
    }

    public CHStorage(Directory dir, String name, int segmentSize, boolean edgeBased, String type) {
        this.isTypeCore =  CHConfig.TYPE_CORE.equals(type);
        this.nodesCH = dir.find("nodes_" + type + "_" + name, DAType.getPreferredInt(dir.getDefaultType()));
        this.shortcuts = dir.find("shortcuts_" + type + "_" + name, DAType.getPreferredInt(dir.getDefaultType()));
// ORS-GH MOD END
        this.edgeBased = edgeBased;
        if (segmentSize >= 0) {
            nodesCH.setSegmentSize(segmentSize);
            shortcuts.setSegmentSize(segmentSize);
        }
        // shortcuts are stored consecutively using this layout (the last two entries only exist for edge-based):
        // NODEA | NODEB | WEIGHT | SKIP_EDGE1 | SKIP_EDGE2 | S_ORIG_FIRST | S_ORIG_LAST
        S_NODEA = 0;
        S_NODEB = S_NODEA + 4;
        S_WEIGHT = S_NODEB + 4;
        S_SKIP_EDGE1 = S_WEIGHT + 4;
        S_SKIP_EDGE2 = S_SKIP_EDGE1 + 4;
        S_ORIG_FIRST = S_SKIP_EDGE2 + (edgeBased ? 4 : 0);
        S_ORIG_LAST = S_ORIG_FIRST + (edgeBased ? 4 : 0);
        shortcutEntryBytes = S_ORIG_LAST + 4;
// ORS-GH MOD START: TD CALT
        if (isTypeCore) {
            S_TIME = shortcutEntryBytes;
            shortcutEntryBytes = S_TIME + 4;
        }
// ORS-GH MOD END

        // nodes/levels are stored consecutively using this layout:
        // LEVEL | N_LAST_SC
        N_LEVEL = 0;
        N_LAST_SC = N_LEVEL + 4;
        nodeCHEntryBytes = N_LAST_SC + 4;
    }

    /**
     * Sets a callback called for shortcuts that are below the minimum weight. e.g. used to find/log mapping errors
     */
    public void setLowShortcutWeightConsumer(Consumer<LowWeightShortcut> lowWeightShortcutConsumer) {
        this.lowShortcutWeightConsumer = lowWeightShortcutConsumer;
    }

    /**
     * Creates a new storage. Alternatively we could load an existing one using {@link #loadExisting()}}.
     */
    public void create() {
        // We have to create the DataAccess here before we flush it. Otherwise we get an error when calling
        // loadExisting() later, see #2384
        nodesCH.create(0);
        shortcuts.create(0);
    }

    /**
     * Initializes the storage. The number of nodes must be given here while the expected number of shortcuts can
     * be given to prevent some memory allocations, but is not a requirement. When in doubt rather use a small value
     * so the resulting files/byte arrays won't be unnecessarily large.
     * todo: we could also trim down the shortcuts DataAccess when we are done adding shortcuts
     */
    public void init(int nodes, int expectedShortcuts) {
        if (nodeCount >= 0)
            throw new IllegalStateException("CHStorage can only be initialized once");
        if (nodes < 0)
            throw new IllegalStateException("CHStorage must be initialized with a positive number of nodes");
        nodesCH.ensureCapacity((long) nodes * nodeCHEntryBytes);
        nodeCount = nodes;
        shortcuts.ensureCapacity((long) expectedShortcuts * shortcutEntryBytes);
        for (int node = 0; node < nodes; node++)
            setLastShortcut(toNodePointer(node), -1);
    }

    public void flush() {
        // nodes
        nodesCH.setHeader(0, nodeCount);
        nodesCH.setHeader(4, nodeCHEntryBytes);
// ORS-GH MOD START added header field
        nodesCH.setHeader(8, coreNodeCount);
// ORS-GH MOD END
        nodesCH.flush();

        // shortcuts
        shortcuts.setHeader(0, shortcutCount);
        shortcuts.setHeader(4, shortcutEntryBytes);
        shortcuts.setHeader(8, numShortcutsExceedingWeight);
        shortcuts.setHeader(12, edgeBased ? 1 : 0);
        shortcuts.flush();
    }

    public boolean loadExisting() {
        if (!nodesCH.loadExisting() || !shortcuts.loadExisting())
            return false;

        // nodes
        nodeCount = nodesCH.getHeader(0);
        nodeCHEntryBytes = nodesCH.getHeader(4);
// ORS-GH MOD START added header field
        coreNodeCount = nodesCH.getHeader(8);
// ORS-GH MOD END

        // shortcuts
        shortcutCount = shortcuts.getHeader(0);
        shortcutEntryBytes = shortcuts.getHeader(4);
        numShortcutsExceedingWeight = shortcuts.getHeader(8);
        edgeBased = shortcuts.getHeader(12) == 1;

        return true;
    }

    public void close() {
        nodesCH.close();
        shortcuts.close();
    }

    /**
     * Adds a shortcut to the storage. Shortcuts are stored in the same order they are added. The underlying DataAccess
     * object grows automatically when adding more shortcuts.
     */
    public int shortcutNodeBased(int nodeA, int nodeB, int accessFlags, double weight, int skip1, int skip2) {
        if (edgeBased)
            throw new IllegalArgumentException("Cannot add node-based shortcuts to edge-based CH");
        return shortcut(nodeA, nodeB, accessFlags, weight, skip1, skip2);
    }

    public int shortcutEdgeBased(int nodeA, int nodeB, int accessFlags, double weight, int skip1, int skip2, int origFirst, int origLast) {
        if (!edgeBased)
            throw new IllegalArgumentException("Cannot add edge-based shortcuts to node-based CH");
        int shortcut = shortcut(nodeA, nodeB, accessFlags, weight, skip1, skip2);
        setOrigEdges(toShortcutPointer(shortcut), origFirst, origLast);
        return shortcut;
    }

// ORS-GH MOD START add method
    public int shortcutCore(int nodeA, int nodeB, int accessFlags, double weight, int skip1, int skip2, int time) {
        if (!isTypeCore) {
            throw new IllegalStateException("Cannot add time to shortcuts of a non-core graph");
        }
        int shortcut = shortcut(nodeA, nodeB, accessFlags, weight, skip1, skip2);
        shortcuts.setInt(toShortcutPointer(shortcut) + S_TIME, time);
        return shortcut;
    }
// ORS-GH MOD END

    private int shortcut(int nodeA, int nodeB, int accessFlags, double weight, int skip1, int skip2) {
        if (shortcutCount == Integer.MAX_VALUE)
            throw new IllegalStateException("Maximum shortcut count exceeded: " + shortcutCount);
        if (lowShortcutWeightConsumer != null && weight < MIN_WEIGHT)
            lowShortcutWeightConsumer.accept(new LowWeightShortcut(nodeA, nodeB, shortcutCount, weight, MIN_WEIGHT));
        long shortcutPointer = (long) shortcutCount * shortcutEntryBytes;
        shortcutCount++;
        shortcuts.ensureCapacity((long) shortcutCount * shortcutEntryBytes);
        int weightInt = weightFromDouble(weight);
        setNodesAB(shortcutPointer, nodeA, nodeB, accessFlags);
        setWeightInt(shortcutPointer, weightInt);
        setSkippedEdges(shortcutPointer, skip1, skip2);
        return shortcutCount - 1;
    }

    /**
     * The number of nodes of this storage.
     */
    public int getNodes() {
        return nodeCount;
    }

    /**
     * The number of shortcuts that were added to this storage
     */
    public int getShortcuts() {
        return shortcutCount;
    }

    /**
     * To use the node getters/setters you need to convert node IDs to a nodePointer first
     */
    public long toNodePointer(int node) {
        assert node >= 0 && node < nodeCount : "node not in bounds: [0, " + nodeCount + "[";
        return (long) node * nodeCHEntryBytes;
    }

    /**
     * To use the shortcut getters/setters you need to convert shortcut IDs to an shortcutPointer first
     */
    public long toShortcutPointer(int shortcut) {
        assert shortcut < shortcutCount : "shortcut " + shortcut + " not in bounds [0, " + shortcutCount + "[";
        return (long) shortcut * shortcutEntryBytes;
    }

    public boolean isEdgeBased() {
        return edgeBased;
    }

    public int getLastShortcut(long nodePointer) {
        return nodesCH.getInt(nodePointer + N_LAST_SC);
    }

    public void setLastShortcut(long nodePointer, int shortcut) {
        nodesCH.setInt(nodePointer + N_LAST_SC, shortcut);
    }

    public int getLevel(long nodePointer) {
        return nodesCH.getInt(nodePointer + N_LEVEL);
    }

    public void setLevel(long nodePointer, int level) {
        nodesCH.setInt(nodePointer + N_LEVEL, level);
    }

    private void setNodesAB(long shortcutPointer, int nodeA, int nodeB, int accessFlags) {
        shortcuts.setInt(shortcutPointer + S_NODEA, nodeA << 1 | accessFlags & PrepareEncoder.getScFwdDir());
        shortcuts.setInt(shortcutPointer + S_NODEB, nodeB << 1 | (accessFlags & PrepareEncoder.getScBwdDir()) >> 1);
    }

    public void setWeight(long shortcutPointer, double weight) {
        setWeightInt(shortcutPointer, weightFromDouble(weight));
    }

    private void setWeightInt(long shortcutPointer, int weightInt) {
        shortcuts.setInt(shortcutPointer + S_WEIGHT, weightInt);
    }

    public void setSkippedEdges(long shortcutPointer, int edge1, int edge2) {
        shortcuts.setInt(shortcutPointer + S_SKIP_EDGE1, edge1);
        shortcuts.setInt(shortcutPointer + S_SKIP_EDGE2, edge2);
    }

    public void setOrigEdges(long shortcutPointer, int origFirst, int origLast) {
        if (!edgeBased)
            throw new IllegalArgumentException("Setting orig edges is only possible for edge-based CH");
        shortcuts.setInt(shortcutPointer + S_ORIG_FIRST, origFirst);
        shortcuts.setInt(shortcutPointer + S_ORIG_LAST, origLast);
    }

    public int getNodeA(long shortcutPointer) {
        return shortcuts.getInt(shortcutPointer + S_NODEA) >>> 1;
    }

    public int getNodeB(long shortcutPointer) {
        return shortcuts.getInt(shortcutPointer + S_NODEB) >>> 1;
    }

    public boolean getFwdAccess(long shortcutPointer) {
        return (shortcuts.getInt(shortcutPointer + S_NODEA) & 0x1) != 0;
    }

    public boolean getBwdAccess(long shortcutPointer) {
        return (shortcuts.getInt(shortcutPointer + S_NODEB) & 0x1) != 0;
    }

    public double getWeight(long shortcutPointer) {
        return weightToDouble(shortcuts.getInt(shortcutPointer + S_WEIGHT));
    }

    public int getSkippedEdge1(long shortcutPointer) {
        return shortcuts.getInt(shortcutPointer + S_SKIP_EDGE1);
    }

    public int getSkippedEdge2(long shortcutPointer) {
        return shortcuts.getInt(shortcutPointer + S_SKIP_EDGE2);
    }

    public int getOrigEdgeFirst(long shortcutPointer) {
        assert edgeBased : "orig edges are only available for edge-based CH";
        return shortcuts.getInt(shortcutPointer + S_ORIG_FIRST);
    }

    public int getOrigEdgeLast(long shortcutPointer) {
        assert edgeBased : "orig edges are only available for edge-based CH";
        return shortcuts.getInt(shortcutPointer + S_ORIG_LAST);
    }

// ORS-GH MOD START add method
    public int getTime(long shortcutPointer) {
        //FIXME: assert isTypeCore : "time is only available for core graph";
        return shortcuts.getInt(shortcutPointer + S_TIME);
    }
// ORS-GH MOD END

    public NodeOrderingProvider getNodeOrderingProvider() {
        int numNodes = getNodes();
        final int[] nodeOrdering = new int[numNodes];
        // the node ordering is the inverse of the ch levels
        // if we really want to save some memory it could be still reasonable to not create the node ordering here,
        // but search nodesCH for a given level on demand.
        for (int i = 0; i < numNodes; ++i) {
            int level = getLevel(toNodePointer(i));
            nodeOrdering[level] = i;
        }
        return NodeOrderingProvider.fromArray(nodeOrdering);
    }

    public void debugPrint() {
        final int printMax = 100;
        System.out.println("nodesCH:");
        String formatNodes = "%12s | %12s | %12s \n";
        System.out.format(Locale.ROOT, formatNodes, "#", "N_LAST_SC", "N_LEVEL");
        for (int i = 0; i < Math.min(nodeCount, printMax); ++i) {
            long ptr = toNodePointer(i);
            System.out.format(Locale.ROOT, formatNodes, i, getLastShortcut(ptr), getLevel(ptr));
        }
        if (nodeCount > printMax) {
            System.out.format(Locale.ROOT, " ... %d more nodes", nodeCount - printMax);
        }
        System.out.println("shortcuts:");
        String formatShortcutsBase = "%12s | %12s | %12s | %12s | %12s | %12s";
        String formatShortcutExt = " | %12s | %12s";
        String header = String.format(Locale.ROOT, formatShortcutsBase, "#", "E_NODEA", "E_NODEB", "S_WEIGHT", "S_SKIP_EDGE1", "S_SKIP_EDGE2");
        if (isEdgeBased()) {
            header += String.format(Locale.ROOT, formatShortcutExt, "S_ORIG_FIRST", "S_ORIG_LAST");
        }
        System.out.println(header);
        for (int i = 0; i < Math.min(shortcutCount, printMax); ++i) {
            long ptr = toShortcutPointer(i);
            String edgeString = String.format(Locale.ROOT, formatShortcutsBase,
                    i,
                    getNodeA(ptr),
                    getNodeB(ptr),
                    getWeight(ptr),
                    getSkippedEdge1(ptr),
                    getSkippedEdge2(ptr));
            if (edgeBased) {
                edgeString += String.format(Locale.ROOT, formatShortcutExt,
                        getOrigEdgeFirst(ptr),
                        getOrigEdgeLast(ptr));
            }
            System.out.println(edgeString);
        }
        if (shortcutCount > printMax) {
            System.out.printf(Locale.ROOT, " ... %d more shortcut edges\n", shortcutCount - printMax);
        }
    }

    public long getCapacity() {
        return nodesCH.getCapacity() + shortcuts.getCapacity();
    }

    public int getNumShortcutsExceedingWeight() {
        return numShortcutsExceedingWeight;
    }

    public String toDetailsString() {
        return "shortcuts:" + nf(shortcutCount) + " (" + nf(shortcuts.getCapacity() / Helper.MB) + "MB)" +
                ", nodesCH:" + nf(nodeCount) + " (" + nf(nodesCH.getCapacity() / Helper.MB) + "MB)";
    }

    public boolean isClosed() {
        assert nodesCH.isClosed() == shortcuts.isClosed();
        return nodesCH.isClosed();
    }

    private int weightFromDouble(double weight) {
        if (weight < 0)
            throw new IllegalArgumentException("weight cannot be negative but was " + weight);
        if (weight < MIN_WEIGHT)
            weight = MIN_WEIGHT;
        if (weight >= MAX_WEIGHT) {
            numShortcutsExceedingWeight++;
            return (int) MAX_STORED_INTEGER_WEIGHT; // negative
        } else
            return (int) Math.round(weight * WEIGHT_FACTOR);
    }

    private double weightToDouble(int intWeight) {
        // If the value is too large (> Integer.MAX_VALUE) the `int` is negative. Converted to `long` the JVM fills the
        // high bits with 1's which we remove via "& 0xFFFFFFFFL" to get the unsigned value. (The L is necessary or prepend 8 zeros.)
        long weightLong = (long) intWeight & 0xFFFFFFFFL;
        if (weightLong == MAX_STORED_INTEGER_WEIGHT)
            return Double.POSITIVE_INFINITY;
        double weight = weightLong / WEIGHT_FACTOR;
        if (weight >= MAX_WEIGHT)
            throw new IllegalArgumentException("too large shortcut weight " + weight + " should get infinity marker bits "
                    + MAX_STORED_INTEGER_WEIGHT);
        return weight;
    }

// ORS-GH MOD START add methods
    public int getCoreNodes() {
        return coreNodeCount;
    }

    public void setCoreNodes(int coreNodeCount) {
        this.coreNodeCount = coreNodeCount;
    }
// ORS-GH MOD END

    public static class LowWeightShortcut {
        int nodeA;
        int nodeB;
        int shortcut;
        double weight;
        double minWeight;

        public LowWeightShortcut(int nodeA, int nodeB, int shortcut, double weight, double minWeight) {
            this.nodeA = nodeA;
            this.nodeB = nodeB;
            this.shortcut = shortcut;
            this.weight = weight;
            this.minWeight = minWeight;
        }
    }
}
