package com.graphhopper.routing;

/**
 * An interface with which you can trigger a heuristic change of the underlying algorithm.
 */
public interface RecalculationHook {

    int getVisitedNodes();
}
