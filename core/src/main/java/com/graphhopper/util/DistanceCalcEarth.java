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
package com.graphhopper.util;

import com.graphhopper.util.shapes.BBox;
import com.graphhopper.util.shapes.GHPoint;

import static java.lang.Math.*;

/**
 * @author Peter Karich
 */
public class DistanceCalcEarth implements DistanceCalc {
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
    public final static double KM_MILE = 1.609344;
    public final static double METERS_PER_DEGREE = C / 360.0;
    public static final DistanceCalc DIST_EARTH = new DistanceCalcEarth();

    /**
     * Calculates distance of (from, to) in meter.
     * <p>
     * http://en.wikipedia.org/wiki/Haversine_formula a = sin²(Δlat/2) +
     * cos(lat1).cos(lat2).sin²(Δlong/2) c = 2.atan2(√a, √(1−a)) d = R.c
     */
    @Override
    public double calcDist(double fromLat, double fromLon, double toLat, double toLon) {
        double normedDist = calcNormalizedDist(fromLat, fromLon, toLat, toLon);
        return R * 2 * asin(sqrt(normedDist));
    }

    /**
     * This implements a rather quick solution to calculate 3D distances on earth using euclidean
     * geometry mixed with Haversine formula used for the on earth distance. The haversine formula makes
     * not so much sense as it is only important for large distances where then the rather smallish
     * heights would becomes negligible.
     */
    @Override
    public double calcDist3D(double fromLat, double fromLon, double fromHeight,
                             double toLat, double toLon, double toHeight) {
        double eleDelta = hasElevationDiff(fromHeight, toHeight) ? (toHeight - fromHeight) : 0;
        double len = calcDist(fromLat, fromLon, toLat, toLon);
        return Math.sqrt(eleDelta * eleDelta + len * len);
    }

    @Override
    public double calcDenormalizedDist(double normedDist) {
        return R * 2 * asin(sqrt(normedDist));
    }

    /**
     * Returns the specified length in normalized meter.
     */
    @Override
    public double calcNormalizedDist(double dist) {
        double tmp = sin(dist / 2 / R);
        return tmp * tmp;
    }

    @Override
    public double calcNormalizedDist(double fromLat, double fromLon, double toLat, double toLon) {
        double sinDeltaLat = sin(toRadians(toLat - fromLat) / 2);
        double sinDeltaLon = sin(toRadians(toLon - fromLon) / 2);
        return sinDeltaLat * sinDeltaLat
                + sinDeltaLon * sinDeltaLon * cos(toRadians(fromLat)) * cos(toRadians(toLat));
    }

    /**
     * Circumference of the earth at different latitudes (breitengrad)
     */
    public double calcCircumference(double lat) {
        return 2 * PI * R * cos(toRadians(lat));
    }

    public boolean isDateLineCrossOver(double lon1, double lon2) {
        return abs(lon1 - lon2) > 180.0;
    }

    @Override
    public BBox createBBox(double lat, double lon, double radiusInMeter) {
        if (radiusInMeter <= 0)
            throw new IllegalArgumentException("Distance must not be zero or negative! " + radiusInMeter + " lat,lon:" + lat + "," + lon);

        // length of a circle at specified lat / dist
        double dLon = (360 / (calcCircumference(lat) / radiusInMeter));

        // length of a circle is independent of the longitude
        double dLat = (360 / (DistanceCalcEarth.C / radiusInMeter));

        // Now return bounding box in coordinates
        return new BBox(lon - dLon, lon + dLon, lat - dLat, lat + dLat);
    }

    @Override
    public double calcNormalizedEdgeDistance(double r_lat_deg, double r_lon_deg,
                                             double a_lat_deg, double a_lon_deg,
                                             double b_lat_deg, double b_lon_deg) {
        double shrinkFactor = calcShrinkFactor(a_lat_deg, b_lat_deg);

        double a_lat = a_lat_deg;
        double a_lon = a_lon_deg * shrinkFactor;

        double b_lat = b_lat_deg;
        double b_lon = b_lon_deg * shrinkFactor;

        double r_lat = r_lat_deg;
        double r_lon = r_lon_deg * shrinkFactor;

        double delta_lon = b_lon - a_lon;
        double delta_lat = b_lat - a_lat;

        if (delta_lat == 0)
            // special case: horizontal edge
            return calcNormalizedDist(a_lat_deg, r_lon_deg, r_lat_deg, r_lon_deg);

        if (delta_lon == 0)
            // special case: vertical edge
            return calcNormalizedDist(r_lat_deg, a_lon_deg, r_lat_deg, r_lon_deg);

        double norm = delta_lon * delta_lon + delta_lat * delta_lat;
        double factor = ((r_lon - a_lon) * delta_lon + (r_lat - a_lat) * delta_lat) / norm;

        // x,y is projection of r onto segment a-b
        double c_lon = a_lon + factor * delta_lon;
        double c_lat = a_lat + factor * delta_lat;
        return calcNormalizedDist(c_lat, c_lon / shrinkFactor, r_lat_deg, r_lon_deg);
    }

