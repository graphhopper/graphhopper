package com.conveyal.gtfs.util;

import com.vividsolutions.jts.geom.GeometryFactory;

/**
 * The methods and classes in this package should eventually be part of a shared Conveyal library.
 */
public abstract class Util {

    public static GeometryFactory geometryFactory = new GeometryFactory();

    public static String human (int n) {
        if (n >= 1000000000) return String.format("%.1fG", n/1000000000.0);
        if (n >= 1000000) return String.format("%.1fM", n/1000000.0);
        if (n >= 1000) return String.format("%dk", n/1000);
        else return String.format("%d", n);
    }

}
