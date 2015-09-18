package com.graphhopper.routing.util;

import com.graphhopper.util.shapes.GHPoint;

/**
 *
 * @author Peter Karich
 */
public class Point {

    public final double lat;
    public final double lon;

    public Point(double lat, double lon) {
        this.lat = lat;
        this.lon = lon;
    }

    public GHPoint toGHPoint() {
        return new GHPoint(lat, lon);
    }

    @Override
    public String toString() {
        return lat + ", " + lon;
    }
}
