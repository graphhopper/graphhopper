/*
 *  Copyright 2012 Peter Karich 
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

import com.graphhopper.storage.VLongStorage;
import com.graphhopper.util.Helper;
import java.util.Arrays;

/**
 * This is a special purpose map for writing increasing OSM IDs with consecutive values. It stores
 * the keys in vlong format and values are determined by the resulting index.
 *
 * @author Peter Karich
 */
public class OSMIDSegmentedMap {

    private int bucketSize;
    private long[] keys;
    private VLongStorage[] buckets;
    private long lastKey = -1;
    private long lastValue = -1;
    private int currentBucket = 0;
    private int currentIndex = -1;
    private int size;

    public OSMIDSegmentedMap() {
        this(100, 10);
    }

    public OSMIDSegmentedMap(int initialCapacity, int maxEntryPerBucket) {
        this.bucketSize = maxEntryPerBucket;
        int cap = initialCapacity / bucketSize;
        keys = new long[cap];
        buckets = new VLongStorage[cap];
    }

    public void write(long key) {
        if (key <= lastKey)
            throw new IllegalStateException("Not supported: key " + key + " is lower than last one " + lastKey);

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
            if (currentBucket > 0)
                buckets[currentBucket - 1].trimToSize();
            buckets[currentBucket] = new VLongStorage(bucketSize);
        } else {
            long delta = key - lastKey;
            buckets[currentBucket].writeVLong(delta);
        }

        size++;
        lastKey = key;
    }

    public long get(long key) {
        int retBucket = SparseLongLongArray.binarySearch(keys, 0, currentBucket + 1, key);
        if (retBucket < 0) {
            retBucket = ~retBucket;
            retBucket--;
            if (retBucket < 0)
                return getNoEntryValue();

            long storedKey = keys[retBucket];
            if (storedKey == key)
                return (long) retBucket * bucketSize;

            VLongStorage buck = buckets[retBucket];
            long tmp = buck.getPosition();
            buck.seek(0);
            int max = currentBucket == retBucket ? currentIndex + 1 : bucketSize;
            long ret = getNoEntryValue();
            for (int i = 1; i < max; i++) {
                storedKey += buck.readVLong();
                if (storedKey == key) {
                    ret = (long) retBucket * bucketSize + i;
                    break;
                } else if (storedKey > key)
                    break;
            }
            buck.seek(tmp);
            return ret;
        }

        return (long) retBucket * bucketSize;
    }

    public long getNoEntryValue() {
        return -1;
    }

    public int size() {
        return size;
    }

    public float calcMemInMB() {
        long bytes = 0;
        for (int i = 0; i < buckets.length; i++) {
            if (buckets[i] != null)
                bytes += buckets[i].getLength();
        }
        return (float) (keys.length * 4 + bytes) / Helper.MB;
    }
}
