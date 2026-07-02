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

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.Arrays

/**
 * Based on a single int[] array, which provides faster reading speed than RAMIntDataAccess which uses nested int[][].
 * On the flip-side resizing requires expensive copying, and the number of elements is limited to 2B four-byte integers (~8GB).
 */
class RAMInt1SegmentDataAccess(
    name: String,
    location: String,
    private val store: Boolean,
    segmentSize: Int
) : AbstractDataAccess(name, location, segmentSize) {

    private var data = IntArray(0)

    override fun create(bytes: Long): RAMInt1SegmentDataAccess {
        if (data.isNotEmpty())
            throw IllegalThreadStateException("already created")
        ensureCapacity(Math.max(10L * 4, bytes))
        return this
    }

    override fun ensureCapacity(bytes: Long): Boolean {
        if (bytes < 0)
            throw IllegalArgumentException("new capacity has to be strictly positive")

        val cap = capacity
        if (bytes <= cap)
            return false

        // round up to segment size for compatibility with the file format used by other DataAccess implementations
        var newCap = bytes
        if (newCap % segmentSizeInBytes != 0L)
            newCap = (newCap / segmentSizeInBytes + 1) * segmentSizeInBytes
        if (newCap / 4 > Integer.MAX_VALUE)
            throw RuntimeException("Cannot ensure capacity for " + bytes + " bytes using RAMInt1SegmentDataAccess. Max: " + (Integer.MAX_VALUE * 4L))

        try {
            data = Arrays.copyOf(data, (newCap / 4).toInt())
        } catch (err: OutOfMemoryError) {
            throw OutOfMemoryError(err.message + " - problem when allocating new memory. Old capacity: "
                    + cap + ", requested bytes:" + bytes)
        }
        return true
    }

    override fun loadExisting(): Boolean {
        if (data.isNotEmpty())
            throw IllegalStateException("already initialized")

        if (isClosed)
            throw IllegalStateException("already closed")

        if (!store)
            return false

        val file = File(getFullName())
        if (!file.exists() || file.length() == 0L)
            return false

        try {
            RandomAccessFile(getFullName(), "r").use { raFile ->
                val byteCount = readHeader(raFile) - HEADER_OFFSET
                if (byteCount < 0) {
                    return false
                }
                val bytes = ByteArray(segmentSizeInBytes)
                raFile.seek(HEADER_OFFSET.toLong())
                // raFile.readInt() <- too slow, so read into byte buffer and convert
                var segmentCount = (byteCount / segmentSizeInBytes).toInt()
                if (byteCount % segmentSizeInBytes != 0L)
                    segmentCount++

                val intsPerSegment = segmentSizeInBytes / 4
                val totalInts = segmentCount.toLong() * intsPerSegment
                if (totalInts > Integer.MAX_VALUE)
                    throw RuntimeException("File " + getFullName() + " is too large to be loaded with RAMInt1SegmentDataAccess. total ints: " + totalInts)
                data = IntArray(segmentCount * intsPerSegment)
                for (s in 0 until segmentCount) {
                    val read = raFile.read(bytes) / 4
                    val offset = s * intsPerSegment
                    for (j in 0 until read) {
                        data[offset + j] = bitUtil.toInt(bytes, j * 4)
                    }
                }
                return true
            }
        } catch (ex: IOException) {
            throw RuntimeException("Problem while loading " + getFullName(), ex)
        }
    }

    override fun flush() {
        if (closed)
            throw IllegalStateException("already closed")
        if (!store)
            return

        try {
            RandomAccessFile(getFullName(), "rw").use { raFile ->
                val len = capacity
                writeHeader(raFile, len, segmentSizeInBytes)
                raFile.seek(HEADER_OFFSET.toLong())
                // raFile.writeInt() <- too slow, so copy into byte array
                val segmentCount = getSegments()
                val intsPerSegment = segmentSizeInBytes / 4
                for (s in 0 until segmentCount) {
                    val offset = s * intsPerSegment
                    val intLen = Math.min(intsPerSegment, data.size - offset)
                    val byteArea = ByteArray(intLen * 4)
                    for (i in 0 until intLen) {
                        bitUtil.fromInt(byteArea, data[offset + i], i * 4)
                    }
                    raFile.write(byteArea)
                }
                raFile.setLength(HEADER_OFFSET + len)
            }
        } catch (ex: Exception) {
            throw RuntimeException("Couldn't store integers to $this", ex)
        }
    }

    override fun setInt(bytePos: Long, value: Int) {
        assert(data.isNotEmpty()) { "call create or loadExisting before usage!" }
        data[(bytePos ushr 2).toInt()] = value
    }

    override fun getInt(bytePos: Long): Int {
        assert(data.isNotEmpty()) { "call create or loadExisting before usage!" }
        return data[(bytePos ushr 2).toInt()]
    }

    fun getIntRaw(index: Int): Int = data[index]

    override fun setShort(bytePos: Long, value: Short) {
        throw UnsupportedOperationException("$this does not support short access. Use RAMDataAccess instead")
    }

    override fun getShort(bytePos: Long): Short {
        throw UnsupportedOperationException("$this does not support short access. Use RAMDataAccess instead")
    }

    override fun setBytes(bytePos: Long, values: ByteArray, length: Int) {
        throw UnsupportedOperationException("$this does not support byte based access. Use RAMDataAccess instead")
    }

    override fun getBytes(bytePos: Long, values: ByteArray, length: Int) {
        throw UnsupportedOperationException("$this does not support byte based access. Use RAMDataAccess instead")
    }

    override fun setByte(currentPointer: Long, value: Byte) {
        throw UnsupportedOperationException("$this does not support byte based access. Use RAMDataAccess instead")
    }

    override fun getByte(currentPointer: Long): Byte {
        throw UnsupportedOperationException("$this does not support byte based access. Use RAMDataAccess instead")
    }

    override fun trimTo(capacity: Long) {
        if (capacity < 0)
            throw IllegalArgumentException("capacity must not be negative")
        if (capacity > this.capacity)
            throw IllegalArgumentException("capacity cannot be larger than the current capacity: " + capacity + " > " + this.capacity)

        var newSegmentCount = (capacity / segmentSizeInBytes).toInt()
        if (capacity % segmentSizeInBytes != 0L)
            newSegmentCount++

        val newIntCount = newSegmentCount * (segmentSizeInBytes / 4)
        if (newIntCount < data.size)
            data = Arrays.copyOf(data, newIntCount)
    }

    override fun close() {
        super.close()
        data = IntArray(0)
    }

    override val capacity: Long
        get() = data.size.toLong() * 4

    override fun getSegments(): Int = data.size / (segmentSizeInBytes / 4)

    override val isStoring: Boolean
        get() = store

    override fun isIntBased(): Boolean = true

    override val type: DAType
        get() = if (isStoring) DAType.RAM_INT_1SEG_STORE else DAType.RAM_INT_1SEG
}
