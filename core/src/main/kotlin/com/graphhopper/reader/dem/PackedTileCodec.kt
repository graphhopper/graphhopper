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
package com.graphhopper.reader.dem

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * This class stores a square tile (default 256x256) in a compressed block format (16x16).
 * It is not necessary to decompress before reading as only the type and base value is necessary to
 * read a pixel value. Currently only used for pmtiles but could be used for srtm or cgiar too.
 */
internal object PackedTileCodec {
    /**
     * Block type: every elevation sample in this block is 0.
     * Block payload size: 1 byte (type only).
     */
    const val TYPE_SEA = 0

    /**
     * Block type: every elevation sample in this block has the same int16 value.
     * Block payload size: 1 byte type + 2 bytes value.
     */
    const val TYPE_CONST = 1

    /**
     * Block type: int16 base value + unsigned byte delta per pixel.
     * For each sample: value = base + delta, delta in [0, 255].
     * Block payload size: 1 byte type + 2 bytes base + N bytes deltas.
     */
    const val TYPE_DELTA8 = 2

    /**
     * Block type: uncompressed int16 sample values (little-endian), row-major.
     * Block payload size: 1 byte type + N*2 bytes.
     */
    const val TYPE_RAW16 = 3

    const val DEFAULT_BLOCK_SIZE = 16

    private const val VERSION = 1

    // 1-byte header marker/version.
    private const val HEADER_BYTE = VERSION

    /**
     * Packed .tile format (little-endian):
     * <pre>
     * byte[0]       version
     * byte[1]       blockSize (currently 16)
     * u16[2..3]     tileSize (e.g. 256)
     * u32[]         (blockCount + 1) block offsets table, relative to payload start
     * bytes[]       block payloads concatenated
     * </pre>
     * The extra final offset allows computing each block length as offsets[i+1]-offsets[i].
     * blockCount is derived as (tileSize / blockSize)^2.
     */
    class PackedHeader(
        @get:JvmName("tileSize") val tileSize: Int,
        @get:JvmName("blockSize") val blockSize: Int,
        @get:JvmName("blocksPerAxis") val blocksPerAxis: Int,
        @get:JvmName("blockOffsets") val blockOffsets: IntArray,
        @get:JvmName("payloadOffset") val payloadOffset: Int
    )

    @JvmStatic
    fun isPackedTile(data: ByteBuffer): Boolean {
        return data.remaining() >= 1 && (data.get(0).toInt() and 0xFF) == HEADER_BYTE
    }

    @JvmStatic
    fun readPackedHeader(data: ByteBuffer): PackedHeader {
        val dup = data.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        if (!isPackedTile(dup))
            throw IllegalArgumentException("Not a packed GH elevation tile")

        val version = dup.get(0).toInt() and 0xFF
        if (version != VERSION)
            throw IllegalArgumentException("Unsupported packed tile version: $version, expected $VERSION")

        val blockSize = dup.get(1).toInt() and 0xFF
        val tileSize = dup.getShort(2).toInt() and 0xFFFF
        if (blockSize <= 0)
            throw IllegalArgumentException("Invalid block size: $blockSize")
        if (tileSize <= 0)
            throw IllegalArgumentException("Invalid tile size: $tileSize")
        if (tileSize % blockSize != 0)
            throw IllegalArgumentException("tileSize must be a multiple of blockSize, got tileSize="
                    + tileSize + ", blockSize=" + blockSize)
        val blocksPerAxis = tileSize / blockSize
        val blockCount = blocksPerAxis * blocksPerAxis
        val offsetTablePos = 4
        val blockOffsets = IntArray(blockCount + 1)
        for (i in blockOffsets.indices) {
            blockOffsets[i] = dup.getInt(offsetTablePos + i * 4)
        }
        val payloadOffset = offsetTablePos + blockOffsets.size * 4
        return PackedHeader(tileSize, blockSize, blocksPerAxis, blockOffsets, payloadOffset)
    }

