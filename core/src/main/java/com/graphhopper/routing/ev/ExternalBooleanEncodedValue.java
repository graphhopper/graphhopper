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

import com.carrotsearch.hppc.BitSet;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Works like a normal encoded value, but the underlying data is not stored within the graph
 */
public class ExternalBooleanEncodedValue implements BooleanEncodedValue {
    private final String name;
    private final boolean storeTwoDirections;
    private final BitSet bits;

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public ExternalBooleanEncodedValue(
            @JsonProperty("name") String name,
            @JsonProperty("store_two_directions") boolean storeTwoDirections
    ) {
        this.name = name;
        this.storeTwoDirections = storeTwoDirections;
        this.bits = new BitSet();
    }

    @Override
    public void setBool(boolean reverse, int edgeId, EdgeIntAccess edgeIntAccess, boolean value) {
        // it'll grow as we go
        if (value) bits.set(getIndex(edgeId, reverse));
        else bits.clear(getIndex(edgeId, reverse));
    }

    @Override
    public boolean getBool(boolean reverse, int edgeId, EdgeIntAccess edgeIntAccess) {
        return bits.get(getIndex(edgeId, reverse));
    }

    private long getIndex(int edgeId, boolean reverse) {
        return storeTwoDirections ? (2L * edgeId + (reverse ? 1 : 0)) : edgeId;
    }

    @Override
    public int init(InitializerConfig init) {
        return 0;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean isStoreTwoDirections() {
        return storeTwoDirections;
    }
}
