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
import java.util.Arrays;

/**
 * Off-heap DataAccess using an array of native {@link MemorySegment}s, mirroring the segment
 * architecture of {@link RAMDataAccess} ({@code MemorySegment[]} instead of {@code byte[][]}).
 * This isolates the effect of off-heap vs on-heap from the effect of removing segmentation.
 * <p>
 * Requires Java 22+.
 */
public class ForeignMemorySegmentedDataAccess extends AbstractDataAccess {
    private static final ValueLayout.OfInt INT_LE =
            ValueLayout.JAVA_INT.withOrder(ByteOrder.LITTLE_ENDIAN).withByteAlignment(1);
    private static final ValueLayout.OfShort SHORT_LE =
            ValueLayout.JAVA_SHORT.withOrder(ByteOrder.LITTLE_ENDIAN).withByteAlignment(1);
    private static final ValueLayout.OfByte BYTE_LAYOUT = ValueLayout.JAVA_BYTE;

    private MemorySegment[] segments = new MemorySegment[0];
    private Arena arena;
    private boolean store;

    public ForeignMemorySegmentedDataAccess(String name, String location, boolean store, int segmentSize) {
        super(name, location, segmentSize);
        this.store = store;
    }

    @Override
    public ForeignMemorySegmentedDataAccess create(long bytes) {
        if (segments.length > 0)
            throw new IllegalThreadStateException("already created");
        ensureCapacity(Math.max(10 * 4, bytes));
        return this;
    }

    @Override
    public boolean ensureCapacity(long bytes) {
        if (bytes < 0)
            throw new IllegalArgumentException("new capacity has to be strictly positive");

        long cap = getCapacity();
        long newBytes = bytes - cap;
        if (newBytes <= 0)
            return false;

        int segmentsToCreate = (int) (newBytes / segmentSizeInBytes);
        if (newBytes % segmentSizeInBytes != 0)
            segmentsToCreate++;

        try {
            if (arena == null)
                arena = Arena.ofConfined();

            MemorySegment[] newSegs = Arrays.copyOf(segments, segments.length + segmentsToCreate);
            for (int i = segments.length; i < newSegs.length; i++) {
                MemorySegment seg = arena.allocate(1 << segmentSizePower);
                seg.fill((byte) 0);
                newSegs[i] = seg;
            }
            segments = newSegs;
        } catch (OutOfMemoryError err) {
            throw new OutOfMemoryError(err.getMessage() + " - problem when allocating new memory. Old capacity: "
                    + cap + ", new bytes:" + newBytes + ", segmentSizeIntsPower:" + segmentSizePower
                    + ", new segments:" + segmentsToCreate + ", existing:" + segments.length);
        }
        return true;
    }

