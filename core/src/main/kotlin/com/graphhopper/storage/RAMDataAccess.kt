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
import java.lang.invoke.MethodHandles
import java.lang.invoke.VarHandle
import java.nio.ByteOrder
import java.util.Arrays

/**
 * This is an in-memory byte-based data structure with the possibility to be stored on flush().
 * Read thread-safe.
 *
 * @author Peter Karich
 */
class RAMDataAccess(
    name: String,
    location: String,
    private var store: Boolean,
    segmentSize: Int
) : AbstractDataAccess(name, location, segmentSize) {

    private var segments: Array<ByteArray> = emptyArray()

    /**
     * @param store true if in-memory data should be saved when calling flush
     */
    fun store(store: Boolean): RAMDataAccess {
        this.store = store
        return this
    }

    override val isStoring: Boolean
        get() = store

    override fun create(bytes: Long): RAMDataAccess {
        if (segments.isNotEmpty())
            throw IllegalThreadStateException("already created")

        ensureCapacity(Math.max(10L * 4, bytes))
        return this
    }

    override fun ensureCapacity(bytes: Long): Boolean {
        if (bytes < 0)
            throw IllegalArgumentException("new capacity has to be strictly positive")

        val cap = capacity
        val newBytes = bytes - cap
        if (newBytes <= 0)
            return false

        var segmentsToCreate = (newBytes / segmentSizeInBytes).toInt()
        if (newBytes % segmentSizeInBytes != 0L)
            segmentsToCreate++

        try {
            val newSegs = Arrays.copyOf(segments, segments.size + segmentsToCreate)
            for (i in segments.size until newSegs.size) {
                newSegs[i] = ByteArray(1 shl segmentSizePower)
            }
            segments = newSegs
        } catch (err: OutOfMemoryError) {
            throw OutOfMemoryError(err.message + " - problem when allocating new memory. Old capacity: "
                    + cap + ", new bytes:" + newBytes + ", segmentSizeIntsPower:" + segmentSizePower
                    + ", new segments:" + segmentsToCreate + ", existing:" + segments.size)
        }
        return true
    }

    override fun loadExisting(): Boolean {
        if (segments.isNotEmpty())
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
                if (byteCount < 0)
                    return false

                raFile.seek(HEADER_OFFSET.toLong())
                // raFile.readInt() <- too slow
                var segmentCount = (byteCount / segmentSizeInBytes).toInt()
                if (byteCount % segmentSizeInBytes != 0L)
                    segmentCount++

                segments = Array(segmentCount) { s ->
                    val bytes = ByteArray(segmentSizeInBytes)
                    val read = raFile.read(bytes)
                    if (read <= 0)
                        throw IllegalStateException("segment $s is empty? $this")

                    bytes
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
                writeHeader(raFile, HEADER_OFFSET + len, segmentSizeInBytes)
                raFile.seek(HEADER_OFFSET.toLong())
                // raFile.writeInt() <- too slow, so copy into byte array
                for (s in segments.indices) {
                    val area = segments[s]
                    raFile.write(area)
                }
                raFile.setLength(HEADER_OFFSET + len)
            }
        } catch (ex: Exception) {
            throw RuntimeException("Couldn't store bytes to $this", ex)
        }
    }

    override fun setInt(bytePos: Long, value: Int) {
        assert(segmentSizePower > 0) { "call create or loadExisting before usage!" }
        val bufferIndex = (bytePos ushr segmentSizePower).toInt()
        val index = (bytePos and indexDivisor.toLong()).toInt()
        if (index + 3 >= segmentSizeInBytes) {
            // seldom and special case if int has to be written into two separate segments
            val b1 = segments[bufferIndex]
            val b2 = segments[bufferIndex + 1]
            if (index + 1 >= segmentSizeInBytes) {
                bitUtil.fromUInt3(b2, value ushr 8, 0)
                b1[index] = value.toByte()
            } else if (index + 2 >= segmentSizeInBytes) {
                bitUtil.fromShort(b2, (value ushr 16).toShort(), 0)
                bitUtil.fromShort(b1, value.toShort(), index)
            } else {
                // index + 3 >= segmentSizeInBytes
                b2[0] = (value ushr 24).toByte()
                bitUtil.fromUInt3(b1, value, index)
            }
        } else {
            INT.set(segments[bufferIndex], index, value)
        }
    }

    override fun getInt(bytePos: Long): Int {
        assert(segments.isNotEmpty()) { "call create or loadExisting before usage!" }
        val bufferIndex = (bytePos ushr segmentSizePower).toInt()
        val index = (bytePos and indexDivisor.toLong()).toInt()
        if (index + 3 >= segmentSizeInBytes) {
            val b1 = segments[bufferIndex]
            val b2 = segments[bufferIndex + 1]
            if (index + 1 >= segmentSizeInBytes)
                return ((b2[2].toInt() and 0xFF) shl 24) or ((b2[1].toInt() and 0xFF) shl 16) or ((b2[0].toInt() and 0xFF) shl 8) or (b1[index].toInt() and 0xFF)
            if (index + 2 >= segmentSizeInBytes)
                return ((b2[1].toInt() and 0xFF) shl 24) or ((b2[0].toInt() and 0xFF) shl 16) or ((b1[index + 1].toInt() and 0xFF) shl 8) or (b1[index].toInt() and 0xFF)
            // index + 3 >= segmentSizeInBytes
            return ((b2[0].toInt() and 0xFF) shl 24) or ((b1[index + 2].toInt() and 0xFF) shl 16) or ((b1[index + 1].toInt() and 0xFF) shl 8) or (b1[index].toInt() and 0xFF)
        }
        return INT.get(segments[bufferIndex], index) as Int
    }

    override fun setShort(bytePos: Long, value: Short) {
        assert(segments.isNotEmpty()) { "call create or loadExisting before usage!" }
        val bufferIndex = (bytePos ushr segmentSizePower).toInt()
        val index = (bytePos and indexDivisor.toLong()).toInt()
        if (index + 1 >= segmentSizeInBytes) {
            // seldom and special case if short has to be written into two separate segments
            segments[bufferIndex][index] = value.toByte()
            segments[bufferIndex + 1][0] = (value.toInt() ushr 8).toByte()
        } else {
            SHORT.set(segments[bufferIndex], index, value)
        }
    }

    override fun getShort(bytePos: Long): Short {
        assert(segments.isNotEmpty()) { "call create or loadExisting before usage!" }
        val bufferIndex = (bytePos ushr segmentSizePower).toInt()
        val index = (bytePos and indexDivisor.toLong()).toInt()
        if (index + 1 >= segmentSizeInBytes)
            return (((segments[bufferIndex + 1][0].toInt() and 0xFF) shl 8) or (segments[bufferIndex][index].toInt() and 0xFF)).toShort()

        return SHORT.get(segments[bufferIndex], index) as Short
    }

    override fun setBytes(bytePos: Long, values: ByteArray, length: Int) {
        assert(length <= segmentSizeInBytes) { "the length has to be smaller or equal to the segment size: $length vs. $segmentSizeInBytes" }
        assert(segments.isNotEmpty()) { "call create or loadExisting before usage!" }
        val bufferIndex = (bytePos ushr segmentSizePower).toInt()
        val index = (bytePos and indexDivisor.toLong()).toInt()
        var seg = segments[bufferIndex]
        val delta = index + length - segmentSizeInBytes
        if (delta > 0) {
            val len = length - delta
            System.arraycopy(values, 0, seg, index, len)
            seg = segments[bufferIndex + 1]
            System.arraycopy(values, len, seg, 0, delta)
        } else {
            System.arraycopy(values, 0, seg, index, length)
        }
    }

    override fun getBytes(bytePos: Long, values: ByteArray, length: Int) {
        assert(length <= segmentSizeInBytes) { "the length has to be smaller or equal to the segment size: $length vs. $segmentSizeInBytes" }
        assert(segments.isNotEmpty()) { "call create or loadExisting before usage!" }
        val bufferIndex = (bytePos ushr segmentSizePower).toInt()
        val index = (bytePos and indexDivisor.toLong()).toInt()
        var seg = segments[bufferIndex]
        val delta = index + length - segmentSizeInBytes
        if (delta > 0) {
            val len = length - delta
            System.arraycopy(seg, index, values, 0, len)
            seg = segments[bufferIndex + 1]
            System.arraycopy(seg, 0, values, len, delta)
        } else {
            System.arraycopy(seg, index, values, 0, length)
        }
    }

    override fun setByte(currentPointer: Long, value: Byte) {
        assert(segments.isNotEmpty()) { "call create or loadExisting before usage!" }
        val bufferIndex = (currentPointer ushr segmentSizePower).toInt()
        val index = (currentPointer and indexDivisor.toLong()).toInt()
        segments[bufferIndex][index] = value
    }

    override fun getByte(currentPointer: Long): Byte {
        assert(segments.isNotEmpty()) { "call create or loadExisting before usage!" }
        val bufferIndex = (currentPointer ushr segmentSizePower).toInt()
        val index = (currentPointer and indexDivisor.toLong()).toInt()
        return segments[bufferIndex][index]
    }

    override fun trimTo(capacity: Long) {
        if (capacity < 0)
            throw IllegalArgumentException("capacity must not be negative")
        if (capacity > this.capacity)
            throw IllegalArgumentException("capacity cannot be larger than the current capacity: " + capacity + " > " + this.capacity)

        var newSegmentCount = (capacity / segmentSizeInBytes).toInt()
        if (capacity % segmentSizeInBytes != 0L)
            newSegmentCount++

        if (newSegmentCount < segments.size)
            segments = Arrays.copyOf(segments, newSegmentCount)
    }

    override fun close() {
        super.close()
        segments = emptyArray()
        closed = true
    }

    override val capacity: Long
        get() = getSegments().toLong() * segmentSizeInBytes

    override fun getSegments(): Int = segments.size

    override val type: DAType
        get() = if (isStoring) DAType.RAM_STORE else DAType.RAM

    companion object {
        // we could also use UNSAFE but it is not really faster (see #3005)
        private val INT: VarHandle =
            MethodHandles.byteArrayViewVarHandle(IntArray::class.java, ByteOrder.LITTLE_ENDIAN).withInvokeExactBehavior()
        private val SHORT: VarHandle =
            MethodHandles.byteArrayViewVarHandle(ShortArray::class.java, ByteOrder.LITTLE_ENDIAN).withInvokeExactBehavior()
    }
}
