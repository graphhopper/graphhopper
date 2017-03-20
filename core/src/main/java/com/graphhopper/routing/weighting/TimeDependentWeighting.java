package com.graphhopper.routing.weighting;

import com.graphhopper.util.EdgeIteratorState;

public interface TimeDependentWeighting {

	double calcWeight(EdgeIteratorState edge, double earliestStartTime);

	long calcTravelTimeSeconds(EdgeIteratorState edge, long earliestStartTime);

	int calcNTransfers(EdgeIteratorState edge);

}
