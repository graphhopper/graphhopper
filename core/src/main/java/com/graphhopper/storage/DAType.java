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

import static com.graphhopper.util.Helper.toUpperCase;

/**
 * Defines how a DataAccess object is created. This is an extensible value object (not an enum) so
 * that downstream projects can introduce further combinations or new backings.
 * <p>
 *
 * @author Peter Karich
 */
public class DAType {
    /**
     * The DA object is hold entirely in-memory. Loading and flushing is a no-op. See RAMDataAccess.
     */
    public static final DAType RAM = new DAType(MemRef.HEAP, false, false, true);
    /**
     * Optimized RAM DA type for integer access. The set and getBytes methods cannot be used.
     */
    public static final DAType RAM_INT = new DAType(MemRef.HEAP, false, true, true);
    /**
     * The DA object is hold entirely in-memory. It will read load disc and flush to it if they
     * equivalent methods are called. See RAMDataAccess.
     */
    public static final DAType RAM_STORE = new DAType(MemRef.HEAP, true, false, true);
    /**
     * Optimized RAM_STORE DA type for integer access. The set and getBytes methods cannot be used.
     */
    public static final DAType RAM_INT_STORE = new DAType(MemRef.HEAP, true, true, true);
    /**
     * Like RAM_INT, but backed by a single contiguous int[] for maximum read speed.
     * Not a good fit if the array needs to be resized frequently. Limited to Integer.MAX_VALUE ints
     * No support for short,byte and bytes.
     */
    public static final DAType RAM_INT_1SEG = new DAType(MemRef.HEAP, false, true, true, true);
    /**
     * See RAM_INT_1SEG
     */
    public static final DAType RAM_INT_1SEG_STORE = new DAType(MemRef.HEAP, true, true, true, true);
    /**
     * Like RAM, but backed by a single contiguous byte[] (no segment math). Limited to ~2GB.
     * The on-heap equivalent of NATIVE. See RAM1SegmentDataAccess.
     */
    public static final DAType RAM_1SEG = new DAType(MemRef.HEAP, false, false, true, true);
    /**
     * See RAM_1SEG
     */
    public static final DAType RAM_1SEG_STORE = new DAType(MemRef.HEAP, true, false, true, true);
    /**
     * Like RAM_1SEG (single contiguous heap array, full byte access), but backed by a {@code long[]}
     * instead of a {@code byte[]} to allow up to ~16GB. See RAMLongDataAccess.
     */
    public static final DAType RAM_LONG = new DAType(MemRef.HEAP, false, false, true, true, true);
    /**
     * See RAM_LONG
     */
    public static final DAType RAM_LONG_STORE = new DAType(MemRef.HEAP, true, false, true, true, true);
    /**
     * Off-heap DA object backed by native (foreign) memory - the equivalent of RAM but outside the
     * JVM heap. Loading and flushing is a no-op. See ForeignMemoryDataAccess.
     */
    public static final DAType NATIVE = new DAType(MemRef.NATIVE, false, false, true);
    /**
     * Like NATIVE but loads from and flushes to disc. See ForeignMemoryDataAccess.
     */
    public static final DAType NATIVE_STORE = new DAType(MemRef.NATIVE, true, false, true);
    /**
     * Memory mapped DA object. See MMapForeignMemoryDataAccess.
     */
    public static final DAType MMAP = new DAType(MemRef.MMAP, true, false, true);

    /**
     * Read-only memory mapped DA object. To avoid write access useful for reading on mobile or
     * embedded data stores. See MMapForeignReadOnlyDataAccess.
     */
    public static final DAType MMAP_RO = new DAType(MemRef.MMAP, true, false, false);

    /**
     * Legacy memory mapped DA object backed by ByteBuffers instead of the Foreign Memory API.
     * Kept usable as a fallback and for comparison. See MMapDataAccess.
     */
    public static final DAType MMAP_OLD = new DAType(MemRef.MMAP_OLD, true, false, true);
    private final MemRef memRef;
    private final boolean storing;
    private final boolean integ;
    private final boolean allowWrites;
    private final boolean singleSegment;
    private final boolean longBacked;

    public DAType(DAType type) {
        this(type.getMemRef(), type.isStoring(), type.isInteg(), type.isAllowWrites(), type.isSingleSegment(), type.isLongBacked());
    }

    public DAType(MemRef memRef, boolean storing, boolean integ, boolean allowWrites) {
        this(memRef, storing, integ, allowWrites, false, false);
    }

    public DAType(MemRef memRef, boolean storing, boolean integ, boolean allowWrites, boolean singleSegment) {
        this(memRef, storing, integ, allowWrites, singleSegment, false);
    }

