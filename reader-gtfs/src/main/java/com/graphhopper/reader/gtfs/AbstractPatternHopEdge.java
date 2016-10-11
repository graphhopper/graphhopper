package com.graphhopper.reader.gtfs;

import java.util.SortedMap;

public abstract class AbstractPatternHopEdge extends AbstractPtEdge {

    public static AbstractPatternHopEdge createHopEdge(SortedMap<Integer, Integer> departureTimeXTravelTime) {
        return new CompressedPatternHopEdge(departureTimeXTravelTime);
    }

    abstract double nextTravelTimeIncludingWaitTime(double earliestStartTime);
}
