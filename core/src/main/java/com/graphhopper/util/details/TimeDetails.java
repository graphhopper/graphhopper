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

import static com.graphhopper.core.util.Parameters.Details.TIME;

/**
 * Calculate the time segments for a Path
 *
 * @author Robin Boldt
 */
public class TimeDetails extends AbstractPathDetailsBuilder {

    private final Weighting weighting;
    private int prevEdgeId = EdgeIterator.NO_EDGE;
    // will include the turn time penalty
    private long time = 0;

    public TimeDetails(Weighting weighting) {
        super(TIME);
        this.weighting = weighting;
    }

    @Override
    public boolean isEdgeDifferentToLastEdge(EdgeIteratorState edge) {
        time = GHUtility.calcMillisWithTurnMillis(weighting, edge, false, prevEdgeId);
        prevEdgeId = edge.getEdge();
        return true;
    }

    @Override
    public Object getCurrentValue() {
        return this.time;
    }
}
