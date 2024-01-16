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

/**
 * @author Ottavio Campana
 * @author Peter Karich
 */
public class NameIndex implements Storable<NameIndex> {
    private static final Logger logger = LoggerFactory.getLogger(NameIndex.class);
    private static final long START_POINTER = 1;
    private final DataAccess names;
    private long bytePointer = START_POINTER;
    // minor optimization for the previous stored name
    private String lastName;
    private long lastIndex;

    public NameIndex(Directory dir) {
        this(dir, "names");
    }

    protected NameIndex(Directory dir, String filename) {
        names = dir.find(filename);
    }

    public NameIndex create(long initBytes) {
        names.create(initBytes);
        return this;
    }

    public boolean loadExisting() {
        if (names.loadExisting()) {
            bytePointer = BitUtil.LITTLE.combineIntsToLong(names.getHeader(0), names.getHeader(4));
            return true;
        }

        return false;
    }

    /**
     * @return the byte pointer to the name
     */
    public long put(String name) {
        if (name == null || name.isEmpty()) {
            return 0;
        }
        if (name.equals(lastName)) {
            return lastIndex;
        }
        byte[] bytes = getBytes(name);
        long oldPointer = bytePointer;
        names.ensureCapacity(bytePointer + 1 + bytes.length);
        byte[] sizeBytes = new byte[]{
                (byte) bytes.length
        };
        names.setBytes(bytePointer, sizeBytes, sizeBytes.length);
        bytePointer++;
        names.setBytes(bytePointer, bytes, bytes.length);
        bytePointer += bytes.length;
        lastName = name;
        lastIndex = oldPointer;
        return oldPointer;
    }

    private byte[] getBytes(String name) {
        byte[] bytes = null;
        for (int i = 0; i < 2; i++) {
            bytes = name.getBytes(Helper.UTF_CS);
            // we have to store the size of the array into *one* byte
            if (bytes.length > 255) {
                String newName = name.substring(0, 256 / 4);
                logger.info("Way name is too long: " + name + " truncated to " + newName);
                name = newName;
                continue;
            }
            break;
        }
        if (bytes.length > 255) {
            // really make sure no such problem exists
            throw new IllegalStateException("Way name is too long: " + name);
        }
        return bytes;
    }

    public String get(long pointer) {
        if (pointer < 0)
            throw new IllegalStateException("Pointer to access NameIndex cannot be negative:" + pointer);

        // default
        if (pointer == 0)
            return "";

        byte[] sizeBytes = new byte[1];
        names.getBytes(pointer, sizeBytes, 1);
        int size = sizeBytes[0] & 0xFF;
        byte[] bytes = new byte[size];
        names.getBytes(pointer + sizeBytes.length, bytes, size);
        return new String(bytes, Helper.UTF_CS);
    }

    public void flush() {
        names.setHeader(0, BitUtil.LITTLE.getIntLow(bytePointer));
        names.setHeader(4, BitUtil.LITTLE.getIntHigh(bytePointer));
        names.flush();
    }

    @Override
    public void close() {
        names.close();
    }

    @Override
    public boolean isClosed() {
        return names.isClosed();
    }

    public long getCapacity() {
        return names.getCapacity();
    }

}
