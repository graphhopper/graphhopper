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
 * Defines how a DataAccess object is created.
 * TODO NOW: rework this class to better integrate "ForeignMemory" (with another MMAP kind)
 *
 * @author Peter Karich
 */
public class DAType {
    /**
     * The DA object is hold entirely in-memory. Loading and flushing is a no-op. See RAMDataAccess.
     */
    public static final DAType RAM = new DAType(MemRef.HEAP, false, false, true, false);
    /**
     * Optimized RAM DA type for integer access. The set and getBytes methods cannot be used.
     */
    public static final DAType RAM_INT = new DAType(MemRef.HEAP, false, true, true, false);
    /**
     * The DA object is hold entirely in-memory. It will read load disc and flush to it if they
     * equivalent methods are called. See RAMDataAccess.
     */
    public static final DAType RAM_STORE = new DAType(MemRef.HEAP, true, false, true, false);
    /**
     * Optimized RAM_STORE DA type for integer access. The set and getBytes methods cannot be used.
     */
    public static final DAType RAM_INT_STORE = new DAType(MemRef.HEAP, true, true, true, false);
    /**
     * Memory mapped DA object. See MMapDataAccess.
     */
    public static final DAType MMAP = new DAType(MemRef.MMAP, true, false, true, false);

    /**
     * Read-only memory mapped DA object. To avoid write access useful for reading on mobile or
     * embedded data stores.
     */
    public static final DAType MMAP_RO = new DAType(MemRef.MMAP, true, false, false, false);
    public static final DAType FOREIGN_MEMORY_STORE = new DAType(MemRef.OFFHEAP, true, false, true, true);
    public static final DAType FOREIGN_MEMORY = new DAType(MemRef.OFFHEAP, false, false, true, true);
    public static final DAType FOREIGN_MEMORY_MMAP = new DAType(MemRef.MMAP, true, false, true, true);
    private final MemRef memRef;
    private final boolean storing;
    private final boolean integ;
    private final boolean allowWrites;
    private boolean foreign;

    public DAType(DAType type) {
        this(type.getMemRef(), type.isStoring(), type.isInteg(), type.isAllowWrites(), false);
    }

    public DAType(MemRef memRef, boolean storing, boolean integ, boolean allowWrites, boolean foreign) {
        this.memRef = memRef;
        this.storing = storing;
        this.integ = integ;
        this.allowWrites = allowWrites;
        this.foreign = foreign;
    }

    public static DAType fromString(String dataAccess) {
        dataAccess = toUpperCase(dataAccess);
        DAType type;
        if (dataAccess.contains("SYNC"))
            throw new IllegalArgumentException("SYNC option is no longer supported, see #982");
        else if (dataAccess.contains("MMAP_RO"))
            type = DAType.MMAP_RO;
        else if (dataAccess.contains("MMAP"))
            type = DAType.MMAP;
        else if (dataAccess.contains("UNSAFE"))
            throw new IllegalArgumentException("UNSAFE option is no longer supported, see #1620");
        else if (dataAccess.equals("RAM"))
            type = DAType.RAM;
        else if (dataAccess.equals("FOREIGN_MEMORY"))
            type = DAType.FOREIGN_MEMORY;
        else if (dataAccess.equals("FOREIGN_MEMORY_STORE"))
            type = DAType.FOREIGN_MEMORY_STORE;
        else if (dataAccess.equals("FOREIGN_MEMORY_MMAP"))
            type = DAType.FOREIGN_MEMORY_MMAP;
        else
            type = DAType.RAM_STORE;
        return type;
    }

    /**
     * Memory mapped or purely in memory? default is HEAP
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
        return memRef == MemRef.MMAP;
    }

    /**
     * Temporary data or store (with loading and storing)? default is false
     */
    public boolean isStoring() {
        return storing;
    }

    public boolean isForeignMemory() {
        return foreign;
    }

    /**
     * Optimized for integer values? default is false
     */
    public boolean isInteg() {
        return integ;
    }

    @Override
    public String toString() {
        String str;
        if (isForeignMemory())
            str = getMemRef() == MemRef.MMAP ? "FOREIGN_MEMORY_MMAP" : "FOREIGN_MEMORY";
        else if (getMemRef() == MemRef.MMAP)
            str = "MMAP";
        else
            str = "RAM";

        if (isInteg())
            str += "_INT";
        if (isStoring())
            str += "_STORE";
        return str;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 59 * hash + 37 * this.memRef.hashCode();
        hash = 59 * hash + (this.storing ? 1 : 0);
        hash = 59 * hash + (this.integ ? 1 : 0);
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
        return true;
    }

    public enum MemRef {
        HEAP, MMAP, OFFHEAP
    }
}
