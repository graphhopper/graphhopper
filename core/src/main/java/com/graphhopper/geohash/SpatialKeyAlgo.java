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
package com.graphhopper.geohash;

import com.graphhopper.util.shapes.BBox;

/**
 * This class implements the idea of a geohash but without a string representation - to avoid confusion, this is
 * called 'spatial key'.
 *
 * Detailed information is available in this blog post:
 *
 * http://karussell.wordpress.com/2012/05/23/spatial-keys-memory-efficient-geohashes/
 * <p>
 * The hash can be used as a key for hash tables. When you organize the grid as a quad tree,
 * it resembles the path down the tree to reach the cell that it encodes. That's how it is used in
 * LocationIndexTree.
 * <p>
 * A 32 bit representation has a precision of approx 600 meters = 40000/2^16
 * <p>
 *
 * Implementation:
 * - From the query point and the grid parameters, calculate (integer) coordinates (x,y) of the cell
 *   the query point is in, using simple arithmetics.
 * - Use a lookup table to interleave the bits of (x,y) to get the cell number, which is the spatial key.
 *   See the drawing below. This is called a Z-order curve (because of the path you get when you follow
 *   increasing cell numbers through the grid), or Morton code.
 *
 * @author Peter Karich
 * @author Michael Zilske
 */

// A 2 bit (per axis) spatial key could look like
// 
//  |----|----|----|----|
//  |1010|1011|1110|1111|
//  |----|----|----|----|  lat0 == 1
//  |1000|1001|1100|1101|
// -|----|----|----|----|------
//  |0010|0011|0110|0111|
//  |----|----|----|----|  lat0 == 0
//  |0000|0001|0100|0101|
//  |----|----|----|----|
//            |
//  lon0 == 0 | lon0 == 1
public class SpatialKeyAlgo {
    private final int parts;
    private final int allBits;
    private final BBox bbox;
    private final double deltaY;
    private final double deltaX;

    /**
     * @param allBits how many bits should be used for the spatial key when encoding/decoding
     */
    public SpatialKeyAlgo(int allBits, BBox bounds) {
        if (allBits > 48)
            throw new IllegalStateException("allBits is too big for this implementation");

        if (allBits <= 0)
            throw new IllegalStateException("allBits must be positive");

        this.allBits = allBits;
        parts = (int) Math.pow(2, allBits / 2);
        bbox = bounds;
        deltaY = (bbox.maxLat - bbox.minLat) / parts;
        deltaX = (bbox.maxLon - bbox.minLon) / parts;
    }

    /**
     * @return the number of involved bits
     */
    public int getBits() {
        return allBits;
    }

    public final long encodeLatLon(double lat, double lon) {
        return encode(x(lon), y(lat));
    }

    public int y(double lat) {
        // Bounding this with parts - 1 or 0 only concerns the case where we are exactly on the bounding box.
        // (The next cell would already start there..)
        // (Or other situations, mostly in tests, where we actually run out of the bounding box.)
        return Math.max(0, Math.min((int) ((lat - bbox.minLat) / deltaY), parts - 1));
    }

    public int x(double lon) {
        // Bounding this with parts - 1 or 0 only concerns the case where we are exactly on the bounding box.
        // (The next cell would already start there..)
        // (Or other situations, mostly in tests, where we actually run out of the bounding box.)
        return Math.max(0, Math.min((int) ((lon - bbox.minLon) / deltaX), parts - 1));
    }

    // https://github.com/eren-ck/MortonLib/blob/master/src/main/java/com/erenck/mortonlib/Morton2D.java

