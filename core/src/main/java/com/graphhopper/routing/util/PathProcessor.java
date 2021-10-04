package com.graphhopper.routing.util;

import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.PointList;

// ORS-GH MOD - new class
// TODO ORS: why is this class needed? How does GH deal with this? See Path.forEveryEdge
public interface PathProcessor {
    PathProcessor DEFAULT = new DefaultPathProcessor();

    void processPathEdge(EdgeIteratorState edge, PointList geom);

    PointList processPoints(PointList points);
}
