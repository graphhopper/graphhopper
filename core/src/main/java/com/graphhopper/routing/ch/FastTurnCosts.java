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

package com.graphhopper.routing.ch;

import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.EdgeIntAccess;
import com.graphhopper.routing.util.AllEdgesIterator;
import com.graphhopper.storage.*;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;

public class FastTurnCosts {
    private final DataAccess fromEdgeIndices;
    private final DataAccess turnCostEntries;
    private final EdgeIntAccess edgeIntAccess = new EdgeIntAccess() {
        @Override
        public void setInt(int edgeId, int index, int value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getInt(int edgeId, int index) {
            return edgeId;
        }
    };

    public FastTurnCosts(BaseGraph graph) {
        System.out.println("building fast turn costs");
        fromEdgeIndices = new RAMDirectory().create("from_edge_indices", DAType.RAM_INT).create(42);
        fromEdgeIndices.ensureCapacity(4L * (graph.getEdges() + 1));
        turnCostEntries = new RAMDirectory().create("turn_cost_entries", DAType.RAM_INT).create(42);
        EdgeExplorer explorer = graph.createEdgeExplorer();
        AllEdgesIterator iter = graph.getAllEdges();
        int count = 0;
        while (iter.next()) {
            fromEdgeIndices.setInt(4L * (iter.getEdge() + 1), fromEdgeIndices.getInt(4L * iter.getEdge()));
            EdgeIterator it = explorer.setBaseNode(iter.getAdjNode());
            while (it.next()) {
                int flags = graph.getTurnCostStorage().getFlags(iter.getEdge(), iter.getAdjNode(), it.getEdge());
                if (flags != 0) {
                    turnCostEntries.ensureCapacity(8L * (count + 1));
                    turnCostEntries.setInt(8L * count, it.getEdge());
                    turnCostEntries.setInt(8L * count + 4L, flags);
                    count++;
                    fromEdgeIndices.setInt(4L * (iter.getEdge() + 1), fromEdgeIndices.getInt(4L * (iter.getEdge() + 1)) + 1);
                }
            }
            // todo: with this storage we cannot distinguish turn costs a-A-a from a-B-a (for an edge A-(a)-B)!! But right now we just want to see how fast this is
            //       if we wanted to actually use this we need the edge keys
            it = explorer.setBaseNode(iter.getBaseNode());
            while (it.next()) {
                int flags = graph.getTurnCostStorage().getFlags(iter.getEdge(), iter.getBaseNode(), it.getEdge());
                if (flags != 0) {
                    turnCostEntries.ensureCapacity(8L * (count + 1));
                    turnCostEntries.setInt(8L * count, it.getEdge());
                    turnCostEntries.setInt(8L * count + 4L, flags);
                    count++;
                    fromEdgeIndices.setInt(4L * (iter.getEdge() + 1), fromEdgeIndices.getInt(4L * (iter.getEdge() + 1)) + 1);
                }
            }
        }

        // consistency check
        TurnCostStorage.Iterator itr = graph.getTurnCostStorage().getAllTurnCosts();
        while (itr.next()) {
            int flags = itr.getFlags();
            int fastFlags = getFlags(itr.getFromEdge(), itr.getToEdge());
//            System.out.println(flags + " " + fastFlags);
            if (flags != fastFlags)
                throw new RuntimeException();
        }
    }

    public boolean get(BooleanEncodedValue turnCostEnc, int fromEdge, int toEdge) {
        int flags = getFlags(fromEdge, toEdge);
        return flags != 0 && turnCostEnc.getBool(false, flags, edgeIntAccess);
    }

    private int getFlags(int fromEdge, int toEdge) {
        int idx = fromEdgeIndices.getInt(4L * fromEdge);
        int end = fromEdgeIndices.getInt(4L * (fromEdge + 1));
        // todo: we could sort by toEdge and do binary search here
        for (int i = idx; i < end; i++) {
            if (turnCostEntries.getInt(8L * i) == toEdge)
                return turnCostEntries.getInt(8L * i + 4L);
        }
        return 0;
    }

}
