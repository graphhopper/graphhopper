package com.graphhopper.routing.bwdcompat;

import com.graphhopper.util.EdgeIteratorState;

public interface SpeedAccessor {
    double getSpeed(EdgeIteratorState edge);
}
