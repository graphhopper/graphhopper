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
package com.graphhopper.util;

import com.graphhopper.util.shapes.BBox;
import static java.lang.Math.*;

/**
 * Calculates the distance of two points or one point and an edge on earth. Allow subclasses to
 * implement less or more precise calculations (so: do not use static methods!).
 * 
 * http://en.wikipedia.org/wiki/Haversine_formula
 *
 * @author Peter Karich,
 */
public class DistanceCalc {

    /**
     * mean radius of the earth
     */
    public final static double R = 6371000; // m
    /**
     * Radius of the earth at equator
     */
    public final static double R_EQ = 6378137; // m
    /**
     * Circumference of the earth
     */
    public final static double C = 2 * PI * R;

    /**
     * Calculates distance of (from, to) in meter.
     *
     * http://en.wikipedia.org/wiki/Haversine_formula a = sin²(Δlat/2) +
     * cos(lat1).cos(lat2).sin²(Δlong/2) c = 2.atan2(√a, √(1−a)) d = R.c
     */
    public double calcDist(double fromLat, double fromLon, double toLat, double toLon) {
        double dLat = toRadians(toLat - fromLat);
        double dLon = toRadians(toLon - fromLon);
        double normedDist = sin(dLat / 2) * sin(dLat / 2)
                + cos(toRadians(fromLat)) * cos(toRadians(toLat)) * sin(dLon / 2) * sin(dLon / 2);
        return R * 2 * asin(sqrt(normedDist));
    }

    public double denormalizeDist(double normedDist) {
        return R * 2 * asin(sqrt(normedDist));
    }

    /**
     * in meter
     */
    public double normalizeDist(double dist) {
        double tmp = sin(dist / 2 / R);
        return tmp * tmp;
    }

    /**
     * Calculates in normalized meter
     */
    public double calcNormalizedDist(double fromLat, double fromLon, double toLat, double toLon) {
        double dLat = toRadians(toLat - fromLat);
        double dLon = toRadians(toLon - fromLon);
        return sin(dLat / 2) * sin(dLat / 2)
                + cos(toRadians(fromLat)) * cos(toRadians(toLat)) * sin(dLon / 2) * sin(dLon / 2);
    }

    /**
     * @deprecated hmmh seems to be lot slower than calcDist
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
        return (int) C >> bit;
    }

    public boolean isDateLineCrossOver(double lon1, double lon2) {
        return abs(lon1 - lon2) > 180.0;
    }

    public BBox createBBox(double lat, double lon, double radiusInMeter) {
        if (radiusInMeter <= 0)
            throw new IllegalArgumentException("Distance cannot be 0 or negative! " + radiusInMeter + " lat,lon:" + lat + "," + lon);

        // length of a circle at specified lat / dist
        double dLon = (360 / (calcCircumference(lat) / radiusInMeter));

        // length of a circle is independent of the longitude
        double dLat = (360 / (DistanceCalc.C / radiusInMeter));

        // Now return bounding box in coordinates
        return new BBox(lon - dLon, lon + dLon, lat - dLat, lat + dLat);
    }

    /**
     * This method calculates the distance from r to edge g=(a to b) where the crossing point is t
     *
     * @return the normalized distance in meter
     */
    public double calcNormalizedEdgeDistance(double r_lat, double r_lon,
            double a_lat, double a_lon,
            double b_lat, double b_lon) {
        // x <=> lon
        // y <=> lat
        double dY_a = a_lat - b_lat;
        if (dY_a == 0)
            // special case: horizontal edge
            return calcNormalizedDist(a_lat, r_lon, r_lat, r_lon);

        double dX_a = a_lon - b_lon;
        if (dX_a == 0)
            // special case: vertical edge
            return calcNormalizedDist(r_lat, a_lon, r_lat, r_lon);

        double m = dY_a / dX_a;
        double n = a_lat - m * a_lon;
        double m_i = 1 / m;
        double n_s = r_lat + m_i * r_lon;
        // g should cross s => t=(t_x,t_y)
        // m + m_i cannot get 0
        double t_x = (n_s - n) / (m + m_i);
        double t_y = m * t_x + n;
        return calcNormalizedDist(r_lat, r_lon, t_y, t_x);
    }

    /**
     * This method decides case 1: if we should use distance(r to edge) where r=(lat,lon) or case 2:
     * min(distance(r to a), distance(r to b)) where edge=(a to b)
     */
    // case 1:
    //   r
    //  . 
    // a-------b
    //    
    // case 2:
    // r
    //  .
    //    a-------b
    public boolean validEdgeDistance(double r_lat, double r_lon,
            double a_lat, double a_lon,
            double b_lat, double b_lon) {
        double ar_x = r_lon - a_lon;
        double ar_y = r_lat - a_lat;
        double ab_x = b_lon - a_lon;
        double ab_y = b_lat - a_lat;
        double ab_ar = ar_x * ab_x + ar_y * ab_y;

        double rb_x = b_lon - r_lon;
        double rb_y = b_lat - r_lat;
        double ab_rb = rb_x * ab_x + rb_y * ab_y;

        // calculate the exact degree alpha(ar, ab) and beta(rb,ab) if it is case 1 then both angles are <= 90°
        // double ab_ar_norm = Math.sqrt(ar_x * ar_x + ar_y * ar_y) * Math.sqrt(ab_x * ab_x + ab_y * ab_y);
        // double ab_rb_norm = Math.sqrt(rb_x * rb_x + rb_y * rb_y) * Math.sqrt(ab_x * ab_x + ab_y * ab_y);
        // return Math.acos(ab_ar / ab_ar_norm) <= Math.PI / 2 && Math.acos(ab_rb / ab_rb_norm) <= Math.PI / 2;
        return ab_ar > 0 && ab_rb > 0;
    }

    @Override
    public String toString() {
        return "EXACT";
    }
}
