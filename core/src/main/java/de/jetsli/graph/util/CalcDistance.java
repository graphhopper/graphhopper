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

import static java.lang.Math.*;
//import static org.apache.commons.math3.util.FastMath.*;
import de.jetsli.graph.util.shapes.BBox;
import java.util.Arrays;

/**
 * Calculates the distance of two points on earth.
 *
 * @author Peter Karich, info@jetsli.de
 */
public class CalcDistance {

    /**
     * mean radius of the earth
     */
    public final static double R = 6371; // km
    /**
     * Radius of the earth at equator
     */
    public final static double R_EQ = 6378.137; // km
    /**
     * Circumference of the earth
     */
    public final static double C = 2 * PI * R;

    /**
     * http://en.wikipedia.org/wiki/Haversine_formula a = sin²(Δlat/2) +
     * cos(lat1).cos(lat2).sin²(Δlong/2) c = 2.atan2(√a, √(1−a)) d = R.c
     */
    public double calcDistKm(double fromLat, double fromLon, double toLat, double toLon) {
        double dLat = toRadians(toLat - fromLat);
        double dLon = toRadians(toLon - fromLon);
        double normedDist = sin(dLat / 2) * sin(dLat / 2)
                + cos(toRadians(fromLat)) * cos(toRadians(toLat)) * sin(dLon / 2) * sin(dLon / 2);
        return R * 2 * asin(sqrt(normedDist));
    }

    public double denormalizeDist(double normedDist) {
        return R * 2 * asin(sqrt(normedDist));
    }

    public double normalizeDist(double dist) {
        double tmp = sin(dist / 2 / R);
        return tmp * tmp;
    }

    public double calcNormalizedDist(double fromLat, double fromLon, double toLat, double toLon) {
        double dLat = toRadians(toLat - fromLat);
        double dLon = toRadians(toLon - fromLon);
        return sin(dLat / 2) * sin(dLat / 2)
                + cos(toRadians(fromLat)) * cos(toRadians(toLat)) * sin(dLon / 2) * sin(dLon / 2);
    }

    /**
     * @deprecated hmmh seems to be lot slower than calcDistKm
     */
    public double calcCartesianDist(double fromLat, double fromLon, double toLat, double toLon) {
        fromLat = toRadians(fromLat);
        fromLon = toRadians(fromLon);

        double tmp = cos(fromLat);
        double x1 = tmp * cos(fromLon);
        double y1 = tmp * sin(fromLon);
        double z1 = sin(fromLat);

        toLat = toRadians(toLat);
        toLon = toRadians(toLon);
        tmp = cos(toLat);
        double x2 = tmp * cos(toLon);
        double y2 = tmp * sin(toLon);
        double z2 = sin(toLat);

        double dx = x1 - x2;
        double dy = y1 - y2;
        double dz = z1 - z2;
        return R * sqrt(dx * dx + dy * dy + dz * dz);
        // now make quadratic stuff faster:
//        dx = abs(dx);
//        dy = abs(dy);
//        int mn = (int) min(dx, dy);
//        return dx + dy - (mn >> 1) - (mn >> 2) + (mn >> 4);
    }

    void calcBearing() {
        // http://stackoverflow.com/questions/2232911/why-is-this-bearing-calculation-so-inacurate
    }

    /**
     * Circumference of the earth at different latitudes (breitengrad)
     */
    public double calcCircumference(double lat) {
        return 2 * PI * R * cos(toRadians(lat));
    }

    public double calcSpatialKeyMaxDist(int bit) {
        bit = bit / 2 + 1;
        return ((int) (C * 1000) >> bit) / 1000d;
    }
    // maximum distance necessary for a spatial key is 2^16 = 65536 kilometres which is the first 
    // distance higher than C => we can multiple the distance of type double to make the integer 
    // value more precise        
    private static final int MAX_DIST = 65536;
    private static final int[] arr = new int[64];

    static {
        // sort ascending distances
        int i = arr.length - 1;
        arr[i] = (int) (round(C * MAX_DIST) / 2);
        for (; i > 0; i--) {
            arr[i - 1] = arr[i] / 2;
        }
    }

    /**
     * @param dist distance in kilometers
     *
     * running time is O(1) => at maximum 6 + 3 integer comparisons
     *
     * @return the bit position i of a spatial key, where dist <= C / 2^i
     */
    public int distToSpatialKeyLatBit(double dist) {
        if (dist > CalcDistance.C / 4)
            return 0;
        if (dist < 0)
            return -1;

        int distInt = ((int) round(dist * MAX_DIST));
        int bitPos = Arrays.binarySearch(arr, distInt);

        // negative if located between two distances
        if (bitPos < 0)
            bitPos = -bitPos - 1;

        // * 2 => because one bit for latitude and one for longitude
        return (63 - bitPos) * 2;
    }

//    public void toCart(double lat, double lon, FloatCart2D result) {
//        lat = toRadians(lat);
//        lon = toRadians(lon);
//        result.x = (float) (R * cos(lat) * cos(lon));
//        result.y = (float) (R * cos(lat) * sin(lon));
//    }
    public boolean isDateLineCrossOver(double lon1, double lon2) {
        return abs(lon1 - lon2) > 180.0;
    }

    public BBox createBBox(double lat, double lon, double radiusInKm) {
        if (radiusInKm <= 0)
            throw new IllegalArgumentException("Distance cannot be 0 or negative! " + radiusInKm + " lat,lon:" + lat + "," + lon);

        // length of a circle at specified lat / dist
        double dLon = (360 / (calcCircumference(lat) / radiusInKm));

        // length of a circle is independent of the longitude
        double dLat = (360 / (CalcDistance.C / radiusInKm));

        // Now return bounding box in coordinates
        return new BBox(lon - dLon, lon + dLon, lat - dLat, lat + dLat);
    }
}
