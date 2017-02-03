package com.graphhopper.reader.gtfs;

import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.weighting.AbstractWeighting;
import com.graphhopper.routing.weighting.TimeDependentWeighting;
import com.graphhopper.util.EdgeIteratorState;

class PtTravelTimeWeighting extends AbstractWeighting implements TimeDependentWeighting {

    private final boolean reverse;
    private final int transferFactor;

    PtTravelTimeWeighting(FlagEncoder encoder) {
		super(encoder);
		this.reverse = false;
        this.transferFactor = 1;
    }

    private PtTravelTimeWeighting(FlagEncoder encoder, boolean reverse, int transferFactor) {
        super(encoder);
        this.reverse = reverse;
        this.transferFactor = transferFactor;
    }

    PtTravelTimeWeighting reverse() {
        return new PtTravelTimeWeighting(flagEncoder, !reverse, transferFactor);
    }

    PtTravelTimeWeighting ignoringNumberOfTransfers() {
        return new PtTravelTimeWeighting(flagEncoder, reverse, 0);
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
	public double calcWeight(EdgeIteratorState edgeState, double earliestStartTime) {
        throw new RuntimeException("Not supported.");
	}

	@Override
	public long calcTravelTimeSeconds(EdgeIteratorState edgeState, long earliestStartTime) {
        GtfsStorage.EdgeType edgeType = ((PtFlagEncoder) getFlagEncoder()).getEdgeType(edgeState.getFlags());
        switch (edgeType) {
            case UNSPECIFIED:
                if (getFlagEncoder().getSpeed(edgeState.getFlags()) == 0.0) {
                    // // FIXME: Shouldn't happen, but does.
                    return 0;
                }
                long l = calcMillis(edgeState, reverse, -1);
                return l / 1000;
            case ENTER_TIME_EXPANDED_NETWORK:
                if (reverse) {
                    return 0;
                } else {
                    return GtfsStorage.traveltime((int) ((PtFlagEncoder) getFlagEncoder()).getTime(edgeState.getFlags()), (long) earliestStartTime);
                }
            case LEAVE_TIME_EXPANDED_NETWORK:
                if (reverse) {
                    return GtfsStorage.traveltimeReverse((int) ((PtFlagEncoder) getFlagEncoder()).getTime(edgeState.getFlags()), (long) earliestStartTime);
                } else {
                    return 0;
                }
            case BOARD:
            case ENTER_PT:
            case EXIT_PT:
				return 0;
            case TIME_PASSES:
            case TRANSFER:
            case HOP:
                return ((PtFlagEncoder) getFlagEncoder()).getTime(edgeState.getFlags());
            default:
                throw new IllegalStateException();
        }
	}

	@Override
	public int calcNTransfers(EdgeIteratorState edgeState) {
		if (((PtFlagEncoder) getFlagEncoder()).getEdgeType(edgeState.getFlags()) == GtfsStorage.EdgeType.BOARD) {
			return transferFactor;
		} else {
			return 0;
		}
	}

	@Override
	public String getName() {
		return "pttraveltime";
	}

}
