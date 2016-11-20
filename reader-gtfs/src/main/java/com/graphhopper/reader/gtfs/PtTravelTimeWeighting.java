package com.graphhopper.reader.gtfs;

import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.weighting.AbstractWeighting;
import com.graphhopper.routing.weighting.TimeDependentWeighting;
import com.graphhopper.util.EdgeIteratorState;

class PtTravelTimeWeighting extends AbstractWeighting implements TimeDependentWeighting {

    PtTravelTimeWeighting(FlagEncoder encoder) {
		super(encoder);
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
        GtfsStorage.EdgeType edgeType = ((PtFlagEncoder) getFlagEncoder()).getEdgeType(edgeState.getFlags());
        switch (edgeType) {
            case UNSPECIFIED:
                long l = calcMillis(edgeState, false, -1);
                return l / 1000;
            case ENTER_TIME_EXPANDED_NETWORK:
                return EnterTimeExpandedNetworkEdge.traveltime((int) ((PtFlagEncoder) getFlagEncoder()).getTime(edgeState.getFlags()), (long) earliestStartTime);
            case LEAVE_TIME_EXPANDED_NETWORK:
                return 0.0;
            case TIME_PASSES_PT_EDGE:
            case BOARD_EDGE:
                return ((PtFlagEncoder) getFlagEncoder()).getTime(edgeState.getFlags());
            default:
                throw new IllegalStateException();
        }
	}

	@Override
	public int calcNTransfers(EdgeIteratorState edgeState) {
		if (((PtFlagEncoder) getFlagEncoder()).getEdgeType(edgeState.getFlags()) == GtfsStorage.EdgeType.BOARD_EDGE) {
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
