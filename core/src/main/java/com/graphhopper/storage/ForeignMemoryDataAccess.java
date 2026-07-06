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
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;

import static java.lang.foreign.ValueLayout.*;

/**
 * Off-heap DataAccess backed by a single contiguous {@link MemorySegment} via the Foreign Memory API.
 * It has a long-indexed access and no segment boundary logic.
 * <p>
 * On Linux the backing region is an anonymous {@code mmap} that {@link #ensureCapacity} enlarges in
 * place with {@code mremap(MREMAP_MAYMOVE)} — the kernel remaps page tables instead of copying bytes,
 * so growth is O(1)-amortized and never needs 2x peak memory. Where {@code mremap} is unavailable
 * (macOS, Windows, ...) it falls back to the portable {@link Arena}-backed allocate + copy, which
 * grows by copying but works everywhere. Anonymous memory is zero-initialized by the OS, matching the
 * previous {@code Arena.allocate} behaviour.
 * <p>
 * <b>Concurrency contract:</b> the base address may change on grow/shrink, so no other thread may
 * access this DataAccess while {@link #ensureCapacity}, {@link #trimTo} or {@link #close} is in
 * flight — a concurrent access could touch memory that was just unmapped and crash the JVM.
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

    // POSIX mmap/mremap/munmap bindings. Resolved once. The native path is used only when all three
    // resolve, i.e. only when we can grow in place with mremap (Linux) — otherwise the copy is
    // unavoidable and a raw-mmap+copy would just add a second code path with no benefit over the
    // portable Arena.allocate+copy fallback (see USE_MMAP).
    private static final MethodHandle MMAP;
    private static final MethodHandle MREMAP;
    private static final MethodHandle MUNMAP;
    private static final boolean USE_MMAP;

    private static final int PROT_READ = 0x1;
    private static final int PROT_WRITE = 0x2;
    private static final int MAP_PRIVATE = 0x02;
    private static final int MAP_ANONYMOUS = 0x20; // Linux value; the native path is gated on Linux-only mremap
    private static final int MREMAP_MAYMOVE = 1;
    private static final long MAP_FAILED = -1L; // (void *) -1

    static {
        MethodHandle mmap = null, mremap = null, munmap = null;
        try {
            Linker linker = Linker.nativeLinker();
            SymbolLookup std = linker.defaultLookup();
            // void *mmap(void *addr, size_t length, int prot, int flags, int fd, off_t offset)
            mmap = std.find("mmap").map(seg -> linker.downcallHandle(seg, FunctionDescriptor.of(
                    ADDRESS, ADDRESS, JAVA_LONG, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_LONG))).orElse(null);
            // int munmap(void *addr, size_t length)
            munmap = std.find("munmap").map(seg -> linker.downcallHandle(seg, FunctionDescriptor.of(
                    JAVA_INT, ADDRESS, JAVA_LONG))).orElse(null);
            // void *mremap(void *old, size_t oldSize, size_t newSize, int flags) — Linux only.
            // Called only without MREMAP_FIXED, so the variadic new_address argument is never passed.
            mremap = std.find("mremap").map(seg -> linker.downcallHandle(seg, FunctionDescriptor.of(
                    ADDRESS, ADDRESS, JAVA_LONG, JAVA_LONG, JAVA_INT))).orElse(null);
        } catch (Throwable t) {
            mmap = null;
            mremap = null;
            munmap = null;
        }
        MMAP = mmap;
        MREMAP = mremap;
        MUNMAP = munmap;
        USE_MMAP = mmap != null && mremap != null && munmap != null;
    }

    // Native-mmap path: base address of the current mapping (0 when unallocated).
    private long address;
    // Portable fallback path: owns the Arena-allocated segment (null when USE_MMAP is true).
    private Arena arena;
    private MemorySegment segment = MemorySegment.NULL;
    private long capacity;
    private final boolean store;

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

        if (bytes <= capacity)
            return false;

        int segmentsNeeded = (int) (bytes / segmentSizeInBytes);
        if (bytes % segmentSizeInBytes != 0)
            segmentsNeeded++;
        long newCapacity = (long) segmentsNeeded * segmentSizeInBytes;

        try {
            if (USE_MMAP) {
                // grow in place, no copy: the kernel remaps page tables (may relocate virtually)
                address = capacity == 0
                        ? mmapAnon(newCapacity)
                        : mremapMove(address, capacity, newCapacity);
                segment = MemorySegment.ofAddress(address).reinterpret(newCapacity);
            } else {
                Arena newArena = Arena.ofShared();
                MemorySegment newSegment = newArena.allocate(newCapacity); // zero-initialized by the FFM API
                if (capacity > 0) {
                    MemorySegment.copy(segment, 0, newSegment, 0, capacity);
                    arena.close();
                }
                arena = newArena;
                segment = newSegment;
            }
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

                if (USE_MMAP) {
                    address = mmapAnon(totalCapacity);
                    segment = MemorySegment.ofAddress(address).reinterpret(totalCapacity);
                } else {
                    arena = Arena.ofShared();
                    segment = arena.allocate(totalCapacity); // zero-initialized by the FFM API
                }
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
                releaseMemory();
                this.capacity = 0;
            } else if (USE_MMAP) {
                address = mremapMove(address, this.capacity, newCapacity);
                segment = MemorySegment.ofAddress(address).reinterpret(newCapacity);
                this.capacity = newCapacity;
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
        releaseMemory();
        capacity = 0;
    }

    private void releaseMemory() {
        if (USE_MMAP) {
            if (address != 0 && capacity > 0)
                munmap(address, capacity);
            address = 0;
        } else if (arena != null) {
            arena.close();
            arena = null;
        }
        segment = MemorySegment.NULL;
    }

    private static long mmapAnon(long size) {
        try {
            MemorySegment res = (MemorySegment) MMAP.invokeExact(
                    MemorySegment.NULL, size, PROT_READ | PROT_WRITE, MAP_PRIVATE | MAP_ANONYMOUS, -1, 0L);
            long addr = res.address();
            if (addr == MAP_FAILED)
                throw new OutOfMemoryError("mmap failed for " + size + " bytes");
            return addr;
        } catch (OutOfMemoryError | RuntimeException e) {
            throw e;
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    private static long mremapMove(long oldAddress, long oldSize, long newSize) {
        try {
            MemorySegment res = (MemorySegment) MREMAP.invokeExact(
                    MemorySegment.ofAddress(oldAddress), oldSize, newSize, MREMAP_MAYMOVE);
            long addr = res.address();
            if (addr == MAP_FAILED)
                throw new OutOfMemoryError("mremap failed for " + oldSize + " -> " + newSize + " bytes");
            return addr;
        } catch (OutOfMemoryError | RuntimeException e) {
            throw e;
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    private static void munmap(long addr, long size) {
        try {
            int res = (int) MUNMAP.invokeExact(MemorySegment.ofAddress(addr), size);
            if (res != 0)
                throw new IllegalStateException("munmap failed for address " + addr + ", size " + size);
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
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
