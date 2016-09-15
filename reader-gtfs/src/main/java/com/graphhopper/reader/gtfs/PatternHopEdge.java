package com.graphhopper.reader.gtfs;

import com.conveyal.gtfs.model.StopTime;

class PatternHopEdge {

	private StopTime from;
	private StopTime to;

	public PatternHopEdge(StopTime from, StopTime to) {
		this.from = from;
		this.to = to;
	}

	public StopTime getFrom() {
		return from;
	}

	public StopTime getTo() {
		return to;
	}
}
