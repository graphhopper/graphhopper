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

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Peter Karich
 */
public class GHSortedCollectionTest {

    @Test
    public void testPoll() {
        GHSortedCollection instance = new GHSortedCollection();
        assertTrue(instance.isEmpty());
        instance.insert(0, 10);
        assertEquals(10, instance.peekValue());
        assertEquals(1, instance.getSize());
        instance.insert(1, 2);
        assertEquals(2, instance.peekValue());
        assertEquals(1, instance.pollKey());
        assertEquals(0, instance.pollKey());
        assertEquals(0, instance.getSize());
    }

    @Test
    public void testInsert() {
        GHSortedCollection instance = new GHSortedCollection();
        assertTrue(instance.isEmpty());
        instance.insert(0, 10);
        assertEquals(1, instance.getSize());
        assertEquals(10, instance.peekValue());
        assertEquals(0, instance.peekKey());
        instance.update(0, 10, 2);
        assertEquals(2, instance.peekValue());
        assertEquals(1, instance.getSize());
        instance.insert(0, 11);
        assertEquals(2, instance.peekValue());
        assertEquals(2, instance.getSize());
        instance.insert(1, 0);
        assertEquals(0, instance.peekValue());
        assertEquals(3, instance.getSize());
    }

    @Test
    public void testUpdate() {
        GHSortedCollection instance = new GHSortedCollection();
        assertTrue(instance.isEmpty());
        instance.insert(0, 10);
        instance.insert(1, 11);
        assertEquals(10, instance.peekValue());
        assertEquals(2, instance.getSize());
        instance.update(0, 10, 12);
        assertEquals(11, instance.peekValue());
        assertEquals(2, instance.getSize());
    }
}
