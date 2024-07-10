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
import com.graphhopper.util.Constants;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.GHUtility;

/**
 * A key/value store, where the unique keys are triples (fromEdge, viaNode, toEdge) and the values
 * are integers that can be used to store encoded values.
 *
 * @author Karl Hübner
 * @author Peter Karich
 * @author Michael Zilske
 */
public class TurnCostStorage {
    static final int NO_TURN_ENTRY = -1;
    // we store each turn cost entry in the format |from_edge|to_edge|flags|. each element has 4 bytes -> 12 bytes total
    private static final int TC_TO = 0;
    private static final int TC_FLAGS = 4;
    private static final int BYTES_PER_ENTRY = 8;
    private static final int BYTES_PER_INDEX = 4; // todo: probably three would be enough

    private final DataAccess turnCosts;
    private final DataAccess turnCostIndices;
    private final EdgeIntAccess edgeIntAccess = createEdgeIntAccess();
    private int lastFromEdge;
    private int turnCostsCount;

    public TurnCostStorage(DataAccess turnCostIndices, DataAccess turnCosts) {
        this.turnCosts = turnCosts;
        this.turnCostIndices = turnCostIndices;
    }

    public TurnCostStorage create(long initBytes) {
        turnCosts.create(initBytes);
        turnCostIndices.create(initBytes);
        return this;
    }

    public void flush() {
        turnCosts.setHeader(0, Constants.VERSION_TURN_COSTS);
        turnCosts.setHeader(4, BYTES_PER_ENTRY);
        turnCosts.setHeader(2 * 4, turnCostsCount);
        turnCosts.flush();

        turnCostIndices.setHeader(0, Constants.VERSION_TURN_COSTS);
        turnCostIndices.setHeader(4, BYTES_PER_INDEX);
        turnCostIndices.setHeader(2 * 4, lastFromEdge);
        turnCostIndices.flush();
    }

    public void close() {
        turnCosts.close();
    }

    public long getCapacity() {
        return turnCosts.getCapacity();
    }

    public boolean loadExisting() {
        if (!turnCosts.loadExisting() || !turnCostIndices.loadExisting())
            return false;

        GHUtility.checkDAVersion(turnCosts.getName(), Constants.VERSION_TURN_COSTS, turnCosts.getHeader(0));
        GHUtility.checkDAVersion(turnCostIndices.getName(), Constants.VERSION_TURN_COSTS, turnCostIndices.getHeader(0));
        if (turnCosts.getHeader(4) != BYTES_PER_ENTRY)
            throw new IllegalStateException("Number of bytes per turn cost entry does not match the current configuration: " + turnCosts.getHeader(4) + " vs. " + BYTES_PER_ENTRY);
        if (turnCostIndices.getHeader(4) != BYTES_PER_INDEX)
            throw new IllegalStateException("Number of bytes per turn cost index does not match the current configuration: " + turnCostIndices.getHeader(4) + " vs. " + BYTES_PER_INDEX);
        turnCostsCount = turnCosts.getHeader(8);
        lastFromEdge = turnCostIndices.getHeader(8);
        return true;
    }

    public void set(BooleanEncodedValue bev, int fromEdge, int viaNode, int toEdge, boolean value) {
        int index = findOrCreateTurnCostEntry(fromEdge, viaNode, toEdge);
        if (index < 0)
            throw new IllegalStateException("Invalid index: " + index + " at (" + fromEdge + ", " + viaNode + ", " + toEdge + ")");
        bev.setBool(false, index, edgeIntAccess, value);
    }

    /**
     * Sets the turn cost at the viaNode when going from "fromEdge" to "toEdge"
     */
    public void set(DecimalEncodedValue turnCostEnc, int fromEdge, int viaNode, int toEdge, double cost) {
        int index = findOrCreateTurnCostEntry(fromEdge, viaNode, toEdge);
        if (index < 0)
            throw new IllegalStateException("Invalid index: " + index + " at (" + fromEdge + ", " + viaNode + ", " + toEdge + ")");
        turnCostEnc.setDecimal(false, index, edgeIntAccess, cost);
    }

