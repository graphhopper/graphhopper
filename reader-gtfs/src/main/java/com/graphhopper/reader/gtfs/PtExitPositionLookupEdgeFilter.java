package com.graphhopper.reader.gtfs;

import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.util.EdgeIteratorState;

class PtExitPositionLookupEdgeFilter implements EdgeFilter {

	@Override
	public boolean accept(EdgeIteratorState edgeState) {
		GtfsStorage.EdgeType edgeType = GtfsStorage.EdgeType.values()[edgeState.getAdditionalField()];
		return edgeType == GtfsStorage.EdgeType.UNSPECIFIED || edgeType == GtfsStorage.EdgeType.STOP_EXIT_NODE_MARKER_EDGE;
	}
}
