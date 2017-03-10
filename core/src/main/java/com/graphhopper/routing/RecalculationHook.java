package com.graphhopper.routing;

/**
 * An interface with which you can trigger a heuristic change of the underlying algorithm.
 */
public interface RecalculationHook {

    /**
     * Call this method after the heuristic has changed and graph exploration should continue.
     *
     * @throws UnsupportedOperationException if change of heuristic is not supported from the underlying algorithm.
     */
    void afterHeuristicChange(boolean forward, boolean backward);

    int getVisitedNodes();
}
