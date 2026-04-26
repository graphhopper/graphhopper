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
 * In-memory DataAccess backed by a single contiguous {@code long[]}. Ints are packed into longs
 * (two ints per long) and extracted via bit shifting. No segment boundary logic.
 * <p>
 * Limited to ~16GB ({@code Integer.MAX_VALUE} longs * 8 bytes).
 */
public class RAMLongDataAccess extends AbstractDataAccess {
    private long[] data = new long[0];
    private boolean store;

    public RAMLongDataAccess(String name, String location, boolean store, int segmentSize) {
        super(name, location, segmentSize);
        this.store = store;
    }

    @Override
    public RAMLongDataAccess create(long bytes) {
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

        // Round up to segment size boundary for API compatibility
        long newCap = bytes;
        if (newCap % segmentSizeInBytes != 0)
            newCap = (newCap / segmentSizeInBytes + 1) * segmentSizeInBytes;

        int newLongCount = (int) (newCap / 8);
        if (newCap % 8 != 0) newLongCount++;

        try {
            data = Arrays.copyOf(data, newLongCount);
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
                if (byteCount < 0)
                    return false;

                raFile.seek(HEADER_OFFSET);
                long allocSize = byteCount;
                if (allocSize % segmentSizeInBytes != 0)
                    allocSize = (allocSize / segmentSizeInBytes + 1) * segmentSizeInBytes;

                int longCount = (int) (allocSize / 8);
                if (allocSize % 8 != 0) longCount++;
                data = new long[longCount];

                byte[] buffer = new byte[segmentSizeInBytes];
                int offset = 0;
                while (offset < byteCount) {
                    int toRead = (int) Math.min(byteCount - offset, segmentSizeInBytes);
                    int read = raFile.read(buffer, 0, toRead);
                    if (read <= 0)
                        throw new IllegalStateException("unexpected end of file at offset " + offset + " " + toString());
                    // Convert bytes to longs (little-endian)
                    for (int i = 0; i < read; i++) {
                        int longIdx = (offset + i) / 8;
                        int shift = ((offset + i) % 8) * 8;
                        data[longIdx] |= (buffer[i] & 0xFFL) << shift;
                    }
                    offset += read;
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

                byte[] buffer = new byte[segmentSizeInBytes];
                long remaining = len;
                int longOffset = 0;
                while (remaining > 0) {
                    int chunk = (int) Math.min(remaining, segmentSizeInBytes);
                    for (int i = 0; i < chunk; i++) {
                        int longIdx = (longOffset + i) / 8;
                        int shift = ((longOffset + i) % 8) * 8;
                        buffer[i] = (byte) (data[longIdx] >>> shift);
                    }
                    raFile.write(buffer, 0, chunk);
                    remaining -= chunk;
                    longOffset += chunk;
                }
                raFile.setLength(HEADER_OFFSET + len);
            }
        } catch (Exception ex) {
            throw new RuntimeException("Couldn't store bytes to " + toString(), ex);
        }
    }

    @Override
    public final void setInt(long bytePos, int value) {
        int longIdx = (int) (bytePos >>> 3);
        int byteOff = (int) bytePos & 7;
        if (byteOff <= 4) {
            // Aligned: fits within one long (offsets 0 or 4)
            int shift = byteOff * 8;
            long mask = ~(0xFFFFFFFFL << shift);
            data[longIdx] = (data[longIdx] & mask) | ((value & 0xFFFFFFFFL) << shift);
        } else {
            // Unaligned: spans two longs (offsets 5, 6, 7)
            setByte(bytePos, (byte) value);
            setByte(bytePos + 1, (byte) (value >>> 8));
            setByte(bytePos + 2, (byte) (value >>> 16));
            setByte(bytePos + 3, (byte) (value >>> 24));
        }
    }

    @Override
    public final int getInt(long bytePos) {
        int longIdx = (int) (bytePos >>> 3);
        int byteOff = (int) bytePos & 7;
        if (byteOff <= 4) {
            int shift = byteOff * 8;
            return (int) (data[longIdx] >>> shift);
        } else {
            return (getByte(bytePos) & 0xFF)
                    | (getByte(bytePos + 1) & 0xFF) << 8
                    | (getByte(bytePos + 2) & 0xFF) << 16
                    | (getByte(bytePos + 3) & 0xFF) << 24;
        }
    }

    @Override
    public final void setShort(long bytePos, short value) {
        int longIdx = (int) (bytePos >>> 3);
        int byteOff = (int) bytePos & 7;
        if (byteOff == 7) {
            // Spans two longs
            setByte(bytePos, (byte) value);
            setByte(bytePos + 1, (byte) (value >>> 8));
        } else {
            int shift = byteOff * 8;
            long mask = ~(0xFFFFL << shift);
            data[longIdx] = (data[longIdx] & mask) | ((value & 0xFFFFL) << shift);
        }
    }

    @Override
    public final short getShort(long bytePos) {
        int longIdx = (int) (bytePos >>> 3);
        int byteOff = (int) bytePos & 7;
        if (byteOff == 7) {
            return (short) ((getByte(bytePos + 1) & 0xFF) << 8 | (getByte(bytePos) & 0xFF));
        }
        int shift = byteOff * 8;
        return (short) (data[longIdx] >>> shift);
    }

    @Override
    public void setBytes(long bytePos, byte[] values, int length) {
        for (int i = 0; i < length; i++) {
            setByte(bytePos + i, values[i]);
        }
    }

    @Override
    public void getBytes(long bytePos, byte[] values, int length) {
        for (int i = 0; i < length; i++) {
            values[i] = getByte(bytePos + i);
        }
    }

    @Override
    public final void setByte(long bytePos, byte value) {
        int longIdx = (int) (bytePos >>> 3);
        int shift = ((int) bytePos & 7) * 8;
        long mask = ~(0xFFL << shift);
        data[longIdx] = (data[longIdx] & mask) | ((value & 0xFFL) << shift);
    }

    @Override
    public final byte getByte(long bytePos) {
        int longIdx = (int) (bytePos >>> 3);
        int shift = ((int) bytePos & 7) * 8;
        return (byte) (data[longIdx] >>> shift);
    }

    @Override
    public void trimTo(long capacity) {
        if (capacity < 0)
            throw new IllegalArgumentException("capacity must not be negative");
        if (capacity > getCapacity())
            throw new IllegalArgumentException("capacity cannot be larger than the current capacity: " + capacity + " > " + getCapacity());

        long newCap = capacity;
        if (newCap % segmentSizeInBytes != 0)
            newCap = (newCap / segmentSizeInBytes + 1) * segmentSizeInBytes;

        int newLongCount = (int) (newCap / 8);
        if (newCap % 8 != 0) newLongCount++;

        if (newLongCount < data.length)
            data = Arrays.copyOf(data, newLongCount);
    }

    @Override
    public void close() {
        super.close();
        data = new long[0];
    }

    @Override
    public long getCapacity() {
        return (long) data.length * 8;
    }

    @Override
    public int getSegments() {
        long cap = getCapacity();
        if (cap == 0) return 0;
        int segs = (int) (cap / segmentSizeInBytes);
        if (cap % segmentSizeInBytes != 0)
            segs++;
        return segs;
    }

    @Override
    public boolean isStoring() {
        return store;
    }

    @Override
    public DAType getType() {
        if (isStoring())
            return DAType.RAM_STORE;
        return DAType.RAM;
    }
}
