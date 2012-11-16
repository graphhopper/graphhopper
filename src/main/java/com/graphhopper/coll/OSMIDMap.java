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

/**
 * This is a special purpose map for writing consecutive OSM IDs. It stores long->int in a memory
 * friendly way and does NOT provide O(1) access.
 *
 * @author Peter Karich
 */
public class OSMIDMap {

    // segmented int/long array which is sorted and stores the offset to point to the values
    // 1-5   => [a,b,c,d,e] where a to e are integers
    // 20-22 => [f,g,h]
    // ...
    //
    // sorted key intervals pointing (access through binary search?) to integer arrays
    // still problematic if the intervals get too large => big 'rehashing' occurs again
    private SparseLongLongArray array;

    public OSMIDMap() {
        this(100);
    }

    public OSMIDMap(int initialCapacity) {
        array = new SparseLongLongArray(initialCapacity);
    }
    long lastKey = Long.MIN_VALUE;
    long lastValue = Long.MIN_VALUE;

    public void put(long key, long value) {
        if (key <= lastKey)
            throw new IllegalStateException("TODO key " + key + " is lower than last one " + lastKey);
        if (value <= lastValue)
            throw new IllegalStateException("Not supported: value " + value + " is lower than last one " + lastValue);

        lastKey = key;
        lastValue = value;
        array.append(key, value);
    }

    public long get(long key) {
        return array.get(key);
    }

    public long getNoEntryValue() {
        return -1;
    }

    public int size() {
        return array.size();
    }
}
