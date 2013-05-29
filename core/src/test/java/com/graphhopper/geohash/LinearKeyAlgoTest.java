/*
 *  Licensed to GraphHopper and Peter Karich under one or more contributor license 
 *  agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  GraphHopper licenses this file to you under the Apache License, 
 *  Version 2.0 (the "License"); you may not use this file except 
 *  in compliance with the License. You may obtain a copy of the 
 *  License at
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

import com.graphhopper.util.shapes.CoordTrig;
import static org.junit.Assert.*;
import org.junit.Test;

/**
 * @author Peter Karich
 */
public class LinearKeyAlgoTest {

    @Test
    public void testEncode() {
        KeyAlgo algo = new LinearKeyAlgo(3, 4).bounds(-1, 9, -2, 20);
        assertEquals(2L, algo.encode(-1, 5));
        assertEquals(11L, algo.encode(14, 7));

        // on grid => lower number
        assertEquals(5L, algo.encode(8, 4));

        // out of bounds
        assertEquals(1L, algo.encode(-4, 3));
        assertEquals(3L, algo.encode(2, 22));
        assertEquals(0L, algo.encode(-4, -4));

        assertEquals(11L, algo.encode(22, 22));
    }

    @Test
    public void testDecode() {
        KeyAlgo algo = new LinearKeyAlgo(3, 4).bounds(-1, 9, -2, 20);
        CoordTrig latLon = new CoordTrig();

        // decode that we get the center of the grid cell!
        algo.decode(5, latLon);
        assertEquals(9, latLon.lat, 1e-7);
        assertEquals(2.75, latLon.lon, 1e-7);

        algo.decode(2, latLon);
        assertEquals(1.66666666, latLon.lat, 1e-7);
        assertEquals(5.25, latLon.lon, 1e-7);

        algo.decode(11, latLon);
        assertEquals(16.3333333, latLon.lat, 1e-7);
        assertEquals(7.75, latLon.lon, 1e-7);

        algo.decode(10, latLon);
        assertEquals(16.3333333, latLon.lat, 1e-7);
        assertEquals(5.25, latLon.lon, 1e-7);
    }
}
