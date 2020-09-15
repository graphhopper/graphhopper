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
import com.graphhopper.util.EdgeIterator;

import java.util.Arrays;

/**
 * A key/value store, where the unique keys are turn relations, and the values are IntRefs.
 * A turn relation is a triple (fromEdge, viaNode, toEdge),
 * and refers to one of the possible ways of crossing an intersection.
 * <p>
 * Like IntRefs on edges, this can in principle be used to store values of any kind.
 * <p>
 * In practice, the IntRefs are used to store generalized travel costs per turn relation per vehicle type.
 * In practice, we only store 0 or infinity. (Can turn, or cannot turn.)
 *
 * @author Karl Hübner
 * @author Peter Karich
 * @author Michael Zilske
 */
public class TurnCostStorage implements Storable<TurnCostStorage> {
    // we store each turn cost entry in the format |from_edge|to_edge|flags|
    private static final int TC_FROM = 0;
    private static final int TC_TO = 4;
    private static final int TC_FLAGS = 8;
    private static final int BYTES_PER_ENTRY = 12;
    private static final int I_TC_FROM = 0;
    private static final int I_TC_TO = 1;
    private static final int I_TC_FLAGS = 2;
    private static final int INTS_PER_ENTRY = 3;
    private int[][] tmpTCArr;

    private BaseGraph baseGraph;
    private DataAccess turnCosts;

    public TurnCostStorage(BaseGraph baseGraph, DataAccess turnCosts) {
        this.baseGraph = baseGraph;
        this.turnCosts = turnCosts;
    }

    public void setSegmentSize(int bytes) {
        turnCosts.setSegmentSize(bytes);
    }

    @Override
    public TurnCostStorage create(long initBytes) {
        turnCosts.create(initBytes);
        tmpTCArr = new int[baseGraph.getNodes()][];
        return this;
    }

    @Override
    public void flush() {
        // ensire array is copied into turnCosts DataAccess before flushing
        if (!isOptimized())
            optimize();

        turnCosts.setHeader(0, BYTES_PER_ENTRY);
        turnCosts.flush();
    }

    @Override
    public void close() {
        turnCosts.close();
    }

    @Override
    public long getCapacity() {
        return isOptimized() ? turnCosts.getCapacity() : tmpTCArr.length * BYTES_PER_ENTRY /*estimated minimum*/;
    }

    @Override
    public boolean loadExisting() {
        if (!turnCosts.loadExisting())
            return false;

        if (turnCosts.getHeader(0) != BYTES_PER_ENTRY)
            throw new IllegalStateException("Number of bytes per turn cost entry does not match the current configuration: " + turnCosts.getHeader(0) + " vs. " + BYTES_PER_ENTRY);
        return true;
    }

    /**
     * Sets the turn cost at the viaNode when going from "fromEdge" to "toEdge"
     * WARNING: It is tacitly assumed that for every encoder, this method is only called once per turn relation.
     * Subsequent calls for the same encoder and the same turn relation will have undefined results.
     * (The implementation below ORs the new bits into the existing bits.)
     */
    public void set(DecimalEncodedValue turnCostEnc, int fromEdge, int viaNode, int toEdge, double cost) {
        if (isOptimized())
            throw new IllegalStateException("Cannot write to TurnCostStorage if optimized");

        if (viaNode >= tmpTCArr.length) {
            if (viaNode >= baseGraph.getNodes())
                throw new IllegalStateException("Cannot set turn cost for node not in bounds " + viaNode + ", nodes: " + baseGraph.getNodes());
            tmpTCArr = Arrays.copyOf(tmpTCArr, baseGraph.getNodes());
        }

        // find position or create space for new entry
        int[] turnCostArr = tmpTCArr[viaNode];
        int tcIndexCurrent = 0;
        if (turnCostArr == null) {
            // new entry
            tmpTCArr[viaNode] = turnCostArr = new int[INTS_PER_ENTRY];
            turnCostArr[tcIndexCurrent + I_TC_FROM] = fromEdge;
            turnCostArr[tcIndexCurrent + I_TC_TO] = toEdge;
        } else {
            for (; tcIndexCurrent < turnCostArr.length; tcIndexCurrent += INTS_PER_ENTRY) {
                if (fromEdge == turnCostArr[tcIndexCurrent + I_TC_FROM] && toEdge == turnCostArr[tcIndexCurrent + I_TC_TO])
                    break;
            }
            if (tcIndexCurrent == turnCostArr.length) {
                // not found => new entry
                tmpTCArr[viaNode] = turnCostArr = Arrays.copyOf(turnCostArr, turnCostArr.length + INTS_PER_ENTRY);
                turnCostArr[tcIndexCurrent + I_TC_FROM] = fromEdge;
                turnCostArr[tcIndexCurrent + I_TC_TO] = toEdge;
            }
        }

        IntsRef tcFlags = TurnCost.createFlags();
        turnCostEnc.setDecimal(false, tcFlags, cost);
        turnCostArr[tcIndexCurrent + I_TC_FLAGS] |= tcFlags.ints[0];
    }

    /**
     * @return the turn cost of the viaNode when going from "fromEdge" to "toEdge"
     */
    public double get(DecimalEncodedValue turnCostEnc, int fromEdge, int viaNode, int toEdge) {
        IntsRef flags = readFlags(fromEdge, viaNode, toEdge);
        return turnCostEnc.getDecimal(false, flags);
    }

    private boolean isOptimized() {
        return tmpTCArr == null;
    }