    @Override
    public double calcNormalizedEdgeDistance3D(double r_lat_deg, double r_lon_deg, double r_ele_m,
                                               double a_lat_deg, double a_lon_deg, double a_ele_m,
                                               double b_lat_deg, double b_lon_deg, double b_ele_m) {
        if (Double.isNaN(r_ele_m) || Double.isNaN(a_ele_m) || Double.isNaN(b_ele_m))
            return calcNormalizedEdgeDistance(r_lat_deg, r_lon_deg, a_lat_deg, a_lon_deg, b_lat_deg, b_lon_deg);

        double shrinkFactor = calcShrinkFactor(a_lat_deg, b_lat_deg);

        double a_lat = a_lat_deg;
        double a_lon = a_lon_deg * shrinkFactor;
        double a_ele = a_ele_m / METERS_PER_DEGREE;

        double b_lat = b_lat_deg;
        double b_lon = b_lon_deg * shrinkFactor;
        double b_ele = b_ele_m / METERS_PER_DEGREE;

        double r_lat = r_lat_deg;
        double r_lon = r_lon_deg * shrinkFactor;
        double r_ele = r_ele_m / METERS_PER_DEGREE;

        double delta_lon = b_lon - a_lon;
        double delta_lat = b_lat - a_lat;
        double delta_ele = b_ele - a_ele;

        double norm = delta_lon * delta_lon + delta_lat * delta_lat + delta_ele * delta_ele;
        double factor = ((r_lon - a_lon) * delta_lon + (r_lat - a_lat) * delta_lat + (r_ele - a_ele) * delta_ele) / norm;
        if (Double.isNaN(factor)) factor = 0;

        // x,y,z is projection of r onto segment a-b
        double c_lon = a_lon + factor * delta_lon;
        double c_lat = a_lat + factor * delta_lat;
        double c_ele_m = (a_ele + factor * delta_ele) * METERS_PER_DEGREE;
        return calcNormalizedDist(c_lat, c_lon / shrinkFactor, r_lat_deg, r_lon_deg) + calcNormalizedDist(r_ele_m - c_ele_m);
    }

    double calcShrinkFactor(double a_lat_deg, double b_lat_deg) {
        return cos(toRadians((a_lat_deg + b_lat_deg) / 2));
    }

    @Override
    public GHPoint calcCrossingPointToEdge(double r_lat_deg, double r_lon_deg,
                                           double a_lat_deg, double a_lon_deg,
                                           double b_lat_deg, double b_lon_deg) {
        double shrinkFactor = calcShrinkFactor(a_lat_deg, b_lat_deg);
        double a_lat = a_lat_deg;
        double a_lon = a_lon_deg * shrinkFactor;

        double b_lat = b_lat_deg;
        double b_lon = b_lon_deg * shrinkFactor;

        double r_lat = r_lat_deg;
        double r_lon = r_lon_deg * shrinkFactor;

        double delta_lon = b_lon - a_lon;
        double delta_lat = b_lat - a_lat;

        if (delta_lat == 0)
            // special case: horizontal edge
            return new GHPoint(a_lat_deg, r_lon_deg);

        if (delta_lon == 0)
            // special case: vertical edge        
            return new GHPoint(r_lat_deg, a_lon_deg);

        double norm = delta_lon * delta_lon + delta_lat * delta_lat;
        double factor = ((r_lon - a_lon) * delta_lon + (r_lat - a_lat) * delta_lat) / norm;

        // x,y is projection of r onto segment a-b
        double c_lon = a_lon + factor * delta_lon;
        double c_lat = a_lat + factor * delta_lat;
        return new GHPoint(c_lat, c_lon / shrinkFactor);
    }

