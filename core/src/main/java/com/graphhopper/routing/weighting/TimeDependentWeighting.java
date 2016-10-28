package com.graphhopper.routing.weighting;

import com.graphhopper.util.EdgeIteratorState;

public interface TimeDependentWeighting {

	double calcWeight(EdgeIteratorState edgeState, boolean reverse, int prevOrNextEdgeId, double earliestStartTime);

	double calcTravelTimeSeconds(EdgeIteratorState edgeState, double earliestStartTime);

	int calcNTransfers(EdgeIteratorState edgeState);

}
