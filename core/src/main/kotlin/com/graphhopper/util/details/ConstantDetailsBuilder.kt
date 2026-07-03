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

import com.graphhopper.coll.MapEntry
import com.graphhopper.util.EdgeIteratorState

/**
 * Simply returns the same value everywhere, useful to represent values that are the same between two (via-)points
 */
class ConstantDetailsBuilder(name: String, private val value: Any?) : AbstractPathDetailsBuilder(name) {

    private var firstEdge = true
    private var lastIndex = -1

    override fun getCurrentValue(): Any? = value

    override fun isEdgeDifferentToLastEdge(edge: EdgeIteratorState): Boolean {
        if (firstEdge) {
            firstEdge = false
            return true
        } else
            return false
    }

    override fun endInterval(lastIndex: Int) {
        this.lastIndex = lastIndex
        super.endInterval(lastIndex)
    }

    override fun build(): Map.Entry<String, List<PathDetail>> {
        if (firstEdge) {
            // #2915 if there was no edge at all we need to add a single entry manually here
            // #3007 we need to set the value but also the (empty) interval (first/last)
            val p = PathDetail(value)
            p.first = lastIndex
            p.last = lastIndex
            return MapEntry(name, arrayListOf(p))
        }
        return super.build()
    }
}
