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
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * In-memory byte-based DataAccess backed by a single contiguous {@code byte[]}. No segment
 * boundary logic, no index math — direct byte-position access via {@link VarHandle}.
 * This is the on-heap equivalent of {@link ForeignMemoryDataAccess}.
 * <p>
 * Limited to ~2GB (Java array size limit).
 */
public class RAM1SegmentDataAccess extends AbstractDataAccess {
    private static final VarHandle INT = MethodHandles.byteArrayViewVarHandle(int[].class, ByteOrder.LITTLE_ENDIAN).withInvokeExactBehavior();
    private static final VarHandle SHORT = MethodHandles.byteArrayViewVarHandle(short[].class, ByteOrder.LITTLE_ENDIAN).withInvokeExactBehavior();

    private byte[] data = new byte[0];
    private boolean store;

    public RAM1SegmentDataAccess(String name, String location, boolean store, int segmentSize) {
        super(name, location, segmentSize);
        this.store = store;
    }

    @Override
    public RAM1SegmentDataAccess create(long bytes) {
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

        try {
            data = Arrays.copyOf(data, (int) Math.min(newCap, Integer.MAX_VALUE - 8));
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

                data = new byte[(int) allocSize];
                int offset = 0;
                while (offset < byteCount) {
                    int read = raFile.read(data, offset, (int) Math.min(byteCount - offset, segmentSizeInBytes));
                    if (read <= 0)
                        throw new IllegalStateException("unexpected end of file at offset " + offset + " " + toString());
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
                raFile.write(data);
                raFile.setLength(HEADER_OFFSET + len);
            }
        } catch (Exception ex) {
            throw new RuntimeException("Couldn't store bytes to " + toString(), ex);
        }
    }

    @Override
    public final void setInt(long bytePos, int value) {
        INT.set(data, (int) bytePos, value);
    }

    @Override
    public final int getInt(long bytePos) {
        return (int) INT.get(data, (int) bytePos);
    }

    @Override
    public final void setShort(long bytePos, short value) {
        SHORT.set(data, (int) bytePos, value);
    }

    @Override
    public final short getShort(long bytePos) {
        return (short) SHORT.get(data, (int) bytePos);
    }

    @Override
    public void setBytes(long bytePos, byte[] values, int length) {
        System.arraycopy(values, 0, data, (int) bytePos, length);
    }

    @Override
    public void getBytes(long bytePos, byte[] values, int length) {
        System.arraycopy(data, (int) bytePos, values, 0, length);
    }

    @Override
    public final void setByte(long bytePos, byte value) {
        data[(int) bytePos] = value;
    }

    @Override
    public final byte getByte(long bytePos) {
        return data[(int) bytePos];
    }

    @Override
    public void trimTo(long capacity) {
        if (capacity < 0)
            throw new IllegalArgumentException("capacity must not be negative");
        if (capacity > getCapacity())
            throw new IllegalArgumentException("capacity cannot be larger than the current capacity: " + capacity + " > " + getCapacity());

        // Round up to segment size boundary
        long newCap = capacity;
        if (newCap % segmentSizeInBytes != 0)
            newCap = (newCap / segmentSizeInBytes + 1) * segmentSizeInBytes;

        if (newCap < data.length)
            data = Arrays.copyOf(data, (int) newCap);
    }

    @Override
    public void close() {
        super.close();
        data = new byte[0];
    }

    @Override
    public long getCapacity() {
        return data.length;
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
