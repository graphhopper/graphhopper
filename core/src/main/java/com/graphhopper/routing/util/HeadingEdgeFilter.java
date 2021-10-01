package com.graphhopper.routing.util;

import com.graphhopper.routing.HeadingResolver;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.shapes.GHPoint;

public class HeadingEdgeFilter implements EdgeFilter {

    private final double heading;
    private final EdgeFilter directedEdgeFilter;
    private final GHPoint pointNearHeading;

    public HeadingEdgeFilter(EdgeFilter directedEdgeFilter, double heading, GHPoint pointNearHeading) {
        this.directedEdgeFilter = directedEdgeFilter;
        this.heading = heading;
        this.pointNearHeading = pointNearHeading;
    }

    @Override
    public boolean accept(EdgeIteratorState edgeState) {
        final double tolerance = 30;
        double headingOfEdge = HeadingResolver.getHeadingOfGeometryNearPoint(edgeState, pointNearHeading);
        if (Double.isNaN(headingOfEdge))
            // this edge is too far away. we do not accept it.
            return false;
        // the edge is not directed. we accept the edge if either of the two directions roughly has the right heading
        return Math.abs(headingOfEdge - heading) < tolerance && directedEdgeFilter.accept(edgeState) ||
                Math.abs((headingOfEdge + 180) % 360 - heading) < tolerance && directedEdgeFilter.accept(edgeState.detach(true));
    }
}
