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
 * An EncodedValue that represents a key-value tag stored in KVStorage. Unlike other EncodedValues,
 * this does not use any bits in the edge flags. Instead, it holds a pre-resolved key index for fast
 * KVStorage lookups, avoiding the HashMap lookup on every edge access.
 */
public class KVStorageEncodedValue implements EncodedValue {
    private final String name;
    private int keyIndex = -1;

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public KVStorageEncodedValue(@JsonProperty("name") String name) {
        this.name = name;
    }

    @Override
    public int init(InitializerConfig init) {
        // no bits needed in edge flags
        return 0;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean isStoreTwoDirections() {
        return true;
    }

    public int getKeyIndex() {
        return keyIndex;
    }

    public void setKeyIndex(int keyIndex) {
        this.keyIndex = keyIndex;
    }
}
