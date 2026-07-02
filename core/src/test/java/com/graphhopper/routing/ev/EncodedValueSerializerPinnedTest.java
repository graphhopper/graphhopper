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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the exact serialized form of the EncodedValue types NOT covered by
 * EncodedValueSerializerTest (which pins Int/Decimal/SimpleBoolean only). This JSON is the
 * stored-graph format: the serializer is FIELD-based, so backing-field names and
 * creator-parameter order ARE the format - renaming a (private!) field silently breaks loading
 * of every existing graph. Expected strings extracted from the pre-migration java
 * implementation. See docs/pinned-behavior.md.
 */
public class EncodedValueSerializerPinnedTest {

    @Test
    public void enumEncodedValueFormat() {
        String expected = "{\"className\":\"com.graphhopper.routing.ev.EnumEncodedValue\",\"name\":\"surface\"," +
                "\"bits\":4,\"min_storable_value\":0,\"max_storable_value\":15,\"max_value\":-2147483648," +
                "\"negate_reverse_direction\":false,\"store_two_directions\":false,\"fwd_data_index\":0," +
                "\"bwd_data_index\":0,\"fwd_shift\":-1,\"bwd_shift\":-1,\"fwd_mask\":0,\"bwd_mask\":0," +
                "\"enum_type\":\"com.graphhopper.routing.ev.Surface\"}";
        assertEquals(expected, EncodedValueSerializer.serializeEncodedValue(new EnumEncodedValue<>("surface", Surface.class)));
        // and the round trip restores an equivalent value
        EncodedValue ev = EncodedValueSerializer.deserializeEncodedValue(expected);
        assertEquals("surface", ev.getName());
        assertTrue(ev instanceof EnumEncodedValue);
    }

    @Test
    public void stringEncodedValueFormat() {
        String expected = "{\"className\":\"com.graphhopper.routing.ev.StringEncodedValue\",\"name\":\"kv\"," +
                "\"bits\":2,\"min_storable_value\":0,\"max_storable_value\":3,\"max_value\":-2147483648," +
                "\"negate_reverse_direction\":false,\"store_two_directions\":false,\"fwd_data_index\":0," +
                "\"bwd_data_index\":0,\"fwd_shift\":-1,\"bwd_shift\":-1,\"fwd_mask\":0,\"bwd_mask\":0," +
                "\"max_values\":3,\"values\":[],\"index_map\":{}}";
        assertEquals(expected, EncodedValueSerializer.serializeEncodedValue(new StringEncodedValue("kv", 3)));
        EncodedValue ev = EncodedValueSerializer.deserializeEncodedValue(expected);
        assertEquals("kv", ev.getName());
        assertTrue(ev instanceof StringEncodedValue);
    }

    @Test
    public void externalBooleanEncodedValueFormat() {
        // pre-existing quirk preserved from java: the hppc BitSet field is serialized too
        // (and cannot be deserialized) - see docs/pinned-behavior.md
        String expected = "{\"className\":\"com.graphhopper.routing.ev.ExternalBooleanEncodedValue\"," +
                "\"name\":\"ext\",\"store_two_directions\":true,\"bits\":{\"bits\":[0],\"wlen\":1}}";
        assertEquals(expected, EncodedValueSerializer.serializeEncodedValue(new ExternalBooleanEncodedValue("ext", true)));
    }
}
