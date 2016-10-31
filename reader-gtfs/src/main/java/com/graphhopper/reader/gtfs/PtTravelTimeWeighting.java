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
		} else if (edge instanceof HopEdge) {
            return ((TimePassesPtEdge) edge).deltaTime;
        } else if (edge instanceof DwellEdge) {
            return ((TimePassesPtEdge) edge).deltaTime;
        } else if (edge instanceof TimePassesPtEdge) {
			return ((TimePassesPtEdge) edge).deltaTime;
		} else if (edge instanceof ExitFindingDummyEdge) {
            return Double.POSITIVE_INFINITY;
        } else {
			throw new IllegalStateException();
		}
	}

	@Override
	public int calcNTransfers(EdgeIteratorState edgeState) {
		AbstractPtEdge edge = gtfsStorage.getEdges().get(edgeState.getEdge());
		if (edge instanceof AccessEdge || edge instanceof EnterEdge) {
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
