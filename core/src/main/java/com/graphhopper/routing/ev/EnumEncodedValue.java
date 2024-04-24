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
package com.graphhopper.routing.ev;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * This class allows to store distinct values via an enum. I.e. it stores just the indices
 */
public final class EnumEncodedValue<E extends Enum> extends IntEncodedValueImpl {
    @JsonIgnore
    private final E[] arr;
    // needed for Jackson
    private final Class<E> enumType;

    public EnumEncodedValue(String name, Class<E> enumType) {
        this(name, enumType, false);
    }

    public EnumEncodedValue(String name, Class<E> enumType, boolean storeTwoDirections) {
        super(name, 32 - Integer.numberOfLeadingZeros(enumType.getEnumConstants().length - 1), storeTwoDirections);
        this.enumType = enumType;
        arr = enumType.getEnumConstants();
    }

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    EnumEncodedValue(@JsonProperty("name") String name,
                     @JsonProperty("bits") int bits,
                     @JsonProperty("min_storable_value") int minStorableValue,
                     @JsonProperty("max_storable_value") int maxStorableValue,
                     @JsonProperty("max_value") int maxValue,
                     @JsonProperty("negate_reverse_direction") boolean negateReverseDirection,
                     @JsonProperty("store_two_directions") boolean storeTwoDirections,
                     @JsonProperty("fwd_data_index") int fwdDataIndex,
                     @JsonProperty("bwd_data_index") int bwdDataIndex,
                     @JsonProperty("fwd_shift") int fwdShift,
                     @JsonProperty("bwd_shift") int bwdShift,
                     @JsonProperty("fwd_mask") int fwdMask,
                     @JsonProperty("bwd_mask") int bwdMask,
                     @JsonProperty("enum_type") Class<E> enumType) {
        // we need this constructor for Jackson
        super(name, bits, minStorableValue, maxStorableValue, maxValue, negateReverseDirection, storeTwoDirections, fwdDataIndex, bwdDataIndex, fwdShift, bwdShift, fwdMask, bwdMask);
        this.enumType = enumType;
        arr = enumType.getEnumConstants();
    }

    public Class<E> getEnumType() {
        return enumType;
    }

    public E[] getValues() {
        return arr;
    }

    public void setEnum(boolean reverse, int edgeId, EdgeBytesAccess edgeAccess, E value) {
        int intValue = value.ordinal();
        super.setInt(reverse, edgeId, edgeAccess, intValue);
    }

    public E getEnum(boolean reverse, int edgeId, EdgeBytesAccess edgeAccess) {
        int value = super.getInt(reverse, edgeId, edgeAccess);
        return arr[value];
    }

}
