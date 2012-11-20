/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.graphhopper.coll;

import java.util.Arrays;

/**
 * This is a special purpose map for writing increasing OSM IDs with consecutive values. It stores
 * long->int in a memory friendly way and but does NOT provide O(1) access.
 *
 * @author Peter Karich
 */
public class OSMIDMap {

    private long[] keys;
    private long lastKey = Long.MIN_VALUE;
    private long lastValue = -1;
    private int size;

    public OSMIDMap() {
        this(10);
    }

    public OSMIDMap(int initialCapacity) {
        keys = new long[initialCapacity];
    }

    public void put(long key, long value) {
        if (key <= lastKey)
            throw new IllegalStateException("Not supported: key " + key + " is lower than last one " + lastKey);
        if (value < 0)
            throw new IllegalStateException("Not supported: negative value " + value);
        if (value != lastValue + 1)
            throw new IllegalStateException("Not supported: value " + value + " is not " + (lastValue + 1));

        if (size >= keys.length) {
            int cap = (int) (size * 1.5f);
            keys = Arrays.copyOf(keys, cap);
        }
        keys[size] = key;
        size++;
        lastKey = key;
        lastValue = value;
    }

    public long get(long key) {
        int retIndex = SparseLongLongArray.binarySearch(keys, 0, size, key);
        if (retIndex < 0)
            return getNoEntryValue();

        return retIndex;
    }

    public long getNoEntryValue() {
        return -1;
    }

    public int size() {
        return size;
    }
}
