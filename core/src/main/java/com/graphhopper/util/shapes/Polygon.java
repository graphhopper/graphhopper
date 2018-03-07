package com.graphhopper.util.shapes;

import java.util.ArrayList;
import java.util.List;

public class Polygon implements Shape {

    private final double[] lat;
    private final double[] lon;

    private double minLat;
    private double minLon;
    private double maxLat;
    private double maxLon;

    private final double epsilon;
    private static final double GROWN_FACTOR = 0.003;

    public Polygon(double[] lat, double[] lon) {
        if (lat.length != lon.length) {
            throw new IllegalArgumentException(
                    "Points must be of equal length but was " + lat.length + " vs. " + lon.length);
        }
        if (lat.length == 0) {
            throw new IllegalArgumentException("Points must not be empty");
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

        minLat -= GROWN_FACTOR;
        minLon -= GROWN_FACTOR;
        maxLat += GROWN_FACTOR;
        maxLon += GROWN_FACTOR;

        epsilon = (maxLat - minLat) / 10;
    }

    @Override
    public boolean intersect(Shape o) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean contains(double lat, double lon) {
        if (lat < minLat || lat > maxLat || lon < minLon || lon > maxLon) {
            return false;
        }

        double rayStartLat = maxLat - (minLat / 2);
        double rayStartLon = minLon - epsilon;

        boolean inside = false;
        int len = this.lat.length;
        for (int i = 0; i < len; i++) {
            if (edgesAreIntersecting(rayStartLon, rayStartLat, lon, lat, this.lon[i], this.lat[i],
                    this.lon[(i + 1) % len], this.lat[(i + 1) % len]))
                inside = !inside;
        }
        return inside;
    }

    private boolean edgesAreIntersecting(double v1x1, double v1y1, double v1x2, double v1y2, double v2x1, double v2y1,
                                         double v2x2, double v2y2) {

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
        // if is on the other side of it. We insert (x1,y1) and (x2,y2) of
        // vector
        // 2 into the equation above.
        d1 = (a1 * v2x1) + (b1 * v2y1) + c1;
        d2 = (a1 * v2x2) + (b1 * v2y2) + c1;

        // If d1 and d2 both have the same sign, they are both on the same side
        // of our line 1 and in that case no intersection is possible. Careful,
        // 0 is a special case, that's why we don't test ">=" and "<=",
        // but "<" and ">".
        if (d1 > 0 && d2 > 0)
            return false;
        if (d1 < 0 && d2 < 0)
            return false;

        // The fact that vector 2 intersected the infinite line 1 above doesn't
        // mean it also intersects the vector 1. Vector 1 is only a subset of
        // that
        // infinite line 1, so it may have intersected that line before the
        // vector
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
        if (d1 > 0 && d2 > 0)
            return false;
        if (d1 < 0 && d2 < 0)
            return false;

        // If we get here, only two possibilities are left. Either the two
        // vectors intersect in exactly one point or they are collinear, which
        // means they intersect in any number of points from zero to infinite.
        return !((a1 * b2) - (a2 * b1) == 0);
    }

    @Override
    public boolean contains(Shape s) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public BBox getBounds() {
        return new BBox(minLon, maxLon, minLat, maxLat);
    }

    @Override
    public GHPoint getCenter() {
        return new GHPoint((maxLat + minLat) / 2, (maxLon + minLon) / 2);
    }



    @Override
    public double calculateArea() {
        // TODO Auto-generated method stub
        return 0;
    }

    public static Polygon parsePoints(String pointsStr) {
        String[] arr = pointsStr.split(",");

        List<Double> listLats = new ArrayList<>();
        List<Double> listLons = new ArrayList<>();
        for (int i = 0; i < arr.length; i++) {
            if (i % 2 == 0) {
                listLats.add(Double.parseDouble(arr[i]));
            } else {
                listLons.add(Double.parseDouble(arr[i]));
            }
        }

        double[] lats = new double[listLats.size()];
        double[] lons = new double[listLons.size()];

        for (int i = 0; i < listLats.size(); i++) {
            lats[i] = listLats.get(i);
        }
        for (int i = 0; i < listLons.size(); i++) {
            lons[i] = listLons.get(i);
        }

        return new Polygon(lats, lons);
    }

}