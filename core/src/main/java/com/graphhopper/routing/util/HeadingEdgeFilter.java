package com.graphhopper.routing.util;

import com.graphhopper.core.util.PointList;
import com.graphhopper.core.util.shapes.GHPoint;
import com.graphhopper.util.AngleCalc;
import com.graphhopper.util.DistanceCalc;
import com.graphhopper.util.DistanceCalcEarth;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.FetchMode;

public class HeadingEdgeFilter implements EdgeFilter {

    private final double heading;
    private final DirectedEdgeFilter directedEdgeFilter;
    private final GHPoint pointNearHeading;

    public HeadingEdgeFilter(DirectedEdgeFilter directedEdgeFilter, double heading, GHPoint pointNearHeading) {
        this.directedEdgeFilter = directedEdgeFilter;
        this.heading = heading;
        this.pointNearHeading = pointNearHeading;
    }

    @Override
    public boolean accept(EdgeIteratorState edgeState) {
        final double tolerance = 30;
        // we only accept edges that are not too far away. It might happen that only far away edges match the heading
        // in which case we rather rely on the fallback snapping than return a match here.
        final double maxDistance = 20;
        double headingOfEdge = getHeadingOfGeometryNearPoint(edgeState, pointNearHeading, maxDistance);
        if (Double.isNaN(headingOfEdge))
            // this edge is too far away. we do not accept it.
            return false;
        // we accept the edge if either of the two directions roughly has the right heading
        return Math.abs(headingOfEdge - heading) < tolerance && directedEdgeFilter.accept(edgeState, false) ||
                Math.abs((headingOfEdge + 180) % 360 - heading) < tolerance && directedEdgeFilter.accept(edgeState, true);
    }

    /**
     * Calculates the heading (in degrees) of the given edge in fwd direction near the given point. If the point is
     * too far away from the edge (according to the maxDistance parameter) it returns Double.NaN.
     */
    static double getHeadingOfGeometryNearPoint(EdgeIteratorState edgeState, GHPoint point, double maxDistance) {
        final DistanceCalc calcDist = DistanceCalcEarth.DIST_EARTH;
        double closestDistance = Double.POSITIVE_INFINITY;
        PointList points = edgeState.fetchWayGeometry(FetchMode.ALL);
        int closestPoint = -1;
        for (int i = 1; i < points.size(); i++) {
            double fromLat = points.getLat(i - 1), fromLon = points.getLon(i - 1);
            double toLat = points.getLat(i), toLon = points.getLon(i);
            // the 'distance' between the point and an edge segment is either the vertical distance to the segment or
            // the distance to the closer one of the two endpoints. here we save one call to calcDist per segment,
            // because each endpoint appears in two segments (except the first and last).
            double distance = calcDist.validEdgeDistance(point.lat, point.lon, fromLat, fromLon, toLat, toLon)
                    ? calcDist.calcDenormalizedDist(calcDist.calcNormalizedEdgeDistance(point.lat, point.lon, fromLat, fromLon, toLat, toLon))
                    : calcDist.calcDist(fromLat, fromLon, point.lat, point.lon);
            if (i == points.size() - 1)
                distance = Math.min(distance, calcDist.calcDist(toLat, toLon, point.lat, point.lon));
            if (distance > maxDistance)
                continue;
            if (distance < closestDistance) {
                closestDistance = distance;
                closestPoint = i;
            }
        }
        if (closestPoint < 0)
            return Double.NaN;

        double fromLat = points.getLat(closestPoint - 1), fromLon = points.getLon(closestPoint - 1);
        double toLat = points.getLat(closestPoint), toLon = points.getLon(closestPoint);
        return AngleCalc.ANGLE_CALC.calcAzimuth(fromLat, fromLon, toLat, toLon);
    }
}
