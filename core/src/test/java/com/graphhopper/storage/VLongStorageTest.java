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
package com.graphhopper.storage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Peter Karich
 */
public class VLongStorageTest {
    @Test
    public void testWrite() {
        VLongStorage store = new VLongStorage();
        store.seek(0);
        store.writeVLong(1);
        store.writeVLong(7);
        assertEquals(2, store.getPosition());
        store.writeVLong(777666555);
        assertEquals(7, store.getPosition());

        store.seek(0);
        assertEquals(1L, store.readVLong());
        assertEquals(7L, store.readVLong());
        assertEquals(777666555L, store.readVLong());
    }

    @Test
    public void testWriteWithTrim() {
        VLongStorage store = new VLongStorage();
        store.seek(0);
        store.writeVLong(1);
        store.trimToSize();
        assertEquals(1, store.getPosition());
        store.writeVLong(7);
        store.trimToSize();
        assertEquals(2, store.getPosition());
        store.writeVLong(777666555);
        store.trimToSize();
        assertEquals(7, store.getPosition());

        store.seek(0);
        assertEquals(1L, store.readVLong());
        assertEquals(7L, store.readVLong());
        assertEquals(777666555L, store.readVLong());
    }
}
