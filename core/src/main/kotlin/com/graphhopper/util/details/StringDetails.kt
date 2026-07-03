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

import com.graphhopper.routing.ev.StringEncodedValue
import com.graphhopper.util.EdgeIteratorState

class StringDetails(name: String, private val ev: StringEncodedValue) : AbstractPathDetailsBuilder(name) {

    private var currentVal: String? = null

    override fun getCurrentValue(): Any? = currentVal

    override fun isEdgeDifferentToLastEdge(edge: EdgeIteratorState): Boolean {
        // !! throws an NPE for a null value, just like value.equals(...) did in Java
        val value: String = edge.get(ev)!!
        if (value != currentVal) {
            this.currentVal = value
            return true
        }
        return false
    }
}
