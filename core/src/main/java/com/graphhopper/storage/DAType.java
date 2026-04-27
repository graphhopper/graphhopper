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
 * <p>
 *
 * @author Peter Karich
 */
public enum DAType {
    /**
     * The DA object is hold entirely in-memory. Loading and flushing is a no-op. See RAMDataAccess.
     */
    RAM(MemRef.HEAP, false, false, true),

    /**
     * Optimized RAM DA type for integer access. The set and getBytes methods cannot be used.
     */
    RAM_INT(MemRef.HEAP, false, true, true),

    /**
     * The DA object is hold entirely in-memory. It will read load disc and flush to it if they
     * equivalent methods are called. See RAMDataAccess.
     */
    RAM_STORE(MemRef.HEAP, true, false, true),

    /**
     * Optimized RAM_STORE DA type for integer access. The set and getBytes methods cannot be used.
     */
    RAM_INT_STORE(MemRef.HEAP, true, true, true),

    /**
     * Memory mapped DA object. See MMapDataAccess.
     */
    MMAP(MemRef.MMAP, true, false, true),
    // MMAP is MMAP_STORE
    MMAP_STORE(MemRef.MMAP, true, false, true),

    /**
     * Read-only memory mapped DA object. To avoid write access useful for reading on mobile or
     * embedded data stores.
     */
    MMAP_RO(MemRef.MMAP, true, false, false);

    private final MemRef memRef;
    private final boolean storing;
    private final boolean integ;
    private final boolean allowWrites;

    DAType(MemRef memRef, boolean storing, boolean integ, boolean allowWrites) {
        this.memRef = memRef;
        this.storing = storing;
        this.integ = integ;
        this.allowWrites = allowWrites;
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

    /**
     * Optimized for integer values? default is false
     */
    public boolean isInteg() {
        return integ;
    }

    public enum MemRef {
        HEAP, MMAP
    }
}
