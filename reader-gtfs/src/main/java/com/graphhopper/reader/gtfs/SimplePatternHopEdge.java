package com.graphhopper.reader.gtfs;

import java.util.*;

class SimplePatternHopEdge extends AbstractPatternHopEdge {

	private final SortedMap<Integer, Integer> departureTimeXTravelTime;

	SimplePatternHopEdge(SortedMap<Integer, Integer> departureTimeXTravelTime) {
		super();
		this.departureTimeXTravelTime = departureTimeXTravelTime;
	}

	@Override
	double nextTravelTimeIncludingWaitTime(double earliestStartTime) {
		Set<Map.Entry<Integer, Integer>> connectionsAfterEarliestStartTime = departureTimeXTravelTime.tailMap((int) earliestStartTime).entrySet();
		if (connectionsAfterEarliestStartTime.isEmpty()) {
			// missed all connections
			return Double.POSITIVE_INFINITY;
		} else {
			Map.Entry<Integer, Integer> tuple2s = connectionsAfterEarliestStartTime.iterator().next();
			int departure_time = tuple2s.getKey();
			int scheduledTravelTime = tuple2s.getValue();
			double waitTime = departure_time - earliestStartTime;
			return waitTime + scheduledTravelTime;
		}
	}
}
