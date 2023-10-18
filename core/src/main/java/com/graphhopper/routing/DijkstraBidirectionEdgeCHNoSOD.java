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

import com.graphhopper.routing.ch.CHEntry;
import com.graphhopper.storage.RoutingCHGraph;

/**
 * @author easbar
 */
public class DijkstraBidirectionEdgeCHNoSOD extends AbstractBidirectionEdgeCHNoSOD {
    public DijkstraBidirectionEdgeCHNoSOD(RoutingCHGraph graph) {
        super(graph);
    }

    @Override
    protected CHEntry createStartEntry(int node, double weight, boolean reverse) {
        return new CHEntry(node, weight);
    }

    @Override
    protected CHEntry createEntry(int edge, int adjNode, int incEdge, double weight, SPTEntry parent, boolean reverse) {
        return new CHEntry(edge, incEdge, adjNode, weight, parent);
    }

    @Override
    protected void updateEntry(SPTEntry entry, int edge, int adjNode, int incEdge, double weight, SPTEntry parent, boolean reverse) {
        assert entry.adjNode == adjNode;
        entry.edge = edge;
        ((CHEntry) entry).incEdge = incEdge;
        entry.weight = weight;
        entry.parent = parent;
    }

    @Override
    public String getName() {
        return "dijkstrabi|ch|edge_based|no_sod";
    }

}