    private final int MortonTable256[]
            = {
            0x0000, 0x0001, 0x0004, 0x0005, 0x0010, 0x0011, 0x0014, 0x0015,
            0x0040, 0x0041, 0x0044, 0x0045, 0x0050, 0x0051, 0x0054, 0x0055,
            0x0100, 0x0101, 0x0104, 0x0105, 0x0110, 0x0111, 0x0114, 0x0115,
            0x0140, 0x0141, 0x0144, 0x0145, 0x0150, 0x0151, 0x0154, 0x0155,
            0x0400, 0x0401, 0x0404, 0x0405, 0x0410, 0x0411, 0x0414, 0x0415,
            0x0440, 0x0441, 0x0444, 0x0445, 0x0450, 0x0451, 0x0454, 0x0455,
            0x0500, 0x0501, 0x0504, 0x0505, 0x0510, 0x0511, 0x0514, 0x0515,
            0x0540, 0x0541, 0x0544, 0x0545, 0x0550, 0x0551, 0x0554, 0x0555,
            0x1000, 0x1001, 0x1004, 0x1005, 0x1010, 0x1011, 0x1014, 0x1015,
            0x1040, 0x1041, 0x1044, 0x1045, 0x1050, 0x1051, 0x1054, 0x1055,
            0x1100, 0x1101, 0x1104, 0x1105, 0x1110, 0x1111, 0x1114, 0x1115,
            0x1140, 0x1141, 0x1144, 0x1145, 0x1150, 0x1151, 0x1154, 0x1155,
            0x1400, 0x1401, 0x1404, 0x1405, 0x1410, 0x1411, 0x1414, 0x1415,
            0x1440, 0x1441, 0x1444, 0x1445, 0x1450, 0x1451, 0x1454, 0x1455,
            0x1500, 0x1501, 0x1504, 0x1505, 0x1510, 0x1511, 0x1514, 0x1515,
            0x1540, 0x1541, 0x1544, 0x1545, 0x1550, 0x1551, 0x1554, 0x1555,
            0x4000, 0x4001, 0x4004, 0x4005, 0x4010, 0x4011, 0x4014, 0x4015,
            0x4040, 0x4041, 0x4044, 0x4045, 0x4050, 0x4051, 0x4054, 0x4055,
            0x4100, 0x4101, 0x4104, 0x4105, 0x4110, 0x4111, 0x4114, 0x4115,
            0x4140, 0x4141, 0x4144, 0x4145, 0x4150, 0x4151, 0x4154, 0x4155,
            0x4400, 0x4401, 0x4404, 0x4405, 0x4410, 0x4411, 0x4414, 0x4415,
            0x4440, 0x4441, 0x4444, 0x4445, 0x4450, 0x4451, 0x4454, 0x4455,
            0x4500, 0x4501, 0x4504, 0x4505, 0x4510, 0x4511, 0x4514, 0x4515,
            0x4540, 0x4541, 0x4544, 0x4545, 0x4550, 0x4551, 0x4554, 0x4555,
            0x5000, 0x5001, 0x5004, 0x5005, 0x5010, 0x5011, 0x5014, 0x5015,
            0x5040, 0x5041, 0x5044, 0x5045, 0x5050, 0x5051, 0x5054, 0x5055,
            0x5100, 0x5101, 0x5104, 0x5105, 0x5110, 0x5111, 0x5114, 0x5115,
            0x5140, 0x5141, 0x5144, 0x5145, 0x5150, 0x5151, 0x5154, 0x5155,
            0x5400, 0x5401, 0x5404, 0x5405, 0x5410, 0x5411, 0x5414, 0x5415,
            0x5440, 0x5441, 0x5444, 0x5445, 0x5450, 0x5451, 0x5454, 0x5455,
            0x5500, 0x5501, 0x5504, 0x5505, 0x5510, 0x5511, 0x5514, 0x5515,
            0x5540, 0x5541, 0x5544, 0x5545, 0x5550, 0x5551, 0x5554, 0x5555
    };

    private final int MortonTable256DecodeX[] = {
            0, 1, 0, 1, 2, 3, 2, 3, 0, 1, 0, 1, 2, 3, 2, 3,
            4, 5, 4, 5, 6, 7, 6, 7, 4, 5, 4, 5, 6, 7, 6, 7,
            0, 1, 0, 1, 2, 3, 2, 3, 0, 1, 0, 1, 2, 3, 2, 3,
            4, 5, 4, 5, 6, 7, 6, 7, 4, 5, 4, 5, 6, 7, 6, 7,
            8, 9, 8, 9, 10, 11, 10, 11, 8, 9, 8, 9, 10, 11, 10, 11,
            12, 13, 12, 13, 14, 15, 14, 15, 12, 13, 12, 13, 14, 15, 14, 15,
            8, 9, 8, 9, 10, 11, 10, 11, 8, 9, 8, 9, 10, 11, 10, 11,
            12, 13, 12, 13, 14, 15, 14, 15, 12, 13, 12, 13, 14, 15, 14, 15,
            0, 1, 0, 1, 2, 3, 2, 3, 0, 1, 0, 1, 2, 3, 2, 3,
            4, 5, 4, 5, 6, 7, 6, 7, 4, 5, 4, 5, 6, 7, 6, 7,
            0, 1, 0, 1, 2, 3, 2, 3, 0, 1, 0, 1, 2, 3, 2, 3,
            4, 5, 4, 5, 6, 7, 6, 7, 4, 5, 4, 5, 6, 7, 6, 7,
            8, 9, 8, 9, 10, 11, 10, 11, 8, 9, 8, 9, 10, 11, 10, 11,
            12, 13, 12, 13, 14, 15, 14, 15, 12, 13, 12, 13, 14, 15, 14, 15,
            8, 9, 8, 9, 10, 11, 10, 11, 8, 9, 8, 9, 10, 11, 10, 11,
            12, 13, 12, 13, 14, 15, 14, 15, 12, 13, 12, 13, 14, 15, 14, 15
    };

