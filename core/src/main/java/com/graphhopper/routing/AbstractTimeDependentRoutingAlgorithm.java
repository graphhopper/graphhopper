package com.graphhopper.routing;

import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;

import java.util.Collections;
import java.util.List;

public abstract class AbstractTimeDependentRoutingAlgorithm extends AbstractRoutingAlgorithm implements TimeDependentRoutingAlgorithm {
	public AbstractTimeDependentRoutingAlgorithm(Graph graph, FlagEncoder encoder, Weighting weighting, TraversalMode traversalMode) {
		super(graph, encoder, weighting, traversalMode);
	}

	@Override
	public List<Path> calcPaths(int from, int to, int earliestDepartureTime) {
		return Collections.singletonList(calcPath(from, to, earliestDepartureTime));
	}

}
