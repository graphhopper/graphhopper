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
package com.graphhopper.coll;

import com.graphhopper.util.shapes.GHPoint;
import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * @author Peter Karich
 */
public class CompressedArrayTest {
    @Test
    public void testCompress() throws Exception {
        CompressedArray arr = new CompressedArray();
        arr.write(10, 1);
        arr.write(11, 2);
        arr.write(12, 3);
        arr.flush();

        GHPoint coord = arr.get(0);
        assertEquals(10, coord.lat, 1e-6);
        assertEquals(1, coord.lon, 1e-6);

        coord = arr.get(1);
        assertEquals(11, coord.lat, 1e-6);
        assertEquals(2, coord.lon, 1e-6);

        coord = arr.get(2);
        assertEquals(12, coord.lat, 1e-6);
        assertEquals(3, coord.lon, 1e-6);

        assertNull(arr.get(3));
        // assertEquals(42, arr.calcMemInMB() * Helper.MB, 1e-3);
    }

    @Test
    public void testCompress2() throws Exception {
        CompressedArray arr = new CompressedArray();
        Random rand = new Random(0);
        for (int i = 0; i < 10000; i++) {
            arr.write(i / 1000.0, rand.nextDouble() * 90);
        }

        arr.flush();
        GHPoint coord = arr.get(0);
        assertEquals(0, coord.lat, 1e-6);
        assertEquals(65.787100, coord.lon, 1e-6);

        coord = arr.get(999);
        assertEquals(0.999, coord.lat, 1e-6);

        coord = arr.get(9998);
        assertEquals(9.998, coord.lat, 1e-6);

        coord = arr.get(9999);
        assertEquals(9.999, coord.lat, 1e-6);

        assertNull(arr.get(10000));

        // assertEquals(43, arr.calcMemInMB() * Helper.MB, 1e-3);
    }
}
