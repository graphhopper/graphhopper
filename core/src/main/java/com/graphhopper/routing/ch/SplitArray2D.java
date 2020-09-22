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

import java.util.Arrays;

/**
 * This is a more memory-efficient version of two equal length `ArrayList<T>[]`s, i.e. this is a fixed size array where
 * each element is a variable sized sub-array and each sub-array is divided into two parts.
 * This is more memory efficient than two arrays of `ArrayList`s, because it saves the object-overhead of using an
 * ArrayList object for each sub-array.
 * <p>
 * The elements in each sub-array are divided into two parts with ranges [0,mid[ and [mid,size[. We can add elements
 * to either of the two parts.
 */
class SplitArray2D<T> {
    private static final int GROW_FACTOR = 2;
    private final int initialSubArrayCapacity;
    private final Object[][] data;
    // todo: mids/sizes can probably be combined into a single int to save some memory, but need to check maximum values
    // checking for Bayern and Germany it seemed like the maximum values were around 50/100 and independent of the
    // map size
    private final int[] mids;
    private final int[] sizes;

    SplitArray2D(int size, int initialSubArrayCapacity) {
        data = new Object[size][];
        sizes = new int[size];
        mids = new int[size];
        this.initialSubArrayCapacity = initialSubArrayCapacity;
    }

    int mid(int n) {
        return mids[n];
    }

    int size(int n) {
        return sizes[n];
    }

    T get(int n, int index) {
        return (T) data[n][index];
    }

    /**
     * Adds the given element to the first part of the n-th sub-array. This changes the order of the existing elements.
     */
    void addPartOne(int n, T element) {
        if (data[n] == null) {
            data[n] = new Object[initialSubArrayCapacity];
            data[n][0] = element;
            mids[n] = 1;
            sizes[n] = 1;
        } else {
            assert data[n].length != 0;
            if (sizes[n] == data[n].length)
                grow(n);
            data[n][sizes[n]] = data[n][mids[n]];
            data[n][mids[n]] = element;
            mids[n]++;
            sizes[n]++;
        }
    }

    /**
     * Adds the given element to the second part of the n-th sub-array. This changes the order of the existing elements.
     */
    void addPartTwo(int n, T element) {
        if (data[n] == null) {
            data[n] = new Object[initialSubArrayCapacity];
            data[n][0] = element;
            sizes[n] = 1;
        } else {
            assert data[n].length != 0;
            if (sizes[n] == data[n].length)
                grow(n);
            data[n][sizes[n]] = element;
            sizes[n]++;
        }
    }

    /**
     * Removes all occurrences of the given element from the given sub-array. Using this method changes the order of the
     * existing elements in the sub-array unless we remove only the very last element(s)!
     */
    void remove(int n, T element) {
        for (int i = 0; i < mids[n]; ++i) {
            while (mids[n] > 0 && i < mids[n] && data[n][i] == element) {
                data[n][i] = data[n][mids[n] - 1];
                data[n][mids[n] - 1] = data[n][sizes[n] - 1];
                data[n][sizes[n] - 1] = null;
                mids[n]--;
                sizes[n]--;
            }
        }
        for (int i = mids[n]; i < sizes[n]; ++i) {
            while (sizes[n] > mids[n] && i < sizes[n] && data[n][i] == element) {
                data[n][i] = data[n][sizes[n] - 1];
                data[n][sizes[n] - 1] = null;
                sizes[n]--;
            }
        }
    }

    void clear(int n) {
        data[n] = null;
        sizes[n] = 0;
        mids[n] = 0;
    }

    private void grow(int n) {
        // todo: think about grow factor: trimming and then doubling the size might be not what we want, rather
        // increase by 50% or something?
        data[n] = Arrays.copyOf(data[n], data[n].length * GROW_FACTOR);
    }

    void trimToSize() {
        for (int n = 0; n < data.length; n++) {
            if (data[n] != null)
                data[n] = Arrays.copyOf(data[n], sizes[n]);
        }
    }

}
