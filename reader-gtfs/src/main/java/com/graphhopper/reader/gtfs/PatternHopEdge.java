package com.graphhopper.reader.gtfs;

import org.mapdb.Fun;

import java.util.*;

public class PatternHopEdge extends AbstractPtEdge {
	public SortedMap<Integer, Integer> getDepartureTimeXTravelTime() {
		return departureTimeXTravelTime;
	}

	private final SortedMap<Integer, Integer> departureTimeXTravelTime;

	public PatternHopEdge(SortedMap<Integer, Integer> departureTimeXTravelTime) {
		super();
		this.departureTimeXTravelTime = departureTimeXTravelTime;
	}
}
