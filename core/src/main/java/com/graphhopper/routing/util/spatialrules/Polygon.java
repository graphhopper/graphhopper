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
package com.graphhopper.routing.util.spatialrules;

import com.graphhopper.util.shapes.GHPoint;

/**
 * TODO: Implement Shape?
 *
 * @author Robin Boldt
 */
public class Polygon {

    private final double[] lat;
    private final double[] lon;

    private double minLat;
    private double minLon;
    private double maxLat;
    private double maxLon;

    private final double epsilon;

    public Polygon(double[] lat, double[] lon) {
        if (lat.length != lon.length) {
            throw new IllegalStateException("Points must be of equal length");
        }
        this.lat = lat;
        this.lon = lon;

        for (int i = 0; i < lat.length; i++) {
            if (i == 0) {
                minLat = lat[i];
                maxLat = lat[i];
                minLon = lon[i];
                maxLon = lon[i];
            } else {
                if (lat[i] < minLat) {
                    minLat = lat[i];
                } else if (lat[i] > maxLat) {
                    maxLat = lat[i];
                }
                if (lon[i] < minLon) {
                    minLon = lon[i];
                } else if (lon[i] > maxLon) {
                    maxLon = lon[i];
                }
            }
        }

        epsilon = (maxLat - minLat) / 10;
    }

    public boolean contains(GHPoint point) {
        return contains(point.lat, point.lon);
    }

    /**
     * Implements the ray casting algorithm
     * Code is inspired from here: http://stackoverflow.com/a/218081/1548788
     *
     * @param lat
     * @param lon
     * @return true if point is inside polygon
     */
    public boolean contains(double lat, double lon) {
        if (lat < minLat || lat > maxLat || lon < minLon || lon > maxLon) {
            return false;
        }

        double rayStartLat = maxLat - (minLat / 2);
        double rayStartLon = minLon - epsilon;

        boolean inside = false;
        int len = this.lat.length;
        for (int i = 0; i < len; i++) {
            if (edgesAreIntersecting(rayStartLon, rayStartLat, lon, lat, this.lon[i], this.lat[i], this.lon[(i + 1) % len], this.lat[(i + 1) % len]))
                inside = !inside;
        }
        return inside;

    }

    private boolean edgesAreIntersecting(
            double v1x1, double v1y1, double v1x2, double v1y2,
            double v2x1, double v2y1, double v2x2, double v2y2
    ) {


        double d1, d2;
        double a1, a2, b1, b2, c1, c2;

        // Convert vector 1 to a line (line 1) of infinite length.
        // We want the line in linear equation standard form: A*x + B*y + C = 0
        // See: http://en.wikipedia.org/wiki/Linear_equation
        a1 = v1y2 - v1y1;
        b1 = v1x1 - v1x2;
        c1 = (v1x2 * v1y1) - (v1x1 * v1y2);

        // Every point (x,y), that solves the equation above, is on the line,
        // every point that does not solve it, is not. The equation will have a
        // positive result if it is on one side of the line and a negative one
        // if is on the other side of it. We insert (x1,y1) and (x2,y2) of vector
        // 2 into the equation above.
        d1 = (a1 * v2x1) + (b1 * v2y1) + c1;
        d2 = (a1 * v2x2) + (b1 * v2y2) + c1;

        // If d1 and d2 both have the same sign, they are both on the same side
        // of our line 1 and in that case no intersection is possible. Careful,
        // 0 is a special case, that's why we don't test ">=" and "<=",
        // but "<" and ">".
        if (d1 > 0 && d2 > 0) return false;
        if (d1 < 0 && d2 < 0) return false;

        // The fact that vector 2 intersected the infinite line 1 above doesn't
        // mean it also intersects the vector 1. Vector 1 is only a subset of that
        // infinite line 1, so it may have intersected that line before the vector
        // started or after it ended. To know for sure, we have to repeat the
        // the same test the other way round. We start by calculating the
        // infinite line 2 in linear equation standard form.
        a2 = v2y2 - v2y1;
        b2 = v2x1 - v2x2;
        c2 = (v2x2 * v2y1) - (v2x1 * v2y2);

        // Calculate d1 and d2 again, this time using points of vector 1.
        d1 = (a2 * v1x1) + (b2 * v1y1) + c2;
        d2 = (a2 * v1x2) + (b2 * v1y2) + c2;

        // Again, if both have the same sign (and neither one is 0),
        // no intersection is possible.
        if (d1 > 0 && d2 > 0) return false;
        if (d1 < 0 && d2 < 0) return false;

        // If we get here, only two possibilities are left. Either the two
        // vectors intersect in exactly one point or they are collinear, which
        // means they intersect in any number of points from zero to infinite.
        if ((a1 * b2) - (a2 * b1) == 0) return false;

        // If they are not collinear, they must intersect in exactly one point.
        return true;
    }

    public double getMinLat() {
        return minLat;
    }

    public double getMinLon() {
        return minLon;
    }

    public double getMaxLat() {
        return maxLat;
    }

    public double getMaxLon() {
        return maxLon;
    }
}
