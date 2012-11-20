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
public class OSMIDSegmentedMap {

    private int bucketSize;
    private long[] keys;
    private short[][] buckets;
    private long lastKey = 1;
    private long lastValue = -1;
    private int currentBucket = 0;
    private int currentIndex = -1;
    private int size;

    public OSMIDSegmentedMap() {
        this(100, 10);
    }

    public OSMIDSegmentedMap(int initialCapacity, int bucketSize) {
        this.bucketSize = bucketSize;
        int cap = initialCapacity / bucketSize;
        keys = new long[cap];
        buckets = new short[cap][];
    }

    public void put(long key, long value) {
        if (key <= lastKey)
            throw new IllegalStateException("Not supported: key " + key + " is lower than last one " + lastKey);
        if (value < 0)
            throw new IllegalStateException("Not supported: negative value " + value);
        if (value != lastValue + 1)
            throw new IllegalStateException("Not supported: value " + value + " is not " + (lastValue + 1));

        currentIndex++;
        if (currentIndex >= bucketSize) {
            currentBucket++;
            currentIndex = 0;
        }

        if (currentBucket >= buckets.length) {
            int cap = (int) (currentBucket * 1.5f);
            buckets = Arrays.copyOf(buckets, cap);
            keys = Arrays.copyOf(keys, cap);
        }

        if (buckets[currentBucket] == null) {
            keys[currentBucket] = key;
            buckets[currentBucket] = new short[bucketSize];
            buckets[currentBucket][currentIndex] = -2;
        } else {
            long keyToStore = key - lastKey;
            if (keyToStore != (short) keyToStore) {
                // TODO if the difference does NOT fit into a byte => store as two byte and skip next
                // hmmh but then we cannot estimate the value from the index via index*bucketSize!!
                throw new UnsupportedOperationException("todo: think/impossible? key=" + key + ", lastKey=" + lastKey);
            }
            buckets[currentBucket][currentIndex] = (short) keyToStore;
        }
        size++;
        lastKey = key;
        lastValue = value;
    }

    public long get(long key) {
        int retBucket = SparseLongLongArray.binarySearch(keys, 0, currentBucket, key);
        if (retBucket < 0) {
            retBucket = ~retBucket;
            long storedKey = keys[retBucket];
            if (storedKey == key)
                return retBucket * bucketSize;

            short[] buck = buckets[retBucket];
            int max = currentBucket == retBucket ? currentIndex + 1 : buck.length;
            for (int i = 1; i < max; i++) {
                storedKey += buck[i];
                if (storedKey == key)
                    return retBucket * bucketSize + i;
                else if (storedKey > key)
                    break;
            }
            return getNoEntryValue();
        }

        return retBucket * bucketSize;
    }

    public long getNoEntryValue() {
        return -1;
    }

    public int size() {
        return size;
    }
}
