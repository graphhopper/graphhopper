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
package com.graphhopper.core.util.details;

import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.core.util.EdgeIterator;
import com.graphhopper.core.util.EdgeIteratorState;
import com.graphhopper.core.util.GHUtility;

import static com.graphhopper.util.Parameters.Details.WEIGHT;

/**
 * Calculate the weight segments for a Path
 *
 * @author Peter Karich
 */
public class WeightDetails extends AbstractPathDetailsBuilder {

    private final Weighting weighting;
    private int edgeId = EdgeIterator.NO_EDGE;
    private Double weight;

    public WeightDetails(Weighting weighting) {
        super(WEIGHT);
        this.weighting = weighting;
    }

    @Override
    public boolean isEdgeDifferentToLastEdge(EdgeIteratorState edge) {
        if (edge.getEdge() != edgeId) {
            edgeId = edge.getEdge();
            weight = GHUtility.calcWeightWithTurnWeightWithAccess(weighting, edge, false, edgeId);
            return true;
        }
        return false;
    }

    @Override
    public Object getCurrentValue() {
        return this.weight;
    }
}
