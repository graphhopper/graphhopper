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

import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.EdgeIntAccess;
import com.graphhopper.routing.ev.IntsRefEdgeIntAccess;
import com.graphhopper.util.BitUtil;
import com.graphhopper.util.Constants;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.GHUtility;

import java.util.ArrayList;

/**
 * A key/value store, where the unique keys are triples (fromEdge, viaNode, toEdge) and the values
 * are integers that can be used to store encoded values.
 *
 * @author Karl Hübner
 * @author Peter Karich
 * @author Michael Zilske
 */
public class TurnCostStorage {
    static final int W_NO_TURN_ENTRY = -1;
    // we store each turn cost entry in the format |from_edge|to_edge|flags|next|. each entry has 4 bytes -> 16 bytes total
    private static final int W_TC_FROM = 0;
    private static final int W_TC_TO = 4;
    private static final int W_TC_FLAGS = 8;
    private static final int W_TC_NEXT = 12;
    private static final int W_BYTES_PER_ENTRY = 16;

    // after freeze() store turn cost sorted by via node to avoid linked list: count|from_edge|to_edge|flags| [repeat]
    private static final int R_TC_FROM = 0;
    private static final int R_TC_TO = 4;
    private static final int R_TC_FLAGS = 8;
    private static final int R_BYTES_PER_ENTRY = 12;

    private final BaseGraph baseGraph;
    private final NodeAccess nodeAccess;

    private final DataAccess writeTmpTurnCosts;
    private int turnCostsWritePtr = 0;
    private int turnCostCount = 0;

    private final DataAccess readTurnCosts;
    private long lastReadPointer = 0;
    private int nodeCount;
    private boolean frozen = false;

    public TurnCostStorage(BaseGraph baseGraph, DataAccess readDataAccess, DataAccess writeTmpTurnCosts) {
        this.baseGraph = baseGraph;
        this.readTurnCosts = readDataAccess;
        this.writeTmpTurnCosts = writeTmpTurnCosts;
        this.nodeAccess = baseGraph.getNodeAccess();
    }

    public TurnCostStorage create(long initBytes) {
        writeTmpTurnCosts.create(initBytes);
        readTurnCosts.create(initBytes);
        return this;
    }

    public void flush() {
        if (!frozen) throw new RuntimeException("Cannot flush turn cost storage if not yet frozen");
        readTurnCosts.setHeader(0, Constants.VERSION_TURN_COSTS);
        readTurnCosts.setHeader(4, R_BYTES_PER_ENTRY);
        readTurnCosts.setHeader(8, turnCostCount);
        readTurnCosts.setHeader(12, BitUtil.LITTLE.getIntLow(lastReadPointer));
        readTurnCosts.setHeader(16, BitUtil.LITTLE.getIntHigh(lastReadPointer));
        readTurnCosts.flush();
    }

    public void close() {
        // if (!frozen) throw new RuntimeException("Cannot close turn cost storage if not yet frozen");
        writeTmpTurnCosts.close();
        readTurnCosts.close();
    }

    public long getCapacity() {
        return frozen ? readTurnCosts.getCapacity() : writeTmpTurnCosts.getCapacity();
    }

    public boolean loadExisting() {
        frozen = true;
        nodeCount = baseGraph.getNodes();
        if (!readTurnCosts.loadExisting())
            return false;

        GHUtility.checkDAVersion(readTurnCosts.getName(), Constants.VERSION_TURN_COSTS, readTurnCosts.getHeader(0));
        if (readTurnCosts.getHeader(4) != R_BYTES_PER_ENTRY)
            throw new IllegalStateException("Number of bytes per turn cost entry does not match the current configuration: " + readTurnCosts.getHeader(0) + " vs. " + R_BYTES_PER_ENTRY);
        turnCostCount = readTurnCosts.getHeader(8);
        lastReadPointer = BitUtil.LITTLE.toLong(readTurnCosts.getHeader(12), readTurnCosts.getHeader(16));

        return true;
    }

    public void set(BooleanEncodedValue bev, int fromEdge, int viaNode, int toEdge, boolean value) {
        long pointer = findOrCreateTurnCostEntry(fromEdge, viaNode, toEdge);
        if (pointer < 0)
            throw new IllegalStateException("Invalid pointer: " + pointer + " at (" + fromEdge + ", " + viaNode + ", " + toEdge + ")");
        bev.setBool(false, -1, createWriteIntAccess(pointer), value);
    }

    /**
     * Sets the turn cost at the viaNode when going from "fromEdge" to "toEdge"
     */
    public void set(DecimalEncodedValue turnCostEnc, int fromEdge, int viaNode, int toEdge, double cost) {
        long pointer = findOrCreateTurnCostEntry(fromEdge, viaNode, toEdge);
        if (pointer < 0)
            throw new IllegalStateException("Invalid pointer: " + pointer + " at (" + fromEdge + ", " + viaNode + ", " + toEdge + ")");
        turnCostEnc.setDecimal(false, -1, createWriteIntAccess(pointer), cost);
    }

