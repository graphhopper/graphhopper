package com.graphhopper.reader.gtfs;

import com.graphhopper.routing.util.CarFlagEncoder;

class PatternHopFlagEncoder extends CarFlagEncoder {

	PatternHopFlagEncoder() {
		super(6, 5, 0);
		maxPossibleSpeed = 300;
	}

	public String toString() {
		return "pt";
	}

}
