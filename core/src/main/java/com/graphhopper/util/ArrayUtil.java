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
import com.carrotsearch.hppc.IntIndexedContainer;

import java.util.Arrays;
import java.util.Random;

public class ArrayUtil {

    /**
     * Creates an IntArrayList of a given size where each element is set to the given value
     */
    public static IntArrayList constant(int size, int value) {
        IntArrayList result = new IntArrayList(size);
        Arrays.fill(result.buffer, value);
        result.elementsCount = size;
        return result;
    }

    /**
     * Creates an IntArrayList filled with the integers 0,1,2,3,...,size-1
     */
    public static IntArrayList iota(int size) {
        IntArrayList result = new IntArrayList(size);
        result.elementsCount = size;
        for (int i = 0; i < size; i++) {
            result.set(i, i);
        }
        return result;
    }

    /**
     * Creates an IntArrayList filled with a permutation of the numbers 0,1,2,...,size-1
     */
    public static IntArrayList permutation(int size, Random rnd) {
        IntArrayList result = iota(size);
        shuffle(result, rnd);
        return result;
    }

    /**
     * Reverses the order of the given list's elements in place and returns it
     */
    public static IntArrayList reverse(IntArrayList list) {
        final int[] buffer = list.buffer;
        int tmp;
        for (int start = 0, end = list.size() - 1; start < end; start++, end--) {
            // swap the values
            tmp = buffer[start];
            buffer[start] = buffer[end];
            buffer[end] = tmp;
        }
        return list;
    }

    /**
     * Shuffles the elements of the given list in place and returns it
     */
    public static IntArrayList shuffle(IntArrayList list, Random random) {
        int maxHalf = list.size() / 2;
        for (int x1 = 0; x1 < maxHalf; x1++) {
            int x2 = random.nextInt(maxHalf) + maxHalf;
            int tmp = list.buffer[x1];
            list.buffer[x1] = list.buffer[x2];
            list.buffer[x2] = tmp;
        }
        return list;
    }

    /**
     * Creates a copy of the given list where all consecutive duplicates are removed
     */
    public static IntIndexedContainer removeConsecutiveDuplicates(IntIndexedContainer arr) {
        if (arr.size() < 2) {
            return arr;
        }
        IntArrayList result = new IntArrayList();
        int prev = arr.get(0);
        for (int i = 1; i < arr.size(); i++) {
            int val = arr.get(i);
            if (val != prev) {
                result.add(val);
            }
            prev = val;
        }
        return result;
    }
}
