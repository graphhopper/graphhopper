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
package com.graphhopper.search;

import com.graphhopper.storage.DataAccess;
import com.graphhopper.storage.Directory;
import com.graphhopper.storage.Storable;
import com.graphhopper.util.BitUtil;
import com.graphhopper.util.Helper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * @author Peter Karich
 */
public class StringIndex implements Storable<StringIndex> {
    private static final Logger logger = LoggerFactory.getLogger(StringIndex.class);
    private static final long EMPTY_POINTER = 0, START_POINTER = 1;
    private final DataAccess keys;
    // storage layout per entry:
    // vals count, val_length_0, ..., val_0, ...
    // Drawback: we need to loop through val_length_x entries to get the start of val_x+1
    // Note that we store duplicate values via an absolute reference (64 bytes) and use negative val_length as marker
    private final DataAccess vals;
    // array.indexOf could be faster than hashmap.get if not too many keys
    private final Map<String, Integer> keysInMem = new HashMap<>();
    private long bytePointer = START_POINTER;

    public StringIndex(Directory dir) {
        keys = dir.find("string_index_keys");
        vals = dir.find("string_index_vals");
    }

    @Override
    public StringIndex create(long initBytes) {
        keys.create(initBytes);
        vals.create(initBytes);
        return this;
    }

    @Override
    public boolean loadExisting() {
        if (vals.loadExisting()) {
            if (!keys.loadExisting())
                throw new IllegalStateException("Loaded values but cannot load keys");
            bytePointer = BitUtil.LITTLE.combineIntsToLong(vals.getHeader(0), vals.getHeader(4));

            // load keys into memory
            int count = keys.getShort(0);
            long keyBytePointer = 2 + 2 * count, indexPointer = 2;
            for (int i = 0; i < count; i++) {
                int keyLength = keys.getShort(indexPointer);
                indexPointer += 2;

                byte[] keyBytes = new byte[keyLength];
                keys.getBytes(keyBytePointer, keyBytes, keyLength);
                keysInMem.put(new String(keyBytes, Helper.UTF_CS), keysInMem.size());
                keyBytePointer += keyLength;
            }
            return true;
        }

        return false;
    }

    Set<String> getKeys() {
        return keysInMem.keySet();
    }

    /**
     * This method writes the specified key-value pairs into the storage.
     */
    public long add(Map<String, String> entryMap) {
        if (entryMap.isEmpty())
            return EMPTY_POINTER;

        long oldPointer = bytePointer;
        vals.ensureCapacity(bytePointer + 1 + 2 * entryMap.size());
        // set only 1 byte for key count
        if (entryMap.size() > 127)
            throw new IllegalArgumentException("Cannot store more than 127 entries per entry");

        vals.setShort(bytePointer, (byte) entryMap.size());
        bytePointer += 1 + 2 * entryMap.size();
        int entryIndex = 0;
        for (Map.Entry<String, String> entry : entryMap.entrySet()) {
            String key = entry.getKey(), value = entry.getValue();
            Integer keyIndex = keysInMem.get(key);
            if (keyIndex == null) {
                keyIndex = keysInMem.size();
                keysInMem.put(key, keyIndex);
            }

            if (value == null || value.isEmpty()) {
                vals.setShort(oldPointer + 1 + 2 * entryIndex, (short) (0 | keyIndex));
            } else {
                byte[] valueBytes = getBytesForString("Value for key" + key, value);
                vals.ensureCapacity(bytePointer + valueBytes.length);
                vals.setShort(oldPointer + 1 + 2 * entryIndex, (short) ((valueBytes.length << 8) | keyIndex));
                vals.setBytes(bytePointer, valueBytes, valueBytes.length);
                bytePointer += valueBytes.length;
            }
            entryIndex++;
        }
        return oldPointer;
    }

    public String get(long pointer) {
        return get(pointer, "");
    }

    public String get(long pointer, String key) {
        if (pointer < 0)
            throw new IllegalStateException("Pointer to access StringIndex cannot be negative:" + pointer);

        if (pointer == EMPTY_POINTER)
            return "";

        int keyCount = vals.getShort(pointer) & 0xFF;
        Integer keyIndex = keysInMem.get(key);
        if (key.isEmpty())
            keyIndex = 0;
        if (keyIndex == null)
            throw new IllegalArgumentException("Cannot find key " + key);

        byte[] bytes = new byte[1 + 2 * keyCount];
        vals.getBytes(pointer, bytes, bytes.length);
        pointer += 1 + 2 * keyCount;
        long tmpPointer = pointer;
        for (int i = 0; i < keyCount; i++) {
            int currentKeyIndex = bytes[i * 2 + 1] & 0xFF;
            if (currentKeyIndex == keyIndex) {
                byte[] stringBytes = new byte[bytes[i * 2 + 2] & 0xFF];
                vals.getBytes(tmpPointer, stringBytes, stringBytes.length);
                return new String(stringBytes, Helper.UTF_CS);
            }

            tmpPointer += bytes[i * 2 + 2] & 0xFF;
        }
        if (!key.isEmpty() || bytes.length < 2)
            return null;
        byte[] stringBytes = new byte[bytes[2] & 0xFF];
        vals.getBytes(pointer, stringBytes, stringBytes.length);
        return new String(stringBytes, Helper.UTF_CS);
    }

    private byte[] getBytesForString(String info, String name) {
        byte[] bytes = name.getBytes(Helper.UTF_CS);
        // store size of byte array into *one* byte
        if (bytes.length > 255) {
            String newName = name.substring(0, 256 / 4);
            logger.warn(info + " is too long: " + name + " truncated to " + newName);
            bytes = newName.getBytes(Helper.UTF_CS);
        }

        if (bytes.length > 255)
            // really make sure no such problem exists
            throw new IllegalStateException(info + " is too long: " + name);

        return bytes;
    }

    @Override
    public void flush() {
        // store: key count, length_0, length_1, ... length_n-1, key_0, key_1, ...
        keys.ensureCapacity(2 + 2 * keysInMem.size());
        keys.setShort(0, (short) keysInMem.size());
        long keyBytePointer = 2 + 2 * keysInMem.size(), indexPointer = 2;
        for (String key : keysInMem.keySet()) {
            byte[] keyBytes = getBytesForString("key", key);
            keys.setShort(indexPointer, (short) keyBytes.length);
            indexPointer += 2;

            keys.ensureCapacity(keyBytePointer + keyBytes.length);
            keys.setBytes(keyBytePointer, keyBytes, keyBytes.length);
            keyBytePointer += keyBytes.length;
        }
        keys.flush();

        vals.setHeader(0, BitUtil.LITTLE.getIntLow(bytePointer));
        vals.setHeader(4, BitUtil.LITTLE.getIntHigh(bytePointer));
        vals.flush();
    }

    @Override
    public void close() {
        keys.close();
        vals.close();
    }

    @Override
    public boolean isClosed() {
        return vals.isClosed() && keys.isClosed();
    }

    public void setSegmentSize(int segments) {
        keys.setSegmentSize(segments);
        vals.setSegmentSize(segments);
    }

    @Override
    public long getCapacity() {
        return vals.getCapacity() + keys.getCapacity();
    }

    public void copyTo(StringIndex stringIndex) {
        keys.copyTo(stringIndex.keys);
        vals.copyTo(stringIndex.vals);
    }
}
