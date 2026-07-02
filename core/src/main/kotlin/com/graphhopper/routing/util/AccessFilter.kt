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
package com.graphhopper.routing.util

import com.graphhopper.routing.ev.BooleanEncodedValue
import com.graphhopper.util.EdgeIteratorState

/**
 * @author Peter Karich
 */
class AccessFilter private constructor(
    val accessEnc: BooleanEncodedValue,
    private val fwd: Boolean,
    private val bwd: Boolean
) : EdgeFilter {

    override fun accept(edgeState: EdgeIteratorState): Boolean =
        fwd && edgeState.get(accessEnc) || bwd && edgeState.getReverse(accessEnc)

    override fun toString(): String = "$accessEnc, bwd:$bwd, fwd:$fwd"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false

        other as AccessFilter

        if (bwd != other.bwd) return false
        if (fwd != other.fwd) return false
        return accessEnc == other.accessEnc
    }

    override fun hashCode(): Int {
        var result = if (bwd) 1 else 0
        result = 31 * result + if (fwd) 1 else 0
        result = 31 * result + accessEnc.hashCode()
        return result
    }

    companion object {
        @JvmStatic
        fun outEdges(accessEnc: BooleanEncodedValue): AccessFilter = AccessFilter(accessEnc, true, false)

        @JvmStatic
        fun inEdges(accessEnc: BooleanEncodedValue): AccessFilter = AccessFilter(accessEnc, false, true)

        /**
         * Accepts all edges that are either forward or backward according to the given accessEnc.
         * Edges where neither one of the flags is enabled will still not be accepted. If you need to retrieve all edges
         * regardless of their encoding use [EdgeFilter.ALL_EDGES] instead.
         */
        @JvmStatic
        fun allEdges(accessEnc: BooleanEncodedValue): AccessFilter = AccessFilter(accessEnc, true, true)
    }
}
