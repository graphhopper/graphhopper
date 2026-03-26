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
package com.graphhopper.storage;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;

/**
 * Based on a single int[] array, which provides faster reading speed than RAMIntDataAccess which uses nested int[][].
 * On the flip-side resizing requires expensive copying, and the number of elements is limited to 2B four-byte integers (~8GB).
 */
public class RAMInt1SegmentDataAccess extends AbstractDataAccess {
    private int[] data = new int[0];
    private final boolean store;

    public RAMInt1SegmentDataAccess(String name, String location, boolean store, int segmentSize) {
        super(name, location, segmentSize);
        this.store = store;
    }

    @Override
    public RAMInt1SegmentDataAccess create(long bytes) {
        if (data.length > 0)
            throw new IllegalThreadStateException("already created");
        ensureCapacity(Math.max(10 * 4, bytes));
        return this;
    }

    @Override
    public boolean ensureCapacity(long bytes) {
        if (bytes < 0)
            throw new IllegalArgumentException("new capacity has to be strictly positive");

        long cap = getCapacity();
        if (bytes <= cap)
            return false;

        // round up to segment size for compatibility with the file format used by other DataAccess implementations
        long newCap = bytes;
        if (newCap % segmentSizeInBytes != 0)
            newCap = (newCap / segmentSizeInBytes + 1) * segmentSizeInBytes;
        if (newCap / 4 > Integer.MAX_VALUE)
            throw new RuntimeException("Cannot ensure capacity for " + bytes + " bytes using RAMInt1SegmentDataAccess. Max: " + (Integer.MAX_VALUE * 4L));

        try {
            data = Arrays.copyOf(data, (int) (newCap / 4));
        } catch (OutOfMemoryError err) {
            throw new OutOfMemoryError(err.getMessage() + " - problem when allocating new memory. Old capacity: "
                    + cap + ", requested bytes:" + bytes);
        }
        return true;
    }

    @Override
    public boolean loadExisting() {
        if (data.length > 0)
            throw new IllegalStateException("already initialized");

        if (isClosed())
            throw new IllegalStateException("already closed");

        if (!store)
            return false;

        File file = new File(getFullName());
        if (!file.exists() || file.length() == 0)
            return false;

        try {
            try (RandomAccessFile raFile = new RandomAccessFile(getFullName(), "r")) {
                long byteCount = readHeader(raFile) - HEADER_OFFSET;
                if (byteCount < 0) {
                    return false;
                }
                byte[] bytes = new byte[segmentSizeInBytes];
                raFile.seek(HEADER_OFFSET);
                // raFile.readInt() <- too slow, so read into byte buffer and convert
                int segmentCount = (int) (byteCount / segmentSizeInBytes);
                if (byteCount % segmentSizeInBytes != 0)
                    segmentCount++;

                int intsPerSegment = segmentSizeInBytes / 4;
                long totalInts = (long) segmentCount * intsPerSegment;
                if (totalInts > Integer.MAX_VALUE)
                    throw new RuntimeException("File " + getFullName() + " is too large to be loaded with RAMInt1SegmentDataAccess. total ints: " + totalInts);
                data = new int[segmentCount * intsPerSegment];
                for (int s = 0; s < segmentCount; s++) {
                    int read = raFile.read(bytes) / 4;
                    int offset = s * intsPerSegment;
                    for (int j = 0; j < read; j++) {
                        data[offset + j] = bitUtil.toInt(bytes, j * 4);
                    }
                }
                return true;
            }
        } catch (IOException ex) {
            throw new RuntimeException("Problem while loading " + getFullName(), ex);
        }
    }

    @Override
    public void flush() {
        if (closed)
            throw new IllegalStateException("already closed");
        if (!store)
            return;

        try {
            try (RandomAccessFile raFile = new RandomAccessFile(getFullName(), "rw")) {
                long len = getCapacity();
                writeHeader(raFile, len, segmentSizeInBytes);
                raFile.seek(HEADER_OFFSET);
                // raFile.writeInt() <- too slow, so copy into byte array
                int segmentCount = getSegments();
                int intsPerSegment = segmentSizeInBytes / 4;
                for (int s = 0; s < segmentCount; s++) {
                    int offset = s * intsPerSegment;
                    int intLen = Math.min(intsPerSegment, data.length - offset);
                    byte[] byteArea = new byte[intLen * 4];
                    for (int i = 0; i < intLen; i++) {
                        bitUtil.fromInt(byteArea, data[offset + i], i * 4);
                    }
                    raFile.write(byteArea);
                }
                raFile.setLength(HEADER_OFFSET + len);
            }
        } catch (Exception ex) {
            throw new RuntimeException("Couldn't store integers to " + toString(), ex);
        }
    }

    @Override
    public final void setInt(long bytePos, int value) {
        assert data.length > 0 : "call create or loadExisting before usage!";
        data[(int) (bytePos >>> 2)] = value;
    }

    @Override
    public final int getInt(long bytePos) {
        assert data.length > 0 : "call create or loadExisting before usage!";
        return data[(int) (bytePos >>> 2)];
    }

    public final int getIntRaw(int index) {
        return data[index];
    }

    @Override
    public final void setShort(long bytePos, short value) {
        throw new UnsupportedOperationException(this + " does not support short access. Use RAMDataAccess instead");
    }

    @Override
    public final short getShort(long bytePos) {
        throw new UnsupportedOperationException(this + " does not support short access. Use RAMDataAccess instead");
    }

    @Override
    public void setBytes(long bytePos, byte[] values, int length) {
        throw new UnsupportedOperationException(this + " does not support byte based access. Use RAMDataAccess instead");
    }

    @Override
    public void getBytes(long bytePos, byte[] values, int length) {
        throw new UnsupportedOperationException(this + " does not support byte based access. Use RAMDataAccess instead");
    }

    @Override
    public final void setByte(long bytePos, byte value) {
        throw new UnsupportedOperationException(this + " does not support byte based access. Use RAMDataAccess instead");
    }

    @Override
    public final byte getByte(long bytePos) {
        throw new UnsupportedOperationException(this + " does not support byte based access. Use RAMDataAccess instead");
    }

    @Override
    public void trimTo(long capacity) {
        if (capacity < 0)
            throw new IllegalArgumentException("capacity must not be negative");
        if (capacity > getCapacity())
            throw new IllegalArgumentException("capacity cannot be larger than the current capacity: " + capacity + " > " + getCapacity());

        int newSegmentCount = (int) (capacity / segmentSizeInBytes);
        if (capacity % segmentSizeInBytes != 0)
            newSegmentCount++;

        int newIntCount = newSegmentCount * (segmentSizeInBytes / 4);
        if (newIntCount < data.length)
            data = Arrays.copyOf(data, newIntCount);
    }

    @Override
    public void close() {
        super.close();
        data = new int[0];
    }

    @Override
    public long getCapacity() {
        return (long) data.length * 4;
    }

    @Override
    public int getSegments() {
        return data.length / (segmentSizeInBytes / 4);
    }

    @Override
    public boolean isStoring() {
        return store;
    }

    @Override
    protected boolean isIntBased() {
        return true;
    }

    @Override
    public DAType getType() {
        if (isStoring())
            return DAType.RAM_INT_1SEG_STORE;
        return DAType.RAM_INT_1SEG;
    }
}
