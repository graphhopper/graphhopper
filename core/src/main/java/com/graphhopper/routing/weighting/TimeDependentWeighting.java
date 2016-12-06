package com.graphhopper.routing.weighting;

import com.graphhopper.util.EdgeIteratorState;

public interface TimeDependentWeighting {

	double calcWeight(EdgeIteratorState edgeState, double earliestStartTime);

	long calcTravelTimeSeconds(EdgeIteratorState edgeState, long earliestStartTime);

	int calcNTransfers(EdgeIteratorState edgeState);

}
