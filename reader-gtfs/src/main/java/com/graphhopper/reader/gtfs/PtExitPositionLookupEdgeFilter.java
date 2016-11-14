package com.graphhopper.reader.gtfs;

import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.util.EdgeIteratorState;

class PtExitPositionLookupEdgeFilter implements EdgeFilter {
	private final GtfsStorage gtfsStorage;

	PtExitPositionLookupEdgeFilter(GtfsStorage gtfsStorage) {
		this.gtfsStorage = gtfsStorage;
	}

	@Override
	public boolean accept(EdgeIteratorState edgeState) {
		AbstractPtEdge ptEdge = gtfsStorage.getEdges().get(edgeState.getEdge());
		return ptEdge instanceof StopExitNodeMarkerEdge;
	}
}
