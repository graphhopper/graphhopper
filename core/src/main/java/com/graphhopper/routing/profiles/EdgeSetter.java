package com.graphhopper.routing.profiles;

import com.graphhopper.util.EdgeIteratorState;

public interface EdgeSetter {
    void set(EdgeIteratorState edgeState, EncodedValue value, Object object);
}
