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

import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import javax.imageio.ImageIO

/**
 * Diagnostic tool: extracts tiles from a PMTiles v3 file and saves each
 * as a grayscale elevation PNG (black=low, white=high).
 *
 * Usage: `java PMTilesExtract de.pmtiles ./tiles_out [zoom]`
 */
internal object PMTilesExtract {

    @JvmStatic
    @Throws(Exception::class)
    fun main(args: Array<String>) {
        if (args.size < 2) {
            println("Usage: PMTilesExtract <file.pmtiles> <output_dir> [zoom]")
            println("  If zoom is omitted, extracts one tile per zoom level at the center.")
            println("  If zoom is given, extracts ALL tiles at that zoom level.")
            return
        }

        val pmtilesPath = args[0]
        val outDir = File(args[1])
        outDir.mkdirs()
        val requestedZoom = if (args.size >= 3) Integer.parseInt(args[2]) else -1

        val reader = PMTilesReader()
        reader.open(pmtilesPath)
        reader.checkWebPSupport()
        val h = reader.header!!

        val typeNames = arrayOf("unknown", "mvt", "png", "jpeg", "webp", "avif")
        println("Tile type: " + typeNames[Math.min(h.tileType, typeNames.size - 1)])
        println("Zoom: " + h.minZoom + " - " + h.maxZoom)
        System.out.printf("Bounds: lon=[%.4f, %.4f] lat=[%.4f, %.4f]%n",
                h.minLonE7 / 1e7, h.maxLonE7 / 1e7, h.minLatE7 / 1e7, h.maxLatE7 / 1e7)
        println("Tiles: " + h.numAddressedTiles + " addressed, " + h.numTileEntries + " entries")
        println("Root directory: " + reader.rootDir!!.size + " entries")

        if (requestedZoom >= 0) {
            println("\nExtracting all tiles at zoom $requestedZoom...")
            val count = extractAllAtZoom(reader, requestedZoom, outDir)
            println("Extracted $count tiles to $outDir")
        } else {
            println("\nExtracting center tile at each zoom level...")
            val centerLon = (h.minLonE7 + h.maxLonE7) / 2.0 / 1e7
            val centerLat = (h.minLatE7 + h.maxLatE7) / 2.0 / 1e7
            extractCenterTiles(reader, centerLat, centerLon, h.minZoom, h.maxZoom, outDir)
        }

        reader.close()
    }

    @Throws(IOException::class)
    private fun extractCenterTiles(reader: PMTilesReader, centerLat: Double, centerLon: Double,
                                   minZoom: Int, maxZoom: Int, outDir: File) {
        for (z in minZoom..maxZoom) {
            val n = 1 shl z
            var tx = ((centerLon + 180.0) / 360.0 * n).toInt()
            val latRad = Math.toRadians(centerLat)
            var ty = ((1.0 - Math.log(Math.tan(latRad) + 1.0 / Math.cos(latRad)) / Math.PI) / 2.0 * n).toInt()
            tx = Math.max(0, Math.min(n - 1, tx))
            ty = Math.max(0, Math.min(n - 1, ty))

            val tileId = PMTilesReader.zxyToTileId(z, tx, ty)
            val data = reader.getTileBytes(tileId)

            if (data == null) {
                System.out.printf("  z=%2d x=%5d y=%5d tileId=%10d -> NOT FOUND%n", z, tx, ty, tileId)
                continue
            }

            val img = decodeImage(data)
            val gray = terrainToGrayscale(img)
            val outFile = File(outDir, String.format("z%d_x%d_y%d.png", z, tx, ty))
            ImageIO.write(gray, "png", outFile)
            System.out.printf("  z=%2d x=%5d y=%5d tileId=%10d -> %s (%dx%d, %d bytes raw)%n",
                    z, tx, ty, tileId, outFile.name, img.width, img.height, data.size)
        }
    }

    @Throws(IOException::class)
    private fun extractAllAtZoom(reader: PMTilesReader, zoom: Int, outDir: File): Int {
        val base = PMTilesReader.hilbertBase(zoom)
        val count = 1L shl (2 * zoom)
        val endId = base + count
        System.out.printf("  TileId range for z=%d: [%d, %d) (%d tiles)%n", zoom, base, endId, count)

        var extracted = 0
        var tileId = base
        while (tileId < endId) {
            val zxy = PMTilesReader.tileIdToZxy(tileId)
            val data = reader.getTileBytes(tileId)
            if (data == null) {
                tileId++
                continue
            }

            val img = decodeImage(data)
            val gray = terrainToGrayscale(img)
            val outFile = File(outDir, String.format("z%d_x%d_y%d.png", zxy[0], zxy[1], zxy[2]))
            ImageIO.write(gray, "png", outFile)
            extracted++
            if (extracted % 100 == 0) println("  ... $extracted tiles extracted")
            tileId++
        }
        return extracted
    }

    // =========================================================================
    // Image decode + terrain-RGB to grayscale
    // =========================================================================

    @Throws(IOException::class)
    private fun decodeImage(data: ByteArray): BufferedImage {
        val img = ImageIO.read(ByteArrayInputStream(data))
        if (img != null) return img

        var fmt = "unknown"
        if (data.size > 12 && data[0] == 'R'.code.toByte() && data[1] == 'I'.code.toByte()
                && data[2] == 'F'.code.toByte() && data[3] == 'F'.code.toByte()
                && data[8] == 'W'.code.toByte() && data[9] == 'E'.code.toByte()
                && data[10] == 'B'.code.toByte() && data[11] == 'P'.code.toByte()) fmt = "WebP"
        else if (data.size > 4 && data[0] == 0x89.toByte() && data[1] == 'P'.code.toByte()) fmt = "PNG"

        throw IOException(fmt + " tile but ImageIO can't decode it (" + data.size + " bytes). "
                + "Add to pom.xml: com.github.usefulness:webp-imageio")
    }

    /** Decode Terrarium-encoded terrain-RGB to 16-bit grayscale. Black=low, white=high. */
    private fun terrainToGrayscale(img: BufferedImage): BufferedImage {
        val w = img.width
        val h = img.height

        // Pass 1: decode elevations, find min/max
        val elev = Array(h) { FloatArray(w) }
        var min = Float.MAX_VALUE
        var max = -Float.MAX_VALUE
        for (y in 0 until h) {
            for (x in 0 until w) {
                val rgb = img.getRGB(x, y)
                val r = (rgb shr 16) and 0xFF
                val g = (rgb shr 8) and 0xFF
                val b = rgb and 0xFF
                val e = ((r * 256.0 + g + b / 256.0) - 32768.0).toFloat()
                elev[y][x] = e
                if (e < min) min = e
                if (e > max) max = e
            }
        }
        System.out.printf("         elevation: min=%.1fm  max=%.1fm%n", min, max)

        // Pass 2: map to 16-bit grayscale
        var range = max - min
        if (range < 1) range = 1f
        val out = BufferedImage(w, h, BufferedImage.TYPE_USHORT_GRAY)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val v = Math.max(0, Math.min(65535, ((elev[y][x] - min) / range * 65535).toInt()))
                out.raster.setSample(x, y, 0, v)
            }
        }
        return out
    }
}
