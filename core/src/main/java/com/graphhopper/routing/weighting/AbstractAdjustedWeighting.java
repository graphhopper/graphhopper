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

import com.graphhopper.util.EdgeIteratorState;

/**
 * The AdjustedWeighting wraps another Weighting.
 *
 * @author Robin Boldt
 */
public abstract class AbstractAdjustedWeighting implements Weighting {
    protected final Weighting superWeighting;

    public AbstractAdjustedWeighting(Weighting superWeighting) {
        if (superWeighting == null)
            throw new IllegalArgumentException("No super weighting set");
        this.superWeighting = superWeighting;
    }

    @Override
    public double getMinWeight(double distance) {
        return superWeighting.getMinWeight(distance);
    }

    @Override
    public boolean edgeHasNoAccess(EdgeIteratorState edgeState, boolean reverse) {
        return superWeighting.edgeHasNoAccess(edgeState, reverse);
    }

    @Override
    public double calcEdgeWeightWithAccess(EdgeIteratorState edgeState, boolean reverse) {
        return superWeighting.calcEdgeWeightWithAccess(edgeState, reverse);
    }

    @Override
    public long calcEdgeMillis(EdgeIteratorState edgeState, boolean reverse) {
        return superWeighting.calcEdgeMillis(edgeState, reverse);
    }

    @Override
    public double calcTurnWeight(int inEdge, int viaNode, int outEdge) {
        return superWeighting.calcTurnWeight(inEdge, viaNode, outEdge);
    }

    @Override
    public long calcTurnMillis(int inEdge, int viaNode, int outEdge) {
        return superWeighting.calcTurnMillis(inEdge, viaNode, outEdge);
    }

    @Override
    public boolean hasTurnCosts() {
        return superWeighting.hasTurnCosts();
    }

    @Override
    public String toString() {
        return getName() + "|" + superWeighting.toString();
    }
}