    private EdgeIntAccess createWriteIntAccess(long pointer) {
        return new EdgeIntAccess() {
            @Override
            public int getInt(int edgeId, int index) {
                return writeTmpTurnCosts.getInt(pointer + W_TC_FLAGS);
            }

            @Override
            public void setInt(int edgeId, int index, int value) {
                writeTmpTurnCosts.setInt(pointer + W_TC_FLAGS, value);
            }
        };
    }

    private long findOrCreateTurnCostEntry(int fromEdge, int viaNode, int toEdge) {
        if (frozen) throw new IllegalStateException("TurnCostsStorage already frozen");
        long pointer = findWriteTmpPointer(fromEdge, viaNode, toEdge);
        if (pointer < 0) {
            // create a new entry
            turnCostCount++;
            writeTmpTurnCosts.ensureCapacity(turnCostsWritePtr + 4 + W_BYTES_PER_ENTRY);
            int prevIndex = nodeAccess.getTurnCostIndex(viaNode);
            nodeAccess.setTurnCostIndex(viaNode, turnCostsWritePtr);
            writeTmpTurnCosts.setInt(turnCostsWritePtr + W_TC_FROM, fromEdge);
            writeTmpTurnCosts.setInt(turnCostsWritePtr + W_TC_TO, toEdge);
            writeTmpTurnCosts.setInt(turnCostsWritePtr + W_TC_NEXT, prevIndex);
            pointer = turnCostsWritePtr;
            turnCostsWritePtr += W_BYTES_PER_ENTRY;
            if (turnCostsWritePtr < 0)
                throw new IllegalStateException("Too many turn cost entries");
        }
        return pointer;
    }

    public double get(DecimalEncodedValue dev, int fromEdge, int viaNode, int toEdge) {
        return dev.getDecimal(false, -1, createReadIntAccess(findReadPointer(fromEdge, viaNode, toEdge)));
    }

    public boolean get(BooleanEncodedValue bev, int fromEdge, int viaNode, int toEdge) {
        return bev.getBool(false, -1, createReadIntAccess(findReadPointer(fromEdge, viaNode, toEdge)));
    }

    private EdgeIntAccess createReadIntAccess(long pointer) {
        return new EdgeIntAccess() {
            @Override
            public int getInt(int edgeId, int index) {
                return pointer < 0 ? 0 : readTurnCosts.getInt(pointer + R_TC_FLAGS);
            }

            @Override
            public void setInt(int edgeId, int index, int value) {
                throw new IllegalArgumentException("turn cost EdgeIntAccess cannot write to readTurnCosts");
            }
        };
    }

    public int getTurnCostsCount() {
        return turnCostCount;
    }

    public void freeze() {
        record TmpEntry(int from, int to, int flags) {
        }
        int nextReadPointer = 4;
        final int maxEntries = 1000;

        for (int nodeIdx = 0; nodeIdx < baseGraph.getNodes(); nodeIdx++) {
            int pointer = nodeAccess.getTurnCostIndex(nodeIdx);

            ArrayList<TmpEntry> list = new ArrayList<>();
            for (int i = 0; i < maxEntries; ++i) {
                if (pointer == W_NO_TURN_ENTRY) break;
                list.add(new TmpEntry(writeTmpTurnCosts.getInt(pointer + W_TC_FROM),
                        writeTmpTurnCosts.getInt(pointer + W_TC_TO),
                        writeTmpTurnCosts.getInt(pointer + W_TC_FLAGS)));
                pointer = writeTmpTurnCosts.getInt(pointer + W_TC_NEXT);
            }
            if (pointer != W_NO_TURN_ENTRY)
                throw new IllegalStateException("Too many turn cost entries for node:" + nodeIdx);

            nodeAccess.setTurnCostIndex(nodeIdx, nextReadPointer);
            if (list.isEmpty()) continue;

            readTurnCosts.ensureCapacity(nextReadPointer + (long) list.size() * R_BYTES_PER_ENTRY);

            for (TmpEntry tmpEntry : list) {
                readTurnCosts.setInt(nextReadPointer + R_TC_FROM, tmpEntry.from);
                readTurnCosts.setInt(nextReadPointer + R_TC_TO, tmpEntry.to);
                readTurnCosts.setInt(nextReadPointer + R_TC_FLAGS, tmpEntry.flags);

                nextReadPointer += R_BYTES_PER_ENTRY;
            }

            if (nextReadPointer + R_BYTES_PER_ENTRY < 0)
                throw new RuntimeException("turn cost storage too large. node: " + nodeIdx + ", of:" + baseGraph.getNodes());
        }

        lastReadPointer = nextReadPointer;
        nodeCount = baseGraph.getNodes();
        writeTmpTurnCosts.close();
        // nodeAccess and readTurnCosts will be flushed later
        frozen = true;
    }

