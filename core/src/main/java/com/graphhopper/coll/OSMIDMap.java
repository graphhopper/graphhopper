/*
 *  Licensed to GraphHopper and Peter Karich under one or more contributor license 
 *  agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  GraphHopper licenses this file to you under the Apache License, 
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
import com.graphhopper.util.Helper;

/**
 * This is a special purpose map for writing increasing OSM IDs with consecutive
 * values. It stores long->int in a memory friendly way and but does NOT provide
 * O(1) access.
 *
 * @author Peter Karich
 */
public class OSMIDMap implements LongIntMap {

    private final DataAccess keys;
    private final DataAccess values;
    private long lastKey = Long.MIN_VALUE;
    private long size;
    private final int noEntryValue;
    private final Directory dir;

    public OSMIDMap(Directory dir) {
        this(dir, -1);
    }

    public OSMIDMap(Directory dir, int noNumber) {
        this.dir = dir;
        this.noEntryValue = noNumber;
        keys = dir.findCreate("osmidMapKeys");
        keys.create(2000);
        values = dir.findCreate("osmidMapValues");
        values.create(1000);
    }

    public void remove() {
        dir.remove(keys);
    }

    @Override
    public int put(long key, int value) {
        if (key <= lastKey) {
            long oldValueIndex = binarySearch(keys, 0, size(), key);
            if (oldValueIndex < 0)
                throw new IllegalStateException("Cannot insert keys lower than "
                        + "the last key " + key + " < " + lastKey + ". Only updating supported");
            int oldValue = values.getInt(oldValueIndex);
            values.setInt(oldValueIndex, value);
            return oldValue;
        }

        values.ensureCapacity(size + 1);
        values.setInt(size, value);
        long doubleSize = size * 2;
        keys.ensureCapacity(doubleSize + 2);

        // store long => double of the orig size
        keys.setInt(doubleSize++, (int) (key >>> 32));
        keys.setInt(doubleSize, (int) (key & 0xFFFFFFFFL));
        lastKey = key;
        size++;
        return -1;
    }

    @Override
    public int get(long key) {
        long retIndex = binarySearch(keys, 0, size(), key);
        if (retIndex < 0)
            return noEntryValue;
        return values.getInt(retIndex);
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

    @Override
    public long size() {
        return size;
    }

    public long capacity() {
        return keys.capacity();
    }

    @Override
    public int memoryUsage() {
        return Math.round(capacity() / Helper.MB);
    }

    @Override
    public void optimize() {
    }
}
