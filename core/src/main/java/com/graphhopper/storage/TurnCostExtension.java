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

import com.graphhopper.util.EdgeIterator;

/**
 * Holds turn cost tables for each node. The additional field of a node will be used to point
 * towards the first entry within a node cost table to identify turn restrictions, or later, turn
 * getCosts.
 * <p>
 *
 * @author Karl Hübner
 * @author Peter Karich
 */
public class TurnCostExtension implements GraphExtension {
    /* pointer for no cost entry */
    private static final int NO_TURN_ENTRY = -1;
    private static final long EMPTY_FLAGS = 0L;

    /*
     * items in turn cost tables: edge from, edge to, getCosts, pointer to next
     * cost entry of same node
     */
    private final int TC_FROM, TC_TO, TC_FLAGS, TC_NEXT;

    private DataAccess turnCosts;
    private int turnCostsEntryIndex = -4;
    private int turnCostsEntryBytes;
    private int turnCostsCount;
    private NodeAccess nodeAccess;

    public TurnCostExtension() {
        TC_FROM = nextTurnCostEntryIndex();
        TC_TO = nextTurnCostEntryIndex();
        TC_FLAGS = nextTurnCostEntryIndex();
        TC_NEXT = nextTurnCostEntryIndex();
        turnCostsEntryBytes = turnCostsEntryIndex + 4;
        turnCostsCount = 0;
    }

    @Override
    public void init(Graph graph, Directory dir) {
        if (turnCostsCount > 0)
            throw new AssertionError("The turn cost storage must be initialized only once.");

        this.nodeAccess = graph.getNodeAccess();
        this.turnCosts = dir.find("turn_costs");
    }

    private int nextTurnCostEntryIndex() {
        turnCostsEntryIndex += 4;
        return turnCostsEntryIndex;
    }

    @Override
    public void setSegmentSize(int bytes) {
        turnCosts.setSegmentSize(bytes);
    }

    @Override
    public TurnCostExtension create(long initBytes) {
        turnCosts.create(initBytes);
        return this;
    }

    @Override
    public void flush() {
        turnCosts.setHeader(0, turnCostsEntryBytes);
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

        turnCostsEntryBytes = turnCosts.getHeader(0);
        turnCostsCount = turnCosts.getHeader(4);
        return true;
    }

    /**
     * Add an entry which is a turn restriction or cost information via the turnFlags. Overwrite existing information
     * if it is the same edges and node.
     */
    public void addTurnInfo(int fromEdge, int viaNode, int toEdge, long turnFlags) {
        // no need to store turn information
        if (turnFlags == EMPTY_FLAGS)
            return;

        mergeOrOverwriteTurnInfo(fromEdge, viaNode, toEdge, turnFlags, true);
    }

    /**
     * Add a new turn cost entry or clear an existing. See tests for usage examples.
     *
     * @param fromEdge  edge ID
     * @param viaNode   node ID
     * @param toEdge    edge ID
     * @param turnFlags flags to be written
     * @param merge     If true don't overwrite existing entries with the new flag but do a bitwise OR of the old and
     *                  new flags and write this merged flag.
     */
    public void mergeOrOverwriteTurnInfo(int fromEdge, int viaNode, int toEdge, long turnFlags, boolean merge) {
        int newEntryIndex = turnCostsCount;
        ensureTurnCostIndex(newEntryIndex);
        boolean oldEntryFound = false;
        long newFlags = turnFlags;
        int next = NO_TURN_ENTRY;

        // determine if we already have a cost entry for this node
        int previousEntryIndex = nodeAccess.getAdditionalNodeField(viaNode);
        if (previousEntryIndex == NO_TURN_ENTRY) {
            // set cost-pointer to this new cost entry
            nodeAccess.setAdditionalNodeField(viaNode, newEntryIndex);
        } else {
            int i = 0;
            next = turnCosts.getInt((long) previousEntryIndex * turnCostsEntryBytes + TC_NEXT);
            long existingFlags = 0;
            while (true) {
                long costsIdx = (long) previousEntryIndex * turnCostsEntryBytes;
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
                next = turnCosts.getInt((long) next * turnCostsEntryBytes + TC_NEXT);
            }
            if (!oldEntryFound) {
                // set next-pointer to this new cost entry
                turnCosts.setInt((long) previousEntryIndex * turnCostsEntryBytes + TC_NEXT, newEntryIndex);
            } else if (merge) {
                newFlags = existingFlags | newFlags;
            } else {
                // overwrite!
            }
        }
        long costsBase; // where to (over)write
        if (!oldEntryFound) {
            costsBase = (long) newEntryIndex * turnCostsEntryBytes;
            turnCostsCount++;
        } else {
            costsBase = (long) previousEntryIndex * turnCostsEntryBytes;
        }
        turnCosts.setInt(costsBase + TC_FROM, fromEdge);
        turnCosts.setInt(costsBase + TC_TO, toEdge);
        turnCosts.setInt(costsBase + TC_FLAGS, (int) newFlags);
        turnCosts.setInt(costsBase + TC_NEXT, next);
    }

