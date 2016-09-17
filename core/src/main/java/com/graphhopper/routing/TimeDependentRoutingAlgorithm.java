package com.graphhopper.routing;

import java.util.List;

public interface TimeDependentRoutingAlgorithm extends RoutingAlgorithm {

	Path calcPath(int from, int to, int earliestDepartureTime);

	List<Path> calcPaths(int from, int to, int earliestDepartureTime);

}
