package com.graphhopper.routing.bwdcompat;

import com.graphhopper.util.EdgeIteratorState;

public interface BoolAccessor {
    boolean get(EdgeIteratorState edge);
}
