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
package com.graphhopper.routing.ev

import com.carrotsearch.hppc.BitSet
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Works like a normal encoded value, but the underlying data is not stored within the graph
 */
open class ExternalBooleanEncodedValue @JsonCreator(mode = JsonCreator.Mode.PROPERTIES) constructor(
        @JsonProperty("name") name: String,
        @JsonProperty("store_two_directions") storeTwoDirections: Boolean
) : BooleanEncodedValue {
    // field names are part of the storage format — do not rename!
    override val name: String = name
    private val storeTwoDirections: Boolean = storeTwoDirections
    private val bits: BitSet = BitSet()

    override fun setBool(reverse: Boolean, edgeId: Int, edgeIntAccess: EdgeIntAccess, value: Boolean) {
        // it'll grow as we go
        if (value) bits.set(getIndex(edgeId, reverse))
        else bits.clear(getIndex(edgeId, reverse))
    }

    override fun getBool(reverse: Boolean, edgeId: Int, edgeIntAccess: EdgeIntAccess): Boolean {
        return bits.get(getIndex(edgeId, reverse))
    }

    private fun getIndex(edgeId: Int, reverse: Boolean): Long =
            if (storeTwoDirections) 2L * edgeId + (if (reverse) 1 else 0) else edgeId.toLong()

    override fun init(init: EncodedValue.InitializerConfig): Int = 0

    override val isStoreTwoDirections: Boolean
        get() = storeTwoDirections
}
