package com.graphhopper.reader.gtfs;

import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.weighting.AbstractWeighting;
import com.graphhopper.routing.weighting.TimeDependentWeighting;
import com.graphhopper.util.EdgeIteratorState;

class PtTravelTimeWeighting extends AbstractWeighting implements TimeDependentWeighting {

	private final GtfsStorage gtfsStorage;

	PtTravelTimeWeighting(FlagEncoder encoder, GtfsStorage gtfsStorage) {
		super(encoder);
		this.gtfsStorage = gtfsStorage;
	}

	@Override
	public double getMinWeight(double distance) {
		return 0.0;
	}

	@Override
	public double calcWeight(EdgeIteratorState edgeState, boolean reverse, int prevOrNextEdgeId) {
		throw new RuntimeException("Not supported.");
	}

	@Override
	public double calcWeight(EdgeIteratorState edgeState, boolean reverse, int prevOrNextEdgeId, double earliestStartTime) {
		if (reverse) {
			throw new UnsupportedOperationException();
		}
		if (edgeState.getEdge() > gtfsStorage.getRealEdgesSize()-1) {
			return 0.0;
		}
		AbstractPtEdge edge = gtfsStorage.getEdges().get(edgeState.getEdge());
		if (edge instanceof StopLoopEdge) {
			return 0.0;
		}
		if (edge instanceof GtfsTransferEdge) {
			return ((GtfsTransferEdge) edge).getTransfer().min_transfer_time;
		}
		TripHopEdge patternHopEdge = (TripHopEdge) edge;
		if (patternHopEdge.getFrom().departure_time < earliestStartTime) {
			// missed that one
			return Double.POSITIVE_INFINITY;
		}
		double waitTime = patternHopEdge.getFrom().departure_time - earliestStartTime;
		int scheduledTravelTime = patternHopEdge.getTo().arrival_time - patternHopEdge.getFrom().departure_time;
		return waitTime + scheduledTravelTime;
	}

	@Override
	public String getName() {
		return "pttraveltime";
	}

}
