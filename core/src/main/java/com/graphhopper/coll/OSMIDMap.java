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
package com.graphhopper.coll;

import com.graphhopper.storage.DataAccess;
import com.graphhopper.storage.Directory;
import com.graphhopper.util.BitUtil;
import com.graphhopper.util.Helper;

/**
 * This is a special purpose map for writing increasing OSM IDs with consecutive values. It stores
 * a map from long to int in a memory friendly way and but does NOT provide O(1) access.
 * <p>
 *
 * @author Peter Karich
 */
public class OSMIDMap implements LongIntMap {
    private static final BitUtil bitUtil = BitUtil.LITTLE;
    private final DataAccess keys;
    private final DataAccess values;
    private final int noEntryValue;
    private final Directory dir;
    private long lastKey = Long.MIN_VALUE;
    private long size;

    public OSMIDMap(Directory dir) {
        this(dir, -1);
    }

    public OSMIDMap(Directory dir, int noNumber) {
        this.dir = dir;
        this.noEntryValue = noNumber;
        keys = dir.find("osmid_map_keys");
        keys.create(2000);
        values = dir.find("osmid_map_values");
        values.create(1000);
    }

    static long binarySearch(DataAccess da, long start, long len, long key) {
        long high = start + len, low = start - 1, guess;
        byte[] longBytes = new byte[8];
        while (high - low > 1) {
            // use >>> for average or we could get an integer overflow.
            guess = (high + low) >>> 1;
            long tmp = guess << 3;
            da.getBytes(tmp, longBytes, 8);
            long guessedKey = bitUtil.toLong(longBytes);
            if (guessedKey < key)
                low = guess;
            else
                high = guess;
        }

        if (high == start + len)
            return ~(start + len);

        long tmp = high << 3;
        da.getBytes(tmp, longBytes, 8);
        long highKey = bitUtil.toLong(longBytes);
        if (highKey == key)
            return high;
        else
            return ~high;
    }

    public void remove() {
        dir.remove(keys);
    }

    @Override
    public int put(long key, int value) {
        if (key <= lastKey) {
            long oldValueIndex = binarySearch(keys, 0, getSize(), key);
            if (oldValueIndex < 0) {
                throw new IllegalStateException("Cannot insert keys lower than "
                        + "the last key " + key + " < " + lastKey + ". Only updating supported");
            }
            oldValueIndex *= 4;
            int oldValue = values.getInt(oldValueIndex);
            values.setInt(oldValueIndex, value);
            return oldValue;
        }

        values.ensureCapacity(size + 4);
        values.setInt(size, value);
        long doubleSize = size * 2;
        keys.ensureCapacity(doubleSize + 8);

        // store long => double of the orig size
        byte[] longBytes = bitUtil.fromLong(key);
        keys.setBytes(doubleSize, longBytes, 8);
        lastKey = key;
        size += 4;
        return -1;
    }

    @Override
    public int get(long key) {
        long retIndex = binarySearch(keys, 0, getSize(), key);
        if (retIndex < 0)
            return noEntryValue;

        return values.getInt(retIndex * 4);
    }

    @Override
    public long getSize() {
        return size / 4;
    }

    public long getCapacity() {
        return keys.getCapacity();
    }

    @Override
    public int getMemoryUsage() {
        return Math.round(getCapacity() / Helper.MB);
    }

    @Override
    public void optimize() {
    }
}
