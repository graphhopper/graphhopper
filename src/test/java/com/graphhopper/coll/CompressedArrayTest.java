/*
 *  Copyright 2012 Peter Karich info@jetsli.de
 * 
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
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

import com.graphhopper.util.Helper;
import com.graphhopper.util.shapes.CoordTrig;
import java.util.Random;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
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

        CoordTrig coord = arr.get(0);
        assertEquals(10, coord.lat, 1e-6);
        assertEquals(1, coord.lon, 1e-6);

        coord = arr.get(1);
        assertEquals(11, coord.lat, 1e-6);
        assertEquals(2, coord.lon, 1e-6);

        coord = arr.get(2);
        assertEquals(12, coord.lat, 1e-6);
        assertEquals(3, coord.lon, 1e-6);

        try {
            assertNull(arr.get(3));
            assertFalse(true);
        } catch (Exception ex) {
        }
        // assertEquals(42, arr.calcMemInMB() * Helper.MB, 1e-3);
    }

    @Test
    public void testCompress2() throws Exception {
        CompressedArray arr = new CompressedArray();
        Random rand = new Random(0);
        for (int i = 0; i < 10000; i++) {
            arr.write(i * rand.nextDouble(), i);
        }
        arr.flush();
        // assertEquals(43, arr.calcMemInMB() * Helper.MB, 1e-3);
    }
}
