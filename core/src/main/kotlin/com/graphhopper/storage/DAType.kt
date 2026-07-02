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
package com.graphhopper.storage

import com.graphhopper.util.Helper

/**
 * Defines how a DataAccess object is created.
 *
 * @author Peter Karich
 */
class DAType @JvmOverloads constructor(
    /**
     * Memory mapped or purely in memory? default is HEAP
     */
    internal val memRef: MemRef,
    /**
     * Temporary data or store (with loading and storing)? default is false
     */
    val isStoring: Boolean,
    /**
     * Optimized for integer values? default is false
     */
    val isInteg: Boolean,
    val isAllowWrites: Boolean,
    /**
     * Backed by a single contiguous array (no segment math)? default is false
     */
    val isSingleSegment: Boolean = false
) {

    constructor(type: DAType) : this(type.memRef, type.isStoring, type.isInteg, type.isAllowWrites, type.isSingleSegment)

    /**
     * @return true if data resides in the JVM heap.
     */
    val isInMemory: Boolean
        get() = memRef == MemRef.HEAP

    val isMMap: Boolean
        get() = memRef == MemRef.MMAP

    override fun toString(): String {
        var str = if (memRef == MemRef.MMAP) "MMAP" else "RAM"
        if (isInteg)
            str += "_INT"
        if (isSingleSegment)
            str += "_1SEG"
        if (isStoring)
            str += "_STORE"
        return str
    }

    override fun hashCode(): Int {
        var hash = 7
        hash = 59 * hash + 37 * this.memRef.hashCode()
        hash = 59 * hash + (if (this.isStoring) 1 else 0)
        hash = 59 * hash + (if (this.isInteg) 1 else 0)
        hash = 59 * hash + (if (this.isSingleSegment) 1 else 0)
        return hash
    }

    override fun equals(other: Any?): Boolean {
        if (other == null)
            return false
        if (javaClass != other.javaClass)
            return false
        other as DAType
        if (this.memRef != other.memRef)
            return false
        if (this.isStoring != other.isStoring)
            return false
        if (this.isInteg != other.isInteg)
            return false
        if (this.isSingleSegment != other.isSingleSegment)
            return false
        return true
    }

    enum class MemRef {
        HEAP, MMAP
    }

    companion object {
        /**
         * The DA object is hold entirely in-memory. Loading and flushing is a no-op. See RAMDataAccess.
         */
        @JvmField
        val RAM = DAType(MemRef.HEAP, false, false, true)

        /**
         * Optimized RAM DA type for integer access. The set and getBytes methods cannot be used.
         */
        @JvmField
        val RAM_INT = DAType(MemRef.HEAP, false, true, true)

        /**
         * The DA object is hold entirely in-memory. It will read load disc and flush to it if they
         * equivalent methods are called. See RAMDataAccess.
         */
        @JvmField
        val RAM_STORE = DAType(MemRef.HEAP, true, false, true)

        /**
         * Optimized RAM_STORE DA type for integer access. The set and getBytes methods cannot be used.
         */
        @JvmField
        val RAM_INT_STORE = DAType(MemRef.HEAP, true, true, true)

        /**
         * Like RAM_INT, but backed by a single contiguous int[] for maximum read speed.
         * Not a good fit if the array needs to be resized frequently. Limited to Integer.MAX_VALUE ints
         * No support for short,byte and bytes.
         */
        @JvmField
        val RAM_INT_1SEG = DAType(MemRef.HEAP, false, true, true, true)

        /**
         * See RAM_INT_1SEG
         */
        @JvmField
        val RAM_INT_1SEG_STORE = DAType(MemRef.HEAP, true, true, true, true)

        /**
         * Memory mapped DA object. See MMapDataAccess.
         */
        @JvmField
        val MMAP = DAType(MemRef.MMAP, true, false, true)

        /**
         * Read-only memory mapped DA object. To avoid write access useful for reading on mobile or
         * embedded data stores.
         */
        @JvmField
        val MMAP_RO = DAType(MemRef.MMAP, true, false, false)

        @JvmStatic
        fun fromString(dataAccess: String): DAType {
            val da = Helper.toUpperCase(dataAccess)
            return if (da.contains("SYNC"))
                throw IllegalArgumentException("SYNC option is no longer supported, see #982")
            else if (da.contains("MMAP_RO"))
                MMAP_RO
            else if (da.contains("MMAP"))
                MMAP
            else if (da.contains("UNSAFE"))
                throw IllegalArgumentException("UNSAFE option is no longer supported, see #1620")
            else if (da == "RAM")
                RAM
            else
                RAM_STORE
        }
    }
}