    public DAType(MemRef memRef, boolean storing, boolean integ, boolean allowWrites, boolean singleSegment, boolean longBacked) {
        this.memRef = memRef;
        this.storing = storing;
        this.integ = integ;
        this.allowWrites = allowWrites;
        this.singleSegment = singleSegment;
        this.longBacked = longBacked;
    }

    /**
     * Parses a DAType from its {@link #toString()} form, e.g. "RAM", "RAM_INT_1SEG_STORE", "MMAP",
     * "MMAP_RO", "MMAP_OLD" or "NATIVE_STORE". The individual tokens (memory backing plus the
     * INT / 1SEG / RO / STORE modifiers) are combined so that every valid combination is reachable
     * from config, not only the predefined constants.
     */
    public static DAType valueOf(String dataAccess) {
        dataAccess = toUpperCase(dataAccess);
        if (dataAccess.contains("SYNC"))
            throw new IllegalArgumentException("SYNC option is no longer supported, see #982");
        if (dataAccess.contains("UNSAFE"))
            throw new IllegalArgumentException("UNSAFE option is no longer supported, see #1620");

        MemRef memRef;
        if (dataAccess.contains("MMAP_OLD"))
            memRef = MemRef.MMAP_OLD;
        else if (dataAccess.contains("MMAP"))
            memRef = MemRef.MMAP;
        else if (dataAccess.contains("NATIVE"))
            memRef = MemRef.NATIVE;
        else
            memRef = MemRef.HEAP;

        boolean integ = dataAccess.contains("INT");
        boolean longBacked = dataAccess.contains("LONG");
        // a long[] backing is always a single contiguous array
        boolean singleSegment = dataAccess.contains("1SEG") || longBacked;
        boolean allowWrites = !dataAccess.contains("_RO");
        // mmap always persists to its file; on heap/native memory storing must be requested explicitly
        boolean storing = memRef == MemRef.MMAP || memRef == MemRef.MMAP_OLD || dataAccess.contains("STORE");
        return new DAType(memRef, storing, integ, allowWrites, singleSegment, longBacked);
    }

    /**
     * Where the data resides: on the JVM heap, off-heap in native memory or memory mapped.
     * default is HEAP
     */
    MemRef getMemRef() {
        return memRef;
    }

    public boolean isAllowWrites() {
        return allowWrites;
    }

    /**
     * @return true if data resides in the JVM heap.
     */
    public boolean isInMemory() {
        return memRef == MemRef.HEAP;
    }

    public boolean isMMap() {
        return memRef == MemRef.MMAP || memRef == MemRef.MMAP_OLD;
    }

    /**
     * Temporary data or store (with loading and storing)? default is false
     */
    public boolean isStoring() {
        return storing;
    }

    /**
     * Optimized for integer values? default is false
     */
    public boolean isInteg() {
        return integ;
    }

    /**
     * Backed by a single contiguous array (no segment math)? default is false
     */
    public boolean isSingleSegment() {
        return singleSegment;
    }

    /**
     * Backed by a single contiguous {@code long[]} (instead of {@code byte[]}) to allow a larger
     * capacity? Implies a single segment. default is false
     */
    public boolean isLongBacked() {
        return longBacked;
    }

    @Override
    public String toString() {
        String str;
        if (getMemRef() == MemRef.MMAP_OLD)
            str = "MMAP_OLD";
        else if (getMemRef() == MemRef.MMAP)
            str = "MMAP";
        else if (getMemRef() == MemRef.NATIVE)
            str = "NATIVE";
        else
            str = "RAM";

        if (isInteg())
            str += "_INT";
        if (isSingleSegment() && !isLongBacked())
            str += "_1SEG";
        if (isLongBacked())
            str += "_LONG";
        if (!isAllowWrites())
            str += "_RO";
        else if (isStoring())
            str += "_STORE";
        return str;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 59 * hash + 37 * this.memRef.hashCode();
        hash = 59 * hash + (this.storing ? 1 : 0);
        hash = 59 * hash + (this.integ ? 1 : 0);
        hash = 59 * hash + (this.singleSegment ? 1 : 0);
        hash = 59 * hash + (this.longBacked ? 1 : 0);
        hash = 59 * hash + (this.allowWrites ? 1 : 0);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        final DAType other = (DAType) obj;
        if (this.memRef != other.memRef)
            return false;
        if (this.storing != other.storing)
            return false;
        if (this.integ != other.integ)
            return false;
        if (this.singleSegment != other.singleSegment)
            return false;
        if (this.longBacked != other.longBacked)
            return false;
        if (this.allowWrites != other.allowWrites)
            return false;
        return true;
    }

    public enum MemRef {
        HEAP, NATIVE, MMAP, MMAP_OLD
    }
}
