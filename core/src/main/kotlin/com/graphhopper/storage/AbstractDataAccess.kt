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

import com.graphhopper.util.BitUtil
import com.graphhopper.util.Helper
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteOrder
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow

/**
 * @author Peter Karich
 */
abstract class AbstractDataAccess(
    final override val name: String,
    location: String,
    segmentSize: Int
) : DataAccess {

    @JvmField
    protected val byteOrder: ByteOrder = ByteOrder.LITTLE_ENDIAN

    @JvmField
    protected val bitUtil: BitUtil = BitUtil.LITTLE

    private val location: String

    @JvmField
    protected val header = IntArray((HEADER_OFFSET - 20) / 4)

    @JvmField
    protected var segmentSizeInBytes = 0

    @JvmField
    protected var segmentSizePower = 0

    @JvmField
    protected var indexDivisor = 0

    @JvmField
    protected var closed = false

    init {
        if (!Helper.isEmpty(location) && !location.endsWith("/"))
            throw IllegalArgumentException("Create DataAccess object via its corresponding Directory!")

        this.location = location
        setSegmentSize(if (segmentSize < 0) SEGMENT_SIZE_DEFAULT else segmentSize)
    }

    protected fun getFullName(): String = location + name

    override fun close() {
        closed = true
    }

    override val isClosed: Boolean
        get() = closed

    override fun setHeader(bytePos: Int, value: Int) {
        header[bytePos shr 2] = value
    }

    override fun getHeader(bytePos: Int): Int = header[bytePos shr 2]

    /**
     * Writes some internal data into the beginning of the specified file.
     */
    @Throws(IOException::class)
    protected fun writeHeader(file: RandomAccessFile, length: Long, segmentSize: Int) {
        file.seek(0)
        file.writeUTF("GH")
        file.writeLong(length)
        file.writeInt(segmentSize)
        for (i in header.indices) {
            file.writeInt(header[i])
        }
    }

    @Throws(IOException::class)
    protected fun readHeader(raFile: RandomAccessFile): Long {
        raFile.seek(0)
        if (raFile.length() == 0L)
            return -1

        val versionHint = raFile.readUTF()
        if ("GH" != versionHint)
            throw IllegalArgumentException("Not a GraphHopper file " + getFullName() + "! Expected 'GH' as file marker but was " + versionHint)

        val bytes = raFile.readLong()
        setSegmentSize(raFile.readInt())
        for (i in header.indices) {
            header[i] = raFile.readInt()
        }
        return bytes
    }

    protected fun copyHeader(da: DataAccess) {
        var h = 0
        while (h < header.size * 4) {
            da.setHeader(h, getHeader(h))
            h += 4
        }
    }

    internal open fun setSegmentSize(bytes: Int): DataAccess {
        if (bytes > 0) {
            // segment size should be a power of 2
            val tmp = (ln(bytes.toDouble()) / ln(2.0)).toInt()
            segmentSizeInBytes = max(2.0.pow(tmp.toDouble()).toInt(), SEGMENT_SIZE_MIN)
        }
        segmentSizePower = (ln(segmentSizeInBytes.toDouble()) / ln(2.0)).toInt()
        indexDivisor = segmentSizeInBytes - 1
        return this
    }

    override val segmentSize: Int
        get() = segmentSizeInBytes

    override fun toString(): String = getFullName()

    open val isStoring: Boolean
        get() = true

    protected open fun isIntBased(): Boolean = false

    companion object {
        protected const val SEGMENT_SIZE_MIN = 1 shl 7

        // reserve some space for downstream usage (in classes using/extending this)
        protected const val HEADER_OFFSET = 20 * 4 + 20

        @JvmField
        protected val EMPTY = ByteArray(1024)

        const val SEGMENT_SIZE_DEFAULT = 1 shl 20
    }
}
