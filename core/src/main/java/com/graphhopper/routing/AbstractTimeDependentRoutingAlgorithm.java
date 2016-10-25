package com.graphhopper.routing;

import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;

import java.util.Collections;
import java.util.List;

public abstract class AbstractTimeDependentRoutingAlgorithm extends AbstractRoutingAlgorithm implements TimeDependentRoutingAlgorithm {
    public AbstractTimeDependentRoutingAlgorithm(Graph graph, Weighting weighting, TraversalMode traversalMode) {
        super(graph, weighting, traversalMode);
    }

    @Override
    public List<Path> calcPaths(int from, int to, int earliestDepartureTime) {
        return Collections.singletonList(calcPath(from, to, earliestDepartureTime));
    }

    protected abstract Path extractPath(int earliestDepartureTime);
}
