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

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * This class implements a simple boolean storage via an UnsignedIntEncodedValue with 1 bit.
 */
class SimpleBooleanEncodedValue : IntEncodedValueImpl, BooleanEncodedValue {
    constructor(name: String) : this(name, false)

    constructor(name: String, storeBothDirections: Boolean) : super(name, 1, storeBothDirections)

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    internal constructor(
            @JsonProperty("name") name: String,
            @JsonProperty("bits") bits: Int,
            @JsonProperty("min_storable_value") minStorableValue: Int,
            @JsonProperty("max_storable_value") maxStorableValue: Int,
            @JsonProperty("max_value") maxValue: Int,
            @JsonProperty("negate_reverse_direction") negateReverseDirection: Boolean,
            @JsonProperty("store_two_directions") storeTwoDirections: Boolean,
            @JsonProperty("fwd_data_index") fwdDataIndex: Int,
            @JsonProperty("bwd_data_index") bwdDataIndex: Int,
            @JsonProperty("fwd_shift") fwdShift: Int,
            @JsonProperty("bwd_shift") bwdShift: Int,
            @JsonProperty("fwd_mask") fwdMask: Int,
            @JsonProperty("bwd_mask") bwdMask: Int
    ) : super(name, bits, minStorableValue, maxStorableValue, maxValue, negateReverseDirection, storeTwoDirections,
            fwdDataIndex, bwdDataIndex, fwdShift, bwdShift, fwdMask, bwdMask) {
        // we need this constructor for Jackson
    }

    override fun setBool(reverse: Boolean, edgeId: Int, edgeIntAccess: EdgeIntAccess, value: Boolean) {
        setInt(reverse, edgeId, edgeIntAccess, if (value) 1 else 0)
    }

    override fun getBool(reverse: Boolean, edgeId: Int, edgeIntAccess: EdgeIntAccess): Boolean {
        return getInt(reverse, edgeId, edgeIntAccess) == 1
    }
}
