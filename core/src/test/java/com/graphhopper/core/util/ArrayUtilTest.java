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

package com.graphhopper.core.util;

import com.graphhopper.core.util.ArrayUtil;
import com.carrotsearch.hppc.IntArrayList;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Random;

import static com.carrotsearch.hppc.IntArrayList.from;
import static org.junit.jupiter.api.Assertions.*;

class ArrayUtilTest {

    @Test
    public void testConstant() {
        IntArrayList list = ArrayUtil.constant(10, 3);
        assertEquals(10, list.size());
        assertEquals(3, list.get(5));
        assertEquals(3, list.get(9));
        assertEquals(10, list.buffer.length);
    }

    @Test
    public void testIota() {
        IntArrayList list = ArrayUtil.iota(15);
        assertEquals(15, list.buffer.length);
        assertEquals(15, list.elementsCount);
        assertEquals(14 / 2.0 * (14 + 1), Arrays.stream(list.buffer).sum());
    }

    @Test
    public void testRange() {
        assertEquals(from(3, 4, 5, 6), ArrayUtil.range(3, 7));
        assertEquals(from(-3, -2), ArrayUtil.range(-3, -1));
        assertEquals(from(), ArrayUtil.range(5, 5));
    }

    @Test
    public void testRangeClosed() {
        assertEquals(from(3, 4, 5, 6, 7), ArrayUtil.rangeClosed(3, 7));
        assertEquals(from(-3, -2, -1), ArrayUtil.rangeClosed(-3, -1));
        assertEquals(from(5), ArrayUtil.rangeClosed(5, 5));
    }

    @Test
    public void testPermutation() {
        IntArrayList list = ArrayUtil.permutation(15, new Random());
        assertEquals(15, list.buffer.length);
        assertEquals(15, list.elementsCount);
        assertEquals(14 / 2.0 * (14 + 1), Arrays.stream(list.buffer).sum());
        assertTrue(ArrayUtil.isPermutation(list));
    }

    @Test
    public void testIsPermutation() {
        assertTrue(ArrayUtil.isPermutation(IntArrayList.from()));
        assertTrue(ArrayUtil.isPermutation(IntArrayList.from(0)));
        assertTrue(ArrayUtil.isPermutation(IntArrayList.from(0, 1)));
        assertTrue(ArrayUtil.isPermutation(IntArrayList.from(6, 2, 4, 0, 1, 3, 5)));
        assertFalse(ArrayUtil.isPermutation(IntArrayList.from(1, 2)));
        assertFalse(ArrayUtil.isPermutation(IntArrayList.from(-1)));
        assertFalse(ArrayUtil.isPermutation(IntArrayList.from(1)));
        assertFalse(ArrayUtil.isPermutation(IntArrayList.from(3, 4, 0, 1)));
        assertFalse(ArrayUtil.isPermutation(IntArrayList.from(0, 1, 3, 3, 4, 4, 6)));
    }

    @Test
    public void testReverse() {
        assertEquals(from(), ArrayUtil.reverse(from()));
        assertEquals(from(1), ArrayUtil.reverse(from(1)));
        assertEquals(from(9, 5), ArrayUtil.reverse(from(5, 9)));
        assertEquals(from(7, 1, 3), ArrayUtil.reverse(from(3, 1, 7)));
        assertEquals(from(4, 3, 2, 1), ArrayUtil.reverse(from(1, 2, 3, 4)));
        assertEquals(from(5, 4, 3, 2, 1), ArrayUtil.reverse(from(1, 2, 3, 4, 5)));
    }

    @Test
    public void testShuffle() {
        assertEquals(from(4, 1, 3, 2), ArrayUtil.shuffle(from(1, 2, 3, 4), new Random(0)));
        assertEquals(from(4, 3, 2, 1, 5), ArrayUtil.shuffle(from(1, 2, 3, 4, 5), new Random(1)));
    }

    @Test
    public void removeConsecutiveDuplicates() {
        int[] arr = new int[]{3, 3, 4, 2, 1, -3, -3, 9, 3, 6, 6, 7, 7};
        assertEquals(9, ArrayUtil.removeConsecutiveDuplicates(arr, arr.length));
        // note that only the first 9 elements should be considered the 'valid' range
        assertEquals(IntArrayList.from(3, 4, 2, 1, -3, 9, 3, 6, 7, 6, 6, 7, 7), IntArrayList.from(arr));

        int[] brr = new int[]{4, 4, 3, 5, 3};
        assertEquals(2, ArrayUtil.removeConsecutiveDuplicates(brr, 3));
        assertEquals(IntArrayList.from(4, 3, 3, 5, 3), IntArrayList.from(brr));
    }