    private void optimize() {
        if (isOptimized())
            return;

        int tcIndexCurrent = 0;
        NodeAccess nodeAccess = baseGraph.getNodeAccess();
        for (int viaNode = 0; viaNode < tmpTCArr.length; viaNode++) {
            int[] turnCostArr = tmpTCArr[viaNode];
            nodeAccess.setTurnCostIndex(viaNode, tcIndexCurrent);
            if (turnCostArr == null)
                continue;

            turnCosts.ensureCapacity((long) tcIndexCurrent * BYTES_PER_ENTRY + turnCostArr.length * 4);
            for (int i = 0; i < turnCostArr.length; i += INTS_PER_ENTRY, tcIndexCurrent++) {
                long tcPointer = (long) tcIndexCurrent * BYTES_PER_ENTRY;
                turnCosts.setInt(tcPointer + TC_FROM, turnCostArr[i + I_TC_FROM]);
                turnCosts.setInt(tcPointer + TC_TO, turnCostArr[i + I_TC_TO]);
                turnCosts.setInt(tcPointer + TC_FLAGS, turnCostArr[i + I_TC_FLAGS]);
            }
        }
        // last entry necessary for boundary
        turnCosts.setHeader(4, tcIndexCurrent);

        // release memory
        tmpTCArr = null;
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
        if (isOptimized()) {
            int tcFromIndex = baseGraph.getNodeAccess().getTurnCostIndex(viaNode);
            int tcToIndex = getTCToIndex(viaNode);
            for (int tcIndex = tcFromIndex; tcIndex < tcToIndex; tcIndex++) {
                if (fromEdge == turnCosts.getInt((long) tcIndex * BYTES_PER_ENTRY + TC_FROM)
                        && toEdge == turnCosts.getInt((long) tcIndex * BYTES_PER_ENTRY + TC_TO)) {
                    flags.ints[0] = turnCosts.getInt((long) tcIndex * BYTES_PER_ENTRY + TC_FLAGS);
                    break;
                }
            }
        } else {
            if (viaNode >= tmpTCArr.length)
                return flags;
            int[] turnCostArr = tmpTCArr[viaNode];
            if (turnCostArr != null) {
                for (int i = 0; i < turnCostArr.length; i += INTS_PER_ENTRY) {
                    if (fromEdge == turnCostArr[i + I_TC_FROM]
                            && toEdge == turnCostArr[i + I_TC_TO]) {
                        flags.ints[0] = turnCostArr[i + I_TC_FLAGS];
                        break;
                    }
                }
            }
        }
        return flags;
    }

    /**
     * turn cost index could be increased beyond node counts but we do not want that graph.getNodes() is changed so access to this value is a bit tricky.
     */
    private int getTCToIndex(int viaNode) {
        return viaNode + 1 == baseGraph.getNodes() ? turnCosts.getHeader(4) : baseGraph.getNodeAccess().getTurnCostIndex(viaNode + 1);
    }

    public TurnCostStorage copyTo(TurnCostStorage turnCostStorage) {
        if (!isOptimized())
            throw new IllegalStateException("Call optimize() before calling copyTo() or implement copyTo() before calling optimize()");
        turnCosts.copyTo(turnCostStorage.turnCosts);
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

    /**
     * Returns an iterator over all entries.
     *
     * @return an iterator over all entries.
     */
    public TurnRelationIterator getAllTurnRelations() {
        return isOptimized() ? new IterDataAccess() : new Iter();
    }

    public interface TurnRelationIterator {
        int getFromEdge();

        int getViaNode();

        int getToEdge();

        double getCost(DecimalEncodedValue encodedValue);

        boolean next();
    }

    private class IterDataAccess implements TurnRelationIterator {
        private int viaNode = -1;
        private int tcIndexEnd = 0;
        private int tcIndexCurrent = -1;
        private IntsRef intsRef = TurnCost.createFlags();

        private long turnCostPtr() {
            return (long) tcIndexCurrent * BYTES_PER_ENTRY;
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
            tcIndexCurrent++;
            while (tcIndexCurrent >= tcIndexEnd) {
                if (!nextNode())
                    return false;
            }
            return true;
        }

        private boolean nextNode() {
            viaNode++;
            if (viaNode >= baseGraph.getNodes())
                return false;

            tcIndexCurrent = tcIndexEnd;
            tcIndexEnd = getTCToIndex(viaNode);
            return true;
        }
    }

    // when read happen before freeze we need a second version for the in-memory array
    private class Iter implements TurnRelationIterator {
        private int viaNode = -1;
        private int[] tcArr;
        private int tcIndexCurrent = -1;
        private IntsRef intsRef = TurnCost.createFlags();

        @Override
        public int getFromEdge() {
            return tcArr[tcIndexCurrent + I_TC_FROM];
        }

        @Override
        public int getViaNode() {
            return viaNode;
        }

        @Override
        public int getToEdge() {
            return tcArr[tcIndexCurrent + I_TC_TO];
        }

        @Override
        public double getCost(DecimalEncodedValue encodedValue) {
            intsRef.ints[0] = tcArr[tcIndexCurrent + I_TC_FLAGS];
            return encodedValue.getDecimal(false, intsRef);
        }

        @Override
        public boolean next() {
            if (tcArr != null) {
                tcIndexCurrent += INTS_PER_ENTRY;
                if (tcIndexCurrent < tcArr.length)
                    return true;
            }
            tcIndexCurrent = 0;
            do {
                if (!nextNode())
                    return false;
            } while (tcArr == null);
            return true;
        }

        private boolean nextNode() {
            viaNode++;
            if (viaNode >= tmpTCArr.length)
                return false;

            tcArr = tmpTCArr[viaNode];
            return true;
        }
    }

}

