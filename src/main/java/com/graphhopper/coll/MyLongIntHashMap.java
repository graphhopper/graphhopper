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
import java.util.Random;

/**
 * A HashMap which can be stored in RAM or on disc, using open addressing
 * hashing. In combination with MMapDirectory and BigLongIntMap this can be used
 * for very large data sets.
 *
 * @see http://en.wikipedia.org/wiki/Hash_table#Open_addressing
 * @author Peter Karich
 */
public class MyLongIntHashMap {

    private static final int MAX_PROBES = 20;
    private DataAccess keysAndValues;
    // 0 as empty key or value indicator is a bit problematic but for now ok
    // as we don't want to prefill the DataAccess with a number for every rehash!
    private final int noNumberValue = 0;
    private long size;
    private long intCapacity;
    private double loadFactor;
    private final String prefix;
    private Random rand = new Random();
    private final Directory dir;

    public MyLongIntHashMap(Directory dir) {
        this(dir, "hashmap", 100);
    }

    public MyLongIntHashMap(Directory dir, String prefix, int initCapacity) {
        this.prefix = prefix;
        this.dir = dir;
        // 8 bytes for key and 4 bytes for value
        keysAndValues = dir.findCreate(prefix + "_kv");
        // TODO should we use 2^x capacities in order to use faster modulo operation (via "& bitmask")
        // 3 integers        
        intCapacity = initCapacity * 3;
        // one int is 4 bytes
        keysAndValues.create(intCapacity * 4);
    }

    public int put(long key, int value) {
        long index = calculateIndex(key);
        for (int i = 0; i < MAX_PROBES; i++) {
            long oldKey = BitUtil.toLong(keysAndValues.getInt(index++), keysAndValues.getInt(index++));
            int oldVal = keysAndValues.getInt(index);
            if (oldKey == noNumberValue) {
                // insert
                // TODO
                intCapacity++;
                return noNumberValue;
            } else if (oldKey == key) {
                // update
                // TODO
                return oldVal;
            }

            // linear probing
            index++;
        }
        // rehash if too many probing
        System.out.println("rehash " + size);
        rehash(size);
        return put(key, value);
    }

    void ensureCapacity() {
        if (size > intCapacity * loadFactor)
            rehash(Math.round(size * 1.8));
    }

    void rehash(long newSize) {
        keysAndValues.rename(prefix + "_tmp_" + rand.nextLong());
        DataAccess tmpKeys = dir.findCreate(prefix + "_keys");
        tmpKeys.create(newSize * 4);

        // TODO rehashing
        //

        dir.remove(keysAndValues);
        keysAndValues = tmpKeys;
    }

    public int get(long key) {
        // TODO

        return noNumberValue;
    }

    protected long calculateIndex(long key) {
        return (key ^ (key * 541)) % intCapacity;
    }

    public long size() {
        return size;
    }

    public long capacity() {
        return keysAndValues.capacity();
    }

    void clear() {
        size = 0;

        // TODO
        // keys.clear();
        // values.clear();
    }

    int getNoNumberValue() {
        return noNumberValue;
    }

    void flush() {
        // TODO set headers with prefix, loadFactor etc
//        keys.flush();
//        values.flush();
        throw new IllegalStateException("not supported yet");
    }
}
