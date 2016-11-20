package com.graphhopper.reader.gtfs;

import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.util.EdgeIteratorState;

class PtEnterPositionLookupEdgeFilter implements EdgeFilter {

	PtFlagEncoder encoder;

	PtEnterPositionLookupEdgeFilter(PtFlagEncoder encoder) {
		this.encoder = encoder;
	}

	@Override
	public boolean accept(EdgeIteratorState edgeState) {
        GtfsStorage.EdgeType edgeType = encoder.getEdgeType(edgeState.getFlags());
        return edgeType == GtfsStorage.EdgeType.UNSPECIFIED || edgeType == GtfsStorage.EdgeType.STOP_NODE_MARKER_EDGE;
	}
}
