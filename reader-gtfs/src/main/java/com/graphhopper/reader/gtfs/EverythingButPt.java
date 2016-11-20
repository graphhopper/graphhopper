package com.graphhopper.reader.gtfs;

import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.util.EdgeIteratorState;

class EverythingButPt implements EdgeFilter {

    PtFlagEncoder encoder;

    EverythingButPt(PtFlagEncoder encoder) {
        this.encoder = encoder;
    }

    @Override
    public boolean accept(EdgeIteratorState edgeState) {
        return encoder.getEdgeType(edgeState.getFlags()) == GtfsStorage.EdgeType.UNSPECIFIED;
    }
}
