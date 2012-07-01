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
package de.jetsli.graph.util;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich, info@jetsli.de
 */
public class CalcDistanceTest {

    @Test
    public void testCalcCircumference() {
        assertEquals(CalcDistance.C, new CalcDistance().calcCircumference(0), 1e-7);
    }

    @Test
    public void testGeohashMaxDist() {
        assertEquals(CalcDistance.C / 2, new CalcDistance().calcSpatialKeyMaxDist(0), 1e-3);
        assertEquals(CalcDistance.C / 2, new CalcDistance().calcSpatialKeyMaxDist(1), 1e-3);
        assertEquals(CalcDistance.C / 4, new CalcDistance().calcSpatialKeyMaxDist(2), 1e-3);
        assertEquals(CalcDistance.C / 4, new CalcDistance().calcSpatialKeyMaxDist(3), 1e-3);
        assertEquals(CalcDistance.C / 8, new CalcDistance().calcSpatialKeyMaxDist(4), 1e-3);
        assertEquals(CalcDistance.C / 8, new CalcDistance().calcSpatialKeyMaxDist(5), 1e-3);
    }

    @Test
    public void testDistToGeohash() {
        assertEquals(-1, new CalcDistance().distToSpatialKeyLatBit(-1));

        assertEquals(0, new CalcDistance().distToSpatialKeyLatBit(CalcDistance.C / 2));

        assertEquals(2, new CalcDistance().distToSpatialKeyLatBit(CalcDistance.C / 4));
        assertEquals(4, new CalcDistance().distToSpatialKeyLatBit(CalcDistance.C / 8));
        assertEquals("should be round to bigger distance C/4", 2, new CalcDistance().distToSpatialKeyLatBit(CalcDistance.C / 6));

        // round to bigger distances!
        assertEquals(0, new CalcDistance().distToSpatialKeyLatBit(CalcDistance.C / 3));
        assertEquals(4, new CalcDistance().distToSpatialKeyLatBit(CalcDistance.C / 10));
        assertEquals(64, new CalcDistance().distToSpatialKeyLatBit(0));
        assertEquals(34, new CalcDistance().distToSpatialKeyLatBit(0.1));
        assertEquals(30, new CalcDistance().distToSpatialKeyLatBit(0.6));
    }

    @Test
    public void testDistance() {
        float lat = 24.235f;
        float lon = 47.234f;
        CalcDistance approxDist = new ApproxCalcDistance();
        CalcDistance dist = new CalcDistance();
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
}
