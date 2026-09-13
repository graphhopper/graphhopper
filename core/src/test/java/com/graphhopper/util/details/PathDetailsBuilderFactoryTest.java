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
package com.graphhopper.util.details;

import com.graphhopper.routing.ev.KVStorageEncodedValue;
import com.graphhopper.routing.ev.Surface;
import com.graphhopper.routing.util.EncodingManager;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PathDetailsBuilderFactoryTest {

    private List<PathDetailsBuilder> create(EncodingManager em, String... details) {
        return new PathDetailsBuilderFactory().createPathDetailsBuilders(List.of(details), null, em, null, null);
    }

    @Test
    void storedTagIsRequestableByItsRawKey() {
        EncodingManager em = new EncodingManager.Builder().add(new KVStorageEncodedValue("cycleway")).build();

        List<PathDetailsBuilder> builders = create(em, "cycleway");
        assertEquals(1, builders.size());
        assertEquals("cycleway", builders.get(0).getName());

        // the internal name must not be part of the API
        assertThrows(IllegalArgumentException.class, () -> create(em, "kv_cycleway"));
    }

    @Test
    void colonKeyIsRequestableToo() {
        EncodingManager em = new EncodingManager.Builder().add(new KVStorageEncodedValue("cycleway:left")).build();
        List<PathDetailsBuilder> builders = create(em, "cycleway:left");
        assertEquals(1, builders.size());
        assertEquals("cycleway:left", builders.get(0).getName());
    }

    @Test
    void encodedValueWinsOverStoredTagWithSameName() {
        EncodingManager em = new EncodingManager.Builder().add(Surface.create()).
                add(new KVStorageEncodedValue(Surface.KEY)).build();

        List<PathDetailsBuilder> builders = create(em, Surface.KEY);
        assertEquals(1, builders.size());
        assertInstanceOf(EnumDetails.class, builders.get(0));
    }

    @Test
    void streetNameIsNotAddedTwice() {
        EncodingManager em = new EncodingManager.Builder().
                add(new KVStorageEncodedValue("street_name")).build();
        List<PathDetailsBuilder> builders = create(em, "street_name");
        assertEquals(1, builders.size());
    }
}
