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
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * This class implements a simple boolean storage via an UnsignedIntEncodedValue with 1 bit.
 */
public final class SimpleBooleanEncodedValue extends IntEncodedValueImpl implements BooleanEncodedValue {
    public SimpleBooleanEncodedValue(String name) {
        this(name, false);
    }

    public SimpleBooleanEncodedValue(String name, boolean storeBothDirections) {
        super(name, 1, storeBothDirections);
    }

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    SimpleBooleanEncodedValue(
            @JsonProperty("name") String name,
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
            @JsonProperty("bwd_mask") int bwdMask
    ) {
        // we need this constructor for Jackson
        super(name, bits, minStorableValue, maxStorableValue, maxValue, negateReverseDirection, storeTwoDirections, fwdDataIndex, bwdDataIndex, fwdShift, bwdShift, fwdMask, bwdMask);
    }

    @Override
    public final void setBool(boolean reverse, int edgeId, EdgeIntAccess edgeIntAccess, boolean value) {
        setInt(reverse, edgeId, edgeIntAccess, value ? 1 : 0);
    }

    @Override
    public final boolean getBool(boolean reverse, int edgeId, EdgeIntAccess edgeIntAccess) {
        return getInt(reverse, edgeId, edgeIntAccess) == 1;
    }
}