    private int findOrCreateTurnCostEntry(int fromEdge, int viaNode, int toEdge) {
        int index = findIndex(fromEdge, viaNode, toEdge);
        if (index < 0) {
            // create a new entry
            if (fromEdge < lastFromEdge)
                throw new IllegalArgumentException("Turn cost entries must be written in ascending order of the fromEdge");
            turnCostIndices.ensureCapacity((long) (fromEdge + 1) * BYTES_PER_INDEX);
            for (int i = lastFromEdge + 1; i <= fromEdge; i++)
                turnCostIndices.setInt((long) i * BYTES_PER_INDEX, turnCostsCount);
            turnCosts.ensureCapacity((long) (turnCostsCount + 1) * BYTES_PER_ENTRY);
            long pointer = (long) turnCostsCount * BYTES_PER_ENTRY;
            // todonow: as long as we do not store the via-node (and do not use edge keys) we cannot really store u-turn restrictions
            turnCosts.setInt(pointer + TC_TO, toEdge);
            turnCostsCount++;
            lastFromEdge = fromEdge;
            return turnCostsCount - 1;
        }
        return index;
    }

    public double get(DecimalEncodedValue dev, int fromEdge, int viaNode, int toEdge) {
        int index = findIndex(fromEdge, viaNode, toEdge);
        // todo: should we rather pass 0 to the encoded value so it can decide what this means?
        if (index < 0) return 0;
        return dev.getDecimal(false, index, edgeIntAccess);
    }

    public boolean get(BooleanEncodedValue bev, int fromEdge, int viaNode, int toEdge) {
        int index = findIndex(fromEdge, viaNode, toEdge);
        // todo: should we rather pass 0 to the encoded value so it can decide what this means?
        if (index < 0) return false;
        return bev.getBool(false, index, edgeIntAccess);
    }

    private EdgeIntAccess createEdgeIntAccess() {
        return new EdgeIntAccess() {
            @Override
            public int getInt(int entryIndex, int index) {
                return turnCosts.getInt((long) entryIndex * BYTES_PER_ENTRY + TC_FLAGS);
            }

            @Override
            public void setInt(int entryIndex, int index, int value) {
                turnCosts.setInt((long) entryIndex * BYTES_PER_ENTRY + TC_FLAGS, value);
            }
        };
    }

    private int findIndex(int fromEdge, int viaNode, int toEdge) {
        if (!EdgeIterator.Edge.isValid(fromEdge) || !EdgeIterator.Edge.isValid(toEdge))
            throw new IllegalArgumentException("from and to edge cannot be NO_EDGE");
        if (viaNode < 0)
            throw new IllegalArgumentException("via node cannot be negative");
        if (fromEdge > lastFromEdge) return NO_TURN_ENTRY;
        int begin = turnCostIndices.getInt((long) fromEdge * BYTES_PER_INDEX);
        int end = fromEdge == lastFromEdge ? turnCostsCount : turnCostIndices.getInt((long) (fromEdge + 1) * BYTES_PER_INDEX);
        for (int index = begin; index < end; ++index) {
            long pointer = (long) index * BYTES_PER_ENTRY;
            if (toEdge == turnCosts.getInt(pointer + TC_TO))
                return index;
        }
        return NO_TURN_ENTRY;
    }

    public int getTurnCostsCount() {
        return turnCostsCount;
    }

    public int getTurnCostsCount(int node) {
        // todonow: not implemented atm
        return 0;
//        if (node > lastFromEdge) return 0;
//        int index = turnCostIndices.getInt((long) node * BYTES_PER_INDEX);
//        int end = node == lastFromEdge ? turnCostsCount : turnCostIndices.getInt((long) (node + 1) * BYTES_PER_INDEX);
//        return end - index;
    }

    public boolean isClosed() {
        return turnCosts.isClosed();
    }

    @Override
    public String toString() {
        return "turn_cost";
    }

    /**
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
        // todonow: this iterator still needs to be adjusted to from-edge sorting
        private int viaNode = 0;
        private int index = -1;
        private int lastIndexForCurrentViaNode = getTurnCostsCount(viaNode);

        @Override
        public int getFromEdge() {
            return 0;
//            return turnCosts.getInt(turnCostPtr() + TC_FROM);
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
        public boolean get(BooleanEncodedValue bev) {
            return bev.getBool(false, index, edgeIntAccess);
        }

        @Override
        public double getCost(DecimalEncodedValue encodedValue) {
            return encodedValue.getDecimal(false, index, edgeIntAccess);
        }

        @Override
        public boolean next() {
            index++;
            if (index < lastIndexForCurrentViaNode)
                return true;
            else {
                while (true) {
                    viaNode++;
                    if (viaNode > lastFromEdge) {
                        viaNode = lastFromEdge;
                        return false;
                    }
                    int count = getTurnCostsCount(viaNode);
                    if (count > 0) {
                        lastIndexForCurrentViaNode = index + count;
                        return true;
                    }
                }
            }
        }

        private long turnCostPtr() {
            return (long) index * BYTES_PER_ENTRY;
        }
    }
}

