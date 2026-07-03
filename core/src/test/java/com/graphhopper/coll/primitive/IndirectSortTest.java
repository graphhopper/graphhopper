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
package com.graphhopper.coll.primitive;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

public class IndirectSortTest {

    @Test
    public void testEmptyAndSingle() {
        assertArrayEquals(new int[0], IndirectSort.mergesort(0, 0, new IndirectComparator.AscendingIntComparator(new int[0])));
        assertArrayEquals(new int[]{0}, IndirectSort.mergesort(0, 1, new IndirectComparator.AscendingIntComparator(new int[]{42})));
    }

    @Test
    public void testAscendingIntComparator() {
        int[] values = {30, 10, 20, 40, 10};
        int[] order = IndirectSort.mergesort(0, values.length, new IndirectComparator.AscendingIntComparator(values));
        // stable: the two 10s keep their relative order (index 1 before index 4)
        assertArrayEquals(new int[]{1, 4, 2, 0, 3}, order);
    }

    @Test
    public void testStartOffset() {
        int[] values = {0, 0, 3, 2, 1};
        int[] order = IndirectSort.mergesort(2, 3, new IndirectComparator.AscendingIntComparator(values));
        assertArrayEquals(new int[]{4, 3, 2}, order);
    }

    @Test
    public void testStabilityWithManyDuplicates() {
        // length > MIN_LENGTH_FOR_INSERTION_SORT (30) to exercise the merge path
        Random rnd = new Random(123);
        int[] values = new int[10_000];
        for (int i = 0; i < values.length; i++)
            values[i] = rnd.nextInt(10); // lots of duplicates
        int[] order = IndirectSort.mergesort(0, values.length, new IndirectComparator.AscendingIntComparator(values));

        for (int i = 1; i < order.length; i++) {
            int prev = values[order[i - 1]], curr = values[order[i]];
            assertTrue(prev <= curr, "not sorted at " + i);
            if (prev == curr)
                assertTrue(order[i - 1] < order[i], "not stable at " + i);
        }
    }

    @Test
    public void testCustomComparatorLambda() {
        int[] values = {5, 1, 4, 2};
        // descending via lambda (fun interface)
        int[] order = IndirectSort.mergesort(0, values.length, (a, b) -> Integer.compare(values[b], values[a]));
        assertArrayEquals(new int[]{0, 2, 3, 1}, order);
    }
}
