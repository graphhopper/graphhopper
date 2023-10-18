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

import static org.junit.jupiter.api.Assertions.*;

public class MinHeapWithUpdateTest implements BinaryHeapTestInterface {

    private MinHeapWithUpdate heap;

    @Override
    public void create(int capacity) {
        heap = new MinHeapWithUpdate(capacity);
    }

    @Override
    public int size() {
        return heap.size();
    }

    @Override
    public boolean isEmpty() {
        return heap.isEmpty();
    }

    @Override
    public void push(int id, float val) {
        heap.push(id, val);
    }

    boolean contains(int id) {
        return heap.contains(id);
    }

    @Override
    public int peekId() {
        return heap.peekId();
    }

    @Override
    public float peekVal() {
        return heap.peekValue();
    }

    @Override
    public void update(int id, float val) {
        heap.update(id, val);
    }

    @Override
    public int poll() {
        return heap.poll();
    }

    @Override
    public void clear() {
        heap.clear();
    }

    @Test
    public void outOfRange() {
        assertThrows(IllegalArgumentException.class, () -> new MinHeapWithUpdate(4).push(4, 1.2f));
        assertThrows(IllegalArgumentException.class, () -> new MinHeapWithUpdate(4).push(-1, 1.2f));
    }

    @Test
    void tooManyElements() {
        create(3);
        push(1, 0.1f);
        push(2, 0.1f);
        push(0, 0.1f);
        // pushing element 1 again is not allowed (but this is not checked explicitly). however pushing more elements
        // than 3 is already an error
        assertThrows(IllegalStateException.class, () -> push(1, 0.1f));
        assertThrows(IllegalStateException.class, () -> push(2, 6.1f));
    }

    @Test
    void duplicateElements() {
        create(5);
        push(1, 0.2f);
        push(0, 0.4f);
        push(2, 0.1f);
        assertEquals(2, poll());
        // pushing 2 again is ok because it was polled before
        push(2, 0.6f);
        // but now its not ok to push it again
        assertThrows(IllegalStateException.class, () -> push(2, 0.4f));
    }

    @Test
    void testContains() {
        create(4);
        push(1, 0.1f);
        push(2, 0.7f);
        push(0, 0.5f);
        assertFalse(contains(3));
        assertTrue(contains(1));
        assertEquals(1, poll());
        assertFalse(contains(1));
    }

    @Test
    void containsAfterClear() {
        create(4);
        push(1, 0.1f);
        push(2, 0.1f);
        assertEquals(2, size());
        clear();
        assertFalse(contains(0));
        assertFalse(contains(1));
        assertFalse(contains(2));
    }


}