    @Override
    public boolean validEdgeDistance(double r_lat_deg, double r_lon_deg,
                                     double a_lat_deg, double a_lon_deg,
                                     double b_lat_deg, double b_lon_deg) {
        double shrinkFactor = calcShrinkFactor(a_lat_deg, b_lat_deg);
        double a_lat = a_lat_deg;
        double a_lon = a_lon_deg * shrinkFactor;

        double b_lat = b_lat_deg;
        double b_lon = b_lon_deg * shrinkFactor;

        double r_lat = r_lat_deg;
        double r_lon = r_lon_deg * shrinkFactor;

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
    public GHPoint projectCoordinate(double latInDeg, double lonInDeg, double distanceInMeter, double headingClockwiseFromNorth) {
        double angularDistance = distanceInMeter / R;

        double latInRadians = Math.toRadians(latInDeg);
        double lonInRadians = Math.toRadians(lonInDeg);
        double headingInRadians = Math.toRadians(headingClockwiseFromNorth);

        // This formula is taken from: http://williams.best.vwh.net/avform.htm#LL (http://www.movable-type.co.uk/scripts/latlong.html -> https://github.com/chrisveness/geodesy MIT)
        // θ=heading,δ=distance,φ1=latInRadians
        // lat2 = asin( sin φ1 ⋅ cos δ + cos φ1 ⋅ sin δ ⋅ cos θ )     
        // lon2 = λ1 + atan2( sin θ ⋅ sin δ ⋅ cos φ1, cos δ − sin φ1 ⋅ sin φ2 )
        double projectedLat = Math.asin(Math.sin(latInRadians) * Math.cos(angularDistance)
                + Math.cos(latInRadians) * Math.sin(angularDistance) * Math.cos(headingInRadians));
        double projectedLon = lonInRadians + Math.atan2(Math.sin(headingInRadians) * Math.sin(angularDistance) * Math.cos(latInRadians),
                Math.cos(angularDistance) - Math.sin(latInRadians) * Math.sin(projectedLat));

        projectedLon = (projectedLon + 3 * Math.PI) % (2 * Math.PI) - Math.PI; // normalise to -180..+180°

        projectedLat = Math.toDegrees(projectedLat);
        projectedLon = Math.toDegrees(projectedLon);

        return new GHPoint(projectedLat, projectedLon);
    }

    @Override
    public GHPoint intermediatePoint(double f, double lat1, double lon1, double lat2, double lon2) {
        double lat1radians = Math.toRadians(lat1);
        double lon1radians = Math.toRadians(lon1);
        double lat2radians = Math.toRadians(lat2);
        double lon2radians = Math.toRadians(lon2);

        // This formula is taken from: (http://www.movable-type.co.uk/scripts/latlong.html -> https://github.com/chrisveness/geodesy MIT)

        double deltaLat = lat2radians - lat1radians;
        double deltaLon = lon2radians - lon1radians;
        double cosLat1 = cos(lat1radians);
        double cosLat2 = cos(lat2radians);
        double sinHalfDeltaLat = sin(deltaLat / 2);
        double sinHalfDeltaLon = sin(deltaLon / 2);

        double a = sinHalfDeltaLat * sinHalfDeltaLat + cosLat1 * cosLat2 * sinHalfDeltaLon * sinHalfDeltaLon;
        double angularDistance =  2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
        double sinDistance = sin(angularDistance);

        if (angularDistance == 0) return new GHPoint(lat1, lon1);

        double A = Math.sin((1-f)*angularDistance) / sinDistance;
        double B = Math.sin(f*angularDistance) / sinDistance;

        double x = A * cosLat1 * cos(lon1radians) + B * cosLat2 * cos(lon2radians);
        double y = A * cosLat1 * sin(lon1radians) + B * cosLat2 * sin(lon2radians);
        double z = A * sin(lat1radians) + B * sin(lat2radians);

        double midLat = Math.toDegrees(Math.atan2(z, Math.sqrt(x*x + y*y)));
        double midLon = Math.toDegrees(Math.atan2(y, x));

        return new GHPoint(midLat, midLon);
    }

    @Override
    public final double calcDistance(PointList pointList) {
        double prevLat = Double.NaN;
        double prevLon = Double.NaN;
        double prevEle = Double.NaN;
        double dist = 0;
        for (int i = 0; i < pointList.size(); i++) {
            if (i > 0) {
                if (pointList.is3D())
                    dist += calcDist3D(prevLat, prevLon, prevEle, pointList.getLat(i), pointList.getLon(i), pointList.getEle(i));
                else
                    dist += calcDist(prevLat, prevLon, pointList.getLat(i), pointList.getLon(i));
            }

            prevLat = pointList.getLat(i);
            prevLon = pointList.getLon(i);
            if (pointList.is3D())
                prevEle = pointList.getEle(i);
        }
        return dist;
    }

    @Override
    public boolean isCrossBoundary(double lon1, double lon2) {
        return abs(lon1 - lon2) > 300;
    }

    protected boolean hasElevationDiff(double a, double b) {
        return a != b && !Double.isNaN(a) && !Double.isNaN(b);
    }

    @Override
    public String toString() {
        return "EXACT";
    }
}
