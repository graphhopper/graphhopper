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

package com.graphhopper.client.tools;

import static java.lang.Math.*;

public class Distance {
    public final static double R = 6371000; // m

    public static double calcDist(double fromLat, double fromLon, double toLat, double toLon) {
        double normedDist = calcNormalizedDist(fromLat, fromLon, toLat, toLon);
        return R * 2 * asin(sqrt(normedDist));
    }

    public static boolean validEdgeDistance(double r_lat_deg, double r_lon_deg,
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
        return ab_ar > 0 && ab_rb > 0;
    }

    public static double calcNormalizedEdgeDistance(double r_lat_deg, double r_lon_deg,
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

    public static double calcNormalizedDist(double fromLat, double fromLon, double toLat, double toLon) {
        double sinDeltaLat = sin(toRadians(toLat - fromLat) / 2);
        double sinDeltaLon = sin(toRadians(toLon - fromLon) / 2);
        return sinDeltaLat * sinDeltaLat
                + sinDeltaLon * sinDeltaLon * cos(toRadians(fromLat)) * cos(toRadians(toLat));
    }

    private static double calcShrinkFactor(double a_lat_deg, double b_lat_deg) {
        return cos(toRadians((a_lat_deg + b_lat_deg) / 2));
    }
}