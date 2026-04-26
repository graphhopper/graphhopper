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
 * TODO NOW: rework this class to better integrate "ForeignMemory" (with another MMAP kind). Also make preload configs working.
 *
 * @author Peter Karich
 */
public enum DAType {
    /**
     * The DA object is hold entirely in-memory. Loading and flushing is a no-op. See RAMDataAccess.
     */
    RAM(false, false, false, false, true),
    /**
     * Optimized RAM DA type for integer access. The set and getBytes methods cannot be used.
     */
    RAM_INT(false, false, false, true, true),
    /**
     * The DA object is hold entirely in-memory. It will read load disc and flush to it if they
     * equivalent methods are called. See RAMDataAccess.
     */
    RAM_STORE(false, false, true, false, true),
    /**
     * Optimized RAM_STORE DA type for integer access. The set and getBytes methods cannot be used.
     */
    RAM_INT_STORE(false, false, true, true, true),
    MMAP(true, true, true, false, true),
    /**
     * Read-only memory mapped DA object. To avoid write access useful for reading on mobile or
     * embedded data stores.
     */
    MMAP_RO(true, true, true, false, false),
    /**
     * Old memory mapped. See MMapDataAccess.
     */
    MMAP_OLD(true, true, true, false, true),
    FOREIGN_MEMORY_STORE(true, false, true, false, true),
    FOREIGN_MEMORY(true, false, false, false, true);

    private final boolean offHeap;
    private final boolean mmap;
    private final boolean storing;
    private final boolean integ;
    private final boolean allowWrites;

    DAType(boolean offHeap, boolean mmap, boolean storing, boolean integ, boolean allowWrites) {
        this.offHeap = offHeap;
        this.mmap = mmap;
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
        return !offHeap;
    }

    public boolean isMMap() {
        return mmap;
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
}
