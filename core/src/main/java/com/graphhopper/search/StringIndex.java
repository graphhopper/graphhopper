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
    private final DataAccess strings;
    private final List<String> keys = new ArrayList<>(32);
    private long bytePointer = START_POINTER;

    public StringIndex(Directory dir) {
        strings = dir.find("string_index");
    }

    @Override
    public StringIndex create(long initBytes) {
        strings.create(initBytes);
        return this;
    }

    @Override
    public boolean loadExisting() {
        if (strings.loadExisting()) {
            bytePointer = BitUtil.LITTLE.combineIntsToLong(strings.getHeader(0), strings.getHeader(4));
            return true;
        }

        return false;
    }

    /**
     * This method remove keys of one duplicated value and puts this value as the first to optimize this map for storage.
     */
    public static Map<String, String> cleanup(Map<String, String> entryMap) {
        if (entryMap.isEmpty())
            return entryMap;
        Set<String> existingStrings = new HashSet<>(Math.min(10, entryMap.size()));
        for (Map.Entry<String, String> entry : entryMap.entrySet()) {
            if (existingStrings.contains(entry.getValue())) {
                Map<String, String> orderedMap = new LinkedHashMap<>(entryMap.size() - 1); // eventually even smaller
                orderedMap.put(entry.getKey(), entry.getValue());
                for (Map.Entry<String, String> newEntry : entryMap.entrySet()) {
                    if (!newEntry.getValue().equals(entry.getValue()))
                        orderedMap.put(newEntry.getKey(), newEntry.getValue());
                }
                return orderedMap;
            }

            existingStrings.add(entry.getValue());
        }
        return entryMap;
    }

    /**
     * This method writes the specified map into the storage.
     */
    public long put(Map<String, String> entryMap) {
        if (entryMap.isEmpty())
            return EMPTY_POINTER;

        long oldPointer = bytePointer;
        strings.setInt(bytePointer, entryMap.size());
        for (Map.Entry<String, String> entry : entryMap.entrySet()) {
            put(entryMap.size(), bytePointer, entry.getKey(), entry.getValue());
        }
        return oldPointer;
    }

    private void put(int size, long pointer, String key, String name) {

        int index = keys.indexOf(key);
        if (index < 0) {
            index = keys.size();
            keys.add(key);
        }

        // without current key
        int[] currentKeys = readKeys(pointer);
        byte[] nameBytes = getBytesForString(key, name);
        strings.ensureCapacity(pointer + 1 + 2 * currentKeys.length + nameBytes.length);

        int length = 0;
        // move values to avoid being overwritten
        for (int i = 0; i < currentKeys.length; i++) {
            length += strings.getShort(pointer + 1 + 2 * i) & 0xFF;
        }
        byte[] bytes = new byte[length];
        strings.getBytes(pointer + 1 + currentKeys.length * 2, bytes, length);
        strings.setBytes(pointer + 1 + (currentKeys.length + 1) * 2, bytes, length);

        strings.setInt(pointer, currentKeys.length);
        int offset = 1;
        strings.setInt(pointer + offset, index);
        for (int i = 0; i < currentKeys.length; i++) {
            strings.setInt(pointer + offset, currentKeys[i]);
            strings.setInt(pointer + offset, currentKeys[i]);
            offset += 2;
        }
    }

    public String get(long pointer) {
        return get(pointer, "");
    }

    public String get(long pointer, String key) {
        if (pointer < 0)
            throw new IllegalStateException("Pointer to access StringIndex cannot be negative:" + pointer);

        if (pointer == EMPTY_POINTER)
            return "";

        return "";
    }

    private int[] readKeys(long pointer) {
        return new int[0];
    }

    private byte[] getBytesForString(String key, String name) {
        byte[] bytes = name.getBytes(Helper.UTF_CS);
        // store size of byte array into *one* byte
        if (bytes.length > 255) {
            String newName = name.substring(0, 256 / 4);
            logger.warn("Name for key " + key + " is too long: " + name + " truncated to " + newName);
            bytes = newName.getBytes(Helper.UTF_CS);
        }

        if (bytes.length > 255)
            // really make sure no such problem exists
            throw new IllegalStateException("Name for key " + key + " is too long: " + name);

        return bytes;
    }

    @Override
    public void flush() {
        strings.setHeader(0, BitUtil.LITTLE.getIntLow(bytePointer));
        strings.setHeader(4, BitUtil.LITTLE.getIntHigh(bytePointer));
        strings.flush();
    }

    @Override
    public void close() {
        strings.close();
    }

    @Override
    public boolean isClosed() {
        return strings.isClosed();
    }

    public void setSegmentSize(int segments) {
        strings.setSegmentSize(segments);
    }

    @Override
    public long getCapacity() {
        return strings.getCapacity();
    }

    public void copyTo(StringIndex nameIndex) {
        strings.copyTo(nameIndex.strings);
    }
}
