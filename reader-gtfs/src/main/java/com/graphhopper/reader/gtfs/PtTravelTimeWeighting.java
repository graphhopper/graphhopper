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
		return calcTravelTimeSeconds(edgeState, earliestStartTime);
	}

	@Override
	public double calcTravelTimeSeconds(EdgeIteratorState edgeState, double earliestStartTime) {
		if (edgeState.getEdge() > gtfsStorage.getRealEdgesSize()-1) {
			return 0.0;
		}
		AbstractPtEdge edge = gtfsStorage.getEdges().get(edgeState.getEdge());
		if (edge instanceof EnterLoopEdge) {
			return 0.0;
		} else if (edge instanceof GtfsTransferEdge) {
			return ((GtfsTransferEdge) edge).getTransfer().min_transfer_time;
		} else if (edge instanceof AccessEdge) {
			return ((AccessEdge) edge).getMinimumTransferTimeSeconds();
		} else if (edge instanceof EgressEdge) {
			return 0.0;
		} else if (edge instanceof EnterEdge) {
			return 0.0;
		} else if (edge instanceof ExitEdge) {
			return 0.0;
		} else {
			AbstractPatternHopEdge hopEdge = (AbstractPatternHopEdge) edge;
			return hopEdge.nextTravelTimeIncludingWaitTime(earliestStartTime);
		}
	}

	@Override
	public String getName() {
		return "pttraveltime";
	}

}