    @Override
    public boolean loadExisting() {
        if (segments.length > 0)
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

                arena = Arena.ofConfined();
                segments = new MemorySegment[segmentCount];
                byte[] buffer = new byte[segmentSizeInBytes];
                for (int s = 0; s < segmentCount; s++) {
                    int read = raFile.read(buffer);
                    if (read <= 0)
                        throw new IllegalStateException("segment " + s + " is empty? " + toString());
                    MemorySegment seg = arena.allocate(segmentSizeInBytes);
                    seg.fill((byte) 0);
                    MemorySegment.copy(buffer, 0, seg, BYTE_LAYOUT, 0, read);
                    segments[s] = seg;
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
                for (MemorySegment seg : segments) {
                    MemorySegment.copy(seg, BYTE_LAYOUT, 0, buffer, 0, segmentSizeInBytes);
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
        assert segments.length > 0 : "call create or loadExisting before usage!";
        int bufferIndex = (int) (bytePos >>> segmentSizePower);
        int index = (int) (bytePos & indexDivisor);
        if (index + 3 >= segmentSizeInBytes) {
            MemorySegment b1 = segments[bufferIndex], b2 = segments[bufferIndex + 1];
            if (index + 1 >= segmentSizeInBytes) {
                b1.set(BYTE_LAYOUT, index, (byte) value);
                b2.set(BYTE_LAYOUT, 0, (byte) (value >>> 8));
                b2.set(BYTE_LAYOUT, 1, (byte) (value >>> 16));
                b2.set(BYTE_LAYOUT, 2, (byte) (value >>> 24));
            } else if (index + 2 >= segmentSizeInBytes) {
                b1.set(SHORT_LE, index, (short) value);
                b2.set(SHORT_LE, 0, (short) (value >>> 16));
            } else {
                // index + 3 >= segmentSizeInBytes
                b1.set(BYTE_LAYOUT, index, (byte) value);
                b1.set(BYTE_LAYOUT, index + 1, (byte) (value >>> 8));
                b1.set(BYTE_LAYOUT, index + 2, (byte) (value >>> 16));
                b2.set(BYTE_LAYOUT, 0, (byte) (value >>> 24));
            }
        } else {
            segments[bufferIndex].set(INT_LE, index, value);
        }
    }

    @Override
    public final int getInt(long bytePos) {
        assert segments.length > 0 : "call create or loadExisting before usage!";
        int bufferIndex = (int) (bytePos >>> segmentSizePower);
        int index = (int) (bytePos & indexDivisor);
        if (index + 3 >= segmentSizeInBytes) {
            MemorySegment b1 = segments[bufferIndex], b2 = segments[bufferIndex + 1];
            if (index + 1 >= segmentSizeInBytes)
                return (b2.get(BYTE_LAYOUT, 2) & 0xFF) << 24 | (b2.get(BYTE_LAYOUT, 1) & 0xFF) << 16
                        | (b2.get(BYTE_LAYOUT, 0) & 0xFF) << 8 | (b1.get(BYTE_LAYOUT, index) & 0xFF);
            if (index + 2 >= segmentSizeInBytes)
                return (b2.get(BYTE_LAYOUT, 1) & 0xFF) << 24 | (b2.get(BYTE_LAYOUT, 0) & 0xFF) << 16
                        | (b1.get(BYTE_LAYOUT, index + 1) & 0xFF) << 8 | (b1.get(BYTE_LAYOUT, index) & 0xFF);
            // index + 3 >= segmentSizeInBytes
            return (b2.get(BYTE_LAYOUT, 0) & 0xFF) << 24 | (b1.get(BYTE_LAYOUT, index + 2) & 0xFF) << 16
                    | (b1.get(BYTE_LAYOUT, index + 1) & 0xFF) << 8 | (b1.get(BYTE_LAYOUT, index) & 0xFF);
        }
        return segments[bufferIndex].get(INT_LE, index);
    }

    @Override
    public final void setShort(long bytePos, short value) {
        assert segments.length > 0 : "call create or loadExisting before usage!";
        int bufferIndex = (int) (bytePos >>> segmentSizePower);
        int index = (int) (bytePos & indexDivisor);
        if (index + 1 >= segmentSizeInBytes) {
            segments[bufferIndex].set(BYTE_LAYOUT, index, (byte) (value));
            segments[bufferIndex + 1].set(BYTE_LAYOUT, 0, (byte) (value >>> 8));
        } else {
            segments[bufferIndex].set(SHORT_LE, index, value);
        }
    }

    @Override
    public final short getShort(long bytePos) {
        assert segments.length > 0 : "call create or loadExisting before usage!";
        int bufferIndex = (int) (bytePos >>> segmentSizePower);
        int index = (int) (bytePos & indexDivisor);
        if (index + 1 >= segmentSizeInBytes)
            return (short) ((segments[bufferIndex + 1].get(BYTE_LAYOUT, 0) & 0xFF) << 8
                    | (segments[bufferIndex].get(BYTE_LAYOUT, index) & 0xFF));
        return segments[bufferIndex].get(SHORT_LE, index);
    }

    @Override
    public void setBytes(long bytePos, byte[] values, int length) {
        assert length <= segmentSizeInBytes : "the length has to be smaller or equal to the segment size: " + length + " vs. " + segmentSizeInBytes;
        assert segments.length > 0 : "call create or loadExisting before usage!";
        int bufferIndex = (int) (bytePos >>> segmentSizePower);
        int index = (int) (bytePos & indexDivisor);
        MemorySegment seg = segments[bufferIndex];
        int delta = index + length - segmentSizeInBytes;
        if (delta > 0) {
            int firstLen = length - delta;
            MemorySegment.copy(values, 0, seg, BYTE_LAYOUT, index, firstLen);
            MemorySegment.copy(values, firstLen, segments[bufferIndex + 1], BYTE_LAYOUT, 0, delta);
        } else {
            MemorySegment.copy(values, 0, seg, BYTE_LAYOUT, index, length);
        }
    }

    @Override
    public void getBytes(long bytePos, byte[] values, int length) {
        assert length <= segmentSizeInBytes : "the length has to be smaller or equal to the segment size: " + length + " vs. " + segmentSizeInBytes;
        assert segments.length > 0 : "call create or loadExisting before usage!";
        int bufferIndex = (int) (bytePos >>> segmentSizePower);
        int index = (int) (bytePos & indexDivisor);
        MemorySegment seg = segments[bufferIndex];
        int delta = index + length - segmentSizeInBytes;
        if (delta > 0) {
            int firstLen = length - delta;
            MemorySegment.copy(seg, BYTE_LAYOUT, index, values, 0, firstLen);
            MemorySegment.copy(segments[bufferIndex + 1], BYTE_LAYOUT, 0, values, firstLen, delta);
        } else {
            MemorySegment.copy(seg, BYTE_LAYOUT, index, values, 0, length);
        }
    }

    @Override
    public final void setByte(long bytePos, byte value) {
        assert segments.length > 0 : "call create or loadExisting before usage!";
        int bufferIndex = (int) (bytePos >>> segmentSizePower);
        int index = (int) (bytePos & indexDivisor);
        segments[bufferIndex].set(BYTE_LAYOUT, index, value);
    }

    @Override
    public final byte getByte(long bytePos) {
        assert segments.length > 0 : "call create or loadExisting before usage!";
        int bufferIndex = (int) (bytePos >>> segmentSizePower);
        int index = (int) (bytePos & indexDivisor);
        return segments[bufferIndex].get(BYTE_LAYOUT, index);
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

        if (newSegmentCount < segments.length)
            segments = Arrays.copyOf(segments, newSegmentCount);
    }

    @Override
    public void close() {
        super.close();
        segments = new MemorySegment[0];
        if (arena != null) {
            arena.close();
            arena = null;
        }
    }

    @Override
    public long getCapacity() {
        return (long) getSegments() * segmentSizeInBytes;
    }

    @Override
    public int getSegments() {
        return segments.length;
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
