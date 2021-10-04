package com.graphhopper.routing.util;

import com.graphhopper.util.EdgeIteratorState;

/**
 * Common interface for time-dependent speed calculation
 *
 * @author Hendrik Leuschner
 * @author Andrzej Oles
 */
public interface SpeedCalculator {
    double getSpeed(EdgeIteratorState edge, boolean reverse, long time);

    boolean isTimeDependent();
}
