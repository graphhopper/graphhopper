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

package com.graphhopper.routing.ch;

import com.carrotsearch.hppc.IntArrayList;
import org.junit.jupiter.api.Test;

import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SplitArray2DTest {

    @Test
    void basic() {
        SplitArray2D<Integer> arr = new SplitArray2D<>(5, 1);
        final int n = 1;
        IntStream.range(0, 5).forEach(i -> assertEquals(0, arr.size(i)));
        IntStream.range(0, 5).forEach(i -> assertEquals(0, arr.mid(i)));
        arr.addPartOne(n, 5);
        arr.addPartTwo(n, 6);
        arr.addPartOne(n, 4);
        arr.addPartTwo(n, 5);
        arr.addPartTwo(n, 7);
        arr.addPartTwo(n, 6);
        assertEquals(2, arr.mid(n));
        assertEquals(6, arr.size(n));
        assertEquals(IntArrayList.from(5, 4), getIn(arr, n));
        assertEquals(IntArrayList.from(6, 5, 7, 6), getOut(arr, n));
        arr.remove(n, 6);
        assertEquals(2, arr.mid(n));
        assertEquals(4, arr.size(n));
        arr.remove(n, 5);
        assertEquals(IntArrayList.from(4), getIn(arr, n));
        assertEquals(IntArrayList.from(7), getOut(arr, n));
        assertEquals(n, arr.mid(n));
        assertEquals(2, arr.size(n));
        arr.clear(n);
        assertEquals(0, arr.mid(n));
        assertEquals(0, arr.size(n));
        arr.addPartTwo(n, 23);
        arr.addPartOne(n, 17);
        assertEquals(1, arr.mid(n));
        assertEquals(2, arr.size(n));
        assertEquals(17, arr.get(n, 0));
        assertEquals(23, arr.get(n, 1));
        assertEquals(IntArrayList.from(17), getIn(arr, n));
        assertEquals(IntArrayList.from(23), getOut(arr, n));
    }

    @Test
    void remove() {
        SplitArray2D<Integer> arr = new SplitArray2D<>(3, 1);
        arr.addPartOne(0, 3);
        arr.addPartOne(0, 2);
        arr.addPartOne(0, 4);
        arr.addPartOne(0, 6);
        arr.addPartTwo(0, 1);
        arr.addPartTwo(0, 8);
        arr.addPartTwo(0, 2);
        assertEquals(IntArrayList.from(3, 2, 4, 6), getIn(arr, 0));
        assertEquals(IntArrayList.from(1, 8, 2), getOut(arr, 0));
        arr.remove(0, 2);
        assertEquals(IntArrayList.from(3, 6, 4), getIn(arr, 0));
        assertEquals(IntArrayList.from(8, 1), getOut(arr, 0));
    }

    @Test
    void remove2() {
        SplitArray2D<Integer> arr = new SplitArray2D<>(3, 1);
        arr.addPartOne(0, 3);
        arr.addPartOne(0, 2);
        arr.addPartOne(0, 4);
        arr.addPartOne(0, 4);
        arr.addPartOne(0, 4);
        arr.addPartOne(0, 6);
        arr.addPartOne(0, 4);
        arr.addPartTwo(0, 1);
        arr.addPartTwo(0, 3);
        arr.addPartTwo(0, 2);
        arr.addPartTwo(0, 1);
        arr.addPartTwo(0, 1);
        arr.addPartTwo(0, 4);

        assertEquals(IntArrayList.from(3, 2, 4, 4, 4, 6, 4), getIn(arr, 0));
        assertEquals(IntArrayList.from(1, 3, 2, 1, 1, 4), getOut(arr, 0));
        arr.remove(0, 4);
        assertEquals(IntArrayList.from(3, 2, 6), getIn(arr, 0));
        assertEquals(IntArrayList.from(2, 1, 1, 3, 1), getOut(arr, 0));
        arr.remove(0, 1);
        assertEquals(IntArrayList.from(3, 2, 6), getIn(arr, 0));
        assertEquals(IntArrayList.from(2, 3), getOut(arr, 0));
        arr.remove(0, 2);
        assertEquals(IntArrayList.from(3, 6), getIn(arr, 0));
        assertEquals(IntArrayList.from(3), getOut(arr, 0));
    }

    @Test
    void remove3() {
        SplitArray2D<Integer> arr = new SplitArray2D<>(3, 1);
        arr.addPartOne(0, 1);
        arr.addPartOne(0, 2);
        arr.addPartTwo(0, 1);
        arr.addPartTwo(0, 2);
        assertEquals(IntArrayList.from(1, 2), getIn(arr, 0));
        assertEquals(IntArrayList.from(1, 2), getOut(arr, 0));
        arr.remove(0, 2);
        assertEquals(IntArrayList.from(1), getIn(arr, 0));
        assertEquals(IntArrayList.from(1), getOut(arr, 0));
    }

    private IntArrayList getIn(SplitArray2D<Integer> arr, int n) {
        return getRange(arr, n, 0, arr.mid(n));
    }

    private IntArrayList getOut(SplitArray2D<Integer> arr, int n) {
        return getRange(arr, n, arr.mid(n), arr.size(n));
    }

    private IntArrayList getRange(SplitArray2D<Integer> arr, int n, int start, int end) {
        IntArrayList res = new IntArrayList(end - start);
        for (int i = start; i < end; i++) {
            res.add(arr.get(n, i));
        }
        return res;
    }
}