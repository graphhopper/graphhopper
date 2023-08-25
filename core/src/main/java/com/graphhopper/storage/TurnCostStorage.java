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

import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.EdgeIntAccess;
import com.graphhopper.routing.ev.IntsRefEdgeIntAccess;
import com.graphhopper.util.Constants;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.GHUtility;

/**
 * A key/value store, where the unique keys are turn cost relations, and the values are IntRefs.
 * A turn cost relation is a triple (fromEdge, viaNode, toEdge),
 * and refers to one of the possible ways of crossing an intersection.
 * <p>
 * Like IntRefs on edges, this can in principle be used to store values of any kind.
 * <p>
 * In practice, the IntRefs are used to store generalized travel costs per turn cost relation per vehicle type.
 * In practice, we only store 0 or infinity. (Can turn, or cannot turn.)
 *
 * @author Karl Hübner
 * @author Peter Karich
 * @author Michael Zilske
 */
public class TurnCostStorage {
    static final int NO_TURN_ENTRY = -1;
    // we store each turn cost entry in the format |from_edge|to_edge|flags|next|. each entry has 4 bytes -> 16 bytes total
    private static final int TC_FROM = 0;
    private static final int TC_TO = 4;
    private static final int TC_FLAGS = 8;
    private static final int TC_NEXT = 12;
    private static final int BYTES_PER_ENTRY = 16;

    private final BaseGraph baseGraph;
    private final DataAccess turnCosts;
    private int turnCostsCount;

    public TurnCostStorage(BaseGraph baseGraph, DataAccess turnCosts) {
        this.baseGraph = baseGraph;
        this.turnCosts = turnCosts;
    }

    public TurnCostStorage create(long initBytes) {
        turnCosts.create(initBytes);
        return this;
    }

    public void flush() {
        turnCosts.setHeader(0, Constants.VERSION_TURN_COSTS);
        turnCosts.setHeader(4, BYTES_PER_ENTRY);
        turnCosts.setHeader(2 * 4, turnCostsCount);
        turnCosts.flush();
    }

    public void close() {
        turnCosts.close();
    }

    public long getCapacity() {
        return turnCosts.getCapacity();
    }

    public boolean loadExisting() {
        if (!turnCosts.loadExisting())
            return false;

        GHUtility.checkDAVersion(turnCosts.getName(), Constants.VERSION_TURN_COSTS, turnCosts.getHeader(0));
        if (turnCosts.getHeader(4) != BYTES_PER_ENTRY) {
            throw new IllegalStateException("Number of bytes per turn cost entry does not match the current configuration: " + turnCosts.getHeader(0) + " vs. " + BYTES_PER_ENTRY);
        }
        turnCostsCount = turnCosts.getHeader(8);
        return true;
    }

    /**
     * Sets the turn cost at the viaNode when going from "fromEdge" to "toEdge"
     * WARNING: It is tacitly assumed that for every encoder, this method is only called once per turn cost relation.
     * Subsequent calls for the same encoder and the same turn cost relation will have undefined results.
     * (The implementation below ORs the new bits into the existing bits.)
     */
    public void set(DecimalEncodedValue turnCostEnc, int fromEdge, int viaNode, int toEdge, double cost) {
        EdgeIntAccessImpl intAccess = new EdgeIntAccessImpl();
        turnCostEnc.setDecimal(false, -1, intAccess, cost);
        int newFlags = intAccess.getVal();

        long pointer = findPointer(fromEdge, viaNode, toEdge);
        if (pointer < 0) {
            // create a new entry
            ensureTurnCostIndex(turnCostsCount);
            int prevIndex = baseGraph.getNodeAccess().getTurnCostIndex(viaNode);
            baseGraph.getNodeAccess().setTurnCostIndex(viaNode, turnCostsCount);
            pointer = (long) turnCostsCount * BYTES_PER_ENTRY;
            turnCosts.setInt(pointer + TC_FROM, fromEdge);
            turnCosts.setInt(pointer + TC_TO, toEdge);
            turnCosts.setInt(pointer + TC_NEXT, prevIndex);
            turnCostsCount++;
        } else {
            newFlags = turnCosts.getInt(pointer + TC_FLAGS) | newFlags;
        }
        turnCosts.setInt(pointer + TC_FLAGS, newFlags);
    }

