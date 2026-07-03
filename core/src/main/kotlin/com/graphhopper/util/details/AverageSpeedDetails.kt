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
import com.graphhopper.util.Parameters.Details.AVERAGE_SPEED
import kotlin.math.abs

/**
 * @param precision e.g. 0.1 to avoid creating too many path details, i.e. round the speed to the specified precision
 *                  before detecting a change.
 */
class AverageSpeedDetails @JvmOverloads constructor(
    private val weighting: Weighting,
    private val precision: Double = 0.1
) : AbstractPathDetailsBuilder(AVERAGE_SPEED) {

    private var decimalValue: Double? = null

    // will include the turn time penalty
    private var prevEdgeId = EdgeIterator.NO_EDGE

    override fun getCurrentValue(): Any? = decimalValue

    override fun isEdgeDifferentToLastEdge(edge: EdgeIteratorState): Boolean {
        // For very short edges we might not be able to calculate a proper value for speed. dividing by calcMillis can
        // even lead to an infinity speed. So, just ignore these edges, see #1848 and #2620 and #2636.
        val distance = edge.distance
        val time = GHUtility.calcMillisWithTurnMillis(weighting, edge, false, prevEdgeId)
        if (distance < 0.01 || time < 1) {
            prevEdgeId = edge.edge
            if (decimalValue != null) return false
            // in case this is the first edge we return decimalValue=null
            return true
        }

        val speed = distance / time * 3600
        prevEdgeId = edge.edge
        val curValue = decimalValue
        if (curValue == null || abs(speed - curValue) >= precision) {
            decimalValue = Math.round(speed / precision) * precision
            return true
        }
        return false
    }
}
