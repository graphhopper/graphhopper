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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for PMTilesElevationProvider and PMTilesReader.
 */
public class PMTilesElevationProviderTest {

    private ElevationProvider instance;

    @AfterEach
    public void tearDown() {
        if (instance != null)
            instance.release();
    }

    @Test
    public void testHilbertRoundTrip() {
        // verify zxy -> tileId -> zxy round-trips correctly
        int[][] cases = {{0, 0, 0}, {1, 0, 0}, {1, 1, 1}, {5, 17, 11}, {10, 512, 300}, {11, 1024, 600}, {12, 2048, 1200}};
        for (int[] c : cases) {
            long tileId = PMTilesReader.zxyToTileId(c[0], c[1], c[2]);
            int[] result = PMTilesReader.tileIdToZxy(tileId);
            assertArrayEquals(c, result, "round-trip failed for z=" + c[0] + " x=" + c[1] + " y=" + c[2]);
        }
    }

    @Test
    public void testTileIdZoom0() {
        assertEquals(0, PMTilesReader.zxyToTileId(0, 0, 0));
        assertArrayEquals(new int[]{0, 0, 0}, PMTilesReader.tileIdToZxy(0));
    }

    @Test
    public void testTileIdOrdering() {
        // tile IDs at higher zoom levels should be larger than all tile IDs at lower zoom levels
        long maxZ10 = 0;
        for (int x = 0; x < (1 << 10); x += 100) {
            for (int y = 0; y < (1 << 10); y += 100) {
                maxZ10 = Math.max(maxZ10, PMTilesReader.zxyToTileId(10, x, y));
            }
        }
        long minZ11 = Long.MAX_VALUE;
        for (int x = 0; x < (1 << 11); x += 100) {
            for (int y = 0; y < (1 << 11); y += 100) {
                minZ11 = Math.min(minZ11, PMTilesReader.zxyToTileId(11, x, y));
            }
        }
        assertTrue(maxZ10 < minZ11, "all zoom 10 tile IDs should be less than all zoom 11 tile IDs");
    }

    @Test
    public void testGunzip() throws Exception {
        byte[] original = "hello pmtiles".getBytes();
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        try (java.util.zip.GZIPOutputStream gos = new java.util.zip.GZIPOutputStream(baos)) {
            gos.write(original);
        }
        byte[] decompressed = PMTilesReader.gunzip(baos.toByteArray());
        assertArrayEquals(original, decompressed);
    }

    @Test
    public void testRealPMTilesWithZoom10() {
        instance = new PMTilesElevationProvider("./files/near-badschandau-z10-11.pmtiles",
                PMTilesElevationProvider.TerrainEncoding.TERRARIUM, false, 10,
                null).init();
        // Elbe
        assertEquals(118, instance.getEle(50.905488, 14.204129), 1);

        // Schrammsteine
        assertEquals(384, instance.getEle(50.912142, 14.2076), 1);
        // Very close but on the path
        assertEquals(377, instance.getEle(50.911849, 14.208042), 1);
        assertEquals(384, instance.getEle(50.9119, 14.207466), 1);
    }

    @Test
    public void testRealPMTilesInterpolate() {
        instance = new PMTilesElevationProvider("./files/near-badschandau-z10-11.pmtiles",
                PMTilesElevationProvider.TerrainEncoding.TERRARIUM, true, 10,
                null).init();
        // Elbe
        assertEquals(118, instance.getEle(50.905488, 14.204129), 1);

        // Schrammsteine
        assertEquals(386, instance.getEle(50.912142, 14.2076), 1);
        // Very close but on the path
        assertEquals(370, instance.getEle(50.911849, 14.208042), 1);
        assertEquals(370, instance.getEle(50.9119, 14.207466), 1);
    }

