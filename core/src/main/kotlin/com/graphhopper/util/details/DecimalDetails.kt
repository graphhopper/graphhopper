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

import com.graphhopper.routing.ev.DecimalEncodedValue
import com.graphhopper.util.EdgeIteratorState
import kotlin.math.abs

/**
 * @param infinityJsonValue DecimalEncodedValue can return infinity as default value, but JSON cannot include this
 *                          https://stackoverflow.com/a/9218955/194609 so we need a special string to handle this or null.
 * @param precision         e.g. 0.1 to avoid creating too many path details, i.e. round the speed to the specified precision
 *                          before detecting a change.
 */
class DecimalDetails @JvmOverloads constructor(
    name: String,
    private val ev: DecimalEncodedValue,
    private val infinityJsonValue: String? = null,
    private val precision: Double = 0.001
) : AbstractPathDetailsBuilder(name) {

    private var decimalValue: Double? = null

    override fun getCurrentValue(): Any? {
        if (decimalValue!!.isInfinite())
            return infinityJsonValue

        return decimalValue
    }

    override fun isEdgeDifferentToLastEdge(edge: EdgeIteratorState): Boolean {
        val tmpVal = edge.get(ev)
        val curValue = decimalValue
        if (curValue == null || abs(tmpVal - curValue) >= precision) {
            decimalValue = if (tmpVal.isInfinite()) tmpVal else Math.round(tmpVal / precision) * precision
            return true
        }
        return false
    }
}
