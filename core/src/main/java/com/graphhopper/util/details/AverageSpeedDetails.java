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
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.GHUtility;

import static com.graphhopper.util.Parameters.Details.AVERAGE_SPEED;

public class AverageSpeedDetails extends AbstractPathDetailsBuilder {

    private final Weighting weighting;
    private final double precision;
    private Double decimalValue;
    // will include the turn time penalty
    private int prevEdgeId = EdgeIterator.NO_EDGE;

    public AverageSpeedDetails(Weighting weighting) {
        this(weighting, 0.1);
    }

    /**
     * @param precision e.g. 0.1 to avoid creating too many path details, i.e. round the speed to the specified precision
     *                  before detecting a change.
     */
    public AverageSpeedDetails(Weighting weighting, double precision) {
        super(AVERAGE_SPEED);
        this.weighting = weighting;
        this.precision = precision;
    }

    @Override
    protected Object getCurrentValue() {
        return decimalValue;
    }

    @Override
    public boolean isEdgeDifferentToLastEdge(EdgeIteratorState edge) {
        // For very short edges we might not be able to calculate a proper value for speed. dividing by calcMillis can
        // even lead to an infinity speed. So, just ignore these edges, see #1848 and #2620 and #2636.
        final double distance = edge.getDistance();
        long time = GHUtility.calcMillisWithTurnMillis(weighting, edge, false, prevEdgeId);
        if (distance < 0.01 || time < 1) {
            prevEdgeId = edge.getEdge();
            if (decimalValue != null) return false;
            // in case this is the first edge we return decimalValue=null
            return true;
        }

        double speed = distance / time * 3600;
        prevEdgeId = edge.getEdge();
        if (decimalValue == null || Math.abs(speed - decimalValue) >= precision) {
            this.decimalValue = Math.round(speed / precision) * precision;
            return true;
        }
        return false;
    }
}