    @Test
    public void testRealPMTilesWithZoom11() {
        // created this file via pmtiles and --bbox=14.203657,50.905387,14.208871,50.912341 --minzoom=10 --maxzoom=11
        instance = new PMTilesElevationProvider("./files/near-badschandau-z10-11.pmtiles",
                PMTilesElevationProvider.TerrainEncoding.TERRARIUM, false, 11, null).
                init();

        // Elbe
        assertEquals(118, instance.getEle(50.905488, 14.204129), 1);

        // Schrammsteine
        assertEquals(390, instance.getEle(50.912142, 14.2076), 1);
        // Very close but on the path
        assertEquals(381, instance.getEle(50.911849, 14.208042), 1);
        assertEquals(361, instance.getEle(50.9119, 14.207466), 1);
    }

    @Test
    public void testOutsideArea() {
        instance = new PMTilesElevationProvider("./files/near-badschandau-z10-11.pmtiles",
                PMTilesElevationProvider.TerrainEncoding.TERRARIUM, false, 10, null).
                init();

        // Point far outside the extract — should return NaN
        double ele = instance.getEle(0, 0);
        assertTrue(Double.isNaN(ele), "expected NaN for point outside extract, got " + ele);
    }

    @Test
    public void testFillGaps() {
        // 4x4 grid of shorts, with two gap pixels
        //   100  100  MIN  100
        //   100  MIN  100  100
        //   100  100  100  100
        //   100  100  100  100
        int w = 4, h = 4;
        short[][] grid = {
                {100, 100, Short.MIN_VALUE, 100},
                {100, Short.MIN_VALUE, 100, 100},
                {100, 100, 100, 100},
                {100, 100, 100, 100},
        };
        byte[] data = new byte[w * h * 2];
        ByteBuffer bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                bb.putShort((y * w + x) * 2, grid[y][x]);

        PMTilesElevationProvider.fillGaps(data, w, 0, 0, 1);

        ShortBuffer shorts = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();
        // Both gap pixels should now be 100 (average of all-100 neighbors)
        assertEquals(100, shorts.get(0 * w + 2), "gap at (2,0) should be filled");
        assertEquals(100, shorts.get(1 * w + 1), "gap at (1,1) should be filled");
        // All other pixels unchanged
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                if (!((x == 2 && y == 0) || (x == 1 && y == 1)))
                    assertEquals(100, shorts.get(y * w + x), "pixel (" + x + "," + y + ") should be unchanged");
    }

    @Test
    public void testFillGapsPropagatesToInterior() {
        // 5x1 strip: 200 MIN MIN MIN 200
        // After fill: 200 200 200 200 200
        int w = 5;
        byte[] data = new byte[w * 2];
        ByteBuffer bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        bb.putShort(0, (short) 200);
        bb.putShort(2, Short.MIN_VALUE);
        bb.putShort(4, Short.MIN_VALUE);
        bb.putShort(6, Short.MIN_VALUE);
        bb.putShort(8, (short) 200);

        PMTilesElevationProvider.fillGaps(data, w, 0, 0, 1);

        ShortBuffer shorts = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();
        assertEquals(200, shorts.get(0));
        assertEquals(200, shorts.get(1)); // filled from left neighbor
        assertEquals(200, shorts.get(2)); // filled from both propagated neighbors
        assertEquals(200, shorts.get(3)); // filled from right neighbor
        assertEquals(200, shorts.get(4));
    }

    @Test
    public void testFillGapsIsolatedGapUnchanged() {
        // 3x3 grid, all MIN_VALUE — no valid data to propagate from
        int w = 3;
        byte[] data = new byte[w * w * 2];
        ByteBuffer bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < w * w; i++)
            bb.putShort(i * 2, Short.MIN_VALUE);

        PMTilesElevationProvider.fillGaps(data, w, 0, 0, 1);

        ShortBuffer shorts = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();
        for (int i = 0; i < w * w; i++)
            assertEquals(Short.MIN_VALUE, shorts.get(i), "isolated gap at index " + i + " should remain MIN_VALUE");
    }
}
