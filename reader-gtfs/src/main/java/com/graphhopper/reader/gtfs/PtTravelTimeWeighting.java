package com.graphhopper.reader.gtfs;

import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.weighting.AbstractWeighting;
import com.graphhopper.routing.weighting.TimeDependentWeighting;
import com.graphhopper.util.EdgeIteratorState;

class PtTravelTimeWeighting extends AbstractWeighting implements TimeDependentWeighting {

    private final boolean reverse;
    private final double walkSpeedKmH;
    private final int transferFactor;

    PtTravelTimeWeighting(FlagEncoder encoder, double walkSpeedKmH) {
		this(encoder, false, walkSpeedKmH, 1);
    }

    private PtTravelTimeWeighting(FlagEncoder encoder, boolean reverse, double walkSpeedKmH, int transferFactor) {
        super(encoder);
        this.reverse = reverse;
        this.walkSpeedKmH = walkSpeedKmH;
        this.transferFactor = transferFactor;
    }

    PtTravelTimeWeighting reverse() {
        return new PtTravelTimeWeighting(flagEncoder, !reverse, walkSpeedKmH, transferFactor);
    }

    PtTravelTimeWeighting ignoringNumberOfTransfers() {
        return new PtTravelTimeWeighting(flagEncoder, reverse, walkSpeedKmH, 0);
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
                return (long) (edgeState.getDistance() * 3.6 / walkSpeedKmH) ;
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
            default:
                return ((PtFlagEncoder) getFlagEncoder()).getTime(edgeState.getFlags());
        }
	}

	@Override
	public int calcNTransfers(EdgeIteratorState edgeState) {
        return transferFactor * ((PtFlagEncoder) getFlagEncoder()).getTransfers(edgeState.getFlags());
	}

	@Override
	public String getName() {
		return "pttraveltime";
	}

}
