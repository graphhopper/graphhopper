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
        if (directedEdgeFilter.accept(edgeState) && HeadingResolver.isHeadingNearlyParallel(edgeState, heading, pointNearHeading))
            return true;

        // now test heading against reversed edge
        if (!directedEdgeFilter.accept(edgeState = edgeState.detach(true)))
            return false;
        return HeadingResolver.isHeadingNearlyParallel(edgeState, heading, pointNearHeading);
    }
}
