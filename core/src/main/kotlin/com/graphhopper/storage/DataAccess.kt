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

import java.io.Closeable

/**
 * Life cycle: (1) object creation, (2) configuration (e.g. segment size), (3) create or
 * loadExisting, (4) usage and calling ensureCapacity if necessary, (5) close
 *
 * @author Peter Karich
 */
interface DataAccess : Closeable {
    /**
     * The logical identification of this object.
     */
    val name: String

    /**
     * Set 4 bytes at position 'bytePos' to the specified value
     */
    fun setInt(bytePos: Long, value: Int)

    /**
     * Get 4 bytes from position 'bytePos'
     */
    fun getInt(bytePos: Long): Int

    /**
     * Set 2 bytes at position 'index' to the specified value
     */
    fun setShort(bytePos: Long, value: Short)

    /**
     * Get 2 bytes from position 'index'
     */
    fun getShort(bytePos: Long): Short

    /**
     * Set bytes from position 'index' to the specified values
     */
    fun setBytes(bytePos: Long, values: ByteArray, length: Int)

    /**
     * Get bytes from position 'index'
     *
     * @param values acts as output
     */
    fun getBytes(bytePos: Long, values: ByteArray, length: Int)

    fun setByte(currentPointer: Long, value: Byte)

    fun getByte(currentPointer: Long): Byte

    /**
     * Set 4 bytes at the header space index to the specified value
     */
    fun setHeader(bytePos: Int, value: Int)

    /**
     * Get 4 bytes from the header at 'index'
     */
    fun getHeader(bytePos: Int): Int

    /**
     * The first time you use a DataAccess object after configuring it you need to call this method.
     * After that first call you have to use ensureCapacity to ensure that enough space is reserved.
     */
    fun create(bytes: Long): DataAccess

    /**
     * This method makes sure that the underlying data is written to the storage. Keep in mind that
     * a disc normally has an IO cache so that flush() is (less) probably not save against power
     * loses.
     */
    fun flush()

    /**
     * This method makes sure that the underlying used resources are released. WARNING: it does NOT
     * flush on close!
     */
    override fun close()

    val isClosed: Boolean

    /**
     * @return true if successfully loaded from persistent storage.
     */
    fun loadExisting(): Boolean

    /**
     * @return the allocated storage size in bytes
     */
    val capacity: Long

    /**
     * Ensures that the capacity of this object is at least the specified bytes. The first time you
     * have to call 'create' instead.
     * <p>
     *
     * @return true if size was increased
     * @see #create(long)
     */
    fun ensureCapacity(bytes: Long): Boolean

    /**
     * Reduces the capacity to the specified number of bytes (rounded up to the next segment
     * boundary). The specified capacity must be less than or equal to the current capacity.
     * A capacity of zero releases all segments.
     */
    fun trimTo(capacity: Long)

    /**
     * @return the size of one segment in bytes
     */
    val segmentSize: Int

    /**
     * @return the number of segments.
     */
    fun getSegments(): Int

    /**
     * @return the data access type of this object.
     */
    val type: DAType
}
