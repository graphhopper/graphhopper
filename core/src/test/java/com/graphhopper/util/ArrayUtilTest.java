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

package com.graphhopper.util;

import com.carrotsearch.hppc.IntArrayList;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Random;

import static com.carrotsearch.hppc.IntArrayList.from;
import static org.junit.jupiter.api.Assertions.assertEquals;

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
    public void testPermutation() {
        IntArrayList list = ArrayUtil.permutation(15, new Random());
        assertEquals(15, list.buffer.length);
        assertEquals(15, list.elementsCount);
        assertEquals(14 / 2.0 * (14 + 1), Arrays.stream(list.buffer).sum());
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

}