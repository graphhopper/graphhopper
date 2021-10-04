package com.graphhopper.routing.util;

import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.PointList;

// ORS-GH MOD
public class DefaultPathProcessor implements PathProcessor {
    public DefaultPathProcessor() {
    }

    public void processPathEdge(EdgeIteratorState edge, PointList geom) {
    }

    public PointList processPoints(PointList points) {
        return points;
    }
}
