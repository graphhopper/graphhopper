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

import com.carrotsearch.hppc.BitSet;
import com.carrotsearch.hppc.IntArrayList;
import com.carrotsearch.hppc.IntIndexedContainer;
import com.carrotsearch.hppc.cursors.IntCursor;
import com.carrotsearch.hppc.sorting.IndirectComparator;
import com.carrotsearch.hppc.sorting.IndirectSort;

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
     * Creates an IntArrayList filled with zeros
     */
    public static IntArrayList zero(int size) {
        IntArrayList result = new IntArrayList(size);
        result.elementsCount = size;
        return result;
    }

    /**
     * Creates an IntArrayList filled with the integers 0,1,2,3,...,size-1
     */
    public static IntArrayList iota(int size) {
        return range(0, size);
    }

    /**
     * Creates an IntArrayList filled with the integers [startIncl,endExcl[
     */
    public static IntArrayList range(int startIncl, int endExcl) {
        IntArrayList result = new IntArrayList(endExcl - startIncl);
        result.elementsCount = endExcl - startIncl;
        for (int i = 0; i < result.size(); ++i)
            result.set(i, startIncl + i);
        return result;
    }

    /**
     * Creates an IntArrayList filled with the integers [startIncl,endIncl]
     */
    public static IntArrayList rangeClosed(int startIncl, int endIncl) {
        return range(startIncl, endIncl + 1);
    }

    /**
     * Creates an IntArrayList filled with a permutation of the numbers 0,1,2,...,size-1
     */
    public static IntArrayList permutation(int size, Random rnd) {
        IntArrayList result = iota(size);
        shuffle(result, rnd);
        return result;
    }

    public static boolean isPermutation(IntArrayList arr) {
        BitSet present = new BitSet(arr.size());
        for (IntCursor e : arr) {
            if (e.value >= arr.size() || e.value < 0)
                return false;
            if (present.get(e.value))
                return false;
            present.set(e.value);
        }
        return true;
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
     * Removes all duplicate elements of the given array in the range [0, end[ in place
     *
     * @return the size of the new range that contains no duplicates (smaller or equal to end).
     */
    public static int removeConsecutiveDuplicates(int[] arr, int end) {
        int curr = 0;
        for (int i = 1; i < end; ++i) {
            if (arr[i] != arr[curr])
                arr[++curr] = arr[i];
        }
        return curr + 1;
    }

    /**
     * Creates a copy of the given list where all consecutive duplicates are removed
     */
    public static IntIndexedContainer withoutConsecutiveDuplicates(IntIndexedContainer arr) {
        IntArrayList result = new IntArrayList();
        if (arr.isEmpty())
            return result;
        int prev = arr.get(0);
        result.add(prev);
        for (int i = 1; i < arr.size(); i++) {
            int val = arr.get(i);
            if (val != prev)
                result.add(val);
            prev = val;
        }
        return result;
    }

    /**
     * Maps one array using another, i.e. every element arr[x] is replaced by map[arr[x]]
     */
    public static void transform(IntIndexedContainer arr, IntIndexedContainer map) {
        for (int i = 0; i < arr.size(); ++i)
            arr.set(i, map.get(arr.get(i)));
    }

    public static int[] calcSortOrder(IntArrayList arr1, IntArrayList arr2) {
        if (arr1.elementsCount != arr2.elementsCount) {
            throw new IllegalArgumentException("Arrays must have equal size");
        }
        return calcSortOrder(arr1.buffer, arr2.buffer, arr1.elementsCount);
    }

    /**
     * This method calculates the sort order of the first {@param length} element-pairs given by two arrays.
     * The order is chosen such that it sorts the element-pairs first by the first and second by the second array.
     * The input arrays are not manipulated by this method.
     *
     * @param length must not be larger than either of the two input array lengths.
     * @return an array x of length {@param length}. e.g. if this method returns x = {2, 0, 1} it means that that
     * the element-pair with index 2 comes first in the order and so on
     */
    public static int[] calcSortOrder(final int[] arr1, final int[] arr2, int length) {
        if (arr1.length < length || arr2.length < length)
            throw new IllegalArgumentException("Arrays must not be shorter than given length");
        IndirectComparator comp = (indexA, indexB) -> {
            final int arr1cmp = Integer.compare(arr1[indexA], arr1[indexB]);
            return arr1cmp != 0 ? arr1cmp : Integer.compare(arr2[indexA], arr2[indexB]);
        };
        return IndirectSort.mergesort(0, length, comp);
    }

    /**
     * Creates a copy of the given array such that it is ordered by the given order.
     * The order can be shorter or equal, but not longer than the array.
     */
    public static int[] applyOrder(int[] arr, int[] order) {
        if (order.length > arr.length)
            throw new IllegalArgumentException("sort order must not be shorter than array");
        int[] result = new int[order.length];
        for (int i = 0; i < result.length; ++i)
            result[i] = arr[order[i]];
        return result;
    }

    /**
     * Creates a new array where each element represents the index position of this element in the given array
     * or is set to -1 if this element does not appear in the input array. None of the elements of the input array may
     * be equal or larger than the arrays length.
     */
    public static int[] invert(int[] arr) {
        int[] result = new int[arr.length];
        Arrays.fill(result, -1);
        for (int i = 0; i < arr.length; i++)
            result[arr[i]] = i;
        return result;
    }

    public static IntArrayList invert(IntArrayList list) {
        IntArrayList result = new IntArrayList(list.size());
        result.elementsCount = list.size();
        for (int i = 0; i < result.elementsCount; ++i)
            result.set(list.get(i), i);
        return result;
    }

    public static IntArrayList subList(IntArrayList list, int fromIndex, int toIndex) {
        IntArrayList result = new IntArrayList(toIndex - fromIndex);
        for (int i = fromIndex; i < toIndex; i++)
            result.add(list.get(i));
        return result;
    }

    /**
     * @param a sorted array
     * @param b sorted array
     * @return sorted array consisting of the elements of a and b, duplicates get removed
     */
    public static int[] merge(int[] a, int[] b) {
        if (a.length + b.length == 0)
            return new int[]{};
        int[] result = new int[a.length + b.length];
        int size = 0;
        int i = 0;
        int j = 0;
        while (i < a.length && j < b.length) {
            if (a[i] < b[j])
                result[size++] = a[i++];
            else
                result[size++] = b[j++];
        }
        if (i == a.length) {
            System.arraycopy(b, j, result, size, b.length - j);
            size += b.length - j;
        } else {
            System.arraycopy(a, i, result, size, a.length - i);
            size += a.length - i;
        }
        int sizeWithoutDuplicates = removeConsecutiveDuplicates(result, size);
        return Arrays.copyOf(result, sizeWithoutDuplicates);
    }
}