    private long findWriteTmpPointer(int fromEdge, int viaNode, int toEdge) {
        if (!EdgeIterator.Edge.isValid(fromEdge) || !EdgeIterator.Edge.isValid(toEdge))
            throw new IllegalArgumentException("from and to edge cannot be NO_EDGE");
        if (viaNode < 0)
            throw new IllegalArgumentException("via node cannot be negative");

        final int maxEntries = 1000;
        int pointer = nodeAccess.getTurnCostIndex(viaNode);
        for (int i = 0; i < maxEntries; ++i) {
            if (pointer == W_NO_TURN_ENTRY) return -1;
            if (fromEdge == writeTmpTurnCosts.getInt(pointer + W_TC_FROM) && toEdge == writeTmpTurnCosts.getInt(pointer + W_TC_TO))
                return pointer;
            pointer = writeTmpTurnCosts.getInt(pointer + W_TC_NEXT);
        }
        throw new IllegalStateException("Turn cost list for node: " + viaNode + " is longer than expected, max: " + maxEntries);
    }

    private long findReadPointer(int fromEdge, int viaNode, int toEdge) {
        if (!frozen) throw new IllegalStateException("TurnCostsStorage not yet frozen");
        if (!EdgeIterator.Edge.isValid(fromEdge) || !EdgeIterator.Edge.isValid(toEdge))
            throw new IllegalArgumentException("from and to edge cannot be NO_EDGE");
        if (viaNode < 0)
            throw new IllegalArgumentException("via node cannot be negative");

        int pointer = nodeAccess.getTurnCostIndex(viaNode);
        long nextPointer = viaNode + 1 == nodeCount ? lastReadPointer : nodeAccess.getTurnCostIndex(viaNode + 1);

        for (; pointer < nextPointer; pointer += R_BYTES_PER_ENTRY) {
            if (fromEdge == readTurnCosts.getInt(pointer + R_TC_FROM) && toEdge == readTurnCosts.getInt(pointer + R_TC_TO))
                return pointer;
        }
        return -1; // specific edge not found
    }

    public boolean isClosed() {
        return frozen ? readTurnCosts.isClosed() : false;
    }

    @Override
    public String toString() {
        return "turn_cost";
    }

    /**
     * Returns an iterator over all entries.
     *
     * @return an iterator over all entries.
     */
    public Iterator getAllTurnCosts() {
        return new Itr();
    }

    public interface Iterator {
        int getFromEdge();

        int getViaNode();

        int getToEdge();

        boolean get(BooleanEncodedValue booleanEncodedValue);

        double getCost(DecimalEncodedValue encodedValue);

        boolean next();
    }

    private class Itr implements Iterator {
        private int viaNode = -1;
        private long pointer = -1;
        private long nextPointer = -1;
        private final IntsRef intsRef = new IntsRef(1);
        private final EdgeIntAccess edgeIntAccess = new IntsRefEdgeIntAccess(intsRef);

        private Itr() {
            if (!frozen)
                throw new RuntimeException("Cannot read turn cost storage if not yet frozen");
        }

        @Override
        public int getFromEdge() {
            return readTurnCosts.getInt(pointer + R_TC_FROM);
        }

        @Override
        public int getViaNode() {
            return viaNode;
        }

        @Override
        public int getToEdge() {
            return readTurnCosts.getInt(pointer + R_TC_TO);
        }

        @Override
        public boolean get(BooleanEncodedValue booleanEncodedValue) {
            intsRef.ints[0] = readTurnCosts.getInt(pointer + R_TC_FLAGS);
            return booleanEncodedValue.getBool(false, -1, edgeIntAccess);
        }

        @Override
        public double getCost(DecimalEncodedValue encodedValue) {
            intsRef.ints[0] = readTurnCosts.getInt(pointer + R_TC_FLAGS);
            return encodedValue.getDecimal(false, -1, edgeIntAccess);
        }

        @Override
        public boolean next() {
            boolean gotNextTce = nextTurnCostEntry();
            if (!gotNextTce) {
                boolean gotNextNode = true;
                while (pointer >= nextPointer && (gotNextNode = nextNode())) {

                }
                if (!gotNextNode) {
                    return false;
                }
            }
            return true;
        }

        private boolean nextNode() {
            viaNode++;
            if (viaNode >= baseGraph.getNodes()) {
                return false;
            }

            pointer = nodeAccess.getTurnCostIndex(viaNode);
            nextPointer = viaNode + 1 == nodeCount ? lastReadPointer : nodeAccess.getTurnCostIndex(viaNode + 1);
            return true;
        }

        private boolean nextTurnCostEntry() {
            pointer += R_BYTES_PER_ENTRY;
            return pointer < nextPointer;
        }
    }

}

