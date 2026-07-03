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

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.Collections
import java.util.zip.GZIPInputStream
import javax.imageio.ImageIO

/**
 * Low-level PMTiles v3 archive reader. Handles header parsing, directory
 * deserialization, Hilbert curve tile ID mapping, and raw tile byte retrieval.
 */
internal class PMTilesReader : Closeable {

    private var raf: RandomAccessFile? = null
    private var channel: FileChannel? = null

    @JvmField
    var header: Header? = null

    @JvmField
    var rootDir: List<DirEntry>? = null

    private val leafCache = object : LinkedHashMap<Long, List<DirEntry>>(LEAF_CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<Long, List<DirEntry>>): Boolean {
            return size > LEAF_CACHE_SIZE
        }
    }

    @Throws(IOException::class)
    fun open(filePath: String) {
        if (header != null) return
        ImageIO.scanForPlugins()
        val raf = RandomAccessFile(filePath, "r")
        this.raf = raf
        channel = raf.channel
        val header = readHeader()
        this.header = header
        if (header.tileCompression > 1)
            throw IOException("PMTiles tile compression not supported for elevation data, got compression=" + header.tileCompression)
        if (header.internalCompression != 0 && header.internalCompression != 1 && header.internalCompression != COMPRESS_GZIP)
            throw IOException("PMTiles internal compression not supported, got compression=" + header.internalCompression
                    + ". Only none (1) and gzip (2) are supported.")
        rootDir = readDirectory(header.rootDirOffset, header.rootDirLength)
    }

    override fun close() {
        rootDir = null
        header = null
        leafCache.clear()
        try {
            channel?.close()
            raf?.close()
        } catch (ignored: IOException) {
        }
    }

    @Throws(IOException::class)
    fun checkWebPSupport() {
        if (header!!.tileType == 4) {
            var hasWebP = false
            for (f in ImageIO.getReaderFormatNames())
                if (f.equals("webp", ignoreCase = true)) {
                    hasWebP = true
                    break
                }
            if (!hasWebP) throw IOException(
                    "PMTiles contains WebP tiles but no WebP ImageIO plugin found. " +
                            "Add com.github.usefulness:webp-imageio to your classpath.")
        }
    }

    @Throws(IOException::class)
    fun getTileBytes(tileId: Long): ByteArray? {
        return findTile(tileId, rootDir, 0)
    }

    @Throws(IOException::class)
    private fun findTile(tileId: Long, dir: List<DirEntry>?, depth: Int): ByteArray? {
        if (dir == null || dir.isEmpty() || depth > 5) return null

        // Find the last entry where entry.tileId <= tileId
        var lo = 0
        var hi = dir.size - 1
        var idx = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (dir[mid].tileId <= tileId) {
                idx = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        if (idx < 0) return null

        val e = dir[idx]
        if (e.runLength > 0) {
            if (tileId < e.tileId + e.runLength) {
                return readBytes(header!!.tileDataOffset + e.offset, e.length.toInt())
            }
            return null
        } else {
            val leafDir = readLeafDirectory(e.offset, e.length)
            return findTile(tileId, leafDir, depth + 1)
        }
    }

    // =========================================================================
    // Header parsing
    // =========================================================================

    @Throws(IOException::class)
    private fun readHeader(): Header {
        val buf = readBytes(0, HEADER_LEN)
        val bb = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN)

        val magic = ByteArray(7)
        bb.get(magic)
        if (!magic.contentEquals("PMTiles".toByteArray()))
            throw IOException("Not a PMTiles file")

        val h = Header()
        h.version = bb.get().toInt() and 0xFF
        if (h.version != 3)
            throw IOException("Only PMTiles v3 supported, got v" + h.version)

        h.rootDirOffset = bb.getLong()
        h.rootDirLength = bb.getLong()
        h.metadataOffset = bb.getLong()
        h.metadataLength = bb.getLong()
        h.leafDirsOffset = bb.getLong()
        h.leafDirsLength = bb.getLong()
        h.tileDataOffset = bb.getLong()
        h.tileDataLength = bb.getLong()
        h.numAddressedTiles = bb.getLong()
        h.numTileEntries = bb.getLong()
        h.numTileContents = bb.getLong()

        h.clustered = (bb.get().toInt() and 0xFF) == 1
        h.internalCompression = bb.get().toInt() and 0xFF
        h.tileCompression = bb.get().toInt() and 0xFF
        h.tileType = bb.get().toInt() and 0xFF
        h.minZoom = bb.get().toInt() and 0xFF
        h.maxZoom = bb.get().toInt() and 0xFF
        h.minLonE7 = bb.getInt()
        h.minLatE7 = bb.getInt()
        h.maxLonE7 = bb.getInt()
        h.maxLatE7 = bb.getInt()
        h.centerZoom = bb.get().toInt() and 0xFF
        h.centerLonE7 = bb.getInt()
        h.centerLatE7 = bb.getInt()
        return h
    }

    // =========================================================================
    // Directory parsing
    // =========================================================================

    @Throws(IOException::class)
    private fun readLeafDirectory(offset: Long, length: Long): List<DirEntry> {
        val cached = leafCache[offset]
        if (cached != null) return cached
        val entries = readDirectory(header!!.leafDirsOffset + offset, length)
        leafCache[offset] = entries
        return entries
    }

    @Throws(IOException::class)
    private fun readDirectory(offset: Long, length: Long): List<DirEntry> {
        var raw = readBytes(offset, length.toInt())
        if (header!!.internalCompression == COMPRESS_GZIP) {
            raw = gunzip(raw)
        }
        return deserializeEntries(raw)
    }

    // =========================================================================
    // I/O helpers
    // =========================================================================

    @Throws(IOException::class)
    private fun readBytes(offset: Long, length: Int): ByteArray {
        val buf = ByteBuffer.allocate(length)
        var read = 0
        while (read < length) {
            val n = channel!!.read(buf, offset + read)
            if (n < 0) break
            read += n
        }
        if (read < length)
            throw IOException("Short read at offset $offset: expected $length bytes but got $read")
        return buf.array()
    }

    // =========================================================================
    // Internal types
    // =========================================================================

    class Header {
        var version = 0
        var rootDirOffset = 0L
        var rootDirLength = 0L
        var metadataOffset = 0L
        var metadataLength = 0L
        var leafDirsOffset = 0L
        var leafDirsLength = 0L
        var tileDataOffset = 0L
        var tileDataLength = 0L
        var numAddressedTiles = 0L
        var numTileEntries = 0L
        var numTileContents = 0L
        var clustered = false
        var internalCompression = 0
        var tileCompression = 0
        var tileType = 0
        var minZoom = 0
        var maxZoom = 0
        var minLonE7 = 0
        var minLatE7 = 0
        var maxLonE7 = 0
        var maxLatE7 = 0
        var centerZoom = 0
        var centerLonE7 = 0
        var centerLatE7 = 0
    }

    class DirEntry(
        @JvmField val tileId: Long,
        @JvmField val runLength: Long,
        @JvmField val offset: Long,
        @JvmField val length: Long
    )

    companion object {
        const val HEADER_LEN = 127
        const val COMPRESS_GZIP = 2

        private const val LEAF_CACHE_SIZE = 1024 * 8 // here larger counts do not increase memory usage much

        // =========================================================================
        // Hilbert curve: Z/X/Y <-> TileID
        // =========================================================================

        @JvmStatic
        fun hilbertBase(z: Int): Long {
            // this is the closed form of:
            // for (int i = 0; i < z; i++) base += (1L << (2 * i));
            return ((1L shl (2 * z)) - 1) / 3
        }

        @JvmStatic
        fun zxyToTileId(z: Int, x: Int, y: Int): Long {
            if (z == 0) return 0
            return hilbertBase(z) + xyToHilbertD(z, x.toLong(), y.toLong())
        }

        @JvmStatic
        fun tileIdToZxy(tileId: Long): IntArray {
            if (tileId == 0L) return intArrayOf(0, 0, 0)
            var acc = 0L
            var z = 0
            while (true) {
                val numTiles = 1L shl (2 * z)
                if (acc + numTiles > tileId) {
                    val xy = hilbertDToXY(z, tileId - acc)
                    return intArrayOf(z, xy[0].toInt(), xy[1].toInt())
                }
                acc += numTiles
                z++
            }
        }

        @JvmStatic
        fun xyToHilbertD(order: Int, x: Long, y: Long): Long {
            @Suppress("NAME_SHADOWING") var x = x
            @Suppress("NAME_SHADOWING") var y = y
            var d = 0L
            var s = 1 shl (order - 1)
            while (s > 0) {
                val rx: Long = if ((x and s.toLong()) > 0) 1 else 0
                val ry: Long = if ((y and s.toLong()) > 0) 1 else 0
                d += s * s.toLong() * ((3 * rx) xor ry)
                if (ry == 0L) {
                    if (rx == 1L) {
                        x = s - 1 - x
                        y = s - 1 - y
                    }
                    val t = x
                    x = y
                    y = t
                }
                s = s shr 1
            }
            return d
        }

        private fun hilbertDToXY(order: Int, d: Long): LongArray {
            @Suppress("NAME_SHADOWING") var d = d
            var x = 0L
            var y = 0L
            var s = 1L
            while (s < (1L shl order)) {
                val rx = (d / 2) and 1
                val ry = (d xor rx) and 1
                if (ry == 0L) {
                    if (rx == 1L) {
                        x = s - 1 - x
                        y = s - 1 - y
                    }
                    val t = x
                    x = y
                    y = t
                }
                x += s * rx
                y += s * ry
                d = d shr 2
                s = s shl 1
            }
            return longArrayOf(x, y)
        }

        private fun deserializeEntries(data: ByteArray): List<DirEntry> {
            val pos = intArrayOf(0)
            val numEntries = readVarint(data, pos).toInt()
            if (numEntries == 0) return Collections.emptyList()

            val tileIds = LongArray(numEntries)
            var lastId = 0L
            for (i in 0 until numEntries) {
                lastId += readVarint(data, pos)
                tileIds[i] = lastId
            }

            val runLengths = LongArray(numEntries)
            for (i in 0 until numEntries) runLengths[i] = readVarint(data, pos)

            val lengths = LongArray(numEntries)
            for (i in 0 until numEntries) lengths[i] = readVarint(data, pos)

            // offsets are stored as offset + 1 to make the offset==0 is available and means "continue from previous entry"
            val offsets = LongArray(numEntries)
            for (i in 0 until numEntries) {
                val v = readVarint(data, pos)
                if (v == 0L && i > 0) offsets[i] = offsets[i - 1] + lengths[i - 1]
                else if (v < 1) // v==0 only valid if there is a previous entry
                    throw IllegalStateException("Invalid directory entry offset at index $i: varint value $v")
                else
                    offsets[i] = v - 1
            }

            val entries = ArrayList<DirEntry>(numEntries)
            for (i in 0 until numEntries)
                entries.add(DirEntry(tileIds[i], runLengths[i], offsets[i], lengths[i]))
            return entries
        }

        @JvmStatic
        @Throws(IOException::class)
        fun gunzip(data: ByteArray): ByteArray {
            GZIPInputStream(ByteArrayInputStream(data)).use { gis ->
                ByteArrayOutputStream(data.size * 4).use { bos ->
                    val buf = ByteArray(4096)
                    var n: Int
                    while (gis.read(buf).also { n = it } >= 0) bos.write(buf, 0, n)
                    return bos.toByteArray()
                }
            }
        }

        private fun readVarint(data: ByteArray, pos: IntArray): Long {
            var result = 0L
            var shift = 0
            while (pos[0] < data.size) {
                val b = data[pos[0]++].toInt() and 0xFF
                result = result or ((b and 0x7F).toLong() shl shift)
                if ((b and 0x80) == 0) break
                shift += 7
            }
            return result
        }
    }
}