    /**
     * @return turn flags of the specified node and edge properties.
     */
    public long getTurnCostFlags(int edgeFrom, int nodeVia, int edgeTo) {
        if (edgeFrom == EdgeIterator.NO_EDGE || edgeTo == EdgeIterator.NO_EDGE)
            throw new IllegalArgumentException("from and to edge cannot be NO_EDGE");
        if (nodeVia < 0)
            throw new IllegalArgumentException("via node cannot be negative");

        return nextCostFlags(edgeFrom, nodeVia, edgeTo);
    }

    private long nextCostFlags(int edgeFrom, int nodeVia, int edgeTo) {
        int turnCostIndex = nodeAccess.getAdditionalNodeField(nodeVia);
        int i = 0;
        for (; i < 1000; i++) {
            if (turnCostIndex == NO_TURN_ENTRY)
                break;
            long turnCostPtr = (long) turnCostIndex * turnCostsEntryBytes;
            if (edgeFrom == turnCosts.getInt(turnCostPtr + TC_FROM)) {
                if (edgeTo == turnCosts.getInt(turnCostPtr + TC_TO))
                    return turnCosts.getInt(turnCostPtr + TC_FLAGS);
            }

            int nextTurnCostIndex = turnCosts.getInt(turnCostPtr + TC_NEXT);
            if (nextTurnCostIndex == turnCostIndex)
                throw new IllegalStateException("something went wrong: next entry would be the same");

            turnCostIndex = nextTurnCostIndex;
        }
        // so many turn restrictions on one node? here is something wrong
        if (i >= 1000)
            throw new IllegalStateException("something went wrong: there seems to be no end of the turn cost-list!?");
        return EMPTY_FLAGS;
    }

    private void ensureTurnCostIndex(int nodeIndex) {
        turnCosts.ensureCapacity(((long) nodeIndex + 4) * turnCostsEntryBytes);
    }

    @Override
    public boolean isRequireNodeField() {
        //we require the additional field in the graph to point to the first entry in the node table
        return true;
    }

    @Override
    public boolean isRequireEdgeField() {
        return false;
    }

    @Override
    public int getDefaultNodeFieldValue() {
        return NO_TURN_ENTRY;
    }

    @Override
    public int getDefaultEdgeFieldValue() {
        throw new UnsupportedOperationException("Not supported by this storage");
    }

    @Override
    public GraphExtension copyTo(GraphExtension clonedStorage) {
        if (!(clonedStorage instanceof TurnCostExtension)) {
            throw new IllegalStateException("the extended storage to clone must be the same");
        }

        TurnCostExtension clonedTC = (TurnCostExtension) clonedStorage;

        turnCosts.copyTo(clonedTC.turnCosts);
        clonedTC.turnCostsCount = turnCostsCount;

        return clonedStorage;
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

