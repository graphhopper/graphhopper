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

import com.graphhopper.util.Constants
import com.graphhopper.util.Helper
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * A DataAccess implementation using a memory-mapped file, i.e. a facility of the
 * operating system to access a file like an area of RAM.
 * <p>
 * Java presents the mapped memory as a ByteBuffer, and ByteBuffer is not
 * thread-safe, which means that access to a ByteBuffer must be externally
 * synchronized.
 * <p>
 * This class itself is intended to be as thread-safe as other DataAccess
 * implementations are.
 * <p>
 * The exact behavior of memory-mapping is reported to be wildly platform-dependent.
 *
 * @author Peter Karich
 * @author Michael Zilske
 */
class MMapDataAccess(
    name: String,
    location: String,
    private val allowWrites: Boolean,
    segmentSize: Int
) : AbstractDataAccess(name, location, segmentSize) {

    private var raFile: RandomAccessFile? = null
    private val segments = ArrayList<MappedByteBuffer?>()

    private fun initRandomAccessFile() {
        if (raFile != null)
            return

        try {
            // raFile necessary for loadExisting and create
            raFile = RandomAccessFile(getFullName(), if (allowWrites) "rw" else "r")
        } catch (ex: IOException) {
            throw RuntimeException(ex)
        }
    }

    override fun create(bytes: Long): MMapDataAccess {
        if (segments.isNotEmpty()) {
            throw IllegalThreadStateException("already created")
        }
        initRandomAccessFile()
        ensureCapacity(Math.max(10L * 4, bytes))
        return this
    }

    override fun ensureCapacity(bytes: Long): Boolean {
        return mapIt(HEADER_OFFSET.toLong(), bytes)
    }

    private fun mapIt(offset: Long, byteCount: Long): Boolean {
        if (byteCount < 0)
            throw IllegalArgumentException("new capacity has to be strictly positive")

        if (byteCount <= capacity)
            return false

        val longSegmentSize = segmentSizeInBytes.toLong()
        var segmentsToMap = (byteCount / longSegmentSize).toInt()
        if (segmentsToMap < 0)
            throw IllegalStateException("Too many segments needs to be allocated. Increase segmentSize.")

        if (byteCount % longSegmentSize != 0L)
            segmentsToMap++

        if (segmentsToMap == 0)
            throw IllegalStateException("0 segments are not allowed.")

        var bufferStart = offset
        val newSegments: Int
        var i = 0
        val newFileLength = offset + segmentsToMap * longSegmentSize
        try {
            // ugly remapping
            // http://stackoverflow.com/q/14011919/194609
            // This approach is probably problematic but a bit faster if done often.
            // Here we rely on the OS+file system that increasing the file
            // size has no effect on the old mappings!
            bufferStart += segments.size * longSegmentSize
            newSegments = segmentsToMap - segments.size
            // rely on automatically increasing when mapping
            // raFile.setLength(newFileLength);
            while (i < newSegments) {
                segments.add(newByteBuffer(bufferStart, longSegmentSize))
                bufferStart += longSegmentSize
                i++
            }
            return true
        } catch (ex: IOException) {
            // we could get an exception here if buffer is too small and area too large
            // e.g. I got an exception for the 65421th buffer (probably around 2**16 == 65536)
            throw RuntimeException("Couldn't map buffer " + i + " of " + segmentsToMap + " with " + longSegmentSize
                    + " for " + name + " at position " + bufferStart + " for " + byteCount + " bytes with offset " + offset
                    + ", new fileLength:" + newFileLength + ", " + Helper.getMemInfo(), ex)
        }
    }

    @Throws(IOException::class)
    private fun newByteBuffer(offset: Long, byteCount: Long): MappedByteBuffer {
        // If we request a buffer larger than the file length, it will automatically increase the file length!
        // Will this cause problems? http://stackoverflow.com/q/14011919/194609
        // For trimTo we need to reset the file length later to reduce that size
        var buf: MappedByteBuffer? = null
        var ioex: IOException? = null
        // One retry if it fails. It could fail e.g. if previously buffer wasn't yet unmapped from the jvm
        var trial = 0
        while (trial < 1) {
            try {
                buf = raFile!!.channel.map(
                        if (allowWrites) FileChannel.MapMode.READ_WRITE else FileChannel.MapMode.READ_ONLY, offset, byteCount)
                break
            } catch (tmpex: IOException) {
                ioex = tmpex
                trial++
                try {
                    // mini sleep to let JVM do unmapping
                    Thread.sleep(5)
                } catch (iex: InterruptedException) {
                    throw IOException(iex)
                }
            }
        }
        if (buf == null) {
            if (ioex == null) {
                throw AssertionError("internal problem as the exception 'ioex' shouldn't be null")
            }
            throw ioex
        }

        buf.order(byteOrder)
        return buf
    }

    override fun loadExisting(): Boolean {
        if (segments.size > 0)
            throw IllegalStateException("already initialized")

        if (isClosed)
            throw IllegalStateException("already closed")

        val file = File(getFullName())
        if (!file.exists() || file.length() == 0L)
            return false

        initRandomAccessFile()
        try {
            val byteCount = readHeader(raFile!!)
            if (byteCount < 0)
                return false

            mapIt(HEADER_OFFSET.toLong(), byteCount - HEADER_OFFSET)
            return true
        } catch (ex: IOException) {
            throw RuntimeException("Problem while loading " + getFullName(), ex)
        }
    }

    override fun flush() {
        if (isClosed)
            throw IllegalStateException("already closed")

        try {
            for (bb in segments) {
                bb!!.force()
            }
            writeHeader(raFile!!, raFile!!.length(), segmentSizeInBytes)

            // this could be necessary too
            // http://stackoverflow.com/q/14011398/194609
            raFile!!.fd.sync()
            // equivalent to raFile.getChannel().force(true);
        } catch (ex: Exception) {
            throw RuntimeException(ex)
        }
    }

    /**
     * Load memory mapped files into physical memory.
     */
    fun load(percentage: Int) {
        if (percentage < 0 || percentage > 100)
            throw IllegalArgumentException("Percentage for MMapDataAccess.load for $name must be in [0,100] but was $percentage")
        val max = Math.round(segments.size * percentage / 100f)
        for (i in 0 until max) {
            segments[i]!!.load()
        }
    }

    override fun trimTo(capacity: Long) {
        if (capacity < 0)
            throw IllegalArgumentException("capacity must not be negative")
        if (capacity > this.capacity)
            throw IllegalArgumentException("capacity cannot be larger than the current capacity: " + capacity + " > " + this.capacity)

        var newSegmentCount = (capacity / segmentSizeInBytes).toInt()
        if (capacity % segmentSizeInBytes != 0L)
            newSegmentCount++

        if (newSegmentCount < segments.size) {
            try {
                if (Constants.WINDOWS) {
                    // Windows refuses setLength while any mapping on the file is open, so unmap
                    // all segments before truncating and remap the remaining ones afterwards.
                    // Might be slightly slower so do this only for Windows.
                    clean(0, segments.size)
                    segments.clear()
                    raFile!!.setLength(HEADER_OFFSET + newSegmentCount.toLong() * segmentSizeInBytes)
                    var bufferStart = HEADER_OFFSET.toLong()
                    for (i in 0 until newSegmentCount) {
                        segments.add(newByteBuffer(bufferStart, segmentSizeInBytes.toLong()))
                        bufferStart += segmentSizeInBytes
                    }
                } else {
                    clean(newSegmentCount, segments.size)
                    segments.subList(newSegmentCount, segments.size).clear()
                    raFile!!.setLength(HEADER_OFFSET + this.capacity)
                }
            } catch (ex: IOException) {
                throw RuntimeException("Failed to truncate file " + getFullName(), ex)
            }
        }
    }

    override fun close() {
        super.close()
        clean(0, segments.size)
        segments.clear()
        Helper.close(raFile)
    }

    override fun setInt(bytePos: Long, value: Int) {
        val bufferIndex = (bytePos shr segmentSizePower).toInt()
        val index = (bytePos and indexDivisor.toLong()).toInt()
        val b1 = segments[bufferIndex]!!
        if (index + 3 >= segmentSizeInBytes) {
            // seldom and special case if int has to be written into two separate segments
            val b2 = segments[bufferIndex + 1]!!
            if (index + 1 >= segmentSizeInBytes) {
                b2.putShort(1, (value ushr 16).toShort())
                b2.put(0, (value ushr 8).toByte())
                b1.put(index, value.toByte())
            } else if (index + 2 >= segmentSizeInBytes) {
                b2.putShort(0, (value ushr 16).toShort())
                b1.putShort(index, value.toShort())
            } else {
                // index + 3 >= segmentSizeInBytes
                b2.put(0, (value ushr 24).toByte())
                b1.putShort(index + 1, (value ushr 8).toShort())
                b1.put(index, value.toByte())
            }
        } else {
            b1.putInt(index, value)
        }
    }

    override fun getInt(bytePos: Long): Int {
        val bufferIndex = (bytePos shr segmentSizePower).toInt()
        val index = (bytePos and indexDivisor.toLong()).toInt()
        val b1 = segments[bufferIndex]!!
        if (index + 3 >= segmentSizeInBytes) {
            val b2 = segments[bufferIndex + 1]!!
            if (index + 1 >= segmentSizeInBytes)
                return ((b2.getShort(1).toInt() and 0xFFFF) shl 16) or ((b2.get(0).toInt() and 0xFF) shl 8) or (b1.get(index).toInt() and 0xFF)
            if (index + 2 >= segmentSizeInBytes)
                return ((b2.getShort(0).toInt() and 0xFFFF) shl 16) or (b1.getShort(index).toInt() and 0xFFFF)
            // index + 3 >= segmentSizeInBytes
            return ((b2.get(0).toInt() and 0xFF) shl 24) or ((b1.getShort(index + 1).toInt() and 0xFFFF) shl 8) or (b1.get(index).toInt() and 0xFF)
        }
        return b1.getInt(index)
    }

    override fun setShort(bytePos: Long, value: Short) {
        val bufferIndex = (bytePos ushr segmentSizePower).toInt()
        val index = (bytePos and indexDivisor.toLong()).toInt()
        val byteBuffer = segments[bufferIndex]!!
        if (index + 1 >= segmentSizeInBytes) {
            val byteBufferNext = segments[bufferIndex + 1]!!
            // seldom and special case if short has to be written into two separate segments
            byteBuffer.put(index, value.toByte())
            byteBufferNext.put(0, (value.toInt() ushr 8).toByte())
        } else {
            byteBuffer.putShort(index, value)
        }
    }

    override fun getShort(bytePos: Long): Short {
        val bufferIndex = (bytePos ushr segmentSizePower).toInt()
        val index = (bytePos and indexDivisor.toLong()).toInt()
        val byteBuffer = segments[bufferIndex]!!
        if (index + 1 >= segmentSizeInBytes) {
            val byteBufferNext = segments[bufferIndex + 1]!!
            return (((byteBufferNext.get(0).toInt() and 0xFF) shl 8) or (byteBuffer.get(index).toInt() and 0xFF)).toShort()
        }
        return byteBuffer.getShort(index)
    }

    override fun setBytes(bytePos: Long, values: ByteArray, length: Int) {
        assert(length <= segmentSizeInBytes) { "the length has to be smaller or equal to the segment size: $length vs. $segmentSizeInBytes" }
        val bufferIndex = (bytePos ushr segmentSizePower).toInt()
        val index = (bytePos and indexDivisor.toLong()).toInt()
        val delta = index + length - segmentSizeInBytes
        val bb1 = segments[bufferIndex]!!
        var len = length
        if (delta > 0) {
            len -= delta
            bb1.put(index, values, 0, len)
        } else {
            bb1.put(index, values, 0, len)
        }
        if (delta > 0) {
            val bb2 = segments[bufferIndex + 1]!!
            bb2.put(0, values, len, delta)
        }
    }

    override fun getBytes(bytePos: Long, values: ByteArray, length: Int) {
        assert(length <= segmentSizeInBytes) { "the length has to be smaller or equal to the segment size: $length vs. $segmentSizeInBytes" }
        val bufferIndex = (bytePos ushr segmentSizePower).toInt()
        val index = (bytePos and indexDivisor.toLong()).toInt()
        val delta = index + length - segmentSizeInBytes
        val bb1 = segments[bufferIndex]!!
        if (delta > 0) {
            val len = length - delta
            bb1.get(index, values, 0, len)

            val bb2 = segments[bufferIndex + 1]!!
            bb2.get(0, values, len, delta)
        } else {
            bb1.get(index, values, 0, length)
        }
    }

    override fun setByte(currentPointer: Long, value: Byte) {
        val bufferIndex = (currentPointer ushr segmentSizePower).toInt()
        val index = (currentPointer and indexDivisor.toLong()).toInt()
        val bb1 = segments[bufferIndex]!!
        bb1.put(index, value)
    }

    override fun getByte(currentPointer: Long): Byte {
        val bufferIndex = (currentPointer ushr segmentSizePower).toInt()
        val index = (currentPointer and indexDivisor.toLong()).toInt()
        val bb1 = segments[bufferIndex]!!
        return bb1.get(index)
    }

    override val capacity: Long
        get() = getSegments().toLong() * segmentSizeInBytes

    override fun getSegments(): Int = segments.size

    /**
     * Cleans up MappedByteBuffers. Be sure you bring the segments list in a consistent state
     * afterwards.
     * <p>
     *
     * @param from inclusive
     * @param to   exclusive
     */
    private fun clean(from: Int, to: Int) {
        for (i in from until to) {
            val bb = segments[i]!!
            cleanMappedByteBuffer(bb)
            segments[i] = null
        }
    }

    override val type: DAType
        get() = DAType.MMAP

    companion object {
        @JvmStatic
        fun cleanMappedByteBuffer(buffer: ByteBuffer) {
            // TODO avoid reflection on every call
            try {
                // >=JDK9 class sun.misc.Unsafe { void invokeCleaner(ByteBuffer buf) }
                val unsafeClass = Class.forName("sun.misc.Unsafe")
                // fetch the unsafe instance and bind it to the virtual MethodHandle
                val f = unsafeClass.getDeclaredField("theUnsafe")
                f.isAccessible = true
                val theUnsafe = f.get(null)
                val method = unsafeClass.getDeclaredMethod("invokeCleaner", ByteBuffer::class.java)
                try {
                    method.invoke(theUnsafe, buffer)
                } catch (t: Throwable) {
                    throw RuntimeException(t)
                }
            } catch (ex: Exception) {
                throw RuntimeException("Unable to unmap the mapped buffer", ex)
            }
        }
    }
}
