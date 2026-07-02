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
import kotlin.math.ln

/**
 * This is an in-memory data structure based on an integer array. With the possibility to be stored
 * on flush().
 *
 * @author Peter Karich
 */
class RAMIntDataAccess(
    name: String,
    location: String,
    private var store: Boolean,
    segmentSize: Int
) : AbstractDataAccess(name, location, segmentSize) {

    private var segments: Array<IntArray> = emptyArray()
    private var segmentSizeIntsPower = 0

    init {
        // the virtual setSegmentSize call from the base class constructor ran before this
        // class' fields were initialized => re-run it to compute segmentSizeIntsPower (idempotent)
        setSegmentSize(segmentSizeInBytes)
    }

    /**
     * @param store true if in-memory data should be saved when calling flush
     */
    fun setStore(store: Boolean): RAMIntDataAccess {
        this.store = store
        return this
    }

    override val isStoring: Boolean
        get() = store

    override fun create(bytes: Long): RAMIntDataAccess {
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
                newSegs[i] = IntArray(1 shl segmentSizeIntsPower)
            }
            segments = newSegs
            return true
        } catch (err: OutOfMemoryError) {
            throw OutOfMemoryError(err.message + " - problem when allocating new memory. Old capacity: "
                    + cap + ", new bytes:" + newBytes + ", segmentSizeIntsPower:" + segmentSizeIntsPower
                    + ", new segments:" + segmentsToCreate + ", existing:" + segments.size)
        }
    }

    override fun loadExisting(): Boolean {
        if (segments.isNotEmpty())
            throw IllegalStateException("already initialized")

        if (isClosed)
            throw IllegalStateException("already closed")

        if (!store)
            return false

        val file = File(getFullName())
        if (!file.exists() || file.length() == 0L) {
            return false
        }
        try {
            RandomAccessFile(getFullName(), "r").use { raFile ->
                val byteCount = readHeader(raFile) - HEADER_OFFSET
                if (byteCount < 0) {
                    return false
                }
                val bytes = ByteArray(segmentSizeInBytes)
                raFile.seek(HEADER_OFFSET.toLong())
                // raFile.readInt() <- too slow
                var segmentCount = (byteCount / segmentSizeInBytes).toInt()
                if (byteCount % segmentSizeInBytes != 0L)
                    segmentCount++

                segments = Array(segmentCount) {
                    val read = raFile.read(bytes) / 4
                    val area = IntArray(read)
                    for (j in 0 until read) {
                        area[j] = bitUtil.toInt(bytes, j * 4)
                    }
                    area
                }
                return true
            }
        } catch (ex: IOException) {
            throw RuntimeException("Problem while loading " + getFullName(), ex)
        }
    }

    override fun flush() {
        if (closed) {
            throw IllegalStateException("already closed")
        }
        if (!store) {
            return
        }
        try {
            RandomAccessFile(getFullName(), "rw").use { raFile ->
                val len = capacity
                writeHeader(raFile, HEADER_OFFSET + len, segmentSizeInBytes)
                raFile.seek(HEADER_OFFSET.toLong())
                // raFile.writeInt() <- too slow, so copy into byte array
                for (s in segments.indices) {
                    val area = segments[s]
                    val intLen = area.size
                    val byteArea = ByteArray(intLen * 4)
                    for (i in 0 until intLen) {
                        bitUtil.fromInt(byteArea, area[i], i * 4)
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
        assert(segments.isNotEmpty()) { "call create or loadExisting before usage!" }
        val intPos = bytePos ushr 2
        val bufferIndex = (intPos ushr segmentSizeIntsPower).toInt()
        val index = (intPos and indexDivisor.toLong()).toInt()
        segments[bufferIndex][index] = value
    }

    override fun getInt(bytePos: Long): Int {
        assert(segments.isNotEmpty()) { "call create or loadExisting before usage!" }
        val intPos = bytePos ushr 2
        val bufferIndex = (intPos ushr segmentSizeIntsPower).toInt()
        val index = (intPos and indexDivisor.toLong()).toInt()
        return segments[bufferIndex][index]
    }

    override fun setShort(bytePos: Long, value: Short) {
        assert(segments.isNotEmpty()) { "call create or loadExisting before usage!" }
        if (bytePos % 4 != 0L && bytePos % 4 != 2L)
            throw IllegalMonitorStateException("bytePos of wrong multiple for RAMInt $bytePos")

        val tmpIndex = bytePos ushr 2
        val bufferIndex = (tmpIndex ushr segmentSizeIntsPower).toInt()
        val index = (tmpIndex and indexDivisor.toLong()).toInt()
        val oldVal = segments[bufferIndex][index]
        if (tmpIndex * 4 == bytePos)
            segments[bufferIndex][index] = (oldVal and 0xFFFF0000.toInt()) or (value.toInt() and 0x0000FFFF)
        else
            segments[bufferIndex][index] = (oldVal and 0x0000FFFF) or (value.toInt() shl 16)
    }

    override fun getShort(bytePos: Long): Short {
        assert(segments.isNotEmpty()) { "call create or loadExisting before usage!" }
        if (bytePos % 4 != 0L && bytePos % 4 != 2L)
            throw IllegalMonitorStateException("bytePos of wrong multiple for RAMInt $bytePos")

        val tmpIndex = bytePos shr 2
        val bufferIndex = (tmpIndex shr segmentSizeIntsPower).toInt()
        val index = (tmpIndex and indexDivisor.toLong()).toInt()
        return if (tmpIndex * 4 == bytePos)
            (segments[bufferIndex][index].toLong() and 0x0000FFFFL).toShort()
        else
            (segments[bufferIndex][index] shr 16).toShort()
    }

    override fun getBytes(bytePos: Long, values: ByteArray, length: Int) {
        throw UnsupportedOperationException("$this does not support byte based acccess. Use RAMDataAccess instead")
    }

    override fun setBytes(bytePos: Long, values: ByteArray, length: Int) {
        throw UnsupportedOperationException("$this does not support byte based acccess. Use RAMDataAccess instead")
    }

    override fun getByte(currentPointer: Long): Byte {
        throw UnsupportedOperationException("$this does not support byte based acccess. Use RAMDataAccess instead")
    }

    override fun setByte(currentPointer: Long, value: Byte) {
        throw UnsupportedOperationException("$this does not support byte based acccess. Use RAMDataAccess instead")
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

    override fun setSegmentSize(bytes: Int): DataAccess {
        super.setSegmentSize(bytes)
        segmentSizeIntsPower = (ln((segmentSizeInBytes / 4).toDouble()) / ln(2.0)).toInt()
        indexDivisor = segmentSizeInBytes / 4 - 1
        return this
    }

    override fun isIntBased(): Boolean = true

    override val type: DAType
        get() = if (isStoring) DAType.RAM_INT_STORE else DAType.RAM_INT
}
