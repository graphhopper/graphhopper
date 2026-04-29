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
package com.graphhopper.reader.dem;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * This class stores a square tile (default 256x256) in a compressed block format (16x16).
 * It is not necessary to decompress before reading as only the type and base value is necessary to
 * read a pixel value. Currently only used for pmtiles but could be used for srtm or cgiar too.
 */
final class PackedTileCodec {
    /**
     * Block type: every elevation sample in this block is 0.
     * Block payload size: 1 byte (type only).
     */
    static final int TYPE_SEA = 0;
    /**
     * Block type: every elevation sample in this block has the same int16 value.
     * Block payload size: 1 byte type + 2 bytes value.
     */
    static final int TYPE_CONST = 1;
    /**
     * Block type: int16 base value + unsigned byte delta per pixel.
     * For each sample: value = base + delta, delta in [0, 255].
     * Block payload size: 1 byte type + 2 bytes base + N bytes deltas.
     */
    static final int TYPE_DELTA8 = 2;
    /**
     * Block type: uncompressed int16 sample values (little-endian), row-major.
     * Block payload size: 1 byte type + N*2 bytes.
     */
    static final int TYPE_RAW16 = 3;

    static final int DEFAULT_BLOCK_SIZE = 16;

    private static final int VERSION = 1;
    // 1-byte header marker/version.
    private static final int HEADER_BYTE = VERSION;

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
    record PackedHeader(int tileSize, int blockSize, int blocksPerAxis, int[] blockOffsets,
                        int payloadOffset) {
    }

    private PackedTileCodec() {
    }

    static boolean isPackedTile(ByteBuffer data) {
        return data.remaining() >= 1 && (data.get(0) & 0xFF) == HEADER_BYTE;
    }

    static PackedHeader readPackedHeader(ByteBuffer data) {
        ByteBuffer dup = data.duplicate().order(ByteOrder.LITTLE_ENDIAN);
        if (!isPackedTile(dup))
            throw new IllegalArgumentException("Not a packed GH elevation tile");

        int version = dup.get(0) & 0xFF;
        if (version != VERSION)
            throw new IllegalArgumentException("Unsupported packed tile version: " + version + ", expected " + VERSION);

        int blockSize = dup.get(1) & 0xFF;
        int tileSize = dup.getShort(2) & 0xFFFF;
        if (blockSize <= 0)
            throw new IllegalArgumentException("Invalid block size: " + blockSize);
        if (tileSize <= 0)
            throw new IllegalArgumentException("Invalid tile size: " + tileSize);
        if (tileSize % blockSize != 0)
            throw new IllegalArgumentException("tileSize must be a multiple of blockSize, got tileSize="
                    + tileSize + ", blockSize=" + blockSize);
        int blocksPerAxis = tileSize / blockSize;
        int blockCount = blocksPerAxis * blocksPerAxis;
        int offsetTablePos = 4;
        int[] blockOffsets = new int[blockCount + 1];
        for (int i = 0; i < blockOffsets.length; i++) {
            blockOffsets[i] = dup.getInt(offsetTablePos + i * 4);
        }
        int payloadOffset = offsetTablePos + blockOffsets.length * 4;
        return new PackedHeader(tileSize, blockSize, blocksPerAxis, blockOffsets, payloadOffset);
    }

    static byte[] encodePacked(byte[] rawLeShorts, int tileSize, int blockSize) {
        if (rawLeShorts.length != tileSize * tileSize * 2)
            throw new IllegalArgumentException("Raw tile size mismatch");
        if (tileSize % blockSize != 0)
            throw new IllegalArgumentException("tileSize must be a multiple of blockSize, got tileSize="
                    + tileSize + ", blockSize=" + blockSize);

        int blocksPerAxis = tileSize / blockSize;
        int blockCount = blocksPerAxis * blocksPerAxis;

        byte[][] blockPayload = new byte[blockCount][];
        int[] offsets = new int[blockCount + 1];

        int offset = 0;
        int i = 0;
        for (int by = 0; by < blocksPerAxis; by++) {
            for (int bx = 0; bx < blocksPerAxis; bx++) {
                int x0 = bx * blockSize;
                int y0 = by * blockSize;
                byte[] block = encodeBlock(rawLeShorts, tileSize, x0, y0, blockSize, blockSize);
                blockPayload[i] = block;
                offsets[i] = offset;
                offset += block.length;
                i++;
            }
        }
        offsets[blockCount] = offset;

        int headerLen = 4 + (blockCount + 1) * 4;
        ByteBuffer header = ByteBuffer.allocate(headerLen).order(ByteOrder.LITTLE_ENDIAN);
        header.put((byte) HEADER_BYTE);
        header.put((byte) blockSize);
        header.putShort((short) tileSize);
        for (int v : offsets) header.putInt(v);

        ByteArrayOutputStream out = new ByteArrayOutputStream(headerLen + offset);
        out.write(header.array(), 0, header.array().length);
        for (byte[] block : blockPayload) out.write(block, 0, block.length);
        return out.toByteArray();
    }

    private static byte[] encodeBlock(byte[] raw, int tileSize, int x0, int y0, int bw, int bh) {
        int len = bw * bh;
        short[] vals = new short[len];

        boolean allZero = true;
        boolean allSame = true;
        short first = 0;
        short min = Short.MAX_VALUE;
        short max = Short.MIN_VALUE;

        int p = 0;
        for (int y = 0; y < bh; y++) {
            int row = y0 + y;
            for (int x = 0; x < bw; x++) {
                int col = x0 + x;
                short v = readLeShort(raw, (row * tileSize + col) * 2);
                vals[p++] = v;
                if (p == 1) first = v;
                if (v != 0) allZero = false;
                if (v != first) allSame = false;
                if (v < min) min = v;
                if (v > max) max = v;
            }
        }

        if (allZero) {
            return new byte[]{(byte) TYPE_SEA};
        }
        if (allSame) {
            ByteBuffer bb = ByteBuffer.allocate(3).order(ByteOrder.LITTLE_ENDIAN);
            bb.put((byte) TYPE_CONST);
            bb.putShort(first);
            return bb.array();
        }

        // DELTA8 can only be used when all values can be represented as:
        // value = base + delta, with unsigned 8-bit delta in [0, 255].
        int range = max - min;
        if (range <= 255) {
            int base = min;
            ByteBuffer bb = ByteBuffer.allocate(3 + len).order(ByteOrder.LITTLE_ENDIAN);
            bb.put((byte) TYPE_DELTA8);
            bb.putShort((short) base);
            for (short v : vals) {
                int d = v - base;
                if (d < 0 || d > 255) {
                    return encodeRaw16(vals);
                }
                bb.put((byte) d);
            }
            return bb.array();
        }

        return encodeRaw16(vals);
    }

    private static byte[] encodeRaw16(short[] vals) {
        ByteBuffer bb = ByteBuffer.allocate(1 + vals.length * 2).order(ByteOrder.LITTLE_ENDIAN);
        bb.put((byte) TYPE_RAW16);
        for (short v : vals) bb.putShort(v);
        return bb.array();
    }

    // Little Endian
    private static short readLeShort(byte[] data, int offset) {
        int lo = data[offset] & 0xFF;
        int hi = data[offset + 1] & 0xFF;
        return (short) (lo | (hi << 8));
    }
}
