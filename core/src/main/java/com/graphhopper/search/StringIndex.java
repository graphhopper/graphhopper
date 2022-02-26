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
import com.graphhopper.util.BitUtil;
import com.graphhopper.util.Constants;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.Helper;

import java.util.*;

/**
 * @author Peter Karich
 */
public class StringIndex {
    private static final long EMPTY_POINTER = 0, START_POINTER = 1;
    // Store the key index in 2 bytes. Use negative values for marking the value as duplicate.
    static final int MAX_UNIQUE_KEYS = (1 << 15);
    // Store string value as byte array and store the length into 1 byte
    private static final int MAX_LENGTH = (1 << 8) - 1;
    boolean throwExceptionIfTooLong = false;
    private final DataAccess keys;
    // storage layout per entry:
    // 1 byte    | 2 bytes  | 1 byte      | x    | 2 bytes  | 1 byte      | x    | 2 bytes  (dup example) | 4 bytes | ...
    // vals count| key_idx_0| val_length_0| val_0| key_idx_1| val_length_1| val_1| -key_idx_2             | delta_2 | key_idx_3 | val_length_3 | val_3
    // Drawback: we need to loop through the entries to get the start of val_x.
    // Note, that we detect duplicate values via smallCache and then use the negative key index as 'duplicate' marker.
    // We then store only the delta (signed int) instead the absolute unsigned long value to reduce memory usage when duplicate entries.
    private final DataAccess vals;
    // array.indexOf could be faster than hashmap.get if not too many keys or even sort keys and use binarySearch
    private final Map<String, Integer> keysInMem = new LinkedHashMap<>();
    private final List<String> keyList = new ArrayList<>();
    private final Map<String, Long> smallCache;
    private long bytePointer = START_POINTER;
    private long lastEntryPointer = -1;
    private Map<String, String> lastEntryMap;

