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

import com.graphhopper.storage.MMapDataAccess
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.util.ArrayDeque
import javax.imageio.ImageIO

/**
 * GraphHopper ElevationProvider that reads elevation data directly from a
 * PMTiles v3 archive containing terrain-RGB encoded tiles.
 *
 * If a directory of pre-decoded .tile files exists (created from previously runs),
 * each file is memory-mapped directly and there is no image decoding, no copying happening.
 * Otherwise tiles are decoded from PMTiles on first access and written as .tile
 * files so subsequent runs skip decoding.
 *
 * Not thread-safe.
 *
 * @param preferredZoom 10 means ~76m at equator and ~49m in Germany (default).
 *                      11 means ~38m at equator and ~25m in Germany.
 *                      12 means ~19m at equator and ~12m in Germany.
 * @param tileDir       directory for .tile tile cache files. Pre-populated by pmtiles_to_ele.py
 *                      or built lazily on first access. If null, decoded tiles are kept on heap only.
 */
open class PMTilesElevationProvider(
    pmFile: String,
    private val encoding: TerrainEncoding,
    private val interpolate: Boolean,
    private val preferredZoom: Int,
    tileDirString: String?
) : ElevationProvider {

    enum class TerrainEncoding { MAPBOX, TERRARIUM }

    private open class PackedTileData(
        private var data: ByteBuffer?,
        private val blockSize: Int,
        private val blocksPerAxis: Int,
        blockOffsets: IntArray,
        private val payloadOffset: Int
    ) {
        private var blockOffsets: IntArray?

        init {
            if (blockOffsets.size != blocksPerAxis * blocksPerAxis + 1)
                throw IllegalArgumentException("Invalid packed block table length")
            this.blockOffsets = blockOffsets
        }

        open fun get(x: Int, y: Int): Short {
            val blockX = x / blockSize
            val blockY = y / blockSize
            val blockIndex = blockY * blocksPerAxis + blockX
            val blockStart = payloadOffset + blockOffsets!![blockIndex]
            val localX = x - blockX * blockSize
            val localY = y - blockY * blockSize
            val idx = localY * blockSize + localX
            val data = this.data!!
            val type = data.get(blockStart).toInt() and 0xFF

            if (type == PackedTileCodec.TYPE_SEA) {
                return 0
            } else if (type == PackedTileCodec.TYPE_CONST) {
                return data.getShort(blockStart + 1)
            } else if (type == PackedTileCodec.TYPE_DELTA8) {
                val base = data.getShort(blockStart + 1)
                val delta = data.get(blockStart + 3 + idx).toInt() and 0xFF
                return (base + delta).toShort()
            } else if (type == PackedTileCodec.TYPE_RAW16) {
                return data.getShort(blockStart + 1 + idx * 2)
            }
            throw IllegalStateException("Unknown packed block type: $type")
        }

        fun release() {
            val data = this.data
            if (data != null && data.isDirect) // ensure it is not MISSING or SEA or heap allocated
                MMapDataAccess.cleanMappedByteBuffer(data)
            this.data = null
            blockOffsets = null
        }
    }

    private val reader = PMTilesReader()

    private var zoom = 0
    private var hilbertBase = 0L
    private var n = 0 // 1 << zoom

    // Cache of packed tiles, keyed by Hilbert tile ID. Missing (or all-sea) tiles use marker objects.
    // On-disk .tile files use the packed block format defined in PackedTileCodex.
    private val tileBuffers = HashMap<Long, PackedTileData>()

    // Last-tile cache: consecutive getEle() calls typically hit the same tile.
    private var lastTileId = -1L
    private var lastTileBuf: PackedTileData? = null

    private var tileSize = 0

    // Directory for .tile files. If non-null and writable, decoded tiles are persisted
    // there so subsequent runs can mmap them without re-decoding.
    private var tileDir: File? = null
    private val tileDirStr: String? = tileDirString

    private var clearTileFiles = true

    private val pmFileStr: String = pmFile

    override fun init(): ElevationProvider {
        try {
            reader.open(pmFileStr)
            reader.checkWebPSupport()
        } catch (e: IOException) {
            throw RuntimeException(e)
        }

        this.zoom = if (preferredZoom > 0) preferredZoom else Math.min(reader.header!!.maxZoom, 11)
        if (this.zoom < 1)
            throw IllegalArgumentException("Zoom level must be at least 1, got " + this.zoom)
        this.hilbertBase = PMTilesReader.hilbertBase(zoom)
        this.n = 1 shl zoom

        if (tileDirStr != null && !tileDirStr.isEmpty()) {
            val tileDir = File(tileDirStr)
            this.tileDir = tileDir
            tileDir.mkdirs()
        }
        return this
    }

    open fun setAutoRemoveTemporaryFiles(clearTileFiles: Boolean): PMTilesElevationProvider {
        this.clearTileFiles = clearTileFiles
        return this
    }

    override fun getEle(lat: Double, lon: Double): Double {
        try {
            return sampleElevation(lat, lon)
        } catch (e: Exception) {
            System.err.println("PMTilesElevationProvider.getEle(" + lat + ", " + lon + ") failed: " + e.message)
            return Double.NaN
        }
    }

    override fun canInterpolate(): Boolean = interpolate

    override fun release() {
        for (p in tileBuffers.values) {
            p.release()
        }
        tileBuffers.clear()
        lastTileId = -1
        lastTileBuf = null
        reader.close()
        val tileDir = this.tileDir
        if (clearTileFiles && tileDir != null) {
            val files = tileDir.listFiles { _, name -> name.endsWith(".tile") }
            if (files != null)
                for (f in files) f.delete()
        }
    }

    private fun zxyToTileId(x: Int, y: Int): Long {
        return hilbertBase + PMTilesReader.xyToHilbertD(zoom, x.toLong(), y.toLong())
    }

    @Throws(IOException::class)
    private fun sampleElevation(lat: Double, lon: Double): Double {
        val xTileD = (lon + 180.0) / 360.0 * n
        val latRad = Math.toRadians(lat)
        val yTileD = (1.0 - Math.log(Math.tan(latRad) + 1.0 / Math.cos(latRad)) / Math.PI) / 2.0 * n

        val tileX = Math.max(0, Math.min(n - 1, Math.floor(xTileD).toInt()))
        val tileY = Math.max(0, Math.min(n - 1, Math.floor(yTileD).toInt()))

        val tile = getTileBuffer(zxyToTileId(tileX, tileY), tileX, tileY)
        if (tile === MISSING_TILE) return Double.NaN
        if (tile === SEA_LEVEL_TILE) return 0.0

        val w = tileSize
        val h = tileSize
        val px = (xTileD - tileX) * (w - 1)
        val py = (yTileD - tileY) * (h - 1)

        if (interpolate) {
            val x0 = Math.max(0, Math.min(w - 2, Math.floor(px).toInt()))
            val y0 = Math.max(0, Math.min(h - 2, Math.floor(py).toInt()))
            val fx = px - x0
            val fy = py - y0
            val v00 = tile.get(x0, y0)
            val v10 = tile.get(x0 + 1, y0)
            val v01 = tile.get(x0, y0 + 1)
            val v11 = tile.get(x0 + 1, y0 + 1)
            if (v00 == Short.MIN_VALUE || v10 == Short.MIN_VALUE || v01 == Short.MIN_VALUE || v11 == Short.MIN_VALUE)
                return Double.NaN
            return v00 * (1 - fx) * (1 - fy) + v10 * fx * (1 - fy) +
                    v01 * (1 - fx) * fy + v11 * fx * fy
        } else {
            val ix = Math.max(0, Math.min(w - 1, Math.round(px).toInt()))
            val iy = Math.max(0, Math.min(h - 1, Math.round(py).toInt()))
            val value = tile.get(ix, iy)
            if (value == Short.MIN_VALUE) return Double.NaN
            return value.toDouble()
        }
    }

    @Throws(IOException::class)
    private fun getTileBuffer(tileId: Long, tileX: Int, tileY: Int): PackedTileData {
        if (tileId == lastTileId) return lastTileBuf!!

        val existing = tileBuffers[tileId]
        if (existing != null) {
            lastTileId = tileId
            lastTileBuf = existing
            return existing
        }

        // Try pre-decoded .tile file first
        var buf = tryMmapTileFile(tileId)
        if (buf == null) {
            // Decode from PMTiles
            val raw = reader.getTileBytes(tileId)
            if (raw == null) {
                buf = MISSING_TILE
            } else {
                val elevBytes = decodeTerrain(raw)
                if (elevBytes == null) {
                    buf = MISSING_TILE
                } else if (elevBytes.isEmpty()) {
                    buf = SEA_LEVEL_TILE
                } else {
                    fillGaps(elevBytes, tileSize, tileX, tileY, n)
                    buf = persistAndLoad(tileId, elevBytes)
                }
            }
        }

        tileBuffers[tileId] = buf
        lastTileId = tileId
        lastTileBuf = buf
        return buf
    }

    /**
     * Try to mmap an existing .tile file. Returns tile data if the file exists,
     * or null if not found (either no tileDir or file not yet decoded).
     */
    @Throws(IOException::class)
    private fun tryMmapTileFile(tileId: Long): PackedTileData? {
        if (tileDir == null) return null
        val f = tileFile(tileId)
        if (!f.exists()) return null
        return loadTileData(f)
    }

    /**
     * Write decoded bytes to a packed .tile file and load it, or keep packed bytes on heap if no tileDir.
     */
    @Throws(IOException::class)
    private fun persistAndLoad(tileId: Long, elevBytes: ByteArray): PackedTileData {
        val packed = PackedTileCodec.encodePacked(elevBytes, tileSize, PackedTileCodec.DEFAULT_BLOCK_SIZE)
        if (tileDir != null) {
            val f = tileFile(tileId)
            Files.write(f.toPath(), packed)
            return loadTileData(f)
        }
        // ByteBuffer in heap
        val buf = ByteBuffer.wrap(packed).order(ByteOrder.LITTLE_ENDIAN)
        return toPackedTileData(buf)
    }

    private fun tileFile(tileId: Long): File {
        return File(tileDir, tileId.toString() + "_" + zoom + ".tile")
    }

    @Throws(IOException::class)
    private fun loadTileData(f: File): PackedTileData {
        FileChannel.open(f.toPath(), StandardOpenOption.READ).use { ch ->
            val buf = ch.map(FileChannel.MapMode.READ_ONLY, 0, f.length())
            buf.order(ByteOrder.LITTLE_ENDIAN)
            if (!PackedTileCodec.isPackedTile(buf)) {
                throw IOException("Unsupported legacy raw .tile format in " + f
                        + ". Remove cached .tile files so they can be regenerated as packed tiles.")
            }
            return toPackedTileData(buf)
        }
    }

    private fun toPackedTileData(buf: ByteBuffer): PackedTileData {
        val h = PackedTileCodec.readPackedHeader(buf)
        if (tileSize == 0) tileSize = h.tileSize // tileSize is set when tile comes from cache
        else if (tileSize != h.tileSize)
            throw IllegalStateException("Inconsistent packed tile size: expected " + tileSize + " but got " + h.tileSize)
        if (tileSize < PackedTileCodec.DEFAULT_BLOCK_SIZE)
            throw IllegalStateException("tileSize must be at least " + PackedTileCodec.DEFAULT_BLOCK_SIZE + ", got " + tileSize)
        return PackedTileData(buf, h.blockSize, h.blocksPerAxis, h.blockOffsets, h.payloadOffset)
    }

    /**
     * Decodes terrain-RGB image bytes into a little-endian byte[] of short elevation values.
     *
     * @return byte[] with LE-encoded shorts, empty byte[] if all elevations are exactly 0 (sea level), or null on decode failure.
     */
    @Throws(IOException::class)
    internal fun decodeTerrain(imageBytes: ByteArray): ByteArray? {
        val img = ImageIO.read(ByteArrayInputStream(imageBytes))
        if (img == null) {
            // Check if it's a WebP file (RIFF....WEBP magic)
            if (imageBytes.size > 12 && imageBytes[0] == 'R'.code.toByte() && imageBytes[1] == 'I'.code.toByte()
                    && imageBytes[2] == 'F'.code.toByte() && imageBytes[3] == 'F'.code.toByte()
                    && imageBytes[8] == 'W'.code.toByte() && imageBytes[9] == 'E'.code.toByte()
                    && imageBytes[10] == 'B'.code.toByte() && imageBytes[11] == 'P'.code.toByte()) {
                throw IOException(
                        "Tile is WebP format but no WebP ImageIO plugin found. " +
                                "Add com.github.usefulness:webp-imageio to your classpath.")
            }
            return null
        }

        val w = img.width
        val h = img.height
        if (w != h)
            throw IOException("Unsupported non-square elevation tile: " + w + "x" + h + ". Expected square terrain tiles.")
        if (tileSize == 0) tileSize = w // tileSize set on first decode
        else if (tileSize != w)
            throw IOException("Inconsistent terrain tile size: expected $tileSize but got $w")
        if (tileSize % PackedTileCodec.DEFAULT_BLOCK_SIZE != 0)
            throw IOException("tileSize must be a multiple of blockSize: tileSize=" + tileSize
                    + ", blockSize=" + PackedTileCodec.DEFAULT_BLOCK_SIZE)

        val elev = ByteArray(h * w * 2)
        var allSeaLevel = true
        for (y in 0 until h) {
            for (x in 0 until w) {
                val rgb = img.getRGB(x, y)
                val r = (rgb shr 16) and 0xFF
                val g = (rgb shr 8) and 0xFF
                val b = rgb and 0xFF

                val e: Double
                if (encoding == TerrainEncoding.MAPBOX) {
                    e = -10000.0 + (r * 65536 + g * 256 + b) * 0.1
                } else {
                    e = (r * 256.0 + g + b / 256.0) - 32768.0
                }
                // Mapbox uses rgb(0,0,0) = -10000 and Terrarium rgb(0,0,0) = -32768 for
                // no-data/ocean. No real place is below -1000m, so treat as no-data marker.
                val s: Short = if (e < -1000) Short.MIN_VALUE
                else Math.max(-32768L, Math.min(32767L, Math.round(e))).toShort()
                if (s.toInt() != 0) allSeaLevel = false

                // little-endian, matching ByteBuffer.LITTLE_ENDIAN order
                val idx = (y * w + x) * 2
                elev[idx] = (s.toInt() and 0xFF).toByte()
                elev[idx + 1] = ((s.toInt() shr 8) and 0xFF).toByte()
            }
        }
        return if (allSeaLevel) ByteArray(0) else elev
    }

    companion object {
        private val MISSING_TILE: PackedTileData = object : PackedTileData(null, 1, 1, intArrayOf(0, 0), 0) {
            override fun get(x: Int, y: Int): Short = Short.MIN_VALUE
        }
        private val SEA_LEVEL_TILE: PackedTileData = object : PackedTileData(null, 1, 1, intArrayOf(0, 0), 0) {
            override fun get(x: Int, y: Int): Short = 0
        }

        /**
         * BFS wavefront fill: replaces Short.MIN_VALUE gap pixels with the average of their
         * valid 4-connected neighbors, propagating inward. Only gap pixels reachable from valid
         * data are filled; isolated gaps remain as Short.MIN_VALUE.
         * See [discussion](ttps://github.com/mapterhorn/mapterhorn/discussions/217)
         */
        @JvmStatic
        @JvmName("fillGaps")
        internal fun fillGaps(data: ByteArray, w: Int, tileX: Int, tileY: Int, n: Int) {
            val shorts = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
            val total = shorts.capacity()
            val h = total / w
            val DX = intArrayOf(-1, 1, 0, 0)
            val DY = intArrayOf(0, 0, -1, 1)

            // Log one line per connected gap area with its lat/lon centroid
            val visited = BooleanArray(total)
            for (i in 0 until total) {
                if (shorts.get(i) != Short.MIN_VALUE || visited[i]) continue
                val comp = ArrayDeque<Int>()
                comp.add(i)
                visited[i] = true
                var count = 0
                var sumPx = 0L
                var sumPy = 0L
                while (!comp.isEmpty()) {
                    val ci = comp.poll()
                    count++
                    sumPx += (ci % w).toLong()
                    sumPy += (ci / w).toLong()
                    val cx = ci % w
                    val cy = ci / w
                    for (d in 0 until 4) {
                        val nx = cx + DX[d]
                        val ny = cy + DY[d]
                        if (nx >= 0 && nx < w && ny >= 0 && ny < h) {
                            val ni = ny * w + nx
                            if (shorts.get(ni) == Short.MIN_VALUE && !visited[ni]) {
                                visited[ni] = true
                                comp.add(ni)
                            }
                        }
                    }
                }
                val cx = sumPx.toDouble() / count
                val cy = sumPy.toDouble() / count
                val lon = ((tileX + cx / w) / n) * 360.0 - 180.0
                val yNorm = (tileY + cy / h) / n
                val lat = Math.toDegrees(Math.atan(Math.sinh(Math.PI * (1 - 2 * yNorm))))
                LoggerFactory.getLogger(PMTilesElevationProvider::class.java)
                        .warn("fillGaps: {} pixels at lat={}, lon={}", count,
                                String.format("%.5f", lat), String.format("%.5f", lon))
            }

            // Seed: gap pixels bordering valid data
            val queued = BooleanArray(total)
            val queue = ArrayDeque<Int>()
            for (i in 0 until total) {
                if (shorts.get(i) != Short.MIN_VALUE) continue
                val x = i % w
                val y = i / w
                for (d in 0 until 4) {
                    val nx = x + DX[d]
                    val ny = y + DY[d]
                    if (nx >= 0 && nx < w && ny >= 0 && ny < h
                            && shorts.get(ny * w + nx) != Short.MIN_VALUE) {
                        queue.add(i)
                        queued[i] = true
                        break
                    }
                }
            }

            // BFS: fill each gap pixel with average of valid neighbors
            while (!queue.isEmpty()) {
                val i = queue.poll()
                if (shorts.get(i) != Short.MIN_VALUE) continue
                val x = i % w
                val y = i / w
                var sum = 0
                var cnt = 0
                for (d in 0 until 4) {
                    val nx = x + DX[d]
                    val ny = y + DY[d]
                    if (nx >= 0 && nx < w && ny >= 0 && ny < h) {
                        val v = shorts.get(ny * w + nx)
                        if (v != Short.MIN_VALUE) {
                            sum += v
                            cnt++
                        }
                    }
                }
                if (cnt == 0) continue
                shorts.put(i, Math.round(sum.toDouble() / cnt).toShort())
                for (d in 0 until 4) {
                    val nx = x + DX[d]
                    val ny = y + DY[d]
                    if (nx >= 0 && nx < w && ny >= 0 && ny < h) {
                        val ni = ny * w + nx
                        if (shorts.get(ni) == Short.MIN_VALUE && !queued[ni]) {
                            queue.add(ni)
                            queued[ni] = true
                        }
                    }
                }
            }
        }
    }
}
