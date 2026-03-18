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
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteOrder;

/**
 * Off-heap DataAccess backed by a single contiguous {@link MemorySegment} via the Foreign Memory API.
 * Direct equivalent of the old UnsafeDataAccess: long-indexed access, no segment boundary logic.
 * <p>
 * Requires Java 21 with {@code --enable-preview} (or Java 22+).
 */
public class ForeignMemoryDataAccess extends AbstractDataAccess {
    private static final ValueLayout.OfInt INT_LE =
            ValueLayout.JAVA_INT.withOrder(ByteOrder.LITTLE_ENDIAN).withByteAlignment(1);
    private static final ValueLayout.OfShort SHORT_LE =
            ValueLayout.JAVA_SHORT.withOrder(ByteOrder.LITTLE_ENDIAN).withByteAlignment(1);
    private static final ValueLayout.OfByte BYTE_LAYOUT = ValueLayout.JAVA_BYTE;

    private Arena arena;
    private MemorySegment segment = MemorySegment.NULL;
    private long capacity;
    private boolean store;

    public ForeignMemoryDataAccess(String name, String location, boolean store, int segmentSize) {
        super(name, location, segmentSize);
        this.store = store;
    }

    @Override
    public ForeignMemoryDataAccess create(long bytes) {
        if (capacity > 0)
            throw new IllegalThreadStateException("already created");
        ensureCapacity(Math.max(10 * 4, bytes));
        return this;
    }

    @Override
    public boolean ensureCapacity(long bytes) {
        if (bytes < 0)
            throw new IllegalArgumentException("new capacity has to be strictly positive");

        long newBytes = bytes - capacity;
        if (newBytes <= 0)
            return false;

        long totalNeeded = bytes;
        int segmentsNeeded = (int) (totalNeeded / segmentSizeInBytes);
        if (totalNeeded % segmentSizeInBytes != 0)
            segmentsNeeded++;
        long newCapacity = (long) segmentsNeeded * segmentSizeInBytes;

        try {
            Arena newArena = Arena.ofShared();
            MemorySegment newSegment = newArena.allocate(newCapacity);
            newSegment.fill((byte) 0);

            if (capacity > 0) {
                MemorySegment.copy(segment, 0, newSegment, 0, capacity);
                arena.close();
            }

            arena = newArena;
            segment = newSegment;
            capacity = newCapacity;
        } catch (OutOfMemoryError err) {
            throw new OutOfMemoryError(err.getMessage() + " - problem when allocating new memory. Old capacity: "
                    + capacity + ", new bytes:" + newBytes + ", segmentSizeIntsPower:" + segmentSizePower
                    + ", requested:" + newCapacity);
        }
        return true;
    }

    @Override
    public boolean loadExisting() {
        if (capacity > 0)
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

                int segmentCount = (int) (byteCount / segmentSizeInBytes);
                if (byteCount % segmentSizeInBytes != 0)
                    segmentCount++;
                long totalCapacity = (long) segmentCount * segmentSizeInBytes;

                arena = Arena.ofShared();
                segment = arena.allocate(totalCapacity);
                segment.fill((byte) 0);
                capacity = totalCapacity;

                byte[] buffer = new byte[segmentSizeInBytes];
                for (int s = 0; s < segmentCount; s++) {
                    int read = raFile.read(buffer);
                    if (read <= 0)
                        throw new IllegalStateException("segment " + s + " is empty? " + toString());
                    MemorySegment.copy(buffer, 0, segment, BYTE_LAYOUT, (long) s * segmentSizeInBytes, read);
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
                int segmentCount = getSegments();
                for (int s = 0; s < segmentCount; s++) {
                    MemorySegment.copy(segment, BYTE_LAYOUT, (long) s * segmentSizeInBytes, buffer, 0, segmentSizeInBytes);
                    raFile.write(buffer);
                }
                raFile.setLength(HEADER_OFFSET + len);
            }
        } catch (Exception ex) {
            throw new RuntimeException("Couldn't store bytes to " + toString(), ex);
        }
    }

    @Override
    public final void setInt(long bytePos, int value) {
        assert capacity > 0 : "call create or loadExisting before usage!";
        segment.set(INT_LE, bytePos, value);
    }

    @Override
    public final int getInt(long bytePos) {
        assert capacity > 0 : "call create or loadExisting before usage!";
        return segment.get(INT_LE, bytePos);
    }

    @Override
    public final void setShort(long bytePos, short value) {
        assert capacity > 0 : "call create or loadExisting before usage!";
        segment.set(SHORT_LE, bytePos, value);
    }

    @Override
    public final short getShort(long bytePos) {
        assert capacity > 0 : "call create or loadExisting before usage!";
        return segment.get(SHORT_LE, bytePos);
    }

    @Override
    public void setBytes(long bytePos, byte[] values, int length) {
        assert capacity > 0 : "call create or loadExisting before usage!";
        MemorySegment.copy(values, 0, segment, BYTE_LAYOUT, bytePos, length);
    }

    @Override
    public void getBytes(long bytePos, byte[] values, int length) {
        assert capacity > 0 : "call create or loadExisting before usage!";
        MemorySegment.copy(segment, BYTE_LAYOUT, bytePos, values, 0, length);
    }

    @Override
    public final void setByte(long bytePos, byte value) {
        assert capacity > 0 : "call create or loadExisting before usage!";
        segment.set(BYTE_LAYOUT, bytePos, value);
    }

    @Override
    public final byte getByte(long bytePos) {
        assert capacity > 0 : "call create or loadExisting before usage!";
        return segment.get(BYTE_LAYOUT, bytePos);
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
        long newCapacity = (long) newSegmentCount * segmentSizeInBytes;

        if (newCapacity < this.capacity) {
            if (newCapacity == 0) {
                if (arena != null) arena.close();
                arena = null;
                segment = MemorySegment.NULL;
                this.capacity = 0;
            } else {
                Arena newArena = Arena.ofShared();
                MemorySegment newSegment = newArena.allocate(newCapacity);
                MemorySegment.copy(segment, 0, newSegment, 0, newCapacity);
                arena.close();
                arena = newArena;
                segment = newSegment;
                this.capacity = newCapacity;
            }
        }
    }

    @Override
    public void close() {
        super.close();
        if (arena != null) {
            arena.close();
            arena = null;
        }
        segment = MemorySegment.NULL;
        capacity = 0;
    }

    @Override
    public long getCapacity() {
        return capacity;
    }

    @Override
    public int getSegments() {
        return (int) (capacity / segmentSizeInBytes);
    }

    @Override
    public boolean isStoring() {
        return store;
    }

    @Override
    public DAType getType() {
        if (isStoring())
            return DAType.FOREIGN_MEMORY_STORE;
        return DAType.FOREIGN_MEMORY;
    }
}
