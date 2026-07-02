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
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * This class allows to store distinct values via an enum. I.e. it stores just the indices
 */
class EnumEncodedValue<E : Enum<*>> : IntEncodedValueImpl {
    @field:JsonIgnore
    private val arr: Array<E>

    // needed for Jackson (the field name is part of the storage format — do not rename!)
    val enumType: Class<E>

    constructor(name: String, enumType: Class<E>) : this(name, enumType, false)

    constructor(name: String, enumType: Class<E>, storeTwoDirections: Boolean) :
            super(name, 32 - Integer.numberOfLeadingZeros(enumType.enumConstants.size - 1), storeTwoDirections) {
        this.enumType = enumType
        arr = enumType.enumConstants
    }

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    internal constructor(@JsonProperty("name") name: String,
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
                         @JsonProperty("bwd_mask") bwdMask: Int,
                         @JsonProperty("enum_type") enumType: Class<E>) :
            super(name, bits, minStorableValue, maxStorableValue, maxValue, negateReverseDirection, storeTwoDirections,
                    fwdDataIndex, bwdDataIndex, fwdShift, bwdShift, fwdMask, bwdMask) {
        // we need this constructor for Jackson
        this.enumType = enumType
        arr = enumType.enumConstants
    }

    fun getValues(): Array<E> = arr

    fun setEnum(reverse: Boolean, edgeId: Int, edgeIntAccess: EdgeIntAccess, value: E) {
        val intValue = value.ordinal
        super.setInt(reverse, edgeId, edgeIntAccess, intValue)
    }

    fun getEnum(reverse: Boolean, edgeId: Int, edgeIntAccess: EdgeIntAccess): E {
        val value = super.getInt(reverse, edgeId, edgeIntAccess)
        return arr[value]
    }
}
