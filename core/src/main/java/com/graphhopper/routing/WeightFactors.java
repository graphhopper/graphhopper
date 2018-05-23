package com.graphhopper.routing;

import com.graphhopper.util.EdgeIteratorState;

public interface WeightFactors {
    double getFactor(EdgeIteratorState edgeState, boolean reverse);
}
