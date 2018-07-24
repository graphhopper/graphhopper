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
package com.graphhopper.routing.weighting;

import com.carrotsearch.hppc.IntSet;
import com.graphhopper.coll.GHIntHashSet;
import com.graphhopper.util.EdgeIteratorState;

import java.util.Collection;

/**
 * Rates already used Paths worse.
 *
 * @author Robin Boldt
 */
public class AvoidEdgesWeighting extends AbstractAdjustedWeighting {
    // contains the edge IDs of the already visited edges
    protected final IntSet visitedEdges = new GHIntHashSet();

    private double edgePenaltyFactor = 5.0;

    public AvoidEdgesWeighting(Weighting superWeighting) {
        super(superWeighting);
    }

    public AvoidEdgesWeighting setEdgePenaltyFactor(double edgePenaltyFactor) {
        this.edgePenaltyFactor = edgePenaltyFactor;
        return this;
    }

    /**
     * This method adds the specified path to this weighting which should be penalized in the
     * calcWeight method.
     */
    public void addEdges(Collection<EdgeIteratorState> edges) {
        for (EdgeIteratorState edge : edges) {
            visitedEdges.add(edge.getEdge());
        }
    }

    @Override
    public double getMinWeight(double distance) {
        return superWeighting.getMinWeight(distance);
    }

    @Override
    public double calcWeight(EdgeIteratorState edgeState, boolean reverse, int prevOrNextEdgeId) {
        double weight = superWeighting.calcWeight(edgeState, reverse, prevOrNextEdgeId);
        if (visitedEdges.contains(edgeState.getEdge()))
            return weight * edgePenaltyFactor;

        return weight;
    }

    @Override
    public String getName() {
        return "avoid_edges";
    }
}
