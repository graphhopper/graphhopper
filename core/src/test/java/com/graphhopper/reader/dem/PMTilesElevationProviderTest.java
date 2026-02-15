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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for PMTilesElevationProvider and PMTilesReader.
 * <p>
 * The elevation tests require a PMTiles extract for Saxony (zoom 10-12). Create it via:
 * <pre>
 * pmtiles extract input.pmtiles files/saxony-z10-12.pmtiles --bbox=11.8,50.1,15.1,51.7 --minzoom=10 --maxzoom=12
 * </pre>
 */
public class PMTilesElevationProviderTest {

    private PMTilesElevationProvider instance;

    @AfterEach
    public void tearDown() {
        if (instance != null)
            instance.release();
    }

    // =========================================================================
    // Unit tests (no PMTiles file needed)
    // =========================================================================

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
                PMTilesElevationProvider.TerrainEncoding.TERRARIUM, false, 10);

        // Elbe
        assertEquals(118, instance.getEle(50.905488,14.204129), 1);

        // Schrammsteine
        assertEquals(384, instance.getEle(50.912142,14.2076), 1);
        // Very close but on the path
        assertEquals(377, instance.getEle(50.911849,14.208042), 1);
        assertEquals(384, instance.getEle(50.9119,14.207466), 1);
    }

    // called pmtiles with --bbox=14.203657,50.905387,14.208871,50.912341 --minzoom=10 --maxzoom=11
    @Test
    public void testRealPMTilesWithZoom11() {
        instance = new PMTilesElevationProvider("./files/near-badschandau-z10-11.pmtiles",
                PMTilesElevationProvider.TerrainEncoding.TERRARIUM, false, 11);

        // Elbe
        assertEquals(118, instance.getEle(50.905488,14.204129), 1);

        // Schrammsteine
        assertEquals(390, instance.getEle(50.912142,14.2076), 1);
        // Very close but on the path
        assertEquals(381, instance.getEle(50.911849,14.208042), 1);
        assertEquals(361, instance.getEle(50.9119,14.207466), 1);
    }

    @Test
    public void testOutsideArea() {
        instance = new PMTilesElevationProvider("./files/near-badschandau-z10-11.pmtiles",
                PMTilesElevationProvider.TerrainEncoding.MAPBOX, false, 10);

        // Point far outside the extract — should return NaN
        double ele = instance.getEle(0, 0);
        assertTrue(Double.isNaN(ele), "expected NaN for point outside extract, got " + ele);
    }
}
