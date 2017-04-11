package com.graphhopper.reader.gtfs;

import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.weighting.AbstractWeighting;
import com.graphhopper.util.EdgeIteratorState;

class PtTravelTimeWeighting extends AbstractWeighting {

    private static long travelTime(int edgeTimeValue, long earliestStartTime) {
        int timeOfDay = (int) (earliestStartTime % (24 * 60 * 60));
        if (timeOfDay <= edgeTimeValue) {
            return (edgeTimeValue - timeOfDay);
        } else {
            throw new RuntimeException();
        }
    }

    private static long travelTimeReverse(int edgeTimeValue, long latestExitTime) {
        int timeOfDay = (int) (latestExitTime % (24 * 60 * 60));
        if (timeOfDay >= edgeTimeValue) {
            return (timeOfDay - edgeTimeValue);
        } else {
            throw new RuntimeException();
        }
    }

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

    @Override
	public double getMinWeight(double distance) {
		return 0.0;
	}

	@Override
	public double calcWeight(EdgeIteratorState edge, boolean reverse, int prevOrNextEdgeId) {
		throw new RuntimeException("Not supported.");
	}

    @Override
    public long calcMillis(EdgeIteratorState edge, boolean reverse, int prevOrNextEdgeId) {
        GtfsStorage.EdgeType edgeType = ((PtFlagEncoder) getFlagEncoder()).getEdgeType(edge.getFlags());
        switch (edgeType) {
            case HIGHWAY:
                return (long) (getWalkDistance(edge) * 3.6 / walkSpeedKmH) * 1000;
            case ENTER_TIME_EXPANDED_NETWORK:
            case LEAVE_TIME_EXPANDED_NETWORK:
                return 0;
            default:
                return ((PtFlagEncoder) getFlagEncoder()).getTime(edge.getFlags());
        }
    }

    long calcTravelTimeSeconds(EdgeIteratorState edge, long earliestStartTime) {
        GtfsStorage.EdgeType edgeType = ((PtFlagEncoder) getFlagEncoder()).getEdgeType(edge.getFlags());
        switch (edgeType) {
            case HIGHWAY:
                return (long) (getWalkDistance(edge) * 3.6 / walkSpeedKmH);
            case ENTER_TIME_EXPANDED_NETWORK:
                if (reverse) {
                    return 0;
                } else {
                    return travelTime((int) ((PtFlagEncoder) getFlagEncoder()).getTime(edge.getFlags()), earliestStartTime);
                }
            case LEAVE_TIME_EXPANDED_NETWORK:
                if (reverse) {
                    return travelTimeReverse((int) ((PtFlagEncoder) getFlagEncoder()).getTime(edge.getFlags()), earliestStartTime);
                } else {
                    return 0;
                }
            default:
                return ((PtFlagEncoder) getFlagEncoder()).getTime(edge.getFlags());
        }
	}

	int calcNTransfers(EdgeIteratorState edge) {
        return transferFactor * ((PtFlagEncoder) getFlagEncoder()).getTransfers(edge.getFlags());
	}

    double getWalkDistance(EdgeIteratorState edge) {
        GtfsStorage.EdgeType edgeType = ((PtFlagEncoder) getFlagEncoder()).getEdgeType(edge.getFlags());
        switch (edgeType) {
            case HIGHWAY:
                return edge.getDistance();
            case ENTER_PT:
            case EXIT_PT:
                return 10.0;
            default:
                return 0.0;
        }
    }

    @Override
	public String getName() {
		return "pttraveltime";
	}

}
