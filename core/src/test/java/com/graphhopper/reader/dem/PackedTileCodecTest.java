package com.graphhopper.reader.dem;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.*;

public class PackedTileCodecTest {

    @Test
    public void testPackedRoundTripBlockTypes() {
        int tileSize = 32;
        short[] values = new short[tileSize * tileSize];

        // Block (0,0): SEA
        fillBlock(values, tileSize, 0, 0, 16, 16, (short) 0);

        // Block (1,0): CONST
        fillBlock(values, tileSize, 16, 0, 16, 16, (short) 1234);

        // Block (0,1): DELTA8 full unsigned range [0,255]
        for (int y = 16; y < 32; y++) {
            for (int x = 0; x < 16; x++) {
                int idx = y * tileSize + x;
                values[idx] = (short) (500 + (y - 16) * 16 + x);
            }
        }

        // Block (1,1): RAW16 (large range)
        for (int y = 16; y < 32; y++) {
            for (int x = 16; x < 32; x++) {
                int idx = y * tileSize + x;
                values[idx] = (short) ((x - 16) * 1000 - (y - 16) * 900);
            }
        }

        byte[] raw = toLeBytes(values);
        byte[] packed = PackedTileCodec.encodePacked(raw, tileSize, 16);
        ByteBuffer packedBuf = ByteBuffer.wrap(packed).order(ByteOrder.LITTLE_ENDIAN);
        assertTrue(PackedTileCodec.isPackedTile(packedBuf));
        PackedTileCodec.PackedHeader h = PackedTileCodec.readPackedHeader(packedBuf);

        assertEquals(tileSize, h.tileSize());
        assertEquals(16, h.blockSize());
        assertEquals(2, h.blocksPerAxis());

        // decode all samples via same read logic as provider
        for (int y = 0; y < tileSize; y++) {
            for (int x = 0; x < tileSize; x++) {
                short actual = samplePacked(packedBuf, h, x, y);
                short expected = values[y * tileSize + x];
                assertEquals(expected, actual, "Mismatch at x=" + x + ", y=" + y);
            }
        }
    }

    private static void fillBlock(short[] values, int tileSize, int x0, int y0, int bw, int bh, short val) {
        for (int y = y0; y < y0 + bh; y++) {
            for (int x = x0; x < x0 + bw; x++) {
                values[y * tileSize + x] = val;
            }
        }
    }

    private static byte[] toLeBytes(short[] values) {
        ByteBuffer bb = ByteBuffer.allocate(values.length * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (short v : values) bb.putShort(v);
        return bb.array();
    }

    private static short samplePacked(ByteBuffer data, PackedTileCodec.PackedHeader h, int x, int y) {
        int blockX = x / h.blockSize();
        int blockY = y / h.blockSize();
        int blockIndex = blockY * h.blocksPerAxis() + blockX;
        int blockStart = h.payloadOffset() + h.blockOffsets()[blockIndex];
        int localX = x - blockX * h.blockSize();
        int localY = y - blockY * h.blockSize();
        int idx = localY * h.blockSize() + localX;
        int type = data.get(blockStart) & 0xFF;

        if (type == PackedTileCodec.TYPE_SEA) return 0;
        if (type == PackedTileCodec.TYPE_CONST) return data.getShort(blockStart + 1);
        if (type == PackedTileCodec.TYPE_DELTA8) {
            short base = data.getShort(blockStart + 1);
            int delta = data.get(blockStart + 3 + idx) & 0xFF;
            return (short) (base + delta);
        }
        if (type == PackedTileCodec.TYPE_RAW16) return data.getShort(blockStart + 1 + idx * 2);
        throw new IllegalStateException("Unknown type " + type);
    }
}
