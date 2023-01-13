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
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.core.util.EdgeIteratorState;
import com.graphhopper.util.Parameters;

/**
 * Calculates best path in bidirectional way.
 * <p>
 * 'Ref' stands for reference implementation and is using the normal Java-'reference'-way.
 * <p>
 *
 * @author Peter Karich
 */
public class DijkstraBidirectionRef extends AbstractNonCHBidirAlgo {
    public DijkstraBidirectionRef(Graph graph, Weighting weighting, TraversalMode tMode) {
        super(graph, weighting, tMode);
    }

    @Override
    protected SPTEntry createStartEntry(int node, double weight, boolean reverse) {
        return new SPTEntry(node, weight);
    }

    @Override
    protected SPTEntry createEntry(EdgeIteratorState edge, double weight, SPTEntry parent, boolean reverse) {
        return new SPTEntry(edge.getEdge(), edge.getAdjNode(), weight, parent);
    }

    protected SPTEntry getParent(SPTEntry entry) {
        return entry.getParent();
    }

    @Override
    public String getName() {
        return Parameters.Algorithms.DIJKSTRA_BI;
    }
}
