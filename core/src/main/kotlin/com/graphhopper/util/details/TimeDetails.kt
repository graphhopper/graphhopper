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
package com.graphhopper.util.details

import com.graphhopper.routing.weighting.Weighting
import com.graphhopper.util.EdgeIterator
import com.graphhopper.util.EdgeIteratorState
import com.graphhopper.util.GHUtility
import com.graphhopper.util.Parameters.Details.TIME

/**
 * Calculate the time segments for a Path
 *
 * @author Robin Boldt
 */
class TimeDetails(private val weighting: Weighting) : AbstractPathDetailsBuilder(TIME) {

    private var prevEdgeId = EdgeIterator.NO_EDGE

    // will include the turn time penalty
    private var time: Long = 0

    override fun isEdgeDifferentToLastEdge(edge: EdgeIteratorState): Boolean {
        time = GHUtility.calcMillisWithTurnMillis(weighting, edge, false, prevEdgeId)
        prevEdgeId = edge.edge
        return true
    }

    public override fun getCurrentValue(): Any = time
}
