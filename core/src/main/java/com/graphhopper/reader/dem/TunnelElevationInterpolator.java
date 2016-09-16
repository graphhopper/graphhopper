package com.graphhopper.reader.dem;

import com.graphhopper.routing.util.DataFlagEncoder;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.util.EdgeIteratorState;

public class TunnelElevationInterpolator extends AbstractEdgeElevationInterpolator {

	public TunnelElevationInterpolator(GraphHopperStorage storage, DataFlagEncoder dataFlagEncoder) {
		super(storage, dataFlagEncoder);
	}

	@Override
	protected boolean isStructureEdge(EdgeIteratorState edge) {
		return dataFlagEncoder.isTransportModeTunnel(edge);
	}
}
