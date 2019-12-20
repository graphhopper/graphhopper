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

import com.graphhopper.routing.profiles.DecimalEncodedValue;
import com.graphhopper.routing.profiles.EncodedValueLookup;
import com.graphhopper.routing.profiles.TurnCost;
import com.graphhopper.util.EdgeIterator;

import static com.graphhopper.routing.profiles.TurnCost.EV_SUFFIX;
import static com.graphhopper.routing.util.EncodingManager.getKey;

/**
 * Holds turn cost tables for each node. The additional field of a node will be used to point towards the
 * first entry within a node cost table to identify turn restrictions or turn costs.
 *
 * @author Karl Hübner
 * @author Peter Karich
 */
public class TurnCostStorage implements Storable<TurnCostStorage> {
    static final int NO_TURN_ENTRY = -1;
    private static final int EMPTY_FLAGS = 0;
    // we store each turn cost entry in the format |from_edge|to_edge|flags|next|. each entry has 4 bytes -> 16 bytes total
    private static final int TC_FROM = 0;
    private static final int TC_TO = 4;
    private static final int TC_FLAGS = 8;
    private static final int TC_NEXT = 12;
    private static final int BYTES_PER_ENTRY = 16;

    private NodeAccess nodeAccess;
    private DataAccess turnCosts;
    private int turnCostsCount;

    public TurnCostStorage(NodeAccess nodeAccess, DataAccess turnCosts) {
        this.nodeAccess = nodeAccess;
        this.turnCosts = turnCosts;
    }

    public TurnCostStorage(TurnCostStorage turnCostStorage) {
        this.nodeAccess = turnCostStorage.nodeAccess;
        this.turnCosts = turnCostStorage.turnCosts;
        this.turnCostsCount = turnCostStorage.turnCostsCount;
    }

    public void setSegmentSize(int bytes) {
        turnCosts.setSegmentSize(bytes);
    }

    @Override
    public TurnCostStorage create(long initBytes) {
        turnCosts.create(initBytes);
        return this;
    }

    @Override
    public void flush() {
        turnCosts.setHeader(0, BYTES_PER_ENTRY);
        turnCosts.setHeader(1 * 4, turnCostsCount);
        turnCosts.flush();
    }

    @Override
    public void close() {
        turnCosts.close();
    }

    @Override
    public long getCapacity() {
        return turnCosts.getCapacity();
    }

    @Override
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
     * This is a convenient setter method and should not be used in loops or where speed is important.
     */
    public void setExpensive(String name, EncodedValueLookup lookup, int fromEdge, int viaNode, int toEdge, double cost) {
        set(lookup.getDecimalEncodedValue(getKey(name, EV_SUFFIX)), TurnCost.createFlags(), fromEdge, viaNode, toEdge, cost);
    }

    /**
     * Sets the turn cost at the viaNode when going from "fromEdge" to "toEdge"
     */
    public void set(DecimalEncodedValue turnCostEnc, IntsRef tcFlags, int fromEdge, int viaNode, int toEdge, double cost) {
        // reset is required as we could have read a value for other vehicles before (that was changed in the meantime) that we would overwrite
        tcFlags.ints[0] = 0;
        turnCostEnc.setDecimal(false, tcFlags, cost);
        setTurnCost(tcFlags, fromEdge, viaNode, toEdge);
    }

    /**
     * Add an entry which is a turn restriction or cost information via the turnFlags. Overwrite existing information
     * if it is the same edges and node.
     *
     * @param fromEdge edge ID
     * @param viaNode  node ID
     * @param toEdge   edge ID
     * @param tcFlags  flags to be written
     */
    public void setTurnCost(IntsRef tcFlags, int fromEdge, int viaNode, int toEdge) {
        if (tcFlags.length != 1)
            throw new IllegalArgumentException("Cannot use IntsRef with length != 1");
        if (tcFlags.ints[0] == 0)
            return;

        setOrMerge(tcFlags, fromEdge, viaNode, toEdge, true);
    }

    void setOrMerge(IntsRef tcFlags, int fromEdge, int viaNode, int toEdge, boolean merge) {
        int newEntryIndex = turnCostsCount;
        ensureTurnCostIndex(newEntryIndex);
        boolean oldEntryFound = false;
        int newFlags = tcFlags.ints[0];
        int next = NO_TURN_ENTRY;

        // determine if we already have a cost entry for this node
        int previousEntryIndex = nodeAccess.getTurnCostIndex(viaNode);
        if (previousEntryIndex == NO_TURN_ENTRY) {
            // set cost-pointer to this new cost entry
            nodeAccess.setTurnCostIndex(viaNode, newEntryIndex);
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
                    throw new IllegalStateException("Something unexpected happened. A node probably will not have 1000+ relations.");
                }
                // get index of next turn cost entry
                next = turnCosts.getInt((long) next * BYTES_PER_ENTRY + TC_NEXT);
            }
            if (!oldEntryFound) {
                // set next-pointer to this new cost entry
                turnCosts.setInt((long) previousEntryIndex * BYTES_PER_ENTRY + TC_NEXT, newEntryIndex);
            } else if (merge) {
                newFlags = existingFlags | newFlags;
            } else {
                // overwrite!
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
     * This is a convenient getter method and should not be used in loops or where speed is important.
     */
    public double getExpensive(String name, EncodedValueLookup lookup, int fromEdge, int viaNode, int toEdge) {
        return get(lookup.getDecimalEncodedValue(getKey(name, EV_SUFFIX)), TurnCost.createFlags(), fromEdge, viaNode, toEdge);
    }

    /**
     * @return the turn cost of the viaNode when going from "fromEdge" to "toEdge"
     */
    public double get(DecimalEncodedValue turnCostEnc, IntsRef tcFlags, int fromEdge, int viaNode, int toEdge) {
        // reset is required as we could have a QueryGraph that does not set tcFlags
        tcFlags.ints[0] = 0;
        return turnCostEnc.getDecimal(false, readFlags(tcFlags, fromEdge, viaNode, toEdge));
    }

    /**
     * @return turn cost flags of the specified triple "from edge", "via node" and "to edge"
     */
    public IntsRef readFlags(IntsRef tcFlags, int fromEdge, int viaNode, int toEdge) {
        if (!EdgeIterator.Edge.isValid(fromEdge) || !EdgeIterator.Edge.isValid(toEdge))
            throw new IllegalArgumentException("from and to edge cannot be NO_EDGE");
        if (viaNode < 0)
            throw new IllegalArgumentException("via node cannot be negative");

        nextCostFlags(tcFlags, fromEdge, viaNode, toEdge);
        return tcFlags;
    }

    public boolean isUTurn(int edgeFrom, int edgeTo) {
        return edgeFrom == edgeTo;
    }

    public boolean isUTurnAllowed(int node) {
        return true;
    }

    private void nextCostFlags(IntsRef tcFlags, int fromEdge, int viaNode, int toEdge) {
        int turnCostIndex = nodeAccess.getTurnCostIndex(viaNode);
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

    public TurnCostStorage copyTo(TurnCostStorage turnCostStorage) {
        turnCosts.copyTo(turnCostStorage.turnCosts);
        turnCostStorage.turnCostsCount = turnCostsCount;
        return turnCostStorage;
    }

    @Override
    public boolean isClosed() {
        return turnCosts.isClosed();
    }

    @Override
    public String toString() {
        return "turn_cost";
    }
}

