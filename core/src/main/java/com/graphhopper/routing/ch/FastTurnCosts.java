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
import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.EdgeIntAccess;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.TurnCostStorage;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;

public class FastTurnCosts {
    private final int[] viaNodeIndices;
    private final IntArrayList fromEdges;
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

    public FastTurnCosts(BaseGraph graph) {
        System.out.println("building fast turn costs");
        viaNodeIndices = new int[graph.getNodes() + 1];
        fromEdges = new IntArrayList();
        toEdges = new IntArrayList();
        turnCostFlags = new IntArrayList();
        EdgeExplorer explorer1 = graph.createEdgeExplorer();
        EdgeExplorer explorer2 = graph.createEdgeExplorer();
        for (int node = 0; node < graph.getNodes(); node++) {
            viaNodeIndices[node + 1] = viaNodeIndices[node];
            EdgeIterator fromIter = explorer1.setBaseNode(node);
            while (fromIter.next()) {
                EdgeIterator toIter = explorer2.setBaseNode(node);
                while (toIter.next()) {
                    int flags = graph.getTurnCostStorage().getFlags(fromIter.getEdge(), node, toIter.getEdge());
                    if (flags != 0) {
                        fromEdges.add(fromIter.getEdge());
                        toEdges.add(toIter.getEdge());
                        turnCostFlags.add(flags);
                        viaNodeIndices[node + 1]++;
                    }
                }
            }
        }

        // consistency check
        TurnCostStorage.Iterator itr = graph.getTurnCostStorage().getAllTurnCosts();
        while (itr.next()) {
            int flags = itr.getFlags();
            int fastFlags = getFlags(itr.getFromEdge(), itr.getViaNode(), itr.getToEdge());
//            System.out.println(flags + " " + fastFlags);
            if (flags != fastFlags)
                throw new RuntimeException();
        }
    }

    public boolean get(BooleanEncodedValue turnCostEnc, int fromEdge, int viaNode, int toEdge) {
        int flags = getFlags(fromEdge, viaNode, toEdge);
        return flags != 0 && turnCostEnc.getBool(false, flags, edgeIntAccess);
    }

    private int getFlags(int fromEdge, int viaNode, int toEdge) {
        int idx = viaNodeIndices[viaNode];
        int end = viaNodeIndices[viaNode + 1];
        // todo: we could sort by from/toEdge and do binary search here
        for (int i = idx; i < end; i++) {
            if (fromEdges.get(i) == fromEdge && toEdges.get(i) == toEdge)
                return turnCostFlags.get(i);
        }
        return 0;
    }

}
