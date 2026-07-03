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

import com.graphhopper.routing.ev.EnumEncodedValue
import com.graphhopper.util.EdgeIteratorState

class EnumDetails<E : Enum<*>>(name: String, private val ev: EnumEncodedValue<E>) : AbstractPathDetailsBuilder(name) {

    private var objVal: E? = null

    override fun getCurrentValue(): Any = objVal!!.toString()

    override fun isEdgeDifferentToLastEdge(edge: EdgeIteratorState): Boolean {
        val value = edge.get(ev)
        // we can use the reference equality here
        if (value !== objVal) {
            this.objVal = value
            return true
        }
        return false
    }
}
