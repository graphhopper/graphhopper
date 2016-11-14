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
		AbstractPtEdge edge = gtfsStorage.getEdges().get(edgeState.getEdge());
		if (edge == null) {
			long l = calcMillis(edgeState, false, -1);
			return l / 1000;
		}
		if (edge instanceof EnterTimeExpandedNetworkEdge) {
			return ((EnterTimeExpandedNetworkEdge) edge).traveltime((long) earliestStartTime);
		} else if (edge instanceof StopNodeMarkerEdge) {
			return Double.POSITIVE_INFINITY;
		} else if (edge instanceof StopExitNodeMarkerEdge) {
            return Double.POSITIVE_INFINITY;
        } else if (edge instanceof HopEdge) {
            return ((TimePassesPtEdge) edge).deltaTime;
        } else if (edge instanceof DwellEdge) {
            return ((TimePassesPtEdge) edge).deltaTime;
        } else if (edge instanceof TimePassesPtEdge) {
			return ((TimePassesPtEdge) edge).deltaTime;
		} else if (edge instanceof ArrivalNodeMarkerEdge) {
            return Double.POSITIVE_INFINITY;
        } else if (edge instanceof LeaveTimeExpandedNetworkEdge) {
			return 0.0;
		} else {
			throw new IllegalStateException();
		}
	}

	@Override
	public int calcNTransfers(EdgeIteratorState edgeState) {
		AbstractPtEdge edge = gtfsStorage.getEdges().get(edgeState.getEdge());
		if (edge instanceof BoardEdge) {
			return 1;
		} else {
			return 0;
		}
	}

	@Override
	public String getName() {
		return "pttraveltime";
	}

}
