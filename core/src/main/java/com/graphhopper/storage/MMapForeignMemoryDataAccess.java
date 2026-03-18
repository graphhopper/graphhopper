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

import com.graphhopper.util.Helper;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;

/**
 * Memory-mapped DataAccess backed by a single contiguous {@link MemorySegment} via the Foreign
 * Memory API's {@link FileChannel#map(FileChannel.MapMode, long, long, Arena)} method.
 * No segment boundary logic — direct long-indexed access.
 * <p>
 * Optionally forces all pages into physical RAM via {@link MemorySegment#load()} to avoid
 * page faults during access (similar to {@code mlock}/{@code MAP_POPULATE}).
 * <p>
 * Requires Java 22+.
 */
public class MMapForeignMemoryDataAccess extends AbstractDataAccess {
    private static final ValueLayout.OfInt INT_LE =
            ValueLayout.JAVA_INT.withOrder(ByteOrder.LITTLE_ENDIAN).withByteAlignment(1);
    private static final ValueLayout.OfShort SHORT_LE =
            ValueLayout.JAVA_SHORT.withOrder(ByteOrder.LITTLE_ENDIAN).withByteAlignment(1);
    private static final ValueLayout.OfByte BYTE_LAYOUT = ValueLayout.JAVA_BYTE;

    private Arena arena;
    private MemorySegment segment = MemorySegment.NULL;
    private long capacity;
    private RandomAccessFile raFile;
    private final boolean allowWrites;
    private boolean preload;

    public MMapForeignMemoryDataAccess(String name, String location, boolean allowWrites, int segmentSize) {
        super(name, location, segmentSize);
        this.allowWrites = allowWrites;
    }

    /**
     * If true, forces all mapped pages into physical RAM after mapping.
     * This avoids page faults during access at the cost of upfront loading time.
     */
    public MMapForeignMemoryDataAccess setPreload(boolean preload) {
        this.preload = preload;
        return this;
    }

    private void initRandomAccessFile() {
        if (raFile != null)
            return;
        try {
            raFile = new RandomAccessFile(getFullName(), allowWrites ? "rw" : "r");
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    private void mapSegment(long offset, long size) {
        try {
            if (arena != null)
                arena.close();

            arena = Arena.ofShared();
            FileChannel.MapMode mode = allowWrites ? FileChannel.MapMode.READ_WRITE : FileChannel.MapMode.READ_ONLY;
            segment = raFile.getChannel().map(mode, offset, size, arena);
            capacity = size;

            if (preload)
                segment.load();
        } catch (IOException ex) {
            throw new RuntimeException("Couldn't map " + size + " bytes at offset " + offset
                    + " for " + getFullName(), ex);
        }
    }

    @Override
    public MMapForeignMemoryDataAccess create(long bytes) {
        if (capacity > 0)
            throw new IllegalThreadStateException("already created");
        initRandomAccessFile();
        ensureCapacity(Math.max(10 * 4, bytes));
        return this;
    }

    @Override
    public boolean ensureCapacity(long bytes) {
        if (bytes < 0)
            throw new IllegalArgumentException("new capacity has to be strictly positive");

        if (bytes <= capacity)
            return false;

        int segmentsNeeded = (int) (bytes / segmentSizeInBytes);
        if (bytes % segmentSizeInBytes != 0)
            segmentsNeeded++;
        long newCapacity = (long) segmentsNeeded * segmentSizeInBytes;

        try {
            // Flush dirty pages before remapping
            if (capacity > 0)
                segment.force();

            raFile.setLength(HEADER_OFFSET + newCapacity);
            mapSegment(HEADER_OFFSET, newCapacity);
            return true;
        } catch (IOException ex) {
            throw new RuntimeException("Couldn't ensure capacity " + newCapacity + " for " + getFullName(), ex);
        }
    }

    @Override
    public boolean loadExisting() {
        if (capacity > 0)
            throw new IllegalStateException("already initialized");
        if (isClosed())
            throw new IllegalStateException("already closed");

        File file = new File(getFullName());
        if (!file.exists() || file.length() == 0)
            return false;

        initRandomAccessFile();
        try {
            long byteCount = readHeader(raFile) - HEADER_OFFSET;
            if (byteCount < 0)
                return false;

            int segmentCount = (int) (byteCount / segmentSizeInBytes);
            if (byteCount % segmentSizeInBytes != 0)
                segmentCount++;
            long totalCapacity = (long) segmentCount * segmentSizeInBytes;

            mapSegment(HEADER_OFFSET, totalCapacity);
            return true;
        } catch (IOException ex) {
            throw new RuntimeException("Problem while loading " + getFullName(), ex);
        }
    }

    @Override
    public void flush() {
        if (closed)
            throw new IllegalStateException("already closed");

        try {
            segment.force();
            writeHeader(raFile, HEADER_OFFSET + capacity, segmentSizeInBytes);
            raFile.getFD().sync();
        } catch (Exception ex) {
            throw new RuntimeException("Couldn't flush " + getFullName(), ex);
        }
    }

    @Override
    public final void setInt(long bytePos, int value) {
        segment.set(INT_LE, bytePos, value);
    }

    @Override
    public final int getInt(long bytePos) {
        return segment.get(INT_LE, bytePos);
    }

    @Override
    public final void setShort(long bytePos, short value) {
        segment.set(SHORT_LE, bytePos, value);
    }

    @Override
    public final short getShort(long bytePos) {
        return segment.get(SHORT_LE, bytePos);
    }

    @Override
    public void setBytes(long bytePos, byte[] values, int length) {
        MemorySegment.copy(values, 0, segment, BYTE_LAYOUT, bytePos, length);
    }

    @Override
    public void getBytes(long bytePos, byte[] values, int length) {
        MemorySegment.copy(segment, BYTE_LAYOUT, bytePos, values, 0, length);
    }

    @Override
    public final void setByte(long bytePos, byte value) {
        segment.set(BYTE_LAYOUT, bytePos, value);
    }

    @Override
    public final byte getByte(long bytePos) {
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
            try {
                segment.force();
                mapSegment(HEADER_OFFSET, newCapacity);
                raFile.setLength(HEADER_OFFSET + newCapacity);
            } catch (IOException ex) {
                throw new RuntimeException("Failed to trim " + getFullName(), ex);
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
        Helper.close(raFile);
        raFile = null;
    }

    @Override
    public long getCapacity() {
        return capacity;
    }

    @Override
    public int getSegments() {
        if (capacity == 0) return 0;
        int segs = (int) (capacity / segmentSizeInBytes);
        if (capacity % segmentSizeInBytes != 0)
            segs++;
        return segs;
    }

    @Override
    public DAType getType() {
        return DAType.MMAP;
    }
}
