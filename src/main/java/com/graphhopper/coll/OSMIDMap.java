/*
 *  Licensed to Peter Karich under one or more contributor license 
 *  agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  Peter Karich licenses this file to you under the Apache License, 
 *  Version 2.0 (the "License"); you may not use this file except 
 *  in compliance with the License. You may obtain a copy of the 
 *  License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.coll;

import com.graphhopper.storage.DataAccess;
import com.graphhopper.storage.Directory;
import com.graphhopper.util.BitUtil;

/**
 * This is a special purpose map for writing increasing OSM IDs with consecutive
 * values. It stores long->int in a memory friendly way and but does NOT provide
 * O(1) access.
 *
 * @author Peter Karich
 */
public class OSMIDMap {

    private final DataAccess keys;
    private long lastKey = Long.MIN_VALUE;
    private long lastValue = -1;
    private long doubleSize;
    private final int noEntryValue;
    private final Directory dir;

    public OSMIDMap(Directory dir) {
        this(dir, -1);
    }

    public OSMIDMap(Directory dir, int noNumber) {
        this.dir = dir;
        this.noEntryValue = noNumber;
        keys = dir.findCreate("osmidMap");
        keys.create(1000);
    }

    public void remove() {
        dir.remove(keys);
    }

    public void put(long key, long value) {
        if (key <= lastKey)
            throw new IllegalStateException("Not supported: key " + key + " is lower than last one " + lastKey);
        if (value < 0)
            throw new IllegalStateException("Not supported: negative value " + value);
        if (value != lastValue + 1)
            throw new IllegalStateException("Not supported: value " + value + " is not " + (lastValue + 1));

        keys.ensureCapacity(doubleSize + 2);
        // store long => double of the orig size
        keys.setInt(doubleSize++, (int) (key >>> 32));
        keys.setInt(doubleSize++, (int) (key & 0xFFFFFFFFL));
        lastKey = key;
        lastValue = value;
    }

    public int get(long key) {
        long retIndex = binarySearch(keys, 0, size(), key);
        if (retIndex < 0)
            return noEntryValue;
        // for now we need only an integer
        return (int) retIndex;
    }

    static long binarySearch(DataAccess da, long start, long len, long key) {
        long high = start + len, low = start - 1, guess;
        while (high - low > 1) {
            guess = (high + low) >>> 1;
            long tmp = guess << 1;
            long guessedKey = BitUtil.toLong(da.getInt(tmp), da.getInt(tmp + 1));
            if (guessedKey < key)
                low = guess;
            else
                high = guess;
        }

        if (high == start + len)
            return ~(start + len);

        long tmp = high << 1;
        long highKey = BitUtil.toLong(da.getInt(tmp), da.getInt(tmp + 1));
        if (highKey == key)
            return high;
        else
            return ~high;
    }

    public long size() {
        return doubleSize >>> 1;
    }
    
    public long capacity() {
        return keys.capacity();
    }
}
