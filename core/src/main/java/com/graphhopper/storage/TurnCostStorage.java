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
import com.graphhopper.routing.ev.TurnCost;
import com.graphhopper.core.util.EdgeIterator;

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
    private static final int EMPTY_FLAGS = 0;
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
        turnCosts.setHeader(0, BYTES_PER_ENTRY);
        turnCosts.setHeader(1 * 4, turnCostsCount);
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

        if (turnCosts.getHeader(0) != BYTES_PER_ENTRY) {
            throw new IllegalStateException("Number of bytes per turn cost entry does not match the current configuration: " + turnCosts.getHeader(0) + " vs. " + BYTES_PER_ENTRY);
        }
        turnCostsCount = turnCosts.getHeader(4);
        return true;
    }

    /**
     * Sets the turn cost at the viaNode when going from "fromEdge" to "toEdge"
     * WARNING: It is tacitly assumed that for every encoder, this method is only called once per turn cost relation.
     * Subsequent calls for the same encoder and the same turn cost relation will have undefined results.
     * (The implementation below ORs the new bits into the existing bits.)
     */
    public void set(DecimalEncodedValue turnCostEnc, int fromEdge, int viaNode, int toEdge, double cost) {
        IntsRef tcFlags = TurnCost.createFlags();
        turnCostEnc.setDecimal(false, tcFlags, cost);
        merge(tcFlags, fromEdge, viaNode, toEdge);
    }

    private void merge(IntsRef tcFlags, int fromEdge, int viaNode, int toEdge) {
        int newEntryIndex = turnCostsCount;
        ensureTurnCostIndex(newEntryIndex);
        boolean oldEntryFound = false;
        int newFlags = tcFlags.ints[0];
        int next = NO_TURN_ENTRY;

        // determine if we already have a cost entry for this node
        int previousEntryIndex = baseGraph.getNodeAccess().getTurnCostIndex(viaNode);
        if (previousEntryIndex == NO_TURN_ENTRY) {
            // set cost-pointer to this new cost entry
            baseGraph.getNodeAccess().setTurnCostIndex(viaNode, newEntryIndex);
        } else {
            int i = 0;
            next = turnCosts.getInt((long) previousEntryIndex * BYTES_PER_ENTRY + TC_NEXT);
            int existingFlags = 0;
            while (true) {
                long costsIdx = (long) previousEntryIndex * BYTES_PER_ENTRY;
                if (fromEdge == turnCosts.getInt(costsIdx + TC_FROM)
                        && toEdge == turnCosts.getInt(costsIdx + TC_TO)) {
                    // there is already an entry for this turn
                    oldEntryFound = true;
                    existingFlags = turnCosts.getInt(costsIdx + TC_FLAGS);
                    break;
                } else if (next == NO_TURN_ENTRY) {
                    break;
                }
                previousEntryIndex = next;
                // search for the last added cost entry
                if (i++ > 1000) {
                    throw new IllegalStateException("Something unexpected happened. A node probably will not have 1000+ turn cost relations.");
                }
                // get index of next turn cost entry
                next = turnCosts.getInt((long) next * BYTES_PER_ENTRY + TC_NEXT);
            }
            if (!oldEntryFound) {
                // set next-pointer to this new cost entry
                turnCosts.setInt((long) previousEntryIndex * BYTES_PER_ENTRY + TC_NEXT, newEntryIndex);
            } else {
                newFlags = existingFlags | newFlags;
            }
        }
        long costsBase; // where to (over)write
        if (!oldEntryFound) {
            costsBase = (long) newEntryIndex * BYTES_PER_ENTRY;
            turnCostsCount++;
        } else {
            costsBase = (long) previousEntryIndex * BYTES_PER_ENTRY;
        }
        turnCosts.setInt(costsBase + TC_FROM, fromEdge);
        turnCosts.setInt(costsBase + TC_TO, toEdge);
        turnCosts.setInt(costsBase + TC_FLAGS, newFlags);
        turnCosts.setInt(costsBase + TC_NEXT, next);
    }

    /**
     * @return the turn cost of the viaNode when going from "fromEdge" to "toEdge"
     */
    public double get(DecimalEncodedValue turnCostEnc, int fromEdge, int viaNode, int toEdge) {
        IntsRef flags = readFlags(fromEdge, viaNode, toEdge);
        return turnCostEnc.getDecimal(false, flags);
    }

    /**
     * @return turn cost flags of the specified triple "from edge", "via node" and "to edge"
     */
    private IntsRef readFlags(int fromEdge, int viaNode, int toEdge) {
        if (!EdgeIterator.Edge.isValid(fromEdge) || !EdgeIterator.Edge.isValid(toEdge))
            throw new IllegalArgumentException("from and to edge cannot be NO_EDGE");
        if (viaNode < 0)
            throw new IllegalArgumentException("via node cannot be negative");

        IntsRef flags = TurnCost.createFlags();
        readFlags(flags, fromEdge, viaNode, toEdge);
        return flags;
    }

    private void readFlags(IntsRef tcFlags, int fromEdge, int viaNode, int toEdge) {
        int turnCostIndex = baseGraph.getNodeAccess().getTurnCostIndex(viaNode);
        int i = 0;
        for (; i < 1000; i++) {
            if (turnCostIndex == NO_TURN_ENTRY)
                break;
            long turnCostPtr = (long) turnCostIndex * BYTES_PER_ENTRY;
            if (fromEdge == turnCosts.getInt(turnCostPtr + TC_FROM)) {
                if (toEdge == turnCosts.getInt(turnCostPtr + TC_TO)) {
                    tcFlags.ints[0] = turnCosts.getInt(turnCostPtr + TC_FLAGS);
                    return;
                }
            }

            int nextTurnCostIndex = turnCosts.getInt(turnCostPtr + TC_NEXT);
            if (nextTurnCostIndex == turnCostIndex)
                throw new IllegalStateException("something went wrong: next entry would be the same");

            turnCostIndex = nextTurnCostIndex;
        }
        // so many turn restrictions on one node? here is something wrong
        if (i >= 1000)
            throw new IllegalStateException("something went wrong: there seems to be no end of the turn cost-list!?");
        tcFlags.ints[0] = EMPTY_FLAGS;
    }

    private void ensureTurnCostIndex(int nodeIndex) {
        turnCosts.ensureCapacity(((long) nodeIndex + 4) * BYTES_PER_ENTRY);
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
        private final IntsRef intsRef = TurnCost.createFlags();

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
            return encodedValue.getDecimal(false, intsRef);
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

