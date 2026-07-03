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
package com.graphhopper.coll;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class GrowableBitSetTest {

    @Test
    public void testSetGetClear() {
        GrowableBitSet bitSet = new GrowableBitSet();
        assertFalse(bitSet.get(0));
        assertFalse(bitSet.get(100_000L));
        bitSet.set(7);
        bitSet.set(63);
        bitSet.set(64);
        assertTrue(bitSet.get(7));
        assertTrue(bitSet.get(63));
        assertTrue(bitSet.get(64));
        assertFalse(bitSet.get(8));
        assertEquals(3, bitSet.cardinality());
        bitSet.clear(63);
        assertFalse(bitSet.get(63));
        assertEquals(2, bitSet.cardinality());
        bitSet.clear();
        assertEquals(0, bitSet.cardinality());
        assertTrue(bitSet.isEmpty());
    }

    @Test
    public void testGrowsBeyondInitialCapacity() {
        GrowableBitSet bitSet = new GrowableBitSet(8);
        assertEquals(1, bitSet.wlen);
        bitSet.set(1_000_000L);
        assertTrue(bitSet.get(1_000_000L));
        assertFalse(bitSet.get(999_999L));
        assertEquals(1, bitSet.cardinality());
        // wlen reflects the highest word in use, like hppc's BitSet
        assertEquals(1_000_000 / 64 + 1, bitSet.wlen);
        assertEquals(1_000_001, bitSet.length());
    }

    @Test
    public void testRangeOps() {
        GrowableBitSet bitSet = new GrowableBitSet(64);
        bitSet.set(10, 200);
        assertEquals(190, bitSet.cardinality());
        assertFalse(bitSet.get(9));
        assertTrue(bitSet.get(10));
        assertTrue(bitSet.get(199));
        assertFalse(bitSet.get(200));
        bitSet.clear(50L, 60L);
        assertEquals(180, bitSet.cardinality());
        bitSet.flip(0L, 12L);
        assertTrue(bitSet.get(5));
        assertFalse(bitSet.get(10));
    }

    @Test
    public void testNextSetBitAndIterator() {
        GrowableBitSet bitSet = new GrowableBitSet();
        int[] setBits = {0, 1, 12, 63, 64, 65, 190, 4096};
        for (int b : setBits) bitSet.set(b);

        // nextSetBit scan
        int idx = 0;
        for (long i = bitSet.nextSetBit(0L); i >= 0; i = bitSet.nextSetBit(i + 1)) {
            assertEquals(setBits[idx++], i);
        }
        assertEquals(setBits.length, idx);

        // int variant
        assertEquals(12, bitSet.nextSetBit(2));
        assertEquals(-1, bitSet.nextSetBit(4097));

        // Analysis-style iterator loop
        idx = 0;
        GrowableBitSetIterator iter = bitSet.iterator();
        for (int i = iter.nextSetBit(); i >= 0; i = iter.nextSetBit()) {
            assertEquals(setBits[idx++], i);
        }
        assertEquals(setBits.length, idx);
    }

    @Test
    public void testToStringEqualsHashCode() {
        GrowableBitSet bitSet = new GrowableBitSet();
        assertEquals("{}", bitSet.toString());
        bitSet.set(1);
        bitSet.set(12);
        assertEquals("{1, 12}", bitSet.toString());

        GrowableBitSet other = new GrowableBitSet(1024);
        other.set(1);
        other.set(12);
        // different capacities but same bits -> equal
        assertEquals(bitSet, other);
        assertEquals(bitSet.hashCode(), other.hashCode());
        other.set(13);
        assertNotEquals(bitSet, other);
    }

    @Test
    public void testLogicalOps() {
        GrowableBitSet a = new GrowableBitSet();
        a.set(1);
        a.set(5);
        a.set(200);
        GrowableBitSet b = new GrowableBitSet();
        b.set(5);
        b.set(300);

        GrowableBitSet or = copy(a);
        or.or(b);
        assertEquals("{1, 5, 200, 300}", or.toString());

        GrowableBitSet and = copy(a);
        and.and(b);
        assertEquals("{5}", and.toString());

        GrowableBitSet andNot = copy(a);
        andNot.andNot(b);
        assertEquals("{1, 200}", andNot.toString());

        GrowableBitSet xor = copy(a);
        xor.xor(b);
        assertEquals("{1, 200, 300}", xor.toString());

        assertTrue(a.intersects(b));
        b.clear(5);
        assertFalse(a.intersects(b));
    }

    /**
     * PINNED STORAGE FORMAT: ExternalBooleanEncodedValue field-serializes its bit set into
     * stored-graph metadata as {"bits":[...],"wlen":N}. GrowableBitSet must keep exactly the
     * two instance fields 'bits' and 'wlen' (in this order) so that the format stays identical
     * to the hppc BitSet it replaces (see EncodedValueSerializerTest).
     */
    @Test
    public void testJacksonFieldSerializationFormat() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE);
        mapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);

        assertEquals("{\"bits\":[0],\"wlen\":1}", mapper.writeValueAsString(new GrowableBitSet()));

        GrowableBitSet bitSet = new GrowableBitSet();
        bitSet.set(0);
        bitSet.set(65);
        // the whole backing array is serialized, incl. words beyond wlen (hppc BitSet parity)
        assertEquals("{\"bits\":[1,2,0,0,0],\"wlen\":2}", mapper.writeValueAsString(bitSet));

        // round-trip
        GrowableBitSet read = mapper.readValue("{\"bits\":[1,2],\"wlen\":2}", GrowableBitSet.class);
        assertEquals(bitSet, read);
        assertEquals("{0, 65}", read.toString());
    }

    private static GrowableBitSet copy(GrowableBitSet bitSet) {
        return new GrowableBitSet(bitSet.bits.clone(), bitSet.wlen);
    }
}
