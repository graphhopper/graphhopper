package com.graphhopper.reader.gtfs;

import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.util.EdgeIteratorState;

class EverythingButPt implements EdgeFilter {

    @Override
    public boolean accept(EdgeIteratorState edgeState) {
        return GtfsStorage.EdgeType.values()[edgeState.getAdditionalField()] == GtfsStorage.EdgeType.UNSPECIFIED;
    }
}
