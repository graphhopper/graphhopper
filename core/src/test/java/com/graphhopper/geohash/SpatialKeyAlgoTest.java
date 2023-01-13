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

import com.graphhopper.core.util.BitUtil;
import com.graphhopper.core.util.shapes.BBox;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Peter Karich
 */
public class SpatialKeyAlgoTest {
    @Test
    public void testEncode() {
        SpatialKeyAlgo algo = new SpatialKeyAlgo(32, new BBox(-180, 180, -90, 90));
        long val = algo.encodeLatLon(-24.235345f, 47.234234f);
        assertEquals("01100110101000111100000110010100", BitUtil.LITTLE.toLastBitString(val, 32));
    }

    @Test
    public void testEdgeCases() {
        double minLon = -1, maxLon = 1.6;
        double minLat = -1, maxLat = 0.5;
        int parts = 4;
        int bits = (int) (Math.log(parts * parts) / Math.log(2));
        SpatialKeyAlgo spatialKeyAlgo = new SpatialKeyAlgo(bits, new BBox(minLon, maxLon, minLat, maxLat));
        // lat border 0.125
        assertEquals(11, spatialKeyAlgo.encodeLatLon(0.125, -0.2));
        assertEquals(9, spatialKeyAlgo.encodeLatLon(0.124, -0.2));
        // lon border -0.35
        assertEquals(11, spatialKeyAlgo.encodeLatLon(0.2, -0.35));
        assertEquals(10, spatialKeyAlgo.encodeLatLon(0.2, -0.351));
    }

    @Test
    public void testFourBits() {
        // This is from the picture in the comments of SpatialKeyAlgo
        SpatialKeyAlgo spatialKeyAlgo = new SpatialKeyAlgo(4, new BBox(-180, 180, -90, 90));
        assertEquals(0b0000, spatialKeyAlgo.encode(0, 0));
        assertEquals(0b0001, spatialKeyAlgo.encode(1, 0));
        assertEquals(0b0100, spatialKeyAlgo.encode(2, 0));
        assertEquals(0b0101, spatialKeyAlgo.encode(3, 0));
        assertEquals(0b0010, spatialKeyAlgo.encode(0, 1));
        assertEquals(0b1000, spatialKeyAlgo.encode(0, 2));
        assertEquals(0b1010, spatialKeyAlgo.encode(0, 3));
        assertEquals(0b1100, spatialKeyAlgo.encode(2, 2));

        for (int x=0; x<4; x++) {
            for (int y=0; y<4; y++) {
                int[] xy = spatialKeyAlgo.decode(spatialKeyAlgo.encode(x, y));
                assertEquals(x, xy[0]);
                assertEquals(y, xy[1]);
            }
        }

        assertEquals(spatialKeyAlgo.encode(2, 0), spatialKeyAlgo.right(spatialKeyAlgo.right(spatialKeyAlgo.encode(0, 0))));
    }

    @Test
    public void testTwentyBits() {
        SpatialKeyAlgo spatialKeyAlgo = new SpatialKeyAlgo(20, new BBox(-180, 180, -90, 90));
        assertEquals(0b11111111111111111111L, spatialKeyAlgo.encode(1023, 1023));

        for (int x=0; x<1024; x++) {
            for (int y=0; y<1024; y++) {
                int[] xy = spatialKeyAlgo.decode(spatialKeyAlgo.encode(x, y));
                assertEquals(x, xy[0]);
                assertEquals(y, xy[1]);
            }
        }
    }

}
