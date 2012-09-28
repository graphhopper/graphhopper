/*
 *  Copyright 2012 Peter Karich 
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
package de.jetsli.graph.util;

import static org.junit.Assert.*;
import org.junit.Test;

/**
 *
 * @author Peter Karich, 
 */
public class DistanceCalcTest {

    @Test
    public void testCalcCircumference() {
        assertEquals(DistanceCalc.C, new DistanceCalc().calcCircumference(0), 1e-7);
    }

    @Test
    public void testGeohashMaxDist() {
        assertEquals(DistanceCalc.C / 2, new DistanceCalc().calcSpatialKeyMaxDist(0), 1e-3);
        assertEquals(DistanceCalc.C / 2, new DistanceCalc().calcSpatialKeyMaxDist(1), 1e-3);
        assertEquals(DistanceCalc.C / 4, new DistanceCalc().calcSpatialKeyMaxDist(2), 1e-3);
        assertEquals(DistanceCalc.C / 4, new DistanceCalc().calcSpatialKeyMaxDist(3), 1e-3);
        assertEquals(DistanceCalc.C / 8, new DistanceCalc().calcSpatialKeyMaxDist(4), 1e-3);
        assertEquals(DistanceCalc.C / 8, new DistanceCalc().calcSpatialKeyMaxDist(5), 1e-3);
    }

    @Test
    public void testDistToGeohash() {
        assertEquals(-1, new DistanceCalc().distToSpatialKeyLatBit(-1));

        assertEquals(0, new DistanceCalc().distToSpatialKeyLatBit(DistanceCalc.C / 2));

        assertEquals(2, new DistanceCalc().distToSpatialKeyLatBit(DistanceCalc.C / 4));
        assertEquals(4, new DistanceCalc().distToSpatialKeyLatBit(DistanceCalc.C / 8));
        assertEquals("should be round to bigger distance C/4", 2, new DistanceCalc().distToSpatialKeyLatBit(DistanceCalc.C / 6));

        // round to bigger distances!
        assertEquals(0, new DistanceCalc().distToSpatialKeyLatBit(DistanceCalc.C / 3));
        assertEquals(4, new DistanceCalc().distToSpatialKeyLatBit(DistanceCalc.C / 10));
        assertEquals(64, new DistanceCalc().distToSpatialKeyLatBit(0));
        assertEquals(34, new DistanceCalc().distToSpatialKeyLatBit(0.1));
        assertEquals(30, new DistanceCalc().distToSpatialKeyLatBit(0.6));
    }

    @Test
    public void testDistance() {
        float lat = 24.235f;
        float lon = 47.234f;
        DistanceCalc approxDist = new DistanceCosProjection();
        DistanceCalc dist = new DistanceCalc();
        double res = 15.051;
        assertEquals(res, dist.calcDistKm(lat, lon, lat - 0.1, lon + 0.1), 1e-3);
        assertEquals(dist.normalizeDist(res), dist.calcNormalizedDist(lat, lon, lat - 0.1, lon + 0.1), 1e-3);
        assertEquals(res, approxDist.calcDistKm(lat, lon, lat - 0.1, lon + 0.1), 1e-3);

        res = 15.046;
        assertEquals(res, dist.calcDistKm(lat, lon, lat + 0.1, lon - 0.1), 1e-3);
        assertEquals(dist.normalizeDist(res), dist.calcNormalizedDist(lat, lon, lat + 0.1, lon - 0.1), 1e-3);
        assertEquals(res, approxDist.calcDistKm(lat, lon, lat + 0.1, lon - 0.1), 1e-3);

        res = 150.748;
        assertEquals(res, dist.calcDistKm(lat, lon, lat - 1, lon + 1), 1e-3);
        assertEquals(dist.normalizeDist(res), dist.calcNormalizedDist(lat, lon, lat - 1, lon + 1), 1e-3);
        assertEquals(res, approxDist.calcDistKm(lat, lon, lat - 1, lon + 1), 1e-2);

        res = 150.211;
        assertEquals(res, dist.calcDistKm(lat, lon, lat + 1, lon - 1), 1e-3);
        assertEquals(dist.normalizeDist(res), dist.calcNormalizedDist(lat, lon, lat + 1, lon - 1), 1e-3);
        assertEquals(res, approxDist.calcDistKm(lat, lon, lat + 1, lon - 1), 1e-2);

        res = 1527.919;
        assertEquals(res, dist.calcDistKm(lat, lon, lat - 10, lon + 10), 1e-3);
        assertEquals(dist.normalizeDist(res), dist.calcNormalizedDist(lat, lon, lat - 10, lon + 10), 1e-3);
        assertEquals(res, approxDist.calcDistKm(lat, lon, lat - 10, lon + 10), 10);

        res = 1474.016;
        assertEquals(res, dist.calcDistKm(lat, lon, lat + 10, lon - 10), 1e-3);
        assertEquals(dist.normalizeDist(res), dist.calcNormalizedDist(lat, lon, lat + 10, lon - 10), 1e-3);
        assertEquals(res, approxDist.calcDistKm(lat, lon, lat + 10, lon - 10), 10);
    }

    @Test
    public void testEdgeDistance() {
        DistanceCalc calc = new DistanceCalc();
        double dist = calc.calcNormalizedEdgeDistance(49.94241, 11.544356,
                49.937964, 11.541824,
                49.942272, 11.555643);
        double expectedDist = calc.calcNormalizedDist(49.94241, 11.544356,
                49.9394, 11.54681);
        assertEquals(expectedDist, dist, 1e-4);

        // test identical lats
        dist = calc.calcNormalizedEdgeDistance(49.936299, 11.543992,
                49.9357, 11.543047,
                49.9357, 11.549227);
        expectedDist = calc.calcNormalizedDist(49.936299, 11.543992,
                49.9357, 11.543992);
        assertEquals(expectedDist, dist, 1e-4);
    }

    @Test
    public void testValidEdgeDistance() {
        DistanceCalc calc = new DistanceCalc();
        assertTrue(calc.validEdgeDistance(49.94241, 11.544356, 49.937964, 11.541824, 49.942272, 11.555643));
        assertTrue(calc.validEdgeDistance(49.936624, 11.547636, 49.937964, 11.541824, 49.942272, 11.555643));
        assertTrue(calc.validEdgeDistance(49.940712, 11.556069, 49.937964, 11.541824, 49.942272, 11.555643));

        // left bottom of the edge
        assertFalse(calc.validEdgeDistance(49.935119, 11.541649, 49.937964, 11.541824, 49.942272, 11.555643));
        // left top of the edge
        assertFalse(calc.validEdgeDistance(49.939317, 11.539675, 49.937964, 11.541824, 49.942272, 11.555643));
        // right top of the edge
        assertFalse(calc.validEdgeDistance(49.944482, 11.555446, 49.937964, 11.541824, 49.942272, 11.555643));
        // right bottom of the edge
        assertFalse(calc.validEdgeDistance(49.94085, 11.557356, 49.937964, 11.541824, 49.942272, 11.555643));
    }
}