    /**
     * @return the turn cost of the viaNode when going from "fromEdge" to "toEdge"
     */
    public double get(DecimalEncodedValue turnCostEnc, int fromEdge, int viaNode, int toEdge) {
        int val = readFlagsInt(fromEdge, viaNode, toEdge);
        return turnCostEnc.getDecimal(false, -1, new EdgeIntAccessImpl(val));
    }

    private static class EdgeIntAccessImpl implements EdgeIntAccess {

        private int val;

        EdgeIntAccessImpl() {
        }

        EdgeIntAccessImpl(int val) {
            this.val = val;
        }

        @Override
        public int getInt(int edgeId, int index) {
            return val;
        }

        @Override
        public void setInt(int edgeId, int index, int value) {
            if (index > 0)
                throw new IllegalArgumentException("TurnCostStorage: more than 1 int not supported");
            this.val = value;
        }

        public int getVal() {
            return val;
        }
    }

    /**
     * @return turn cost flags of the specified triple "from edge", "via node" and "to edge"
     */
    private int readFlagsInt(int fromEdge, int viaNode, int toEdge) {
        long pointer = findPointer(fromEdge, viaNode, toEdge);
        return pointer < 0 ? 0 : turnCosts.getInt(pointer + TC_FLAGS);
    }

    private void ensureTurnCostIndex(int nodeIndex) {
        turnCosts.ensureCapacity(((long) nodeIndex + 4) * BYTES_PER_ENTRY);
    }

    private long findPointer(int fromEdge, int viaNode, int toEdge) {
        if (!EdgeIterator.Edge.isValid(fromEdge) || !EdgeIterator.Edge.isValid(toEdge))
            throw new IllegalArgumentException("from and to edge cannot be NO_EDGE");
        if (viaNode < 0)
            throw new IllegalArgumentException("via node cannot be negative");

        final int maxEntries = 1000;
        int index = baseGraph.getNodeAccess().getTurnCostIndex(viaNode);
        for (int i = 0; i < maxEntries; ++i) {
            if (index == NO_TURN_ENTRY) return -1;
            long pointer = (long) index * BYTES_PER_ENTRY;
            if (fromEdge == turnCosts.getInt(pointer + TC_FROM) && toEdge == turnCosts.getInt(pointer + TC_TO))
                return pointer;
            index = turnCosts.getInt(pointer + TC_NEXT);
        }
        throw new IllegalStateException("Turn cost list for node: " + viaNode + " is longer than expected, max: " + maxEntries);
    }

    public boolean isClosed() {
        return turnCosts.isClosed();
    }

    @Override
    public String toString() {
        return "turn_cost";
    }

    // TODO: Maybe some of the stuff above could now be re-implemented in a simpler way with some of the stuff below.
    // For now, I just wanted to iterate over all entries.

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

        double getCost(DecimalEncodedValue encodedValue);

        boolean next();
    }

    private class Itr implements Iterator {
        private int viaNode = -1;
        private int turnCostIndex = -1;
        private final IntsRef intsRef = new IntsRef(1);
        private final EdgeIntAccess edgeIntAccess = new IntsRefEdgeIntAccess(intsRef);

        private long turnCostPtr() {
            return (long) turnCostIndex * BYTES_PER_ENTRY;
        }

        @Override
        public int getFromEdge() {
            return turnCosts.getInt(turnCostPtr() + TC_FROM);
        }

        @Override
        public int getViaNode() {
            return viaNode;
        }

        @Override
        public int getToEdge() {
            return turnCosts.getInt(turnCostPtr() + TC_TO);
        }

        @Override
        public double getCost(DecimalEncodedValue encodedValue) {
            intsRef.ints[0] = turnCosts.getInt(turnCostPtr() + TC_FLAGS);
            return encodedValue.getDecimal(false, -1, edgeIntAccess);
        }

        @Override
        public boolean next() {
            boolean gotNextTci = nextTci();
            if (!gotNextTci) {
                turnCostIndex = NO_TURN_ENTRY;
                boolean gotNextNode = true;
                while (turnCostIndex == NO_TURN_ENTRY && (gotNextNode = nextNode())) {

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
            turnCostIndex = baseGraph.getNodeAccess().getTurnCostIndex(viaNode);
            return true;
        }

        private boolean nextTci() {
            if (turnCostIndex == NO_TURN_ENTRY) {
                return false;
            }
            turnCostIndex = turnCosts.getInt(turnCostPtr() + TC_NEXT);
            if (turnCostIndex == NO_TURN_ENTRY) {
                return false;
            }
            return true;
        }
    }

}

