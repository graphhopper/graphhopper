package com.graphhopper.reader.gtfs;

import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.weighting.AbstractWeighting;
import com.graphhopper.routing.weighting.TimeDependentWeighting;
import com.graphhopper.util.EdgeIteratorState;

import java.util.Map;

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
		return calcTravelTimeSeconds(edgeState, earliestStartTime);
	}

	@Override
	public double calcTravelTimeSeconds(EdgeIteratorState edgeState, double earliestStartTime) {
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
		PatternHopEdge patternHopEdge = (PatternHopEdge) edge;
		for (Map.Entry<Integer, Integer> tuple2s : patternHopEdge.getDepartureTimeXTravelTime().entrySet()) {
			int departure_time = tuple2s.getKey();
			int scheduledTravelTime = tuple2s.getValue();
			if (departure_time >= earliestStartTime) {
				double waitTime = departure_time - earliestStartTime;
				return waitTime + scheduledTravelTime;
			}
		}
		// missed all connections
		return Double.POSITIVE_INFINITY;
	}

	@Override
	public String getName() {
		return "pttraveltime";
	}

}
