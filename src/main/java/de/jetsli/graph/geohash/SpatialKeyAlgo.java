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
package de.jetsli.graph.geohash;

import de.jetsli.graph.util.CoordTrig;

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
/**
 * This class implements the idea of a geohash but in 'binary form' - to avoid confusion this is
 * called 'spatial key'. The idea of mixing the latitude and longitude is also taken to allow
 * removing the insignificant (right side) bits to make a geo-query or the coordinate less precise.
 * E.g. for a 3 bit precision the spatial key would need 6 bits and look like:
 *
 * lat0 lon0 | lat1 lon1 | lat2 lon2
 *
 * The bits are usable as key for hash tables like our SpatialKeyHashtable or for a spatial tree
 * like QuadTreeSimple. Also the binary form makes it relative simple for implementations using this
 * encoding scheme to expand to arbitrary dimension (e.g. shifting n-times if n would be the
 * dimension).
 *
 * A 32 bit representation has a precision of approx 600 meters = 40000/2^16
 *
 * There are different possibilities how to handle different precision and order of bits. Either:
 *
 * lat0 lon0 | lat1 lon1 | lat2 lon2
 *
 * 0 0 | lat0 lon0 | lat1 lon1
 *
 * as it is done now. Advantage: A single shift is only necessary to make it less precise. Or:
 *
 * lat2 lon2 | lat1 lon1 | lat0 lon0
 *
 * 0 0 | lat1 lon1 | lat0 lon0
 *
 * Advantage: the bit mask to get lat0 lon0 is simple: 000..0011 and independent of the precision!
 * But when stored e.g. as int one would need to (left) shift several times if precision is only
 * 3bits.
 *
 * @author Peter Karich, info@jetsli.de
 */
public class SpatialKeyAlgo {

    // private int factorForPrecision;
    // normally +90 degree (south to nord)
    private double maxLatI;
    // normally -90 degree
    private double minLatI;
    // normally +180 degree (parallel to equator)
    private int maxLonI;
    // normally -180 degree
    private int minLonI;
    private int iterations;
    private long initialBits;

    /**
     * @param precision specifies how many positions after the decimal point are significant.
     */
    public SpatialKeyAlgo initFromPrecision(int precision) {
        // 360 / 2^(allBits/2) = 1/precision = x
        double x = 1d / Math.pow(10, precision);
        double allbits = Math.round(2 * Math.log(360 / x) / Math.log(2));
        // add 4 bits to make the last position correct
        myinit(Math.min((int) allbits + 4 * 2, 64));
        setPrecision(precision);
        return this;
    }

    public SpatialKeyAlgo(int allBits) {
        myinit(allBits);
    }

    private void myinit(int allBits) {
        if (allBits > 64)
            throw new IllegalStateException("allBits is too big and does not fit into 8 bytes");

        if (allBits <= 0)
            throw new IllegalStateException("allBits must be positive");

        if ((allBits & 0x1) == 1)
            throw new IllegalStateException("allBits needs to be even to use the same amount for lat and lon");

        iterations = allBits / 2;
        initialBits = 1L << (allBits - 1);
        setPrecision(11930460);
    }

    public int getExactPrecision() {
        // 360 / 2^(allBits/2) = 1/precision
        int p = (int) (Math.pow(2, iterations) / 360);
        // no rounding error
        p++;
        return (int) Math.log10(p);
    }

    protected void setPrecision(int factorForPrecision) {
        // this.factorForPrecision = factorForPrecision;
        minLatI = -90;
        maxLatI = 90;
        minLonI = -180;
        maxLonI = 180;
    }

    public long encode(CoordTrig coord) {
        return encode(coord.lat, coord.lon);
    }

    /**
     * Take latitude and longitude as input.
     *
     * @return the spatial key
     */
    public final long encode(double latI, double lonI) {
        // PERFORMANCE: int operations would be faster than double (for further comparison etc)
        // but we would need 'long' because 'int factorForPrecision' is not enough (problem: coord!=decode(encode(coord)) see testBijection)
        // and 'long'-ops are more expensive than double (at least on 32bit systems)
        long hash = 0;
        double minLat = minLatI;
        double maxLat = maxLatI;
        double minLon = minLonI;
        double maxLon = maxLonI;
        int i = 0;
        while (true) {
            if (minLat < maxLat) {
                double midLat = (minLat + maxLat) / 2;
                if (latI > midLat) {
                    hash |= 1;
                    minLat = midLat;
                } else
                    maxLat = midLat;
            }

            hash <<= 1;
            if (minLon < maxLon) {
                double midLon = (minLon + maxLon) / 2;
                if (lonI > midLon) {
                    hash |= 1;
                    minLon = midLon;
                } else
                    maxLon = midLon;
            }

            i++;
            if (i < iterations)
                hash <<= 1;
            else
                break;
        }
        return hash;
    }

    /**
     * This method returns latitude and longitude via latLon - calculated from specified spatialKey
     *
     * @param spatialKey is the input
     */
    public final void decode(long spatialKey, CoordTrig latLon) {
        // use the value in the middle => start from "min" use "max" as initial step-size
        double midLat = maxLatI;
        double midLon = maxLonI;

        long bits = initialBits;
        double lat = minLatI;
        double lon = minLonI;
        while (true) {
            if ((spatialKey & bits) != 0)
                lat += midLat;

            midLat /= 2;
            bits >>>= 1;

            if ((spatialKey & bits) != 0)
                lon += midLon;

            midLon /= 2;
            if (bits != 0) {
                bits >>>= 1;
            } else {
                // stable rounding - see testBijection
                lat += midLat;
                lon += midLon;
                break;
            }
        }

        latLon.lat = lat;
        latLon.lon = lon;
    }
//    protected int toLong(double d) {
//        return (int) (d * factorForPrecision);
//    }
//    private double toDouble(int val) {
//        return (double) val / factorForPrecision;
//    }
}
