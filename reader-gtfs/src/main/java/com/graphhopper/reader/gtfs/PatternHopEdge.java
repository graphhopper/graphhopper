package com.graphhopper.reader.gtfs;

import org.mapdb.Fun;

import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

public class PatternHopEdge extends AbstractPtEdge {
	public SortedSet<Fun.Tuple2<Integer, Integer>> getDepartureTimeXTravelTime() {
		return departureTimeXTravelTime;
	}

	private final TreeSet<Fun.Tuple2<Integer, Integer>> departureTimeXTravelTime;

	public PatternHopEdge(TreeSet<Fun.Tuple2<Integer, Integer>> departureTimeXTravelTime) {
		super();
		this.departureTimeXTravelTime = departureTimeXTravelTime;
	}
}
