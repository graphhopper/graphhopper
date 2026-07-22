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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.graphhopper.util.Helper.toUpperCase;

/**
 * Defines how a DataAccess object is created and creates it: every DAType brings its own factory.
 * This is a value object and not an enum so that new types can be {@link #register}ed outside of
 * core.
 * <p>
 *
 * @author Peter Karich
 */
public class DAType {
    private static final Map<String, DAType> REGISTRY = new ConcurrentHashMap<>();

    /**
     * The DA object is hold entirely in-memory. It only reads from resp. writes to a backing file
     * if loadExisting resp. flush are called, so whether it persists is decided by the caller, not
     * by the type. See RAMDataAccess.
     */
    public static final DAType RAM = register("RAM", true, false,
            (name, location, segmentSize, preload, readOnly) -> new RAMDataAccess(name, location, readOnly, segmentSize));
    /**
     * Optimized RAM DA type for integer access. The set and getBytes methods cannot be used.
     */
    public static final DAType RAM_INT = register("RAM_INT", true, false,
            (name, location, segmentSize, preload, readOnly) -> new RAMIntDataAccess(name, location, readOnly, segmentSize));
    /**
     * Like RAM_INT, but backed by a single contiguous int[] for maximum read speed.
     * Not a good fit if the array needs to be resized frequently. Limited to Integer.MAX_VALUE ints
     * No support for short,byte and bytes.
     */
    public static final DAType RAM_INT_1SEG = register("RAM_INT_1SEG", true, false,
            (name, location, segmentSize, preload, readOnly) -> new RAMInt1SegmentDataAccess(name, location, readOnly, segmentSize));
    /**
     * Like RAM, but backed by a single contiguous byte[] (no segment math). Limited to ~2GB.
     * The on-heap equivalent of FOREIGN_ANON. See RAM1SegmentDataAccess.
     */
    public static final DAType RAM_1SEG = register("RAM_1SEG", true, false,
            (name, location, segmentSize, preload, readOnly) -> new RAM1SegmentDataAccess(name, location, readOnly, segmentSize));
    /**
     * Like RAM_1SEG (single contiguous heap array, full byte access), but backed by a {@code long[]}
     * instead of a {@code byte[]} to allow up to ~16GB. See RAMLongDataAccess.
     */
    public static final DAType RAM_LONG = register("RAM_LONG", true, false,
            (name, location, segmentSize, preload, readOnly) -> new RAMLongDataAccess(name, location, readOnly, segmentSize));
    /**
     * Off-heap DA object backed by anonymous (foreign) memory - the equivalent of RAM but outside
     * the JVM heap. See ForeignMemoryDataAccess.
     */
    public static final DAType FOREIGN_ANON = register("FOREIGN_ANON", false, false,
            (name, location, segmentSize, preload, readOnly) -> new ForeignMemoryDataAccess(name, location, readOnly, segmentSize));
    /**
     * Memory mapped DA object backed by the Foreign Memory API. Always writes a file when created. The
     * caller cannot keep it in-memory. See MMapForeignMemoryDataAccess.
     * In read-only mode the MMapForeignReadOnlyDataAccess with a fast path due to all-final fields
     * is used instead: the file must already exist on disk, there is no "loadExisting returned
     * false" state as the factory fails fast.
     */
    public static final DAType FOREIGN_MMAP = register("FOREIGN_MMAP", false, true,
            (name, location, segmentSize, preload, readOnly) -> readOnly
                    ? MMapForeignReadOnlyDataAccess.load(name, location, segmentSize, preload > 0)
                    : new MMapForeignMemoryDataAccess(name, location, true, segmentSize));
    /**
     * Legacy memory mapped DA object backed by ByteBuffers instead of the Foreign Memory API.
     * Always writes a file when created, it cannot be kept in-memory. Kept usable as a fallback and
     * for comparison. See MMapDataAccess.
     */
    public static final DAType MMAP = register("MMAP", false, true,
            (name, location, segmentSize, preload, readOnly) -> new MMapDataAccess(name, location, !readOnly, segmentSize));

    static {
        // legacy names, still accepted in configs
        alias("RAM_STORE", RAM);
        alias("RAM_INT_STORE", RAM_INT);
    }

    private final String name;
    private final boolean onHeap;
    private final boolean mmap;
    private final DataAccessFactory factory;

    private DAType(String name, boolean onHeap, boolean mmap, DataAccessFactory factory) {
        this.name = name;
        this.onHeap = onHeap;
        this.mmap = mmap;
        this.factory = factory;
    }

    /**
     * Registers a new DAType under the given name so that it is available via {@link #fromString}
     * and can be used everywhere a predefined type can, e.g. in the graph.dataaccess configuration.
     * Whether a created DataAccess persists to a backing file is decided by the caller (by calling
     * loadExisting resp. flush or not), not by the type.
     *
     * @param onHeap  true if the data resides in the JVM heap
     * @param mmap    true if the backing file is memory mapped instead of being read and written
     *                explicitly on loadExisting and flush
     */
    public static DAType register(String name, boolean onHeap, boolean mmap, DataAccessFactory factory) {
        DAType type = new DAType(toUpperCase(name), onHeap, mmap, factory);
        if (REGISTRY.putIfAbsent(type.name, type) != null)
            throw new IllegalArgumentException("DAType " + type.name + " is already registered");
        return type;
    }

    private static DAType alias(String name, DAType type) {
        if (REGISTRY.putIfAbsent(name, type) != null)
            throw new IllegalArgumentException("DAType " + name + " is already registered");
        return type;
    }

    /**
     * Returns the registered DAType for the given name, e.g. "RAM", "RAM_INT_1SEG",
     * "MMAP" or "FOREIGN_MMAP".
     */
    public static DAType fromString(String dataAccess) {
        dataAccess = toUpperCase(dataAccess);
        if (dataAccess.contains("SYNC"))
            throw new IllegalArgumentException("SYNC option is no longer supported, see #982");
        DAType type = REGISTRY.get(dataAccess);
        if (type == null) {
            if (dataAccess.endsWith("_RO"))
                throw new IllegalArgumentException("DAType " + dataAccess + " no longer exists, use "
                        + dataAccess.substring(0, dataAccess.length() - "_RO".length())
                        + " with the system-wide read-only mode instead (graph.read_only)");
            throw new IllegalArgumentException("Unknown DAType " + dataAccess + ", supported: " + REGISTRY.keySet());
        }
        return type;
    }

    /**
     * Creates the DataAccess object of this type.
     *
     * @param preload  percentage of the backing file to load into physical memory upfront, so far
     *                 only used by the read-only FOREIGN_MMAP
     * @param readOnly if true the backing file must not be modified: memory mapped types map it
     *                 read-only (enforced by the OS) and all other types throw on flush. Useful
     *                 for read-only filesystems, see GraphHopper.setReadOnly.
     */
    public DataAccess create(String name, String location, int segmentSize, int preload, boolean readOnly) {
        return factory.create(name, location, segmentSize, preload, readOnly);
    }

    /**
     * @return true if data resides in the JVM heap.
     */
    public boolean isOnHeap() {
        return onHeap;
    }

    public boolean isMMap() {
        return mmap;
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        return name.equals(((DAType) obj).name);
    }

    @FunctionalInterface
    public interface DataAccessFactory {
        /**
         * @see DAType#create(String, String, int, int, boolean, boolean)
         */
        DataAccess create(String name, String location, int segmentSize, int preload, boolean readOnly);
    }
}
