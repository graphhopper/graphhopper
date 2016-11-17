package com.graphhopper.reader.gtfs;

import com.graphhopper.routing.util.CarFlagEncoder;

class PtFlagEncoder extends CarFlagEncoder {

	PtFlagEncoder() {
		super(6, 5, 0);
		maxPossibleSpeed = 300;
	}

	public String toString() {
		return "pt";
	}

}
