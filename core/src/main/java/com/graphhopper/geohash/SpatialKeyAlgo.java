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

import com.graphhopper.util.shapes.BBox;
import com.graphhopper.util.shapes.CoordTrig;

/**
 * This class implements the idea of a geohash but in 'binary form' - to avoid
 * confusion this is called 'spatial key'. The idea of mixing the latitude and
 * longitude is also taken to allow removing the insignificant (right side) bits
 * to make a geo-query or the coordinate less precise. E.g. for a 3 bit
 * precision the spatial key would need 6 bits and look like:
 *
 * lat0 lon0 | lat1 lon1 | lat2 lon2
 *
 * Detailed information is available in this blog post:
 * http://karussell.wordpress.com/2012/05/23/spatial-keys-memory-efficient-geohashes/
 *
 * The bits are usable as key for hash tables like our SpatialKeyHashtable or
 * for a spatial tree like QuadTreeSimple. Also the binary form makes it
 * relative simple for implementations using this encoding scheme to expand to
 * arbitrary dimension (e.g. shifting n-times if n would be the dimension).
 *
 * A 32 bit representation has a precision of approx 600 meters = 40000/2^16
 *
 * There are different possibilities how to handle different precision and order
 * of bits. Either:
 *
 * lat0 lon0 | lat1 lon1 | lat2 lon2
 *
 * 0 0 | lat0 lon0 | lat1 lon1
 *
 * as it is done now. Advantage: A single shift is only necessary to make it
 * less precise. Or:
 *
 * lat2 lon2 | lat1 lon1 | lat0 lon0
 *
 * 0 0 | lat1 lon1 | lat0 lon0
 *
 * Advantage: the bit mask to get lat0 lon0 is simple: 000..0011 and independent
 * of the precision! But when stored e.g. as int one would need to (left) shift
 * several times if precision is only 3bits.
 *
 * @author Peter Karich
 */
// A 2 bit precision spatial key could look like
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
public class SpatialKeyAlgo implements KeyAlgo {

    private BBox bbox;
    private int allBits;
    private long initialBits;

    /**
     * @param allBits how many bits should be used for the spatial key when
     * encoding/decoding
     */
    public SpatialKeyAlgo(int allBits) {
        myinit(allBits);
    }

    private void myinit(int allBits) {
        if (allBits > 64)
            throw new IllegalStateException("allBits is too big and does not fit into 8 bytes");

        if (allBits <= 0)
            throw new IllegalStateException("allBits must be positive");

//        if ((allBits & 0x1) == 1)
//            throw new IllegalStateException("allBits needs to be even to use the same amount for lat and lon");

        this.allBits = allBits;
        initialBits = 1L << (allBits - 1);
        setWorldBounds();
    }

    /**
     * @return the number of involved bits
     */
    public int bits() {
        return allBits;
    }

    public int exactPrecision() {
        // 360 / 2^(allBits/2) = 1/precision
        int p = (int) (Math.pow(2, allBits) / 360);
        // no rounding error
        p++;
        return (int) Math.log10(p);
    }

    public SpatialKeyAlgo bounds(BBox box) {
        bbox = box.clone();
        return this;
    }

    @Override
    public SpatialKeyAlgo bounds(double minLonInit, double maxLonInit, double minLatInit, double maxLatInit) {
        bounds(new BBox(minLonInit, maxLonInit, minLatInit, maxLatInit));
        return this;
    }

    protected void setWorldBounds() {
        bounds(-180, 180, -90, 90);
    }

    @Override
    public long encode(CoordTrig coord) {
        return encode(coord.lat, coord.lon);
    }

    /**
     * Take latitude and longitude as input.
     *
     * @return the spatial key
     */
    @Override
    public final long encode(double lat, double lon) {
        // PERFORMANCE: int operations would be faster than double (for further comparison etc)
        // but we would need 'long' because 'int factorForPrecision' is not enough (problem: coord!=decode(encode(coord)) see testBijection)
        // and 'long'-ops are more expensive than double (at least on 32bit systems)
        long hash = 0;
        double minLatTmp = bbox.minLat;
        double maxLatTmp = bbox.maxLat;
        double minLonTmp = bbox.minLon;
        double maxLonTmp = bbox.maxLon;
        int i = 0;
        while (true) {
            if (minLatTmp < maxLatTmp) {
                double midLat = (minLatTmp + maxLatTmp) / 2;
                if (lat > midLat) {
                    hash |= 1;
                    minLatTmp = midLat;
                } else
                    maxLatTmp = midLat;
            }
            i++;
            if (i < allBits)
                hash <<= 1;
            // if allBits is an odd number
            else
                break;

            if (minLonTmp < maxLonTmp) {
                double midLon = (minLonTmp + maxLonTmp) / 2;
                if (lon > midLon) {
                    hash |= 1;
                    minLonTmp = midLon;
                } else
                    maxLonTmp = midLon;
            }
            i++;
            if (i < allBits)
                hash <<= 1;
            else
                break;
        }
        return hash;
    }

    /**
     * This method returns latitude and longitude via latLon - calculated from
     * specified spatialKey
     *
     * @param spatialKey is the input
     */
    @Override
    public final void decode(long spatialKey, CoordTrig latLon) {
        // Performance: calculating 'midLon' and 'midLat' on the fly is not slower than using 
        // precalculated values from arrays and for 'bits' a precalculated array is even slightly slower!

        // Use the value in the middle => start from "min" use "max" as initial step-size
        double midLat = (bbox.maxLat - bbox.minLat) / 2;
        double midLon = (bbox.maxLon - bbox.minLon) / 2;
        double lat = bbox.minLat;
        double lon = bbox.minLon;
        long bits = initialBits;
        while (true) {
            if ((spatialKey & bits) != 0)
                lat += midLat;

            midLat /= 2;
            bits >>>= 1;
            if ((spatialKey & bits) != 0)
                lon += midLon;

            midLon /= 2;
            if (bits > 1)
                bits >>>= 1;
            else
                break;
        }

        // stable rounding - see testBijection
        lat += midLat;
        lon += midLon;
        latLon.lat = lat;
        latLon.lon = lon;
    }

    @Override
    public String toString() {
        return "bits:" + allBits + ", bounds:" + bbox;
    }
}