    @Test
    public void testWithoutConsecutiveDuplicates() {
        assertEquals(from(), ArrayUtil.withoutConsecutiveDuplicates(from()));
        assertEquals(from(1), ArrayUtil.withoutConsecutiveDuplicates(from(1)));
        assertEquals(from(1), ArrayUtil.withoutConsecutiveDuplicates(from(1, 1)));
        assertEquals(from(1), ArrayUtil.withoutConsecutiveDuplicates(from(1, 1, 1)));
        assertEquals(from(1, 2), ArrayUtil.withoutConsecutiveDuplicates(from(1, 1, 2)));
        assertEquals(from(1, 2, 1), ArrayUtil.withoutConsecutiveDuplicates(from(1, 2, 1)));
        assertEquals(
                from(5, 6, 5, 8, 9, 11, 2, -1, 3),
                ArrayUtil.withoutConsecutiveDuplicates(from(5, 5, 5, 6, 6, 5, 5, 8, 9, 11, 11, 2, 2, -1, 3, 3)));
    }

    @Test
    public void testTransform() {
        IntArrayList arr = from(7, 6, 2);
        ArrayUtil.transform(arr, ArrayUtil.constant(8, 4));
        assertEquals(IntArrayList.from(4, 4, 4), arr);

        IntArrayList brr = from(3, 0, 1);
        ArrayUtil.transform(brr, IntArrayList.from(6, 2, 1, 5));
        assertEquals(IntArrayList.from(5, 6, 2), brr);
    }

    @Test
    public void testCalcSortOrder() {
        assertEquals(from(), from(ArrayUtil.calcSortOrder(from(), from())));
        assertEquals(from(0), from(ArrayUtil.calcSortOrder(from(3), from(4))));
        assertEquals(from(0, 2, 3, 1), from(ArrayUtil.calcSortOrder(from(3, 6, 3, 4), from(0, -1, 2, -6))));
        assertEquals(from(2, 3, 1, 0), from(ArrayUtil.calcSortOrder(from(3, 3, 0, 0), from(0, -1, 1, 2))));
        assertEquals(from(), from(ArrayUtil.calcSortOrder(new int[]{3, 3, 0, 0}, new int[]{0, -1, 1, 2}, 0)));
        assertEquals(from(0), from(ArrayUtil.calcSortOrder(new int[]{3, 3, 0, 0}, new int[]{0, -1, 1, 2}, 1)));
        assertEquals(from(1, 0), from(ArrayUtil.calcSortOrder(new int[]{3, 3, 0, 0}, new int[]{0, -1, 1, 2}, 2)));
        assertEquals(from(2, 1, 0), from(ArrayUtil.calcSortOrder(new int[]{3, 3, 0, 0}, new int[]{0, -1, 1, 2}, 3)));
        assertEquals(from(2, 3, 1, 0), from(ArrayUtil.calcSortOrder(new int[]{3, 3, 0, 0}, new int[]{0, -1, 1, 2}, 4)));
    }

    @Test
    public void testApplyOrder() {
        assertEquals(from(0, 6, 3, 1, 4), from(ArrayUtil.applyOrder(new int[]{3, 4, 6, 0, 1}, new int[]{3, 2, 0, 4, 1})));
    }

    @Test
    public void testInvert() {
        assertEquals(from(-1, -1, -1, 3), from(ArrayUtil.invert(new int[]{3, 3, 3, 3})));
        assertEquals(from(3, 2, 0, 1), from(ArrayUtil.invert(new int[]{2, 3, 1, 0})));
        assertEquals(from(2, 3, 1, 0), from(ArrayUtil.invert(new int[]{3, 2, 0, 1})));
    }

    @Test
    public void testMerge() {
        assertArrayEquals(new int[]{}, ArrayUtil.merge(new int[]{}, new int[]{}));
        assertArrayEquals(new int[]{4, 5}, ArrayUtil.merge(new int[]{}, new int[]{4, 5}));
        assertArrayEquals(new int[]{4, 5}, ArrayUtil.merge(new int[]{4, 5}, new int[]{}));
        assertArrayEquals(new int[]{3, 6, 9}, ArrayUtil.merge(new int[]{6, 6, 6, 9}, new int[]{3, 9}));
        int[] a = {2, 6, 8, 12, 15};
        int[] b = {3, 7, 9, 10, 11, 12, 15, 20, 21, 26};
        assertEquals(from(2, 3, 6, 7, 8, 9, 10, 11, 12, 15, 20, 21, 26), from(ArrayUtil.merge(a, b)));
    }
}