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
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;

/**
 * Read-only, fixed-size memory-mapped DataAccess. Sibling of {@link MMapForeignMemoryDataAccess}
 * for the post-import phase: the file's size is known from its header and the mapping never
 * grows or shrinks. This lets all storage fields ({@link #arena}, {@link #mappedSegment},
 * {@link #segment}, {@link #capacity}) be {@code final}, which lets the JIT hoist the
 * field-load for {@code segment} out of hot loops — the residual cost that the resizable
 * variant cannot avoid.
 * <p>
 * Construction is done via {@link #load} (no separate {@code loadExisting} step) so the final
 * fields can be assigned in the constructor.
 * <p>
 * Write operations ({@code create}, {@code ensureCapacity} past the loaded capacity,
 * {@code trimTo}, {@code flush}, {@code setInt}/{@code setShort}/{@code setByte}/
 * {@code setBytes}) throw {@link UnsupportedOperationException}.
 * <p>
 * <b>Concurrency:</b> the same contract as {@link MMapForeignMemoryDataAccess} on the read
 * path — accesses go through a global-scope reinterpret to skip the per-call scope check.
 * Because there is no remap, concurrent reads from many threads are safe; the only operation
 * that invalidates the mapping is {@link #close}, which callers must not race with reads.
 */
public final class MMapForeignReadOnlyDataAccess extends AbstractDataAccess {
    private static final ValueLayout.OfInt INT_LE =
            ValueLayout.JAVA_INT.withOrder(ByteOrder.LITTLE_ENDIAN).withByteAlignment(1);
    private static final ValueLayout.OfShort SHORT_LE =
            ValueLayout.JAVA_SHORT.withOrder(ByteOrder.LITTLE_ENDIAN).withByteAlignment(1);
    private static final ValueLayout.OfByte BYTE_LAYOUT = ValueLayout.JAVA_BYTE;
    private static final VarHandle INT_VH = INT_LE.varHandle();
    private static final VarHandle SHORT_VH = SHORT_LE.varHandle();
    private static final VarHandle BYTE_VH = BYTE_LAYOUT.varHandle();

    private final Arena arena;
    private final MemorySegment mappedSegment;
    private final MemorySegment segment;
    private final long capacity;
    private final RandomAccessFile raFile;

    /**
     * Opens an existing GraphHopper data file for read-only access. The file must exist and
     * have a valid header — there is no notion of a "created but empty" state for this class.
     *
     * @param preload     if true, forces all mapped pages into RAM after mapping
     * @param segmentSize used only for {@link #getSegments()} reporting; the actual segment
     *                    size is overridden by what is recorded in the file header
     */
    public static MMapForeignReadOnlyDataAccess load(String name, String location,
                                                     int segmentSize, boolean preload) {
        File file = new File(location + name);
        if (!file.exists() || file.length() == 0)
            throw new IllegalStateException("File does not exist or is empty: " + file);
        return new MMapForeignReadOnlyDataAccess(name, location, segmentSize, preload);
    }

    private MMapForeignReadOnlyDataAccess(String name, String location,
                                          int segmentSize, boolean preload) {
        super(name, location, segmentSize);
        try {
            this.raFile = new RandomAccessFile(getFullName(), "r");
            long byteCount = readHeader(raFile) - HEADER_OFFSET;
            if (byteCount < 0)
                throw new IllegalStateException("Invalid GraphHopper file (no header): " + getFullName());

            int segmentCount = (int) (byteCount / segmentSizeInBytes);
            if (byteCount % segmentSizeInBytes != 0)
                segmentCount++;
            long totalCapacity = (long) segmentCount * segmentSizeInBytes;

            this.arena = Arena.ofShared();
            this.mappedSegment = raFile.getChannel().map(
                    FileChannel.MapMode.READ_ONLY, HEADER_OFFSET, totalCapacity, arena);
            this.segment = MemorySegment.ofAddress(mappedSegment.address()).reinterpret(totalCapacity);
            this.capacity = totalCapacity;

            if (preload)
                mappedSegment.load();
        } catch (IOException ex) {
            throw new RuntimeException("Problem opening read-only " + getFullName(), ex);
        }
    }

    @Override
    public DataAccess create(long bytes) {
        throw new UnsupportedOperationException("read-only — file must already exist; use " +
                MMapForeignMemoryDataAccess.class.getSimpleName() + " for writes");
    }

    @Override
    public boolean ensureCapacity(long bytes) {
        if (bytes <= capacity)
            return false;
        throw new UnsupportedOperationException("read-only — fixed capacity " + capacity
                + ", requested " + bytes);
    }

    /**
     * The data is already loaded by the constructor. Provided to satisfy the
     * {@link DataAccess} contract; always returns {@code true}.
     */
    @Override
    public boolean loadExisting() {
        if (closed)
            throw new IllegalStateException("already closed");
        return true;
    }

    @Override
    public void flush() {
        throw new UnsupportedOperationException("read-only");
    }

    @Override
    public void setInt(long bytePos, int value) {
        throw new UnsupportedOperationException("read-only");
    }

    @Override
    public int getInt(long bytePos) {
        return (int) INT_VH.get(segment, bytePos);
    }

    @Override
    public void setShort(long bytePos, short value) {
        throw new UnsupportedOperationException("read-only");
    }

    @Override
    public short getShort(long bytePos) {
        return (short) SHORT_VH.get(segment, bytePos);
    }

    @Override
    public void setBytes(long bytePos, byte[] values, int length) {
        throw new UnsupportedOperationException("read-only");
    }

    @Override
    public void getBytes(long bytePos, byte[] values, int length) {
        MemorySegment.copy(segment, BYTE_LAYOUT, bytePos, values, 0, length);
    }

    @Override
    public void setByte(long bytePos, byte value) {
        throw new UnsupportedOperationException("read-only");
    }

    @Override
    public byte getByte(long bytePos) {
        return (byte) BYTE_VH.get(segment, bytePos);
    }

    @Override
    public void trimTo(long capacity) {
        throw new UnsupportedOperationException("read-only");
    }

    @Override
    public void close() {
        if (closed)
            return;
        super.close();
        arena.close();
        Helper.close(raFile);
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
        return DAType.MMAP_RO;
    }
}
