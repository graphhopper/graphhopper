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

import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.storage.RoutingCHGraph;

public class DijkstraBidirectionCHNoSOD extends AbstractBidirCHAlgo {
    public DijkstraBidirectionCHNoSOD(RoutingCHGraph graph) {
        super(graph, TraversalMode.NODE_BASED);
    }

    @Override
    protected SPTEntry createStartEntry(int node, double weight, boolean reverse) {
        return new SPTEntry(node, weight);
    }

    @Override
    protected SPTEntry createEntry(int edge, int adjNode, int incEdge, double weight, SPTEntry parent, boolean reverse) {
        return new SPTEntry(edge, adjNode, weight, parent);
    }

    protected SPTEntry getParent(SPTEntry entry) {
        return entry.getParent();
    }

    @Override
    public String getName() {
        return "dijkstrabi|ch|no_sod";
    }

}
