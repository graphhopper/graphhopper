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
package com.graphhopper.routing;

import com.carrotsearch.hppc.IntObjectMap;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.SPTEntry;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;

/**
 * Uses a very simple version of stall-on-demand (SOD) for CH queries to prevent exploring nodes that can not be part
 * of a shortest path. When a node that is about to be settled is stallable it is not expanded. However, no further search
 * for neighboring stallable nodes is performed (sometimes called 'aggressive' stalling in the literature). Some experimenting
 * showed that due to the overhead for such aggressive stalling the routing does not become faster, see #240.
 *
 * @author easbar
 */
public class DijkstraBidirectionCH extends DijkstraBidirectionCHNoSOD {

    public DijkstraBidirectionCH(Graph graph, Weighting weighting) {
        super(graph, weighting);
    }

    @Override
    protected boolean fromEntryCanBeSkipped() {
        return entryIsStallable(currFrom, bestWeightMapFrom, inEdgeExplorer, false);
    }

    @Override
    protected boolean toEntryCanBeSkipped() {
        return entryIsStallable(currTo, bestWeightMapTo, outEdgeExplorer, true);
    }

    @Override
    public String getName() {
        return "dijkstrabi|ch";
    }

    @Override
    public String toString() {
        return getName() + "|" + weighting;
    }

    private boolean entryIsStallable(SPTEntry entry, IntObjectMap<SPTEntry> bestWeightMap, EdgeExplorer edgeExplorer,
                                     boolean reverse) {
        // We check for all 'incoming' edges if we can prove that the current node (that is about to be settled) is 
        // reached via a suboptimal path. We do this regardless of the CH level of the adjacent nodes.
        EdgeIterator iter = edgeExplorer.setBaseNode(entry.adjNode);
        while (iter.next()) {
            // no need to inspect the edge we are coming from
            if (iter.getEdge() == entry.edge) {
                continue;
            }
            int traversalId = traversalMode.createTraversalId(iter, reverse);
            SPTEntry adjNode = bestWeightMap.get(traversalId);
            // we have to be careful because of rounded shortcut weights in combination with virtual via nodes, see #1574
            final double precision = 0.001;
            if (adjNode != null &&
                    adjNode.weight + weighting.calcWeight(iter, !reverse, getIncomingEdge(entry)) - entry.weight < -precision) {
                return true;
            }
        }
        return false;
    }
}
