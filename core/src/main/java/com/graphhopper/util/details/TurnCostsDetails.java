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
package com.graphhopper.util.details;

import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.util.EdgeIteratorState;

public class TurnCostsDetails extends AbstractPathDetailsBuilder {

    private Weighting weighting;
    private EdgeIteratorState prevEdge;
    private Double costs = null;

    public TurnCostsDetails(Weighting weighting) {
        super("turn_costs");

        this.weighting = weighting;
    }

    @Override
    public boolean isEdgeDifferentToLastEdge(EdgeIteratorState edge) {
        final Double currCosts;
        if (prevEdge != null && prevEdge != edge) {
            currCosts = Math.round(weighting.calcTurnMillis(prevEdge.getEdge(), prevEdge.getAdjNode(), edge.getEdge()) / 100d) / 10d;
        } else {
            currCosts = 0d;
        }

        prevEdge = edge;
        costs = currCosts;
        return true;
    }

    @Override
    protected Object getCurrentValue() {
        return costs;
    }
}
