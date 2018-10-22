package com.graphhopper.routing.weighting;

import com.graphhopper.util.EdgeIteratorState;

public interface ScriptInterface {
    double getMillisFactor(EdgeIteratorState edge, boolean reverse);
}