    @JvmStatic
    fun encodePacked(rawLeShorts: ByteArray, tileSize: Int, blockSize: Int): ByteArray {
        if (rawLeShorts.size != tileSize * tileSize * 2)
            throw IllegalArgumentException("Raw tile size mismatch")
        if (tileSize % blockSize != 0)
            throw IllegalArgumentException("tileSize must be a multiple of blockSize, got tileSize="
                    + tileSize + ", blockSize=" + blockSize)

        val blocksPerAxis = tileSize / blockSize
        val blockCount = blocksPerAxis * blocksPerAxis

        val blockPayload = arrayOfNulls<ByteArray>(blockCount)
        val offsets = IntArray(blockCount + 1)

        var offset = 0
        var i = 0
        for (by in 0 until blocksPerAxis) {
            for (bx in 0 until blocksPerAxis) {
                val x0 = bx * blockSize
                val y0 = by * blockSize
                val block = encodeBlock(rawLeShorts, tileSize, x0, y0, blockSize, blockSize)
                blockPayload[i] = block
                offsets[i] = offset
                offset += block.size
                i++
            }
        }
        offsets[blockCount] = offset

        val headerLen = 4 + (blockCount + 1) * 4
        val header = ByteBuffer.allocate(headerLen).order(ByteOrder.LITTLE_ENDIAN)
        header.put(HEADER_BYTE.toByte())
        header.put(blockSize.toByte())
        header.putShort(tileSize.toShort())
        for (v in offsets) header.putInt(v)

        val out = ByteArrayOutputStream(headerLen + offset)
        out.write(header.array(), 0, header.array().size)
        for (block in blockPayload) out.write(block!!, 0, block.size)
        return out.toByteArray()
    }

    private fun encodeBlock(raw: ByteArray, tileSize: Int, x0: Int, y0: Int, bw: Int, bh: Int): ByteArray {
        val len = bw * bh
        val vals = ShortArray(len)

        var allZero = true
        var allSame = true
        var first: Short = 0
        var min = Short.MAX_VALUE
        var max = Short.MIN_VALUE

        var p = 0
        for (y in 0 until bh) {
            val row = y0 + y
            for (x in 0 until bw) {
                val col = x0 + x
                val v = readLeShort(raw, (row * tileSize + col) * 2)
                vals[p++] = v
                if (p == 1) first = v
                if (v.toInt() != 0) allZero = false
                if (v != first) allSame = false
                if (v < min) min = v
                if (v > max) max = v
            }
        }

        if (allZero) {
            return byteArrayOf(TYPE_SEA.toByte())
        }
        if (allSame) {
            val bb = ByteBuffer.allocate(3).order(ByteOrder.LITTLE_ENDIAN)
            bb.put(TYPE_CONST.toByte())
            bb.putShort(first)
            return bb.array()
        }

        // DELTA8 can only be used when all values can be represented as:
        // value = base + delta, with unsigned 8-bit delta in [0, 255].
        val range = max - min
        if (range <= 255) {
            val base = min.toInt()
            val bb = ByteBuffer.allocate(3 + len).order(ByteOrder.LITTLE_ENDIAN)
            bb.put(TYPE_DELTA8.toByte())
            bb.putShort(base.toShort())
            for (v in vals) {
                val d = v - base
                if (d < 0 || d > 255) {
                    return encodeRaw16(vals)
                }
                bb.put(d.toByte())
            }
            return bb.array()
        }

        return encodeRaw16(vals)
    }

    private fun encodeRaw16(vals: ShortArray): ByteArray {
        val bb = ByteBuffer.allocate(1 + vals.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        bb.put(TYPE_RAW16.toByte())
        for (v in vals) bb.putShort(v)
        return bb.array()
    }

    // Little Endian
    private fun readLeShort(data: ByteArray, offset: Int): Short {
        val lo = data[offset].toInt() and 0xFF
        val hi = data[offset + 1].toInt() and 0xFF
        return (lo or (hi shl 8)).toShort()
    }
}
