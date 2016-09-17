package com.graphhopper.reader.gtfs;

import com.conveyal.gtfs.model.StopTime;

class TripHopEdge extends AbstractPtEdge {

	private StopTime from;
	private StopTime to;

	public TripHopEdge(StopTime from, StopTime to) {
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
