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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author Peter Karich
 */
public class GHTreeMapComposedTest {

    private GHTreeMapComposed instance = new GHTreeMapComposed();

    @Test
    public void testInsert() {
        instance.insert(1, 100.001f);
        assertEquals(1, instance.peekKey());
        assertEquals(100.001, instance.peekValue(), 1.e-4);

        instance.insert(2, 99.7f);
        instance.insert(3, 101.4f);
        assertEquals(2, instance.peekKey());
        assertEquals(99.7, instance.peekValue(), 1.e-4);

        assertEquals(2, instance.pollKey());
    }

    @Test
    public void testDifferentValuesSameKey() {
        instance.insert(0, -4.0f);
        instance.insert(0, -24.0f);
        assertEquals(2, instance.getSize());
        assertEquals(0, instance.peekKey());
        assertEquals(-24.0f, instance.peekValue(), 1.e-4);

        assertEquals(0, instance.pollKey());
        assertEquals(0, instance.pollKey());
    }

    @Test
    public void testDifferentKeysSameValue() {
        instance.insert(0, -4.0f);
        instance.insert(1, -4.0f);
        assertEquals(2, instance.getSize());
    }

    @Test
    public void testUpdate() {
        instance.insert(34302, 26.25f);
        instance.update(34302, 26.25f, 5.6f);
        assertEquals(5.6f, instance.peekValue(), 1.e-4);
        assertEquals(34302, instance.pollKey());
    }

    @Test
    public void testUpdateDuplicateValues() {
        instance.insert(34302, 26.25f);
        instance.insert(160654, 26.25f);
        instance.insert(34302, 26.25f);
        instance.insert(160654, 26.25f);
        assertEquals(2, instance.getSize());
        instance.remove(34302, 26.25f);
        assertEquals(1, instance.getSize());
    }

    @Test
    public void testRemovingNonExistentKeyThrows() {
        instance.insert(5, 1.1f);
        assertThrows(IllegalStateException.class, () -> instance.remove(5, 2.2f));
    }
}