    /**
     * Specify a larger cacheSize to reduce disk usage. Note that this increases the memory usage of this object.
     */
    public StringIndex(Directory dir, final int cacheSize, final int segmentSize) {
        keys = dir.create("string_index_keys", segmentSize);
        vals = dir.create("string_index_vals", segmentSize);
        smallCache = new LinkedHashMap<String, Long>(cacheSize, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Long> entry) {
                return size() > cacheSize;
            }
        };
    }

    public StringIndex create(long initBytes) {
        keys.create(initBytes);
        vals.create(initBytes);
        // add special empty case to have a reliable duplicate detection via negative keyIndex
        keysInMem.put("", 0);
        keyList.add("");
        return this;
    }

    public boolean loadExisting() {
        if (vals.loadExisting()) {
            if (!keys.loadExisting())
                throw new IllegalStateException("Loaded values but cannot load keys");
            int stringIndexKeysVersion = keys.getHeader(0);
            int stringIndexValsVersion = vals.getHeader(0);
            GHUtility.checkDAVersion(keys.getName(), Constants.VERSION_STRING_IDX, stringIndexKeysVersion);
            GHUtility.checkDAVersion(vals.getName(), Constants.VERSION_STRING_IDX, stringIndexValsVersion);
            bytePointer = BitUtil.LITTLE.combineIntsToLong(vals.getHeader(4), vals.getHeader(8));

            // load keys into memory
            int count = keys.getShort(0);
            long keyBytePointer = 2;
            for (int i = 0; i < count; i++) {
                int keyLength = keys.getShort(keyBytePointer);
                keyBytePointer += 2;

                byte[] keyBytes = new byte[keyLength];
                keys.getBytes(keyBytePointer, keyBytes, keyLength);
                String valueStr = new String(keyBytes, Helper.UTF_CS);
                keysInMem.put(valueStr, keysInMem.size());
                keyList.add(valueStr);
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
     *
     * @return entryPointer to later fetch the entryMap via get
     */
    public long add(Map<String, String> entryMap) {
        if (entryMap.isEmpty())
            return EMPTY_POINTER;
        else if (entryMap.size() > 200)
            throw new IllegalArgumentException("Cannot store more than 200 entries per entry");

        // This is a very important compressing mechanism due to the nature of OSM. Make this faster via precalculated hastcodes?
        if (entryMap.equals(lastEntryMap))
            return lastEntryPointer;

        lastEntryMap = entryMap;
        lastEntryPointer = bytePointer;
        // while adding there could be exceptions and we need to avoid that the bytePointer is modified
        long currentPointer = bytePointer;

        vals.ensureCapacity(currentPointer + 1);
        vals.setByte(currentPointer, (byte) entryMap.size());
        currentPointer += 1;
        for (Map.Entry<String, String> entry : entryMap.entrySet()) {
            String key = entry.getKey(), value = entry.getValue();
            Integer keyIndex = keysInMem.get(key);
            if (keyIndex == null) {
                keyIndex = keysInMem.size();
                if (keyIndex >= MAX_UNIQUE_KEYS)
                    throw new IllegalArgumentException("Cannot store more than " + MAX_UNIQUE_KEYS + " unique keys");
                keysInMem.put(key, keyIndex);
                keyList.add(key);
            }

            if (value == null || value.isEmpty()) {
                vals.ensureCapacity(currentPointer + 3);
                vals.setShort(currentPointer, keyIndex.shortValue());
                // ensure that also in case of MMap value is set to 0
                vals.setByte(currentPointer + 2, (byte) 0);
                currentPointer += 3;
            } else {
                Long existingRef = smallCache.get(value);
                if (existingRef != null) {
                    long delta = lastEntryPointer - existingRef;
                    if (delta < Integer.MAX_VALUE && delta > Integer.MIN_VALUE) {
                        vals.ensureCapacity(currentPointer + 2 + 4);
                        vals.setShort(currentPointer, (short) -keyIndex);
                        currentPointer += 2;
                        // do not store valueBytes.length as we know it already: it is 4!
                        byte[] valueBytes = new byte[4];
                        BitUtil.LITTLE.fromInt(valueBytes, (int) delta);
                        vals.setBytes(currentPointer, valueBytes, valueBytes.length);
                        currentPointer += valueBytes.length;
                        continue;
                    } else {
                        smallCache.remove(value);
                    }
                }

                byte[] valueBytes = getBytesForString("Value for key" + key, value);
                // only cache value if storing via duplicate marker is valuable (the delta costs 4 bytes minus 1 due to omitted valueBytes.length storage)
                if (valueBytes.length > 3)
                    smallCache.put(value, currentPointer);

                vals.ensureCapacity(currentPointer + 2 + 1 + valueBytes.length);
                vals.setShort(currentPointer, keyIndex.shortValue());
                currentPointer += 2;
                vals.setByte(currentPointer, (byte) valueBytes.length);
                currentPointer++;
                vals.setBytes(currentPointer, valueBytes, valueBytes.length);
                currentPointer += valueBytes.length;
            }
        }
        bytePointer = currentPointer;
        return lastEntryPointer;
    }

    public Map<String, String> getAll(final long entryPointer) {
        if (entryPointer < 0)
            throw new IllegalStateException("Pointer to access StringIndex cannot be negative:" + entryPointer);

        if (entryPointer == EMPTY_POINTER)
            return Collections.emptyMap();

        int keyCount = vals.getByte(entryPointer) & 0xFF;
        if (keyCount == 0)
            return Collections.emptyMap();

        Map<String, String> map = new LinkedHashMap<>(keyCount);
        long tmpPointer = entryPointer + 1;
        for (int i = 0; i < keyCount; i++) {
            int currentKeyIndex = vals.getShort(tmpPointer);
            tmpPointer += 2;

            if (currentKeyIndex < 0) {
                currentKeyIndex = -currentKeyIndex;
                byte[] valueBytes = new byte[4];
                vals.getBytes(tmpPointer, valueBytes, valueBytes.length);
                tmpPointer += 4;

                long dupPointer = entryPointer - BitUtil.LITTLE.toInt(valueBytes);
                dupPointer += 2;
                if (dupPointer > bytePointer)
                    throw new IllegalStateException("dup marker should exist but points into not yet allocated area " + dupPointer + " > " + bytePointer);
                putIntoMap(map, dupPointer, currentKeyIndex);
            } else {
                int valueLength = putIntoMap(map, tmpPointer, currentKeyIndex);
                tmpPointer += 1 + valueLength;
            }
        }

        // value for specified key does not existing for the specified pointer
        return map;
    }

    private int putIntoMap(Map<String, String> map, long tmpPointer, int currentKeyIndex) {
        int valueLength = vals.getByte(tmpPointer) & 0xFF;
        tmpPointer++;
        String valueStr;
        if (valueLength == 0) {
            valueStr = "";
        } else {
            byte[] valueBytes = new byte[valueLength];
            vals.getBytes(tmpPointer, valueBytes, valueBytes.length);
            valueStr = new String(valueBytes, Helper.UTF_CS);
        }

        map.put(keyList.get(currentKeyIndex), valueStr);
        return valueLength;
    }

    public String get(final long entryPointer, String key) {
        if (entryPointer < 0)
            throw new IllegalStateException("Pointer to access StringIndex cannot be negative:" + entryPointer);

        if (entryPointer == EMPTY_POINTER)
            return "";

        int keyCount = vals.getByte(entryPointer) & 0xFF;
        if (keyCount == 0)
            return null;

        Integer keyIndex = keysInMem.get(key);
        // specified key is not known to the StringIndex
        if (keyIndex == null)
            return null;

        long tmpPointer = entryPointer + 1;
        for (int i = 0; i < keyCount; i++) {
            int currentKeyIndex = vals.getShort(tmpPointer);
            tmpPointer += 2;
            if (Math.abs(currentKeyIndex) == keyIndex) {
                if (currentKeyIndex < 0) {
                    byte[] valueBytes = new byte[4];
                    vals.getBytes(tmpPointer, valueBytes, valueBytes.length);
                    tmpPointer = entryPointer - BitUtil.LITTLE.toInt(valueBytes);
                    tmpPointer += 2;
                    if (tmpPointer > bytePointer)
                        throw new IllegalStateException("dup marker " + bytePointer + " should exist but points into not yet allocated area " + tmpPointer);
                }

                int valueLength = vals.getByte(tmpPointer) & 0xFF;
                if (valueLength == 0)
                    return "";

                tmpPointer++;
                byte[] valueBytes = new byte[valueLength];
                vals.getBytes(tmpPointer, valueBytes, valueBytes.length);
                return new String(valueBytes, Helper.UTF_CS);
            }
            int valueLength = vals.getByte(tmpPointer) & 0xFF;
            tmpPointer += 1 + valueLength;
        }

        // value for specified key does not exist for the specified pointer
        return null;
    }

    private byte[] getBytesForString(String info, String name) {
        byte[] bytes = name.getBytes(Helper.UTF_CS);
        if (bytes.length > MAX_LENGTH) {
            String newString = new String(bytes, 0, MAX_LENGTH, Helper.UTF_CS);
            if (throwExceptionIfTooLong)
                throw new IllegalStateException(info + " is too long: " + name + " truncated to " + newString);
            return newString.getBytes(Helper.UTF_CS);
        }

        return bytes;
    }

    public void flush() {
        keys.setHeader(0, Constants.VERSION_STRING_IDX);
        keys.ensureCapacity(2);
        keys.setShort(0, (short) keysInMem.size());
        long keyBytePointer = 2;
        for (String key : keysInMem.keySet()) {
            byte[] keyBytes = getBytesForString("key", key);
            keys.ensureCapacity(keyBytePointer + 2 + keyBytes.length);
            keys.setShort(keyBytePointer, (short) keyBytes.length);
            keyBytePointer += 2;

            keys.setBytes(keyBytePointer, keyBytes, keyBytes.length);
            keyBytePointer += keyBytes.length;
        }
        keys.flush();

        vals.setHeader(0, Constants.VERSION_STRING_IDX);
        vals.setHeader(4, BitUtil.LITTLE.getIntLow(bytePointer));
        vals.setHeader(8, BitUtil.LITTLE.getIntHigh(bytePointer));
        vals.flush();
    }

    public void close() {
        keys.close();
        vals.close();
    }

    public boolean isClosed() {
        return vals.isClosed() && keys.isClosed();
    }

    public long getCapacity() {
        return vals.getCapacity() + keys.getCapacity();
    }

}
