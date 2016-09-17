package com.graphhopper.reader.gtfs;

import com.graphhopper.routing.util.CarFlagEncoder;

class TripHopFlagEncoder extends CarFlagEncoder {

	TripHopFlagEncoder() {
		super(6, 5, 0);
		maxPossibleSpeed = 300;
	}

	public String toString() {
		return "pt";
	}

}
