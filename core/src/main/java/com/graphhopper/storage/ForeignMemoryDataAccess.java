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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.lang.foreign.ValueLayout.*;

/**
 * Off-heap DataAccess backed by a single contiguous {@link MemorySegment} via the Foreign Memory API.
 * It has a long-indexed access and no segment boundary logic.
 */
public final class ForeignMemoryDataAccess extends AbstractDataAccess {
    private static final ValueLayout.OfInt INT_LE =
            ValueLayout.JAVA_INT.withOrder(ByteOrder.LITTLE_ENDIAN).withByteAlignment(1);
    private static final ValueLayout.OfShort SHORT_LE =
            ValueLayout.JAVA_SHORT.withOrder(ByteOrder.LITTLE_ENDIAN).withByteAlignment(1);
    private static final ValueLayout.OfByte BYTE_LAYOUT = ValueLayout.JAVA_BYTE;
    private static final VarHandle INT_VH = INT_LE.varHandle();
    private static final VarHandle SHORT_VH = SHORT_LE.varHandle();
    private static final VarHandle BYTE_VH = BYTE_LAYOUT.varHandle();

    private static final Logger LOGGER = LoggerFactory.getLogger(ForeignMemoryDataAccess.class);
    // -XX:+UseTransparentHugePages only covers the Java heap, not FFM/native allocations. For those
    // we mmap the memory ourselves, align it to the huge page size and madvise(MADV_HUGEPAGE)
    // before it is first touched, so the kernel backs it with transparent huge pages right at
    // fault time, in both the "madvise" and the "always" THP mode. Arena.allocate would not work
    // here: it zero-fills the memory and thereby faults it in as regular 4K pages before we could
    // give the advice.
    private static final long HUGE_PAGE_SIZE = 2L * 1024 * 1024;
    private static final int MADV_HUGEPAGE = 14, PROT_READ = 1, PROT_WRITE = 2, MAP_PRIVATE = 2, MAP_ANONYMOUS = 0x20; // linux/mman.h
    private static final MethodHandle MMAP = link("mmap",
            FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_LONG, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_LONG));
    private static final MethodHandle MUNMAP = link("munmap", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_LONG));
    private static final MethodHandle MADVISE = link("madvise", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_LONG, JAVA_INT));
    private static final AtomicBoolean MADVISE_LOGGED = new AtomicBoolean();

    private static MethodHandle link(String name, FunctionDescriptor descriptor) {
        if (!System.getProperty("os.name", "").startsWith("Linux") || "false".equals(System.getProperty("graphhopper.thp")))
            return null;
        try {
            Linker linker = Linker.nativeLinker();
            return linker.downcallHandle(linker.defaultLookup().find(name).orElseThrow(), descriptor);
        } catch (Throwable t) {
            LOGGER.warn("Could not link " + name + ", transparent huge pages unavailable for off-heap memory", t);
            return null;
        }
    }

    private Arena arena;
    private MemorySegment segment = MemorySegment.NULL;
    private long capacity;
    private final boolean store;

    public ForeignMemoryDataAccess(String name, String location, boolean store, int segmentSize) {
        super(name, location, segmentSize);
        this.store = store;
    }

    private static MemorySegment allocate(Arena arena, long byteCount) {
        return arena.allocate(byteCount);
    }

    /**
     * Allocates a zero-initialized native segment. On Linux it is allocated via mmap, aligned to
     * the 2MB huge page size and advised with MADV_HUGEPAGE before it is first touched, so it can
     * be backed by transparent huge pages; the mapping is released when the given arena is closed.
     * On other platforms it falls back to {@link Arena#allocate}. Disable with -Dgraphhopper.thp=false.
     */
    private static MemorySegment allocateTHP(Arena arena, long byteCount) {
        if (MMAP == null || MUNMAP == null || MADVISE == null)
            return arena.allocate(byteCount);
        try {
            // Huge pages require a 2MB-aligned start, but mmap only guarantees base-page (4K)
            // alignment, so we over-map by one huge page and use the first 2MB boundary inside
            // the mapping. The skipped head (up to ~2MB, depending on where mmap placed us) and
            // the unused tail are never touched, i.e. they cost only virtual address space, and
            // are released together with the rest of the mapping when the arena is closed:
            //
            // raw (from mmap, 4K-aligned)                                  raw + mapLength
            //  v                                                            v
            //  |-- head, up to ~2MB --|========== advisedLength ==========|-- tail --|
            //                         ^
            //                         aligned (first 2MB boundary >= raw)
            long advisedLength = Math.ceilDiv(byteCount, HUGE_PAGE_SIZE) * HUGE_PAGE_SIZE;
            long mapLength = advisedLength + HUGE_PAGE_SIZE;
            MemorySegment raw = (MemorySegment) MMAP.invokeExact(MemorySegment.NULL, mapLength,
                    PROT_READ | PROT_WRITE, MAP_PRIVATE | MAP_ANONYMOUS, -1, 0L);
            if (raw.address() == -1L)
                throw new OutOfMemoryError("mmap of " + mapLength + " bytes failed");
            long aligned = Math.ceilDiv(raw.address(), HUGE_PAGE_SIZE) * HUGE_PAGE_SIZE;
            int res = (int) MADVISE.invokeExact(MemorySegment.ofAddress(aligned), advisedLength, MADV_HUGEPAGE);
            if (MADVISE_LOGGED.compareAndSet(false, true))
                LOGGER.info("madvise(MADV_HUGEPAGE) " + (res == 0
                        ? "succeeded, off-heap memory can use transparent huge pages"
                        : "failed, is THP disabled in the kernel?"));
            return MemorySegment.ofAddress(aligned).reinterpret(byteCount, arena, seg -> {
                try {
                    int ignored = (int) MUNMAP.invokeExact(raw, mapLength);
                } catch (Throwable t) {
                    LOGGER.warn("munmap failed", t);
                }
            });
        } catch (OutOfMemoryError err) {
            throw err;
        } catch (Throwable t) {
            LOGGER.warn("mmap-based allocation failed, falling back to Arena.allocate", t);
            return arena.allocate(byteCount);
        }
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

        if (bytes <= capacity)
            return false;

        int segmentsNeeded = (int) (bytes / segmentSizeInBytes);
        if (bytes % segmentSizeInBytes != 0)
            segmentsNeeded++;
        long newCapacity = (long) segmentsNeeded * segmentSizeInBytes;

        try {
            Arena newArena = Arena.ofShared();
            MemorySegment newSegment = allocate(newArena, newCapacity); // zero-initialized by the FFM API
            if (capacity > 0) {
                MemorySegment.copy(segment, 0, newSegment, 0, capacity);
                arena.close();
            }

            arena = newArena;
            segment = newSegment;
            capacity = newCapacity;
        } catch (OutOfMemoryError err) {
            throw new OutOfMemoryError(err.getMessage() + " - problem when allocating new memory. Old capacity: "
                    + capacity + ", requested bytes:" + bytes + ", new capacity:" + newCapacity);
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
                segment = allocate(arena, totalCapacity); // zero-initialized by the FFM API
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
    public void setInt(long bytePos, int value) {
        assert capacity > 0 : "call create or loadExisting before usage!";
        INT_VH.set(segment, bytePos, value);
    }

    @Override
    public int getInt(long bytePos) {
        assert capacity > 0 : "call create or loadExisting before usage!";
        return (int) INT_VH.get(segment, bytePos);
    }

    @Override
    public void setShort(long bytePos, short value) {
        assert capacity > 0 : "call create or loadExisting before usage!";
        SHORT_VH.set(segment, bytePos, value);
    }

    @Override
    public short getShort(long bytePos) {
        assert capacity > 0 : "call create or loadExisting before usage!";
        return (short) SHORT_VH.get(segment, bytePos);
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
    public void setByte(long bytePos, byte value) {
        assert capacity > 0 : "call create or loadExisting before usage!";
        BYTE_VH.set(segment, bytePos, value);
    }

    @Override
    public byte getByte(long bytePos) {
        assert capacity > 0 : "call create or loadExisting before usage!";
        return (byte) BYTE_VH.get(segment, bytePos);
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
                MemorySegment newSegment = allocate(newArena, newCapacity);
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
            return DAType.NATIVE_STORE;
        return DAType.NATIVE;
    }
}