    private final int MortonTable256DecodeY[] = {
            0, 0, 1, 1, 0, 0, 1, 1, 2, 2, 3, 3, 2, 2, 3, 3,
            0, 0, 1, 1, 0, 0, 1, 1, 2, 2, 3, 3, 2, 2, 3, 3,
            4, 4, 5, 5, 4, 4, 5, 5, 6, 6, 7, 7, 6, 6, 7, 7,
            4, 4, 5, 5, 4, 4, 5, 5, 6, 6, 7, 7, 6, 6, 7, 7,
            0, 0, 1, 1, 0, 0, 1, 1, 2, 2, 3, 3, 2, 2, 3, 3,
            0, 0, 1, 1, 0, 0, 1, 1, 2, 2, 3, 3, 2, 2, 3, 3,
            4, 4, 5, 5, 4, 4, 5, 5, 6, 6, 7, 7, 6, 6, 7, 7,
            4, 4, 5, 5, 4, 4, 5, 5, 6, 6, 7, 7, 6, 6, 7, 7,
            8, 8, 9, 9, 8, 8, 9, 9, 10, 10, 11, 11, 10, 10, 11, 11,
            8, 8, 9, 9, 8, 8, 9, 9, 10, 10, 11, 11, 10, 10, 11, 11,
            12, 12, 13, 13, 12, 12, 13, 13, 14, 14, 15, 15, 14, 14, 15, 15,
            12, 12, 13, 13, 12, 12, 13, 13, 14, 14, 15, 15, 14, 14, 15, 15,
            8, 8, 9, 9, 8, 8, 9, 9, 10, 10, 11, 11, 10, 10, 11, 11,
            8, 8, 9, 9, 8, 8, 9, 9, 10, 10, 11, 11, 10, 10, 11, 11,
            12, 12, 13, 13, 12, 12, 13, 13, 14, 14, 15, 15, 14, 14, 15, 15,
            12, 12, 13, 13, 12, 12, 13, 13, 14, 14, 15, 15, 14, 14, 15, 15
    };

    public long encode(int x, int y) {
        int EIGHTBITMASK = 0xff;
        return (MortonTable256[(y >> 8) & EIGHTBITMASK] << 17
                | MortonTable256[(x >> 8) & EIGHTBITMASK] << 16
                | MortonTable256[y & EIGHTBITMASK] << 1
                | MortonTable256[x & EIGHTBITMASK]);
    }

    public int[] decode(long z) {
        int[] result = new int[2];
        // Morton codes up to 48 bits
        if (z < Math.pow(2, 48)) {
            result[0] = decodeHelper(z, MortonTable256DecodeX);
            result[1] = decodeHelper(z, MortonTable256DecodeY);
        }
        return result;
    }

    private static int decodeHelper(long z, int coord[]) {
        long a = 0;
        long EIGHTBITMASK = 0x000000ff;
        long loops = (long) Math.floor(64.0f / 9.0f);
        for (long i = 0; i < loops; ++i) {
            a |= (coord[(int) ((z >> (i * 8)) & EIGHTBITMASK)] << (4 * i));
        }
        return (int) a;
    }

    // https://en.wikipedia.org/wiki/Z-order_curve

    public long up(long z) {
        return (((z | 0b0101010101010101010101010101010101010101010101010101010101010101L) + 1) &
                0b1010101010101010101010101010101010101010101010101010101010101010L) | (z & 0b0101010101010101010101010101010101010101010101010101010101010101L);
    }

    public long down(long z) {
        return (((z & 0b1010101010101010101010101010101010101010101010101010101010101010L) - 1) &
                0b1010101010101010101010101010101010101010101010101010101010101010L) | (z & 0b0101010101010101010101010101010101010101010101010101010101010101L);
    }

    public long right(long z) {
        return (((z | 0b1010101010101010101010101010101010101010101010101010101010101010L) + 1)
                & 0b0101010101010101010101010101010101010101010101010101010101010101L) | (z & 0b1010101010101010101010101010101010101010101010101010101010101010L);
    }

    public long left(long z) {
        return (((z & 0b0101010101010101010101010101010101010101010101010101010101010101L) - 1) &
                0b0101010101010101010101010101010101010101010101010101010101010101L) | (z & 0b1010101010101010101010101010101010101010101010101010101010101010L);
    }

}
