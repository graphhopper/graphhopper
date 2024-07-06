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

import com.carrotsearch.hppc.IntArrayList;
import com.carrotsearch.hppc.LongIntMap;
import com.carrotsearch.hppc.LongIntScatterMap;
import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.EdgeIntAccess;
import com.graphhopper.routing.util.AllEdgesIterator;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.TurnCostStorage;
import com.graphhopper.util.BitUtil;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;

public class FastTurnCosts {
    private final int[] fromEdgeIndices;
    private final IntArrayList toEdges;
    private final IntArrayList turnCostFlags;
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
    private final LongIntMap turnCosts = new LongIntScatterMap();

    public FastTurnCosts(BaseGraph graph) {
        System.out.println("building fast turn costs");
        fromEdgeIndices = new int[graph.getEdges() + 1];
        toEdges = new IntArrayList();
        turnCostFlags = new IntArrayList();
        EdgeExplorer explorer = graph.createEdgeExplorer();
        AllEdgesIterator iter = graph.getAllEdges();
        while (iter.next()) {
            fromEdgeIndices[iter.getEdge() + 1] = fromEdgeIndices[iter.getEdge()];
            EdgeIterator it = explorer.setBaseNode(iter.getAdjNode());
            while (it.next()) {
                int flags = graph.getTurnCostStorage().getFlags(iter.getEdge(), iter.getAdjNode(), it.getEdge());
                if (flags != 0) {
                    toEdges.add(it.getEdge());
                    turnCostFlags.add(flags);
                    fromEdgeIndices[iter.getEdge() + 1]++;
                }
            }
            // todo: with this storage we cannot distinguish turn costs a-A-a from a-B-a (for an edge A-(a)-B)!! But right now we just want to see how fast this is
            //       if we wanted to actually use this we need the edge keys
            it = explorer.setBaseNode(iter.getBaseNode());
            while (it.next()) {
                int flags = graph.getTurnCostStorage().getFlags(iter.getEdge(), iter.getBaseNode(), it.getEdge());
                if (flags != 0) {
                    toEdges.add(it.getEdge());
                    turnCostFlags.add(flags);
                    fromEdgeIndices[iter.getEdge() + 1]++;
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

        itr = graph.getTurnCostStorage().getAllTurnCosts();
        while (itr.next())
            turnCosts.put(BitUtil.LITTLE.toLong(itr.getFromEdge(), itr.getToEdge()), itr.getFlags());
    }

    public boolean get(BooleanEncodedValue turnCostEnc, int fromEdge, int toEdge) {
        int flags = getFlags(fromEdge, toEdge);
        return flags != 0 && turnCostEnc.getBool(false, flags, edgeIntAccess);
    }

    private int getFlags(int fromEdge, int toEdge) {
        return turnCosts.getOrDefault(BitUtil.LITTLE.toLong(fromEdge, toEdge), 0);
//        int idx = fromEdgeIndices[fromEdge];
//        int end = fromEdgeIndices[fromEdge + 1];
//        for (int i = idx; i < end; i++) {
//            if (toEdges.get(i) == toEdge)
//                return turnCostFlags.get(i);
//        }
//        return 0;
    }

}
